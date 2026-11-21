package io.github.makeforge.clapi.testsupport

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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.content.forEachPart
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.websocket.CloseReason
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DummyApiServer(
    private val port: Int = 0,
    private val grpcPort: Int = 0,
    private val validBearerToken: String = "dummy-token",
    private val validBasicToken: String = "dXNlcjpwYXNz",
    private val validApiKey: String = "test-api-key",
    private val recordRequests: Boolean = true,
) {
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var grpcServer: Server? = null
    private var tempProtoDir: java.io.File? = null
    private var tempProtoFile: java.io.File? = null
    private var tempMetaFile: java.io.File? = null
    private val idSequence = AtomicInteger(3)
    private val users = ConcurrentHashMap<Int, UserRecord>()
    private val _requestsLog = MutableStateFlow<List<LoggedRequest>>(emptyList())

    val requestsLog = _requestsLog.asStateFlow()

    init {
        users[1] = UserRecord(1, "Alice", "alice@example.com")
        users[2] = UserRecord(2, "Bob", "bob@example.com")
        users[3] = UserRecord(3, "Charlie", "charlie@example.com")
    }

    fun start(): Int {
        if (engine != null) return resolvedPort()
        val started = embeddedServer(Netty, port = port) {
            dummyApiModule(
                users = users,
                idSequence = idSequence,
                requestLogger = { request ->
                    if (recordRequests) {
                        _requestsLog.value = _requestsLog.value + request
                    }
                },
                validBearerToken = validBearerToken,
                validBasicToken = validBasicToken,
                validApiKey = validApiKey
            )
        }.start(wait = false)
        engine = started
        if (grpcServer == null) {
            startGrpc()
        }
        return resolvedPort()
    }

    fun startGrpc(): Int {
        grpcServer?.let { return it.port }
        val protoStream = DummyApiServer::class.java.getResourceAsStream("/protobuf/greeter.proto")
            ?: error("greeter.proto resource not found on test classpath")
        val metaStream = DummyApiServer::class.java.getResourceAsStream("/protobuf/common/metadata.proto")
            ?: error("metadata.proto resource not found on test classpath")

        val tempDir = java.nio.file.Files.createTempDirectory("dummy_grpc_protos_").toFile()
        tempProtoDir = tempDir

        val metaDir = java.io.File(tempDir, "protobuf/common").apply { mkdirs() }
        val metaFile = java.io.File(metaDir, "metadata.proto")
        metaStream.use { input ->
            metaFile.outputStream().use { output -> input.copyTo(output) }
        }
        tempMetaFile = metaFile

        val protoFile = java.io.File(tempDir, "protobuf/greeter.proto")
        protoStream.use { input ->
            protoFile.outputStream().use { output -> input.copyTo(output) }
        }
        tempProtoFile = protoFile

        val parser = ProtocJarParser()
        val schema = runBlocking {
            parser.parseProtoFile(
                protoFilePath = protoFile.absolutePath,
                includePaths = listOf(metaFile.absolutePath),
            )
        }
        val fds = DescriptorProtos.FileDescriptorSet.parseFrom(
            requireNotNull(schema.fileDescriptorBytes) { "Failed to compile greeter.proto" }
        )
        val fileDescriptors = ProtocJarParser.buildFileDescriptors(fds)
        val serviceDesc = fileDescriptors.flatMap { it.services }.first { it.fullName == "helloworld.Greeter" }

        val customMetaContextKey = Context.key<String>("x-custom-meta")
        val authContextKey = Context.key<String>("authorization")

        fun buildMethodHandler(
            methodDesc: Descriptors.MethodDescriptor,
            prefix: String,
        ): Pair<MethodDescriptor<DynamicMessage, DynamicMessage>, ServerCallHandler<DynamicMessage, DynamicMessage>> {
            val grpcMethod = MethodDescriptor.newBuilder<DynamicMessage, DynamicMessage>()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceDesc.fullName, methodDesc.name))
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDesc.inputType)))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodDesc.outputType)))
                .build()

            val nameField = methodDesc.inputType.findFieldByName("name")!!
            val messageField = methodDesc.outputType.findFieldByName("message")!!
            val receivedMetaField = methodDesc.outputType.findFieldByName("received_meta")
            val ackMetadataField = methodDesc.outputType.findFieldByName("ack_metadata")

            val handler = ServerCalls.asyncUnaryCall<DynamicMessage, DynamicMessage> { req, observer ->
                val name = req.getField(nameField)?.toString().orEmpty()
                when (name) {
                    "denied" -> {
                        observer.onError(
                            Status.PERMISSION_DENIED
                                .withDescription("Access denied for $name")
                                .asRuntimeException()
                        )
                    }
                    "error" -> {
                        observer.onError(
                            Status.INTERNAL
                                .withDescription("Simulated gRPC server error")
                                .asRuntimeException()
                        )
                    }
                    else -> {
                        val customMeta = customMetaContextKey.get().orEmpty()
                        val authMeta = authContextKey.get().orEmpty()
                        val combinedMeta = listOfNotNull(
                            customMeta.takeIf { it.isNotEmpty() },
                            authMeta.takeIf { it.isNotEmpty() }
                        ).joinToString(";")
                        val replyText = if (name.startsWith("large:")) {
                            val requestedBytes = name.removePrefix("large:").toIntOrNull()?.coerceIn(1, 4_000_000) ?: 65_536
                            val base = "$prefix, large-payload:"
                            val padLen = (requestedBytes - base.length).coerceAtLeast(0)
                            base + "x".repeat(padLen)
                        } else {
                            "$prefix, $name!"
                        }
                        val replyBuilder = DynamicMessage.newBuilder(methodDesc.outputType)
                            .setField(messageField, replyText)
                        if (receivedMetaField != null && combinedMeta.isNotEmpty()) {
                            replyBuilder.setField(receivedMetaField, combinedMeta)
                        }
                        if (ackMetadataField != null) {
                            val ackMetaDesc = ackMetadataField.messageType
                            val traceField = ackMetaDesc.findFieldByName("trace_id")
                            val versionField = ackMetaDesc.findFieldByName("client_version")
                            val timeField = ackMetaDesc.findFieldByName("timestamp_epoch_ms")
                            val ackMeta = DynamicMessage.newBuilder(ackMetaDesc).apply {
                                if (traceField != null) setField(traceField, "trace-$name")
                                if (versionField != null) setField(versionField, "clapi-qa-1.0")
                                if (timeField != null) setField(timeField, 1700000000000L)
                            }.build()
                            replyBuilder.setField(ackMetadataField, ackMeta)
                        }
                        observer.onNext(replyBuilder.build())
                        observer.onCompleted()
                    }
                }
            }
            return grpcMethod to handler
        }

        val sayHelloMethod = serviceDesc.findMethodByName("SayHello")!!
        val sayHelloAgainMethod = serviceDesc.findMethodByName("SayHelloAgain")!!
        val (sayHelloDesc, sayHelloHandler) = buildMethodHandler(sayHelloMethod, "Hello")
        val (sayHelloAgainDesc, sayHelloAgainHandler) = buildMethodHandler(sayHelloAgainMethod, "Hello again")

        val serviceDef = ServerServiceDefinition.builder(serviceDesc.fullName)
            .addMethod(sayHelloDesc, sayHelloHandler)
            .addMethod(sayHelloAgainDesc, sayHelloAgainHandler)
            .build()

        val metadataEchoInterceptor = object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>,
            ): ServerCall.Listener<ReqT> {
                val customMetaKey = Metadata.Key.of("x-custom-meta", Metadata.ASCII_STRING_MARSHALLER)
                val authKey = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
                val customVal = headers.get(customMetaKey)
                val authVal = headers.get(authKey)

                val loggedHeaders = mutableMapOf<String, String>()
                for (keyName in headers.keys()) {
                    if (!keyName.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                        val k = Metadata.Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER)
                        headers.get(k)?.let { loggedHeaders[keyName] = it }
                    }
                }
                if (recordRequests) {
                    _requestsLog.value = _requestsLog.value + LoggedRequest(
                        method = "GRPC",
                        path = call.methodDescriptor.fullMethodName,
                        headers = loggedHeaders,
                        epochMs = Clock.System.now().toEpochMilliseconds(),
                    )
                }

                val forwardingCall = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    override fun sendHeaders(responseHeaders: Metadata) {
                        val serverKey = Metadata.Key.of("x-grpc-server", Metadata.ASCII_STRING_MARSHALLER)
                        responseHeaders.put(serverKey, "DummyApiServer")
                        if (customVal != null) {
                            val echoKey = Metadata.Key.of("x-echo-custom-meta", Metadata.ASCII_STRING_MARSHALLER)
                            responseHeaders.put(echoKey, customVal)
                        }
                        super.sendHeaders(responseHeaders)
                    }
                }
                val ctx = Context.current()
                    .withValue(customMetaContextKey, customVal)
                    .withValue(authContextKey, authVal)
                return Contexts.interceptCall(ctx, forwardingCall, headers, next)
            }
        }

        val startedGrpc = NettyServerBuilder.forPort(grpcPort)
            .addService(ServerInterceptors.intercept(serviceDef, metadataEchoInterceptor))
            .build()
            .start()
        grpcServer = startedGrpc
        return startedGrpc.port
    }

    fun stop() {
        grpcServer?.shutdownNow()
        grpcServer = null
        tempProtoFile = null
        tempMetaFile = null
        tempProtoDir?.deleteRecursively()
        tempProtoDir = null
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
    }

    fun baseUrl(): String = "http://127.0.0.1:${resolvedPort()}"

    fun wsUrl(): String = "ws://127.0.0.1:${resolvedPort()}/ws"

    fun wsPort(): Int = resolvedPort()

    fun grpcPort(): Int = requireNotNull(grpcServer) { "gRPC server not started" }.port

    fun grpcEndpoint(): String = "127.0.0.1:${grpcPort()}"

    fun protoFilePath(): String = requireNotNull(tempProtoFile) { "gRPC server not started" }.absolutePath

    fun metadataProtoFilePath(): String = requireNotNull(tempMetaFile) { "gRPC server not started" }.absolutePath

    private fun resolvedPort(): Int {
        val appEngine = requireNotNull(engine) { "Server not started" }
        return runBlocking { appEngine.engine.resolvedConnectors().first().port }
    }
}

