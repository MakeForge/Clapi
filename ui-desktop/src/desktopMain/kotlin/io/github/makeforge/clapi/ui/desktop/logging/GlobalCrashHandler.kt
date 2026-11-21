package io.github.makeforge.clapi.ui.desktop.logging

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import io.github.makeforge.clapi.core.logging.AppLogging
import io.github.makeforge.clapi.ui.shared.components.CrashReportContent
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.Window
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Global crash coordinator that catches, logs, and displays crash dialogs for unhandled exceptions.
 */
object GlobalCrashHandler {

    private val isDialogShowing = AtomicBoolean(false)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var crashListener: ((Throwable) -> Unit)? = null

    @Volatile
    var currentTheme: AppTheme = AppTheme.DARK

    @Volatile
    var currentLanguage: AppLanguage = AppLanguage.EN

    @Volatile
    var showDialogOnException: Boolean = true

    /**
     * Registers an optional listener to receive uncaught exceptions (e.g. for testing or diagnostics).
     */
    fun registerListener(listener: (Throwable) -> Unit) {
        crashListener = listener
    }

    /**
     * Unregisters the UI crash listener.
     */
    fun unregisterListener() {
        crashListener = null
    }

    /**
     * Handles an uncaught exception:
     * 1. Prints stack trace to standard error.
     * 2. Logs via [AppLogging] logger ("CrashHandler") to ensure persistence in log files.
     * 3. Dispatches the exception to the registered crash listener.
     * 4. Triggers the crash dialog callback if enabled.
     */
    fun handleException(throwable: Throwable, threadName: String? = null, window: Window? = null) {
        throwable.printStackTrace()
        runCatching {
            AppLogging.logger("CrashHandler").error(throwable) {
                if (threadName != null) {
                    "Uncaught fatal exception on thread '$threadName': ${throwable.message}"
                } else {
                    "Uncaught fatal exception in window: ${throwable.message}"
                }
            }
        }

        val listener = crashListener
        if (listener != null) {
            runCatching {
                listener(throwable)
            }.onFailure {
                mainScope.launch {
                    listener(throwable)
                }
            }
        }

        showCrashDialog(throwable, window)
    }

    /**
     * Restarts the Clapi desktop application process and terminates the current JVM instance.
     */
    fun restartApplication() {
        try {
            val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
            val command = ProcessHandle.current().info().command().orElse(null)
            val arguments = ProcessHandle.current().info().arguments().orElse(null)

            val processBuilder = if (command != null && arguments != null && arguments.isNotEmpty()) {
                ProcessBuilder(listOf(command) + arguments.toList())
            } else {
                val classPath = System.getProperty("java.class.path")
                val sunJavaCommand = System.getProperty("sun.java.command") ?: ""
                val parts = sunJavaCommand.split(" ").filter { it.isNotBlank() }
                val mainTarget = parts.firstOrNull() ?: ""
                if (mainTarget.endsWith(".jar")) {
                    ProcessBuilder(listOf(javaBin, "-jar") + parts)
                } else {
                    ProcessBuilder(listOf(javaBin, "-cp", classPath) + parts)
                }
            }
            processBuilder.start()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            exitProcess(0)
        }
    }

    /**
     * Displays the crash dialog if not already handled by the in-window Compose listener.
     * On Tao, the active window's registered listener displays the in-composition [CrashReportDialog],
     * or if a fatal exception escapes the Tao loop, Nucleus shows its native fatal error dialog.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun showCrashDialog(throwable: Throwable, parentWindow: Window? = null) {
        if (!showDialogOnException) {
            return
        }
        // In-window CrashReportDialog is rendered via crashListener in Main.kt.
    }
}
