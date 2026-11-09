package io.github.crustack.clapi.core.model.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageExtensionTest {

    @Test
    fun local_storage_extension_is_valid() {
        val local = StorageExtension(type = StorageType.LOCAL)
        assertTrue(local.validate().isEmpty())
    }

    @Test
    fun git_storage_extension_requires_git_config_and_valid_fields() {
        val missingGit = StorageExtension(type = StorageType.GIT, git = null)
        val errors1 = missingGit.validate()
        assertFalse(errors1.isEmpty())
        assertTrue(errors1.any { it.contains("Git storage configuration is required") })

        val blankFields = StorageExtension(
            type = StorageType.GIT,
            git = GitStorageConfig(remoteUrl = "", branch = "", credentials = GitCredentialsConfig(type = "custom")),
        )
        val errors2 = blankFields.validate()
        assertEquals(3, errors2.size)

        val validGit = StorageExtension(
            type = StorageType.GIT,
            git = GitStorageConfig(remoteUrl = "git@github.com:org/repo.git", branch = "main"),
        )
        assertTrue(validGit.validate().isEmpty())
    }

    @Test
    fun extension_map_roundtrip() {
        val extension = StorageExtension(
            type = StorageType.GIT,
            git = GitStorageConfig(remoteUrl = "https://github.com/user/api.git", branch = "dev"),
        )
        val map = emptyMap<String, kotlinx.serialization.json.JsonElement>().withStorageExtension(extension)
        val decoded = map.getStorageExtension()
        assertEquals(StorageType.GIT, decoded?.type)
        assertEquals("https://github.com/user/api.git", decoded?.git?.remoteUrl)
        assertEquals("dev", decoded?.git?.branch)
        assertEquals("system", decoded?.git?.credentials?.type)
    }
}
