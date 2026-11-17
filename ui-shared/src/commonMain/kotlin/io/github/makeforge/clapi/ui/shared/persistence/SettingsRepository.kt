package io.github.makeforge.clapi.ui.shared.persistence

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.network.gcp.GcpAuthMethod
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.github.makeforge.clapi.ui.shared.state.ResponseLayout
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.state.DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP
import io.github.makeforge.clapi.ui.shared.state.DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP

/**
 * Canonical storage keys used for persisting settings and secret manager credentials
 * via [PlatformStorage], with transparent fallback support for legacy `"clapi.settings.*"` keys.
 */
object SettingsStorageKeys {
    const val PREFIX = "settings."
    const val LEGACY_PREFIX = "clapi.settings."

    const val VAULT_TOKEN = "${PREFIX}vaultToken"
    const val AWS_SECRET_ACCESS_KEY = "${PREFIX}awsSecretAccessKey"
    const val AWS_SESSION_TOKEN = "${PREFIX}awsSessionToken"
    const val AZURE_CLIENT_SECRET = "${PREFIX}azureClientSecret"
    const val GCP_SERVICE_ACCOUNT_JSON = "${PREFIX}gcpServiceAccountJson"
    const val GCP_ACCESS_TOKEN = "${PREFIX}gcpAccessToken"
    const val GCP_AUTH_METHOD = "${PREFIX}gcpAuthMethod"
    const val GCP_ADC_FILE_PATH = "${PREFIX}gcpAdcFilePath"
    const val KEEPER_CONFIG = "${PREFIX}keeperConfig"
    const val KEEPASSXC_KEY = "${PREFIX}keepassxcKey"

    const val LEGACY_VAULT_TOKEN = "${LEGACY_PREFIX}vaultToken"
    const val LEGACY_AWS_SECRET_ACCESS_KEY = "${LEGACY_PREFIX}awsSecretAccessKey"
    const val LEGACY_AWS_SESSION_TOKEN = "${LEGACY_PREFIX}awsSessionToken"
    const val LEGACY_AZURE_CLIENT_SECRET = "${LEGACY_PREFIX}azureClientSecret"
    const val LEGACY_GCP_ACCESS_TOKEN = "${LEGACY_PREFIX}gcpAccessToken"
    const val LEGACY_KEEPER_CONFIG = "${LEGACY_PREFIX}keeperConfig"

    /**
     * Reads a secret string from [PlatformStorage] using [canonicalKey] (`"settings.*"`),
     * falling back to [legacyKey] (`"clapi.settings.*"`) if not present under the canonical key.
     */
    fun getSecretWithFallback(
        canonicalKey: String,
        legacyKey: String? = canonicalKey.replaceFirst(PREFIX, LEGACY_PREFIX),
    ): String? {
        val primary = PlatformStorage.getSecretString(canonicalKey)?.takeIf { it.isNotBlank() }
        if (primary != null) return primary
        return legacyKey?.let { PlatformStorage.getSecretString(it)?.takeIf { s -> s.isNotBlank() } }
    }
}

/**
 * Persists [AppSettings] using [PlatformStorage].
 * Call [load] on startup and [save] after any settings mutation.
 */
object SettingsRepository {

    private const val PREFIX = SettingsStorageKeys.PREFIX

    // ── Load ───────────────────────────────────────────────────────────────

