package io.github.makeforge.clapi.mcp.server.tools

import io.github.makeforge.clapi.core.model.McpTool
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.mcp.server.backend.DeleteResultDto
import io.github.makeforge.clapi.mcp.server.backend.EnvironmentsOverviewDto
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.SanitizedEnvironmentDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedExternalSecretDeclarationDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedVariableDto
import io.github.makeforge.clapi.mcp.server.backend.SetVariableInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertEnvironmentInput
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.okResult
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.optBoolean
import io.github.makeforge.clapi.mcp.server.tools.WorkspaceTools.optString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

object EnvironmentTools {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val tools: List<McpTool> = listOf(
        McpTool(
            name = "clapi_list_environments",
            title = "List Clapi Environments",
            description = "Lists all environments, active environment selection, inheritance chains ('extends'), sanitized global variables, and effective environment variable projections.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("includeVariables", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to include sanitized variable projections (default true).")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_set_active_environment",
            title = "Set Active Clapi Environment",
            description = "Switches the active environment by name, or clears active selection when name is empty/null.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Environment name to activate, or empty string to clear active selection.")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_upsert_environment",
            title = "Create or Update Clapi Environment",
            description = "Creates or updates an environment (name, color, extends inheritance parent, description, and external secret declarations). Validates against circular inheritance chains.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add(JsonPrimitive("name")) })
                put("properties", buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Environment name.")
                    })
                    put("newName", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional new name when renaming an existing environment.")
                    })
                    put("color", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional hex color string (e.g., '#4CAF50').")
                    })
                    put("extends", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional parent environment name to inherit variables from.")
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional environment description.")
                    })
                    put("externalSecretsProvider", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional external secrets provider type: 'hashicorp-vault-server', 'aws-secrets-manager', 'azure-key-vault', 'gcp-secrets-manager', 'keeper-security-cloud', or 'keepassxc'.")
                    })
                    put("externalSecrets", buildJsonObject {
                        put("type", "array")
                        put("description", "Optional external secret declarations [{name, providerType, path, url, targetField, customFilters, enabled}].")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_delete_environment",
            title = "Delete Clapi Environment",
            description = "Deletes an environment by name. Requires confirm=true.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray {
                    add(JsonPrimitive("name"))
                    add(JsonPrimitive("confirm"))
                })
                put("properties", buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Environment name to delete.")
                    })
                    put("confirm", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Must be true to confirm deletion.")
                    })
                })
            },
        ),
        McpTool(
            name = "clapi_set_variable",
            title = "Set or Delete Scoped Clapi Variable",
            description = "Creates, updates, or deletes a variable in 'global', 'environment', 'collection', or 'folder' scope with full support for kind ('regular', 'secret', 'dynamic') and variableType ('string', 'number', 'boolean', 'object'). Secret variables are write-only and never echoed back in plaintext.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray {
                    add(JsonPrimitive("scope"))
                    add(JsonPrimitive("key"))
                })
                put("properties", buildJsonObject {
                    put("scope", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("global"))
                            add(JsonPrimitive("environment"))
                            add(JsonPrimitive("collection"))
                            add(JsonPrimitive("folder"))
                        })
                        put("description", "Variable scope: 'global', 'environment', 'collection', or 'folder'.")
                    })
                    put("target", buildJsonObject {
                        put("type", "string")
                        put("description", "Environment name (for 'environment' scope) or collection/folder UUID/slash path (for 'collection'/'folder' scope).")
                    })
                    put("key", buildJsonObject {
                        put("type", "string")
                        put("description", "Variable name.")
                    })
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("upsert"))
                            add(JsonPrimitive("delete"))
                        })
                        put("description", "'upsert' (default) or 'delete'.")
                    })
                    put("kind", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("regular"))
                            add(JsonPrimitive("secret"))
                            add(JsonPrimitive("dynamic"))
                        })
                        put("description", "Variable kind: 'regular' (default), 'secret', or 'dynamic'.")
                    })
                    put("variableType", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("string"))
                            add(JsonPrimitive("number"))
                            add(JsonPrimitive("boolean"))
                            add(JsonPrimitive("object"))
                        })
                        put("description", "Variable JSON data type: 'string' (default), 'number', 'boolean', or 'object'.")
                    })
                    put("value", buildJsonObject {
                        put("type", "string")
                        put("description", "Variable value for 'regular' and 'secret' variables.")
                    })
                    put("secret", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Optional shorthand for kind='secret'.")
                    })
                    put("enabled", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether the variable is enabled (default true).")
                    })
                    put("dynamicScript", buildJsonObject {
                        put("type", "string")
                        put("description", "JavaScript generator body when kind='dynamic'.")
                    })
                    put("dynamicParameters", buildJsonObject {
                        put("type", "array")
                        put("description", "Declared parameters [{name, defaultValue, description}] when kind='dynamic'.")
                    })
                })
            },
        ),
    )

    suspend fun handleToolCall(
        toolName: String,
        args: JsonObject,
        backend: McpWorkspaceBackend,
    ): McpToolResult? {
        return when (toolName) {
            "clapi_list_environments" -> {
                val includeVars = args.optBoolean("includeVariables") ?: true
                val res = backend.listEnvironments(includeVars)
                okResult(json.encodeToJsonElement(EnvironmentsOverviewDto.serializer(), res))
            }
            "clapi_set_active_environment" -> {
                val name = args.optString("name")
                val res = backend.setActiveEnvironment(name)
                okResult(json.encodeToJsonElement(EnvironmentsOverviewDto.serializer(), res))
            }
            "clapi_upsert_environment" -> {
                val name = args.optString("name")
                    ?: throw IllegalArgumentException("Missing required argument 'name'.")
                val input = UpsertEnvironmentInput(
                    name = name,
                    newName = args.optString("newName"),
                    color = args.optString("color"),
                    extends = args.optString("extends"),
                    description = args.optString("description"),
                    externalSecretsProvider = args.optString("externalSecretsProvider"),
                    externalSecrets = args["externalSecrets"]?.let(::parseExternalSecrets),
                )
                val res = backend.upsertEnvironment(input)
                okResult(json.encodeToJsonElement(SanitizedEnvironmentDto.serializer(), res))
            }
            "clapi_delete_environment" -> {
                val name = args.optString("name")
                    ?: throw IllegalArgumentException("Missing required argument 'name'.")
                val confirm = args.optBoolean("confirm") ?: false
                val res = backend.deleteEnvironment(name, confirm)
                okResult(json.encodeToJsonElement(DeleteResultDto.serializer(), res))
            }
            "clapi_set_variable" -> {
                val scope = args.optString("scope")
                    ?: throw IllegalArgumentException("Missing required argument 'scope'.")
                val key = args.optString("key") ?: args.optString("name")
                    ?: throw IllegalArgumentException("Missing required argument 'key'.")
                val secretFlag = args.optBoolean("secret") ?: false
                val explicitKind = args.optString("kind")
                val effectiveKind = when {
                    explicitKind != null -> explicitKind
                    secretFlag -> "secret"
                    !args.optString("dynamicScript").isNullOrBlank() -> "dynamic"
                    else -> "regular"
                }
                val dynParams = (args["dynamicParameters"] as? JsonArray)?.mapNotNull { elem ->
                    val obj = elem as? JsonObject ?: return@mapNotNull null
                    val pName = obj.optString("name") ?: return@mapNotNull null
                    DynamicVariableParameter(
                        name = pName,
                        description = obj.optString("description"),
                        defaultValue = obj.optString("defaultValue"),
                    )
                }.orEmpty()
                val input = SetVariableInput(
                    scope = scope,
                    target = args.optString("target") ?: args.optString("environmentName"),
                    key = key,
                    action = args.optString("action") ?: "upsert",
                    kind = effectiveKind,
                    variableType = args.optString("variableType") ?: args.optString("type") ?: "string",
                    value = args.optString("value").orEmpty(),
                    enabled = args.optBoolean("enabled") ?: true,
                    dynamicScript = args.optString("dynamicScript"),
                    dynamicParameters = dynParams,
                )
                val res = backend.setVariable(input)
                okResult(json.encodeToJsonElement(SanitizedVariableDto.serializer(), res))
            }
            else -> null
        }
    }

    private fun parseExternalSecrets(element: JsonElement): List<SanitizedExternalSecretDeclarationDto> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.optString("name") ?: return@mapNotNull null
            val providerType = obj.optString("providerType") ?: "hashicorp-vault-server"
            val path = obj.optString("path") ?: obj.optString("secretName") ?: obj.optString("secretId")
                ?: obj.optString("recordTitle").orEmpty()
            val filters = (obj["customFilters"] as? JsonObject)?.entries?.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
            }?.toMap().orEmpty()
            SanitizedExternalSecretDeclarationDto(
                name = name,
                providerType = providerType,
                path = path,
                url = obj.optString("url") ?: obj.optString("vaultName") ?: obj.optString("projectId"),
                targetField = obj.optString("targetField") ?: "password",
                customFilters = filters,
                enabled = obj.optBoolean("enabled") ?: true,
            )
        }
    }
}
