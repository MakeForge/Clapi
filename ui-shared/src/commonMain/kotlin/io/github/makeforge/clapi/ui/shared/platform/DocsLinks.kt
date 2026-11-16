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

    /** Workspaces & Collections documentation page. */
    val collections: String
        get() = url("/workspaces/collections")

    /** Folders and Inheritance documentation page. */
    val folders: String
        get() = url("/workspaces/folders-and-inheritance")

    /** Protobuf Settings documentation page. */
    val protobufSettings: String
        get() = url("/workspaces/protobuf-settings")

    /** Git Sync Overview documentation page. */
    val gitSync: String
        get() = url("/collaboration/git-sync-overview")

    /** Git Operations documentation page. */
    val gitOperations: String
        get() = url("/collaboration/git-operations")

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

    /** HTTP Requests documentation page. */
    val http: String
        get() = url("/request-types/http/")

    /** GraphQL Requests documentation page. */
    val graphql: String
        get() = url("/request-types/graphql/")

    /** gRPC Requests documentation page. */
    val grpc: String
        get() = url("/request-types/grpc/")

    /** WebSocket Requests documentation page. */
    val websocket: String
        get() = url("/request-types/websocket/")

    /** MCP (Model Context Protocol) documentation page. */
    val mcp: String
        get() = url("/request-types/mcp/")

    /** Realtime Streaming documentation page. */
    val realtime: String
        get() = url("/request-types/realtime/")

    /** Scripting Engine Architecture documentation page. */
    val scriptingArchitecture: String
        get() = url("/scripting/engine-architecture")

    /** Pre-Request Scripting documentation page. */
    val preRequestScripting: String
        get() = url("/scripting/pre-request")

    /** Post-Response Assertions documentation page. */
    val postResponseTests: String
        get() = url("/scripting/post-response-tests")

    /** Scripting API Reference documentation page. */
    val scriptingApiReference: String
        get() = url("/scripting/api-reference")

    /** Keyboard Shortcuts reference page. */
    val shortcuts: String
        get() = url("/reference/shortcuts")

    /** Application Settings reference page. */
    val settings: String
        get() = url("/reference/settings")

    /** Import documentation page. */
    val importDocs: String
        get() = url("/import-export/import")

    /** Export documentation page. */
    val exportDocs: String
        get() = url("/import-export/export")

    /** Zero-Telemetry documentation page. */
    val zeroTelemetry: String
        get() = url("/security/zero-telemetry")

    /** KSafe Encryption documentation page. */
    val ksafeEncryption: String
        get() = url("/security/ksafe-encryption")

    /** External Libraries documentation page. */
    val externalLibraries: String
        get() = url("/security/external-libraries")

    /** SBOM & Vulnerabilities documentation page. */
    val sbomVulnerabilities: String
        get() = url("/security/sbom-vulnerabilities")
}
