package io.github.makeforge.clapi.core.network.grpc

interface ProtoParser {
    /**
     * Compiles a .proto file and its imports into a [ProtoSchemaDefinition].
     *
     * @param protoFilePath Absolute path to the target .proto file on disk.
     * @param includePaths Optional additional import directories (defaults to protoFile's parent directory).
     * @return [ProtoSchemaDefinition] containing services, methods, and raw descriptor bytes.
     * @throws ProtoParseException if the file does not exist or fails protoc compilation.
     */
    suspend fun parseProtoFile(
        protoFilePath: String,
        includePaths: List<String> = emptyList(),
    ): ProtoSchemaDefinition
}

class ProtoParseException(
    message: String,
    val stderr: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
