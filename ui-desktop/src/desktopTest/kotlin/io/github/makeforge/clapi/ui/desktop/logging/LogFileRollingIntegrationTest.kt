package io.github.makeforge.clapi.ui.desktop.logging

import io.github.makeforge.clapi.core.logging.AppLogging
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.tinylog.provider.ProviderRegistry
import java.io.File
import kotlin.test.assertTrue

class LogFileRollingIntegrationTest {

    private lateinit var testDir: File

    @Before
    fun setup() {
        testDir = File(System.getProperty("java.io.tmpdir"), "clapi-test-logs")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        // Do not delete directory during test suite run as Tinylog holds background file handles
    }

    @Test
    fun testLogDirectoryConfigurationAndFileCreation() {
        val logDir = LogDirectoryConfigurator.configure(arrayOf("--dir", testDir.absolutePath))
        assertTrue(logDir.exists(), "Log directory should be created")

        val logger = AppLogging.logger("IntegrationTest")
        val uniqueMarker = "TEST_LOG_ENTRY_" + System.currentTimeMillis()
        logger.info { uniqueMarker }

        // Allow async writing thread to flush to disk
        Thread.sleep(300)

        val effectiveLogDir = File(System.getProperty("clapi.log.dir") ?: logDir.absolutePath)
        val logFiles = effectiveLogDir.listFiles { _, name -> name.startsWith("clapi") } ?: emptyArray()
        assertTrue(logFiles.isNotEmpty(), "At least one clapi log file should exist in $effectiveLogDir")

        val foundMarker = logFiles.any { file ->
            runCatching { file.readText().contains(uniqueMarker) }.getOrDefault(false)
        }
        assertTrue(foundMarker, "Expected log message with marker $uniqueMarker to be found in log files")
    }
}
