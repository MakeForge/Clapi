# Data Model: Azure Key Vault Integration

## Overview

This document specifies the data models, entity relationships, validation rules, and state mappings for Azure Key Vault support across `core-model`, `core-network`, `ui-shared`, and `core-storage`.

---

## 1. Core Model Entities (`core-model`)

### `AzureKeyVaultExternalSecret`

Represents an individual external secret declaration in an environment backed by Azure Key Vault.

```kotlin
package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.Serializable

@Serializable
data class AzureKeyVaultExternalSecret(
    override val name: String,
    val secretName: String,
    val vaultName: String? = null,
    val targetField: String = "",
    val customFilters: Map<String, String> = emptyMap(),
    override val description: Description? = null,
    override val disabled: Boolean? = null,
) : ExternalSecretVariable
```

#### Fields & Constraints:
- `name`: Local placeholder reference name (e.g. `client_secret` resolving from `{{_secret.client_secret}}`). Must not be blank.
- `secretName`: The identifier of the secret within Azure Key Vault (e.g., `payment-foo`, `plain-token`).
- `vaultName`: Optional override for the Azure Key Vault name or URL. If omitted or blank, falls back to the global vault configured in Settings.
- `targetField`: Specific key to extract if the secret value is a JSON object. If blank, defaults to the whole secret value (or single key).
- `customFilters`: Map of key-value attributes to match against parsed JSON payload, supporting variable interpolation (e.g., `stage -> {{env}}`).
- `description`: Optional user documentation.
- `disabled`: Flag indicating whether the secret is active (`false`/`null`) or inactive (`true`).

---

## 2. Network Client Configuration (`core-network`)

### `AzureKeyVaultConfig`

Encapsulates all connection, authentication, and secret identification parameters needed to retrieve a secret from Azure Key Vault.

```kotlin
package io.github.crustack.clapi.core.network.azure

data class AzureKeyVaultConfig(
    val vaultName: String,
    val secretName: String,
    val secretVersion: String? = null,
    val propertyKey: String? = null,
    val tenantId: String,
    val clientId: String,
    val clientSecret: String,
    val authorityHost: String = "https://login.microsoftonline.com",
    val apiVersion: String = "7.4",
)
```

#### Validation Rules:
- `vaultName`: Must not be blank. Accepts a short vault name (`my-vault`) or a full URL (`https://my-vault.vault.azure.net` or `https://localhost:8443`).
- `secretName`: Must not be blank.
- `tenantId`: Must not be blank unless `authorityHost == "bypass"`.
- `clientId`: Must not be blank unless `authorityHost == "bypass"`.
- `clientSecret`: Must not be blank unless `authorityHost == "bypass"`.
- `apiVersion`: Defaults to `"7.4"`.

---

## 3. UI & Application State Entities (`ui-shared`)

### `AppSettings` Additions

Global settings state capturing Azure Key Vault credentials:

```kotlin
// In AppSettings.kt
var azureVaultName by mutableStateOf("")
var azureTenantId by mutableStateOf("")
var azureClientId by mutableStateOf("")
var azureClientSecret by mutableStateOf("")
var azureAuthorityHost by mutableStateOf("https://login.microsoftonline.com")
var azureApiVersion by mutableStateOf("7.4")
```

#### Storage Keys & Encryption:
- `clapi.settings.azureVaultName` -> Plaintext in preferences
- `clapi.settings.azureTenantId` -> Plaintext in preferences
- `clapi.settings.azureClientId` -> Plaintext in preferences
- `clapi.settings.azureClientSecret` -> Encrypted via `PlatformStorage.putSecretString`
- `clapi.settings.azureAuthorityHost` -> Plaintext in preferences
- `clapi.settings.azureApiVersion` -> Plaintext in preferences

---

## 4. Internal Secret Resolution Entities

### `SecretDeclaration`

Shared internal model used by `SecretResolutionService`:

```kotlin
data class SecretDeclaration(
    val name: String,
    val path: String = "",
    val enabled: Boolean = true,
    val providerType: String = "hashicorp-vault-server",
    val url: String = "",
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    val secretId: String = path,
)
```

For Azure Key Vault:
- `providerType`: `"azure-key-vault"`
- `path`: Set to `secretName`
- `secretId`: Set to `secretName`
- `targetField`: Set to `targetField`
- `customFilters`: Set to `customFilters`
