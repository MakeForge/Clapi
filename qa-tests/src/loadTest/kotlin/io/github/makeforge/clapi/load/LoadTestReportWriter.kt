package io.github.makeforge.clapi.load

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@Serializable
data class ThresholdCheckResult(
    val name: String,
    val actual: String,
    val threshold: String,
    val passed: Boolean,
)

@Serializable
data class LoadTestReport(
    val profile: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Double,
    val operationsExecuted: Map<String, Int>,
    val snapshots: List<MemorySnapshot>,
    val nmtDiffPeakVsBaseline: String,
    val nmtDiffCooldownVsBaseline: String,
    val jfrAllocations: JfrAllocationReport?,
    val checks: List<ThresholdCheckResult>,
)

object LoadTestReportWriter {
    private val json = Json { prettyPrint = true }

    fun write(reportDir: File, report: LoadTestReport) {
        reportDir.mkdirs()
        File(reportDir, "metrics.json").writeText(
            json.encodeToString(LoadTestReport.serializer(), report)
        )
        File(reportDir, "summary.md").writeText(renderMarkdown(report))
    }

    private fun fmt(v: Double?): String =
        if (v == null) "n/a" else String.format(Locale.US, "%.2f MB", v)

    private fun pct(part: Double, whole: Double): String =
        if (whole <= 0.0) "0.0%" else String.format(Locale.US, "%.1f%%", (part / whole) * 100.0)

