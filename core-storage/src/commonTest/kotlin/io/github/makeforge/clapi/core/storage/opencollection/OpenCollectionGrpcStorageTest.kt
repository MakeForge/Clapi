package io.github.makeforge.clapi.core.storage.opencollection

import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.GrpcMetadata
import io.github.makeforge.clapi.core.model.opencollection.GrpcMethodType
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestRuntime
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.ItemType
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.Script
import io.github.makeforge.clapi.core.model.opencollection.ScriptType
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenCollectionGrpcStorageTest {

    private val validator = OpenCollectionSchemaValidator()

    private fun createGrpcDocument(): OpenCollectionDocument {
        return OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "gRPC Collection",
                version = "1.0.0"
            ),
            bundled = true,
            items = listOf(
                FolderItem(
                    info = FolderInfo(name = "Greeter Service", type = "folder"),
                    items = listOf(
                        GrpcRequestItem(
                            info = GrpcRequestInfo(name = "SayHello", type = "grpc"),
                            grpc = GrpcRequestDetails(
                                url = "grpc://localhost:50051",
                                method = "helloworld.Greeter/SayHello",
                                methodType = "unary",
                                protoFilePath = "greeter.proto",
                                metadata = listOf(
                                    GrpcMetadata(name = "x-request-id", value = "{{reqId}}"),
                                    GrpcMetadata(name = "x-disabled-meta", value = "skip", disabled = true)
                                ),
                                message = "{\n  \"name\": \"World\"\n}",
                                auth = AuthBearer(token = "{{token}}")
                            ),
                            runtime = GrpcRequestRuntime(
                                scripts = listOf(
                                    Script(type = "before-request", code = "console.log('pre');"),
                                    Script(type = "after-response", code = "console.log('post');")
                                )
                            )
                        ),
                        HttpRequestItem(
                            info = HttpRequestInfo(name = "Health Check", type = "http"),
                            http = HttpRequestDetails(
                                method = "GET",
                                url = "http://localhost:8080/health"
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun testGrpcDocumentPassesSchemaValidation() {
        val doc = createGrpcDocument()
        val json = OpenCollectionSerializer.serializeJson(doc)
        val issues = validator.validate(json)
        assertTrue(issues.isEmpty(), "Expected gRPC OpenCollection document to pass schema validation, but got: $issues")

        val yaml = OpenCollectionSerializer.serializeYaml(doc)
        val yamlIssues = validator.validateYaml(yaml)
        assertTrue(yamlIssues.isEmpty(), "Expected gRPC OpenCollection YAML to pass schema validation, but got: $yamlIssues")
    }

    @Test
    fun testGrpcJsonRoundTrip() {
        val original = createGrpcDocument()
        val json = OpenCollectionSerializer.serializeJson(original)
        val decoded = OpenCollectionSerializer.deserializeJson(json)

        assertEquals(original.info.name, decoded.info.name)
        val folder = assertIs<FolderItem>(decoded.items.single())
        assertEquals(2, folder.items.size)

        val grpcItem = assertIs<GrpcRequestItem>(folder.items[0])
        assertEquals("SayHello", grpcItem.info.name)
        assertEquals(ItemType.GRPC, grpcItem.info.type)
        assertEquals("grpc://localhost:50051", grpcItem.grpc.url)
        assertEquals("helloworld.Greeter/SayHello", grpcItem.grpc.method)
        assertEquals(GrpcMethodType.UNARY, grpcItem.grpc.methodType)
        assertEquals("greeter.proto", grpcItem.grpc.protoFilePath)
        assertEquals("{\n  \"name\": \"World\"\n}", grpcItem.grpc.message)

        assertEquals(2, grpcItem.grpc.metadata.size)
        assertEquals("x-request-id", grpcItem.grpc.metadata[0].name)
        assertEquals("{{reqId}}", grpcItem.grpc.metadata[0].value)
        assertNull(grpcItem.grpc.metadata[0].disabled)
        assertEquals("x-disabled-meta", grpcItem.grpc.metadata[1].name)
        assertEquals("skip", grpcItem.grpc.metadata[1].value)
        assertEquals(true, grpcItem.grpc.metadata[1].disabled)

        val auth = assertIs<AuthBearer>(grpcItem.grpc.auth)
        assertEquals("{{token}}", auth.token)

        val scripts = grpcItem.runtime?.scripts.orEmpty()
        assertEquals(2, scripts.size)
        assertEquals(ScriptType.BEFORE_REQUEST, scripts[0].type)
        assertEquals("console.log('pre');", scripts[0].code)
        assertEquals(ScriptType.AFTER_RESPONSE, scripts[1].type)
        assertEquals("console.log('post');", scripts[1].code)
    }

    @Test
    fun testGrpcYamlRoundTrip() {
        val original = createGrpcDocument()
        val yaml = OpenCollectionSerializer.serializeYaml(original)
        val decoded = OpenCollectionSerializer.deserializeYaml(yaml)

        val folder = assertIs<FolderItem>(decoded.items.single())
        val grpcItem = assertIs<GrpcRequestItem>(folder.items[0])
        assertEquals("SayHello", grpcItem.info.name)
        assertEquals("grpc://localhost:50051", grpcItem.grpc.url)
        assertEquals("helloworld.Greeter/SayHello", grpcItem.grpc.method)
        assertEquals(GrpcMethodType.UNARY, grpcItem.grpc.methodType)
        assertEquals("greeter.proto", grpcItem.grpc.protoFilePath)
        assertEquals("{\n  \"name\": \"World\"\n}", grpcItem.grpc.message)
        assertEquals(2, grpcItem.grpc.metadata.size)
        assertEquals(true, grpcItem.grpc.metadata[1].disabled)
    }

    @Test
    fun testGrpcUnknownFieldsCapturedIntoExtensions() {
        val json = """
            {
                "opencollection": "1.0.0",
                "info": {
                    "name": "gRPC Ext Test"
                },
                "items": [
                    {
                        "info": {
                            "name": "Unary Call",
                            "type": "grpc"
                        },
                        "grpc": {
                            "url": "localhost:50051",
                            "method": "pkg.Svc/Call",
                            "methodType": "unary"
                        },
                        "customGrpcExtension": "extValue"
                    }
                ]
            }
        """.trimIndent()

        val doc = OpenCollectionSerializer.deserializeJson(json)
        val grpcItem = assertIs<GrpcRequestItem>(doc.items.single())
        assertEquals(JsonPrimitive("extValue"), grpcItem.extensions["customGrpcExtension"])
    }
}
