package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.network.gcp.GcpAuthMethod
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import kotlinx.coroutines.launch

// ── Settings model ──────────────────────────────────────────────

const val DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP = 720f
const val DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP = 560f

class AppSettings {
    // General
    var autoSaveRequests       by mutableStateOf(false)
    var confirmBeforeDelete    by mutableStateOf(true)
    var defaultTimeoutSec      by mutableStateOf(30)
    var snackbarAutoDismissSec by mutableStateOf(DEFAULT_SNACKBAR_AUTO_DISMISS_SEC)
    var responseLayout         by mutableStateOf(ResponseLayout.RIGHT)

    // Theme
    var theme by mutableStateOf(AppTheme.DARK)

    // Language (i18n)
    var language by mutableStateOf(AppLanguage.EN)

    // Network
    var requestTimeoutSec    by mutableStateOf(30)
    var requestTimeoutMsOverride: Long? = null
    var followRedirects      by mutableStateOf(true)
    var maxRedirects         by mutableStateOf(20)

    fun withNetworkOverrides(
        timeoutMsOverride: Long? = null,
        followRedirectsOverride: Boolean? = null,
        maxRedirectsOverride: Int? = null,
    ): AppSettings {
        if (timeoutMsOverride == null && followRedirectsOverride == null && maxRedirectsOverride == null) {
            return this
        }
        val base = this
        return AppSettings().also { copy ->
            copy.autoSaveRequests = base.autoSaveRequests
            copy.confirmBeforeDelete = base.confirmBeforeDelete
            copy.defaultTimeoutSec = base.defaultTimeoutSec
            copy.snackbarAutoDismissSec = base.snackbarAutoDismissSec
            copy.responseLayout = base.responseLayout
            copy.theme = base.theme
            copy.language = base.language
            copy.requestTimeoutSec = if (timeoutMsOverride != null) {
                (timeoutMsOverride / 1_000L).toInt().coerceAtLeast(1)
            } else {
                base.requestTimeoutSec
            }
            copy.requestTimeoutMsOverride = timeoutMsOverride ?: base.requestTimeoutMsOverride
            copy.followRedirects = followRedirectsOverride ?: base.followRedirects
            copy.maxRedirects = maxRedirectsOverride ?: base.maxRedirects
            copy.collectionsExpanded = base.collectionsExpanded
            copy.environmentsExpanded = base.environmentsExpanded
            copy.httpProxy = base.httpProxy
            copy.httpsProxy = base.httpsProxy
            copy.proxyEnabled = base.proxyEnabled
            copy.scriptPrefix = base.scriptPrefix
            copy.selectedEnvName = base.selectedEnvName
            copy.allowJson5InJsonBodies = base.allowJson5InJsonBodies
            copy.showEditorPositionIndicator = base.showEditorPositionIndicator
            copy.environmentDialogWidthDp = base.environmentDialogWidthDp
            copy.environmentDialogHeightDp = base.environmentDialogHeightDp
            copy.vaultProviderType = base.vaultProviderType
            copy.vaultServerUrl = base.vaultServerUrl
            copy.vaultMountPath = base.vaultMountPath
            copy.vaultNamespace = base.vaultNamespace
            copy.vaultToken = base.vaultToken
            copy.awsRegion = base.awsRegion
            copy.awsAccessKeyId = base.awsAccessKeyId
            copy.awsSecretAccessKey = base.awsSecretAccessKey
            copy.awsSessionToken = base.awsSessionToken
            copy.awsEndpointUrl = base.awsEndpointUrl
            copy.awsVersionStage = base.awsVersionStage
            copy.azureVaultName = base.azureVaultName
            copy.azureTenantId = base.azureTenantId
            copy.azureClientId = base.azureClientId
            copy.azureClientSecret = base.azureClientSecret
            copy.azureAuthorityHost = base.azureAuthorityHost
            copy.azureApiVersion = base.azureApiVersion
            copy.gcpProjectId = base.gcpProjectId
            copy.gcpAccessToken = base.gcpAccessToken
            copy.gcpEndpointUrl = base.gcpEndpointUrl
            copy.gcpAuthMethod = base.gcpAuthMethod
            copy.gcpServiceAccountKey = base.gcpServiceAccountKey
            copy.gcpAdcFilePath = base.gcpAdcFilePath
            copy.keeperConfigJson = base.keeperConfigJson
            copy.keeperConnectionStatus = base.keeperConnectionStatus
            copy.mcpServerEnabled = base.mcpServerEnabled
            copy.mcpServerPort = base.mcpServerPort
        }
    }

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
    var gcpProjectId          by mutableStateOf("")
    var gcpAccessToken        by mutableStateOf("")
    var gcpEndpointUrl        by mutableStateOf("https://secretmanager.googleapis.com")
    var gcpAuthMethod         by mutableStateOf(GcpAuthMethod.SERVICE_ACCOUNT_KEY)
    var gcpServiceAccountKey  by mutableStateOf("")
    var gcpAdcFilePath        by mutableStateOf("")

    // Secrets Manager (Keeper Security Cloud)
    var keeperConfigJson       by mutableStateOf("")
    var keeperConnectionStatus by mutableStateOf(KeeperConnectionStatus.UNKNOWN)

    // Embedded MCP Server
    var mcpServerEnabled       by mutableStateOf(false)
    var mcpServerPort          by mutableStateOf(3333)
}

enum class KeeperConnectionStatus {
    UNKNOWN,
    CHECKING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}
