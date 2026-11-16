package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.model.AuthType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicVariableRegistryTest {

    private class CustomDynamicVariableProvider(
        override val name: String = "CustomTestProvider",
        private val prefix: String = "_custom.",
    ) : DynamicVariableProvider {
        var callCount = 0

        override fun supports(token: String): Boolean = token.startsWith(prefix)

        override suspend fun resolve(
            tokens: Set<String>,
            context: DynamicVariableContext,
        ): Map<String, String> {
            callCount++
            return tokens.associateWith { "resolved-$it" }
        }
    }

    private class FailingDynamicVariableProvider(
        override val name: String = "FailingProvider",
    ) : DynamicVariableProvider {
        override fun supports(token: String): Boolean = token == "_fail.test"

        override suspend fun resolve(
            tokens: Set<String>,
            context: DynamicVariableContext,
        ): Map<String, String> {
            throw DynamicVariableResolutionException("Simulated provider failure")
        }
    }

    @Test
    fun registers_and_unregisters_providers() {
        val registry = DynamicVariableRegistry(emptyList())
        val provider = CustomDynamicVariableProvider()

        registry.register(provider)
        assertTrue(registry.providers.contains(provider))

        registry.unregister(provider)
        assertFalse(registry.providers.contains(provider))

        registry.register(provider)
        registry.unregisterByName(provider.name)
        assertFalse(registry.providers.contains(provider))
    }

    @Test
    fun extractTokens_extracts_tokens_from_multiple_texts() {
        val registry = DynamicVariableRegistry()
        val texts = listOf(
            "https://api.example.com/{{_auth.oauth2.accessToken}}?id={{userId}}",
            "Authorization: Bearer {{ _auth.oauth2.accessToken }}",
            "Body: {{_custom.payload}} and plain text",
            "",
            "   ",
        )

        val tokens = registry.extractTokens(texts)
        assertEquals(setOf("_auth.oauth2.accessToken", "userId", "_custom.payload"), tokens)
    }

    @Test
    fun delegates_to_multiple_registered_providers() = runTest {
        val customProvider = CustomDynamicVariableProvider()
        val registry = DynamicVariableRegistry()
        registry.register(customProvider)

        val context = DynamicVariableContext(
            effectiveAuthType = AuthType.OAUTH2,
            oauth2TokenState = io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenState(
                accessToken = "valid-oauth-token",
                expiresAtEpochMs = io.github.makeforge.clapi.core.network.oauth2.currentEpochMillis() + 60_000L,
            ),
        )

        val texts = listOf(
            "{{_auth.oauth2.accessToken}}",
            "{{_custom.testVar}}",
            "{{staticVar}}",
        )

        val resolved = registry.resolveDynamicVariables(texts, context)

        assertEquals("valid-oauth-token", resolved["_auth.oauth2.accessToken"])
        assertEquals("resolved-_custom.testVar", resolved["_custom.testVar"])
        assertFalse(resolved.containsKey("staticVar"))
        assertEquals(1, customProvider.callCount)
    }

    @Test
    fun propagates_provider_exceptions() = runTest {
        val registry = DynamicVariableRegistry()
        registry.register(FailingDynamicVariableProvider())

        val context = DynamicVariableContext(effectiveAuthType = AuthType.NONE)

        val ex = assertFailsWith<DynamicVariableResolutionException> {
            registry.resolveDynamicVariables(listOf("{{_fail.test}}"), context)
        }
        assertEquals("Simulated provider failure", ex.message)
    }
}
