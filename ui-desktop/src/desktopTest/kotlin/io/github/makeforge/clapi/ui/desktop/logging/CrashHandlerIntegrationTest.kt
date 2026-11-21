package io.github.makeforge.clapi.ui.desktop.logging

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class CrashHandlerIntegrationTest {

    private lateinit var testDir: File
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setup() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        GlobalCrashHandler.showDialogOnException = false
        testDir = File(System.getProperty("java.io.tmpdir"), "clapi-test-logs")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        GlobalCrashHandler.showDialogOnException = true
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun testUncaughtExceptionIsCapturedInLogs() {
        val logDir = LogDirectoryConfigurator.configure(arrayOf("--dir", testDir.absolutePath))
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue(handler != null, "Default uncaught exception handler should be registered")

        val marker = "SYNTHETIC_CRASH_EXCEPTION_" + System.currentTimeMillis()
        val dummyThread = Thread({ }, "SyntheticWorkerThread")
        val exception = IllegalStateException(marker)

        // Invoke the handler directly to simulate an unhandled thread exception
        handler.uncaughtException(dummyThread, exception)
        Thread.sleep(300)
        ClapiRollingFileWriter.flushCurrent()

        val effectiveLogDir = File(System.getProperty("clapi.log.dir") ?: logDir.absolutePath)
        val logFiles = effectiveLogDir.listFiles { _, name -> name.startsWith("clapi") } ?: emptyArray()
        val foundException = logFiles.any { file ->
            runCatching { file.readText().contains(marker) }.getOrDefault(false)
        }
        assertTrue(foundException, "Uncaught exception marker should be recorded in persistent log file")
    }
}
