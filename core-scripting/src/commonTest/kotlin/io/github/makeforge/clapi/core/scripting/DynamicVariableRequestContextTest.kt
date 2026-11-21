package io.github.makeforge.clapi.core.scripting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DynamicVariableRequestContextTest {

    private val engine = ClapiDynamicVariableEngine()

    @Test
    fun exposes_request_url_method_headers_and_name() = runTest {
        val ctx = DynamicScriptContext(
            request = RequestContext(
                url = "https://api.example.com/v1/orders",
                method = "POST",
                headers = mapOf("X-Tenant-Id" to "acme-corp", "Content-Type" to "application/json"),
                name = "Create Order",
            )
        )
        val result = engine.executeDynamicVariable(
            script = """
                return context.request.method + ":" + context.request.url + ":" + context.request.headers["x-tenant-id"] + ":" + context.request.name;
            """.trimIndent(),
            context = ctx,
        )
        assertTrue(result.success, "Error: ${result.errorMessage}")
        assertEquals("POST:https://api.example.com/v1/orders:acme-corp:Create Order", result.returnValue)
    }

    @Test
    fun request_context_is_read_only_and_ignores_mutations() = runTest {
        val ctx = DynamicScriptContext(
            request = RequestContext(
                url = "https://api.example.com/v1/orders",
                method = "GET",
                headers = mapOf("Authorization" to "Bearer abc"),
                name = "Get Orders",
            )
        )
        val result = engine.executeDynamicVariable(
            script = """
                context.request.url = "https://malicious.example.com";
                context.request.method = "DELETE";
                context.request.headers["Authorization"] = "hacked";
                delete context.request.url;
                return context.request.method + "|" + context.request.url + "|" + context.request.headers["Authorization"];
            """.trimIndent(),
            context = ctx,
        )
        assertTrue(result.success, "Error: ${result.errorMessage}")
        assertEquals("GET|https://api.example.com/v1/orders|Bearer abc", result.returnValue)
    }
}
