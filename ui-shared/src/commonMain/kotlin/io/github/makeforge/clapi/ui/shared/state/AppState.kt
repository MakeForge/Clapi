package io.github.makeforge.clapi.ui.shared.state

import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestSettings
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.makeforge.clapi.core.logging.AppLogging
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.storage.GitCredentialsConfig
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.makeforge.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.makeforge.clapi.core.network.keeper.DefaultKeeperClientManager
import io.github.makeforge.clapi.core.network.websocket.WebSocketClientManager
import io.github.makeforge.clapi.core.storage.git.CollectionGitStatus
import io.github.makeforge.clapi.core.storage.git.GitRepositoryManager
import io.github.makeforge.clapi.core.storage.git.ProtoPathResolver
import io.github.makeforge.clapi.ui.shared.components.syncParamsFromUrl
import io.github.makeforge.clapi.ui.shared.mcp.McpSessionState
import io.github.makeforge.clapi.ui.shared.mcp.server.EmbeddedMcpServerController
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportNaming
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis
import io.github.makeforge.clapi.ui.shared.platform.generateUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

// ── Global application state ────────────────────────────────────

class AppState(openDefaultTab: Boolean = true) {
    val dynamicVariableRegistry: DynamicVariableRegistry = DynamicVariableRegistry()

    // ── workspace mode ──
    var workspaceMode by mutableStateOf(WorkspaceMode.HTTP)

    // ── sidebar ────────
    var sidebarExpanded      by mutableStateOf(true)
    var sidebarWidth         by mutableStateOf(260f)
    var historyExpanded      by mutableStateOf(false)
    var collectionsExpanded  by mutableStateOf(false)
    var environmentsExpanded by mutableStateOf(false)
    var sidebarSearchQuery   by mutableStateOf("")
    var selectedCollectionId by mutableStateOf<String?>(null)
    var selectedRequestId    by mutableStateOf<String?>(null)
    var sidebarScrollToRequestId by mutableStateOf<String?>(null)
    var collectionsRevision  by mutableStateOf(0)
    var historyRevision      by mutableStateOf(0)

    val selectedFolderIds = mutableStateListOf<String>()
    val selectedRequestIds = mutableStateListOf<String>()
    var lastSelectedSidebarItemId by mutableStateOf<String?>(null)

    fun clearSidebarSelection() {
        selectedFolderIds.clear()
        selectedRequestIds.clear()
        lastSelectedSidebarItemId = null
    }

    fun pruneSidebarSelection(removedIds: Collection<String>) {
        if (removedIds.isEmpty()) return
        val removedSet = removedIds.toHashSet()
        selectedFolderIds.removeAll { it in removedSet }
        selectedRequestIds.removeAll { it in removedSet }
        if (lastSelectedSidebarItemId in removedSet) {
            lastSelectedSidebarItemId = selectedRequestIds.lastOrNull() ?: selectedFolderIds.lastOrNull()
        }
    }

    fun selectSidebarFolder(
        folderId: String,
        isCtrlPressed: Boolean = false,
        isShiftPressed: Boolean = false,
        visibleFolders: List<String> = emptyList(),
    ) {
        selectedRequestIds.clear()
        if (isCtrlPressed && isShiftPressed) {
            val anchor = lastSelectedSidebarItemId
            val anchorIdx = if (anchor != null) visibleFolders.indexOf(anchor) else -1
            val targetIdx = visibleFolders.indexOf(folderId)
            if (anchorIdx >= 0 && targetIdx >= 0) {
                val range = minOf(anchorIdx, targetIdx)..maxOf(anchorIdx, targetIdx)
                val toAdd = visibleFolders.slice(range)
                for (id in toAdd) {
                    if (id !in selectedFolderIds) {
                        selectedFolderIds.add(id)
                    }
                }
            } else {
                if (folderId !in selectedFolderIds) {
                    selectedFolderIds.add(folderId)
                }
                lastSelectedSidebarItemId = folderId
            }
        } else if (isCtrlPressed) {
            if (folderId in selectedFolderIds) {
                selectedFolderIds.removeAll { it == folderId }
                if (lastSelectedSidebarItemId == folderId) {
                    lastSelectedSidebarItemId = selectedFolderIds.lastOrNull()
                }
            } else {
                selectedFolderIds.add(folderId)
                lastSelectedSidebarItemId = folderId
            }
        } else if (isShiftPressed) {
            val anchor = lastSelectedSidebarItemId
            val anchorIdx = if (anchor != null) visibleFolders.indexOf(anchor) else -1
            val targetIdx = visibleFolders.indexOf(folderId)
            if (anchorIdx >= 0 && targetIdx >= 0) {
                val range = minOf(anchorIdx, targetIdx)..maxOf(anchorIdx, targetIdx)
                val toSelect = visibleFolders.slice(range).distinct()
                selectedFolderIds.clear()
                selectedFolderIds.addAll(toSelect)
            } else {
                selectedFolderIds.clear()
                selectedFolderIds.add(folderId)
                lastSelectedSidebarItemId = folderId
            }
        } else {
            selectedFolderIds.clear()
            selectedFolderIds.add(folderId)
            lastSelectedSidebarItemId = folderId
        }
    }

    fun selectSidebarRequest(
        requestId: String,
        isCtrlPressed: Boolean = false,
        isShiftPressed: Boolean = false,
        visibleRequests: List<String> = emptyList(),
    ) {
        selectedFolderIds.clear()
        if (isCtrlPressed && isShiftPressed) {
            val anchor = lastSelectedSidebarItemId
            val anchorIdx = if (anchor != null) visibleRequests.indexOf(anchor) else -1
            val targetIdx = visibleRequests.indexOf(requestId)
            if (anchorIdx >= 0 && targetIdx >= 0) {
                val range = minOf(anchorIdx, targetIdx)..maxOf(anchorIdx, targetIdx)
                val toAdd = visibleRequests.slice(range)
                for (id in toAdd) {
                    if (id !in selectedRequestIds) {
                        selectedRequestIds.add(id)
                    }
                }
            } else {
                if (requestId !in selectedRequestIds) {
                    selectedRequestIds.add(requestId)
                }
                lastSelectedSidebarItemId = requestId
            }
        } else if (isCtrlPressed) {
            if (requestId in selectedRequestIds) {
                selectedRequestIds.removeAll { it == requestId }
                if (lastSelectedSidebarItemId == requestId) {
                    lastSelectedSidebarItemId = selectedRequestIds.lastOrNull()
                }
            } else {
                selectedRequestIds.add(requestId)
                lastSelectedSidebarItemId = requestId
            }
        } else if (isShiftPressed) {
            val anchor = lastSelectedSidebarItemId
            val anchorIdx = if (anchor != null) visibleRequests.indexOf(anchor) else -1
            val targetIdx = visibleRequests.indexOf(requestId)
            if (anchorIdx >= 0 && targetIdx >= 0) {
                val range = minOf(anchorIdx, targetIdx)..maxOf(anchorIdx, targetIdx)
                val toSelect = visibleRequests.slice(range).distinct()
                selectedRequestIds.clear()
                selectedRequestIds.addAll(toSelect)
            } else {
                selectedRequestIds.clear()
                selectedRequestIds.add(requestId)
                lastSelectedSidebarItemId = requestId
            }
        } else {
            selectedRequestIds.clear()
            selectedRequestIds.add(requestId)
            lastSelectedSidebarItemId = requestId
        }
    }

    /** Per-folder expanded state. Absent key → expanded (true) by default. */
    val collectionExpandedState = mutableStateMapOf<String, Boolean>()

    // ── split fractions ─
    var requestResponseSplit by mutableStateOf(0.50f)
    var mainVerticalSplit    by mutableStateOf(0.73f)   // main area / bottom panel

    // ── response view state (expand / collapse / detach) ─
    var responseDisplayMode by mutableStateOf(ResponseDisplayMode.EXPANDED)

    val isResponseCollapsed: Boolean get() = responseDisplayMode == ResponseDisplayMode.COLLAPSED
    val isResponseDetached:  Boolean get() = responseDisplayMode == ResponseDisplayMode.DETACHED
    val isResponseExpanded:  Boolean get() = responseDisplayMode == ResponseDisplayMode.EXPANDED

    fun collapseResponse() {
        responseDisplayMode = ResponseDisplayMode.COLLAPSED
    }

    fun expandResponse() {
        responseDisplayMode = ResponseDisplayMode.EXPANDED
    }

    fun detachResponse() {
        responseDisplayMode = ResponseDisplayMode.DETACHED
    }

    fun reattachResponse() {
        responseDisplayMode = ResponseDisplayMode.EXPANDED
    }

    fun closeDetachedResponse() {
        responseDisplayMode = ResponseDisplayMode.COLLAPSED
    }

    // ── tabs ───────────
    val openTabs       = mutableStateListOf<RequestTabState>().also {
        if (openDefaultTab) it.add(RequestTabState())
    }
    var activeTabIndex by mutableStateOf(if (openDefaultTab) 0 else -1)
    val activeTab: RequestTabState? get() = openTabs.getOrNull(activeTabIndex)

    // ── MCP sessions ──
    // Long-lived, keyed by tab id so an MCP connection (and its loaded tools/
    // resources) survives tab switches. Disposed only when the tab is closed.
    /** Application-lifetime scope for background work that must outlive individual composables. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mcpSessions = mutableMapOf<String, McpSessionState>()
    private var disposalJob: Job? = null
    val embeddedMcpServerController: EmbeddedMcpServerController by lazy { EmbeddedMcpServerController(this) }

    /** Returns the persistent MCP session for [tabId], creating it on first use. */
    fun getOrCreateMcpSession(tabId: String): McpSessionState {
        check(disposalJob == null) { "AppState is disposed" }
        return mcpSessions.getOrPut(tabId) {
            McpSessionState(appScope, onConsole = { message, level ->
                logNetworkEvent(message, level, echoToConsole = false)
            })
        }
    }

    /** Disconnects and forgets the MCP session for [tabId] (called on tab close). */
    fun disposeMcpSession(tabId: String) {
        mcpSessions.remove(tabId)?.let { session -> appScope.launch { session.disconnect() } }
    }

    /**
     * Releases editor caches, active requests, MCP transports, and application work.
     * Idempotent: repeated callers receive the same completion [Job].
     */
    fun dispose(): Job {
        disposalJob?.let { return it }
        runCatching { embeddedMcpServerController.stop() }
        openTabs.forEach { tab ->
            tab.currentJob?.cancel()
            tab.disposeBodyViewModels()
        }
        val sessions = mcpSessions.values.toList()
        mcpSessions.clear()
        val cleanup = appScope.launch {
            sessions.forEach { session -> runCatching { session.disconnect() } }
        }
        disposalJob = cleanup
        cleanup.invokeOnCompletion { appScope.cancel() }
        return cleanup
    }

    /** Awaitable shutdown path used by desktop exit and lifecycle tests. */
    suspend fun disposeAndAwait() {
        dispose().join()
    }

    // ── bottom panel ──
    var selectedBottomTab    by mutableStateOf(BottomTab.CONSOLE)
    var bottomPanelExpanded  by mutableStateOf(false)
    var bottomPanelHeight    by mutableStateOf(200f)
    val diagnostics = DiagnosticsLogState()
    val consoleLogs: androidx.compose.runtime.snapshots.SnapshotStateList<ConsoleEntry>
        get() = diagnostics.consoleLogs
    /** Structured network-event log shown in the Logs tab (fixes M-3). */
    val networkEventLogs: androidx.compose.runtime.snapshots.SnapshotStateList<ConsoleEntry>
        get() = diagnostics.networkEventLogs
    private val fallbackTestResults = mutableStateListOf<TestResultEntry>()
    internal var standaloneTestResultsTab by mutableStateOf<RequestTabState?>(null)
    val testResults: androidx.compose.runtime.snapshots.SnapshotStateList<TestResultEntry>
        get() {
            val active = activeTab
            val standalone = standaloneTestResultsTab
            return when {
                standalone != null && standalone !in openTabs && (active == null || active.testResults.isEmpty()) -> standalone.testResults
                active != null -> active.testResults
                else -> fallbackTestResults
            }
        }

    // ── dialogs / overlays ──
    // State lives in [DialogState]; the boolean-style properties below are thin helpers over it.
    val dialogs = DialogState()
    var showSettingsDialog: Boolean
        get() = dialogs.isShown(AppDialog.Settings)
        set(value) = dialogs.setShown(AppDialog.Settings, value)
    var showHelpDialog: Boolean
        get() = dialogs.isShown(AppDialog.Help)
        set(value) = dialogs.setShown(AppDialog.Help, value)
    var showGlobalVariablesDialog: Boolean
        get() = dialogs.isShown(AppDialog.GlobalVariables)
        set(value) = dialogs.setShown(AppDialog.GlobalVariables, value)
    var showImportSpecificationDialog: Boolean
        get() = dialogs.isShown(AppDialog.ImportSpecification)
        set(value) = dialogs.setShown(AppDialog.ImportSpecification, value)
    var showExportCollectionsDialog: Boolean
        get() = dialogs.isShown(AppDialog.ExportCollections)
        set(value) = dialogs.setShown(AppDialog.ExportCollections, value)
    var showGitSyncDialog: Boolean
        get() = dialogs.isShown(AppDialog.GitSync)
        set(value) = dialogs.setShown(AppDialog.GitSync, value)

