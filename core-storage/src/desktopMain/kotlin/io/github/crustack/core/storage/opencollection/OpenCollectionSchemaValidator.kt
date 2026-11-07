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
            val errors = schema.validate(rawJson, InputFormat.JSON)
            errors.map { err ->
                ValidationIssue(
                    path = err.instanceLocation.toString().ifBlank { "$" },
                    message = err.message ?: "Schema validation failed",
                    keyword = err.keyword
                )
            }
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
            val errors = schema.validate(rawYaml, InputFormat.YAML)
            errors.map { err ->
                ValidationIssue(
                    path = err.instanceLocation.toString().ifBlank { "$" },
                    message = err.message ?: "Schema validation failed",
                    keyword = err.keyword
                )
            }
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
