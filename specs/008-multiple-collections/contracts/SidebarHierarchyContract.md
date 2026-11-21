# Contract: Sidebar Collections Hierarchy

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09

## 1. Overview

Specifies the sidebar presentation and interaction contracts for multiple OpenCollection collections.

---

## 2. Component Structure

```text
Sidebar
└── "Collections" Header (SectionHeader)
    ├── Title: "Collections"
    ├── Expand/Collapse Toggle: state.collectionsExpanded
    └── Action Button: "+" (Icon: Icons.Default.Add, ContentDescription: "Add Collection")
        └── OnClick: Creates and appends a new CollectionNode(kind = RequestKind.COLLECTION)
    └── Collection List (when state.collectionsExpanded == true)
        ├── Collection Item 1 (kind == RequestKind.COLLECTION)
        │   ├── Expand/Collapse Chevron
        │   ├── Icon: Icons.Default.Article
        │   ├── Name Label / Inline Rename Text Field
        │   ├── Action: "+" (Add folder to Collection)
        │   └── Context Menu:
        │       ├── Add Folder
        │       ├── Add Request
        │       ├── Add gRPC Request
        │       ├── Add MCP Connection
        │       ├── Duplicate Collection
        │       ├── Export Collection...
        │       └── Delete Collection
        │   └── Children (when expanded):
        │       ├── Folder Item 1 (CollectionTreeNode depth = 1)
        │       └── Request Item 1 (CollectionTreeNode depth = 1)
        └── Collection Item 2
            └── ...
```

---

## 3. Interaction Contracts

| Action | User Gesture | Expected Result |
|---|---|---|
| **Add Collection** | Click `+` on "Collections" header | Generates a new collection with name `New Collection` (disambiguated if existing), appends to `state.collections`, focuses and starts inline rename. |
| **Open Collection Editor** | Single left-click on collection row | Calls `state.openCollection(collection.id)`. Opens new tab or focuses existing tab in editor tab bar. |
| **Expand / Collapse** | Click chevron on collection row | Toggles `state.collectionExpandedState[collection.id]`. Expands/collapses child folders and requests. |
| **Inline Rename** | Double-click on collection name | Activates inline text field (`inlineRenamingId = collection.id`). |
| **Commit Inline Rename** | Press `Enter` or click outside | Trims new name. If non-empty and changed: auto-disambiguates if duplicate (appends `(1)`), updates collection name, syncs open collection tab title, and triggers auto-save. |
| **Cancel Inline Rename** | Press `Escape` | Reverts name to previous value without saving. |
| **Delete Collection** | Select "Delete Collection" in menu | Shows confirmation dialog (if configured). If confirmed: deletes collection from `state.collections`, closes all open tabs for that collection and its descendants, and persists workspace. |
| **Export Collection** | Select "Export Collection..." in menu | Opens save dialog to export that single collection as an OpenCollection file. |
