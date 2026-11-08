package io.github.crustack.clapi.core.network.grpc

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
}
