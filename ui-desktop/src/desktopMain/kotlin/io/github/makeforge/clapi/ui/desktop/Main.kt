package io.github.makeforge.clapi.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.aotTraining
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.mcp.server.transport.StdioMcpServerRunner
import io.github.makeforge.clapi.ui.desktop.aot.AotTrainingRunner
import io.github.makeforge.clapi.ui.desktop.logging.GlobalCrashHandler
import io.github.makeforge.clapi.ui.desktop.logging.LogDirectoryConfigurator
import io.github.makeforge.clapi.ui.desktop.logging.UiLogDispatcher
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.BrandIcon
import io.github.makeforge.clapi.ui.shared.components.CrashReportContent
import io.github.makeforge.clapi.ui.shared.components.ResponseViewer
import io.github.makeforge.clapi.ui.shared.components.TitleBarBrandContent
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import io.github.vinceglb.filekit.FileKit
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

private fun initLinuxX11Threads() {
    if (System.getProperty("os.name")?.contains("Linux", ignoreCase = true) == true) {
        val result = runCatching {
            val linker = java.lang.foreign.Linker.nativeLinker()
            val lookup = java.lang.foreign.SymbolLookup.libraryLookup("libX11.so.6", java.lang.foreign.Arena.global())
            lookup.find("XInitThreads").ifPresent { sym ->
                val handle = linker.downcallHandle(
                    sym,
                    java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT),
                )
                val status = handle.invokeExact() as Int
                println("Linux XInitThreads initialized: status=$status")
            }
        }
        result.onFailure {
            println("Linux XInitThreads initialization failed: ${it.message}")
        }
    }
}

internal fun formatWindowTitle(activeTabName: String?): String {
    val trimmed = activeTabName?.trim()
    return if (trimmed.isNullOrEmpty()) "Clapi" else "$trimmed - Clapi"
}

internal fun formatDetachedResponseTitle(activeTabName: String?): String {
    val trimmed = activeTabName?.trim()
    return if (trimmed.isNullOrEmpty()) "Clapi - Response" else "$trimmed - Response - Clapi"
}

private fun parseMcpPortArg(args: Array<String>): Int {
    for (i in args.indices) {
        val arg = args[i]
        if (arg == "--mcp-port" && i + 1 < args.size) {
            return args[i + 1].trim().toIntOrNull() ?: 0
        }
        if (arg.startsWith("--mcp-port=")) {
            return arg.removePrefix("--mcp-port=").trim().toIntOrNull() ?: 0
        }
    }
    return 0
}

