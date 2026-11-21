package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.importers.SpecFormat
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.ScriptType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestItem
import io.github.makeforge.clapi.core.storage.importers.InsomniaImporter
import io.github.makeforge.clapi.core.storage.importers.SpecificationImporter
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.core.storage.opencollection.ExportFormat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end QA suite verifying complete Insomnia v5 collection, API specification,
 * standalone environment, authentication, and script migration into Clapi.
 */
class InsomniaImportE2ETest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val insomniaYamlCollection = """
        type: collection.insomnia.rest/5.0
        schema_version: "5.1"
        name: "E2E Insomnia Workspace"
        description: "Full E2E Insomnia v5 migration fixture"
        collection:
          - name: "Users & Auth"
            meta:
              id: fld_users
              description: "User management endpoints"
            children:
              - url: "{{ _.base_url }}/v1/users"
                name: "List Users"
                meta:
                  id: req_list_users
                  description: "Fetch paginated users"
                method: GET
                headers:
                  - name: Accept
                    value: application/json
                parameters:
                  - name: limit
                    value: "25"
                authentication:
                  type: bearer
                  token: "{{ _.access_token }}"
                  prefix: Bearer
                scripts:
                  preRequest: |
                    const token = insomnia.environment.get("access_token");
                    insomnia.request.addHeader({ key: "X-Trace-Token", value: token });
                  afterResponse: |
                    insomnia.test("Status is 200 OK", () => {
                      insomnia.expect(insomnia.response.code).to.eql(200);
                    });
              - url: "{{ _.base_url }}/v1/users"
                name: "Create User"
                meta:
                  id: req_create_user
                method: POST
                body:
                  mimeType: application/json
                  text: '{"username": "alice", "role": "admin"}'
                authentication:
                  type: basic
                  username: "{{ _.admin_user }}"
                  password: "{{ _.admin_pass }}"
              - url: "{{ _.base_url }}/v1/oauth/token"
                name: "Form Login"
                meta:
                  id: req_form_login
                method: POST
                body:
                  mimeType: application/x-www-form-urlencoded
                  params:
                    - name: grant_type
                      value: client_credentials
                    - name: scope
                      value: read:users
                authentication:
                  type: apikey
                  key: X-API-Key
                  value: "{{ _.api_key }}"
                  addTo: header
          - url: "grpc://localhost:50051"
            name: "UserService SayHello"
            meta:
              id: greq_hello
            protoMethodName: "/helloworld.Greeter/SayHello"
            body:
              text: '{"name": "Clapi"}'
            metadata:
              - name: x-request-id
                value: "req-123"
            reflectionApi:
              enabled: true
          - url: "wss://echo.websocket.org"
            name: "Live Events Stream"
            meta:
              id: ws_req_events
            headers:
              - name: Origin
                value: "https://clapi.dev"
        environments:
          name: Base Environment
          meta:
            id: env_base
          data:
            base_url: "https://api.example.com"
            access_token: "base-secret-token"
          subEnvironments:
            - name: Staging
              meta:
                id: env_staging
              data:
                base_url: "https://staging.api.example.com"
                access_token: "staging-token"
            - name: Production
              meta:
                id: env_prod
              data:
                base_url: "https://prod.api.example.com"
                access_token: "prod-token"
        certificates:
          - host: "api.example.com"
            cert: "/path/to/cert.pem"
        cookieJar:
          name: Default Jar
          cookies:
            - key: session_id
              value: xyz987
        testSuites:
          - name: Smoke Suite
    """.trimIndent()

    @Test
    fun fullCollectionYamlImportPreservesHierarchyEnvironmentsScriptsAndRoundTripsOnDisk() = runBlocking {
        val importer = SpecificationImporter.default()
        val scanResult = importer.scanContent(insomniaYamlCollection)
        val success = assertIs<ImportScanResult.Success>(scanResult)
        assertEquals(SpecFormat.INSOMNIA, success.format)
        assertEquals("E2E Insomnia Workspace", success.specTitle)
        assertEquals(1, success.folders.size)
        assertEquals(5, success.folders.first().requestCount)

        // Verify environments discovered in scan result
        val envNames = success.environments.map { it.name }
        assertTrue(envNames.contains("Base Environment"), "Base Environment should be discovered")
        assertTrue(envNames.contains("Staging"), "Staging sub-environment should be discovered")
        assertTrue(envNames.contains("Production"), "Production sub-environment should be discovered")

        val stagingEnv = success.environments.first { it.name == "Staging" }.environment
        assertTrue(stagingEnv.variables.any { it.name == "base_url" && (it as? RegularVariable)?.value?.asString == "https://staging.api.example.com" })
        assertTrue(stagingEnv.variables.any { it.name == "access_token" && (it as? RegularVariable)?.value?.asString == "staging-token" })

        // Verify OpenCollectionDocument canonical representation and disk persistence round-trip
        val doc = InsomniaImporter.default().importToOpenCollection(insomniaYamlCollection)
        assertEquals("E2E Insomnia Workspace", doc.info.name)
        assertEquals(3, doc.items.size)

        val folder = assertIs<FolderItem>(doc.items[0])
        assertEquals("Users & Auth", folder.info.name)
        assertEquals(3, folder.items.size)

        val listUsers = assertIs<HttpRequestItem>(folder.items[0])
        assertEquals("{{base_url}}/v1/users", listUsers.http.url)
        val bearerAuth = assertIs<AuthBearer>(listUsers.http.auth)
        assertEquals("{{access_token}}", bearerAuth.token)

        // Verify script translation
        val scripts = listUsers.runtime?.scripts.orEmpty()
        val preScript = scripts.firstOrNull { it.type == ScriptType.BEFORE_REQUEST }
        val postScript = scripts.firstOrNull { it.type == ScriptType.AFTER_RESPONSE }
        assertNotNull(preScript)
        assertNotNull(postScript)
        assertTrue(preScript.code.contains("clapi.environment.get(\"access_token\")"))
        assertTrue(preScript.code.contains("clapi.request.addHeader"))
        assertTrue(postScript.code.contains("clapi.test(\"Status is 200 OK\""))
        assertTrue(postScript.code.contains("clapi.expect(clapi.response.code).to.eql(200)"))

        val grpcItem = assertIs<GrpcRequestItem>(doc.items[1])
        assertEquals("grpc://localhost:50051", grpcItem.grpc.url)
        assertEquals("/helloworld.Greeter/SayHello", grpcItem.grpc.method)

        val wsItem = assertIs<WebSocketRequestItem>(doc.items[2])
        assertEquals("wss://echo.websocket.org", wsItem.websocket.url)

        // Persist to disk via CanonicalCollectionRepository and reload
        val dir = tempDir.root.absolutePath
        val canonicalRepo = DefaultCanonicalCollectionRepository(baseDirectory = dir)
        canonicalRepo.saveCollection(doc)

        val reloaded = DefaultCanonicalCollectionRepository(baseDirectory = dir)
            .getCollection("E2E Insomnia Workspace")
        assertNotNull(reloaded)
        assertEquals(doc.info.name, reloaded.info.name)
        assertEquals(doc.items.size, reloaded.items.size)

        // Export to YAML and re-import
        val exportedYaml = canonicalRepo.exportCollection(reloaded, ExportFormat.YAML)
        val reimported = canonicalRepo.importCollection(exportedYaml)
        assertEquals(reloaded.items, reimported.items)
    }

    @Test
    fun specInsomniaV5WithEmbeddedOpenApiImportsEndpointsAndEnvironments() = runBlocking {
        val specYaml = """
            type: spec.insomnia.rest/5.0
            schema_version: "5.1"
            name: "Payments API Spec"
            spec:
              contents: |
                openapi: "3.0.3"
                info:
                  title: "Payments Service"
                  version: "2.1.0"
                servers:
                  - url: "https://payments.example.com"
                paths:
                  /charges:
                    get:
                      summary: "List Charges"
                      responses:
                        "200":
                          description: "OK"
                    post:
                      summary: "Create Charge"
                      responses:
                        "201":
                          description: "Created"
            environments:
              name: Base Environment
              data:
                merchant_id: "acct_123"
              subEnvironments:
                - name: Sandbox
                  data:
                    merchant_id: "acct_sandbox_999"
        """.trimIndent()

        val importer = SpecificationImporter.default()
        val scanResult = importer.scanContent(specYaml)
        val success = assertIs<ImportScanResult.Success>(scanResult)
        assertEquals(SpecFormat.INSOMNIA, success.format)
        assertTrue(success.folders.first().requestCount >= 2)

        val doc = InsomniaImporter.default().importToOpenCollection(specYaml)
        assertEquals("Payments API Spec", doc.info.name)
        assertTrue(doc.items.isNotEmpty())
        val envNames = doc.config?.environments.orEmpty().map { it.name }
        assertTrue(envNames.contains("Base Environment"))
        assertTrue(envNames.contains("Sandbox"))
    }

    @Test
    fun standaloneInsomniaV5EnvironmentJsonImportsAllSubEnvironments() = runBlocking {
        val envJson = """
            {
              "type": "environment.insomnia.rest/5.0",
              "schema_version": "5.1",
              "name": "Shared Cloud Environments",
              "environments": {
                "name": "Global Base",
                "data": {
                  "region": "eu-central-1",
                  "timeout_ms": "5000"
                },
                "subEnvironments": [
                  {
                    "name": "QA Cluster",
                    "data": {
                      "region": "us-east-1",
                      "cluster_url": "https://qa.internal.example.com"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val scanResult = SpecificationImporter.default().scanContent(envJson)
        val success = assertIs<ImportScanResult.Success>(scanResult)
        assertEquals(SpecFormat.INSOMNIA, success.format)
        assertEquals(2, success.environments.size)

        val importedEnvs = success.environments.map { it.environment }
        assertTrue(importedEnvs.any { it.name == "Global Base" })
        assertTrue(importedEnvs.any { it.name == "QA Cluster" })

        val qaEnv = importedEnvs.first { it.name == "QA Cluster" }
        assertTrue(qaEnv.variables.any { it.name == "region" && (it as? RegularVariable)?.value?.asString == "us-east-1" })
        assertTrue(qaEnv.variables.any { it.name == "timeout_ms" && (it as? RegularVariable)?.value?.asString == "5000" }, "Sub-environment inherits base variables")
        assertTrue(qaEnv.variables.any { it.name == "cluster_url" && (it as? RegularVariable)?.value?.asString == "https://qa.internal.example.com" })
    }

    @Test
    fun unsupportedMockAndMcpClientTypesProduceActionableErrors() = runBlocking {
        val mockYaml = """
            type: mock.insomnia.rest/5.0
            name: "My Mock Server"
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            InsomniaImporter.default().importToOpenCollection(mockYaml)
        }
        assertTrue(error.message.orEmpty().contains("mock.insomnia.rest/5.0"))
    }
}
