# Contract: OpenAPI & OpenCollection Importer Engine

**Feature**: OpenAPI & OpenCollection Import Dialog
**Path**: `core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/importers/SpecificationImporter.kt`

## Interface Definition

```kotlin
sealed interface ImportScanResult {
    data class Success(
        val format: SpecFormat,
        val specTitle: String,
        val folders: List<ImportCandidateFolder>,
        val environments: List<ImportCandidateEnvironment>,
    ) : ImportScanResult

    data class Failure(
        val message: String,
        val details: List<String> = emptyList(),
    ) : ImportScanResult
}

interface SpecificationImporter {
    /**
     * Inspects and parses [rawContent] as either OpenCollection YAML or OpenAPI v3 (JSON/YAML).
     * Returns [ImportScanResult.Success] on valid content or [ImportScanResult.Failure] with diagnostic messages.
     */
    suspend fun scanContent(rawContent: String): ImportScanResult
}
```

## OpenAPI Parsing & Mapping Rules

### 1. Specification Detection & Version Gate
- If content parses with `OpenAPIParser().readContents(rawContent, null, null)`:
  - Check `result.openAPI`:
    - If `openAPI != null` and `openAPI.openapi` starts with `"3."`: Valid OpenAPI v3 specification.
    - If `result.openAPI` has `openapi` or `swagger` starting with `"2."`: Return `ImportScanResult.Failure("Swagger 2.0 is not supported. Please provide an OpenAPI v3.0+ specification.")`.
    - If `result.openAPI == null`: Check `result.messages` and return `ImportScanResult.Failure("Invalid OpenAPI specification: " + result.messages.joinToString("; "))`.

### 2. OpenCollection Detection
- If content contains `opencollection:` or deserializes via `OpenCollectionSerializer.deserializeYaml`:
  - Validate with `OpenCollectionSchemaValidator().validateYamlOrThrow(rawContent)`.
  - Extract root folder / items and environments from `OpenCollectionDocument`.

### 3. OpenAPI Hierarchy Construction
- **Root Parent Folder**:
  - Name: `openAPI.info?.title ?: "OpenAPI Specification"`.
  - ID: Generated UUID.
- **Subfolders from Tags**:
  - Extract all unique tags defined on operations across all paths.
  - Create a child `CollectionNode` for each tag within the root parent folder.
  - Place each operation under its primary tag's subfolder.
  - If an operation has no tags, place it directly under the root parent folder.
- **Operation to Request Conversion**:
  - Method: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`, `HEAD`.
  - Name: `operation.summary ?: operation.operationId ?: "$method $path"`.
  - URL: `{{baseUrl}}$path` (or server URL combined with path).
  - Headers: Mapped from header parameters.
  - Query Parameters: Mapped from query parameters.
  - Body: Mapped from `requestBody` (e.g. JSON schema example or generated empty JSON object).
- **Environment Creation**:
  - If `openAPI.servers` are specified, create an environment containing:
    - Variable `baseUrl` set to `servers.first().url`.
    - Variables for any templated server variables.