    /** True while the environment editor is open; close it via `dialogs.dismissEnvironmentEdit()`. */
    val showEnvEditDialog: Boolean get() = dialogs.environmentEdit != null
    /** Index into [environments] of the environment being edited, or -1 when the editor is closed. */
    val editingEnvIndex: Int get() = dialogs.environmentEdit?.index ?: -1

    // ── Git synchronization ──
    var gitRepositoryManager: GitRepositoryManager = GitRepositoryManager()
    val gitStatusesByRepoDir = mutableStateMapOf<String, CollectionGitStatus>()

    val activeCollectionNode: CollectionNode?
        get() {
            val tab = activeTab
            if (tab != null) {
                if (tab.isCollectionTab) {
                    collections.firstOrNull { it.id == tab.id || it.id == tab.collectionId }?.let { return it }
                }
                tab.collectionId?.let { cid ->
                    collections.firstOrNull { it.id == cid }?.let { return it }
                }
                val rootId = rootCollectionIdContaining(tab.id)
                if (rootId != null) {
                    collections.firstOrNull { it.id == rootId }?.let { return it }
                }
                tab.collectionName?.let { cname ->
                    collections.firstOrNull { it.name == cname }?.let { return it }
                }
            }
            selectedCollectionId?.let { sid ->
                collections.firstOrNull { it.id == sid }?.let { return it }
                val rootId = rootCollectionIdContaining(sid)
                if (rootId != null) {
                    collections.firstOrNull { it.id == rootId }?.let { return it }
                }
            }
            selectedRequestId?.let { rid ->
                val rootId = rootCollectionIdContaining(rid)
                if (rootId != null) {
                    collections.firstOrNull { it.id == rootId }?.let { return it }
                }
            }
            return collections.firstOrNull { it.storageType == StorageType.GIT } ?: collections.firstOrNull()
        }

    val activeGitCollectionNode: CollectionNode?
        get() {
            val active = activeCollectionNode
            if (active != null && active.storageType == StorageType.GIT && active.gitRemoteUrl.isNotBlank()) {
                return active
            }
            return null
        }

    val activeGitStatus: CollectionGitStatus?
        get() {
            val col = activeGitCollectionNode ?: return null
            val repoDir = gitRepositoryManager.getRepositoryDirectory(col.gitRemoteUrl)
            return gitStatusesByRepoDir[repoDir] ?: CollectionGitStatus(branch = col.gitBranch, isClean = true)
        }

    fun getCollectionGitDirectory(tab: RequestTabState? = activeTab): String? {
        val colNode = if (tab != null) {
            tab.collectionId?.let { cid -> collections.firstOrNull { it.id == cid } }
                ?: rootCollectionIdContaining(tab.id)?.let { rid -> collections.firstOrNull { it.id == rid } }
                ?: tab.collectionName?.let { cname -> collections.firstOrNull { it.name == cname } }
                ?: activeCollectionNode
        } else {
            activeCollectionNode
        }
        if (colNode == null || colNode.storageType != StorageType.GIT || colNode.gitRemoteUrl.isBlank()) {
            return null
        }
        return gitRepositoryManager.getCollectionDirectory(colNode.gitRemoteUrl, colNode.name)
    }

    suspend fun refreshGitStatusForCollection(collection: CollectionNode): CollectionGitStatus? {
        if (collection.storageType != StorageType.GIT || collection.gitRemoteUrl.isBlank()) return null
        val repoDir = gitRepositoryManager.getRepositoryDirectory(collection.gitRemoteUrl)
        val status = runCatching {
            gitRepositoryManager.checkStatus(repoDir)
        }.getOrElse { e ->
            CollectionGitStatus(branch = collection.gitBranch, isClean = false, lastError = e.message)
        }
        val effectiveStatus = if (status.branch.isBlank()) status.copy(branch = collection.gitBranch) else status
        gitStatusesByRepoDir[repoDir] = effectiveStatus
        return effectiveStatus
    }

    fun refreshActiveGitStatusAsync() {
        val col = activeGitCollectionNode ?: return
        appScope.launch {
            refreshGitStatusForCollection(col)
        }
    }

    fun refreshAllGitStatusesAsync() {
        val gitCols = collections.filter { it.storageType == StorageType.GIT && it.gitRemoteUrl.isNotBlank() }
        if (gitCols.isEmpty()) return
        appScope.launch {
            gitCols.distinctBy { it.gitRemoteUrl }.forEach { col ->
                refreshGitStatusForCollection(col)
            }
        }
    }

    // ── operation / error / confirm alerts (delegated to [DialogState]) ──
    val showOperationDialog: Boolean get() = dialogs.alert<AppAlert.Operation>() != null
    val operationTitle: String get() = dialogs.alert<AppAlert.Operation>()?.title.orEmpty()
    val operationMessage: String get() = dialogs.alert<AppAlert.Operation>()?.message.orEmpty()

    val showErrorDialog: Boolean get() = dialogs.alert<AppAlert.Error>() != null
    val errorDialogTitle: String get() = dialogs.alert<AppAlert.Error>()?.title ?: "Operation failed"
    val errorDialogMessage: String get() = dialogs.alert<AppAlert.Error>()?.message.orEmpty()

    val showConfirmDialog: Boolean get() = dialogs.alert<AppAlert.Confirm>() != null
    val confirmDialogTitle: String get() = dialogs.alert<AppAlert.Confirm>()?.title.orEmpty()
    val confirmDialogMessage: String get() = dialogs.alert<AppAlert.Confirm>()?.message.orEmpty()

    /** Show a [ConfirmDeleteDialog] and run [action] if the user confirms. */
    fun showConfirm(title: String, message: String, action: () -> Unit) =
        dialogs.showConfirm(title, message, action)

    fun resolveConfirm(confirmed: Boolean) = dialogs.resolveConfirm(confirmed)

    fun startOperation(title: String, message: String, job: Job) =
        dialogs.startOperation(title, message, job)

    fun finishOperation() = dialogs.finishOperation()

    fun cancelOperation() {
        dialogs.cancelOperation()
        log("Operation canceled", LogLevel.WARNING)
    }

    fun showError(title: String, message: String) = dialogs.showError(title, message)

    fun dismissError() = dialogs.dismissError()

    // ── environment ──
    private var _selectedEnvIndex by mutableStateOf(0)
    var selectedEnvIndex: Int
        get() = _selectedEnvIndex
        set(value) {
            _selectedEnvIndex = value
            val current = environments.getOrNull(value)
            val hasKeePassXc = current?.externalSecretsProvider.equals("keepassxc", ignoreCase = true) &&
                    current?.externalSecrets?.any { it.enabled } == true
            if (!hasKeePassXc) {
                appScope.launch {
                    DefaultKeePassXcClientManager.Shared.onEnvironmentSwitched(false)
                }
            }
            val hasKeeper = current?.externalSecretsProvider.equals("keeper-security-cloud", ignoreCase = true) &&
                    current?.externalSecrets?.any { it.enabled } == true
            if (!hasKeeper) {
                appScope.launch {
                    DefaultKeeperClientManager.Shared.onEnvironmentSwitched(false)
                }
            }
        }
    val environments = mutableStateListOf<EnvState>()

    // ── global variables (lowest priority, overridden by environment + local) ──
    val globalVariables = mutableStateListOf<MutableKeyValue>()
    val collectionVariables = mutableStateMapOf<String, String>()
    val collectionVariablesById = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>>()

    /**
     * Resolves the root [CollectionNode] owning [tab], or null if [tab] is not associated with a collection.
     */
    fun resolveRootCollectionNode(tab: RequestTabState? = activeTab): CollectionNode? {
        if (tab == null) return null
        if (tab.isCollectionTab) {
            return collections.firstOrNull { it.id == tab.id }
        }
        return getAncestorFolders(tab).firstOrNull { it.isCollection }
            ?: tab.collectionId?.let { id -> collections.firstOrNull { it.id == id } }
            ?: tab.collectionName?.let { name -> collections.firstOrNull { it.name == name } }
    }

    /**
     * Returns the effective collection variables for [tab]'s root collection, isolating collection
     * variables across different collections in the workspace.
     */
    fun collectionVariablesFor(tab: RequestTabState? = activeTab): Map<String, String> {
        val root = resolveRootCollectionNode(tab) ?: return collectionVariables.toMap()
        val openRootTab = openTabs.firstOrNull { it.id == root.id && it.isFolderTab }
        val baseVars = if (openRootTab != null) {
            openRootTab.variables
                .filter { it.enabled && !it.isDynamic && it.key.isNotBlank() }
                .associate { it.key to it.value }
        } else {
            root.variableEntries
                ?.filter { it.enabled && !it.isDynamic && it.key.isNotBlank() }
                ?.associate { it.key to it.value }
                .orEmpty()
        }
        val runtimeVars = collectionVariablesById[root.id]?.toMap().orEmpty()
        return baseVars + runtimeVars
    }

    // ── collection protobuf ──
    val collectionProtoFiles = mutableStateListOf<ProtoFileItemState>()
    val collectionImportPaths = mutableStateListOf<ProtoImportPathState>()

    val selectedEnvironment: EnvState? get() = environments.getOrNull(selectedEnvIndex)

    /**
     * Variable layers for the active environment (used in request variable resolution).
     * Resolution priority (first match wins): Environment → Folder (leaf-to-root) → Collection → Global.
     * Environment variables override folder, collection, and global variables.
     */
    fun activeVariableLayers(tab: RequestTabState? = activeTab): List<Map<String, String>> {
        val layers = mutableListOf<Map<String, String>>()
        val envStaticMap = selectedEnvironment?.toVariableMap(environments).orEmpty()
        val shadowedByDynamic = selectedEnvironment
            ?.resolveEffectiveDynamicVariables(environments)
            ?.map { it.name.trim() }
            ?.toMutableSet()
            ?: mutableSetOf()
        val definedStaticNames = envStaticMap.keys.toMutableSet()
        layers.add(envStaticMap)

        var includedRootCollection = false
        if (tab != null) {
            val ancestors = getAncestorFolders(tab).asReversed()
            for (folder in ancestors) {
                val openFolderTab = openTabs.firstOrNull { it.id == folder.id && it.isFolderTab }
                val baseVars = if (openFolderTab != null) {
                    openFolderTab.variables.forEach { v ->
                        val k = v.key.trim()
                        if (v.enabled && k.isNotEmpty() && v.isDynamic && k !in definedStaticNames) {
                            shadowedByDynamic.add(k)
                        }
                    }
                    openFolderTab.variables
                        .filter { it.enabled && !it.isDynamic && it.key.isNotBlank() && it.key.trim() !in shadowedByDynamic }
                        .associate { it.key to it.value }
                } else {
                    folder.variableEntries?.forEach { v ->
                        val k = v.key.trim()
                        if (v.enabled && k.isNotEmpty() && v.isDynamic && k !in definedStaticNames) {
                            shadowedByDynamic.add(k)
                        }
                    }
                    folder.variableEntries
                        ?.filter { it.enabled && !it.isDynamic && it.key.isNotBlank() && it.key.trim() !in shadowedByDynamic }
                        ?.associate { it.key to it.value }
                        .orEmpty()
                }
                val fVars = if (folder.isCollection) {
                    includedRootCollection = true
                    (baseVars + collectionVariablesById[folder.id]?.toMap().orEmpty()).filterKeys { it.trim() !in shadowedByDynamic }
                } else {
                    baseVars
                }
                definedStaticNames.addAll(fVars.keys.map { it.trim() })
                if (fVars.isNotEmpty()) {
                    layers.add(fVars)
                }
            }
            if (tab.isFolderTab) {
                tab.variables.forEach { v ->
                    val k = v.key.trim()
                    if (v.enabled && k.isNotEmpty() && v.isDynamic && k !in definedStaticNames) {
                        shadowedByDynamic.add(k)
                    }
                }
                val baseTabVars = tab.variables
                    .filter { it.enabled && !it.isDynamic && it.key.isNotBlank() && it.key.trim() !in shadowedByDynamic }
                    .associate { it.key to it.value }
                val tabVars = if (tab.isCollectionTab) {
                    includedRootCollection = true
                    (baseTabVars + collectionVariablesById[tab.id]?.toMap().orEmpty()).filterKeys { it.trim() !in shadowedByDynamic }
                } else {
                    baseTabVars
                }
                definedStaticNames.addAll(tabVars.keys.map { it.trim() })
                if (tabVars.isNotEmpty()) {
                    layers.add(tabVars)
                }
            }
        }

        if (!includedRootCollection) {
            val colVars = collectionVariablesFor(tab).filterKeys { it.trim() !in shadowedByDynamic }
            if (colVars.isNotEmpty()) {
                layers.add(colVars)
            }
        }
        val globals = globalVariables
            .filter { it.enabled && !it.isDynamic && it.key.trim() !in shadowedByDynamic }
            .associate { it.key to it.value }
        if (globals.isNotEmpty()) {
            layers.add(globals)
        }
        return layers
    }

