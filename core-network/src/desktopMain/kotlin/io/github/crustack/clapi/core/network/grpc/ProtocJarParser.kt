package io.github.crustack.clapi.core.network.grpc

import com.github.os72.protocjar.Protoc
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ProtocJarParser : ProtoParser {

    suspend fun parseProtoFile(
        protoFile: File,
        includePaths: List<File> = emptyList(),
    ): ProtoSchemaDefinition = parseProtoFile(
        protoFilePath = protoFile.absolutePath,
        includePaths = includePaths.map { it.absolutePath },
    )

    override suspend fun parseProtoFile(
        protoFilePath: String,
        includePaths: List<String>,
    ): ProtoSchemaDefinition = withContext(Dispatchers.IO) {
        val protoFile = File(protoFilePath)
        if (!protoFile.exists() || !protoFile.isFile) {
            throw ProtoParseException("Proto file does not exist: $protoFilePath")
        }

        val tempDescriptorFile = File.createTempFile("clapi_proto_", ".desc")
        try {
            val parentDir = protoFile.parentFile?.absolutePath ?: "."
            val allIncludeDirs = (listOf(parentDir) + includePaths).distinct()

            val args = buildList {
                add("--include_imports")
                add("--include_std_types")
                allIncludeDirs.forEach { dir ->
                    add("-I$dir")
                }
                add("--descriptor_set_out=${tempDescriptorFile.absolutePath}")
                add(protoFile.absolutePath)
            }.toTypedArray()

            val stdoutStream = ByteArrayOutputStream()
            val stderrStream = ByteArrayOutputStream()
            val exitCode = try {
                Protoc.runProtoc(args, stdoutStream, stderrStream)
            } catch (e: Exception) {
                throw ProtoParseException(
                    message = "Failed to invoke protoc for ${protoFile.name}: ${e.message}",
                    stderr = stderrStream.toString(Charsets.UTF_8.name()).ifBlank { null },
                    cause = e,
                )
            }

            if (exitCode != 0) {
                val errOutput = stderrStream.toString(Charsets.UTF_8.name()).trim()
                    .ifBlank { stdoutStream.toString(Charsets.UTF_8.name()).trim() }
                throw ProtoParseException(
                    message = "Failed to compile ${protoFile.name} (exit code $exitCode)${if (errOutput.isNotEmpty()) ": $errOutput" else ""}",
                    stderr = errOutput.ifBlank { null },
                )
            }

            val descriptorBytes = tempDescriptorFile.readBytes()
            val fileDescriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(descriptorBytes)
            val fileDescriptors = buildFileDescriptors(fileDescriptorSet)

            val services = fileDescriptors
                .filter { fd -> fd.`package` != "google.protobuf" }
                .flatMap { fd ->
                    fd.services.map { service ->
                        ProtoServiceInfo(
                            fullName = service.fullName,
                            methods = service.methods.map { method ->
                                ProtoMethodInfo(
                                    serviceFullName = service.fullName,
                                    methodName = method.name,
                                    fullMethodName = "${service.fullName}/${method.name}",
                                    inputTypeName = method.inputType.fullName,
                                    outputTypeName = method.outputType.fullName,
                                    isClientStreaming = method.isClientStreaming,
                                    isServerStreaming = method.isServerStreaming,
                                )
                            },
                        )
                    }
                }

            ProtoSchemaDefinition(
                protoFilePath = protoFile.absolutePath,
                services = services,
                fileDescriptorBytes = descriptorBytes,
            )
        } finally {
            runCatching { tempDescriptorFile.delete() }
        }
    }

    companion object {
        fun buildFileDescriptors(
            fileDescriptorSet: DescriptorProtos.FileDescriptorSet,
        ): List<Descriptors.FileDescriptor> {
            val protoMap = fileDescriptorSet.fileList.associateBy { it.name }
            val builtDescriptors = mutableMapOf<String, Descriptors.FileDescriptor>()

            fun resolve(proto: DescriptorProtos.FileDescriptorProto): Descriptors.FileDescriptor {
                builtDescriptors[proto.name]?.let { return it }
                val deps = proto.dependencyList.mapNotNull { depName ->
                    protoMap[depName]?.let { resolve(it) }
                }.toTypedArray()
                val fd = Descriptors.FileDescriptor.buildFrom(proto, deps)
                builtDescriptors[proto.name] = fd
                return fd
            }

            return fileDescriptorSet.fileList.map { resolve(it) }
        }
    }
}
