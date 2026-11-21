package io.github.crustack.qa

import io.github.crustack.core.model.opencollection.AuthBearer
import io.github.crustack.core.model.opencollection.CollectionConfig
import io.github.crustack.core.model.opencollection.CollectionInfo
import io.github.crustack.core.model.opencollection.Environment
import io.github.crustack.core.model.opencollection.FolderInfo
import io.github.crustack.core.model.opencollection.FolderItem
import io.github.crustack.core.model.opencollection.HttpRequestDetails
import io.github.crustack.core.model.opencollection.HttpRequestHeader
import io.github.crustack.core.model.opencollection.HttpRequestInfo
import io.github.crustack.core.model.opencollection.HttpRequestItem
import io.github.crustack.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.core.model.opencollection.RegularVariable
import io.github.crustack.core.model.opencollection.SecretVariable
import io.github.crustack.core.model.opencollection.VariableValue
import io.github.crustack.core.storage.opencollection.CollectionValidationException
import io.github.crustack.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.crustack.core.storage.opencollection.ExportFormat
import io.github.crustack.core.storage.opencollection.OpenCollectionSchemaValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Validates that every OpenCollection document the repository writes or accepts
 * conforms to the bundled OpenCollection v1.0.0 schema.
 */
class OpenCollectionValidationE2ETest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val validator = OpenCollectionSchemaValidator()

    private fun sampleDocument(): OpenCollectionDocument = OpenCollectionDocument(
        opencollection = "1.0.0",
        info = CollectionInfo(name = "Sample Collection", version = "1.0.0"),
        bundled = true,
        config = CollectionConfig(
            environments = listOf(
                Environment(
                    name = "Production",
                    variables = listOf(
                        RegularVariable(name = "baseUrl", value = VariableValue.of("https://api.example.com")),
                        SecretVariable(name = "apiKey", type = "string"),
                    ),
                ),
            ),
        ),
        items = listOf(
            FolderItem(
                info = FolderInfo(name = "Users", type = "folder"),
                items = listOf(
                    HttpRequestItem(
                        info = HttpRequestInfo(name = "List Users", type = "http"),
                        http = HttpRequestDetails(
                            method = "GET",
                            url = "{{baseUrl}}/users",
                            headers = listOf(HttpRequestHeader(name = "Accept", value = "application/json")),
                            auth = AuthBearer(token = "bearer-token"),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun savedJsonFileConformsToSchema() = runBlocking {
        val repo = DefaultCanonicalCollectionRepository(baseDirectory = tempDir.root.absolutePath)
        repo.saveCollection(sampleDocument())

        val file = File(tempDir.root, "Sample_Collection.json")
        assertTrue(file.exists(), "Expected saved collection file at ${file.path}")
        val issues = validator.validate(file.readText())
        assertTrue(issues.isEmpty(), "Saved file has schema issues: $issues")
    }

    @Test
    fun exportedJsonAndYamlConformToSchema() = runBlocking {
        val repo = DefaultCanonicalCollectionRepository()
        val doc = sampleDocument()

        val json = repo.exportCollection(doc, ExportFormat.JSON)
        val yaml = repo.exportCollection(doc, ExportFormat.YAML)

        assertTrue(validator.validate(json).isEmpty(), "JSON export has schema issues")
        assertTrue(validator.validateYaml(yaml).isEmpty(), "YAML export has schema issues")
    }

    @Test
    fun saveRejectsUnsupportedVersionAndKeepsExistingFile() = runBlocking {
        val repo = DefaultCanonicalCollectionRepository(baseDirectory = tempDir.root.absolutePath)
        repo.saveCollection(sampleDocument())

        val file = File(tempDir.root, "Sample_Collection.json")
        val bytesBefore = file.readBytes()

        val invalid = sampleDocument().copy(opencollection = "2.0.0")
        val error = assertFailsWith<CollectionValidationException> {
            repo.saveCollection(invalid)
        }
        assertTrue(
            error.issues.any { it.path == "$.opencollection" },
            "Expected an issue at \$.opencollection, got: ${error.issues}",
        )

        assertEquals(bytesBefore.toList(), file.readBytes().toList(), "File must not change after rejected save")
        assertEquals("1.0.0", repo.getCollection("Sample Collection")?.opencollection)
    }

    @Test
    fun importRejectsNonStringVersionWithoutStoringAnything() = runBlocking {
        val repo = DefaultCanonicalCollectionRepository(baseDirectory = tempDir.root.absolutePath)
        val raw = """{"opencollection": 1, "info": {"name": "Bad", "version": "1.0.0"}, "items": []}"""

        assertFailsWith<CollectionValidationException> {
            repo.importCollection(raw)
        }

        assertTrue(repo.observeCollections().first().isEmpty(), "Rejected import must not be stored in state")
        assertTrue(tempDir.root.listFiles().orEmpty().isEmpty(), "Rejected import must not write a file")
    }

    @Test
    fun importRejectsUnsupportedYamlVersion() = runBlocking {
        val repo = DefaultCanonicalCollectionRepository()
        val raw = """
            opencollection: "2.0.0"
            info:
              name: Bad
              version: "1.0.0"
            items: []
        """.trimIndent()

        val error = assertFailsWith<CollectionValidationException> {
            repo.importCollection(raw)
        }
        assertTrue(error.issues.any { it.path == "$.opencollection" })
        assertTrue(repo.observeCollections().first().isEmpty())
    }
}
