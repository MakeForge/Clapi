package io.github.makeforge.clapi.ui.shared.platform

/**
 * Centralized helper for building Clapi documentation URLs.
 *
 * - Production base URL: [PRODUCTION_BASE_URL] (`https://makeforge.github.io/Clapi`)
 * - Local development (`jvmDev`) base URL: [LOCAL_DEV_BASE_URL] (`http://localhost:5173`)
 */
object DocsLinks {
    const val PRODUCTION_BASE_URL: String = "https://makeforge.github.io/Clapi"
    const val LOCAL_DEV_BASE_URL: String = "http://localhost:5173"

    /**
     * Resolves the active documentation base URL based on explicit override or runtime mode.
     */
    val baseUrl: String
        get() {
            val override = platformDocsBaseUrlOverride()?.trim()?.trimEnd('/')
            if (!override.isNullOrEmpty()) return override
            return if (isLocalDevMode()) LOCAL_DEV_BASE_URL else PRODUCTION_BASE_URL
        }

    /**
     * Builds a full documentation URL for [path] against [base] (defaults to [baseUrl]).
     */
    fun url(path: String, base: String = baseUrl): String {
        val cleanBase = base.trim().trimEnd('/')
        val cleanPath = path.trim().trimStart('/')
        return if (cleanPath.isEmpty()) cleanBase else "$cleanBase/$cleanPath"
    }

    /** Dedicated documentation subpage for Built-in & User-Defined Dynamic Variables. */
    val dynamicVariables: String
        get() = url("/environments/dynamic-variables")

    /** Variable Scopes & Syntax documentation page. */
    val variables: String
        get() = url("/environments/variables")

    /** Environments documentation page. */
    val environments: String
        get() = url("/environments/environments")

    /** Secret Managers documentation page. */
    val secretManagers: String
        get() = url("/environments/secret-managers")

    /** Pre-Request Scripting documentation page. */
    val preRequestScripting: String
        get() = url("/scripting/pre-request")

    /** Post-Response Assertions documentation page. */
    val postResponseTests: String
        get() = url("/scripting/post-response-tests")

    /** Scripting API Reference documentation page. */
    val scriptingApiReference: String
        get() = url("/scripting/api-reference")
}