    /** Applies persisted values into [settings]. Missing keys fall back to field defaults. */
    fun load(settings: AppSettings) {
        settings.autoSaveRequests    = getBool("autoSaveRequests", settings.autoSaveRequests)
        settings.confirmBeforeDelete = getBool("confirmBeforeDelete", settings.confirmBeforeDelete)
        settings.defaultTimeoutSec   = getInt("defaultTimeoutSec", settings.defaultTimeoutSec)
        settings.responseLayout      = safeEnumOf(
            PlatformStorage.getString(PREFIX + "responseLayout") ?: settings.responseLayout.name,
            settings.responseLayout,
        )

        settings.theme = safeEnumOf(
            PlatformStorage.getString(PREFIX + "theme") ?: settings.theme.name,
            settings.theme,
        )

        settings.language = safeEnumOf(
            PlatformStorage.getString(PREFIX + "language") ?: settings.language.name,
            settings.language,
        )

        settings.requestTimeoutSec = getInt("requestTimeoutSec", settings.requestTimeoutSec)
        settings.followRedirects   = getBool("followRedirects", settings.followRedirects)
        settings.collectionsExpanded  = getBool("collectionsExpanded", settings.collectionsExpanded)
        settings.environmentsExpanded = getBool("environmentsExpanded", settings.environmentsExpanded)

        settings.proxyEnabled = getBool("proxyEnabled", settings.proxyEnabled)
        settings.httpProxy    = PlatformStorage.getString(PREFIX + "httpProxy") ?: settings.httpProxy
        settings.httpsProxy   = PlatformStorage.getString(PREFIX + "httpsProxy") ?: settings.httpsProxy

        settings.scriptPrefix = PlatformStorage.getString(PREFIX + "scriptPrefix") ?: settings.scriptPrefix
        settings.selectedEnvName = PlatformStorage.getString(PREFIX + "selectedEnvName") ?: settings.selectedEnvName
        settings.allowJson5InJsonBodies = getBool("allowJson5InJsonBodies", settings.allowJson5InJsonBodies)
        settings.showEditorPositionIndicator = getBool("showEditorPositionIndicator", settings.showEditorPositionIndicator)
        settings.environmentDialogWidthDp = getPositiveFloat(
            "environmentDialogWidthDp", DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP,
        )
        settings.environmentDialogHeightDp = getPositiveFloat(
            "environmentDialogHeightDp", DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP,
        )

        // Secrets Manager
        settings.vaultProviderType = PlatformStorage.getString(PREFIX + "vaultProviderType")
            ?.let { ExternalSecretType.fromValue(it) ?: safeEnumOf(it, settings.vaultProviderType) }
            ?: settings.vaultProviderType
        settings.vaultServerUrl    = PlatformStorage.getString(PREFIX + "vaultServerUrl") ?: settings.vaultServerUrl
        settings.vaultMountPath    = PlatformStorage.getString(PREFIX + "vaultMountPath") ?: settings.vaultMountPath
        settings.vaultNamespace    = PlatformStorage.getString(PREFIX + "vaultNamespace") ?: settings.vaultNamespace
        settings.vaultToken        = PlatformStorage.getSecretString(PREFIX + "vaultToken") ?: settings.vaultToken

        // Secrets Manager (AWS)
        settings.awsRegion          = PlatformStorage.getString(PREFIX + "awsRegion") ?: settings.awsRegion
        settings.awsAccessKeyId     = PlatformStorage.getString(PREFIX + "awsAccessKeyId") ?: settings.awsAccessKeyId
        settings.awsSecretAccessKey = PlatformStorage.getSecretString(PREFIX + "awsSecretAccessKey") ?: settings.awsSecretAccessKey
        settings.awsSessionToken    = PlatformStorage.getSecretString(PREFIX + "awsSessionToken") ?: settings.awsSessionToken
        settings.awsEndpointUrl     = PlatformStorage.getString(PREFIX + "awsEndpointUrl") ?: settings.awsEndpointUrl
        settings.awsVersionStage    = PlatformStorage.getString(PREFIX + "awsVersionStage") ?: settings.awsVersionStage

        // Secrets Manager (Azure Key Vault)
        settings.azureVaultName     = PlatformStorage.getString(PREFIX + "azureVaultName") ?: settings.azureVaultName
        settings.azureTenantId      = PlatformStorage.getString(PREFIX + "azureTenantId") ?: settings.azureTenantId
        settings.azureClientId      = PlatformStorage.getString(PREFIX + "azureClientId") ?: settings.azureClientId
        settings.azureClientSecret  = PlatformStorage.getSecretString(PREFIX + "azureClientSecret") ?: settings.azureClientSecret
        settings.azureAuthorityHost = PlatformStorage.getString(PREFIX + "azureAuthorityHost") ?: settings.azureAuthorityHost
        settings.azureApiVersion    = PlatformStorage.getString(PREFIX + "azureApiVersion") ?: settings.azureApiVersion

        // Secrets Manager (GCP)
        settings.gcpProjectId         = PlatformStorage.getString(PREFIX + "gcpProjectId") ?: settings.gcpProjectId
        settings.gcpAccessToken       = PlatformStorage.getSecretString(PREFIX + "gcpAccessToken") ?: settings.gcpAccessToken
        settings.gcpEndpointUrl       = PlatformStorage.getString(PREFIX + "gcpEndpointUrl") ?: settings.gcpEndpointUrl
        settings.gcpAuthMethod        = PlatformStorage.getString(PREFIX + "gcpAuthMethod")?.let { GcpAuthMethod.fromId(it) } ?: settings.gcpAuthMethod
        settings.gcpServiceAccountKey = PlatformStorage.getSecretString(PREFIX + "gcpServiceAccountJson") ?: settings.gcpServiceAccountKey
        settings.gcpAdcFilePath       = PlatformStorage.getString(PREFIX + "gcpAdcFilePath") ?: settings.gcpAdcFilePath

        // Secrets Manager (Keeper Security Cloud)
        settings.keeperConfigJson   = PlatformStorage.getSecretString(PREFIX + "keeperConfig") ?: settings.keeperConfigJson
    }

    // ── Save ───────────────────────────────────────────────────────────────

