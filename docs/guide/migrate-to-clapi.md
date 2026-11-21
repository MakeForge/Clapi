---
title: Migrating to Clapi
description: Import your existing work from other tools into Clapi.
---

# Migrating from Postman or Insomnia

<p class="badges">
  <span class="badge badge-protocol">Tutorial</span>
</p>

---

Already have API collections in **Postman** or **Insomnia**, or an **OpenAPI Schema**? You can import them into Clapi in seconds:

1. **Export from your existing tool**:
   - **Insomnia**: Open your Collection, Design Document, or Environment menu (**...** &rarr; **Export**) and save as **Insomnia v5 (YAML or JSON)**.
   - **Postman**: Right-click your Collection (**Export** &rarr; **Collection v2.1** or **v2.0 JSON**).
2. **Import into Clapi**:
   - Click the **Import** button in the **Collections** sidebar header.
   - Select your exported `.yaml`, `.yml`, or `.json` file (supported formats `OpenAPI 3.0+`, `OpenCollection`, `Postman`, and `Insomnia` are badged right below the file picker).
   - Clapi imports your folder hierarchy, requests, environments, authentication configs, and automatically translates `insomnia.*` and `pm.*` scripts to `clapi.*`.

<ThemedScreenshot
  light="/generated/screenshots/ImportSpecificationDialogPreviewsKt.ImportSpecificationDialogPreviewLight.W640dp_H560dp.png"
  dark="/generated/screenshots/ImportSpecificationDialogPreviewsKt.ImportSpecificationDialogPreviewDark.W640dp_H560dp.png"
  alt="Clapi Import Specification Dialog"
  :crop="false"
  max-width="640px"
/>

See the full [Importing & Migrating Guide](./import-export.md) for detailed feature mappings and script translation reference.

🎉 **Congratulations!** You've completed your first API flow in Clapi.
