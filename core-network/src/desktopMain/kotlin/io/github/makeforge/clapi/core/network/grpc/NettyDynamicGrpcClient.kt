package io.github.makeforge.clapi.core.network.grpc

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.util.JsonFormat
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ClientInterceptors
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ClientCalls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NettyDynamicGrpcClient(
    private val protoParser: ProtoParser = ProtocJarParser(),
    private val timeoutMs: Long = 30_000L,
) : DynamicGrpcClient {

    private data class ChannelKey(
        val host: String,
        val port: Int,
        val useTls: Boolean,
    )

    private val channels = ConcurrentHashMap<ChannelKey, ManagedChannel>()

    override suspend fun executeUnary(request: GrpcCallRequest): GrpcCallResponse = withContext(Dispatchers.IO) {
        val startNanos = System.nanoTime()
        fun elapsedMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)

        val capturedMetadata = Collections.synchronizedList(mutableListOf<Pair<String, String>>())

        try {
            if (request.host.isBlank() || request.port <= 0) {
                return@withContext GrpcCallResponse(
                    requestId = request.requestId,
                    statusCode = Status.Code.INVALID_ARGUMENT.value(),
                    statusDescription = Status.Code.INVALID_ARGUMENT.name,
                    responseJson = null,
                    durationMs = elapsedMs(),
                    errorMessage = "Invalid gRPC target endpoint: ${request.host}:${request.port}",
                )
            }

            val schema = try {
                protoParser.parseProtoFile(request.protoFilePath, request.includePaths)
            } catch (e: ProtoParseException) {
                return@withContext GrpcCallResponse(
                    requestId = request.requestId,
                    statusCode = Status.Code.INVALID_ARGUMENT.value(),
                    statusDescription = Status.Code.INVALID_ARGUMENT.name,
                    responseJson = null,
                    durationMs = elapsedMs(),
                    errorMessage = e.message ?: "Failed to parse proto file",
                )
            }

            val descriptorBytes = schema.fileDescriptorBytes
                ?: return@withContext GrpcCallResponse(
                    requestId = request.requestId,
                    statusCode = Status.Code.INTERNAL.value(),
                    statusDescription = Status.Code.INTERNAL.name,
                    responseJson = null,
                    durationMs = elapsedMs(),
                    errorMessage = "Compiled proto schema did not contain descriptor bytes",
                )

            val fileDescriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(descriptorBytes)
            val fileDescriptors = ProtocJarParser.buildFileDescriptors(fileDescriptorSet)

            val normalizedFullMethod = request.fullMethodName.trim().removePrefix("/")
            val serviceName = normalizedFullMethod.substringBeforeLast('/', "")
            val methodName = normalizedFullMethod.substringAfterLast('/', "")

            val methodDescriptor: Descriptors.MethodDescriptor = findMethodDescriptor(
                fileDescriptors = fileDescriptors,
                serviceName = serviceName,
                methodName = methodName,
            ) ?: return@withContext GrpcCallResponse(
                requestId = request.requestId,
                statusCode = Status.Code.UNIMPLEMENTED.value(),
                statusDescription = Status.Code.UNIMPLEMENTED.name,
                responseJson = null,
                durationMs = elapsedMs(),
                errorMessage = "Method '${request.fullMethodName}' not found in proto schema",
            )

            val typeRegistryBuilder = JsonFormat.TypeRegistry.newBuilder()
            fileDescriptors.forEach { fd ->
                fd.messageTypes.forEach { msgType ->
                    typeRegistryBuilder.add(msgType)
                }
            }
            val typeRegistry = typeRegistryBuilder.build()

            val requestBuilder = DynamicMessage.newBuilder(methodDescriptor.inputType)
            val payloadToParse = request.jsonPayload.trim().ifBlank { "{}" }
            try {
                JsonFormat.parser()
                    .usingTypeRegistry(typeRegistry)
                    .ignoringUnknownFields()
                    .merge(payloadToParse, requestBuilder)
            } catch (e: InvalidProtocolBufferException) {
                return@withContext GrpcCallResponse(
                    requestId = request.requestId,
                    statusCode = Status.Code.INVALID_ARGUMENT.value(),
                    statusDescription = Status.Code.INVALID_ARGUMENT.name,
                    responseJson = null,
                    durationMs = elapsedMs(),
                    errorMessage = "Invalid JSON message payload: ${e.message}",
                )
            }
            val requestMessage = requestBuilder.build()

            val grpcMethodDescriptor = MethodDescriptor.newBuilder<DynamicMessage, DynamicMessage>()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(
                    MethodDescriptor.generateFullMethodName(
                        methodDescriptor.service.fullName,
                        methodDescriptor.name,
                    )
                )
                .setRequestMarshaller(
                    ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDescriptor.inputType))
                )
                .setResponseMarshaller(
                    ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDescriptor.outputType))
                )
                .build()

            val channel = getOrCreateChannel(
                ChannelKey(
                    host = request.host,
                    port = request.port,
                    useTls = request.useTls,
                )
            )

            val interceptor = createMetadataInterceptor(
                outgoingMetadata = request.metadata,
                capturedResponseMetadata = capturedMetadata,
            )
            val interceptedChannel = ClientInterceptors.intercept(channel, interceptor)

            val callOptions = CallOptions.DEFAULT.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
            val clientCall = interceptedChannel.newCall(grpcMethodDescriptor, callOptions)

            val responseMessage = ClientCalls.futureUnaryCall(clientCall, requestMessage).await()

            val responseJson = JsonFormat.printer()
                .usingTypeRegistry(typeRegistry)
                .alwaysPrintFieldsWithNoPresence()
                .print(responseMessage)

            GrpcCallResponse(
                requestId = request.requestId,
                statusCode = Status.Code.OK.value(),
                statusDescription = Status.Code.OK.name,
                responseJson = responseJson,
                durationMs = elapsedMs(),
                responseMetadata = capturedMetadata.toList(),
                errorMessage = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val status = Status.fromThrowable(e)
            val trailers = Status.trailersFromThrowable(e)
            if (trailers != null && capturedMetadata.isEmpty()) {
                extractMetadataEntries(trailers, capturedMetadata)
            }
            val detail = if(e.message != null) {
                "${e.message}${e.cause?.message?.let { "\nCause:${it}" } ?: ""}"
            } else status.description ?: status.code.name
            GrpcCallResponse(
                requestId = request.requestId,
                statusCode = status.code.value(),
                statusDescription = status.code.name,
                responseJson = null,
                durationMs = elapsedMs(),
                responseMetadata = capturedMetadata.toList(),
                errorMessage = detail,
            )
        }
    }

    private fun getOrCreateChannel(key: ChannelKey): ManagedChannel {
        val existing = channels[key]
        if (existing != null && !existing.isShutdown && !existing.isTerminated) {
            return existing
        }
        return channels.compute(key) { _, current ->
            if (current != null && !current.isShutdown && !current.isTerminated) {
                current
            } else {
                val builder = OkHttpChannelBuilder.forAddress(key.host, key.port)
                if (key.useTls) {
                    builder.useTransportSecurity()
                } else {
                    builder.usePlaintext()
                }
                builder.build()
            }
        }!!
    }

    private fun findMethodDescriptor(
        fileDescriptors: List<Descriptors.FileDescriptor>,
        serviceName: String,
        methodName: String,
    ): Descriptors.MethodDescriptor? {
        for (fd in fileDescriptors) {
            for (service in fd.services) {
                if (service.fullName == serviceName || service.name == serviceName) {
                    service.findMethodByName(methodName)?.let { return it }
                }
            }
        }
        return null
    }

    internal fun createMetadataInterceptor(
        outgoingMetadata: List<Pair<String, String>>,
        capturedResponseMetadata: MutableList<Pair<String, String>>,
    ): ClientInterceptor = object : ClientInterceptor {
        override fun <ReqT : Any?, RespT : Any?> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<ReqT, RespT> {
            val delegate = next.newCall(method, callOptions)
            return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(delegate) {
                override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                    outgoingMetadata.forEach { (rawKey, value) ->
                        val cleanKey = rawKey.trim().lowercase()
                        if (cleanKey.isNotEmpty() && !cleanKey.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                            runCatching {
                                val metaKey = Metadata.Key.of(cleanKey, Metadata.ASCII_STRING_MARSHALLER)
                                headers.put(metaKey, value)
                            }
                        }
                    }

                    val listener = object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                        responseListener
                    ) {
                        override fun onHeaders(headers: Metadata) {
                            extractMetadataEntries(headers, capturedResponseMetadata)
                            super.onHeaders(headers)
                        }

                        override fun onClose(status: Status, trailers: Metadata) {
                            extractMetadataEntries(trailers, capturedResponseMetadata)
                            super.onClose(status, trailers)
                        }
                    }
                    super.start(listener, headers)
                }
            }
        }
    }

    private fun extractMetadataEntries(
        metadata: Metadata,
        target: MutableList<Pair<String, String>>,
    ) {
        for (keyName in metadata.keys()) {
            if (!keyName.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                runCatching {
                    val key = Metadata.Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER)
                    metadata.getAll(key)?.forEach { value ->
                        target.add(keyName to value)
                    }
                }
            }
        }
    }

    override suspend fun shutdown() = withContext(Dispatchers.IO) {
        val activeChannels = channels.values.toList()
        channels.clear()
        activeChannels.forEach { channel ->
            runCatching {
                channel.shutdown()
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow()
                }
            }
        }
    }
}
