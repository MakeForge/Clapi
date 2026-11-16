package io.github.crustack.clapi.core.network

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.RequestDefinition
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorApiClientLifecycleTest {

    @Test
    fun close_terminates_underlying_HttpClient_coroutine_scope() {
        val httpClient = createPlatformHttpClient()
        assertTrue(httpClient.isActive, "HttpClient should be active before close()")

        val apiClient = KtorApiClient(httpClient = httpClient)
        apiClient.close()

        assertFalse(httpClient.isActive, "HttpClient must be inactive after KtorApiClient.close()")
    }

    @Test
    fun execute_with_websocket_url_throws_UnsupportedOperationException() = runTest {
        val apiClient = KtorApiClient()
        try {
            val wsRequest = RequestDefinition(
                id = "ws-test",
                name = "WS Stub Check",
                method = HttpMethodType.GET,
                url = "ws://localhost:8080/ws",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )

            val ex = assertFailsWith<UnsupportedOperationException> {
                apiClient.execute(wsRequest).toList()
            }
            assertTrue(ex.message!!.contains("WebSocket sessions must be executed via WebSocketClient"))
        } finally {
            apiClient.close()
        }
    }
}
