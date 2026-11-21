package io.github.makeforge.clapi.ui.shared.mcp.server

import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.mcp.server.backend.DeleteResultDto
import io.github.makeforge.clapi.mcp.server.backend.EnvironmentsOverviewDto
import io.github.makeforge.clapi.mcp.server.backend.ExecuteRequestInput
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.ExportCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.ExportResultDto
import io.github.makeforge.clapi.mcp.server.backend.GitSyncInput
import io.github.makeforge.clapi.mcp.server.backend.GitSyncResultDto
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.ImportSpecInput
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseInput
import io.github.makeforge.clapi.mcp.server.backend.InspectResponseResultDto
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.ManageFolderInput
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.RunTestSuiteInput
import io.github.makeforge.clapi.mcp.server.backend.SanitizedCollectionDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedConsoleEntryDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedEnvironmentDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedFolderDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedRequestDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedVariableDto
import io.github.makeforge.clapi.mcp.server.backend.SetVariableInput
import io.github.makeforge.clapi.mcp.server.backend.TestSuiteResultDto
import io.github.makeforge.clapi.mcp.server.backend.UpsertEnvironmentInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import io.github.makeforge.clapi.mcp.server.backend.WorkspaceTreeNodeDto
import io.github.makeforge.clapi.ui.shared.network.NetworkClientFactory
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.persistence.toGlobalOpenCollectionDocument
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.ConsoleEntry
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Live [McpWorkspaceBackend] implementation synchronized with a running [AppState] instance.
 *
 * Ensures that:
 * 1. Before any MCP operation, current in-memory [AppState] collections, environments,
 *    active environment, and global variables are flushed to the backing repository.
 * 2. After any MCP mutation, [AppState] collections, environments, active environment,
 *    and global variables are immediately updated and persisted via [WorkspaceRepository].
 */
