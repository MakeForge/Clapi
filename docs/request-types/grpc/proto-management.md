---
title: Protobuf Schema Management
description: Managing .proto files, proto folders, import directories, Git sync, and collection-level Protobuf settings in Clapi.
---

# Protobuf Schema Management

<p class="badges">
  <span class="badge badge-protocol">gRPC</span>
</p>

## Overview

gRPC relies on Protocol Buffer definitions (`.proto`) to define service contracts, RPC methods, and binary message formats. Clapi provides flexible schema management at both the request and collection levels.

## Loading Proto Files in a gRPC Request

Click the **Proto** button in a gRPC request's top bar to open the **Select Proto File** dialog. You can choose from three workflows:

### 1. Select a Proto Folder (Recommended for Multi-File Schemas)
If your service `.proto` file imports other `.proto` files from the same directory or subdirectories (for example, `greeter.proto` importing `protobuf/common/metadata.proto`):

1. Click **Select Proto Folder** and choose the folder containing your `.proto` hierarchy.
2. Clapi recursively scans the folder and lists all discovered `.proto` files by their relative paths.
3. Click the specific service `.proto` file you want to call (e.g. `protobuf/greeter.proto`) and click **Save**.
4. Clapi resolves imports against all `.proto` files inside the selected folder **and** any enabled collection-level import paths or collection proto files, then populates the RPC method dropdown.

### 2. Select a Single `.proto` File
1. Click **Select Proto File** and browse for a standalone `.proto` file (e.g. `user_service.proto`).
2. Click **Save** to compile the file and load its services and methods.
3. Standard Google protobuf well-known types (such as `google/protobuf/timestamp.proto`) are built in automatically.

### 3. Choose a Collection-Level Proto File
When your collection has shared proto files configured in the Collection Editor, they appear under **Collection Proto Files** inside the dialog for one-click selection.

### Syncing Request Proto Files or Folders to Git
When the parent collection uses **Git** storage, the **Sync to Git** toggle is available directly inside the **Select Proto File** dialog (for request-level file and folder selections):
- **Single file**: Copies the selected `.proto` file into the collection's Git repository (`./protobuf/requests/<requestId>/<fileName>.proto`).
- **Proto folder**: Recursively copies **all `.proto` files** from the selected folder and its subfolders into `./protobuf/requests/<requestId>/<folderName>/`, preserving relative directory structure and excluding non-`.proto` files so teammates can immediately parse and run the request after pulling.

## Collection-Level Protobuf Settings

When working on a collection with multiple gRPC endpoints, you can configure shared Protobuf settings on the collection root:
- Open the collection editor and switch to the **Protobuf** tab.
- Add shared **Proto Files** and **Import Paths** (with individual enable/disable toggles).
- All gRPC requests within the collection automatically inherit enabled collection import paths and proto files during compilation and execution.
- **Git Sync Integration**: Collection proto files and import paths can be tracked and synchronized through Git alongside your requests.