    private fun renderMarkdown(report: LoadTestReport): String = buildString {
        appendLine("# Clapi Load & Memory Profiling Report")
        appendLine()
        appendLine("- **Profile**: `${report.profile}`")
        appendLine("- **Total Duration**: ${String.format(Locale.US, "%.1f", report.durationSeconds)} s")
        appendLine("- **Operations Executed**: ${report.operationsExecuted.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        report.jfrAllocations?.let { jfr ->
            appendLine(
                "- **JFR Sampled Allocations**: ${fmt(jfr.totalSampledAllocationMb)} " +
                    "(${jfr.sampleCount} samples) | **ZGC Uncommits**: ${jfr.zgcUncommitEvents} events (${fmt(jfr.zgcTotalUncommittedMb)})"
            )
            appendLine(
                "- **JFR GC Activity**: ${jfr.gcCount} GCs (${jfr.allocationRateGcCount} Allocation Rate) | " +
                    "${jfr.gcPauseCount} pauses, total ${String.format(Locale.US, "%.2f", jfr.totalGcPauseMs)} ms, " +
                    "max ${String.format(Locale.US, "%.3f", jfr.maxGcPauseMs)} ms"
            )
        }
        appendLine()

        appendLine("## 1. Memory & Thread Timeline")
        appendLine()
        appendLine("| Phase | OS RSS | OS PSS (Physical) | Heap Live | NMT Committed | NMT Java Heap | NMT Metaspace (Full) | NMT Code | NMT Profiler Overhead | Unaccounted Native | Threads |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        for (s in report.snapshots) {
            val nmt = s.nmt
            appendLine(
                "| ${s.label} " +
                    "| ${fmt(s.os.rssMb)} " +
                    "| ${fmt(s.os.pssMb)} " +
                    "| ${fmt(s.heapLiveMb)} " +
                    "| ${fmt(nmt?.totalCommittedMb)} " +
                    "| ${fmt(nmt?.committedFor("Java Heap"))} " +
                    "| ${fmt(nmt?.totalMetaspaceCommittedMb)} " +
                    "| ${fmt(nmt?.committedFor("Code"))} " +
                    "| ${fmt(nmt?.profilerOverheadCommittedMb)} " +
                    "| ${fmt(s.unaccountedNativeMb)} " +
                    "| ${s.threads.totalThreads} |"
            )
        }
        appendLine()

        val peak = report.snapshots.firstOrNull { it.label == "peak" } ?: report.snapshots.lastOrNull()
        val cooldown = report.snapshots.firstOrNull { it.label == "cooldown" } ?: report.snapshots.lastOrNull()

        if (peak != null && peak.nmt != null) {
            val nmt = peak.nmt
            val totalOs = peak.os.pssMb ?: peak.os.rssMb
            val osLabel = if (peak.os.pssMb != null) "OS PSS (Physical RAM)" else "OS RSS"
            val javaHeapCommitted = nmt.committedFor("Java Heap")
            val physicalJavaHeap = peak.smaps.firstOrNull { it.name.startsWith("ZGC Java Heap") }?.pssMb
            val effectiveJavaHeap = physicalJavaHeap ?: javaHeapCommitted
            val liveHeap = peak.heapLiveMb
            val zgcHeadroom = (effectiveJavaHeap - liveHeap).coerceAtLeast(0.0)
            val metaspace = nmt.totalMetaspaceCommittedMb
            val code = nmt.committedFor("Code")
            val threads = nmt.committedFor("Thread")
            val profiler = nmt.profilerOverheadCommittedMb
            val otherJvm = (nmt.nonHeapCommittedMb - metaspace - code - threads - profiler).coerceAtLeast(0.0)
            val nativeOffHeap = peak.unaccountedNativeMb ?: (totalOs - effectiveJavaHeap - nmt.nonHeapCommittedMb)
            val heapHeadroomNote = if (physicalJavaHeap != null) {
                "Physical resident ZGC headroom (`memfd:java_heap` ${fmt(physicalJavaHeap)} vs NMT committed ${fmt(javaHeapCommitted)})"
            } else {
                "Committed above live set to absorb allocation bursts (see JFR hotspots below)"
            }

            appendLine("## 2. Peak Memory Composition (What Makes Up Peak RAM)")
            appendLine()
            appendLine("Total **$osLabel** at peak: **${fmt(totalOs)}** (OS VmRSS: ${fmt(peak.os.rssMb)}, VmHWM: ${fmt(peak.os.hwmMb)})")
            appendLine()
            appendLine("| Component | Peak MB | Share of $osLabel | Notes |")
            appendLine("|---|---:|---:|---|")
            appendLine("| **Live Java Heap (reachable objects)** | ${fmt(liveHeap)} | ${pct(liveHeap, totalOs)} | Actual retained objects from `GC.class_histogram` |")
            appendLine("| **ZGC Heap Headroom (Physical)** | ${fmt(zgcHeadroom)} | ${pct(zgcHeadroom, totalOs)} | $heapHeadroomNote |")
            appendLine("| **JVM Metaspace (Metaspace + Class Space)** | ${fmt(metaspace)} | ${pct(metaspace, totalOs)} | Loaded class metadata (`Metaspace` + `Class` NMT blocks) |")
            appendLine("| **JIT Code Cache** | ${fmt(code)} | ${pct(code, totalOs)} | Compiled native machine code |")
            appendLine("| **JVM Thread Stacks & Metadata** | ${fmt(threads)} | ${pct(threads, totalOs)} | ${peak.threads.totalThreads} threads |")
            appendLine("| **Other JVM NMT (Symbols, CDS, GC, Arenas, Direct)** | ${fmt(otherJvm)} | ${pct(otherJvm, totalOs)} | Shared class space, symbol table, GC structures, internal buffers |")
            appendLine("| **Profiler Overhead (JFR Tracing + NMT)** | ${fmt(profiler)} | ${pct(profiler, totalOs)} | Instrumentation only — **not present in production** |")
            appendLine("| **Off-Heap Native (V8 / glibc / .so / mmap delta)** | ${fmt(nativeOffHeap)} | ${pct(nativeOffHeap, totalOs)} | `$osLabel - Resident ZGC Heap - Non-Heap NMT` (see `/proc/<pid>/smaps` below) |")
            appendLine()
        }

        if (peak != null && peak.smaps.isNotEmpty()) {
            val cooldownSmaps = cooldown?.smaps?.associateBy { it.name }.orEmpty()
            appendLine("## 3. OS Memory Map Breakdown (`/proc/<pid>/smaps`, Peak vs Cooldown)")
            appendLine()
            appendLine("| Memory Region | Peak PSS | Peak RSS | Cooldown PSS | Cooldown RSS | Mappings |")
            appendLine("|---|---:|---:|---:|---:|---:|")
            for (b in peak.smaps) {
                val cd = cooldownSmaps[b.name]
                appendLine(
                    "| ${b.name} | ${fmt(b.pssMb)} | ${fmt(b.rssMb)} | ${fmt(cd?.pssMb ?: 0.0)} | ${fmt(cd?.rssMb ?: 0.0)} | ${b.mappings} |"
                )
            }
            appendLine()
        }

        report.jfrAllocations?.let { jfr ->
            appendLine("## 4. JFR Allocation Hotspots (What Drove ZGC Heap Expansion)")
            appendLine()
            appendLine("Total sampled allocation churn: **${fmt(jfr.totalSampledAllocationMb)}** across ${jfr.sampleCount} samples.")
            appendLine()
            appendLine("### Top Allocated Classes")
            appendLine("| Class | Sampled Allocation | Share | Samples |")
            appendLine("|---|---:|---:|---:|")
            for (c in jfr.topAllocatedClasses) {
                appendLine("| `${c.name}` | ${fmt(c.allocatedMb)} | ${String.format(Locale.US, "%.1f%%", c.percentage)} | ${c.sampleCount} |")
            }
            appendLine()
            appendLine("### Top Allocation Call Sites (Deepest Clapi / Framework Frame)")
            appendLine("| Call Site | Sampled Allocation | Share | Samples |")
            appendLine("|---|---:|---:|---:|")
            for (s in jfr.topClapiCallSites) {
                appendLine("| `${s.name}` | ${fmt(s.allocatedMb)} | ${String.format(Locale.US, "%.1f%%", s.percentage)} | ${s.sampleCount} |")
            }
            appendLine()
        }

        appendLine("## 5. Threshold Verification")
        appendLine()
        appendLine("| Check | Actual | Threshold | Status |")
        appendLine("|---|---|---|---|")
        for (c in report.checks) {
            val status = if (c.passed) "PASS" else "**FAIL**"
            appendLine("| ${c.name} | ${c.actual} | ${c.threshold} | $status |")
        }
        appendLine()

        if (peak != null) {
            appendLine("## 6. Retained Heap Breakdown by Area (Peak vs Cooldown)")
            appendLine()
            val cooldownMap = cooldown?.heapByArea?.associateBy { it.area }.orEmpty()
            appendLine("| Area | Peak MB | Peak Instances | Cooldown MB | Cooldown Instances |")
            appendLine("|---|---:|---:|---:|---:|")
            for (area in peak.heapByArea.take(15)) {
                val cd = cooldownMap[area.area]
                appendLine(
                    "| `${area.area}` | ${fmt(area.mb)} | ${area.instances} | ${fmt(cd?.mb ?: 0.0)} | ${cd?.instances ?: 0L} |"
                )
            }
            appendLine()
        }

        if (cooldown != null) {
            appendLine("## 7. Top 15 Retained Classes After Cooldown (Post-GC)")
            appendLine()
            appendLine("| Class | Retained MB | Instances |")
            appendLine("|---|---:|---:|")
            for (entry in cooldown.topClasses.take(15)) {
                appendLine("| `${entry.className}` | ${fmt(entry.mb)} | ${entry.instances} |")
            }
            appendLine()

            appendLine("## 8. Thread Pools After Cooldown")
            appendLine()
            appendLine("| Pool | Count |")
            appendLine("|---|---:|")
            for ((pool, count) in cooldown.threads.pools) {
                appendLine("| `$pool` | $count |")
            }
            appendLine()
        }

        appendLine("## 9. Artifacts")
        appendLine("- `clapi-load.jfr` — Java Flight Recorder profile (inspect with `jfr summary` or JDK Mission Control)")
        appendLine("- `jstack-peak.txt` / `jstack-cooldown.txt` — Thread dumps")
        appendLine("- `sut.log` — Clapi process stdout/stderr")
        appendLine("- `metrics.json` — Raw structured metrics")
    }
}
