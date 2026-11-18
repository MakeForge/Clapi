package io.github.makeforge.clapi.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import io.github.makeforge.clapi.ui.desktop.logging.GlobalCrashHandler
import io.github.makeforge.clapi.ui.shared.components.CrashReportDialog
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Frame
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class GlobalCrashHandlingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        GlobalCrashHandler.showDialogOnException = false
    }

    @After
    fun tearDown() {
        GlobalCrashHandler.showDialogOnException = true
        GlobalCrashHandler.unregisterListener()
    }

    @Test
    fun crashDialog_rendersDetailsAndTriggersActions() {
        val testException = IllegalStateException("Simulated unhandled exception for UI test")
        var dismissed = false
        var copiedDetails: String? = null
        var reportedUrl: String? = null

        composeRule.setContent {
            CrashReportDialog(
                throwable = testException,
                appVersion = "1.0.0-test",
                operatingSystem = "Linux TestArch",
                onDismiss = { dismissed = true },
                onCopyDetails = { copiedDetails = it },
                onReportBug = { reportedUrl = it },
            )
        }

        // Verify dialog components are displayed
        composeRule.onNodeWithTag("crash-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("crash-dialog-message")
            .assertIsDisplayed()
            .assertTextContains("Simulated unhandled exception for UI test")
        composeRule.onNodeWithTag("crash-dialog-stacktrace").assertIsDisplayed()

        // Test Copy Details action
        composeRule.onNodeWithTag("crash-dialog-copy-button").performClick()
        composeRule.waitForIdle()
        assertNotNull(copiedDetails, "Copy action should pass details string")
        assertTrue(copiedDetails!!.contains("1.0.0-test"))
        assertTrue(copiedDetails!!.contains("Simulated unhandled exception for UI test"))
        composeRule.onNodeWithTag("crash-dialog-copy-button").assertTextContains("Copied!")

        // Test Report on GitHub action
        composeRule.onNodeWithTag("crash-dialog-report-button").performClick()
        composeRule.waitForIdle()
        assertNotNull(reportedUrl, "Report action should build and pass bug report URL")
        assertTrue(reportedUrl!!.startsWith("https://github.com/MakeForge/Clapi/issues/new?"))
        assertTrue(reportedUrl!!.contains("template=bug_report.yml"))
        assertTrue(reportedUrl!!.contains("version=1.0.0-test"))
        assertTrue(reportedUrl!!.contains("operating-system=Linux+TestArch"))
        assertTrue(reportedUrl!!.contains("logs="))

        // Test Restart action button
        composeRule.onNodeWithTag("crash-dialog-restart-button")
            .assertIsDisplayed()
            .assertTextContains("Restart")
            .performClick()
        composeRule.waitForIdle()
        assertTrue(dismissed, "Restart button should trigger onRestart / onDismiss")
    }

    @Test
    fun unexpectedError_handledByWindowExceptionHandlerFactory_showsDialog() {
        var caughtThrowable by mutableStateOf<Throwable?>(null)

        val factory = object : WindowExceptionHandlerFactory {
            override fun exceptionHandler(window: java.awt.Window): WindowExceptionHandler {
                return WindowExceptionHandler { throwable ->
                    caughtThrowable = throwable
                }
            }
        }
        val handler = factory.exceptionHandler(Frame())

        composeRule.setContent {
            Box {
                Text("App Content Running")
                caughtThrowable?.let { throwable ->
                    CrashReportDialog(
                        throwable = throwable,
                        onDismiss = { caughtThrowable = null },
                    )
                }
            }
        }

        // Initially no crash dialog is shown
        composeRule.onNodeWithTag("crash-dialog").assertDoesNotExist()

        // Simulate an unexpected window exception intercepted by the handler
        val exception = RuntimeException("Fatal error during window event processing")
        handler.onException(exception)
        composeRule.waitForIdle()

        // Verify that the crash dialog is now displayed with the exception details
        composeRule.onNodeWithTag("crash-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("crash-dialog-message")
            .assertIsDisplayed()
            .assertTextContains("Fatal error during window event processing")

        // Dismiss the crash dialog
        composeRule.onNodeWithTag("crash-dialog-restart-button").performClick()
        composeRule.waitForIdle()

        // Verify dialog is dismissed
        composeRule.onNodeWithTag("crash-dialog").assertDoesNotExist()
        assertNull(caughtThrowable)
    }

    @Test
    fun unexpectedError_caughtByGlobalCrashHandler_showsDialog() {
        var caughtThrowable by mutableStateOf<Throwable?>(null)

        composeRule.setContent {
            DisposableEffect(Unit) {
                GlobalCrashHandler.registerListener { throwable ->
                    caughtThrowable = throwable
                }
                onDispose {
                    GlobalCrashHandler.unregisterListener()
                }
            }

            Box {
                Text("Main App Screen")
                caughtThrowable?.let { throwable ->
                    CrashReportDialog(
                        throwable = throwable,
                        onDismiss = { caughtThrowable = null },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("crash-dialog").assertDoesNotExist()

        // Trigger unexpected crash via GlobalCrashHandler on EDT
        javax.swing.SwingUtilities.invokeAndWait {
            GlobalCrashHandler.handleException(IllegalStateException("Simulated global runtime crash"))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("crash-dialog-message")
            .assertIsDisplayed()
            .assertTextContains("Simulated global runtime crash")

        // Dismiss dialog
        composeRule.onNodeWithTag("crash-dialog-restart-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash-dialog").assertDoesNotExist()
        assertNull(caughtThrowable)
    }

    @Test
    fun globalCrashHandler_routesExceptionsToListener() {
        var listenerInvoked: Throwable? = null
        GlobalCrashHandler.registerListener { throwable ->
            listenerInvoked = throwable
        }

        try {
            val exception = IllegalStateException("Direct GlobalCrashHandler dispatch test")
            GlobalCrashHandler.handleException(exception, threadName = "UnitTestThread")

            composeRule.waitForIdle()
            assertNotNull(listenerInvoked, "Registered listener should receive unhandled exception")
            assertEquals("Direct GlobalCrashHandler dispatch test", listenerInvoked?.message)
        } finally {
            GlobalCrashHandler.unregisterListener()
        }
    }
}
