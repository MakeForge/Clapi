package io.github.makeforge.clapi.core.network.grpc

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptors
import io.grpc.ServerInterceptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ServerCalls
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicGrpcClientTest {

    private lateinit var protoFile: File
    private lateinit var server: Server
    private lateinit var client: NettyDynamicGrpcClient
    private val lastReceivedCustomHeader = AtomicReference<String?>(null)

    @BeforeTest
    fun setUp() = runTest {
        protoFile = File.createTempFile("dynamic_grpc_test_", ".proto")
        protoFile.writeText(
            """
            syntax = "proto3";
            package helloworld;

            service Greeter {
              rpc SayHello (HelloRequest) returns (HelloReply);
            }

            message HelloRequest {
              string name = 1;
            }

            message HelloReply {
              string message = 1;
            }
            """.trimIndent()
        )

        val parser = ProtocJarParser()
        val schema = parser.parseProtoFile(protoFile)
        val fds = DescriptorProtos.FileDescriptorSet.parseFrom(schema.fileDescriptorBytes!!)
        val fileDescriptors = ProtocJarParser.buildFileDescriptors(fds)
        val serviceDesc = fileDescriptors.flatMap { it.services }.first { it.fullName == "helloworld.Greeter" }
        val methodDesc: Descriptors.MethodDescriptor = serviceDesc.findMethodByName("SayHello")!!

        val grpcMethod = MethodDescriptor.newBuilder<DynamicMessage, DynamicMessage>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceDesc.fullName, methodDesc.name))
            .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDesc.inputType)))
            .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDesc.outputType)))
            .build()

        val nameField = methodDesc.inputType.findFieldByName("name")!!
        val messageField = methodDesc.outputType.findFieldByName("message")!!

        val handler = ServerCalls.asyncUnaryCall<DynamicMessage, DynamicMessage> { req, observer ->
            val name = req.getField(nameField)?.toString().orEmpty()
            if (name == "denied") {
                observer.onError(
                    Status.PERMISSION_DENIED
                        .withDescription("Access denied for user")
                        .asRuntimeException()
                )
            } else {
                val reply = DynamicMessage.newBuilder(methodDesc.outputType)
                    .setField(messageField, "Hello, $name!")
                    .build()
                observer.onNext(reply)
                observer.onCompleted()
            }
        }

        val serviceDef = ServerServiceDefinition.builder(serviceDesc.fullName)
            .addMethod(grpcMethod, handler)
            .build()

        val headerCaptureInterceptor = object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>,
            ): ServerCall.Listener<ReqT> {
                val authKey = Metadata.Key.of("x-custom-auth", Metadata.ASCII_STRING_MARSHALLER)
                lastReceivedCustomHeader.set(headers.get(authKey))
                val forwardingCall = object : io.grpc.ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    override fun sendHeaders(responseHeaders: Metadata) {
                        val replyHeaderKey = Metadata.Key.of("x-server-reply", Metadata.ASCII_STRING_MARSHALLER)
                        responseHeaders.put(replyHeaderKey, "ack-from-server")
                        super.sendHeaders(responseHeaders)
                    }
                }
                return next.startCall(forwardingCall, headers)
            }
        }

        server = NettyServerBuilder.forPort(0)
            .addService(ServerInterceptors.intercept(serviceDef, headerCaptureInterceptor))
            .build()
            .start()

        client = NettyDynamicGrpcClient(protoParser = parser)
    }

    @AfterTest
    fun tearDown() = runTest {
        if (::client.isInitialized) {
            client.shutdown()
        }
        if (::server.isInitialized) {
            server.shutdownNow()
        }
        if (::protoFile.isInitialized) {
            protoFile.delete()
        }
    }

    @Test
    fun `executes unary call, sends metadata, and formats JSON response`() = runTest {
        val response = client.executeUnary(
            GrpcCallRequest(
                requestId = "req-1",
                host = "127.0.0.1",
                port = server.port,
                useTls = false,
                protoFilePath = protoFile.absolutePath,
                fullMethodName = "helloworld.Greeter/SayHello",
                jsonPayload = """{"name": "Clapi"}""",
                metadata = listOf("X-Custom-Auth" to "Bearer secret-token"),
            )
        )

        assertEquals(0, response.statusCode, "Expected status 0 (OK), got ${response.statusCode}: ${response.errorMessage}")
        assertEquals("OK", response.statusDescription)
        assertNull(response.errorMessage)
        assertNotNull(response.responseJson)
        assertTrue(response.responseJson!!.contains("\"message\": \"Hello, Clapi!\""))
        assertTrue(response.durationMs >= 1L)
        assertEquals("Bearer secret-token", lastReceivedCustomHeader.get())
        assertTrue(response.responseMetadata.any { it.first == "x-server-reply" && it.second == "ack-from-server" })
    }

    @Test
    fun `maps gRPC status exception to structured GrpcCallResponse without throwing`() = runTest {
        val response = client.executeUnary(
            GrpcCallRequest(
                requestId = "req-denied",
                host = "127.0.0.1",
                port = server.port,
                useTls = false,
                protoFilePath = protoFile.absolutePath,
                fullMethodName = "helloworld.Greeter/SayHello",
                jsonPayload = """{"name": "denied"}""",
            )
        )

        assertEquals(Status.Code.PERMISSION_DENIED.value(), response.statusCode)
        assertEquals("PERMISSION_DENIED", response.statusDescription)
        assertNull(response.responseJson)
        assertNotNull(response.errorMessage)
        assertTrue(response.errorMessage!!.contains("Access denied for user"))
    }

    @Test
    fun `returns INVALID_ARGUMENT when JSON payload is malformed`() = runTest {
        val response = client.executeUnary(
            GrpcCallRequest(
                requestId = "req-bad-json",
                host = "127.0.0.1",
                port = server.port,
                useTls = false,
                protoFilePath = protoFile.absolutePath,
                fullMethodName = "helloworld.Greeter/SayHello",
                jsonPayload = """{"name": [1, 2, 3]}""",
            )
        )

        assertEquals(Status.Code.INVALID_ARGUMENT.value(), response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.statusDescription)
        assertNull(response.responseJson)
        assertNotNull(response.errorMessage)
        assertTrue(response.errorMessage!!.contains("Invalid JSON message payload"))
    }
}
