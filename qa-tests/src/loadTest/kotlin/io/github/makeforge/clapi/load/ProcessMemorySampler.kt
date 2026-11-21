package io.github.makeforge.clapi.load

import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class OsMemoryMetrics(
    val rssMb: Double,
    val pssMb: Double?,
    val hwmMb: Double?,
    val rssAnonMb: Double?,
    val rssFileMb: Double?,
)

@Serializable
data class SmapsBucket(
    val name: String,
    val rssMb: Double,
    val pssMb: Double,
    val mappings: Int,
)

@Serializable
data class NmtCategory(
    val name: String,
    val reservedMb: Double,
    val committedMb: Double,
)

@Serializable
data class NmtSummary(
    val totalReservedMb: Double,
    val totalCommittedMb: Double,
    val metadataCommittedMb: Double,
    val categories: List<NmtCategory>,
) {
    fun committedFor(categoryName: String): Double =
        categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.committedMb ?: 0.0

    /** Full Metaspace = top-level Metaspace (JDK 25+) + Class space (or Metadata sub-block on older JDKs). */
    val totalMetaspaceCommittedMb: Double
        get() {
            val topLevelMetaspace = committedFor("Metaspace")
            val classSpace = committedFor("Class")
            return if (topLevelMetaspace > 0.0) {
                topLevelMetaspace + classSpace
            } else {
                classSpace + metadataCommittedMb
            }
        }

    /** Non-heap JVM NMT committed memory (everything tracked by NMT except Java Heap). */
    val nonHeapCommittedMb: Double
        get() = (totalCommittedMb - committedFor("Java Heap")).coerceAtLeast(0.0)

    /** Memory consumed by JFR Tracing + NMT itself (not present in production). */
    val profilerOverheadCommittedMb: Double
        get() = committedFor("Tracing") + committedFor("Native Memory Tracking")
}

@Serializable
data class HistogramEntry(
    val className: String,
    val instances: Long,
    val bytes: Long,
) {
    val mb: Double get() = bytes / (1024.0 * 1024.0)
}

@Serializable
data class PackageMemorySummary(
    val area: String,
    val instances: Long,
    val bytes: Long,
) {
    val mb: Double get() = bytes / (1024.0 * 1024.0)
}

@Serializable
data class ThreadSummary(
    val totalThreads: Int,
    val deadlocksDetected: Boolean,
    val pools: Map<String, Int>,
)

@Serializable
data class MemorySnapshot(
    val label: String,
    val timestampEpochMs: Long,
    val os: OsMemoryMetrics,
    val smaps: List<SmapsBucket>,
    val nmt: NmtSummary?,
    val unaccountedNativeMb: Double?,
    val heapLiveBytes: Long,
    val topClasses: List<HistogramEntry>,
    val heapByArea: List<PackageMemorySummary>,
    val threads: ThreadSummary,
) {
    val heapLiveMb: Double get() = heapLiveBytes / (1024.0 * 1024.0)
}

