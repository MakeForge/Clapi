package io.github.crustack.clapi.core.network.grpc

data class ProtoMethodInfo(
    val serviceFullName: String,
    val methodName: String,
    val fullMethodName: String, // "package.Service/Method"
    val inputTypeName: String,
    val outputTypeName: String,
    val isClientStreaming: Boolean,
    val isServerStreaming: Boolean,
)

data class ProtoSchemaDefinition(
    val protoFilePath: String,
    val services: List<ProtoServiceInfo>,
    val fileDescriptorBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ProtoSchemaDefinition

        if (protoFilePath != other.protoFilePath) return false
        if (services != other.services) return false
        if (fileDescriptorBytes != null) {
            if (other.fileDescriptorBytes == null) return false
            if (!fileDescriptorBytes.contentEquals(other.fileDescriptorBytes)) return false
        } else if (other.fileDescriptorBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protoFilePath.hashCode()
        result = 31 * result + services.hashCode()
        result = 31 * result + (fileDescriptorBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class ProtoServiceInfo(
    val fullName: String,
    val methods: List<ProtoMethodInfo>,
)

data class GrpcCallRequest(
    val requestId: String,
    val host: String,
    val port: Int,
    val useTls: Boolean = false,
    val protoFilePath: String,
    val fullMethodName: String,
    val jsonPayload: String,
    val metadata: List<Pair<String, String>> = emptyList(),
    val includePaths: List<String> = emptyList(),
)

data class GrpcCallResponse(
    val requestId: String,
    val statusCode: Int,
    val statusDescription: String,
    val responseJson: String?,
    val durationMs: Long,
    val responseMetadata: List<Pair<String, String>> = emptyList(),
    val errorMessage: String? = null,
)

data class GrpcEndpoint(
    val host: String,
    val port: Int,
    val useTls: Boolean,
)

fun parseGrpcEndpoint(rawUrl: String): GrpcEndpoint {
    var working = rawUrl.trim()
    var useTls = false
    var defaultPort = 50051

    when {
        working.startsWith("grpcs://", ignoreCase = true) -> {
            useTls = true
            defaultPort = 443
            working = working.substring("grpcs://".length)
        }
        working.startsWith("https://", ignoreCase = true) -> {
            useTls = true
            defaultPort = 443
            working = working.substring("https://".length)
        }
        working.startsWith("grpc://", ignoreCase = true) -> {
            useTls = false
            defaultPort = 50051
            working = working.substring("grpc://".length)
        }
        working.startsWith("http://", ignoreCase = true) -> {
            useTls = false
            defaultPort = 80
            working = working.substring("http://".length)
        }
    }

    val authority = working.substringBefore('/').substringBefore('?').trim()
    val colonIdx = authority.lastIndexOf(':')
    return if (colonIdx > 0 && !authority.endsWith(']')) {
        val host = authority.substring(0, colonIdx).trim()
        val port = authority.substring(colonIdx + 1).trim().toIntOrNull() ?: defaultPort
        if (port == 443 && !rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("grpc://", ignoreCase = true)) {
            useTls = true
        }
        GrpcEndpoint(host = host, port = port, useTls = useTls)
    } else {
        GrpcEndpoint(host = authority, port = defaultPort, useTls = useTls)
    }
}
