package io.github.crustack.clapi.core.storage.opencollection

expect class OpenCollectionSchemaValidator() {
    fun validate(rawJson: String): List<ValidationIssue>
    fun validateYaml(rawYaml: String): List<ValidationIssue>
    fun validateOrThrow(rawJson: String)
    fun validateYamlOrThrow(rawYaml: String)
}
