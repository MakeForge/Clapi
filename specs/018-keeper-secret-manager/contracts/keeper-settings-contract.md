# Contract: Keeper Settings and UI Interactions

## 1. Global Settings Flow (`SettingsDialog.kt`)

### 1.1 Unconnected State
- **Input Field**: Masked/unmasked text field for One-Time Token (OTT).
  - Placeholder: `US:XXXX...` or `XXXX...`
  - Tag: `keeper-one-time-token`
- **Connect Button**:
  - Label: `Connect`
  - Tag: `keeper-connect-btn`
  - Action: Invokes `KeeperStorageInitializer.initializeAndBind(token)` in a coroutine.
  - On Success: Saves config JSON via `PlatformStorage.putSecretString("settings.keeperConfig", configJson)`, resets OTT text to `""`, updates connection status to `CONNECTED`.
  - On Error: Displays error banner without storing anything.
- **Guidance Text & Link**:
  - Text: `"To get a one time token: Login to Keeper > Secrets Manager > Create Application > Generate Access Token (Read Only Record Permissions are enough)"`
  - Clickable Help Icon `?`: Opens URL `https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#create-a-secrets-manager-application` in the default browser.

### 1.2 Connected State
- **Status Indicator**:
  - Tag: `keeper-connection-status`
  - Behavior: When opening Settings with an existing config, fires a lightweight connection probe via `KeeperClient.testConnection()`.
  - Status display:
    - Checking: `"Checking connection..."` (dim text)
    - Connected: `"Connected to Keeper Cloud"` (green indicator)
    - Error: `"Connection failed: <reason>"` (red indicator)
- **Re-connect Option**:
  - Allows entering a new One-Time Token to bind a new client configuration, replacing the current one.
  - Button: `Re-connect`
- **Disconnect / Clear Option**:
  - Clears `settings.keeperConfig` from `PlatformStorage` (`PlatformStorage.remove("settings.keeperConfig")`).

---

## 2. Environment Editor Flow (`EnvironmentEditDialog.kt`)

### 2.1 Provider Selection
- Dropdown entry: `Keeper Security Cloud` (`keeper-security-cloud`).
- Tag: `env-provider-option-keeper-security-cloud`.

### 2.2 Table Layout
- Columns:
  1. Enabled: Checkbox
  2. Name: Variable name (`placeholder = "secret_name"`, tag = `env-secret-row-$index-name`)
  3. Record / Title: `placeholder = "Record Title or UID"`, tag = `env-secret-row-$index-record-title`
  4. Target Field: `placeholder = "password"`, tag = `env-secret-row-$index-target-field`
  5. Status: Match indicator (`✔ 1 match`, `✖ 0 found`, etc.), tag = `env-secret-row-$index-status`
  6. Filters: Button expanding custom field filters panel, tag = `env-secret-row-$index-filter-toggle`
  7. Description: `placeholder = "Description (optional)"`, tag = `env-secret-row-$index-desc`
  8. Delete: IconButton

### 2.3 Custom Field Filters Expansion Panel
- Key TextField: `placeholder = "field (e.g. url)"`, tag = `env-secret-row-$index-filter-key-$fIdx`
- Value TextField: `placeholder = "match value or {{var}}"`, tag = `env-secret-row-$index-filter-val-$fIdx`
- Remove Button: tag = `env-secret-row-$index-remove-filter-$fIdx`
- Add Filter Row: Inputs for adding new filter key-value pairs.
