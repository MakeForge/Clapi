package io.github.makeforge.clapi.ui.desktop.logging

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import io.github.makeforge.clapi.core.logging.AppLogging
import io.github.makeforge.clapi.ui.shared.components.CrashReportContent
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import java.awt.Dialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/**
 * Global crash coordinator that catches, logs, and displays crash dialogs for unhandled exceptions.
 */
object GlobalCrashHandler {

    private val isDialogShowing = AtomicBoolean(false)

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
     * 3. Dispatches the exception to the registered crash listener on the EDT.
     * 4. Displays the consistent Clapi crash report dialog on the EDT.
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
            if (SwingUtilities.isEventDispatchThread()) {
                listener(throwable)
            } else {
                SwingUtilities.invokeLater {
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
     * Displays the crash dialog in a standalone modal [ComposeDialog] on the EDT.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun showCrashDialog(throwable: Throwable, parentWindow: Window? = null) {
        if (!showDialogOnException) {
            return
        }

        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return
        }

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater {
                showCrashDialog(throwable, parentWindow)
            }
            return
        }

        // Avoid opening multiple stacked crash dialogs
        if (!isDialogShowing.compareAndSet(false, true)) {
            return
        }

        var owner: Window? = null
        try {
            owner = parentWindow
                ?: Window.getWindows().firstOrNull { it.isVisible && it is Frame }
                ?: Window.getWindows().firstOrNull { it.isVisible }

            val dialog = ComposeDialog(
                owner = owner,
                modalityType = Dialog.ModalityType.APPLICATION_MODAL,
            )
            dialog.title = "Clapi - Application Error"
            dialog.setSize(640, 520)
            dialog.setLocationRelativeTo(owner)
            dialog.isResizable = true

            // Set dark background on the Swing contentPane to avoid any white flash
            val bgRgb = if (currentTheme == AppTheme.LIGHT) java.awt.Color(0xF6, 0xF6, 0xF8) else java.awt.Color(0x1E, 0x1E, 0x24)
            dialog.contentPane.background = bgRgb

            dialog.setContent {
                ClapiTheme(appTheme = currentTheme, language = currentLanguage) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = ClapiColors.Background,
                    ) {
                        CrashReportContent(
                            throwable = throwable,
                            onRestart = {
                                dialog.dispose()
                                restartApplication()
                            },
                        )
                    }
                }
            }
            dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    isDialogShowing.set(false)
                }
            })
            dialog.isVisible = true
        } catch (dialogException: Throwable) {
            isDialogShowing.set(false)
            dialogException.printStackTrace()
            try {
                javax.swing.JOptionPane.showMessageDialog(
                    owner,
                    "An unexpected error occurred in Clapi:\n\n${throwable.message ?: throwable::class.simpleName}\n\nFull details were logged to the Clapi log directory.",
                    "Application Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE,
                )
            } catch (_: Throwable) {
                // Ignore fallback dialog failures
            }
        }
    }
}
