package io.github.makeforge.clapi.load

import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class AllocationHotspot(
    val name: String,
    val allocatedMb: Double,
    val percentage: Double,
    val sampleCount: Int,
)

@Serializable
data class JfrAllocationReport(
    val totalSampledAllocationMb: Double,
    val sampleCount: Int,
    val topAllocatedClasses: List<AllocationHotspot>,
    val topClapiCallSites: List<AllocationHotspot>,
    val zgcUncommitEvents: Int,
    val zgcTotalUncommittedMb: Double,
    val gcCount: Int = 0,
    val allocationRateGcCount: Int = 0,
    val gcPauseCount: Int = 0,
    val totalGcPauseMs: Double = 0.0,
    val maxGcPauseMs: Double = 0.0,
)

object JfrAllocationAnalyzer {

    private val OBJECT_CLASS_REGEX = Regex("""objectClass\s*=\s*([^\n(]+)""")
    private val WEIGHT_REGEX = Regex("""weight\s*=\s*([\d.]+)\s*([kMG]?B)""")
    private val STACK_FRAME_REGEX = Regex("""^\s+([a-zA-Z0-9_$.<>]+\([^)]*\))""", RegexOption.MULTILINE)
    private val UNCOMMIT_BYTES_REGEX = Regex("""uncommitted\s*=\s*([\d.]+)\s*([kMG]?B)""")
    private val GC_CAUSE_REGEX = Regex("""cause\s*=\s*"([^"]+)"""")
    private val DURATION_REGEX = Regex("""duration\s*=\s*([\d.]+)\s*(ns|us|µs|ms|s)\b""")

    fun analyze(jdkHome: File, jfrFile: File): JfrAllocationReport? {
        if (!jfrFile.exists() || jfrFile.length() == 0L) return null
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val jfrBin = File(jdkHome, if (isWin) "bin/jfr.exe" else "bin/jfr")
        if (!jfrBin.exists()) return null

        val allocOutput = runJfr(
            jfrBin,
            "print",
            "--events",
            "jdk.ObjectAllocationSample",
            "--stack-depth",
            "16",
            jfrFile.absolutePath,
        ) ?: return null

        val uncommitOutput = runJfr(
            jfrBin,
            "print",
            "--events",
            "jdk.ZUncommit",
            jfrFile.absolutePath,
        ).orEmpty()

        val gcOutput = runJfr(
            jfrBin,
            "print",
            "--events",
            "jdk.GarbageCollection",
            jfrFile.absolutePath,
        ).orEmpty()

        val gcPauseOutput = runJfr(
            jfrBin,
            "print",
            "--events",
            "jdk.GCPhasePause",
            jfrFile.absolutePath,
        ).orEmpty()

        return parse(allocOutput, uncommitOutput, gcOutput, gcPauseOutput)
    }

    private fun runJfr(jfrBin: File, vararg args: String): String? {
        return runCatching {
            val proc = ProcessBuilder(listOf(jfrBin.absolutePath) + args)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(60, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            if (proc.exitValue() != 0) return null
            out
        }.getOrNull()
    }

    private fun parseSizeBytes(value: String, unit: String): Long {
        val v = value.toDoubleOrNull() ?: return 0L
        val mult = when (unit) {
            "B" -> 1.0
            "kB" -> 1024.0
            "MB" -> 1024.0 * 1024.0
            "GB" -> 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (v * mult).toLong()
    }

    private fun parseDurationMs(value: String, unit: String): Double {
        val v = value.toDoubleOrNull() ?: return 0.0
        return when (unit) {
            "ns" -> v / 1_000_000.0
            "us", "µs" -> v / 1_000.0
            "ms" -> v
            "s" -> v * 1_000.0
            else -> 0.0
        }
    }

    private fun parse(
        allocOutput: String,
        uncommitOutput: String,
        gcOutput: String,
        gcPauseOutput: String,
    ): JfrAllocationReport {
        val blocks = allocOutput.split("jdk.ObjectAllocationSample {")
        val byClassBytes = mutableMapOf<String, Long>()
        val byClassCount = mutableMapOf<String, Int>()
        val bySiteBytes = mutableMapOf<String, Long>()
        val bySiteCount = mutableMapOf<String, Int>()

        var totalBytes = 0L
        var totalSamples = 0

        for (i in 1 until blocks.size) {
            val block = blocks[i]
            val clsMatch = OBJECT_CLASS_REGEX.find(block) ?: continue
            val weightMatch = WEIGHT_REGEX.find(block) ?: continue

            val cls = clsMatch.groupValues[1].trim()
            val bytes = parseSizeBytes(weightMatch.groupValues[1], weightMatch.groupValues[2])
            if (bytes <= 0L) continue

            totalBytes += bytes
            totalSamples++
            byClassBytes[cls] = (byClassBytes[cls] ?: 0L) + bytes
            byClassCount[cls] = (byClassCount[cls] ?: 0) + 1

            val frames = STACK_FRAME_REGEX.findAll(block).map { it.groupValues[1] }.toList()
            val site = frames.firstOrNull { it.contains("io.github.makeforge.clapi.") }
                ?: frames.firstOrNull {
                    !it.startsWith("java.") && !it.startsWith("jdk.") && !it.startsWith("kotlin.")
                }
                ?: frames.firstOrNull()
                ?: "unknown"

            bySiteBytes[site] = (bySiteBytes[site] ?: 0L) + bytes
            bySiteCount[site] = (bySiteCount[site] ?: 0) + 1
        }

        val denom = totalBytes.coerceAtLeast(1L).toDouble()
        val topClasses = byClassBytes.entries
            .sortedByDescending { it.value }
            .take(12)
            .map { (cls, bytes) ->
                AllocationHotspot(
                    name = cls,
                    allocatedMb = bytes / (1024.0 * 1024.0),
                    percentage = (bytes / denom) * 100.0,
                    sampleCount = byClassCount[cls] ?: 0,
                )
            }

        val topSites = bySiteBytes.entries
            .sortedByDescending { it.value }
            .take(15)
            .map { (site, bytes) ->
                AllocationHotspot(
                    name = site,
                    allocatedMb = bytes / (1024.0 * 1024.0),
                    percentage = (bytes / denom) * 100.0,
                    sampleCount = bySiteCount[site] ?: 0,
                )
            }

        var uncommitEvents = 0
        var uncommittedBytes = 0L
        for (m in UNCOMMIT_BYTES_REGEX.findAll(uncommitOutput)) {
            uncommitEvents++
            uncommittedBytes += parseSizeBytes(m.groupValues[1], m.groupValues[2])
        }

        var gcCount = 0
        var allocationRateGcCount = 0
        for (m in GC_CAUSE_REGEX.findAll(gcOutput)) {
            gcCount++
            if (m.groupValues[1].contains("Allocation Rate", ignoreCase = true)) {
                allocationRateGcCount++
            }
        }

        var gcPauseCount = 0
        var totalGcPauseMs = 0.0
        var maxGcPauseMs = 0.0
        for (m in DURATION_REGEX.findAll(gcPauseOutput)) {
            gcPauseCount++
            val ms = parseDurationMs(m.groupValues[1], m.groupValues[2])
            totalGcPauseMs += ms
            if (ms > maxGcPauseMs) maxGcPauseMs = ms
        }

        return JfrAllocationReport(
            totalSampledAllocationMb = totalBytes / (1024.0 * 1024.0),
            sampleCount = totalSamples,
            topAllocatedClasses = topClasses,
            topClapiCallSites = topSites,
            zgcUncommitEvents = uncommitEvents,
            zgcTotalUncommittedMb = uncommittedBytes / (1024.0 * 1024.0),
            gcCount = gcCount,
            allocationRateGcCount = allocationRateGcCount,
            gcPauseCount = gcPauseCount,
            totalGcPauseMs = totalGcPauseMs,
            maxGcPauseMs = maxGcPauseMs,
        )
    }
}
