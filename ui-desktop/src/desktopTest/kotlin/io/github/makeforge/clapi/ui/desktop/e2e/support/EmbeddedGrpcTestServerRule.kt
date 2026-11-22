// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e.support

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import io.github.makeforge.clapi.core.network.grpc.ProtocJarParser
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ServerCalls
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Self-contained in-process Netty gRPC test server exposing `helloworld.Greeter/SayHello`
 * with dynamic metadata echo, trailing metadata, and simulated error statuses for UI E2E tests.
 */
class EmbeddedGrpcTestServerRule : ExternalResource() {

    private var grpcServer: Server? = null
    private var tempProtoDir: File? = null

    var port: Int = 0
        private set

    val address: String
        get() = "127.0.0.1:$port"

    lateinit var protoFilePath: String
        private set

    lateinit var protoDirPath: String
        private set

    override fun before() {
        val dir = Files.createTempDirectory("clapi-ui-e2e-grpc-").toFile()
        tempProtoDir = dir
        protoDirPath = dir.absolutePath

        val protoFile = File(dir, "greeter.proto")
        protoFile.writeText(GREETER_PROTO)
        protoFilePath = protoFile.absolutePath

        val parser = ProtocJarParser()
        val schema = runBlocking {
            parser.parseProtoFile(
                protoFilePath = protoFile.absolutePath,
                includePaths = listOf(dir.absolutePath),
            )
        }
        val fds = DescriptorProtos.FileDescriptorSet.parseFrom(
            requireNotNull(schema.fileDescriptorBytes) { "Failed to compile greeter.proto" },
        )
        val fileDescriptors = ProtocJarParser.buildFileDescriptors(fds)
        val serviceDesc = fileDescriptors.flatMap { it.services }.first { it.fullName == "helloworld.Greeter" }

        val customMetaContextKey = Context.key<String>("x-custom-meta")
        val methodDesc: Descriptors.MethodDescriptor = serviceDesc.findMethodByName("SayHello")!!

        val grpcMethod = MethodDescriptor.newBuilder<DynamicMessage, DynamicMessage>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceDesc.fullName, methodDesc.name))
            .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDesc.inputType)))
            .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDesc.outputType)))
            .build()

        val nameField = methodDesc.inputType.findFieldByName("name")!!
        val messageField = methodDesc.outputType.findFieldByName("message")!!
        val receivedMetaField = methodDesc.outputType.findFieldByName("received_meta")!!

        val handler = ServerCalls.asyncUnaryCall<DynamicMessage, DynamicMessage> { req, observer ->
            val name = req.getField(nameField)?.toString().orEmpty()
            when (name) {
                "error" -> observer.onError(
                    Status.PERMISSION_DENIED
                        .withDescription("Simulated permission denied for $name")
                        .asRuntimeException(),
                )
                else -> {
                    val reply = DynamicMessage.newBuilder(methodDesc.outputType)
                        .setField(messageField, "Hello, $name!")
                        .setField(receivedMetaField, customMetaContextKey.get().orEmpty())
                        .build()
                    observer.onNext(reply)
                    observer.onCompleted()
                }
            }
        }

        val serviceDef = ServerServiceDefinition.builder(serviceDesc.fullName)
            .addMethod(grpcMethod, handler)
            .build()

        val interceptor = object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>,
            ): ServerCall.Listener<ReqT> {
                val metaKey = Metadata.Key.of("x-custom-meta", Metadata.ASCII_STRING_MARSHALLER)
                val incomingMeta = headers.get(metaKey)
                val ctx = Context.current().withValue(customMetaContextKey, incomingMeta.orEmpty())
                val wrappedCall = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    override fun sendHeaders(responseHeaders: Metadata) {
                        responseHeaders.put(
                            Metadata.Key.of("x-grpc-server", Metadata.ASCII_STRING_MARSHALLER),
                            "EmbeddedGrpcTestServer",
                        )
                        if (!incomingMeta.isNullOrBlank()) {
                            responseHeaders.put(
                                Metadata.Key.of("x-echo-custom-meta", Metadata.ASCII_STRING_MARSHALLER),
                                incomingMeta,
                            )
                        }
                        super.sendHeaders(responseHeaders)
                    }

                    override fun close(status: Status, trailers: Metadata) {
                        trailers.put(
                            Metadata.Key.of("x-grpc-trailer", Metadata.ASCII_STRING_MARSHALLER),
                            "trailer-ok",
                        )
                        super.close(status, trailers)
                    }
                }
                return Contexts.interceptCall(ctx, wrappedCall, headers, next)
            }
        }

        val started = NettyServerBuilder.forPort(0)
            .addService(ServerInterceptors.intercept(serviceDef, interceptor))
            .build()
            .start()
        grpcServer = started
        port = started.port
    }

    override fun after() {
        runCatching {
            grpcServer?.shutdownNow()
            grpcServer?.awaitTermination(3, TimeUnit.SECONDS)
        }
        grpcServer = null
        tempProtoDir?.deleteRecursively()
        tempProtoDir = null
    }

    companion object {
        private val GREETER_PROTO = """
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
              string received_meta = 2;
            }
        """.trimIndent()
    }
}
