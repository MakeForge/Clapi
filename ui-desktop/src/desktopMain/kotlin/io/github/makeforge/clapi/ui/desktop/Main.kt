package io.github.makeforge.clapi.ui.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.mcp.server.transport.StdioMcpServerRunner
import io.github.makeforge.clapi.ui.desktop.logging.GlobalCrashHandler
import io.github.makeforge.clapi.ui.desktop.logging.LogDirectoryConfigurator
import io.github.makeforge.clapi.ui.desktop.logging.UiLogDispatcher
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import java.awt.Window as AwtWindow
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Starts Clapi, or runs the headless packaged-runtime shutdown smoke test.
 *
 * The smoke mode intentionally exercises the same [AppState.disposeAndAwait]
 * path used during desktop shutdown without opening a Compose window. It is
 * used by the packaging task to catch stale or incomplete fat-JAR contents.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    LogDirectoryConfigurator.configure(args)

    if ("--mcp" in args) {
        StdioMcpServerRunner.runFromCli(args)
        return
    }

    // Initialize persistent workspace repository with the configured or default app directory.
    val storageDir = StdioMcpServerRunner.resolveStorageDirectory(args)
    File(storageDir).mkdirs()
    WorkspaceRepository.canonicalRepository = DefaultCanonicalCollectionRepository(baseDirectory = storageDir)

    // Preload workspace in the background while Compose/Skiko initializes the window.
    CoroutineScope(Dispatchers.IO).launch {
        WorkspaceRepository.canonicalRepository.loadGlobalCollection()
    }

    if ("--shutdown-smoke-test" in args) {
        runBlocking {
            AppState(openDefaultTab = false).disposeAndAwait()
        }
        return
    }

    // Ensure OS surfaces (dock/app switcher/task tooltip) use the product name.
    System.setProperty("apple.awt.application.name", "Clapi")
    System.setProperty("java.awt.application.name", "Clapi")

    // Set the dock/taskbar icon from the classpath so that `java -jar Clapi.jar`
    // shows the correct icon on all OSes without extra JVM flags.
    kotlin.concurrent.thread(name = "AppIconLoader", isDaemon = true) {
        applyAppIcon()
    }

    application {
    val state = remember {
        AppState(openDefaultTab = false).also {
            SettingsRepository.load(it.settings)
            it.collectionsExpanded = it.settings.collectionsExpanded
            it.environmentsExpanded = it.settings.environmentsExpanded
            WorkspaceRepository.load(it)   // collections first
            TabsRepository.load(it)         // IDs resolved against loaded collections
            // Always start with an unfiltered sidebar on app launch.
            it.sidebarSearchQuery = ""
            // Link restored tabs to sidebar: highlight + expand ancestors
            it.syncSidebarToActiveTab()
            // Start embedded MCP server if enabled in settings
            it.embeddedMcpServerController.applySettings()
            UiLogDispatcher.bind(it.diagnostics)
        }
    }

    // Async exit: avoids blocking the EDT with file I/O during window close.
    var exitPending by remember { mutableStateOf(false) }
    if (exitPending) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                TabsRepository.save(state)
                WorkspaceRepository.save(state)
            }
            UiLogDispatcher.unbind()
            state.disposeAndAwait()
            LogDirectoryConfigurator.shutdown()
            exitApplication()
        }
    }

    val windowExceptionHandlerFactory = remember {
        object : WindowExceptionHandlerFactory {
            override fun exceptionHandler(window: AwtWindow): WindowExceptionHandler {
                return WindowExceptionHandler { throwable ->
                    GlobalCrashHandler.handleException(throwable, Thread.currentThread().name, window)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalWindowExceptionHandlerFactory provides windowExceptionHandlerFactory,
    ) {
        Window(
            onCloseRequest = { exitPending = true },
            // Prevent a second close event from launching a duplicate save coroutine.
            // The window stays visible briefly while the LaunchedEffect finishes.
            icon = painterResource("icons/clapi-icon-64.png"),
            title = "Clapi",
            state = rememberWindowState(
                size = DpSize(1400.dp, 900.dp),
                position = WindowPosition.Aligned(Alignment.Center),
            ),
            onPreviewKeyEvent = { false },
        ) {
            LaunchedEffect(state.settings.theme, state.settings.language) {
                GlobalCrashHandler.currentTheme = state.settings.theme
                GlobalCrashHandler.currentLanguage = state.settings.language
            }

            ClapiTheme(appTheme = state.settings.theme, language = state.settings.language) {
                MainScreen(state)
            }
        }
    }
    }
}

/**
 * Sets the application dock / taskbar icon from the bundled classpath resource so that
 * running `java -jar Clapi.jar` shows the correct icon on all supported OSes without
 * requiring any extra JVM flags (e.g. `-Xdock:icon` on macOS).
 *
 * Uses [java.awt.Taskbar] (available since Java 9).  Any unsupported platform or JVM
 * silently swallows the exception and falls back to the Compose [Window] icon.
 */
private fun applyAppIcon() {
    try {
        val iconUrl = object {}::class.java.classLoader
            .getResource("icons/clapi-icon-256.png") ?: return
        val image = javax.imageio.ImageIO.read(iconUrl) ?: return
        if (java.awt.Taskbar.isTaskbarSupported()) {
            val taskbar = java.awt.Taskbar.getTaskbar()
            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = image
            }
        }
    } catch (_: Exception) {
        // Non-GUI environment or JVM / OS that does not support Taskbar — ignore.
    }
}
