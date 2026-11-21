package io.github.makeforge.clapi.core.scripting

import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.exceptions.JavetTerminatedException
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Javet V8 JS runtime for Desktop (JVM).
 *
 * Creates a fresh [V8Runtime] per evaluation for sandboxing and enforces
 * execution timeout via `V8Guard`.
 */

private const val SCRIPT_TIMEOUT_SECONDS = 5L

internal suspend fun evaluateJsOffMain(script: String): String =
    withContext(Dispatchers.Default) {
        evaluateJs(script)
    }

internal actual fun evaluateJs(script: String): String {
    return try {
        V8Host.getV8Instance().createV8Runtime<V8Runtime>().use { v8Runtime ->
            v8Runtime.getGuard(SCRIPT_TIMEOUT_SECONDS * 1000L).use { _ ->
                v8Runtime.getExecutor(script).executeString() ?: ""
            }
        }
    } catch (_: JavetTerminatedException) {
        """{"error":"Script execution timed out after ${SCRIPT_TIMEOUT_SECONDS} seconds"}"""
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
