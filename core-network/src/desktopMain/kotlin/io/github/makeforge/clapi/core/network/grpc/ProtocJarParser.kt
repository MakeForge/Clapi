package io.github.makeforge.clapi.core.network.grpc

import com.github.os72.protocjar.Protoc
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ProtocJarParser(
    private val collectionBaseDir: String? = null,
) : ProtoParser {

    suspend fun parseProtoFile(
        protoFile: File,
        includePaths: List<File> = emptyList(),
    ): ProtoSchemaDefinition = parseProtoFile(
        protoFilePath = protoFile.absolutePath,
        includePaths = includePaths.map { it.absolutePath },
    )

    suspend fun parseProtoFile(
        protoFilePath: String,
        includePaths: List<String>,
        baseDir: String?,
    ): ProtoSchemaDefinition {
        val resolvedProto = resolvePath(protoFilePath, baseDir)
        val resolvedIncludes = includePaths.map { resolvePath(it, baseDir) }
        return parseProtoFile(resolvedProto, resolvedIncludes)
    }

    private fun resolvePath(rawPath: String, explicitBaseDir: String? = collectionBaseDir): String {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return ""
        val file = File(trimmed)
        if (file.isAbsolute) return trimmed
        val base = explicitBaseDir?.takeIf { it.isNotBlank() } ?: return trimmed
        val rel = trimmed.replace('\\', '/').removePrefix("./").trimStart('/')
        return File(base, rel).absolutePath
    }

    override suspend fun parseProtoFile(
        protoFilePath: String,
        includePaths: List<String>,
    ): ProtoSchemaDefinition = withContext(Dispatchers.IO) {
        val resolvedMainPath = resolvePath(protoFilePath)
        val protoFile = File(resolvedMainPath)
        if (!protoFile.exists() || !protoFile.isFile) {
            throw ProtoParseException("Proto file does not exist: $protoFilePath")
        }

        val inferredCollectionDir = inferCollectionDirFromProtoFile(protoFile) ?: collectionBaseDir
        val resolvedIncludePaths = includePaths.map { resolvePath(it, inferredCollectionDir) }

        val tempDescriptorFile = File.createTempFile("clapi_proto_", ".desc")
        val stagingDir = File.createTempFile("clapi_proto_stage_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val stagedTargetFile = File(stagingDir, protoFile.name)
            protoFile.copyTo(stagedTargetFile, overwrite = true)
            protoFile.parentFile?.listFiles()?.forEach { sibling ->
                if (sibling.isFile && sibling.extension == "proto" && sibling.name != protoFile.name) {
                    sibling.copyTo(File(stagingDir, sibling.name), overwrite = true)
                }
            }

            val allIncludeDirs = normalizeIncludeDirs(stagingDir, resolvedIncludePaths)

            val args = buildList {
                add("--include_imports")
                add("--include_std_types")
                allIncludeDirs.forEach { dir ->
                    add("-I$dir")
                }
                add("--descriptor_set_out=${tempDescriptorFile.absolutePath}")
                add(stagedTargetFile.absolutePath)
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
            runCatching { stagingDir.deleteRecursively() }
        }
    }

    private fun normalizeIncludeDirs(stagingDir: File, includePaths: List<String>): List<String> {
        val dirs = LinkedHashSet<String>()

        dirs.add(stagingDir.absolutePath)

        fun addDirectoryAndAncestors(startDir: File?) {
            var current: File? = startDir?.canonicalFile ?: startDir?.absoluteFile
            var depth = 0
            while (current != null && current.exists() && current.isDirectory && depth < 10) {
                dirs.add(current.absolutePath)
                current = current.parentFile
                depth++
            }
        }

        for (rawPath in includePaths) {
            val trimmed = rawPath.trim()
            if (trimmed.isEmpty()) continue
            val f = File(trimmed).let { if (it.isAbsolute) it else it.absoluteFile }
            if (!f.exists()) continue
            if (f.isDirectory) {
                addDirectoryAndAncestors(f)
            } else if (f.isFile) {
                f.parentFile?.let { addDirectoryAndAncestors(it) }
            }
        }

        return dirs.toList()
    }

    private fun inferCollectionDirFromProtoFile(protoFile: File): String? {
        var current: File? = protoFile.parentFile
        while (current != null) {
            if (current.name == "protobuf" && current.parentFile != null) {
                return current.parentFile.absolutePath
            }
            current = current.parentFile
        }
        return null
    }

    companion object {
        val WELL_KNOWN_DESCRIPTORS: Map<String, Descriptors.FileDescriptor> by lazy {
            listOf(
                com.google.protobuf.AnyProto.getDescriptor(),
                com.google.protobuf.ApiProto.getDescriptor(),
                com.google.protobuf.DurationProto.getDescriptor(),
                com.google.protobuf.EmptyProto.getDescriptor(),
                com.google.protobuf.FieldMaskProto.getDescriptor(),
                com.google.protobuf.SourceContextProto.getDescriptor(),
                com.google.protobuf.StructProto.getDescriptor(),
                com.google.protobuf.TimestampProto.getDescriptor(),
                com.google.protobuf.TypeProto.getDescriptor(),
                com.google.protobuf.WrappersProto.getDescriptor(),
                com.google.protobuf.DescriptorProtos.getDescriptor(),
                com.google.protobuf.compiler.PluginProtos.getDescriptor(),
            ).associateBy { it.name }
        }

        fun buildFileDescriptors(
            fileDescriptorSet: DescriptorProtos.FileDescriptorSet,
        ): List<Descriptors.FileDescriptor> {
            val protoMap = fileDescriptorSet.fileList.associateBy { it.name }
            val builtDescriptors = mutableMapOf<String, Descriptors.FileDescriptor>()

            fun resolve(proto: DescriptorProtos.FileDescriptorProto): Descriptors.FileDescriptor {
                builtDescriptors[proto.name]?.let { return it }
                val deps = proto.dependencyList.mapNotNull { depName ->
                    protoMap[depName]?.let { resolve(it) } ?: WELL_KNOWN_DESCRIPTORS[depName]
                }.toTypedArray()
                val fd = Descriptors.FileDescriptor.buildFrom(proto, deps)
                builtDescriptors[proto.name] = fd
                return fd
            }

            return fileDescriptorSet.fileList.map { resolve(it) }
        }
    }
}