class ProcessMemorySampler(
    private val jdkHome: File,
    private val pid: Long,
) {
    private val jcmdBinary: File = resolveTool("jcmd")

    private fun resolveTool(name: String): File {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val exe = if (isWin) "$name.exe" else name
        val candidate = File(jdkHome, "bin/$exe")
        require(candidate.exists()) { "JDK tool not found at ${candidate.absolutePath}" }
        return candidate
    }

    fun establishNmtBaseline() {
        runCatching { runJcmd("VM.native_memory", "baseline") }
    }

    fun triggerGc() {
        runJcmd("GC.run")
    }

    fun dumpHeap(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        runJcmd("GC.heap_dump", outputFile.absolutePath)
    }

    fun captureNmtDiff(): String =
        runCatching { runJcmd("VM.native_memory", "summary.diff", "scale=KB") }
            .getOrElse { "NMT diff unavailable: ${it.message}" }

    fun captureSnapshot(
        label: String,
        triggerGcBeforeHistogram: Boolean = false,
        saveThreadDumpTo: File? = null,
    ): MemorySnapshot {
        if (triggerGcBeforeHistogram) {
            triggerGc()
            Thread.sleep(500)
        }

        val smapsBuckets = readLinuxSmaps(pid)
        val totalPssMb = if (smapsBuckets.isNotEmpty()) smapsBuckets.sumOf { it.pssMb } else null
        val osMetrics = readOsMemory(pid, pssMbOverride = totalPssMb)
        val nmtSummary = runCatching {
            parseNmtSummary(runJcmd("VM.native_memory", "summary", "scale=KB"))
        }.getOrNull()

        val histOutput = runJcmd("GC.class_histogram")
        val (totalBytes, entries) = parseClassHistogram(histOutput)
        val byArea = groupHistogramByArea(entries)

        val threadDump = runJcmd("Thread.print")
        saveThreadDumpTo?.let { file ->
            file.parentFile?.mkdirs()
            file.writeText(threadDump)
        }
        val threads = parseThreadDump(threadDump)

        // Use resident ZGC heap PSS (from /proc/<pid>/smaps) when available so non-resident committed
        // ZGC pages don't skew the native off-heap delta or create artificial peak-vs-cooldown jumps.
        val effectiveOsMb = osMetrics.pssMb ?: osMetrics.rssMb
        val physicalJavaHeapMb = smapsBuckets.firstOrNull { it.name.startsWith("ZGC Java Heap") }?.pssMb
        val unaccountedNative = nmtSummary?.let { nmt ->
            if (physicalJavaHeapMb != null) {
                effectiveOsMb - physicalJavaHeapMb - nmt.nonHeapCommittedMb
            } else {
                effectiveOsMb - nmt.totalCommittedMb
            }
        }

        return MemorySnapshot(
            label = label,
            timestampEpochMs = System.currentTimeMillis(),
            os = osMetrics,
            smaps = smapsBuckets,
            nmt = nmtSummary,
            unaccountedNativeMb = unaccountedNative,
            heapLiveBytes = totalBytes,
            topClasses = entries.take(20),
            heapByArea = byArea,
            threads = threads,
        )
    }

    fun runJcmd(vararg args: String): String {
        val cmd = buildList {
            add(jcmdBinary.absolutePath)
            add(pid.toString())
            addAll(args)
        }
        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        val finished = proc.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            error("jcmd ${args.joinToString(" ")} timed out after 60s")
        }
        if (proc.exitValue() != 0) {
            error("jcmd ${args.joinToString(" ")} failed (exit ${proc.exitValue()}): $output")
        }
        return output
    }

    companion object {
        private val NMT_TOTAL_REGEX =
            Regex("""Total:\s+reserved=(\d+)KB,\s+committed=(\d+)KB""")
        private val NMT_CATEGORY_REGEX =
            Regex("""^\s*-\s+([A-Za-z0-9 /._-]+?)\s+\(reserved=(\d+)KB,\s+committed=(\d+)KB\)""")
        private val NMT_METADATA_COMMITTED_REGEX =
            Regex("""\(\s*Metadata\)\s+\(\s*reserved=\d+KB,\s*committed=(\d+)KB""")
        private val SMAPS_HEADER_REGEX =
            Regex("""^[0-9a-f]+-[0-9a-f]+\s+\S+\s+[0-9a-f]+\s+\S+\s+\d+\s*(.*)$""")
        private val HISTOGRAM_ROW_REGEX =
            Regex("""^\s*\d+:\s+(\d+)\s+(\d+)\s+([^\s(]+)""")
        private val HISTOGRAM_TOTAL_REGEX =
            Regex("""^Total\s+(\d+)\s+(\d+)""")
        private val THREAD_HEADER_REGEX =
            Regex("""^"([^"]+)"\s+#\d+""")

        fun readOsMemory(pid: Long, pssMbOverride: Double? = null): OsMemoryMetrics {
            val statusFile = File("/proc/$pid/status")
            if (statusFile.exists()) {
                val lines = statusFile.readLines()
                fun parseKb(prefix: String): Double? = lines
                    .firstOrNull { it.startsWith(prefix) }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.substringBefore(" ")
                    ?.toLongOrNull()
                    ?.let { it / 1024.0 }

                val rss = parseKb("VmRSS:") ?: 0.0
                return OsMemoryMetrics(
                    rssMb = rss,
                    pssMb = pssMbOverride,
                    hwmMb = parseKb("VmHWM:"),
                    rssAnonMb = parseKb("RssAnon:"),
                    rssFileMb = parseKb("RssFile:"),
                )
            }

            // macOS / POSIX fallback
            val proc = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString())
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor(10, TimeUnit.SECONDS)
            val rssKb = out.toLongOrNull() ?: 0L
            return OsMemoryMetrics(
                rssMb = rssKb / 1024.0,
                pssMb = pssMbOverride,
                hwmMb = null,
                rssAnonMb = null,
                rssFileMb = null,
            )
        }

        fun readLinuxSmaps(pid: Long): List<SmapsBucket> {
            val smapsFile = File("/proc/$pid/smaps")
            if (!smapsFile.exists()) return emptyList()

            val rssByBucket = mutableMapOf<String, Long>()
            val pssByBucket = mutableMapOf<String, Long>()
            val countByBucket = mutableMapOf<String, Int>()

            var currentBucket: String? = null
            runCatching {
                smapsFile.useLines { lines ->
                    for (line in lines) {
                        val header = SMAPS_HEADER_REGEX.find(line)
                        if (header != null) {
                            val pathname = header.groupValues[1].trim()
                            val bucket = classifySmapsPathname(pathname)
                            currentBucket = bucket
                            countByBucket[bucket] = (countByBucket[bucket] ?: 0) + 1
                            continue
                        }
                        val bucket = currentBucket ?: continue
                        when {
                            line.startsWith("Rss:") -> {
                                val kb = line.substringAfter(":").trim().substringBefore(" ").toLongOrNull() ?: 0L
                                rssByBucket[bucket] = (rssByBucket[bucket] ?: 0L) + kb
                            }
                            line.startsWith("Pss:") -> {
                                val kb = line.substringAfter(":").trim().substringBefore(" ").toLongOrNull() ?: 0L
                                pssByBucket[bucket] = (pssByBucket[bucket] ?: 0L) + kb
                            }
                        }
                    }
                }
            }

            return countByBucket.keys
                .map { name ->
                    SmapsBucket(
                        name = name,
                        rssMb = (rssByBucket[name] ?: 0L) / 1024.0,
                        pssMb = (pssByBucket[name] ?: 0L) / 1024.0,
                        mappings = countByBucket[name] ?: 0,
                    )
                }
                .filter { it.rssMb >= 0.05 || it.pssMb >= 0.05 }
                .sortedByDescending { it.pssMb }
        }

        private fun classifySmapsPathname(pathname: String): String = when {
            pathname.contains("java_heap") -> "ZGC Java Heap (memfd:java_heap)"
            pathname == "[heap]" -> "Native glibc malloc ([heap])"
            pathname == "[stack]" || pathname.startsWith("[stack:") -> "OS Thread Stacks"
            pathname.isEmpty() || pathname.startsWith("[anon") -> "Anonymous mmap (V8 / JIT / Direct / Thread stacks)"
            pathname.contains("javet", ignoreCase = true) -> "Native lib: Javet V8 (.so)"
            pathname.contains("skiko", ignoreCase = true) -> "Native lib: Skiko/Skia (.so)"
            pathname.contains("netty", ignoreCase = true) -> "Native lib: Netty (.so)"
            pathname.contains("libjvm.so") || pathname.contains("/lib/server/") || pathname.contains("/jdk") || pathname.contains("/java/") -> "JVM & JDK binaries/libs"
            pathname.endsWith(".jar") || pathname.endsWith(".jsa") -> "Mapped JARs & CDS archives"
            pathname.endsWith(".so") || pathname.contains(".so.") -> "Other system shared libs (.so)"
            else -> "Other mapped files"
        }

        fun parseNmtSummary(output: String): NmtSummary {
            val totalMatch = NMT_TOTAL_REGEX.find(output)
            val totalReservedKb = totalMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val totalCommittedKb = totalMatch?.groupValues?.get(2)?.toLongOrNull() ?: 0L
            val metadataCommittedKb = NMT_METADATA_COMMITTED_REGEX.find(output)
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            val categories = output.lineSequence()
                .mapNotNull { line ->
                    val match = NMT_CATEGORY_REGEX.find(line) ?: return@mapNotNull null
                    NmtCategory(
                        name = match.groupValues[1].trim(),
                        reservedMb = match.groupValues[2].toLong() / 1024.0,
                        committedMb = match.groupValues[3].toLong() / 1024.0,
                    )
                }
                .toList()

            return NmtSummary(
                totalReservedMb = totalReservedKb / 1024.0,
                totalCommittedMb = totalCommittedKb / 1024.0,
                metadataCommittedMb = metadataCommittedKb / 1024.0,
                categories = categories,
            )
        }

        fun parseClassHistogram(output: String): Pair<Long, List<HistogramEntry>> {
            val entries = mutableListOf<HistogramEntry>()
            var totalBytes = 0L

            for (line in output.lineSequence()) {
                val row = HISTOGRAM_ROW_REGEX.find(line)
                if (row != null) {
                    val instances = row.groupValues[1].toLong()
                    val bytes = row.groupValues[2].toLong()
                    val className = row.groupValues[3]
                    entries += HistogramEntry(className, instances, bytes)
                    continue
                }
                val total = HISTOGRAM_TOTAL_REGEX.find(line)
                if (total != null) {
                    totalBytes = total.groupValues[2].toLong()
                }
            }

            if (totalBytes == 0L) {
                totalBytes = entries.sumOf { it.bytes }
            }
            return totalBytes to entries
        }

        fun groupHistogramByArea(entries: List<HistogramEntry>): List<PackageMemorySummary> {
            val buckets = linkedMapOf<String, Pair<Long, Long>>()
            for (entry in entries) {
                val area = classifyClassName(entry.className)
                val (prevInst, prevBytes) = buckets[area] ?: (0L to 0L)
                buckets[area] = (prevInst + entry.instances) to (prevBytes + entry.bytes)
            }
            return buckets.entries
                .map { (area, pair) -> PackageMemorySummary(area, pair.first, pair.second) }
                .sortedByDescending { it.bytes }
        }

        private fun classifyClassName(rawName: String): String {
            val name = rawName.removePrefix("[L").removeSuffix(";")
            return when {
                rawName == "[B" -> "byte[] (strings / buffers / payloads)"
                rawName == "[C" -> "char[]"
                rawName == "[I" || rawName == "[J" -> "int[] / long[]"
                name == "java.lang.String" -> "java.lang.String"
                name.startsWith("io.github.makeforge.clapi.mcp.") -> "clapi.mcp-server"
                name.startsWith("io.github.makeforge.clapi.ui.") -> "clapi.ui-shared / desktop"
                name.startsWith("io.github.makeforge.clapi.core.network.") -> "clapi.core-network"
                name.startsWith("io.github.makeforge.clapi.core.storage.") -> "clapi.core-storage"
                name.startsWith("io.github.makeforge.clapi.core.scripting.") -> "clapi.core-scripting"
                name.startsWith("io.github.makeforge.clapi.core.model.") -> "clapi.core-model"
                name.startsWith("io.github.makeforge.clapi.editor.") -> "clapi.editor"
                name.startsWith("com.caoccao.javet.") -> "javet (V8 bridge)"
                name.startsWith("io.netty.") || name.startsWith("io.grpc.") -> "netty / grpc"
                name.startsWith("io.ktor.") -> "ktor"
                name.startsWith("kotlinx.serialization.") -> "kotlinx.serialization"
                name.startsWith("kotlinx.coroutines.") -> "kotlinx.coroutines"
                name.startsWith("com.google.protobuf.") -> "protobuf"
                name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.") -> "jdk / stdlib"
                else -> "other"
            }
        }

        fun parseThreadDump(output: String): ThreadSummary {
            val deadlocks = output.contains("Found one Java-level deadlock", ignoreCase = true) ||
                output.contains("Found ") && output.contains("deadlock", ignoreCase = true)

            val pools = linkedMapOf<String, Int>()
            var total = 0
            for (line in output.lineSequence()) {
                val match = THREAD_HEADER_REGEX.find(line) ?: continue
                total++
                val name = match.groupValues[1]
                val pool = classifyThreadPool(name)
                pools[pool] = (pools[pool] ?: 0) + 1
            }
            return ThreadSummary(
                totalThreads = total,
                deadlocksDetected = deadlocks,
                pools = pools.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            )
        }

        private fun classifyThreadPool(name: String): String = when {
            name.startsWith("DefaultDispatcher-worker") -> "DefaultDispatcher-worker"
            name.startsWith("ktor-") -> "ktor-*"
            name.startsWith("nioEventLoopGroup") || name.startsWith("eventLoopGroupProxy") -> "netty-eventloop"
            name.startsWith("grpc-") -> "grpc-*"
            name.startsWith("OkHttp") -> "okhttp"
            name.startsWith("GC ") || name.startsWith("Z") || name.startsWith("C1 ") || name.startsWith("C2 ") -> "jvm-gc-jit"
            name.startsWith("JFR ") -> "jvm-jfr"
            name.startsWith("AWT-") || name.startsWith("Java2D") -> "awt-ui"
            else -> "other"
        }
    }
}