class AppStateWorkspaceBackend(
    val appState: AppState,
    apiClient: ApiClient = KtorApiClient(),
) : McpWorkspaceBackend {

    private val syncMutex = Mutex()

    private val delegate: HeadlessWorkspaceBackend
        get() = cachedDelegate ?: createDelegate().also { cachedDelegate = it }

    private var cachedDelegate: HeadlessWorkspaceBackend? = null
    private val customApiClient: ApiClient = apiClient
    private var lastSyncedGlobalDoc: io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument? = null

    private fun createDelegate(): HeadlessWorkspaceBackend {
        val repo = WorkspaceRepository.canonicalRepository
        val baseDir = (repo as? DefaultCanonicalCollectionRepository)?.baseDirectory
        return HeadlessWorkspaceBackend(
            baseDirectory = baseDir,
            canonicalRepository = repo,
            apiClient = customApiClient,
            grpcClient = NetworkClientFactory.buildGrpcClient(appState.settings),
            gitRepositoryManager = appState.gitRepositoryManager,
        )
    }

    private suspend fun syncFromAppStateToDelegate() {
        val globalDoc = appState.toGlobalOpenCollectionDocument()
        if (globalDoc != lastSyncedGlobalDoc) {
            delegate.canonicalRepository.saveGlobalCollection(globalDoc)
            lastSyncedGlobalDoc = globalDoc
        }
        val activeEnvName = appState.environments.getOrNull(appState.selectedEnvIndex)?.name
        if (activeEnvName != null) {
            runCatching { delegate.setActiveEnvironment(activeEnvName) }
        }
        // Sync global variables into delegate
        for (gv in appState.globalVariables) {
            if (gv.key.isBlank()) continue
            val kind = when {
                gv.secret -> "secret"
                gv.isDynamic -> "dynamic"
                else -> "regular"
            }
            delegate.setVariable(
                SetVariableInput(
                    scope = "global",
                    key = gv.key.trim(),
                    action = "upsert",
                    kind = kind,
                    variableType = gv.variableType.value,
                    value = gv.value,
                    enabled = gv.enabled,
                    dynamicScript = gv.dynamicScript,
                    dynamicParameters = gv.dynamicParameters.toList(),
                )
            )
        }
        // Sync in-memory environment secret values into delegate
        for (env in appState.environments) {
            for (v in env.variables) {
                if (v.secret && v.key.isNotBlank() && v.value.isNotBlank()) {
                    delegate.setVariable(
                        SetVariableInput(
                            scope = "environment",
                            target = env.name,
                            key = v.key.trim(),
                            action = "upsert",
                            kind = "secret",
                            variableType = v.variableType.value,
                            value = v.value,
                            enabled = v.enabled,
                        )
                    )
                }
            }
        }
    }

    private suspend fun syncFromDelegateToAppState() {
        // Preserve in-memory secret values before reload
        val envSecretCache = mutableMapOf<Pair<String, String>, String>()
        appState.environments.forEach { env ->
            env.variables.filter { it.secret && it.value.isNotBlank() }.forEach { v ->
                envSecretCache[env.name to v.key.trim()] = v.value
            }
        }

        WorkspaceRepository.load(appState)

        // Restore in-memory secret values
        appState.environments.forEach { env ->
            env.variables.filter { it.secret && it.value.isBlank() }.forEach { v ->
                envSecretCache[env.name to v.key.trim()]?.let { v.value = it }
            }
        }

        val overview = delegate.listEnvironments(includeVariables = false)
        if (overview.activeEnvironment != null) {
            val idx = appState.environments.indexOfFirst { it.name == overview.activeEnvironment }
            if (idx >= 0) {
                appState.selectedEnvIndex = idx
                appState.settings.selectedEnvName = overview.activeEnvironment!!
            }
        }

        appState.notifyCollectionsChanged()
        WorkspaceRepository.save(appState)
        lastSyncedGlobalDoc = appState.toGlobalOpenCollectionDocument()
    }

    override suspend fun listTree(
        collectionFilter: String?,
        kindFilter: String?,
        query: String?,
    ): List<WorkspaceTreeNodeDto> = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.listTree(collectionFilter, kindFilter, query)
    }

    override suspend fun getCollection(idOrPath: String): SanitizedCollectionDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.getCollection(idOrPath)
    }

    override suspend fun manageCollection(input: ManageCollectionInput): SanitizedCollectionDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.manageCollection(input)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun deleteCollection(idOrPath: String, confirm: Boolean): DeleteResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.deleteCollection(idOrPath, confirm)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun manageFolder(input: ManageFolderInput): SanitizedFolderDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.manageFolder(input)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun deleteFolder(idOrPath: String, confirm: Boolean): DeleteResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.deleteFolder(idOrPath, confirm)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun getRequest(idOrPath: String): SanitizedRequestDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.getRequest(idOrPath)
    }

    override suspend fun upsertRequest(input: UpsertRequestInput): SanitizedRequestDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.upsertRequest(input)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun deleteRequest(idOrPath: String, confirm: Boolean): DeleteResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.deleteRequest(idOrPath, confirm)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun listEnvironments(includeVariables: Boolean): EnvironmentsOverviewDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.listEnvironments(includeVariables)
    }

    override suspend fun getEnvironmentDetails(name: String): SanitizedEnvironmentDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.getEnvironmentDetails(name)
    }

    override suspend fun setActiveEnvironment(name: String?): EnvironmentsOverviewDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.setActiveEnvironment(name)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun upsertEnvironment(input: UpsertEnvironmentInput): SanitizedEnvironmentDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.upsertEnvironment(input)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun deleteEnvironment(name: String, confirm: Boolean): DeleteResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.deleteEnvironment(name, confirm)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun setVariable(input: SetVariableInput): SanitizedVariableDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.setVariable(input)
        val scope = input.scope.trim().lowercase()
        val varType = VariableType.fromValue(input.variableType) ?: VariableType.STRING
        if (scope == "global") {
            val idx = appState.globalVariables.indexOfFirst { it.key.trim() == input.key.trim() }
            if (input.action.equals("delete", ignoreCase = true)) {
                if (idx >= 0) appState.globalVariables.removeAt(idx)
            } else {
                val prevVal = appState.globalVariables.getOrNull(idx)?.value.orEmpty()
                val effectiveVal = if (input.kind == "secret" && input.value == MASKED_SECRET_PLACEHOLDER) prevVal else input.value
                val kv = MutableKeyValue(
                    key = input.key.trim(),
                    value = effectiveVal,
                    enabled = input.enabled,
                    secret = input.kind == "secret",
                    isDynamic = input.kind == "dynamic",
                    variableType = varType,
                    dynamicScript = input.dynamicScript.orEmpty(),
                    dynamicParameters = input.dynamicParameters,
                )
                if (idx >= 0) appState.globalVariables[idx] = kv else appState.globalVariables.add(kv)
            }
            WorkspaceRepository.save(appState)
        } else {
            syncFromDelegateToAppState()
            if (scope == "environment" && input.kind == "secret" && input.value != MASKED_SECRET_PLACEHOLDER) {
                val envName = input.target ?: appState.environments.getOrNull(appState.selectedEnvIndex)?.name
                val env = appState.environments.firstOrNull { it.name.equals(envName, ignoreCase = true) }
                env?.variables?.firstOrNull { it.key.trim() == input.key.trim() }?.let { v ->
                    v.value = input.value
                }
            }
        }
        result
    }

    override suspend fun executeRequest(input: ExecuteRequestInput): ExecutionResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val res = delegate.executeRequest(input)
        res.scriptLogs.forEach { msg ->
            appendConsoleEntry(ConsoleEntry(message = "[MCP] $msg", level = LogLevel.INFO))
        }
        res.scriptError?.let { err ->
            appendConsoleEntry(ConsoleEntry(message = "[MCP Error] $err", level = LogLevel.ERROR))
        }
        res
    }

    private fun appendConsoleEntry(entry: ConsoleEntry) {
        appState.consoleLogs.add(entry)
        val overflow = appState.consoleLogs.size - MAX_CONSOLE_LOG_ENTRIES
        if (overflow > 0) {
            repeat(overflow) { appState.consoleLogs.removeAt(0) }
        }
    }

    private companion object {
        private const val MAX_CONSOLE_LOG_ENTRIES = 500
    }

    override suspend fun subscribeRabbitMq(
        input: io.github.makeforge.clapi.mcp.server.backend.SubscribeRabbitMqInput,
    ): io.github.makeforge.clapi.mcp.server.backend.SubscribeRabbitMqResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.subscribeRabbitMq(input)
    }

    override suspend fun inspectResponse(input: InspectResponseInput): InspectResponseResultDto = syncMutex.withLock {
        delegate.inspectResponse(input)
    }

    override suspend fun getLatestResponse(idOrPath: String, maxBodyLength: Int): ExecutionResultDto? = syncMutex.withLock {
        delegate.getLatestResponse(idOrPath, maxBodyLength)
    }

    override suspend fun runTestSuite(input: RunTestSuiteInput): TestSuiteResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.runTestSuite(input)
    }

    override suspend fun importSpecification(input: ImportSpecInput): SanitizedCollectionDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.importSpecification(input)
        syncFromDelegateToAppState()
        result
    }

    override suspend fun exportCollection(input: ExportCollectionInput): ExportResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        delegate.exportCollection(input)
    }

    override suspend fun gitSync(input: GitSyncInput): GitSyncResultDto = syncMutex.withLock {
        syncFromAppStateToDelegate()
        val result = delegate.gitSync(input)
        if (input.action.equals("pull", ignoreCase = true)) {
            syncFromDelegateToAppState()
        }
        result
    }

    override suspend fun getConsoleLogs(limit: Int): List<SanitizedConsoleEntryDto> = syncMutex.withLock {
        val delegateLogs = delegate.getConsoleLogs(limit)
        if (delegateLogs.isNotEmpty()) return@withLock delegateLogs
        appState.consoleLogs.takeLast(limit.coerceIn(1, 200)).map {
            SanitizedConsoleEntryDto(
                timestamp = it.timestamp,
                level = it.level.name,
                message = it.message,
            )
        }
    }
}
