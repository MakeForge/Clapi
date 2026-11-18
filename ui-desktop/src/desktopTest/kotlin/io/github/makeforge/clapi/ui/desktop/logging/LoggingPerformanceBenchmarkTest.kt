package io.github.makeforge.clapi.ui.desktop.logging

import io.github.makeforge.clapi.core.logging.AppLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class LoggingPerformanceBenchmarkTest {

    @Test
    fun testHighThroughputLoggingIsNonBlocking() = runBlocking(Dispatchers.Default) {
        val logger = AppLogging.logger("PerfBenchmark")
        val threadCount = 8
        val logsPerThread = 1_000
        val totalLogs = threadCount * logsPerThread

        val elapsedMs = measureTimeMillis {
            val jobs = List(threadCount) { threadId ->
                launch(Dispatchers.Default) {
                    for (i in 0 until logsPerThread) {
                        logger.info { "Concurrent performance message from worker-$threadId iteration $i" }
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val avgOverheadPerLogMs = elapsedMs.toDouble() / totalLogs
        println("[PERF] $totalLogs log events completed in $elapsedMs ms (avg: $avgOverheadPerLogMs ms/event)")

        assertTrue(
            avgOverheadPerLogMs < 0.1,
            "Per-call caller overhead should be < 0.1ms for async logging, got $avgOverheadPerLogMs ms"
        )
    }
}
