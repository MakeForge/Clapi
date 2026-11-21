---
title: Exporting Workspace
description: Exporting collections to OpenCollection format with automatic secret sanitization in Clapi.
---

# Exporting Workspace

<p class="badges">
  <span class="badge badge-protocol">Import / Export</span>
</p>

## Overview

You can export any collection or full workspace to share with colleagues or commit to external archives.

---

## Exporting a Collection

1. In the sidebar, right-click the collection you want to export.
2. Select **Export Collection**.
3. Choose your export format:
   - **OpenCollection (YAML)**: Recommended for Git and human readability.
   - **OpenCollection (JSON)**: Standard JSON representation.
4. Select destination directory and save.

---

## Secret Sanitization Guarantee

When exporting:
- Any variable or auth token flagged as **Secret** is automatically stripped or sanitized.
- KSafe master credentials and local vault tokens are never written into export artifacts.
