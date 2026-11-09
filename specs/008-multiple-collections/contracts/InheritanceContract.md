# Contract: Request Defaults Inheritance

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09

## 1. Overview

Specifies the cascade rules and resolution contracts for request defaults propagating from Collection to Folders and down to Requests.

---

## 2. Ancestor Resolution Chain

For any request or folder tab, `getAncestorFolders(tab)` returns the ordered hierarchy list from root to leaf:

```text
[ CollectionNode (kind = COLLECTION), FolderNode (kind = FOLDER), SubFolderNode (kind = FOLDER) ]
```

---

## 3. Inheritance Rules by Setting Type

### 3.1 Headers & Variables

1. **Resolution Direction**: Root to Leaf (`Collection` -> `Parent Folder` -> `Child Folder` -> `Request`).
2. **Precedence**: Descendants override ancestors for matching keys (case-insensitive).
3. **Display**:
   - In Folder Editor and Request Editor, inherited headers and variables that are not explicitly overridden are displayed with `HeaderKind.INHERITED` styling and an "Inherited" tag.
   - The user cannot directly edit inherited values in the child tab; hovering indicates the origin (e.g., "Inherited from Collection Payments API" or "Inherited from Folder Auth").
4. **Overriding**:
   - Defining a row in the child tab with the same key overrides the inherited value for that specific level and its descendants.

### 3.2 Authentication

1. **Inherit Option**:
   - Available on all Folders and Requests.
   - Designated by `AuthType.INHERIT`.
2. **Resolution Algorithm**:
   ```text
   current = tab
   while current has ancestor:
       ancestor = getNearestAncestor(current)
       if ancestor.authType != null and ancestor.authType != INHERIT and ancestor.authType != NONE:
           return ancestor.resolvedAuth
       current = ancestor
   return AuthType.NONE
   ```
3. **Folder Inheritance**:
   - A folder whose auth is `INHERIT` resolves to the parent folder's auth, or if at the root of the collection, resolves to the collection's auth.
4. **Summary Display**:
   - When `INHERIT` is selected, the Auth tab displays an info banner showing the resolved auth type and ancestor source:
     *"Inheriting Bearer Token from collection 'Payments API'"*

### 3.3 Scripts

1. **Pre-request Scripts**: Executed in root-to-leaf sequence:
   `Collection.preRequestScript` -> `Folder.preRequestScript` -> `Request.preRequestScript`
2. **Post-request / Tests Scripts**: Executed in leaf-to-root sequence:
   `Request.testScript` -> `Folder.testScript` -> `Collection.testScript`
