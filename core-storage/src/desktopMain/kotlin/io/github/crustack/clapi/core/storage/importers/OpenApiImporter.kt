package io.github.crustack.clapi.core.storage.importers

import io.github.crustack.clapi.core.model.importers.ImportCandidateEnvironment
import io.github.crustack.clapi.core.model.importers.ImportCandidateFolder
import io.github.crustack.clapi.core.model.importers.ImportScanResult
import io.github.crustack.clapi.core.model.importers.SpecFormat
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.FolderInfo
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestDetails
import io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader
import io.github.crustack.clapi.core.model.opencollection.HttpRequestInfo
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestParam
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionItem
import io.github.crustack.clapi.core.model.opencollection.RawBody
import io.github.crustack.clapi.core.model.opencollection.RegularVariable
import io.github.crustack.clapi.core.model.opencollection.VariableValue
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.parser.core.models.ParseOptions
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

actual class OpenApiImporter actual constructor() : SpecificationImporter {

    actual override suspend fun scanContent(rawContent: String): ImportScanResult {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return ImportScanResult.Failure("Input content is empty.")
        }

        // Gate: Reject Swagger 2.0 with explicit diagnostic message
        val isSwagger2 = trimmed.contains("swagger:") ||
            trimmed.contains("\"swagger\":") ||
            trimmed.contains("'swagger':")
        if (isSwagger2) {
            return ImportScanResult.Failure("Swagger 2.0 is not supported. Please provide an OpenAPI v3.0+ specification.")
        }

        val parseOptions = ParseOptions().apply {
            isResolve = true
            isResolveFully = false
        }
        val parseResult = OpenAPIParser().readContents(rawContent, null, parseOptions)
        val openAPI: OpenAPI? = parseResult.openAPI

        if (openAPI == null) {
            val messages = parseResult.messages ?: emptyList()
            val msg = if (messages.isNotEmpty()) {
                "Invalid OpenAPI specification: " + messages.joinToString("; ")
            } else {
                "Invalid OpenAPI specification: Could not parse content."
            }
            return ImportScanResult.Failure(msg, messages)
        }

        val openApiVersion = openAPI.getOpenapi() ?: ""
        if (!openApiVersion.startsWith("3.")) {
            return ImportScanResult.Failure(
                "Swagger 2.0 is not supported. Please provide an OpenAPI v3.0+ specification."
            )
        }

        val specTitle = openAPI.getInfo()?.getTitle()?.takeIf { s: String -> s.isNotBlank() } ?: "OpenAPI Specification"
        val rootFolderId = UUID.randomUUID().toString()

        // Organize operations by tag
        val taggedItems = mutableMapOf<String, MutableList<OpenCollectionItem>>()
        val untaggedItems = mutableListOf<OpenCollectionItem>()

        val paths = openAPI.getPaths()
        paths?.forEach { (path: String, pathItem: PathItem) ->
            val operationsMap = pathItem.readOperationsMap() ?: emptyMap()
            operationsMap.forEach { (httpMethod: PathItem.HttpMethod, operation: Operation) ->
                val methodStr = httpMethod.name.uppercase()
                val operationSummary = operation.getSummary()?.takeIf { s: String -> s.isNotBlank() }
                    ?: operation.getOperationId()?.takeIf { s: String -> s.isNotBlank() }
                    ?: "$methodStr $path"

                val pathParams: List<Parameter> = pathItem.getParameters() ?: emptyList()
                val opParams: List<Parameter> = operation.getParameters() ?: emptyList()
                val allParameters: List<Parameter> = pathParams + opParams

                val headers = allParameters
                    .filter { it.getIn() == "header" }
                    .map { param ->
                        HttpRequestHeader(
                            name = param.getName() ?: "",
                            value = param.getExample()?.toString()
                                ?: param.getSchema()?.getExample()?.toString()
                                ?: ""
                        )
                    }

                val params = allParameters
                    .filter { it.getIn() == "query" || it.getIn() == "path" }
                    .map { param ->
                        HttpRequestParam(
                            name = param.getName() ?: "",
                            value = param.getExample()?.toString()
                                ?: param.getSchema()?.getExample()?.toString()
                                ?: "",
                            type = if (param.getIn() == "path") "path" else "query"
                        )
                    }

                val jsonContent = operation.getRequestBody()?.getContent()?.get("application/json")
                val bodyData = jsonContent?.getExample()?.toString()
                    ?: jsonContent?.getSchema()?.getExample()?.toString()
                    ?: (if (jsonContent != null) "{}" else null)
                val body = bodyData?.let { RawBody(type = "json", data = it) }

                val formattedPath = if (path.startsWith("/")) path else "/$path"
                val url = "{{baseUrl}}$formattedPath"

                val reqId = UUID.randomUUID().toString()
                val requestItem = HttpRequestItem(
                    info = HttpRequestInfo(name = operationSummary, type = "http"),
                    http = HttpRequestDetails(
                        method = methodStr,
                        url = url,
                        headers = headers,
                        params = params,
                        body = body,
                    ),
                    extensions = mapOf("id" to JsonPrimitive(reqId))
                )

                val tags: List<String>? = operation.getTags()
                val primaryTag = tags?.firstOrNull()?.takeIf { s: String -> s.isNotBlank() }
                if (primaryTag != null) {
                    taggedItems.getOrPut(primaryTag) { mutableListOf() }.add(requestItem)
                } else {
                    untaggedItems.add(requestItem)
                }
            }
        }

        val childFolders = taggedItems.map { (tagName, items) ->
            val tagFolderId = UUID.randomUUID().toString()
            FolderItem(
                info = FolderInfo(name = tagName, type = "folder"),
                items = items,
                extensions = mapOf("id" to JsonPrimitive(tagFolderId))
            )
        }

        val rootChildren = childFolders + untaggedItems
        val totalRequestCount = countRequests(rootChildren)

        val rootFolderItem = FolderItem(
            info = FolderInfo(name = specTitle, type = "folder"),
            items = rootChildren,
            extensions = mapOf("id" to JsonPrimitive(rootFolderId))
        )

        val candidateFolder = ImportCandidateFolder(
            id = rootFolderId,
            originalName = specTitle,
            name = specTitle,
            folder = rootFolderItem,
            requestCount = totalRequestCount,
            selected = true,
            hasConflict = false,
        )

        val candidateEnvironments = mutableListOf<ImportCandidateEnvironment>()
        val xEnvs = openAPI.extensions?.get("x-environments") as? List<*>
        if (!xEnvs.isNullOrEmpty()) {
            for (envObj in xEnvs) {
                if (envObj is Map<*, *>) {
                    val envName = envObj["name"]?.toString()?.takeIf { it.isNotBlank() } ?: "Imported Environment"
                    val varsMap = envObj["variables"] as? Map<*, *>
                    val envVariables = mutableListOf<RegularVariable>()
                    varsMap?.forEach { (k, v) ->
                        val kStr = k?.toString()?.trim().orEmpty()
                        if (kStr.isNotEmpty()) {
                            envVariables.add(RegularVariable(name = kStr, value = VariableValue.of(v?.toString() ?: "")))
                        }
                    }
                    val env = Environment(
                        name = envName,
                        variables = envVariables,
                    )
                    candidateEnvironments.add(
                        ImportCandidateEnvironment(
                            id = UUID.randomUUID().toString(),
                            originalName = envName,
                            name = envName,
                            environment = env,
                            variableCount = envVariables.size,
                            selected = true,
                            hasConflict = false,
                        )
                    )
                }
            }
        }

        if (candidateEnvironments.isEmpty()) {
            val servers = openAPI.getServers()
            if (!servers.isNullOrEmpty()) {
                val primaryServer = servers.first()
                val serverUrl = primaryServer.getUrl()?.takeIf { s: String -> s.isNotBlank() } ?: "http://localhost"
                val envVariables = mutableListOf<RegularVariable>(
                    RegularVariable(name = "baseUrl", value = VariableValue.of(serverUrl))
                )
                primaryServer.getVariables()?.forEach { (varName, varObj) ->
                    val defaultVal = varObj.getDefault() ?: ""
                    envVariables.add(RegularVariable(name = varName, value = VariableValue.of(defaultVal)))
                }

                val envName = "$specTitle Environment"
                val env = Environment(
                    name = envName,
                    variables = envVariables
                )

                candidateEnvironments.add(
                    ImportCandidateEnvironment(
                        id = UUID.randomUUID().toString(),
                        originalName = envName,
                        name = envName,
                        environment = env,
                        variableCount = envVariables.size,
                        selected = true,
                        hasConflict = false,
                    )
                )
            }
        }

        return ImportScanResult.Success(
            format = SpecFormat.OPENAPI_V3,
            specTitle = specTitle,
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
