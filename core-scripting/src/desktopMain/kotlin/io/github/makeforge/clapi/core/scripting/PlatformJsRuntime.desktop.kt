// Modified by MakeForge (2026)
package io.github.makeforge.clapi.core.scripting

import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.exceptions.JavetTerminatedException
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Javet V8 JS runtime for Desktop (JVM).
 *
 * All [V8Runtime] operations (creation, script evaluation, context reset, and disposal) are
 * pinned to a single dedicated OS thread ([v8Executor]) to satisfy V8's strict isolate
 * thread-affinity requirements and prevent cross-thread native crashes (`SIGSEGV`).
 * Between evaluations, `resetContext()` provides full JS global sandbox isolation; after
 * [IDLE_CLOSE_DELAY_MS] of inactivity, the isolate is closed on the same owning thread so
 * V8 releases its native heap back to the OS.
 * Large strings (such as response bodies) can be bound directly onto `globalThis` via
 * [evaluateJsWithGlobals] without escaping them into JS source literals.
 */

private const val SCRIPT_TIMEOUT_SECONDS = 5L
private const val IDLE_CLOSE_DELAY_MS = 10_000L
private const val LARGE_PAYLOAD_GC_HINT_THRESHOLD = 64_000

private object PooledV8RuntimeHolder {
    // All mutable state below is accessed exclusively on the single v8Executor thread.
    @Volatile
    private var v8Thread: Thread? = null
    private var runtime: V8Runtime? = null
    private var idleCloseTask: ScheduledFuture<*>? = null
    private var generation: Long = 0L

    private val v8Executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "clapi-v8-worker").apply {
            isDaemon = true
            v8Thread = this
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread({
            runCatching {
                v8Executor.submit {
                    idleCloseTask?.cancel(false)
                    idleCloseTask = null
                    runCatching { runtime?.close() }
                    runtime = null
                }.get(2, TimeUnit.SECONDS)
            }
            runCatching { v8Executor.shutdownNow() }
        }, "clapi-v8-shutdown"))
    }

    fun execute(
        script: String,
        timeoutMs: Long,
        globals: Map<String, String?> = emptyMap(),
    ): String {
        if (Thread.currentThread() === v8Thread) {
            return executeOnV8Thread(script, timeoutMs, globals)
        }
        return try {
            v8Executor.submit(Callable {
                executeOnV8Thread(script, timeoutMs, globals)
            }).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun executeOnV8Thread(
        script: String,
        timeoutMs: Long,
        globals: Map<String, String?>,
    ): String {
        val currentGen = ++generation
        idleCloseTask?.cancel(false)
        idleCloseTask = null

        val v8 = runtime?.takeUnless { it.isClosed }
            ?: V8Host.getV8Instance().createV8Runtime<V8Runtime>().also { runtime = it }

        val totalPayloadChars = script.length + globals.values.sumOf { it?.length ?: 0 }

        try {
            if (globals.isNotEmpty()) {
                v8.globalObject.use { globalObj ->
                    for ((key, value) in globals) {
                        if (value == null) {
                            globalObj.setNull(key)
                        } else {
                            globalObj.set(key, value)
                        }
                    }
                }
            }
            val result = v8.getGuard(timeoutMs.coerceAtLeast(1L)).use { _ ->
                v8.getExecutor(script).executeString() ?: ""
            }
            v8.resetContext()
            if (totalPayloadChars >= LARGE_PAYLOAD_GC_HINT_THRESHOLD) {
                v8.lowMemoryNotification()
            }
            scheduleIdleClose(currentGen)
            return result
        } catch (e: JavetTerminatedException) {
            // Isolate was forcefully terminated; discard and recreate on next call.
            runCatching { v8.close() }
            runtime = null
            throw e
        } catch (e: Exception) {
            // Reset context so any partial globals don't leak; recreate if reset fails.
            val resetOk = runCatching { v8.resetContext() }.isSuccess
            if (!resetOk) {
                runCatching { v8.close() }
                runtime = null
            } else {
                scheduleIdleClose(currentGen)
            }
            throw e
        }
    }

    private val mallocTrimHandle: java.lang.invoke.MethodHandle? by lazy {
        if (!System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)) {
            return@lazy null
        }
        runCatching {
            val linker = java.lang.foreign.Linker.nativeLinker()
            val sym = linker.defaultLookup().find("malloc_trim").orElse(null) ?: return@runCatching null
            linker.downcallHandle(
                sym,
                java.lang.foreign.FunctionDescriptor.of(
                    java.lang.foreign.ValueLayout.JAVA_INT,
                    java.lang.foreign.ValueLayout.JAVA_LONG,
                ),
            )
        }.getOrNull()
    }

    private fun trimNativeHeapIfLinux() {
        runCatching {
            mallocTrimHandle?.invoke(0L)
        }
    }

    private fun scheduleIdleClose(expectedGen: Long) {
        idleCloseTask = v8Executor.schedule({
            if (generation != expectedGen) return@schedule
            val current = runtime
            if (current != null && !current.isClosed) {
                runCatching { current.close() }
            }
            runtime = null
            idleCloseTask = null
            trimNativeHeapIfLinux()
        }, IDLE_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS)
    }
}

internal suspend fun evaluateJsOffMain(script: String): String =
    withContext(Dispatchers.Default) {
        evaluateJs(script)
    }

internal actual fun evaluateJs(script: String): String =
    evaluateJsWithTimeout(script, SCRIPT_TIMEOUT_SECONDS * 1000L)

internal actual fun evaluateJsWithTimeout(script: String, timeoutMs: Long): String =
    evaluateJsWithGlobals(script, emptyMap(), timeoutMs)

internal actual fun evaluateJsWithGlobals(
    script: String,
    globals: Map<String, String?>,
    timeoutMs: Long,
): String {
    return try {
        PooledV8RuntimeHolder.execute(script, timeoutMs, globals)
    } catch (_: JavetTerminatedException) {
        """{"error":"Script execution timed out after ${timeoutMs}ms"}"""
    } catch (e: JavetException) {
        val msg = (e.message ?: "Unknown JS error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        """{"error":"$msg"}"""
    } catch (e: Exception) {
        val msg = (e.message ?: "Unknown error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        """{"error":"$msg"}"""
    }
}
