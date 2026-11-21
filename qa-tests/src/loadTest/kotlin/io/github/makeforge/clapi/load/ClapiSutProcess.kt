package io.github.makeforge.clapi.load

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Manages an out-of-process Clapi Desktop fat JAR instance launched with production ZGC flags,
 * Native Memory Tracking (NMT), Java Flight Recorder (JFR), and the headless embedded MCP HTTP server.
 */
class ClapiSutProcess(
    private val jdkHome: File,
    private val fatJar: File,
    private val workspaceDir: File,
    private val jfrOutputFile: File,
    private val logOutputFile: File,
) {
    private var process: Process? = null
    private var outputPumpThread: Thread? = null

    var pid: Long = -1L
        private set

    var mcpUrl: String = ""
        private set

    fun start(startupTimeoutSeconds: Long = 60L) {
        require(fatJar.exists()) { "Clapi fat JAR not found at ${fatJar.absolutePath}. Run :ui-desktop:packageClapiJar first." }
        workspaceDir.mkdirs()
        jfrOutputFile.parentFile?.mkdirs()
        logOutputFile.parentFile?.mkdirs()

        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val javaBin = File(jdkHome, if (isWin) "bin/java.exe" else "bin/java")
        require(javaBin.exists()) { "JDK 25 java binary not found at ${javaBin.absolutePath}" }

        // Production JVM flags from ui-desktop/build.gradle.kts + NMT & JFR instrumentation
        val cmd = listOf(
            javaBin.absolutePath,
            "-Xms64m",
            "-Xmx2g",
            "-XX:SoftMaxHeapSize=192m",
            "-XX:+UseZGC",
            "-XX:ZUncommitDelay=5",
            "-XX:+UseStringDeduplication",
            "-Xss256k",
            "-XX:MaxMetaspaceSize=128m",
            "-XX:ReservedCodeCacheSize=96m",
            "-XX:MaxDirectMemorySize=256m",
            "-XX:NativeMemoryTracking=detail",
            "-XX:StartFlightRecording=name=clapi-load,settings=profile,dumponexit=true,filename=${jfrOutputFile.absolutePath}",
            "-Djava.util.prefs.userRoot=${File(workspaceDir, "prefs").absolutePath}",
            "-jar",
            fatJar.absolutePath,
            "--mcp-http-headless",
            "--mcp-port=0",
            "--dir",
            workspaceDir.absolutePath,
        )

        val procBuilder = ProcessBuilder(cmd)
            .redirectErrorStream(true)
        // Limit glibc per-thread malloc arenas on Linux so native memory doesn't fragment across dozens of arenas.
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            procBuilder.environment().putIfAbsent("MALLOC_ARENA_MAX", "2")
        }
        val proc = procBuilder.start()
        process = proc
        pid = proc.pid()

        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val startupLines = mutableListOf<String>()
        val logWriter = logOutputFile.bufferedWriter()
        val readyRegex = Regex("""CLAPI_MCP_READY\s+url=(\S+)\s+pid=(\d+)""")

        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(startupTimeoutSeconds)
        var readyFound = false

        while (System.nanoTime() < deadlineNanos) {
            if (!proc.isAlive) {
                while (reader.ready()) {
                    val line = reader.readLine() ?: break
                    startupLines += line
                    logWriter.appendLine(line)
                }
                logWriter.flush()
                logWriter.close()
                error(
                    "Clapi SUT exited prematurely (code=${proc.exitValue()}) before ready.\n" +
                        startupLines.takeLast(40).joinToString("\n")
                )
            }
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                startupLines += line
                logWriter.appendLine(line)
                logWriter.flush()
                val match = readyRegex.find(line)
                if (match != null) {
                    mcpUrl = match.groupValues[1]
                    pid = match.groupValues[2].toLong()
                    readyFound = true
                    break
                }
            } else {
                Thread.sleep(50)
            }
        }

        if (!readyFound) {
            proc.destroyForcibly()
            logWriter.close()
            error(
                "Timed out waiting for CLAPI_MCP_READY after ${startupTimeoutSeconds}s.\n" +
                    startupLines.takeLast(40).joinToString("\n")
            )
        }

        // Continue draining stdout/stderr into sut.log in the background so the pipe never blocks.
        outputPumpThread = Thread({
            runCatching {
                logWriter.use { writer ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        writer.appendLine(line)
                        writer.flush()
                    }
                }
            }
        }, "clapi-sut-log-pump").apply {
            isDaemon = true
            start()
        }
    }

    fun stopGracefully(timeoutSeconds: Long = 20L) {
        val proc = process ?: return
        process = null
        // Closing stdin signals --mcp-http-headless to exit cleanly (flushing JFR dumponexit=true).
        runCatching { proc.outputStream.close() }
        if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            proc.destroy()
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
            }
        }
        outputPumpThread?.join(2_000)
        outputPumpThread = null
    }
}
