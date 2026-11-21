package io.github.makeforge.clapi.ui.shared.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CrashReportDialogTest {

    @Test
    fun testGetOperatingSystemString_returnsNonEmptyOsDetails() {
        val os = getOperatingSystemString()
        assertTrue(os.isNotBlank(), "Operating system string should not be blank")
        val osName = System.getProperty("os.name")
        if (!osName.isNullOrBlank()) {
            assertTrue(os.contains(osName), "Operating system string should contain os.name")
        }
    }

    @Test
    fun testBuildGitHubIssueUrl_includesAllTemplateFields() {
        val exception = IllegalStateException("Test crash message")
        val stackTrace = exception.stackTraceToString()

        val url = buildGitHubIssueUrl(
            stackTrace = stackTrace,
            version = "1.2.3",
            operatingSystem = "TestOS 42",
            title = "Crash: Test crash message",
            whatHappened = "Something went wrong during testing",
            repository = "MakeForge/Clapi",
            maxUrlLength = 5000,
        )

        assertTrue(url.startsWith("https://github.com/MakeForge/Clapi/issues/new?"))
        assertTrue(url.contains("labels=bug"))
        assertTrue(url.contains("template=bug_report.yml"))
        assertTrue(url.contains("&version=1.2.3"))
        assertTrue(url.contains("&operating-system=TestOS+42"))
        assertTrue(url.contains("&title=Crash%3A+Test+crash+message"))
        assertTrue(url.contains("&what-happened=Something+went+wrong+during+testing"))
        assertTrue(url.contains("&logs="))
        assertTrue(url.contains("IllegalStateException"))
    }

    @Test
    fun testBuildGitHubIssueUrl_truncatesLogsToFitMaxUrlLength() {
        val hugeStackTrace = buildString {
            appendLine("java.lang.RuntimeException: Massive crash")
            repeat(300) { i ->
                appendLine("    at com.example.MyClass.method$i(MyClass.kt:${100 + i})")
            }
        }
        assertTrue(hugeStackTrace.length > 5000)

        val maxUrlLength = 2000
        val url = buildGitHubIssueUrl(
            stackTrace = hugeStackTrace,
            version = "1.0.0",
            operatingSystem = "Linux",
            maxUrlLength = maxUrlLength,
            truncateLogsToFit = true,
        )

        assertTrue(url.length <= maxUrlLength, "URL length ${url.length} should be <= $maxUrlLength")
        assertTrue(url.contains("truncated"), "URL should indicate that logs were truncated")
    }

    @Test
    fun testBuildGitHubIssueUrl_throwsWhenTruncateDisabledAndExceedsLimit() {
        val hugeStackTrace = "A".repeat(3000)

        assertFailsWith<IllegalArgumentException> {
            buildGitHubIssueUrl(
                stackTrace = hugeStackTrace,
                maxUrlLength = 1000,
                truncateLogsToFit = false,
            )
        }
    }

    @Test
    fun testFormatCrashDetails_includesAllDiagnosticSections() {
        val exception = IllegalArgumentException("Invalid state in test")
        val formatted = formatCrashDetails(
            throwable = exception,
            version = "2.0.0",
            operatingSystem = "Ubuntu Linux 24.04",
        )

        assertTrue(formatted.contains("Clapi Version: 2.0.0"))
        assertTrue(formatted.contains("Operating System: Ubuntu Linux 24.04"))
        assertTrue(formatted.contains("Exception: java.lang.IllegalArgumentException: Invalid state in test"))
        assertTrue(formatted.contains("Stack Trace:"))
        assertTrue(formatted.contains("IllegalArgumentException"))
    }
}
