# Contract: HTTP Execution Adapter

**Feature**: `001-opencollection-data-model`
**Contract Name**: `HttpExecutionAdapterContract`
**Module**: `core-network` / `feature-requests`

## 1. Overview
Specifies the transformation boundary between the canonical persisted `HttpRequestItem` and the executable `RequestDefinition` consumed by `ApiClient` and `KtorApiClient`.

---

## 2. Interface Definitions

```kotlin
package io.github.makeforge.clapi.feature.requests

import io.github.makeforge.clapi.core.model.HttpRequestItem
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.FolderItem

interface HttpExecutionAdapter {
    /**
     * Resolves inherited defaults (collection headers, folder headers, folder auth)
     * and compiles a canonical [HttpRequestItem] into an executable [RequestDefinition].
     *
     * @param requestItem The target request item to execute.
     * @param parentHierarchy Chain of parent folders up to the collection root for inheritance.
     * @param collectionRoot Root collection containing defaults.
     * @return Fully resolved [RequestDefinition] ready for network execution.
     */
    fun toExecutableRequest(
        requestItem: HttpRequestItem,
        parentHierarchy: List<FolderItem> = emptyList(),
        collectionRoot: OpenCollectionDocument? = null,
    ): RequestDefinition
}
```

---

## 3. Invariants & Preservation Rules

1. **Unchanged Network Behavior (FR-035, SC-003)**:
   - Converting `HttpRequestItem` to `RequestDefinition` MUST produce identical HTTP requests on the wire (methods, URLs, query parameters, headers, multipart form data, urlencoded, raw bodies, and auth headers).
2. **Inheritance Precedence**:
   - Headers and Auth defined at the request level override those defined at the folder level.
   - Headers and Auth defined at the folder level override those defined at the collection level (`request: RequestDefaults`).
   - If request auth is `AuthInherit` or not specified, the parent hierarchy is traversed to resolve the effective authentication.
3. **No UI State Dependency**:
   - The execution adapter relies exclusively on the collection document hierarchy and is completely independent of Compose UI and platform classes.