fun Application.dummyApiModule(
    users: ConcurrentHashMap<Int, UserRecord>,
    idSequence: AtomicInteger,
    requestLogger: (LoggedRequest) -> Unit,
    validBearerToken: String,
    validBasicToken: String,
    validApiKey: String
) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
    install(WebSockets)

    routing {
        route("/users") {
            get {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@get
                if (call.request.queryParameters["large"] == "true") {
                    val largePayload = buildJsonArray {
                        repeat(5_000) { index ->
                            add(
                                buildJsonObject {
                                    put("id", index + 1)
                                    put("name", "User-$index")
                                    put("email", "user$index@example.com")
                                }
                            )
                        }
                    }
                    call.respond(largePayload)
                    return@get
                }

                call.respond(users.values.sortedBy { it.id })
            }

            post {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@post
                val (name, email) = if (call.request.contentType().match(ContentType.Application.FormUrlEncoded)) {
                    val params = call.receiveParameters()
                    Pair(
                        params["name"] ?: "Unnamed",
                        params["email"] ?: "unknown@example.com"
                    )
                } else {
                    val payloadText = call.receiveText()
                    val payload = Json.parseToJsonElement(payloadText).jsonObject
                    Pair(
                        payload["name"]?.jsonPrimitive?.content ?: "Unnamed",
                        payload["email"]?.jsonPrimitive?.content ?: "unknown@example.com"
                    )
                }
                val id = idSequence.incrementAndGet()
                val created = UserRecord(id, name, email)
                users[id] = created
                call.respond(HttpStatusCode.Created, created)
            }

            options {
                requestLogger(call.logRequest())
                call.response.headers.append("Allow", "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD")
                call.respond(HttpStatusCode.OK)
            }

            head {
                requestLogger(call.logRequest())
                call.response.headers.append("X-Clapi-Head", "ok")
                call.respond(HttpStatusCode.OK)
            }
        }

        route("/users/{id}") {
            put {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@put
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null || users[id] == null) {
                    call.respond(HttpStatusCode.NotFound, jsonError("User not found"))
                    return@put
                }
                val payloadText = call.receiveText()
                val payload = Json.parseToJsonElement(payloadText).jsonObject
                val replaced = UserRecord(
                    id = id,
                    name = payload["name"]?.jsonPrimitive?.content ?: "Unnamed",
                    email = payload["email"]?.jsonPrimitive?.content ?: "unknown@example.com"
                )
                users[id] = replaced
                call.respond(replaced)
            }

            patch {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@patch
                val id = call.parameters["id"]?.toIntOrNull()
                val current = id?.let { users[it] }
                if (id == null || current == null) {
                    call.respond(HttpStatusCode.NotFound, jsonError("User not found"))
                    return@patch
                }
                val payloadText = call.receiveText()
                val payload = Json.parseToJsonElement(payloadText).jsonObject
                val updated = current.copy(
                    name = payload["name"]?.jsonPrimitive?.content ?: current.name,
                    email = payload["email"]?.jsonPrimitive?.content ?: current.email
                )
                users[id] = updated
                call.respond(updated)
            }

            delete {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@delete
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null || users.remove(id) == null) {
                    call.respond(HttpStatusCode.NotFound, jsonError("User not found"))
                    return@delete
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/graphql") {
            requestLogger(call.logRequest())
            val payloadText = call.receiveText()
            val query = runCatching {
                Json.parseToJsonElement(payloadText).jsonObject["query"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("")
            call.respond(
                buildJsonObject {
                    putJsonObject("data") {
                        put("echo", query)
                        putJsonArray("users") {
                            users.values.sortedBy { it.id }.forEach { user ->
                                add(
                                    buildJsonObject {
                                        put("id", user.id)
                                        put("name", user.name)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        get("/graphql") {
            requestLogger(call.logRequest())
            val query = call.request.queryParameters["query"].orEmpty()
            call.respond(
                buildJsonObject {
                    putJsonObject("data") {
                        put("echo", query)
                        putJsonArray("users") {
                            users.values.sortedBy { it.id }.forEach { user ->
                                add(
                                    buildJsonObject {
                                        put("id", user.id)
                                        put("name", user.name)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        post("/upload") {
            requestLogger(call.logRequest())
            var fileCount = 0
            call.receiveMultipart().forEachPart { part ->
                if (part is io.ktor.http.content.PartData.FileItem) {
                    fileCount++
                }
            }
            call.respond(
                buildJsonObject {
                    put("uploadedFiles", fileCount)
                    put("totalBytes", 0L)
                }
            )
        }

        post("/auth/login") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val jsonBody = Json.parseToJsonElement(body).jsonObject
            val username = jsonBody["username"]?.jsonPrimitive?.content
            val password = jsonBody["password"]?.jsonPrimitive?.content

            if (username == "user" && password == "pass") {
                call.respond(
                    buildJsonObject {
                        put("accessToken", validBearerToken)
                        put("tokenType", "Bearer")
                    }
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized, jsonError("Invalid credentials"))
            }
        }

        post("/oauth/token") {
            requestLogger(call.logRequest())
            call.respond(
                buildJsonObject {
                    put("access_token", validBearerToken)
                    put("token_type", "Bearer")
                    put("expires_in", 3600)
                }
            )
        }

        get("/auth/protected") {
            requestLogger(call.logRequest())
            val authHeader = call.request.headers["Authorization"]
            val apiKeyHeader = call.request.headers["X-API-Key"]

            val isBearerOk = authHeader == "Bearer $validBearerToken"
            val isBasicOk = authHeader == "Basic $validBasicToken"
            val isApiKeyOk = apiKeyHeader == validApiKey || call.request.queryParameters["apiKey"] == validApiKey

            if (!isBearerOk && !isBasicOk && !isApiKeyOk) {
                call.respond(HttpStatusCode.Unauthorized, jsonError("Unauthorized"))
                return@get
            }

            call.respond(buildJsonObject { put("authorized", true) })
        }

        get("/stream") {
            requestLogger(call.logRequest())
            call.respondTextWriter(contentType = ContentType.Text.Plain) {
                repeat(5) { idx ->
                    write("event-$idx\n")
                    flush()
                    delay(120)
                }
            }
        }

        webSocket("/ws") {
            requestLogger(call.logRequest())
            send(Frame.Text("connected"))
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text == "close") {
                        send(Frame.Text("bye"))
                        return@webSocket
                    }
                    send(Frame.Text("echo:$text"))
                }
            }
        }

        // ── Body type echo endpoints ────────────────────────────

        post("/echo-xml") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val ct = call.request.contentType().toString()
            call.respond(buildJsonObject {
                put("contentType", ct)
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        post("/echo-html") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val ct = call.request.contentType().toString()
            call.respond(buildJsonObject {
                put("contentType", ct)
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        post("/echo-js") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val ct = call.request.contentType().toString()
            call.respond(buildJsonObject {
                put("contentType", ct)
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        post("/echo-json") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val ct = call.request.contentType().toString()
            val parsedElement = try { Json.parseToJsonElement(body) } catch (_: Exception) { null }
            call.respond(buildJsonObject {
                put("contentType", ct)
                put("bodyLength", body.length)
                put("rawBody", body)
                if (parsedElement != null) {
                    put("parsed", parsedElement)
                }
            })
        }

        post("/echo-form-data") {
            requestLogger(call.logRequest())
            val fields = mutableMapOf<String, String>()
            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is io.ktor.http.content.PartData.FormItem -> {
                        fields[part.name ?: "unknown"] = part.value
                    }
                    is io.ktor.http.content.PartData.FileItem -> {
                        fields[part.name ?: "file"] = "<file:${part.originalFileName}>"
                    }
                    else -> { /* ignore */ }
                }
            }
            call.respond(buildJsonObject {
                put("fieldCount", fields.size)
                putJsonObject("fields") {
                    fields.forEach { (k, v) -> put(k, v) }
                }
            })
        }

        post("/echo-urlencoded") {
            requestLogger(call.logRequest())
            val params = call.receiveParameters()
            call.respond(buildJsonObject {
                put("paramCount", params.entries().size)
                putJsonObject("params") {
                    params.entries().forEach { (k, values) -> put(k, values.firstOrNull() ?: "") }
                }
            })
        }

        // ── Load-test payload endpoints ─────────────────────────

        get("/load/json") {
            requestLogger(call.logRequest())
            val items = call.request.queryParameters["items"]?.toIntOrNull()?.coerceIn(1, 100_000) ?: 5_000
            val paddingBytes = call.request.queryParameters["paddingBytes"]?.toIntOrNull()?.coerceIn(0, 4_096) ?: 64
            val padding = if (paddingBytes > 0) "a".repeat(paddingBytes) else ""
            call.respondTextWriter(contentType = ContentType.Application.Json) {
                write("{\"count\":$items,\"items\":[")
                for (i in 0 until items) {
                    if (i > 0) write(",")
                    write("{\"id\":${i + 1},\"name\":\"User-$i\",\"email\":\"user$i@example.com\",\"status\":\"active\",\"role\":\"member\",\"tags\":[\"load\",\"json\",\"clapi\"],\"payload\":\"$padding\"}")
                }
                write("]}")
                flush()
            }
        }

        get("/load/binary") {
            requestLogger(call.logRequest())
            val sizeMb = call.request.queryParameters["sizeMb"]?.toIntOrNull()?.coerceIn(1, 32) ?: 1
            val chunk = ByteArray(64 * 1024) { idx -> (idx % 251).toByte() }
            val totalChunks = sizeMb * 16
            call.respondOutputStream(contentType = ContentType.Application.OctetStream) {
                repeat(totalChunks) {
                    write(chunk)
                }
                flush()
            }
        }

        get("/load/sse") {
            requestLogger(call.logRequest())
            val events = call.request.queryParameters["events"]?.toIntOrNull()?.coerceIn(1, 5_000) ?: 20
            val delayMs = call.request.queryParameters["delayMs"]?.toLongOrNull()?.coerceIn(0L, 1_000L) ?: 0L
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                for (i in 0 until events) {
                    write("id: $i\nevent: tick\ndata: {\"seq\":$i,\"status\":\"ok\"}\n\n")
                    flush()
                    if (delayMs > 0L) delay(delayMs)
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.simulateDelayOrError(): Boolean {
    when (request.queryParameters["mode"]) {
        "slow" -> delay(900)
        "error" -> {
            respond(HttpStatusCode.InternalServerError, jsonError("Simulated server error"))
            return true
        }
        else -> Unit
    }
    return false
}

private fun io.ktor.server.application.ApplicationCall.logRequest(): LoggedRequest = LoggedRequest(
    method = request.httpMethod.value,
    path = request.uri,
    headers = request.headers.entries().associate { it.key to it.value.joinToString(",") },
    epochMs = Clock.System.now().toEpochMilliseconds()
)

private fun jsonError(message: String): JsonObject = buildJsonObject {
    put("error", JsonPrimitive(message))
}

@Serializable
data class UserRecord(
    val id: Int,
    val name: String,
    val email: String
)

data class LoggedRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val epochMs: Long
)

fun main() {
    val server = DummyApiServer()
    val httpPort = server.start()
    println("HTTP Port: $httpPort")
    println("HTTP Base URL: ${server.baseUrl()}")
    println("WebSocket Port: ${server.wsPort()}")
    println("WebSocket URL: ${server.wsUrl()}")
    println("gRPC Port: ${server.grpcPort()}")
    println("gRPC Endpoint: ${server.grpcEndpoint()}")
    println("gRPC Proto Path: ${server.protoFilePath()}")
    println("gRPC Common Metadata Proto Path: ${server.metadataProtoFilePath()}")
    while (true) {
        Thread.sleep(100)
    }
}