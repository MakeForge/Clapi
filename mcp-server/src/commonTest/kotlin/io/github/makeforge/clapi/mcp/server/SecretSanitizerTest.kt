package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.OAuth2ClientCredentials
import io.github.makeforge.clapi.core.model.opencollection.OAuth2ResourceOwner
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.mcp.server.backend.ExecuteRequestInput
import io.github.makeforge.clapi.mcp.server.backend.ExportCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseInput
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.REDACTED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.SanitizedAuthDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedKeyValueDto
import io.github.makeforge.clapi.mcp.server.backend.SetVariableInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertEnvironmentInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import io.github.makeforge.clapi.mcp.server.security.SecretSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretSanitizerTest {

    @Test
    fun `masks auth credentials and preserves stored secret when updated with asterisks`() {
        val original = AuthOAuth2(
            flow = "client_credentials",
            accessTokenUrl = "https://auth.example.com/token",
            credentials = OAuth2ClientCredentials(
                clientId = "pub-client-id",
                clientSecret = "top-secret-oauth-9999",
                placement = "basic_auth_header",
            ),
            resourceOwner = OAuth2ResourceOwner(
                username = "alice",
                password = "alice-secret-password",
            ),
            scope = "read:all",
        )

        val sanitized = SecretSanitizer.sanitizeAuth(original)
        assertEquals("OAUTH2", sanitized.type)
        assertEquals("pub-client-id", sanitized.oauth2ClientId)
        assertEquals(MASKED_SECRET_PLACEHOLDER, sanitized.oauth2ClientSecret)
        assertEquals("alice", sanitized.oauth2Username)
        assertEquals(MASKED_SECRET_PLACEHOLDER, sanitized.oauth2Password)
        assertTrue(sanitized.isConfigured)

        // Updating scope while leaving clientSecret as "********" preserves the underlying secret
        val merged = SecretSanitizer.mergeAuthUpdate(
            sanitized.copy(oauth2Scope = "read:all write:all"),
            original,
        ) as AuthOAuth2
        assertEquals("top-secret-oauth-9999", merged.credentials?.clientSecret)
        assertEquals("alice-secret-password", merged.resourceOwner?.password)
        assertEquals("read:all write:all", merged.scope)
    }

    @Test
    fun `masks sensitive headers preserving auth scheme prefix and scrubs short vs long secrets safely`() {
        val headers = listOf(
            KeyValueEntry("Authorization", "Bearer my-jwt-secret-token-123"),
            KeyValueEntry("X-Api-Key", "raw-api-key-456"),
            KeyValueEntry("Set-Cookie", "sid=cookie-secret-789"),
            KeyValueEntry("X-Custom-Header", "contains my-jwt-secret-token-123 in text"),
        )
        val sanitized = SecretSanitizer.sanitizeHeaders(
            headers = headers,
            knownSecrets = setOf("my-jwt-secret-token-123", "1"),
        )
        assertEquals("Bearer $MASKED_SECRET_PLACEHOLDER", sanitized[0].value)
        assertEquals(MASKED_SECRET_PLACEHOLDER, sanitized[1].value)
        assertEquals(MASKED_SECRET_PLACEHOLDER, sanitized[2].value)
        assertEquals("contains $REDACTED_SECRET_PLACEHOLDER in text", sanitized[3].value)

        // Short secrets (< 4 chars, e.g., "1") are NOT scrubbed from free-form text so JSON syntax is uncorrupted
        val jsonText = """{"id": 1, "tokenEcho": "my-jwt-secret-token-123"}"""
        val scrubbed = SecretSanitizer.scrubText(jsonText, setOf("my-jwt-secret-token-123", "1"))
        assertEquals("""{"id": 1, "tokenEcho": "$REDACTED_SECRET_PLACEHOLDER"}""", scrubbed)
    }

    @Test
    fun `sanitizes external secret declarations without exposing resolved secret values`() {
        val awsDecl = AwsSecretsManagerExternalSecret(
            name = "dbPassword",
            secretName = "prod/billing/rds",
            targetField = "password",
            customFilters = mapOf("stage" to "AWSCURRENT"),
        )
        val dto = SecretSanitizer.sanitizeExternalSecretDeclaration(awsDecl)
        assertEquals("dbPassword", dto.name)
        assertEquals("aws-secrets-manager", dto.providerType)
        assertEquals("prod/billing/rds", dto.path)
        assertEquals("password", dto.targetField)
    }

    @Test
    fun `end to end zero exfiltration on echo endpoint execution inspection logs and exports`() = runTest {
        val secretApiToken = "ultra-secret-production-token-8888"
        val secretBasicPass = "basic-auth-pass-7777"

        val echoClient = object : ApiClient {
            override fun execute(
                request: RequestDefinition,
                variableLayers: List<Map<String, String>>,
            ): Flow<NetworkEvent> = flow {
                val resolvedToken = VariableResolver.resolve("{{prodApiKey}}", variableLayers, removeUnresolved = false)
                // Target server echoes the resolved secret token in body and custom response header
                val echoBody = """{"echoedToken": "$resolvedToken", "basicPassEcho": "$secretBasicPass", "ok": true}"""
                emit(
                    NetworkEvent.Success(
                        ResponseDefinition(
                            requestId = request.id,
                            statusCode = 200,
                            statusText = "OK",
                            headers = listOf(
                                KeyValueEntry("Content-Type", "application/json"),
                                KeyValueEntry("X-Debug-Echo", "Received token $resolvedToken"),
                                KeyValueEntry("Set-Cookie", "session=$resolvedToken"),
                            ),
                            cookies = emptyList(),
                            bodyText = echoBody,
                            contentType = "application/json",
                            executedAtEpochMillis = 999L,
                            metrics = ResponseMetrics(statusCode = 200, responseTimeMs = 10L, responseSizeBytes = echoBody.length.toLong()),
                        )
                    )
                )
            }
        }

        val backend = HeadlessWorkspaceBackend(apiClient = echoClient)
        backend.manageCollection(ManageCollectionInput(action = "create", name = "Secure API"))
        backend.upsertEnvironment(UpsertEnvironmentInput(name = "Prod"))
        backend.setActiveEnvironment("Prod")

        // Write-only secret variable
        val setVarDto = backend.setVariable(
            SetVariableInput(
                scope = "environment",
                target = "Prod",
                key = "prodApiKey",
                kind = "secret",
                value = secretApiToken,
            )
        )
        assertEquals(MASKED_SECRET_PLACEHOLDER, setVarDto.value)
        assertTrue(setVarDto.secret)
        assertTrue(setVarDto.isSet)

        // Read environment back — secret must be masked
        val envDto = backend.getEnvironmentDetails("Prod")
        assertEquals(MASKED_SECRET_PLACEHOLDER, envDto.variables.first { it.key == "prodApiKey" }.value)

        // Create request with Basic Auth and script logging the secret
        val reqDto = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "Secure API",
                name = "Echo Secret Call",
                kind = "http",
                method = "GET",
                url = "https://api.example.com/echo",
                headers = listOf(SanitizedKeyValueDto(key = "X-Api-Key", value = "{{prodApiKey}}")),
                auth = SanitizedAuthDto(type = "BASIC", username = "admin", password = secretBasicPass),
                preRequestScript = "console.log('Sending secret: ' + clapi.environment.get('prodApiKey'));",
            )
        )
        assertEquals(MASKED_SECRET_PLACEHOLDER, reqDto.auth.password)

        // Execute request against echo endpoint
        val execResult = backend.executeRequest(ExecuteRequestInput(requestId = reqDto.id))
        assertFalse(execResult.bodyPreview.contains(secretApiToken))
        assertFalse(execResult.bodyPreview.contains(secretBasicPass))
        assertTrue(execResult.bodyPreview.contains(REDACTED_SECRET_PLACEHOLDER))

        // Outgoing & incoming headers must not contain the plaintext secret
        val allHeadersDump = (execResult.requestHeaders + execResult.responseHeaders).toString()
        assertFalse(allHeadersDump.contains(secretApiToken))
        assertFalse(allHeadersDump.contains(secretBasicPass))

        // Console logs must not contain the plaintext secret
        val logsDump = backend.getConsoleLogs().toString()
        assertFalse(logsDump.contains(secretApiToken))
        assertTrue(logsDump.contains(REDACTED_SECRET_PLACEHOLDER))

        // Inspect cached response via JSONPath — must still be redacted because snapshot was sanitized pre-cache
        val inspected = backend.inspectResponse(
            InspectResponseInput(requestId = reqDto.id, jsonPath = "$.echoedToken")
        )
        assertEquals(REDACTED_SECRET_PLACEHOLDER, inspected.content)

        // Export collection — must not contain secret values or passwords
        val exportedYaml = backend.exportCollection(
            ExportCollectionInput(collectionId = "Secure API", format = "opencollection-yaml")
        )
        assertFalse(exportedYaml.content.contains(secretApiToken))
        assertFalse(exportedYaml.content.contains(secretBasicPass))
    }
}