    /**
     * Collects effective user-defined dynamic variables for [tab] in precedence order
     * (Environment overrides Folder overrides Collection overrides Global), ensuring higher-precedence
     * static variables shadow lower-precedence dynamic variables.
     */
    fun activeDynamicVariables(tab: RequestTabState? = activeTab): List<DynamicVariable> {
        val effective = LinkedHashMap<String, DynamicVariable>()

        fun applyKeyValues(entries: List<KeyValueEntry>?) {
            entries?.forEach { v ->
                val k = v.key.trim()
                if (v.enabled && k.isNotEmpty()) {
                    if (v.isDynamic) {
                        effective[k] = DynamicVariable(
                            name = k,
                            type = v.variableType,
                            script = v.dynamicScript,
                            parameters = v.dynamicParameters,
                            description = v.description.takeIf { it.isNotBlank() }?.let { Description(it) },
                            disabled = null,
                        )
                    } else {
                        effective.remove(k)
                    }
                }
            }
        }

        fun applyMutableKeyValues(entries: List<MutableKeyValue>?) {
            entries?.forEach { v ->
                val k = v.key.trim()
                if (v.enabled && k.isNotEmpty()) {
                    if (v.isDynamic) {
                        effective[k] = v.toDynamicVariable()
                    } else if (!v.secret || v.value.isNotBlank()) {
                        effective.remove(k)
                    }
                }
            }
        }

        // 1. Global variables (lowest precedence)
        applyMutableKeyValues(globalVariables)

        // 2. Collection & Folder hierarchy (root to leaf)
        var includedRootCollection = false
        if (tab != null) {
            val ancestors = getAncestorFolders(tab) // root-first
            for (folder in ancestors) {
                val openFolderTab = openTabs.firstOrNull { it.id == folder.id && it.isFolderTab }
                if (openFolderTab != null) {
                    applyMutableKeyValues(openFolderTab.variables)
                } else {
                    applyKeyValues(folder.variableEntries)
                }
                if (folder.isCollection) {
                    includedRootCollection = true
                    collectionVariablesById[folder.id]?.keys?.forEach { effective.remove(it.trim()) }
                }
            }
            if (tab.isFolderTab) {
                applyMutableKeyValues(tab.variables)
                if (tab.isCollectionTab) {
                    includedRootCollection = true
                    collectionVariablesById[tab.id]?.keys?.forEach { effective.remove(it.trim()) }
                }
            }
        }
        if (!includedRootCollection) {
            val root = resolveRootCollectionNode(tab)
            if (root != null) {
                val openRootTab = openTabs.firstOrNull { it.id == root.id && it.isFolderTab }
                if (openRootTab != null) {
                    applyMutableKeyValues(openRootTab.variables)
                } else {
                    applyKeyValues(root.variableEntries)
                }
                collectionVariablesById[root.id]?.keys?.forEach { effective.remove(it.trim()) }
            }
        }

        // 3. Selected Environment (with inheritance, highest scope before request)
        val env = selectedEnvironment
        if (env != null) {
            val visited = mutableSetOf<String>()
            fun walkEnv(current: EnvState) {
                if (!visited.add(current.name)) return
                val parentName = current.extends?.trim()
                if (!parentName.isNullOrEmpty()) {
                    val parent = environments.firstOrNull { it.name == parentName }
                    if (parent != null) walkEnv(parent)
                }
                applyMutableKeyValues(current.variables)
            }
            walkEnv(env)
        }

        return effective.values.toList()
    }

    /**
     * Resolves the effective [VariableType] map across Global, Collection/Folder, and Environment scopes.
     */
    fun activeVariableTypes(tab: RequestTabState? = activeTab): Map<String, VariableType> {
        val types = LinkedHashMap<String, VariableType>()

        fun applyKeyValues(entries: List<KeyValueEntry>?) {
            entries?.forEach { v ->
                val k = v.key.trim()
                if (v.enabled && k.isNotEmpty()) {
                    types[k] = v.variableType
                }
            }
        }

        fun applyMutableKeyValues(entries: List<MutableKeyValue>?) {
            entries?.forEach { v ->
                val k = v.key.trim()
                if (v.enabled && k.isNotEmpty()) {
                    types[k] = v.variableType
                }
            }
        }

        applyMutableKeyValues(globalVariables)

        var includedRootCollection = false
        if (tab != null) {
            val ancestors = getAncestorFolders(tab)
            for (folder in ancestors) {
                val openFolderTab = openTabs.firstOrNull { it.id == folder.id && it.isFolderTab }
                if (openFolderTab != null) {
                    applyMutableKeyValues(openFolderTab.variables)
                } else {
                    applyKeyValues(folder.variableEntries)
                }
                if (folder.isCollection) {
                    includedRootCollection = true
                }
            }
            if (tab.isFolderTab) {
                applyMutableKeyValues(tab.variables)
                if (tab.isCollectionTab) {
                    includedRootCollection = true
                }
            }
        }
        if (!includedRootCollection) {
            val root = resolveRootCollectionNode(tab)
            if (root != null) {
                val openRootTab = openTabs.firstOrNull { it.id == root.id && it.isFolderTab }
                if (openRootTab != null) {
                    applyMutableKeyValues(openRootTab.variables)
                } else {
                    applyKeyValues(root.variableEntries)
                }
            }
        }

        val env = selectedEnvironment
        if (env != null) {
            val visited = mutableSetOf<String>()
            fun walkEnv(current: EnvState) {
                if (!visited.add(current.name)) return
                val parentName = current.extends?.trim()
                if (!parentName.isNullOrEmpty()) {
                    val parent = environments.firstOrNull { it.name == parentName }
                    if (parent != null) walkEnv(parent)
                }
                applyMutableKeyValues(current.variables)
            }
            walkEnv(env)
        }

        return types
    }

    /**
     * Gathers all defined variable names across all scopes (static variables from active layers,
     * active dynamic variables, external secrets, OAuth2 auth tokens, and built-in system variables).
     */
    fun allDefinedVariableNames(tab: RequestTabState? = activeTab): Set<String> {
        val names = LinkedHashSet<String>()
        activeVariableLayers(tab).forEach { layer ->
            names.addAll(layer.keys)
        }
        activeDynamicVariables(tab).forEach { dv ->
            names.add(dv.name)
        }
        selectedEnvironment?.externalSecrets?.filter { it.enabled && it.name.isNotBlank() }?.forEach {
            names.add("_secret.${it.name.trim()}")
        }
        val effectiveAuthType = if (tab != null) {
            if (tab.authType == AuthType.INHERIT) {
                resolveInheritedAuth(tab)?.authType ?: AuthType.NONE
            } else {
                tab.authType
            }
        } else {
            AuthType.NONE
        }
        if (effectiveAuthType == AuthType.OAUTH2) {
            names.add("_auth.oauth2.accessToken")
        }
        VariableResolver.dynamicVariables.forEach { dv ->
            names.add(dv.name)
            names.add(dv.name.removePrefix("$"))
        }
        return names
    }

    /**
     * Merges variables set by a pre-request or post-request script into the active environment.
     * Existing variables with the same key are updated; new keys are appended.
     */
    fun mergeScriptVariables(vars: Map<String, String>) {
        val env = selectedEnvironment ?: return
        mergeInto(env.variables, vars)
    }

    fun mergeGlobalScriptVariables(vars: Map<String, String>) {
        mergeInto(globalVariables, vars)
    }

    fun mergeCollectionScriptVariables(vars: Map<String, String>, tab: RequestTabState? = activeTab) {
        val root = resolveRootCollectionNode(tab)
        if (root != null) {
            val map = collectionVariablesById.getOrPut(root.id) { mutableStateMapOf() }
            vars.forEach { (key, value) ->
                map[key] = value
            }
            val openRootTab = openTabs.firstOrNull { it.id == root.id && it.isFolderTab }
            if (openRootTab != null) {
                mergeInto(openRootTab.variables, vars)
            }
            val updatedEntries = (root.variableEntries ?: emptyList()).toMutableList()
            vars.forEach { (key, value) ->
                val idx = updatedEntries.indexOfFirst { it.key == key }
                if (idx >= 0) {
                    updatedEntries[idx] = updatedEntries[idx].copy(value = value, enabled = true)
                } else {
                    updatedEntries.add(io.github.makeforge.clapi.core.model.KeyValueEntry(key = key, value = value, enabled = true))
                }
            }
            val rootIdx = collections.indexOfFirst { it.id == root.id }
            if (rootIdx >= 0) {
                collections[rootIdx] = collections[rootIdx].copy(variableEntries = updatedEntries)
            }
        } else {
            vars.forEach { (key, value) ->
                collectionVariables[key] = value
            }
        }
    }

    private fun mergeInto(target: MutableList<MutableKeyValue>, vars: Map<String, String>) {
        vars.forEach { (key, value) ->
            val existing = target.firstOrNull { it.key == key }
            if (existing != null) existing.value = value
            else target.add(MutableKeyValue(key = key, value = value))
        }
    }

    // ── settings ────────
    val settings = AppSettings()

    // ── history data ──
    val historyItems = mutableStateListOf<HistoryItem>()

    val collections = mutableStateListOf<CollectionNode>()

    init {
        ensureRequestRefsInitialized()
        syncHistoryWithCollections()
    }

    private data class RequestReference(
        val requestId: String,
        val requestRef: String,
        val collectionId: String?,
        val folderPath: List<String>,
        val name: String,
        val method: HttpMethodType,
        val url: String,
    )

    data class ScriptRequestTarget(
        val requestId: String,
        val name: String,
        val method: HttpMethodType,
        val url: String,
    )

    // ── actions ──────────────────────────────────────────────────

