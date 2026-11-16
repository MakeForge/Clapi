package io.github.crustack.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.ui.shared.i18n.AppLanguage
import kotlinx.coroutines.launch

// ── Settings model ──────────────────────────────────────────────

const val DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP = 720f
const val DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP = 560f

class AppSettings {
    // General
    var autoSaveRequests     by mutableStateOf(false)
    var confirmBeforeDelete  by mutableStateOf(true)
    var defaultTimeoutSec    by mutableStateOf(30)
    var responseLayout       by mutableStateOf(ResponseLayout.RIGHT)

    // Theme
    var theme by mutableStateOf(AppTheme.DARK)

    // Language (i18n)
    var language by mutableStateOf(AppLanguage.EN)

    // Network
    var requestTimeoutSec    by mutableStateOf(30)
    var followRedirects      by mutableStateOf(true)

    // Sidebar
    var collectionsExpanded  by mutableStateOf(false)
    var environmentsExpanded by mutableStateOf(false)

    // Proxy
    var httpProxy            by mutableStateOf("")
    var httpsProxy           by mutableStateOf("")
    var proxyEnabled         by mutableStateOf(false)

    // Scripting
    /** Namespace prefix for scripts (default "clapi"). Change to e.g. "api". */
    var scriptPrefix         by mutableStateOf("clapi")

    // Environment
    /** Name of the last selected environment; restored on app launch. Empty = first env. */
    var selectedEnvName      by mutableStateOf("")

    /** When true (default), JSON bodies accept JSON5; Send converts to strict JSON. */
    var allowJson5InJsonBodies by mutableStateOf(true)

    /** Shows the compact line and column indicator in request and response editors. */
    var showEditorPositionIndicator by mutableStateOf(false)

    /** Preferred manual size for the environment editor. Viewport clamping is not persisted. */
    var environmentDialogWidthDp by mutableStateOf(DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP)
    var environmentDialogHeightDp by mutableStateOf(DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP)

    // Secrets Manager (HashiCorp Vault)
    var vaultProviderType    by mutableStateOf(ExternalSecretType.HASHICORP_VAULT_SERVER)
    var vaultServerUrl       by mutableStateOf("")
    var vaultMountPath       by mutableStateOf("secret")
    var vaultNamespace       by mutableStateOf("")
    var vaultToken           by mutableStateOf("")

    // Secrets Manager (AWS)
    var awsRegion          by mutableStateOf("us-east-1")
    var awsAccessKeyId     by mutableStateOf("")
    var awsSecretAccessKey by mutableStateOf("")
    var awsSessionToken    by mutableStateOf("")
    var awsEndpointUrl     by mutableStateOf("")
    var awsVersionStage    by mutableStateOf("AWSCURRENT")

    // Secrets Manager (Azure Key Vault)
    var azureVaultName     by mutableStateOf("")
    var azureTenantId      by mutableStateOf("")
    var azureClientId      by mutableStateOf("")
    var azureClientSecret  by mutableStateOf("")
    var azureAuthorityHost by mutableStateOf("https://login.microsoftonline.com")
    var azureApiVersion    by mutableStateOf("7.4")

    // Secrets Manager (GCP)
    var gcpProjectId       by mutableStateOf("")
    var gcpAccessToken     by mutableStateOf("")
    var gcpEndpointUrl     by mutableStateOf("https://secretmanager.googleapis.com")
}
