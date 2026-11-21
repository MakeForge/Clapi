# Contract: Insomnia v5 Importer Engine

**Feature**: Insomnia v5 Collection & Environment Import
**Path**: `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaImporter.kt`

---

## 1. Interface & API Definition

```kotlin
package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument

interface InsomniaImporter {
    /**
     * Inspects whether [rawContent] matches an Insomnia v5 export (YAML or JSON).
     */
    fun canHandle(rawContent: String): Boolean

    /**
     * Scans and parses [rawContent] as an Insomnia v5 document, extracting candidate
     * folders, requests, and environments.
     */
    suspend fun scanContent(rawContent: String): ImportScanResult

    /**
     * Converts an Insomnia v5 document directly into a canonical [OpenCollectionDocument].
     */
    fun importToOpenCollection(rawContent: String): OpenCollectionDocument

    companion object {
        fun default(): InsomniaImporter = DefaultInsomniaImporter()
    }
}
```

---

## 2. Detection Criteria

Content is detected as an Insomnia v5 document if:
1. The string contains `type: collection.insomnia.rest/5.0` or `"type": "collection.insomnia.rest/5.0"`
2. The string contains `type: spec.insomnia.rest/5.0` or `"type": "spec.insomnia.rest/5.0"`
3. The string contains `type: environment.insomnia.rest/5.0` or `"type": "environment.insomnia.rest/5.0"`

---

## 3. Script Translation Contract

```kotlin
object InsomniaScriptTranslator {
    /**
     * Translates Insomnia `insomnia.*` scripting calls into Clapi `clapi.*` calls.
     */
    fun convertScript(script: String): String
}
```

### Transformation Rules:
1. **Assertion & Test APIs**:
   - `insomnia.test(` $\rightarrow$ `clapi.test(`
   - `insomnia.expect(` $\rightarrow$ `clapi.expect(`
2. **Variable Scopes**:
   - `insomnia.environment.` $\rightarrow$ `clapi.environment.`
   - `insomnia.baseEnvironment.` $\rightarrow$ `clapi.collectionVariables.`
   - `insomnia.collectionVariables.` $\rightarrow$ `clapi.collectionVariables.`
   - `insomnia.variables.` $\rightarrow$ `clapi.variables.`
3. **HTTP Context**:
   - `insomnia.request.` $\rightarrow$ `clapi.request.`
   - `insomnia.response.` $\rightarrow$ `clapi.response.`
   - `insomnia.sendRequest(` $\rightarrow$ `clapi.sendRequest(`
   - `insomnia.cookies.` $\rightarrow$ `clapi.cookies.`
4. **Folder Scoping**:
   - `insomnia.parentFolders.get(` $\rightarrow$ `clapi.folder.get(`

---

## 4. Integration into Specification Importer

In `CompositeSpecificationImporter`:
```kotlin
class CompositeSpecificationImporter(
    private val openApiImporter: OpenApiImporter = OpenApiImporter(),
    private val openCollectionImporter: OpenCollectionImporter = OpenCollectionImporter(),
    private val insomniaImporter: InsomniaImporter = InsomniaImporter.default(),
    private val postmanImporter: PostmanSpecificationImporter = PostmanSpecificationImporter.default(),
) : SpecificationImporter
```
When `insomniaImporter.canHandle(rawContent)` is true:
- Executes `insomniaImporter.scanContent(rawContent)`
- Returns `ImportScanResult.Success` with `format = SpecFormat.INSOMNIA`