    fun addTab(
        requestId: String = generateUuid(),
        name: String = "Untitled ${openTabs.size + 1}",
        method: HttpMethodType = HttpMethodType.GET,
        url: String = "",
    ) {
        openTabs.indexOfFirst { it.id == requestId }.takeIf { it >= 0 }?.let { existingIdx ->
            activeTabIndex = existingIdx
            selectedRequestId = requestId
            return
        }
        var cName: String? = null
        var cId: String? = null
        val fPath = mutableListOf<String>()
        var found = false
        for (c in collections) {
            if (findPathRecursive(c, requestId, fPath)) {
                cName = c.name
                cId = c.id
                found = true
                break
            }
        }
        val pathWithoutNode = if (found && fPath.isNotEmpty()) fPath.dropLast(1) else emptyList()
        val node = findNodeById(collections, requestId)
        val tab = RequestTabState(
            id = requestId,
            requestRef = node?.requestRef ?: node?.id,
            name = name,
            method = method,
            url = url,
            collectionName = cName,
            collectionId = cId,
            folderPath = pathWithoutNode,
        )
        if (node?.queryEntries != null) {
            tab.params.addAll(node.queryEntries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret) })
        } else if (url.contains('?')) {
            syncParamsFromUrl(tab, url)
        }
        node?.preRequestScript?.takeIf { it.isNotBlank() }?.let { tab.preRequestScript = it }
        node?.testScript?.takeIf { it.isNotBlank() }?.let { tab.testScript = it }
        // Populate body, headers, and auth from collection node
        node?.bodyType?.let { tab.bodyType = it; tab.syncSystemHeaders() }
        node?.bodyContent?.takeIf { it.isNotBlank() }?.let { tab.bodyContent = it }
        node?.bodyContents?.forEach { (typeName, content) ->
            runCatching { BodyType.valueOf(typeName) }.getOrNull()?.let { tab.bodyContents[it] = content }
        }
        // Populate structured form rows from collection node
        if (!node?.formDataEntries.isNullOrEmpty()) {
            tab.formRows.clear()
            node!!.formDataEntries.forEach { e ->
                tab.formRows.add(MutableFormDataRow(e.key, e.type, e.value, e.description, e.enabled, e.bytesBase64))
            }
        }
        if (!node?.urlencodedEntries.isNullOrEmpty()) {
            tab.urlencodedRows.clear()
            node!!.urlencodedEntries.forEach { e ->
                tab.urlencodedRows.add(MutableFormDataRow(e.key, e.type, e.value, e.description, e.enabled))
            }
        }
        node?.authType?.let { tab.authType = it }
        node?.authUsername?.takeIf { it.isNotBlank() }?.let { tab.authUsername = it }
        node?.authPassword?.takeIf { it.isNotBlank() }?.let { tab.authPassword = it }
        node?.authToken?.takeIf { it.isNotBlank() }?.let { tab.authToken = it }
        node?.authApiKey?.takeIf { it.isNotBlank() }?.let { tab.authApiKey = it }
        node?.authApiValue?.takeIf { it.isNotBlank() }?.let { tab.authApiValue = it }
        tab.authApiPlacement = normalizeApiKeyPlacement(node?.authApiPlacement)
        node?.authOAuth2Flow?.let { tab.authOAuth2Flow = it }
        node?.authOAuth2AccessTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AccessTokenUrl = it }
        node?.authOAuth2RefreshTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RefreshTokenUrl = it }
        node?.authOAuth2AuthorizationUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AuthorizationUrl = it }
        node?.authOAuth2RedirectUri?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RedirectUri = it }
        node?.authOAuth2Scope?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Scope = it }
        node?.authOAuth2ClientId?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientId = it }
        node?.authOAuth2ClientSecret?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientSecret = it }
        node?.authOAuth2CredentialsPlacement?.let { tab.authOAuth2CredentialsPlacement = it }
        node?.authOAuth2Username?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Username = it }
        node?.authOAuth2Password?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Password = it }
        node?.headerEntries?.let { entries ->
            tab.headers.clear()
            tab.headers.addAll(entries.map { entry ->
                val system = SystemHeaderRules.isSystemHeader(entry.key)
                MutableKeyValue(entry.key, entry.value, entry.enabled, entry.secret,
                    kind = if (system) HeaderKind.SYSTEM else HeaderKind.USER, keyLocked = system)
            })
        } ?: node?.userHeaders?.forEach { (k, v) ->
            val existing = tab.headers.find { h -> h.key.equals(k, ignoreCase = true) }
            if (existing != null) existing.value = v
            else tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
        }
        tab.kind = node?.kind ?: RequestKind.HTTP
        node?.mcpConfig?.let { tab.mcpConfig = it }
        if (tab.kind == RequestKind.MCP && tab.url.isBlank()) {
            tab.url = tab.mcpConfig.url
        }
        if (tab.kind == RequestKind.GRPC) {
            tab.headers.clear()
            tab.bodyType = BodyType.JSON
            tab.protoFilePath = node?.protoFilePath
            tab.protoSyncToGit = node?.protoSyncToGit ?: (node?.protoFilePath?.let { ProtoPathResolver.isRelativeGitProtoPath(it) } ?: false)
            tab.grpcMethod = node?.grpcMethod
            tab.grpcMethodType = node?.grpcMethodType ?: "unary"
            node?.bodyContent?.takeIf { it.isNotBlank() }?.let { tab.grpcMessageBody = it }
            tab.grpcMetadata.clear()
            node?.headerEntries?.let { entries ->
                tab.grpcMetadata.addAll(
                    entries.map { entry ->
                        MutableKeyValue(entry.key, entry.value, entry.enabled, entry.secret, kind = HeaderKind.USER)
                    }
                )
            } ?: node?.userHeaders?.forEach { (k, v) ->
                tab.grpcMetadata.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
            }
        }
        if (tab.kind == RequestKind.WEBSOCKET) {
            tab.headers.clear()
            node?.headerEntries?.let { entries ->
                tab.headers.addAll(entries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret, kind = HeaderKind.USER) })
            } ?: node?.userHeaders?.forEach { (k, v) ->
                tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
            }
            if (!node?.webSocketVariants.isNullOrEmpty()) {
                tab.webSocketMessageVariants.clear()
                tab.webSocketMessageVariants.addAll(node!!.webSocketVariants)
                val sel = tab.webSocketMessageVariants.indexOfFirst { it.selected == true }
                tab.activeWebSocketVariantIndex = if (sel >= 0) sel else 0
            }
            node?.webSocketTimeout?.let { tab.webSocketTimeout = it }
            node?.webSocketKeepAliveInterval?.let { tab.webSocketKeepAliveInterval = it }
            node?.webSocketDocs?.let { tab.webSocketDocs = it }
            node?.webSocketMessageFormat?.let { tab.webSocketMessageFormat = it }
            if (node?.bodyContent != null && tab.webSocketMessageVariants.isEmpty()) {
                tab.webSocketOutgoingPayload = node.bodyContent
            }
        }
        if (tab.kind == RequestKind.RABBITMQ) {
            tab.headers.clear()
            node?.headerEntries?.let { entries ->
                tab.headers.addAll(entries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret, kind = HeaderKind.USER) })
            } ?: node?.userHeaders?.forEach { (k, v) ->
                tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
            }
            if (!node?.webSocketVariants.isNullOrEmpty()) {
                tab.webSocketMessageVariants.clear()
                tab.webSocketMessageVariants.addAll(node!!.webSocketVariants)
                val sel = tab.webSocketMessageVariants.indexOfFirst { it.selected == true }
                tab.activeWebSocketVariantIndex = if (sel >= 0) sel else 0
            }
            node?.webSocketMessageFormat?.let { tab.webSocketMessageFormat = it }
            if (node?.bodyContent != null && tab.webSocketMessageVariants.isEmpty()) {
                tab.webSocketOutgoingPayload = node.bodyContent
            }
            node?.rabbitMqDetails?.let { d ->
                tab.rabbitMqMode = d.mode
                tab.rabbitMqConnection = d.connection
                d.exchange?.let { tab.rabbitMqExchange = it }
                d.queue?.let { tab.rabbitMqQueue = it }
                tab.rabbitMqRoutingKey = d.routingKey
                tab.rabbitMqReplyQueue = d.replyQueue.orEmpty()
                d.properties?.let { tab.rabbitMqProperties = it }
                if (d.mode == io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode.SUBSCRIBE &&
                    tab.selectedRabbitMqTab == RabbitMqEditorTab.MESSAGE
                ) {
                    tab.selectedRabbitMqTab = RabbitMqEditorTab.AMQP
                }
            }
            node?.rabbitMqSettings?.let { tab.rabbitMqSettings = it }
            node?.rabbitMqDocs?.let { tab.rabbitMqDocs = it }
        }
        if (tab.kind == RequestKind.GRAPHQL) {
            tab.headers.clear()
            node?.headerEntries?.let { entries ->
                tab.headers.addAll(entries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret, kind = HeaderKind.USER) })
            } ?: node?.userHeaders?.forEach { (k, v) ->
                tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
            }
            if (!node?.graphQlVariants.isNullOrEmpty()) {
                tab.graphQlVariants.clear()
                tab.graphQlVariants.addAll(node!!.graphQlVariants)
                val sel = tab.graphQlVariants.indexOfFirst { it.selected == true }
                tab.activeGraphQLVariantIndex = if (sel >= 0) sel else 0
            }
            node?.bodyContent?.let { tab.graphQlQuery = it }
            node?.graphQlDocs?.let { tab.graphQlDocs = it }
            node?.method?.name?.let { tab.graphQlMethod = it }
            node?.graphQlSettings?.let { s ->
                (s.timeout as? JsonPrimitive)?.content?.let { tab.graphQlTimeout = it }
                (s.encodeUrl as? JsonPrimitive)?.content?.let { tab.graphQlEncodeUrl = it }
                (s.followRedirects as? JsonPrimitive)?.content?.let { tab.graphQlFollowRedirects = it }
                (s.forwardAuthorizationHeader as? JsonPrimitive)?.content?.let { tab.graphQlForwardAuthHeader = it }
                (s.maxRedirects as? JsonPrimitive)?.content?.let { tab.graphQlMaxRedirects = it }
            }
        }
        if (tab.kind != RequestKind.FOLDER && tab.kind != RequestKind.GRPC && tab.kind != RequestKind.WEBSOCKET) {
            applyInheritedDefaults(tab)
        }
        // Re-anchor the saved snapshot after all fields (including system headers
        // injected by syncSystemHeaders above) have been populated. Without this,
        // the snapshot captured in RequestTabState.init{} pre-dates the system
        // headers, so recomputeDirty() is always true immediately after opening.
        tab.restoreSavedSnapshot(null)
        openTabs.add(tab)
        activeTabIndex = openTabs.size - 1
        selectedRequestId = requestId
    }

    /**
     * Opens or focuses a dedicated settings tab for the folder with [folderId].
     */
    fun openFolder(folderId: String) {
        val folderNode = findNodeById(collections, folderId) ?: return
        if (!folderNode.isFolder) return

        val existingIdx = openTabs.indexOfFirst { it.id == folderId && it.kind == RequestKind.FOLDER }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
            selectedCollectionId = folderId
            return
        }

        var cName: String? = null
        var cId: String? = null
        val fPath = mutableListOf<String>()
        var found = false
        for (c in collections) {
            if (findPathRecursive(c, folderId, fPath)) {
                cName = c.name
                cId = c.id
                found = true
                break
            }
        }
        val pathWithoutNode = if (found && fPath.isNotEmpty()) fPath.dropLast(1) else emptyList()

        val tab = RequestTabState(
            id = folderId,
            name = folderNode.name,
            kind = RequestKind.FOLDER,
            collectionName = cName,
            collectionId = cId,
            folderPath = pathWithoutNode,
        )
        tab.selectedEditorTab = RequestEditorTab.VARIABLES

        folderNode.variableEntries?.let { entries ->
            tab.variables.clear()
            tab.variables.addAll(entries.map { it.toMutableKeyValue() })
        }
        folderNode.headerEntries?.let { entries ->
            tab.headers.clear()
            tab.headers.addAll(entries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret, kind = HeaderKind.USER) })
        } ?: folderNode.userHeaders.forEach { (k, v) ->
            tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
        }
        folderNode.authType?.let { tab.authType = it }
        folderNode.authUsername?.takeIf { it.isNotBlank() }?.let { tab.authUsername = it }
        folderNode.authPassword?.takeIf { it.isNotBlank() }?.let { tab.authPassword = it }
        folderNode.authToken?.takeIf { it.isNotBlank() }?.let { tab.authToken = it }
        folderNode.authApiKey?.takeIf { it.isNotBlank() }?.let { tab.authApiKey = it }
        folderNode.authApiValue?.takeIf { it.isNotBlank() }?.let { tab.authApiValue = it }
        tab.authApiPlacement = normalizeApiKeyPlacement(folderNode.authApiPlacement)
        folderNode.authOAuth2Flow.let { tab.authOAuth2Flow = it }
        folderNode.authOAuth2AccessTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AccessTokenUrl = it }
        folderNode.authOAuth2RefreshTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RefreshTokenUrl = it }
        folderNode.authOAuth2AuthorizationUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AuthorizationUrl = it }
        folderNode.authOAuth2RedirectUri?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RedirectUri = it }
        folderNode.authOAuth2Scope?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Scope = it }
        folderNode.authOAuth2ClientId?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientId = it }
        folderNode.authOAuth2ClientSecret?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientSecret = it }
        folderNode.authOAuth2CredentialsPlacement.let { tab.authOAuth2CredentialsPlacement = it }
        folderNode.authOAuth2Username?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Username = it }
        folderNode.authOAuth2Password?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Password = it }
        folderNode.preRequestScript?.takeIf { it.isNotBlank() }?.let { tab.preRequestScript = it }
        folderNode.testScript?.takeIf { it.isNotBlank() }?.let { tab.testScript = it }

        applyInheritedDefaults(tab)
        tab.restoreSavedSnapshot(null)
        openTabs.add(tab)
        activeTabIndex = openTabs.size - 1
        selectedCollectionId = folderId
    }

    fun collectionProtobufSnapshot(): String {
        val protos = collectionProtoFiles.joinToString(";") { "${it.path}:${it.syncToGit}" }
        val imports = collectionImportPaths.joinToString(";") { "${it.path}:${it.enabled}:${it.syncToGit}" }
        return "$protos#$imports"
    }

    /**
     * Opens or focuses a dedicated settings tab for the top-level collection.
     */
    fun openCollection(collectionId: String? = null) {
        val target = if (collectionId != null) {
            collections.firstOrNull { it.id == collectionId }
        } else {
            collections.firstOrNull { it.isCollection } ?: collections.firstOrNull()
        }

        if (target == null) {
            val existingIdx = openTabs.indexOfFirst { it.kind == RequestKind.COLLECTION }
            if (existingIdx >= 0) {
                activeTabIndex = existingIdx
                return
            }
            val fallbackTab = RequestTabState(
                id = "collection_settings",
                name = "Collection",
                kind = RequestKind.COLLECTION,
            )
            fallbackTab.collectionSnapshotProvider = { collectionProtobufSnapshot() }
            fallbackTab.markSaved()
            openTabs.add(fallbackTab)
            activeTabIndex = openTabs.size - 1
            return
        }

        selectedCollectionId = target.id
        val existingIdx = openTabs.indexOfFirst {
            it.kind == RequestKind.COLLECTION && (it.id == target.id || it.collectionId == target.id || it.id == "collection_${target.id}")
        }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
            return
        }

        val tab = RequestTabState(
            id = target.id,
            name = target.name,
            kind = RequestKind.COLLECTION,
            collectionId = target.id,
            collectionName = target.name,
            collectionSummary = target.summary,
            collectionVersion = target.version,
        )

        // Populate request defaults
        if (target.variableEntries != null) {
            tab.variables.clear()
            tab.variables.addAll(target.variableEntries.map { it.toMutableKeyValue() })
        }
        if (target.headerEntries != null) {
            tab.headers.clear()
            tab.headers.addAll(target.headerEntries.map {
                MutableKeyValue(key = it.key, value = it.value, enabled = it.enabled, secret = it.secret, kind = HeaderKind.USER)
            })
        }
        tab.authType = target.authType ?: AuthType.NONE
        tab.authUsername = target.authUsername ?: ""
        tab.authPassword = target.authPassword ?: ""
        tab.authToken = target.authToken ?: ""
        tab.authApiKey = target.authApiKey ?: ""
        tab.authApiValue = target.authApiValue ?: ""
        tab.authApiPlacement = target.authApiPlacement ?: "header"
        tab.authOAuth2Flow = target.authOAuth2Flow
        tab.authOAuth2AccessTokenUrl = target.authOAuth2AccessTokenUrl ?: ""
        tab.authOAuth2RefreshTokenUrl = target.authOAuth2RefreshTokenUrl ?: ""
        tab.authOAuth2AuthorizationUrl = target.authOAuth2AuthorizationUrl ?: ""
        tab.authOAuth2RedirectUri = target.authOAuth2RedirectUri ?: ""
        tab.authOAuth2Scope = target.authOAuth2Scope ?: ""
        tab.authOAuth2ClientId = target.authOAuth2ClientId ?: ""
        tab.authOAuth2ClientSecret = target.authOAuth2ClientSecret ?: ""
        tab.authOAuth2CredentialsPlacement = target.authOAuth2CredentialsPlacement
        tab.authOAuth2Username = target.authOAuth2Username ?: ""
        tab.authOAuth2Password = target.authOAuth2Password ?: ""
        tab.preRequestScript = target.preRequestScript ?: ""
        tab.testScript = target.testScript ?: ""

        // Populate collection-level protobuf
        if (target.protoFiles.isNotEmpty() || collectionProtoFiles.isEmpty()) {
            collectionProtoFiles.clear()
            collectionProtoFiles.addAll(target.protoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
        }
        if (target.importPaths.isNotEmpty() || collectionImportPaths.isEmpty()) {
            collectionImportPaths.clear()
            collectionImportPaths.addAll(target.importPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })
        }
        tab.collectionProtoFiles.clear()
        tab.collectionProtoFiles.addAll(collectionProtoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
        tab.collectionImportPaths.clear()
        tab.collectionImportPaths.addAll(collectionImportPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })

        // Populate storage settings
        tab.storageType = target.storageType
        tab.gitRemoteUrl = target.gitRemoteUrl
        tab.gitBranch = target.gitBranch

        tab.collectionSnapshotProvider = { collectionProtobufSnapshot() }
        tab.markSaved()
        openTabs.add(tab)
        activeTabIndex = openTabs.size - 1
    }

    fun createNewCollection(initialName: String = "New Collection"): CollectionNode {
        val uniqueName = disambiguateCollectionName(initialName)
        val node = CollectionNode(
            id = generateUuid(),
            name = uniqueName,
            isFolder = true,
            kind = RequestKind.COLLECTION,
            children = mutableStateListOf(),
        )
        collections.add(node)
        notifyCollectionsChanged()
        return node
    }

    fun deleteCollection(collectionId: String): CollectionNode? {
        val index = collections.indexOfFirst { it.id == collectionId }
        if (index < 0) return null
        val removed = collections.removeAt(index)
        fun collectChildIds(node: CollectionNode): List<String> {
            val ids = mutableListOf(node.id, "collection_${node.id}")
            node.children.forEach { ids.addAll(collectChildIds(it)) }
            return ids
        }
        closeTabsByIds(collectChildIds(removed))
        notifyCollectionsChanged()
        return removed
    }

    fun disambiguateCollectionName(name: String, excludeCollectionId: String? = null): String {
        val existingNames = collections
            .filter { it.id != excludeCollectionId }
            .map { it.name }
            .toSet()
        return ImportExportNaming.generateUniqueCollectionName(name, existingNames)
    }

    fun renameCollection(collectionId: String, desiredName: String): String {
        val trimmed = desiredName.trim()
        val disambiguated = disambiguateCollectionName(trimmed, collectionId)
        val idx = collections.indexOfFirst { it.id == collectionId }
        if (idx >= 0) {
            val oldName = collections[idx].name
            collections[idx] = collections[idx].copy(name = disambiguated)
            openTabs.filter { (it.id == collectionId || it.collectionId == collectionId) && it.isCollectionTab }.forEach { tab ->
                tab.name = disambiguated
                tab.collectionName = disambiguated
                tab.reanchorSavedSnapshotAfterRename(oldName)
            }
            updateTabsCollectionName(oldName, disambiguated)
            notifyCollectionsChanged()
        }
        return disambiguated
    }

    fun getAncestorFolders(tab: RequestTabState): List<CollectionNode> {
        if (tab.isCollectionTab) return emptyList()
        val chain = mutableListOf<CollectionNode>()
        for (root in collections) {
            if (findAncestorChain(root, tab.id, chain)) {
                return chain
            }
        }
        if (tab.collectionId != null || tab.collectionName != null) {
            val root = collections.firstOrNull { it.id == tab.collectionId }
                ?: collections.firstOrNull { it.name == tab.collectionName }
            if (root != null) {
                val fallbackChain = mutableListOf<CollectionNode>()
                fallbackChain.add(root)
                var cursor: CollectionNode = root
                for (folderName in tab.folderPath) {
                    val next = cursor.children.firstOrNull { it.isFolder && it.name == folderName } ?: break
                    fallbackChain.add(next)
                    cursor = next
                }
                return if (tab.isFolderTab && fallbackChain.lastOrNull()?.id == tab.id) {
                    fallbackChain.dropLast(1)
                } else {
                    fallbackChain
                }
            }
        }
        return emptyList()
    }

    private fun findAncestorChain(
        node: CollectionNode,
        targetId: String,
        currentChain: MutableList<CollectionNode>,
    ): Boolean {
        if (node.id == targetId) {
            return true
        }
        if (node.isFolder) {
            currentChain.add(node)
            for (child in node.children) {
                if (findAncestorChain(child, targetId, currentChain)) {
                    return true
                }
            }
            currentChain.removeAt(currentChain.size - 1)
        }
        return false
    }

    fun resolveInheritedAuth(tab: RequestTabState): ResolvedFolderAuth? {
        val ancestors = getAncestorFolders(tab).asReversed()
        if (ancestors.isEmpty() && tab.collectionName == null && tab.collectionId == null) {
            return null
        }
        for (folder in ancestors) {
            val type = folder.authType
            if (type != null && type != AuthType.NONE && type != AuthType.INHERIT) {
                return ResolvedFolderAuth(
                    folderName = folder.name,
                    authType = type,
                    username = folder.authUsername,
                    password = folder.authPassword,
                    token = folder.authToken,
                    apiKey = folder.authApiKey,
                    apiValue = folder.authApiValue,
                    apiPlacement = folder.authApiPlacement,
                    oauth2Flow = folder.authOAuth2Flow,
                    oauth2AccessTokenUrl = folder.authOAuth2AccessTokenUrl,
                    oauth2RefreshTokenUrl = folder.authOAuth2RefreshTokenUrl,
                    oauth2AuthorizationUrl = folder.authOAuth2AuthorizationUrl,
                    oauth2RedirectUri = folder.authOAuth2RedirectUri,
                    oauth2Scope = folder.authOAuth2Scope,
                    oauth2ClientId = folder.authOAuth2ClientId,
                    oauth2ClientSecret = folder.authOAuth2ClientSecret,
                    oauth2CredentialsPlacement = folder.authOAuth2CredentialsPlacement,
                    oauth2Username = folder.authOAuth2Username,
                    oauth2Password = folder.authOAuth2Password,
                )
            }
        }
        val directParentName = ancestors.firstOrNull()?.name ?: tab.collectionName.orEmpty()
        return ResolvedFolderAuth(
            folderName = directParentName,
            authType = AuthType.NONE,
        )
    }

    fun applyInheritedDefaults(tab: RequestTabState) {
        if (tab.isCollectionTab) return
        val ancestors = getAncestorFolders(tab)
        if (ancestors.isEmpty()) {
            tab.headers.removeAll { it.kind == HeaderKind.INHERITED }
            tab.variables.removeAll { it.kind == HeaderKind.INHERITED }
            tab.params.removeAll { it.kind == HeaderKind.INHERITED }
            return
        }

        // 1. Cascade headers (root-to-leaf: closer ancestor overrides distant ancestor)
        val resolvedHeaders = mutableMapOf<String, KeyValueEntry>()
        for (ancestor in ancestors) {
            val entries = ancestor.headerEntries ?: ancestor.userHeaders.map { KeyValueEntry(it.first, it.second) }
            for (entry in entries) {
                if (entry.enabled && entry.key.isNotBlank()) {
                    resolvedHeaders[entry.key.lowercase()] = entry
                }
            }
        }

        tab.headers.removeAll { it.kind == HeaderKind.INHERITED }

        for (entry in resolvedHeaders.values) {
            val alreadyExists = tab.headers.any { it.key.equals(entry.key, ignoreCase = true) }
            if (!alreadyExists) {
                tab.headers.add(
                    MutableKeyValue(
                        key = entry.key,
                        value = entry.value,
                        enabled = true,
                        kind = HeaderKind.INHERITED,
                        keyLocked = true,
                    )
                )
            }
        }

        // 2. Cascade variables (root-to-leaf: closer ancestor overrides distant ancestor)
        val resolvedVariables = mutableMapOf<String, KeyValueEntry>()
        for (ancestor in ancestors) {
            val entries = ancestor.variableEntries.orEmpty()
            for (entry in entries) {
                if (entry.enabled && entry.key.isNotBlank()) {
                    resolvedVariables[entry.key.lowercase()] = entry
                }
            }
        }

        tab.variables.removeAll { it.kind == HeaderKind.INHERITED }

        for (entry in resolvedVariables.values) {
            val alreadyExists = tab.variables.any { it.key.equals(entry.key, ignoreCase = true) }
            if (!alreadyExists) {
                tab.variables.add(
                    MutableKeyValue(
                        key = entry.key,
                        value = entry.value,
                        enabled = true,
                        secret = entry.secret,
                        kind = HeaderKind.INHERITED,
                        keyLocked = true,
                        isDynamic = entry.isDynamic,
                        dynamicScript = entry.dynamicScript,
                        dynamicParameters = entry.dynamicParameters,
                        description = entry.description,
                    )
                )
            }
        }

        // Folder settings no longer define query parameters; ensure inherited query params are cleared.
        tab.params.removeAll { it.kind == HeaderKind.INHERITED }
    }

    fun refreshInheritedDefaultsForOpenTabs() {
        for (openTab in openTabs) {
            if (!openTab.isCollectionTab) {
                applyInheritedDefaults(openTab)
            }
        }
    }

    private fun findPathRecursive(node: CollectionNode, targetId: String, path: MutableList<String>): Boolean {
        if (node.id == targetId) return true
        if (node.isFolder) {
            path.add(node.name)
            for (child in node.children) {
                if (findPathRecursive(child, targetId, path)) return true
            }
            path.removeAt(path.size - 1)
        }
        return false
    }

    private fun findNodeById(nodes: List<CollectionNode>, id: String): CollectionNode? {
        for (node in nodes) {
            if (node.id == id) return node
            if (node.isFolder) findNodeById(node.children, id)?.let { return it }
        }
        return null
    }

    /** Folder anywhere in the tree (root or nested). */
    fun findFolder(id: String): CollectionNode? =
        findNodeById(collections, id)?.takeIf { it.isFolder }

    private fun rootCollectionIdContaining(nodeId: String): String? {
        for (root in collections) {
            if (root.id == nodeId) return root.id
            if (findNodeById(listOf(root), nodeId) != null) return root.id
        }
        return null
    }

    private fun findRequestBySignature(
        nodes: List<CollectionNode>,
        name: String,
        method: HttpMethodType,
        url: String,
    ): CollectionNode? {
        for (node in nodes) {
            if (!node.isFolder && node.name == name && node.method == method && node.url == url) return node
            if (node.isFolder) findRequestBySignature(node.children, name, method, url)?.let { return it }
        }
        return null
    }

    private fun findRequestBySignatureInSubtree(node: CollectionNode, tab: RequestTabState): CollectionNode? {
        node.children.forEach { child ->
            if (!child.isFolder && child.name == tab.name && child.method == tab.method && child.url == tab.url) {
                return child
            }
            if (child.isFolder) {
                findRequestBySignatureInSubtree(child, tab)?.let { return it }
            }
        }
        return null
    }

    private fun findRequestByNameInSubtree(node: CollectionNode, tab: RequestTabState): CollectionNode? {
        node.children.forEach { child ->
            if (!child.isFolder && child.name == tab.name) return child
            if (child.isFolder) {
                findRequestByNameInSubtree(child, tab)?.let { return it }
            }
        }
        return null
    }

    private fun resolveFolderPathScope(root: CollectionNode, folderPath: List<String>): CollectionNode {
        var cursor = root
        for (folderName in folderPath) {
            val next = cursor.children.firstOrNull { it.isFolder && it.name == folderName } ?: break
            cursor = next
        }
        return cursor
    }

    private fun resolveSidebarRequestId(requestId: String): String {
        val tab = openTabs.firstOrNull { it.id == requestId }
        if (tab == null) {
            if (findNodeById(collections, requestId) != null) return requestId
            return requestId
        }

        val references = buildRequestReferenceMap()
        val byRequestRef = tab.requestRef?.let { ref ->
            references.values.firstOrNull { it.requestRef == ref }
        }
        if (byRequestRef != null) return byRequestRef.requestId

        if (references.containsKey(requestId) && tabMatchesRequestScope(tab, requestId, references)) {
            return requestId
        }

        val collectionId = tab.collectionId
        if (collectionId != null) {
            val root = collections.firstOrNull { it.isFolder && it.id == collectionId }
            if (root != null) {
                val scope = resolveFolderPathScope(root, tab.folderPath)
                val candidate = findRequestBySignatureInSubtree(scope, tab)
                    ?: findRequestByNameInSubtree(scope, tab)
                    ?: findRequestBySignatureInSubtree(root, tab)
                    ?: findRequestByNameInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
        }

        val collectionName = tab.collectionName
        if (collectionName != null) {
            val roots = collections.filter { it.isFolder && it.name == collectionName }
            val candidateFolders = mutableListOf<CollectionNode>()
            roots.forEach { root ->
                candidateFolders.add(resolveFolderPathScope(root, tab.folderPath))
            }

            candidateFolders.forEach { folder ->
                val candidate = findRequestBySignatureInSubtree(folder, tab)
                if (candidate != null) return candidate.id
            }
            candidateFolders.forEach { folder ->
                val candidate = findRequestByNameInSubtree(folder, tab)
                if (candidate != null) return candidate.id
            }

            roots.forEach { root ->
                val candidate = findRequestBySignatureInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
            roots.forEach { root ->
                val candidate = findRequestByNameInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
        }

        val bySignature = findRequestBySignature(
            nodes = collections,
            name = tab.name,
            method = tab.method,
            url = tab.url,
        )
        if (bySignature != null) return bySignature.id

        if (references.containsKey(requestId)) return requestId

        return requestId
    }

    private fun tabMatchesRequestScope(
        tab: RequestTabState,
        requestId: String,
        references: Map<String, RequestReference>,
    ): Boolean {
        val reference = references[requestId] ?: return false
        tab.requestRef?.let { if (reference.requestRef != it) return false }

        // Collection ID is the most reliable discriminator: use it exclusively when available.
        // We intentionally do NOT also check collectionName here — names can be stale after
        // a collection rename between sessions while the ID stays correct.
        val tabCollectionId = tab.collectionId
        if (tabCollectionId != null) {
            return tabCollectionId == reference.collectionId
        }

        // No collection ID available — fall back to name + URL to prevent a stale tab id from
        // matching a same-named request that has a different URL (classic restored-tab problem).
        val tabCollectionName = tab.collectionName
        if (tabCollectionName != null) {
            val referenceCollectionName = reference.collectionId?.let { id ->
                collections.firstOrNull { it.isFolder && it.id == id }?.name
            }
            if (referenceCollectionName != null && referenceCollectionName != tabCollectionName) return false
        }
        if (tab.folderPath.isNotEmpty() && !pathStartsWith(reference.folderPath, tab.folderPath)) return false
        // URL match: guard against stale ids in same-named collections.
        if (reference.url.isNotEmpty() && reference.url != tab.url) return false
        return true
    }

    private fun pathStartsWith(path: List<String>, prefix: List<String>): Boolean {
        if (prefix.size > path.size) return false
        prefix.indices.forEach { index ->
            if (path[index] != prefix[index]) return false
        }
        return true
    }

    fun closeTab(index: Int) {
        if (index !in openTabs.indices) return
        val tab = openTabs[index]
        tab.currentJob?.cancel()
        tab.currentJob = null
        tab.isLoading = false
        tab.disposeBodyViewModels()
        disposeMcpSession(tab.id)
        if (tab.kind == RequestKind.WEBSOCKET) {
            WebSocketClientManager.default.disconnect(tab.id)
        }
        openTabs.removeAt(index)
        if (openTabs.isEmpty()) {
            activeTabIndex = -1
        } else if (activeTabIndex >= openTabs.size) {
            activeTabIndex = openTabs.size - 1
        }
        selectedRequestId = activeTab?.id
    }

    /**
     * Closes every open tab whose id is in [ids].
     * Safe to call with ids that have no matching open tab (silently ignored).
     * Adjusts [activeTabIndex] correctly even when multiple tabs are removed.
     */
    fun closeTabsByIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toHashSet()
        // Remove in reverse-index order to keep earlier indices valid.
        openTabs.indices.reversed()
            .filter { openTabs[it].id in idSet }
            .forEach { idx ->
                val tab = openTabs[idx]
                tab.currentJob?.cancel()
                tab.currentJob = null
                tab.isLoading = false
                tab.disposeBodyViewModels()
                disposeMcpSession(tab.id)
                if (tab.kind == RequestKind.WEBSOCKET) {
                    WebSocketClientManager.default.disconnect(tab.id)
                }
                openTabs.removeAt(idx)
            }
        if (openTabs.isEmpty()) {
            activeTabIndex = -1
        } else if (activeTabIndex >= openTabs.size) {
            activeTabIndex = openTabs.size - 1
        }
        selectedRequestId = activeTab?.id
    }

    /** Updates [RequestTabState.collectionName] for every open tab that still has [oldName].
     *  Called after a root collection is renamed so [resolveSidebarRequestId] keeps working. */
    fun updateTabsCollectionName(oldName: String, newName: String) {
        openTabs.forEach { tab ->
            if (tab.collectionName == oldName) tab.collectionName = newName
        }
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in openTabs.indices || toIndex !in openTabs.indices || fromIndex == toIndex) return
        val tab = openTabs.removeAt(fromIndex)
        openTabs.add(toIndex, tab)
        activeTabIndex = toIndex
        selectedRequestId = tab.id
    }

    /**
     * Focuses / activates the next open tab, or wraps around to the first tab if
     * the current tab is the last one.
     */
    fun selectNextTab() {
        if (openTabs.isEmpty()) return
        val nextIndex = if (activeTabIndex + 1 < openTabs.size) activeTabIndex + 1 else 0
        activeTabIndex = nextIndex
        selectedRequestId = activeTab?.id
    }

    private val appLogger by lazy { AppLogging.logger("App") }
    private val networkLogger by lazy { AppLogging.logger("Network") }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        when (level) {
            LogLevel.ERROR -> appLogger.error { message }
            LogLevel.WARNING -> appLogger.warn { message }
            LogLevel.INFO, LogLevel.SUCCESS -> appLogger.info { message }
        }
        diagnostics.log(message, level)
    }

    /**
     * Appends a network-level event to the structured Logs tab.
     * REST traffic also echoes to Console ([echoToConsole] default true). MCP
     * summaries pass false so Console stays for scripts and app messages.
     */
    fun logNetworkEvent(message: String, level: LogLevel = LogLevel.INFO, echoToConsole: Boolean = true) {
        when (level) {
            LogLevel.ERROR -> networkLogger.error { message }
            LogLevel.WARNING -> networkLogger.warn { message }
            LogLevel.INFO, LogLevel.SUCCESS -> networkLogger.info { message }
        }
        diagnostics.logNetworkEvent(message, level, echoToConsole)
    }

    fun notifyCollectionsChanged() {
        // Bump revision for the autosave snapshotFlow.
        // Children lists are now SnapshotStateList (mutableStateListOf), so Compose
        // automatically observes add/remove on them — no .copy() trick needed.
        ensureRequestRefsInitialized()
        syncOpenTabsWithCollections()
        syncHistoryWithCollections()
        collectionsRevision++
    }

    /** Collapse every folder in the collection tree. */
    fun collapseAllCollections() {
        for (id in allFolderIds(collections)) collectionExpandedState[id] = false
    }

    /** Expand every folder in the collection tree. */
    fun expandAllCollections() {
        for (id in allFolderIds(collections)) collectionExpandedState[id] = true
    }

    private fun allFolderIds(nodes: List<CollectionNode>): List<String> =
        nodes.flatMap { node ->
            if (node.isFolder) listOf(node.id) + allFolderIds(node.children)
            else emptyList()
        }

    /** Add a new request node inside the given collection (folder). Opens it as a tab. */
    fun addRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.GET,
            url = "",
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.GET, url = "")
    }

    /** Add a new gRPC request node inside the given collection (folder). Opens it as a tab. */
    fun addGrpcRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = "",
            kind = RequestKind.GRPC,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = "")
    }

    /** Add a new WebSocket request node inside the given collection (folder). Opens it as a tab. */
    fun addWebSocketRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.GET,
            url = "ws://",
            kind = RequestKind.WEBSOCKET,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.GET, url = "ws://")
    }

    /** Add a new GraphQL request node inside the given collection (folder). Opens it as a tab. */
    fun addGraphQLRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://",
            kind = RequestKind.GRAPHQL,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = "https://")
    }

    /** Add a new RabbitMQ request node inside the given collection (folder). Opens it as a tab. */
    fun addRabbitMqRequestToCollection(
        collectionId: String,
        mode: io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode =
            io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode.PUBLISH,
    ) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val baseName = when (mode) {
            io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode.PUBLISH -> "New RabbitMQ Publish"
            io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode.SUBSCRIBE -> "New RabbitMQ Subscribe"
            io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode.RPC -> "New RabbitMQ RPC"
        }
        val name = generateUniqueName(baseName, siblingNames)
        val requestId = generateUuid()
        val defaultDetails = io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails(
            mode = mode,
            connection = io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig(
                host = "localhost",
                port = 5672,
                vhost = "/",
            ),
            auth = io.github.makeforge.clapi.core.model.opencollection.AuthInherit,
        )
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = "amqp://localhost:5672/",
            authType = AuthType.INHERIT,
            kind = RequestKind.RABBITMQ,
            rabbitMqDetails = defaultDetails,
            rabbitMqSettings = io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings(),
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = "amqp://localhost:5672/")
    }

    fun addMcpConnectionToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New MCP Connection", siblingNames)
        val requestId = generateUuid()
        val mcp = McpConnectionConfig(url = "{{mcpBaseUrl}}")
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = mcp.url,
            kind = RequestKind.MCP,
            mcpConfig = mcp,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = mcp.url)
    }

    fun addSseRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New SSE Request", siblingNames)
        val requestId = generateUuid()
        val url = "{{baseUrl}}/sse"
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.GET,
            url = url,
            userHeaders = listOf(SystemHeaderRules.ACCEPT to SSE_ACCEPT_MEDIA_TYPE),
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.GET, url = url)
    }

    /** Create a request in the selected collection, ensuring a default collection exists when needed. */
    fun addTabInSelectedCollection() {
        val collId = selectedCollectionId
        val folder = if (collId != null) findFolder(collId) else null
        if (folder != null) {
            addRequestToCollection(folder.id)
        } else {
            val firstFolder = collections.firstOrNull { it.isFolder }
            if (firstFolder != null) {
                addRequestToCollection(firstFolder.id)
            } else {
                val defaultFolder = ensureDefaultCollectionFolder()
                addRequestToCollection(defaultFolder.id)
            }
        }
    }

    fun pruneEmptyGlobalVariables() {
        globalVariables.removeAll { it.key.isBlank() && it.value.isBlank() && !it.isDynamic && it.dynamicScript.isBlank() }
    }

    fun pruneEmptyVariablesForEnvironment(index: Int) {
        val env = environments.getOrNull(index) ?: return
        env.variables.removeAll { it.key.isBlank() && it.value.isBlank() && !it.isDynamic && it.dynamicScript.isBlank() }
    }

    fun renameRequestEverywhere(requestId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        openTabs.filter { it.id == requestId }.forEach { tab ->
            val oldName = tab.name
            tab.name = trimmed
            // Re-anchor the name component of savedSnapshot so that dirty tracking
            // reflects only non-name unsaved changes after a rename from the sidebar
            // or tab chip. Without this, reverting a URL edit after a rename always
            // appears dirty because savedSnapshot still contains the old name.
            tab.reanchorSavedSnapshotAfterRename(oldName)
        }
        renameRequestNodeById(collections, requestId, trimmed)
        notifyCollectionsChanged()
    }

    fun revealRequestInSidebar(requestId: String): Boolean {
        val resolvedRequestId = resolveSidebarRequestId(requestId)
        val targetNode = findNodeById(collections, resolvedRequestId)
        if (targetNode == null) {
            showError("Item not found", "Item no longer exists in collections.")
            return false
        }
        sidebarExpanded = true
        sidebarSearchQuery = ""

        if (targetNode.isFolder) {
            selectedCollectionId = targetNode.id
        } else {
            selectedRequestId = resolvedRequestId
        }

        // Also switch the active tab to this request so the sidebar
        // selection stays in sync with the tab bar (the LaunchedEffect
        // in MainScreen sets selectedRequestId = activeTab?.id).
        val tabIdx = openTabs.indexOfFirst { it.id == requestId || it.id == resolvedRequestId }
        if (tabIdx >= 0) activeTabIndex = tabIdx

        if (expandAncestorsForRequest(collections, resolvedRequestId)) {
            // Clear first, then set, so duplicate-name tab switches can retrigger
            // scroll even when the same request id is emitted consecutively.
            sidebarScrollToRequestId = null
            sidebarScrollToRequestId = resolvedRequestId
        }
        return true
    }

    fun recordHistory(
        requestId: String,
        method: HttpMethodType,
        name: String,
        url: String,
        aliasRequestIds: Set<String> = emptySet(),
    ) {
        val references = buildRequestReferenceMap()
        val resolvedRequestId = resolveSidebarRequestId(requestId)
        val reference = references[resolvedRequestId]
            ?: references.values.firstOrNull { it.name == name && it.method == method && it.url == url }
            ?: return

        val idsToReplace = (aliasRequestIds + requestId + resolvedRequestId + reference.requestId)
            .filter { it.isNotBlank() }
            .toSet()
        if (idsToReplace.isNotEmpty()) {
            historyItems.removeAll { it.requestId in idsToReplace }
        }
        val item = HistoryItem(
            requestId = reference.requestId,
            method = reference.method,
            name = reference.name,
            url = reference.url,
            timestamp = currentTimeMillis(),
            collectionId = reference.collectionId,
            folderPath = reference.folderPath,
        )
        if (historyItems.size >= DiagnosticsLogState.MAX_HISTORY_ITEMS) {
            historyItems.removeRange(DiagnosticsLogState.MAX_HISTORY_ITEMS - 1, historyItems.size)
        }
        historyItems.add(0, item)
        historyRevision++
    }

    fun openHistoryItem(item: HistoryItem) {
        val resolvedRequestId = resolveHistoryRequestId(item)
        val target = findNodeById(collections, resolvedRequestId)
        if (target != null && !target.isFolder) {
            openRequest(requestId = resolvedRequestId, name = target.name, method = target.method ?: item.method, url = target.url ?: item.url)
            selectedRequestId = resolvedRequestId
        } else {
            addTab(
                requestId = generateUuid(),
                name = item.name,
                method = item.method,
                url = item.url,
            )
            showError("Request not found", "Request no longer exists in collections. Opened a temporary tab.")
        }
    }

    private fun resolveHistoryRequestId(item: HistoryItem): String {
        if (findNodeById(collections, item.requestId) != null) return item.requestId
        val bySignature = findRequestBySignature(
            nodes = collections,
            name = item.name,
            method = item.method,
            url = item.url,
        )
        return bySignature?.id ?: item.requestId
    }

    fun goToCollectionFromHistory(item: HistoryItem): Boolean {
        val resolvedRequestId = resolveHistoryRequestId(item)
        val revealed = revealRequestInSidebar(resolvedRequestId)
        if (!revealed) return false

        if (openTabs.none { it.id == resolvedRequestId }) {
            val target = findNodeById(collections, resolvedRequestId)
            if (target != null && !target.isFolder) {
                openRequest(
                    requestId = target.id,
                    name = target.name,
                    method = target.method ?: item.method,
                    url = target.url ?: item.url,
                )
            }
        }
        return true
    }

    fun clearHistory() {
        if (historyItems.isEmpty()) return
        historyItems.clear()
        historyRevision++
    }

    /**
     * Resolves a script-provided next-request token to an existing request
     * across collections.
     *
     * Matching order:
     * 1) exact request id
     * 2) exact requestRef
     * 3) exact request name (preferring [preferredCollectionId] when provided)
     */
    fun resolveScriptRequestTarget(token: String, preferredCollectionId: String? = null): ScriptRequestTarget? {
        val normalized = token.trim()
        if (normalized.isEmpty()) return null
        val refs = buildRequestReferenceMap().values.toList()

        refs.firstOrNull { it.requestId == normalized }?.let {
            return ScriptRequestTarget(it.requestId, it.name, it.method, it.url)
        }
        refs.firstOrNull { it.requestRef == normalized }?.let {
            return ScriptRequestTarget(it.requestId, it.name, it.method, it.url)
        }

        val byName = refs.filter { it.name == normalized }
        if (byName.isEmpty()) return null

        val preferred = preferredCollectionId?.let { cid -> byName.filter { it.collectionId == cid } }.orEmpty()
        val winner = when {
            preferred.size == 1 -> preferred.first()
            byName.size == 1 -> byName.first()
            else -> null
        } ?: return null

        return ScriptRequestTarget(winner.requestId, winner.name, winner.method, winner.url)
    }

    fun removeHistoryItem(requestId: String) {
        val removed = historyItems.removeAll { it.requestId == requestId }
        if (removed) {
            historyRevision++
        }
    }

    fun replaceHistoryItems(items: List<HistoryItem>) {
        historyItems.clear()
        historyItems.addAll(items)
        historyRevision++
    }

    /**
     * Syncs the sidebar to the current active tab: sets [selectedRequestId],
     * expands ancestor folders, and sets the scroll target.
     * Called on startup after tab + workspace restoration, and whenever the
     * active tab changes.
     */
    fun syncSidebarToActiveTab() {
        val tab = activeTab ?: return
        val resolvedRequestId = resolveSidebarRequestId(tab.id)
        val wasAlreadySelected = selectedRequestId == resolvedRequestId
        selectedRequestId = resolvedRequestId
        val rootColId = if (tab.isCollectionTab) {
            tab.collectionId ?: tab.id
        } else {
            tab.collectionId ?: rootCollectionIdContaining(resolvedRequestId)
        }
        if (rootColId != null) {
            selectedCollectionId = rootColId
        }
        if (!wasAlreadySelected && expandAncestorsForRequest(collections, resolvedRequestId)) {
            // Reset signal first so repeated targets still trigger row scrolling.
            sidebarScrollToRequestId = null
            sidebarScrollToRequestId = resolvedRequestId
        }
    }

    private fun buildRequestReferenceMap(): Map<String, RequestReference> {
        val references = linkedMapOf<String, RequestReference>()
        collectRequestReferences(
            nodes = collections,
            currentCollectionId = null,
            currentFolderPath = emptyList(),
            target = references,
        )
        return references
    }

    private fun collectRequestReferences(
        nodes: List<CollectionNode>,
        currentCollectionId: String?,
        currentFolderPath: List<String>,
        target: MutableMap<String, RequestReference>,
    ) {
        for (node in nodes) {
            if (node.isFolder) {
                val nextCollectionId = currentCollectionId ?: node.id
                val nextFolderPath = if (currentCollectionId == null) {
                    currentFolderPath
                } else {
                    currentFolderPath + node.name
                }
                collectRequestReferences(node.children, nextCollectionId, nextFolderPath, target)
            } else {
                val method = node.method ?: continue
                val url = node.url ?: continue
                val requestRef = node.requestRef ?: node.id
                target[node.id] = RequestReference(
                    requestId = node.id,
                    requestRef = requestRef,
                    collectionId = currentCollectionId,
                    folderPath = currentFolderPath,
                    name = node.name,
                    method = method,
                    url = url,
                )
            }
        }
    }

    internal fun syncHistoryWithCollections() {
        val references = buildRequestReferenceMap()
        var changed = false
        val synced = historyItems.mapNotNull { item ->
            val ref = references[item.requestId] ?: run {
                changed = true
                return@mapNotNull null
            }
            val updated = item.copy(
                method = ref.method,
                name = ref.name,
                url = ref.url,
                collectionId = ref.collectionId,
                folderPath = ref.folderPath,
            )
            if (updated != item) changed = true
            updated
        }
        if (changed) {
            historyItems.clear()
            historyItems.addAll(synced)
            historyRevision++
        }
    }

    private fun syncOpenTabsWithCollections() {
        val references = buildRequestReferenceMap()
        val rootNamesById = collections
            .filter { it.isFolder }
            .associate { it.id to it.name }

        openTabs.forEach { tab ->
            val reference = tab.requestRef?.let { ref ->
                references.values.firstOrNull { it.requestRef == ref }
            } ?: references[tab.id] ?: return@forEach
            tab.requestRef = reference.requestRef
            tab.collectionId = reference.collectionId
            tab.collectionName = reference.collectionId?.let { rootNamesById[it] }
            tab.folderPath = reference.folderPath
        }
    }

    internal fun ensureRequestRefsInitialized() {
        ensureRequestRefsInitializedRecursive(collections)
    }

    private fun ensureRequestRefsInitializedRecursive(nodes: MutableList<CollectionNode>) {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if (node.isFolder) {
                if (node.children.isNotEmpty()) ensureRequestRefsInitializedRecursive(node.children)
            } else if (node.requestRef == null) {
                nodes[index] = node.copy(requestRef = generateUuid())
            }
        }
    }

    private fun renameRequestNodeById(nodes: MutableList<CollectionNode>, requestId: String, newName: String): Boolean {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if (node.id == requestId) {
                nodes[index] = node.copy(name = newName)
                return true
            }
            if (node.isFolder && node.children.isNotEmpty()) {
                if (renameRequestNodeById(node.children, requestId, newName)) return true
            }
        }
        return false
    }

    /**
     * Writes the current tab editing state back into the matching [CollectionNode] in the
     * collections tree so that workspace export reflects the latest saved content.
     *
     * Must be called from the main thread (it mutates Compose snapshot state).
     * Returns `true` if the node was found and updated.
     */
    fun syncTabToCollectionNode(tab: RequestTabState): Boolean {
        val updated = syncTabToNodeRecursive(collections, tab)
        if (updated) {
            notifyCollectionsChanged()
            if (tab.isFolderTab || tab.isCollectionTab) {
                refreshInheritedDefaultsForOpenTabs()
            }
        }
        return updated
    }

    private fun syncTabToNodeRecursive(
        nodes: MutableList<CollectionNode>,
        tab: RequestTabState,
    ): Boolean {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if ((node.isFolder || node.isCollection) && (node.id == tab.id || (tab.isCollectionTab && (node.id == tab.collectionId || tab.id == "collection_${node.id}")))) {
                val variableEntriesSnapshot = tab.variables
                    .filter { it.kind != HeaderKind.INHERITED }
                    .map { it.toKeyValueEntry() }
                val headerEntriesSnapshot = tab.headers
                    .filter { it.kind != HeaderKind.SYSTEM && it.kind != HeaderKind.INHERITED }
                    .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
                val userHeadersSnapshot = tab.headers
                    .filter { it.kind == HeaderKind.USER && it.enabled }
                    .map { it.key to it.value }
                val oldName = node.name
                val finalName = if (tab.isCollectionTab && tab.name != oldName) {
                    disambiguateCollectionName(tab.name.trim(), node.id)
                } else {
                    tab.name
                }
                if (tab.isCollectionTab && finalName != tab.name) {
                    tab.name = finalName
                    tab.collectionName = finalName
                }
                if (tab.isCollectionTab && finalName != oldName) {
                    updateTabsCollectionName(oldName, finalName)
                    tab.reanchorSavedSnapshotAfterRename(oldName)
                }
                val updatedStorageExt = if (tab.isCollectionTab) {
                    if (tab.storageType == StorageType.GIT) {
                        StorageExtension(
                            type = StorageType.GIT,
                            git = GitStorageConfig(
                                remoteUrl = tab.gitRemoteUrl.trim(),
                                branch = tab.gitBranch.trim().ifBlank { "main" },
                                credentials = GitCredentialsConfig(type = "system"),
                            ),
                        )
                    } else {
                        StorageExtension(type = StorageType.LOCAL, git = null)
                    }
                } else {
                    node.storageExtension
                }
                val effectiveProtoFiles = if (tab.isCollectionTab) {
                    val src = if (collectionProtoFiles.isNotEmpty() || tab.collectionProtoFiles.isEmpty()) collectionProtoFiles else tab.collectionProtoFiles
                    src.map { ProtoFileItemState(it.path, it.syncToGit) }
                } else {
                    node.protoFiles
                }
                val effectiveImportPaths = if (tab.isCollectionTab) {
                    val src = if (collectionImportPaths.isNotEmpty() || tab.collectionImportPaths.isEmpty()) collectionImportPaths else tab.collectionImportPaths
                    src.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) }
                } else {
                    node.importPaths
                }
                nodes[index] = node.copy(
                    name = finalName,
                    summary = if (tab.isCollectionTab) tab.collectionSummary else node.summary,
                    version = if (tab.isCollectionTab) tab.collectionVersion else node.version,
                    protoFiles = effectiveProtoFiles,
                    importPaths = effectiveImportPaths,
                    storageExtension = updatedStorageExt,
                    variableEntries = variableEntriesSnapshot,
                    queryEntries = null,
                    headerEntries = headerEntriesSnapshot,
                    userHeaders = userHeadersSnapshot,
                    authType = tab.authType.takeIf { it != AuthType.NONE },
                    authUsername = tab.authUsername.ifBlank { null },
                    authPassword = tab.authPassword.ifBlank { null },
                    authToken = tab.authToken.ifBlank { null },
                    authApiKey = tab.authApiKey.ifBlank { null },
                    authApiValue = tab.authApiValue.ifBlank { null },
                    authApiPlacement = normalizeApiKeyPlacement(tab.authApiPlacement),
                    authOAuth2Flow = tab.authOAuth2Flow,
                    authOAuth2AccessTokenUrl = tab.authOAuth2AccessTokenUrl.ifBlank { null },
                    authOAuth2RefreshTokenUrl = tab.authOAuth2RefreshTokenUrl.ifBlank { null },
                    authOAuth2AuthorizationUrl = tab.authOAuth2AuthorizationUrl.ifBlank { null },
                    authOAuth2RedirectUri = tab.authOAuth2RedirectUri.ifBlank { null },
                    authOAuth2Scope = tab.authOAuth2Scope.ifBlank { null },
                    authOAuth2ClientId = tab.authOAuth2ClientId.ifBlank { null },
                    authOAuth2ClientSecret = tab.authOAuth2ClientSecret.ifBlank { null },
                    authOAuth2CredentialsPlacement = tab.authOAuth2CredentialsPlacement,
                    authOAuth2Username = tab.authOAuth2Username.ifBlank { null },
                    authOAuth2Password = tab.authOAuth2Password.ifBlank { null },
                    preRequestScript = tab.preRequestScript.ifBlank { null },
                    testScript = tab.testScript.ifBlank { null },
                )
                if (tab.isCollectionTab) {
                    collectionProtoFiles.clear()
                    collectionProtoFiles.addAll(effectiveProtoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
                    collectionImportPaths.clear()
                    collectionImportPaths.addAll(effectiveImportPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })
                    tab.collectionProtoFiles.clear()
                    tab.collectionProtoFiles.addAll(effectiveProtoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
                    tab.collectionImportPaths.clear()
                    tab.collectionImportPaths.addAll(effectiveImportPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })
                }
                return true
            }
            if (!node.isFolder && (node.id == tab.id || (tab.requestRef != null && node.requestRef == tab.requestRef))) {
                if (tab.id != node.id) {
                    tab.id = node.id
                }
                if (tab.requestRef == null && node.requestRef != null) {
                    tab.requestRef = node.requestRef
                }
                val effectiveHeaderSource = if (tab.kind == RequestKind.GRPC) tab.grpcMetadata else tab.headers
                val userHeadersSnapshot = effectiveHeaderSource
                    .filter { it.kind == HeaderKind.USER && it.enabled }
                    .map { it.key to it.value }
                    .toMutableList()
                val queryEntriesSnapshot = tab.params
                    .filter { it.kind != HeaderKind.INHERITED }
                    .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
                val headerEntriesSnapshot = effectiveHeaderSource
                    .filter { it.kind != HeaderKind.INHERITED }
                    .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
                val sseAccept = tab.headers.firstOrNull { isSseAccept(it.key, it.value, it.enabled) }
                if (sseAccept != null &&
                    userHeadersSnapshot.none { it.first.equals(SystemHeaderRules.ACCEPT, ignoreCase = true) }
                ) {
                    userHeadersSnapshot.add(sseAccept.key to sseAccept.value)
                }
                val bodyContentsSnapshot: Map<String, String> =
                    tab.bodyContents.entries.associate { it.key.name to it.value }
                val formEntriesSnapshot = tab.formRows.map { r ->
                    FormDataEntryState(r.key, r.type, r.value, r.description, r.enabled, r.bytesBase64)
                }
                val urlencodedSnapshot = tab.urlencodedRows.map { r ->
                    FormDataEntryState(r.key, r.type, r.value, r.description, r.enabled)
                }
                nodes[index] = node.copy(
                    requestRef = node.requestRef ?: tab.requestRef ?: generateUuid(),
                    name   = tab.name,
                    method = if (tab.kind == RequestKind.GRAPHQL) {
                        HttpMethodType.entries.firstOrNull { it.name.equals(tab.graphQlMethod, ignoreCase = true) } ?: HttpMethodType.POST
                    } else tab.method,
                    url    = tab.url,
                    bodyType           = if (tab.kind == RequestKind.GRPC) BodyType.JSON else if (tab.kind == RequestKind.GRAPHQL) BodyType.GRAPHQL else tab.bodyType.takeIf { it != BodyType.NONE },
                    bodyContent        = if (tab.kind == RequestKind.GRPC) tab.grpcMessageBody.ifBlank { null } else if (tab.kind == RequestKind.GRAPHQL) tab.effectiveGraphQlQuery.ifBlank { null } else tab.bodyContent.ifBlank { null },
                    bodyContents       = bodyContentsSnapshot,
                    formDataEntries    = formEntriesSnapshot,
                    urlencodedEntries  = urlencodedSnapshot,
                    authType           = tab.authType.takeIf { it != AuthType.NONE },
                    authUsername       = tab.authUsername.ifBlank { null },
                    authPassword       = tab.authPassword.ifBlank { null },
                    authToken          = tab.authToken.ifBlank { null },
                    authApiKey         = tab.authApiKey.ifBlank { null },
                    authApiValue       = tab.authApiValue.ifBlank { null },
                    authApiPlacement   = normalizeApiKeyPlacement(tab.authApiPlacement),
                    authOAuth2Flow     = tab.authOAuth2Flow,
                    authOAuth2AccessTokenUrl = tab.authOAuth2AccessTokenUrl.ifBlank { null },
                    authOAuth2RefreshTokenUrl = tab.authOAuth2RefreshTokenUrl.ifBlank { null },
                    authOAuth2AuthorizationUrl = tab.authOAuth2AuthorizationUrl.ifBlank { null },
                    authOAuth2RedirectUri = tab.authOAuth2RedirectUri.ifBlank { null },
                    authOAuth2Scope    = tab.authOAuth2Scope.ifBlank { null },
                    authOAuth2ClientId = tab.authOAuth2ClientId.ifBlank { null },
                    authOAuth2ClientSecret = tab.authOAuth2ClientSecret.ifBlank { null },
                    authOAuth2CredentialsPlacement = tab.authOAuth2CredentialsPlacement,
                    authOAuth2Username = tab.authOAuth2Username.ifBlank { null },
                    authOAuth2Password = tab.authOAuth2Password.ifBlank { null },
                    userHeaders        = userHeadersSnapshot,
                    queryEntries       = queryEntriesSnapshot,
                    headerEntries      = headerEntriesSnapshot,
                    preRequestScript   = tab.preRequestScript.ifBlank { null },
                    testScript         = tab.testScript.ifBlank { null },
                    kind               = tab.kind,
                    mcpConfig          = tab.mcpConfig.copy(
                        url = tab.url.ifBlank { tab.mcpConfig.url },
                    ),
                    protoFilePath      = tab.protoFilePath?.ifBlank { null },
                    protoSyncToGit     = tab.protoSyncToGit,
                    grpcMethod         = tab.grpcMethod?.ifBlank { null },
                    grpcMethodType     = tab.grpcMethodType.ifBlank { "unary" },
                    webSocketVariants  = if (tab.kind == RequestKind.WEBSOCKET || tab.kind == RequestKind.RABBITMQ) tab.webSocketMessageVariants.toList() else emptyList(),
                    webSocketTimeout   = if (tab.kind == RequestKind.WEBSOCKET) tab.webSocketTimeout else null,
                    webSocketKeepAliveInterval = if (tab.kind == RequestKind.WEBSOCKET) tab.webSocketKeepAliveInterval else null,
                    webSocketDocs      = if (tab.kind == RequestKind.WEBSOCKET) tab.webSocketDocs.ifBlank { null } else null,
                    webSocketMessageFormat = if (tab.kind == RequestKind.WEBSOCKET || tab.kind == RequestKind.RABBITMQ) tab.effectiveWebSocketFormat else null,
                    rabbitMqDetails    = if (tab.kind == RequestKind.RABBITMQ) {
                        io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails(
                            mode = tab.rabbitMqMode,
                            connection = tab.rabbitMqConnection,
                            exchange = tab.rabbitMqExchange,
                            queue = tab.rabbitMqQueue,
                            routingKey = tab.rabbitMqRoutingKey,
                            replyQueue = tab.rabbitMqReplyQueue.ifBlank { null },
                            properties = tab.rabbitMqProperties,
                        )
                    } else null,
                    rabbitMqSettings   = if (tab.kind == RequestKind.RABBITMQ) tab.rabbitMqSettings else null,
                    rabbitMqDocs       = if (tab.kind == RequestKind.RABBITMQ) tab.rabbitMqDocs.ifBlank { null } else null,
                    graphQlVariants    = if (tab.kind == RequestKind.GRAPHQL) tab.graphQlVariants.toList() else emptyList(),
                    graphQlDocs        = if (tab.kind == RequestKind.GRAPHQL) tab.graphQlDocs.ifBlank { null } else null,
                    graphQlSettings    = if (tab.kind == RequestKind.GRAPHQL) {
                        GraphQLRequestSettings(
                            timeout = tab.graphQlTimeout?.let { if (it == "inherit") JsonPrimitive("inherit") else it.toLongOrNull()?.let { l -> JsonPrimitive(l) } ?: JsonPrimitive(it) },
                            encodeUrl = tab.graphQlEncodeUrl?.let { if (it == "inherit") JsonPrimitive("inherit") else it.toBooleanStrictOrNull()?.let { b -> JsonPrimitive(b) } ?: JsonPrimitive(it) },
                            followRedirects = tab.graphQlFollowRedirects?.let { if (it == "inherit") JsonPrimitive("inherit") else it.toBooleanStrictOrNull()?.let { b -> JsonPrimitive(b) } ?: JsonPrimitive(it) },
                            forwardAuthorizationHeader = tab.graphQlForwardAuthHeader?.let { if (it == "inherit") JsonPrimitive("inherit") else it.toBooleanStrictOrNull()?.let { b -> JsonPrimitive(b) } ?: JsonPrimitive(it) },
                            maxRedirects = tab.graphQlMaxRedirects?.let { if (it == "inherit") JsonPrimitive("inherit") else it.toIntOrNull()?.let { i -> JsonPrimitive(i) } ?: JsonPrimitive(it) },
                        )
                    } else null,
                )
                return true
            }
            if ((node.isFolder || node.isCollection) && node.children.isNotEmpty()) {
                if (syncTabToNodeRecursive(node.children, tab)) return true
            }
        }
        return false
    }

    private fun expandAncestorsForRequest(nodes: List<CollectionNode>, requestId: String): Boolean {
        for (node in nodes) {
            if (node.id == requestId) return true
            if ((node.isFolder || node.isCollection) && node.children.isNotEmpty()) {
                if (expandAncestorsForRequest(node.children, requestId)) {
                    collectionExpandedState[node.id] = true
                    return true
                }
            }
        }
        return false
    }

    private fun generateUniqueName(base: String, existingNames: Set<String>): String {
        if (base !in existingNames) return base
        var index = 2
        while ("$base $index" in existingNames) index++
        return "$base $index"
    }

    private fun ensureDefaultCollectionFolder(): CollectionNode {
        collections.firstOrNull { it.isFolder }?.let { return it }

        val existingNames = collections.map { it.name }.toSet()
        val folder = CollectionNode(
            id = generateUuid(),
            name = generateUniqueName("Default Collection", existingNames),
            isFolder = true,
            children = mutableStateListOf(),
        )
        collections.add(folder)
        notifyCollectionsChanged()
        selectedCollectionId = folder.id
        return folder
    }

    /** Open a history / collection request as a new tab. */
    fun openRequest(requestId: String? = null, name: String, method: HttpMethodType, url: String) {
        if (requestId != null) {
            selectedRequestId = requestId
        }
        val existing = if (requestId != null) {
            openTabs.indexOfFirst { it.id == requestId }
        } else {
            openTabs.indexOfFirst { it.url == url && it.method == method }
        }
        if (existing >= 0) {
            activeTabIndex = existing
            selectedRequestId = openTabs[existing].id
        } else {
            addTab(requestId = requestId ?: generateUuid(), name = name, method = method, url = url)
        }
    }

    /** Open the environment edit dialog for the given environment index. */
    fun openEnvEdit(index: Int) {
        dialogs.show(AppDialog.EnvironmentEdit(index))
    }
}
