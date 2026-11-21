package io.github.makeforge.clapi.core.storage.git

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtoPathResolverTest {

    @Test
    fun `resolve translates relative protobuf path against collection repository directory`() {
        val collectionDir = "/home/user/.clapi/storage/repo_123/Users_API"
        val relativePath = "./protobuf/collection/user_service.proto"

        val resolved = ProtoPathResolver.resolve(relativePath, collectionDir)

        assertEquals(
            "/home/user/.clapi/storage/repo_123/Users_API/protobuf/collection/user_service.proto",
            resolved,
        )
        assertTrue(ProtoPathResolver.isRelativeGitProtoPath(relativePath))
    }

    @Test
    fun `resolve preserves unix and windows absolute paths unchanged`() {
        val collectionDir = "/home/user/.clapi/storage/repo_123/Users_API"
        val unixAbs = "/Users/alice/protos/payment.proto"
        val winAbs = "C:\\Users\\alice\\protos\\payment.proto"

        assertEquals(unixAbs, ProtoPathResolver.resolve(unixAbs, collectionDir))
        assertEquals(winAbs, ProtoPathResolver.resolve(winAbs, collectionDir))
        assertFalse(ProtoPathResolver.isRelativeGitProtoPath(unixAbs))
        assertFalse(ProtoPathResolver.isRelativeGitProtoPath(winAbs))
    }

    @Test
    fun `resolveAll resolves mixed relative and absolute import paths`() {
        val collectionDir = "/home/user/.clapi/storage/repo_123/Users_API"
        val rawPaths = listOf(
            "./protobuf/collection/imports/common",
            "/opt/google/protobuf/include",
        )

        val resolved = ProtoPathResolver.resolveAll(rawPaths, collectionDir)

        assertEquals(
            listOf(
                "/home/user/.clapi/storage/repo_123/Users_API/protobuf/collection/imports/common",
                "/opt/google/protobuf/include",
            ),
            resolved,
        )
    }

    @Test
    fun `syncCollectionProtoFile and syncRequestProtoFile copy files into protobuf subdirectories`() {
        val tempDir = File.createTempFile("clapi_proto_resolver_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val sourceDir = File(tempDir, "source").apply { mkdirs() }
            val collectionDir = File(tempDir, "collection").apply { mkdirs() }

            val colProto = File(sourceDir, "common.proto").apply {
                writeText("syntax = \"proto3\";\npackage common;\n")
            }
            val reqProto = File(sourceDir, "orders.proto").apply {
                writeText("syntax = \"proto3\";\npackage orders;\n")
            }
            val importDir = File(sourceDir, "google_apis").apply { mkdirs() }
            File(importDir, "annotations.proto").writeText("syntax = \"proto3\";\n")

            val colRel = ProtoPathResolver.syncCollectionProtoFile(colProto.absolutePath, collectionDir.absolutePath)
            assertEquals("./protobuf/collection/common.proto", colRel)
            assertTrue(File(collectionDir, "protobuf/collection/common.proto").exists())

            val reqRel = ProtoPathResolver.syncRequestProtoFile(reqProto.absolutePath, "req-42", collectionDir.absolutePath)
            assertEquals("./protobuf/requests/req-42/orders.proto", reqRel)
            assertTrue(File(collectionDir, "protobuf/requests/req-42/orders.proto").exists())

            val impRel = ProtoPathResolver.syncCollectionImportPath(importDir.absolutePath, collectionDir.absolutePath)
            assertEquals("./protobuf/collection/imports/google_apis", impRel)
            assertTrue(File(collectionDir, "protobuf/collection/imports/google_apis/annotations.proto").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `syncRequestProtoFolder copies only proto files recursively cleans stale files and is idempotent`() {
        val tempDir = File.createTempFile("clapi_proto_folder_sync_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val sourceFolder = File(tempDir, "protobuf").apply { mkdirs() }
            val commonDir = File(sourceFolder, "common").apply { mkdirs() }
            val greeterProto = File(sourceFolder, "greeter.proto").apply {
                writeText("syntax = \"proto3\";\npackage helloworld;\nimport \"protobuf/common/metadata.proto\";\n")
            }
            File(commonDir, "metadata.proto").writeText("syntax = \"proto3\";\npackage clapi.common;\n")
            File(sourceFolder, "README.md").writeText("# Should not be synced")
            File(commonDir, "notes.txt").writeText("Also should not be synced")

            val collectionDir = File(tempDir, "collection").apply { mkdirs() }

            val synced = ProtoPathResolver.syncRequestProtoFolder(
                sourceFolderPath = sourceFolder.absolutePath,
                sourceProtoFilePath = greeterProto.absolutePath,
                requestId = "req-99",
                collectionDir = collectionDir.absolutePath,
            )

            assertEquals("./protobuf/requests/req-99/protobuf", synced.folderRelPath)
            assertEquals("./protobuf/requests/req-99/protobuf/greeter.proto", synced.protoFileRelPath)
            assertTrue(File(collectionDir, "protobuf/requests/req-99/protobuf/greeter.proto").exists())
            assertTrue(File(collectionDir, "protobuf/requests/req-99/protobuf/common/metadata.proto").exists())
            assertFalse(File(collectionDir, "protobuf/requests/req-99/protobuf/README.md").exists())
            assertFalse(File(collectionDir, "protobuf/requests/req-99/protobuf/common/notes.txt").exists())

            // Re-saving an already-synced relative folder must be idempotent and keep existing files
            val resynced = ProtoPathResolver.syncRequestProtoFolder(
                sourceFolderPath = synced.folderRelPath,
                sourceProtoFilePath = synced.protoFileRelPath,
                requestId = "req-99",
                collectionDir = collectionDir.absolutePath,
            )
            assertEquals(synced, resynced)
            assertTrue(File(collectionDir, "protobuf/requests/req-99/protobuf/greeter.proto").exists())
            assertTrue(File(collectionDir, "protobuf/requests/req-99/protobuf/common/metadata.proto").exists())

            // Replacing with a new folder of the same name cleans stale files
            val replacementFolder = File(tempDir, "v2/protobuf").apply { mkdirs() }
            val newProto = File(replacementFolder, "orders.proto").apply {
                writeText("syntax = \"proto3\";\npackage orders;\n")
            }
            val replaced = ProtoPathResolver.syncRequestProtoFolder(
                sourceFolderPath = replacementFolder.absolutePath,
                sourceProtoFilePath = newProto.absolutePath,
                requestId = "req-99",
                collectionDir = collectionDir.absolutePath,
            )
            assertEquals("./protobuf/requests/req-99/protobuf/orders.proto", replaced.protoFileRelPath)
            assertTrue(File(collectionDir, "protobuf/requests/req-99/protobuf/orders.proto").exists())
            assertFalse(File(collectionDir, "protobuf/requests/req-99/protobuf/greeter.proto").exists())
            assertFalse(File(collectionDir, "protobuf/requests/req-99/protobuf/common/metadata.proto").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
