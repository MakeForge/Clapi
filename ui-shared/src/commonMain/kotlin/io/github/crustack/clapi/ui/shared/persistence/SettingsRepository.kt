package io.github.crustack.clapi.ui.shared.persistence

import io.github.crustack.clapi.ui.shared.platform.PlatformStorage
import io.github.crustack.clapi.ui.shared.i18n.AppLanguage
import io.github.crustack.clapi.ui.shared.state.AppSettings
import io.github.crustack.clapi.ui.shared.state.ResponseLayout
import io.github.crustack.clapi.ui.shared.state.AppTheme
import io.github.crustack.clapi.ui.shared.state.DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP
import io.github.crustack.clapi.ui.shared.state.DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP

/**
 * Persists [AppSettings] using [PlatformStorage].
 * Call [load] on startup and [save] after any settings mutation.
 */
object SettingsRepository {

    private const val PREFIX = "settings."

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
        settings.vaultProviderType = PlatformStorage.getString(PREFIX + "vaultProviderType") ?: settings.vaultProviderType
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
        PlatformStorage.putString(PREFIX + "vaultProviderType", settings.vaultProviderType)
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
