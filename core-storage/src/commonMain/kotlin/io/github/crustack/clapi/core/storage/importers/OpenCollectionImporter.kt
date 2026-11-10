package io.github.crustack.clapi.core.storage.importers

import io.github.crustack.clapi.core.model.importers.ImportCandidateEnvironment
import io.github.crustack.clapi.core.model.importers.ImportCandidateFolder
import io.github.crustack.clapi.core.model.importers.ImportScanResult
import io.github.crustack.clapi.core.model.importers.SpecFormat
import io.github.crustack.clapi.core.model.opencollection.FolderInfo
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionItem
import io.github.crustack.clapi.core.storage.opencollection.OpenCollectionSchemaValidator
import io.github.crustack.clapi.core.storage.opencollection.OpenCollectionSerializer
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

class OpenCollectionImporter(
    private val validator: OpenCollectionSchemaValidator = OpenCollectionSchemaValidator()
) : SpecificationImporter {

    override suspend fun scanContent(rawContent: String): ImportScanResult {
        val issues = validator.validateYaml(rawContent)
        if (issues.isNotEmpty()) {
            return ImportScanResult.Failure(
                message = "Invalid OpenCollection specification: ${issues.first().message}",
                details = issues.map { "${it.path}: ${it.message}" }
            )
        }

        val document: OpenCollectionDocument = try {
            OpenCollectionSerializer.deserializeYaml(rawContent)
        } catch (e: Exception) {
            return ImportScanResult.Failure(
                message = "Failed to deserialize OpenCollection: ${e.message ?: "Unknown error"}",
                details = listOfNotNull(e.message)
            )
        }

        val folderId = (document.extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        val folderItem = FolderItem(
            info = FolderInfo(name = document.info.name, type = "folder"),
            items = document.items,
            request = document.request,
            docs = document.docs,
            extensions = document.extensions + ("id" to JsonPrimitive(folderId)),
        )

        val candidateFolder = ImportCandidateFolder(
            id = folderId,
            originalName = document.info.name,
            name = document.info.name,
            folder = folderItem,
            requestCount = countRequests(document.items),
            selected = true,
            hasConflict = false,
        )

        val envs = document.config?.environments ?: emptyList()
        val candidateEnvironments = envs.map { env ->
            ImportCandidateEnvironment(
                id = UUID.randomUUID().toString(),
                originalName = env.name,
                name = env.name,
                environment = env,
                variableCount = env.variables.size,
                selected = true,
                hasConflict = false,
            )
        }

        return ImportScanResult.Success(
            format = SpecFormat.OPENCOLLECTION,
            specTitle = document.info.name,
            folders = listOf(candidateFolder),
            environments = candidateEnvironments,
        )
    }

    private fun countRequests(items: List<OpenCollectionItem>): Int {
        var count = 0
        for (item in items) {
            when (item) {
                is HttpRequestItem -> count++
                is io.github.crustack.clapi.core.model.opencollection.GrpcRequestItem -> count++
                is io.github.crustack.clapi.core.model.opencollection.WebSocketRequestItem -> count++
                is io.github.crustack.clapi.core.model.opencollection.GraphQLRequestItem -> count++
                is FolderItem -> count += countRequests(item.items)
            }
        }
        return count
    }
}