    /** Persists all [settings] fields. */
    fun save(settings: AppSettings) {
        putBool("autoSaveRequests", settings.autoSaveRequests)
        putBool("confirmBeforeDelete", settings.confirmBeforeDelete)
        putInt("defaultTimeoutSec", settings.defaultTimeoutSec)
        PlatformStorage.putString(PREFIX + "responseLayout", settings.responseLayout.name)
        PlatformStorage.putString(PREFIX + "theme", settings.theme.name)
        PlatformStorage.putString(PREFIX + "language", settings.language.name)
        putInt("requestTimeoutSec", settings.requestTimeoutSec)
        putBool("followRedirects", settings.followRedirects)
        putBool("collectionsExpanded", settings.collectionsExpanded)
        putBool("environmentsExpanded", settings.environmentsExpanded)
        putBool("proxyEnabled", settings.proxyEnabled)
        PlatformStorage.putString(PREFIX + "httpProxy", settings.httpProxy)
        PlatformStorage.putString(PREFIX + "httpsProxy", settings.httpsProxy)

        PlatformStorage.putString(PREFIX + "scriptPrefix", settings.scriptPrefix)
        PlatformStorage.putString(PREFIX + "selectedEnvName", settings.selectedEnvName)
        putBool("allowJson5InJsonBodies", settings.allowJson5InJsonBodies)
        putBool("showEditorPositionIndicator", settings.showEditorPositionIndicator)
        putFloat("environmentDialogWidthDp", settings.environmentDialogWidthDp)
        putFloat("environmentDialogHeightDp", settings.environmentDialogHeightDp)

        // Secrets Manager
        PlatformStorage.putString(PREFIX + "vaultProviderType", settings.vaultProviderType.value)
        PlatformStorage.putString(PREFIX + "vaultServerUrl", settings.vaultServerUrl)
        PlatformStorage.putString(PREFIX + "vaultMountPath", settings.vaultMountPath)
        PlatformStorage.putString(PREFIX + "vaultNamespace", settings.vaultNamespace)
        PlatformStorage.putSecretString(PREFIX + "vaultToken", settings.vaultToken)

        // Secrets Manager (AWS)
        PlatformStorage.putString(PREFIX + "awsRegion", settings.awsRegion)
        PlatformStorage.putString(PREFIX + "awsAccessKeyId", settings.awsAccessKeyId)
        PlatformStorage.putSecretString(PREFIX + "awsSecretAccessKey", settings.awsSecretAccessKey)
        PlatformStorage.putSecretString(PREFIX + "awsSessionToken", settings.awsSessionToken)
        PlatformStorage.putString(PREFIX + "awsEndpointUrl", settings.awsEndpointUrl)
        PlatformStorage.putString(PREFIX + "awsVersionStage", settings.awsVersionStage)

        // Secrets Manager (Azure Key Vault)
        PlatformStorage.putString(PREFIX + "azureVaultName", settings.azureVaultName)
        PlatformStorage.putString(PREFIX + "azureTenantId", settings.azureTenantId)
        PlatformStorage.putString(PREFIX + "azureClientId", settings.azureClientId)
        PlatformStorage.putSecretString(PREFIX + "azureClientSecret", settings.azureClientSecret)
        PlatformStorage.putString(PREFIX + "azureAuthorityHost", settings.azureAuthorityHost)
        PlatformStorage.putString(PREFIX + "azureApiVersion", settings.azureApiVersion)

        // Secrets Manager (GCP)
        PlatformStorage.putString(PREFIX + "gcpProjectId", settings.gcpProjectId)
        PlatformStorage.putSecretString(PREFIX + "gcpAccessToken", settings.gcpAccessToken)
        PlatformStorage.putString(PREFIX + "gcpEndpointUrl", settings.gcpEndpointUrl)
        PlatformStorage.putString(PREFIX + "gcpAuthMethod", settings.gcpAuthMethod.id)
        PlatformStorage.putSecretString(PREFIX + "gcpServiceAccountJson", settings.gcpServiceAccountKey)
        PlatformStorage.putString(PREFIX + "gcpAdcFilePath", settings.gcpAdcFilePath)

        // Secrets Manager (Keeper Security Cloud)
        if (settings.keeperConfigJson.isNotBlank()) {
            PlatformStorage.putSecretString(PREFIX + "keeperConfig", settings.keeperConfigJson)
        } else {
            PlatformStorage.remove(PREFIX + "keeperConfig")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun getBool(key: String, default: Boolean): Boolean =
        PlatformStorage.getString(PREFIX + key)?.toBooleanStrictOrNull() ?: default

    private fun getInt(key: String, default: Int): Int =
        PlatformStorage.getString(PREFIX + key)?.toIntOrNull() ?: default

    private fun putBool(key: String, value: Boolean) =
        PlatformStorage.putString(PREFIX + key, value.toString())

    private fun putInt(key: String, value: Int) =
        PlatformStorage.putString(PREFIX + key, value.toString())

    private fun getPositiveFloat(key: String, default: Float): Float =
        PlatformStorage.getString(PREFIX + key)?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: default

    private fun putFloat(key: String, value: Float) =
        PlatformStorage.putString(PREFIX + key, value.toString())

    private inline fun <reified T : Enum<T>> safeEnumOf(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
