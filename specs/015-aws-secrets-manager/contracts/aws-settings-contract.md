# Contract: AWS Secrets Manager Global Settings & UI Contract

## Purpose
Defines the user interface and local storage contract for configuring AWS Secrets Manager in global Settings.

## UI Fields & Components

The Settings Dialog navigation contains a "Secrets Manager" tab (`SettingsSection.SECRETS`). When "AWS Secrets Manager" (`aws-secrets-manager`) is selected in the provider dropdown, the following form fields are rendered:

| UI Label | Component | State Binding | Placeholder / Default | Secure / Masked | Test Tag |
|---|---|---|---|---|---|
| **Secret Provider** | `SettingChoice` | `s.vaultProviderType` | `"aws-secrets-manager"` | No | `secrets-provider` |
| **AWS Region** | `SettingTextField` | `s.awsRegion` | `"us-east-1"` | No | `aws-region` |
| **AWS Access Key ID** | `SettingTextField` | `s.awsAccessKeyId` | `"AKIA..."` | No | `aws-access-key-id` |
| **AWS Secret Access Key** | `SettingPasswordField` | `s.awsSecretAccessKey` | `"••••••••••••••••"` | Yes | `aws-secret-access-key` |
| **AWS Session Token** | `SettingPasswordField` | `s.awsSessionToken` | `""` (Optional) | Yes | `aws-session-token` |
| **Endpoint URL Override** | `SettingTextField` | `s.awsEndpointUrl` | `"http://localhost:4566"` | No | `aws-endpoint-url` |
| **Version Stage** | `SettingTextField` | `s.awsVersionStage` | `"AWSCURRENT"` | No | `aws-version-stage` |

## Storage Contract

- Non-sensitive strings (`awsRegion`, `awsAccessKeyId`, `awsEndpointUrl`, `awsVersionStage`) are persisted to `PlatformStorage.putString` using prefix `settings.`.
- Sensitive credentials (`awsSecretAccessKey`, `awsSessionToken`) are persisted to `PlatformStorage.putSecretString` (backed by encrypted `KSafe` on disk).