/**
 * Starts Clapi, or runs the headless packaged-runtime shutdown smoke test.
 *
 * The smoke mode intentionally exercises the same [AppState.disposeAndAwait]
 * path used during desktop shutdown without opening a Compose window. It is
 * used by the packaging task to catch stale or incomplete fat-JAR contents.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    initLinuxX11Threads()
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

    if ("--mcp-http-headless" in args) {
        val requestedPort = parseMcpPortArg(args)
        val state = AppState(openDefaultTab = false).also {
            SettingsRepository.load(it.settings)
            WorkspaceRepository.load(it)
            UiLogDispatcher.bind(it.diagnostics)
        }
        val started = state.embeddedMcpServerController.start(requestedPort)
        if (!started) {
            System.err.println("Failed to start embedded MCP server: ${state.embeddedMcpServerController.lastError}")
            runBlocking { state.disposeAndAwait() }
            LogDirectoryConfigurator.shutdown()
            kotlin.system.exitProcess(1)
        }

        val shutdownLatch = CountDownLatch(1)
        val shutdownHook = Thread {
            shutdownLatch.countDown()
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        // Also release on stdin EOF so parent test drivers can trigger clean shutdown cleanly.
        Thread({
            runCatching {
                while (System.`in`.read() != -1) {
                    // Wait for EOF
                }
            }
            shutdownLatch.countDown()
        }, "clapi-mcp-headless-stdin-watcher").apply {
            isDaemon = true
            start()
        }

        println("CLAPI_MCP_READY url=${state.embeddedMcpServerController.endpointUrl} pid=${ProcessHandle.current().pid()}")
        System.out.flush()

        try {
            shutdownLatch.await()
        } finally {
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            UiLogDispatcher.unbind()
            runBlocking { state.disposeAndAwait() }
            LogDirectoryConfigurator.shutdown()
        }
        return
    }

    FileKit.init(appId = "io.github.makeforge.clapi")

    nucleusApplication(args) {
        val appScope: NucleusApplicationScope = this
        aotTraining(duration = 15.seconds)
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

        if (isAotTraining) {
            LaunchedEffect(Unit) {
                AotTrainingRunner.runTrainingPass(state)
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

        ProvideWindowExceptionHandler(
            onException = { throwable ->
                GlobalCrashHandler.handleException(throwable, Thread.currentThread().name)
            },
        ) {
            ClapiTheme(appTheme = state.settings.theme, language = state.settings.language) {
                with(appScope) {
                    MaterialDecoratedWindow(
                        onCloseRequest = { exitPending = true },
                        icon = painterResource("icons/clapi-icon-64.png"),
                        title = formatWindowTitle(state.activeTab?.name),
                        state = rememberWindowState(
                            size = DpSize(1400.dp, 900.dp),
                            position = WindowPosition.Aligned(Alignment.Center),
                        ),
                        minimumSize = DpSize(800.dp, 600.dp),
                        onPreviewKeyEvent = { event ->
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.Escape &&
                                (state.cancelActiveSidebarDrag?.invoke() == true)
                        },
                        onKeyEvent = { event ->
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.Escape &&
                                state.clearSidebarSelectionOnEscape()
                        },
                    ) {
                        MaterialTitleBar {
                            TitleBarBrandContent(
                                state = state,
                                modifier = Modifier.align(Alignment.Start),
                            )
                        }

                        LaunchedEffect(state.settings.theme, state.settings.language) {
                            GlobalCrashHandler.currentTheme = state.settings.theme
                            GlobalCrashHandler.currentLanguage = state.settings.language
                        }

                        Box(Modifier.fillMaxSize()) {
                            MainScreen(state)
                        }
                    }

                    activeCrash?.let { throwable ->
                        MaterialDecoratedWindow(
                            onCloseRequest = { activeCrash = null },
                            icon = painterResource("icons/clapi-icon-64.png"),
                            title = "Clapi - Application Error",
                            state = rememberWindowState(
                                size = DpSize(640.dp, 520.dp),
                                position = WindowPosition.Aligned(Alignment.Center),
                            ),
                            minimumSize = DpSize(440.dp, 360.dp),
                            onPreviewKeyEvent = { false },
                        ) {
                            MaterialTitleBar {
                                Row(
                                    modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    BrandIcon(modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Application Error",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ClapiColors.OnSurface,
                                    )
                                }
                            }

                            Box(Modifier.fillMaxSize().background(ClapiColors.Background)) {
                                CrashReportContent(
                                    throwable = throwable,
                                    onRestart = {
                                        activeCrash = null
                                        GlobalCrashHandler.restartApplication()
                                    },
                                )
                            }
                        }
                    }

                    if (state.isResponseDetached) {
                        MaterialDecoratedWindow(
                            onCloseRequest = { state.closeDetachedResponse() },
                            icon = painterResource("icons/clapi-icon-64.png"),
                            title = formatDetachedResponseTitle(state.activeTab?.name),
                            state = rememberWindowState(
                                size = DpSize(800.dp, 600.dp),
                                position = WindowPosition.Aligned(Alignment.Center),
                            ),
                            minimumSize = DpSize(400.dp, 300.dp),
                            onPreviewKeyEvent = { false },
                        ) {
                            MaterialTitleBar {
                                Row(
                                    modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    BrandIcon(modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Response",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ClapiColors.OnSurface,
                                    )
                                }
                            }

                            Box(Modifier.fillMaxSize().background(ClapiColors.Background)) {
                                val activeTab = state.activeTab
                                if (activeTab != null) {
                                    ResponseViewer(
                                        tab = activeTab,
                                        modifier = Modifier.fillMaxSize(),
                                        showCursorPosition = state.settings.showEditorPositionIndicator,
                                        state = state,
                                        isDetached = true,
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = Strings.noRequestSelected,
                                            color = ClapiColors.OnSurfaceDim,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
