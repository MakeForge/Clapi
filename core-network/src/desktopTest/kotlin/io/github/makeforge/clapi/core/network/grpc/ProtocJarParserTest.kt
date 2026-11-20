package io.github.makeforge.clapi.core.network.grpc

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProtocJarParserTest {

    private val parser = ProtocJarParser()

    @Test
    fun `parses valid proto3 file and extracts services and methods`() = runTest {
        val tempFile = File.createTempFile("greeter_test_", ".proto")
        try {
            tempFile.writeText(
                """
                syntax = "proto3";
                package helloworld;

                service Greeter {
                  rpc SayHello (HelloRequest) returns (HelloReply);
                  rpc SayHelloAgain (HelloRequest) returns (HelloReply);
                  rpc StreamGreetings (HelloRequest) returns (stream HelloReply);
                }

                message HelloRequest {
                  string name = 1;
                }

                message HelloReply {
                  string message = 1;
                }
                """.trimIndent()
            )

            val schema = parser.parseProtoFile(tempFile)
            assertEquals(tempFile.absolutePath, schema.protoFilePath)
            assertNotNull(schema.fileDescriptorBytes)
            assertTrue(schema.fileDescriptorBytes!!.isNotEmpty())

            assertEquals(1, schema.services.size)
            val service = schema.services.single()
            assertEquals("helloworld.Greeter", service.fullName)
            assertEquals(3, service.methods.size)

            val sayHello = service.methods[0]
            assertEquals("helloworld.Greeter", sayHello.serviceFullName)
            assertEquals("SayHello", sayHello.methodName)
            assertEquals("helloworld.Greeter/SayHello", sayHello.fullMethodName)
            assertEquals("helloworld.HelloRequest", sayHello.inputTypeName)
            assertEquals("helloworld.HelloReply", sayHello.outputTypeName)
            assertFalse(sayHello.isClientStreaming)
            assertFalse(sayHello.isServerStreaming)

            val sayHelloAgain = service.methods[1]
            assertEquals("helloworld.Greeter/SayHelloAgain", sayHelloAgain.fullMethodName)

            val streamGreetings = service.methods[2]
            assertEquals("helloworld.Greeter/StreamGreetings", streamGreetings.fullMethodName)
            assertFalse(streamGreetings.isClientStreaming)
            assertTrue(streamGreetings.isServerStreaming)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `throws ProtoParseException when proto file does not exist`() = runTest {
        val ex = assertFailsWith<ProtoParseException> {
            parser.parseProtoFile("/non/existent/path/missing.proto")
        }
        assertTrue(ex.message!!.contains("does not exist"))
    }

    @Test
    fun `throws ProtoParseException when proto file has syntax errors`() = runTest {
        val tempFile = File.createTempFile("invalid_test_", ".proto")
        try {
            tempFile.writeText(
                """
                syntax = "proto3";
                package broken;
                service BrokenService {
                  rpc BadMethod (UndefinedInput) returns (UndefinedOutput)
                """.trimIndent()
            )

            val ex = assertFailsWith<ProtoParseException> {
                parser.parseProtoFile(tempFile)
            }
            assertTrue(ex.message!!.contains("Failed to compile"))
            assertNotNull(ex.stderr)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `cleans up temporary descriptor files after parsing and on error`() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val beforeDescFiles = tempDir.listFiles { _, name -> name.startsWith("clapi_proto_") && name.endsWith(".desc") }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

        val validProto = File.createTempFile("cleanup_valid_", ".proto")
        val invalidProto = File.createTempFile("cleanup_invalid_", ".proto")
        try {
            validProto.writeText(
                """
                syntax = "proto3";
                package cleanup;
                service Ping { rpc Call (Msg) returns (Msg); }
                message Msg { string id = 1; }
                """.trimIndent()
            )
            invalidProto.writeText("syntax = \"proto3\"; broken_proto")

            parser.parseProtoFile(validProto)
            assertFailsWith<ProtoParseException> {
                parser.parseProtoFile(invalidProto)
            }

            val afterDescFiles = tempDir.listFiles { _, name -> name.startsWith("clapi_proto_") && name.endsWith(".desc") }
                ?.map { it.name }
                ?.toSet()
                ?: emptySet()
            val leakedFiles = afterDescFiles - beforeDescFiles
            assertTrue(leakedFiles.isEmpty(), "Expected no leaked temporary .desc files, but found: $leakedFiles")
        } finally {
            validProto.delete()
            invalidProto.delete()
        }
    }

    @Test
    fun `resolves imported proto files using includePaths with file links and ancestor traversal`() = runTest {
        val rootDir = kotlin.io.path.createTempDirectory("clapi_import_test_").toFile()
        try {
            val serviceDir = File(rootDir, "services").apply { mkdirs() }
            val commonDir = File(rootDir, "external/protobuf/common").apply { mkdirs() }
            val metaFile = File(commonDir, "metadata.proto").apply {
                writeText(
                    """
                    syntax = "proto3";
                    package clapi.common;
                    message RequestMetadata {
                      string trace_id = 1;
                    }
                    """.trimIndent()
                )
            }

            val serviceFile = File(serviceDir, "service.proto").apply {
                writeText(
                    """
                    syntax = "proto3";
                    package helloworld;
                    import "protobuf/common/metadata.proto";

                    service TestService {
                      rpc Ping (clapi.common.RequestMetadata) returns (clapi.common.RequestMetadata);
                    }
                    """.trimIndent()
                )
            }

            // Test 1: Passing the specific file path as an includePath should resolve through ancestor traversal (external directory)
            val schema = parser.parseProtoFile(
                protoFilePath = serviceFile.absolutePath,
                includePaths = listOf(metaFile.absolutePath),
            )

            assertEquals(1, schema.services.size)
            val service = schema.services.first()
            assertEquals("helloworld.TestService", service.fullName)
            assertEquals("clapi.common.RequestMetadata", service.methods.first().inputTypeName)

            // Test 2: Passing empty includePaths when service relies on external import should fail
            val ex = assertFailsWith<ProtoParseException> {
                parser.parseProtoFile(
                    protoFilePath = serviceFile.absolutePath,
                    includePaths = emptyList(),
                )
            }
            assertTrue(ex.message!!.contains("Failed to compile") || ex.stderr?.contains("not found") == true)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `parses proto file with well-known google protobuf built-in types without extra include paths`() = runTest {
        val tempFile = File.createTempFile("well_known_types_", ".proto")
        try {
            tempFile.writeText(
                """
                syntax = "proto3";
                package clapi.test;

                import "google/protobuf/timestamp.proto";
                import "google/protobuf/duration.proto";
                import "google/protobuf/empty.proto";
                import "google/protobuf/any.proto";
                import "google/protobuf/struct.proto";
                import "google/protobuf/wrappers.proto";
                import "google/protobuf/field_mask.proto";

                service WellKnownService {
                  rpc GetTimestamp (google.protobuf.Empty) returns (google.protobuf.Timestamp);
                  rpc EchoDuration (google.protobuf.Duration) returns (google.protobuf.Duration);
                }

                message TestPayload {
                  google.protobuf.Timestamp created_at = 1;
                  google.protobuf.Duration timeout = 2;
                  google.protobuf.Any details = 3;
                  google.protobuf.Struct payload = 4;
                  google.protobuf.StringValue note = 5;
                  google.protobuf.FieldMask mask = 6;
                }
                """.trimIndent()
            )

            val schema = parser.parseProtoFile(
                protoFilePath = tempFile.absolutePath,
                includePaths = emptyList(),
            )

            assertEquals(1, schema.services.size)
            val service = schema.services.single()
            assertEquals("clapi.test.WellKnownService", service.fullName)
            assertEquals(2, service.methods.size)

            val getTimestamp = service.methods.first { it.methodName == "GetTimestamp" }
            assertEquals("google.protobuf.Empty", getTimestamp.inputTypeName)
            assertEquals("google.protobuf.Timestamp", getTimestamp.outputTypeName)

            val echoDuration = service.methods.first { it.methodName == "EchoDuration" }
            assertEquals("google.protobuf.Duration", echoDuration.inputTypeName)
            assertEquals("google.protobuf.Duration", echoDuration.outputTypeName)

            // Verify Java protobuf Descriptors and JsonFormat integration
            val fds = com.google.protobuf.DescriptorProtos.FileDescriptorSet.parseFrom(schema.fileDescriptorBytes!!)
            val fileDescriptors = ProtocJarParser.buildFileDescriptors(fds)
            val typeRegistryBuilder = com.google.protobuf.util.JsonFormat.TypeRegistry.newBuilder()
            fileDescriptors.forEach { fd ->
                fd.messageTypes.forEach { msgType ->
                    typeRegistryBuilder.add(msgType)
                }
            }
            val typeRegistry = typeRegistryBuilder.build()
            val testPayloadDesc = fileDescriptors.flatMap { it.messageTypes }.first { it.name == "TestPayload" }
            val json = """
                {
                  "createdAt": "2026-11-09T20:00:00Z",
                  "timeout": "10s",
                  "note": "sample note"
                }
            """.trimIndent()
            val builder = com.google.protobuf.DynamicMessage.newBuilder(testPayloadDesc)
            com.google.protobuf.util.JsonFormat.parser().usingTypeRegistry(typeRegistry).merge(json, builder)
            val msg = builder.build()
            val printedJson = com.google.protobuf.util.JsonFormat.printer().usingTypeRegistry(typeRegistry).print(msg)
            assertTrue(printedJson.contains("2026-11-09T20:00:00Z"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `buildFileDescriptors resolves standard types from Java runtime even when descriptor set lacks imported protos`() = runTest {
        val tempFile = File.createTempFile("timestamp_dep_", ".proto")
        try {
            tempFile.writeText(
                """
                syntax = "proto3";
                package clapi.test;
                import "google/protobuf/timestamp.proto";
                message TimestampedRecord {
                  string id = 1;
                  google.protobuf.Timestamp time = 2;
                }
                """.trimIndent()
            )

            val schema = parser.parseProtoFile(tempFile)
            val fullFds = com.google.protobuf.DescriptorProtos.FileDescriptorSet.parseFrom(schema.fileDescriptorBytes!!)

            // Filter out google.protobuf files to simulate a stripped FileDescriptorSet
            val strippedFds = com.google.protobuf.DescriptorProtos.FileDescriptorSet.newBuilder()
                .addAllFile(fullFds.fileList.filter { !it.name.startsWith("google/protobuf/") })
                .build()

            val builtDescriptors = ProtocJarParser.buildFileDescriptors(strippedFds)
            assertEquals(1, builtDescriptors.size)
            val recordDesc = builtDescriptors.first().findMessageTypeByName("TimestampedRecord")
            assertNotNull(recordDesc)
            val timeField = recordDesc.findFieldByName("time")
            assertNotNull(timeField)
            assertEquals("google.protobuf.Timestamp", timeField.messageType.fullName)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `resolves nested imports when root proto folder or protobuf subfolder is passed in includePaths`() = runTest {
        val tempRoot = File.createTempFile("clapi_proto_folder_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val resourcesDir = File(tempRoot, "resources").apply { mkdirs() }
            val protobufDir = File(resourcesDir, "protobuf").apply { mkdirs() }
            val commonDir = File(protobufDir, "common").apply { mkdirs() }
            val collectionSharedDir = File(tempRoot, "collection_shared").apply { mkdirs() }

            File(commonDir, "metadata.proto").writeText(
                """
                syntax = "proto3";
                package clapi.common;
                message RequestMetadata {
                  string trace_id = 1;
                }
                """.trimIndent()
            )
            val sharedProto = File(collectionSharedDir, "extra.proto").apply {
                writeText(
                    """
                    syntax = "proto3";
                    package clapi.extra;
                    message ExtraInfo {
                      string label = 1;
                    }
                    """.trimIndent()
                )
            }
            val greeterProto = File(protobufDir, "greeter.proto").apply {
                writeText(
                    """
                    syntax = "proto3";
                    package helloworld;
                    import "protobuf/common/metadata.proto";
                    import "extra.proto";
                    service Greeter {
                      rpc SayHello (HelloRequest) returns (HelloReply);
                    }
                    message HelloRequest { string name = 1; }
                    message HelloReply {
                      string message = 1;
                      clapi.common.RequestMetadata ack_metadata = 2;
                      clapi.extra.ExtraInfo extra = 3;
                    }
                    """.trimIndent()
                )
            }

            // 1. Selecting the parent `resources` folder + collection proto file
            val schemaFromRoot = parser.parseProtoFile(
                protoFilePath = greeterProto.absolutePath,
                includePaths = listOf(resourcesDir.absolutePath, sharedProto.absolutePath),
            )
            assertEquals(1, schemaFromRoot.services.size)
            assertEquals("helloworld.Greeter/SayHello", schemaFromRoot.services.single().methods.single().fullMethodName)

            // 2. Selecting the `protobuf` subfolder itself + collection proto file
            val schemaFromSubfolder = parser.parseProtoFile(
                protoFilePath = greeterProto.absolutePath,
                includePaths = listOf(protobufDir.absolutePath, sharedProto.absolutePath),
            )
            assertEquals(1, schemaFromSubfolder.services.size)
            assertEquals("helloworld.Greeter/SayHello", schemaFromSubfolder.services.single().methods.single().fullMethodName)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
