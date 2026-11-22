// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.scripting.ClapiScriptEngine
import io.github.makeforge.clapi.core.scripting.ScriptContext

/**
 * Executes a minimal Javet V8 script assertion to confirm native script runtime loading works
 * inside packaged release artifacts (fat JAR and jlink-trimmed native distributables).
 */
suspend fun runScriptEngineSmokeCheck() {
    val engine = ClapiScriptEngine()
    val result = engine.executeTestScript(
        script = """
            clapi.test("packaged v8 smoke", function() {
                clapi.expect(1 + 1).to.equal(2);
            });
        """.trimIndent(),
        context = ScriptContext(
            url = "http://127.0.0.1/smoke",
            method = "GET",
            statusCode = 200,
            responseBody = """{"ok":true}""",
        ),
        prefix = "clapi",
    )
    check(result.success) { "Script engine smoke check failed: ${result.error ?: result.logs}" }
    check(result.assertions.size == 1 && result.assertions.single().passed) {
        "Expected 1 passing smoke test assertion, got: ${result.assertions}"
    }
}
