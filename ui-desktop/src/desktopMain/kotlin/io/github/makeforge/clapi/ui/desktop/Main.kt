package io.github.makeforge.clapi.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import dev.nucleusframework.window.tao.LocalWindowExceptionHandlerFactory
import dev.nucleusframework.window.tao.WindowExceptionHandlerFactory
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.mcp.server.transport.StdioMcpServerRunner
import io.github.makeforge.clapi.ui.desktop.logging.GlobalCrashHandler
import io.github.makeforge.clapi.ui.desktop.logging.LogDirectoryConfigurator
import io.github.makeforge.clapi.ui.desktop.logging.UiLogDispatcher
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.CrashReportDialog
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import io.github.vinceglb.filekit.FileKit
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

    FileKit.init(appId = "io.github.makeforge.clapi")

    nucleusApplication(args) {
        val appScope: NucleusApplicationScope = this
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

        // Async exit: avoids blocking the UI thread with file I/O during window close.
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

        var activeCrash by remember { mutableStateOf<Throwable?>(null) }

        DisposableEffect(Unit) {
            GlobalCrashHandler.registerListener { throwable ->
                activeCrash = throwable
            }
            onDispose {
                GlobalCrashHandler.unregisterListener()
            }
        }

        val windowExceptionHandlerFactory = remember {
            WindowExceptionHandlerFactory { _ ->
                WindowExceptionHandler { throwable ->
                    GlobalCrashHandler.handleException(throwable, Thread.currentThread().name)
                }
            }
        }

        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides windowExceptionHandlerFactory,
        ) {
            ClapiTheme(appTheme = state.settings.theme, language = state.settings.language) {
                with(appScope) {
                    MaterialDecoratedWindow(
                        onCloseRequest = { exitPending = true },
                        icon = painterResource("icons/clapi-icon-64.png"),
                        title = "Clapi",
                        state = rememberWindowState(
                            size = DpSize(1400.dp, 900.dp),
                            position = WindowPosition.Aligned(Alignment.Center),
                        ),
                        minimumSize = DpSize(800.dp, 600.dp),
                        onPreviewKeyEvent = { false },
                    ) {
                        MaterialTitleBar()

                        LaunchedEffect(state.settings.theme, state.settings.language) {
                            GlobalCrashHandler.currentTheme = state.settings.theme
                            GlobalCrashHandler.currentLanguage = state.settings.language
                        }

                        Box(Modifier.fillMaxSize()) {
                            MainScreen(state)

                            activeCrash?.let { throwable ->
                                CrashReportDialog(
                                    throwable = throwable,
                                    onDismiss = { activeCrash = null },
                                    onRestart = {
                                        activeCrash = null
                                        GlobalCrashHandler.restartApplication()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
