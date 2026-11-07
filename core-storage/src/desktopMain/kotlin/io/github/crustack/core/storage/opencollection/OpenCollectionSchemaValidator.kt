package io.github.crustack.core.storage.opencollection

import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion

actual class OpenCollectionSchemaValidator actual constructor() {
    private val schema: Schema = run {
        val stream = OpenCollectionSchemaValidator::class.java.getResourceAsStream("/opencollection/opencollection_v1.0.0.json")
            ?: error("Embedded OpenCollection v1.0.0 schema resource not found at /opencollection/opencollection_v1.0.0.json")
        val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
        stream.use { registry.getSchema(it) }
    }

    actual fun validate(rawJson: String): List<ValidationIssue> {
        return try {
            val errors = schema.validate(rawJson, InputFormat.JSON).toMutableList()
            val issues = errors.map { err ->
                ValidationIssue(
                    path = err.instanceLocation.toString().ifBlank { "$" },
                    message = err.message ?: "Schema validation failed",
                    keyword = err.keyword
                )
            }.toMutableList()

            // Check opencollection version constraint (FR-002: must be "1.0.0")
            try {
                val element = kotlinx.serialization.json.Json.parseToJsonElement(rawJson)
                val version = (element as? kotlinx.serialization.json.JsonObject)
                    ?.get("opencollection")
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                if (version != null && version != "1.0.0") {
                    issues.add(
                        ValidationIssue(
                            path = "$.opencollection",
                            message = "OpenCollection version '$version' is unsupported. Only '1.0.0' is supported.",
                            keyword = "const"
                        )
                    )
                }
            } catch (_: Exception) {}

            issues
        } catch (e: Exception) {
            listOf(
                ValidationIssue(
                    path = "$",
                    message = e.message ?: "Invalid JSON payload",
                    keyword = "syntax"
                )
            )
        }
    }

    actual fun validateYaml(rawYaml: String): List<ValidationIssue> {
        return try {
            val errors = schema.validate(rawYaml, InputFormat.YAML).toMutableList()
            val issues = errors.map { err ->
                ValidationIssue(
                    path = err.instanceLocation.toString().ifBlank { "$" },
                    message = err.message ?: "Schema validation failed",
                    keyword = err.keyword
                )
            }.toMutableList()

            try {
                val node = com.charleskorn.kaml.Yaml.default.parseToYamlNode(rawYaml)
                val version = ((node as? com.charleskorn.kaml.YamlMap)?.get<com.charleskorn.kaml.YamlScalar>("opencollection"))?.content
                if (version != null && version != "1.0.0") {
                    issues.add(
                        ValidationIssue(
                            path = "$.opencollection",
                            message = "OpenCollection version '$version' is unsupported. Only '1.0.0' is supported.",
                            keyword = "const"
                        )
                    )
                }
            } catch (_: Exception) {}

            issues
        } catch (e: Exception) {
            listOf(
                ValidationIssue(
                    path = "$",
                    message = e.message ?: "Invalid YAML payload",
                    keyword = "syntax"
                )
            )
        }
    }

    actual fun validateOrThrow(rawJson: String) {
        val issues = validate(rawJson)
        if (issues.isNotEmpty()) {
            val formatted = issues.joinToString("; ") { "${it.path}: ${it.message}" }
            throw CollectionValidationException(
                message = "OpenCollection schema validation failed: $formatted",
                issues = issues
            )
        }
    }

    actual fun validateYamlOrThrow(rawYaml: String) {
        val issues = validateYaml(rawYaml)
        if (issues.isNotEmpty()) {
            val formatted = issues.joinToString("; ") { "${it.path}: ${it.message}" }
            throw CollectionValidationException(
                message = "OpenCollection schema validation failed: $formatted",
                issues = issues
            )
        }
    }
}
