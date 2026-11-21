package io.github.makeforge.clapi.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.components.BottomPanel
import io.github.makeforge.clapi.ui.shared.components.CollectionEditor
import io.github.makeforge.clapi.ui.shared.components.ConfirmDeleteDialog
import io.github.makeforge.clapi.ui.shared.components.DirtyCloseDialog
import io.github.makeforge.clapi.ui.shared.components.DirtyMultiCloseDialog
import io.github.makeforge.clapi.ui.shared.components.EnvironmentEditDialog
import io.github.makeforge.clapi.ui.shared.components.ErrorMessageDialog
import io.github.makeforge.clapi.ui.shared.components.ExportCollectionsDialog
import io.github.makeforge.clapi.ui.shared.components.FolderEditor
import io.github.makeforge.clapi.ui.shared.components.GitSyncDialog
import io.github.makeforge.clapi.ui.shared.components.GlobalVariablesDialog
import io.github.makeforge.clapi.ui.shared.components.GraphQLPanel
import io.github.makeforge.clapi.ui.shared.components.HelpAboutDialog
import io.github.makeforge.clapi.ui.shared.components.HorizontalSplitPane
import io.github.makeforge.clapi.ui.shared.components.ImportSpecificationDialog
import io.github.makeforge.clapi.ui.shared.components.McpPanel
import io.github.makeforge.clapi.ui.shared.components.McpWorkspaceResponse
import io.github.makeforge.clapi.ui.shared.components.OperationProgressDialog
import io.github.makeforge.clapi.ui.shared.components.RealtimePanel
import io.github.makeforge.clapi.ui.shared.components.RequestEditor
import io.github.makeforge.clapi.ui.shared.components.RequestTabsBar
import io.github.makeforge.clapi.ui.shared.components.ResponseViewer
import io.github.makeforge.clapi.ui.shared.components.SettingsDialog
import io.github.makeforge.clapi.ui.shared.components.SidebarResizeDivider
import io.github.makeforge.clapi.ui.shared.components.VerticalSplitPane
import io.github.makeforge.clapi.ui.shared.components.WebSocketRequestEditor
import io.github.makeforge.clapi.ui.shared.components.executor.saveRequest
import io.github.makeforge.clapi.ui.shared.components.executor.sendRequest
import io.github.makeforge.clapi.ui.shared.components.sidebar.Sidebar
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.ioDispatcher
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.state.AppAlert
import io.github.makeforge.clapi.ui.shared.state.AppDialog
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.ResponseLayout
import io.github.makeforge.clapi.ui.shared.state.WorkspaceMode
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main composable shell shared between Desktop and Web.
 * Contains: toolbar, sidebar, request/response editors, bottom panel, and all dialog overlays.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, FlowPreview::class)
@Composable
fun MainScreen(
    state: AppState = remember { AppState() },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dirtyCloseTabIndex by remember { mutableStateOf<Int?>(null) }
    var multiDirtyIdsToClose by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastActiveTabId by remember(state) { mutableStateOf(state.activeTab?.id) }

    DisposableEffect(state) {
        onDispose { state.dispose() }
    }

    val forceCloseByIds: (List<String>) -> Unit = { ids ->
        ids
            .mapNotNull { id -> state.openTabs.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            .sortedDescending()
            .forEach { idx -> state.closeTab(idx) }
    }
    val closeManyTabs: (List<String>) -> Unit = { ids ->
        val hasDirty = ids.any { id -> state.openTabs.any { it.id == id && it.isDirty } }
        if (hasDirty) multiDirtyIdsToClose = ids else forceCloseByIds(ids)
    }

    val requestCloseTab: (Int) -> Unit = { index ->
        val tab = state.openTabs.getOrNull(index)
        if (tab == null) {
            Unit
        } else if (tab.isDirty) {
            dirtyCloseTabIndex = index
        } else {
            state.closeTab(index)
        }
    }

    // ── Persist settings whenever any field changes ───────────────────────
    LaunchedEffect(state) {
        snapshotFlow {
            with(state.settings) {
                "$autoSaveRequests|$confirmBeforeDelete|$defaultTimeoutSec|${theme.name}" +
                    "|${responseLayout.name}|${language.name}|$requestTimeoutSec|$followRedirects|$collectionsExpanded|$environmentsExpanded|$proxyEnabled|$httpProxy|$httpsProxy|$allowJson5InJsonBodies|$showEditorPositionIndicator" +
                    "|$environmentDialogWidthDp|$environmentDialogHeightDp" +
                    "|$vaultProviderType|$vaultServerUrl|$vaultMountPath|$vaultNamespace|$vaultToken" +
                    "|${state.selectedEnvironment?.name ?: ""}"
            }
        }.drop(1)
            .debounce(500)
            .collectLatest {
                state.settings.selectedEnvName = state.selectedEnvironment?.name ?: ""
                withContext(ioDispatcher) { SettingsRepository.save(state.settings) }
            }
    }

    // ── Auto-save tabs when switching or editing (if auto-save is on) ─────
    LaunchedEffect(state) {
        snapshotFlow {
            val t = state.activeTab
            val revisions = state.openTabs.joinToString("|") { "${it.id}:${it.persistenceRevision}" }
            "${state.openTabs.size}|${state.activeTabIndex}|${t?.id}|$revisions"
        }.drop(1)
            .debounce(500)
            .collectLatest {
                if (state.settings.autoSaveRequests) {
                    val snapshot = TabsRepository.buildSnapshot(state)
                    withContext(ioDispatcher) { TabsRepository.saveSnapshot(snapshot) }
                }
            }
    }

    LaunchedEffect(state) {
        snapshotFlow {
            val collectionFingerprint = state.collectionsRevision
            val envFingerprint = state.environments.joinToString("|") { env ->
                val vars = env.variables.joinToString(",") { v -> "${v.key}=${v.value}:${v.enabled}:${v.secret}" }
                "${env.name}:[$vars]"
            }
            val globalFingerprint = state.globalVariables.joinToString("|") { gv ->
                "${gv.key}=${gv.value}:${gv.enabled}:${gv.secret}"
            }
            val historyFingerprint = "rev-${state.historyRevision}"
            // Include expand state so collapsing/expanding a collection triggers a save
            val expandedFingerprint = state.collectionExpandedState.entries
                .joinToString(",") { (k, v) -> "$k=$v" }
            "$collectionFingerprint#$envFingerprint#$globalFingerprint#$historyFingerprint#$expandedFingerprint"
        }.drop(1)
            .debounce(500)
            .collectLatest {
                WorkspaceRepository.saveAsync(state, ioDispatcher)
            }
    }

    LaunchedEffect(state.activeTabIndex, state.openTabs.size) {
        state.syncSidebarToActiveTab()
    }

    LaunchedEffect(state.activeTabIndex, state.openTabs.size, state.settings.autoSaveRequests) {
        val previousId = lastActiveTabId
        val currentId = state.activeTab?.id

        if (state.settings.autoSaveRequests && previousId != null && previousId != currentId) {
            val previousTab = state.openTabs.firstOrNull { it.id == previousId }
            if (previousTab != null && previousTab.isDirty) {
                withContext(ioDispatcher) { TabsRepository.save(state) }
                previousTab.markSaved()
            }
        }

        lastActiveTabId = currentId
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .testTag("main-screen")
            .hotkeys {
                on(AppHotkeys.Send) {
                    val activeTab = state.activeTab
                    if (activeTab != null) {
                        if (activeTab.kind == RequestKind.MCP) {
                            val session = state.getOrCreateMcpSession(activeTab.id)
                            if (activeTab.isLoading) session.cancelCall() else session.pendingShortcut?.invoke()
                        } else if (activeTab.isLoading) {
                            activeTab.currentJob?.cancel()
                            activeTab.isLoading = false
                        } else {
                            sendRequest(scope, state, activeTab)
                        }
                    }
                }
                on(AppHotkeys.Save) {
                    state.activeTab?.let { saveRequest(scope, state, it) }
                }
                on(AppHotkeys.CloseTab) {
                    requestCloseTab(state.activeTabIndex)
                }
                on(AppHotkeys.NewTab) {
                    state.addTabInSelectedCollection()
                }
                on(AppHotkeys.Settings) {
                    state.showSettingsDialog = true
                }
                onConditional(AppHotkeys.MoveTabLeft) { event ->
                    if (event.utf16CodePoint != '{'.code && event.utf16CodePoint != '['.code) {
                        val from = state.activeTabIndex
                        if (from > 0) {
                            state.moveTab(from, from - 1)
                            true
                        } else false
                    } else false
                }
                onConditional(AppHotkeys.MoveTabRight) { event ->
                    if (event.utf16CodePoint != '}'.code && event.utf16CodePoint != ']'.code) {
                        val from = state.activeTabIndex
                        if (from < state.openTabs.lastIndex) {
                            state.moveTab(from, from + 1)
                            true
                        } else false
                    } else false
                }
            },
    ) {
        // ── Dialog overlays ─────────────────────────────────
        // Feature dialogs render bottom-to-top, so a dialog opened later (e.g. Help from Settings) is on top.
        state.dialogs.dialogs.forEach { dialog -> key(dialog) {
            when (dialog) {
                is AppDialog.EnvironmentEdit -> EnvironmentEditDialog(state)
                AppDialog.Settings -> SettingsDialog(state)
                AppDialog.Help -> HelpAboutDialog(state)
                AppDialog.GlobalVariables -> GlobalVariablesDialog(state)
                AppDialog.ImportSpecification -> ImportSpecificationDialog(
                    state = state,
                    onDismiss = { state.showImportSpecificationDialog = false },
                )
                AppDialog.ExportCollections -> ExportCollectionsDialog(
                    state = state,
                    onDismiss = { state.showExportCollectionsDialog = false },
                )
                AppDialog.GitSync -> GitSyncDialog(
                    state = state,
                    onDismiss = { state.showGitSyncDialog = false },
                )
            }
        } }
        // Alerts always sit above feature dialogs.
        state.dialogs.alerts.forEach { alert -> key(alert) {
            when (alert) {
                is AppAlert.Confirm -> ConfirmDeleteDialog(
                    title = alert.title,
                    message = alert.message,
                    onConfirm = { state.resolveConfirm(true) },
                    onDismiss = { state.resolveConfirm(false) },
                )
                is AppAlert.Operation -> OperationProgressDialog(
                    title = alert.title,
                    message = alert.message,
                    onCancel = { state.cancelOperation() },
                )
                is AppAlert.Error -> ErrorMessageDialog(
                    title = alert.title,
                    message = alert.message,
                    onDismiss = { state.dismissError() },
                )
            }
        } }
        dirtyCloseTabIndex?.let { closeIndex ->
            DirtyCloseDialog(
                onSave = {
                    val tab = state.openTabs.getOrNull(closeIndex)
                    if (tab != null) {
                        saveRequest(scope, state, tab) {
                            state.closeTab(closeIndex)
                            dirtyCloseTabIndex = null
                        }
                    } else {
                        dirtyCloseTabIndex = null
                    }
                },
                onDiscard = {
                    state.closeTab(closeIndex)
                    dirtyCloseTabIndex = null
                },
                onCancel = { dirtyCloseTabIndex = null },
            )
        }
        if (multiDirtyIdsToClose.isNotEmpty()) {
            val ids = multiDirtyIdsToClose
            val dirtyCount = ids.count { id -> state.openTabs.any { it.id == id && it.isDirty } }
            DirtyMultiCloseDialog(
                dirtyCount = dirtyCount,
                onSaveAll = {
                    scope.launch {
                        withContext(ioDispatcher) { TabsRepository.save(state) }
                        state.openTabs.forEach { it.markSaved() }
                        forceCloseByIds(ids)
                        multiDirtyIdsToClose = emptyList()
                    }
                },
                onDiscardAll = {
                    forceCloseByIds(ids)
                    multiDirtyIdsToClose = emptyList()
                },
                onCancel = { multiDirtyIdsToClose = emptyList() },
            )
        }

        // ── Main content ────────────────────────────────────
        Row(modifier = Modifier.weight(1f)) {
            AnimatedVisibility(
                visible = state.sidebarExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
            ) {
                Row {
                    Sidebar(state)
                    SidebarResizeDivider(state)
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (state.workspaceMode) {
                    WorkspaceMode.HTTP -> HttpWorkspaceContent(state, scope, requestCloseTab, closeManyTabs)
                    WorkspaceMode.REALTIME -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) { RealtimePanel() }
                    WorkspaceMode.GRAPHQL -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) { GraphQLPanel(state) }
                }

                BottomPanel(state)
            }
        }
    }
}

@Composable
private fun ColumnScope.HttpWorkspaceContent(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    onRequestClose: (Int) -> Unit,
    closeManyTabs: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.weight(1f).fillMaxWidth()) {
        RequestTabsBar(
                    state = state,
                    onRequestClose = onRequestClose,
                    onRenameTab = { requestId, newName ->
                        state.renameRequestEverywhere(requestId, newName)
                    },
                    onShowInSidebar = { requestId ->
                        state.revealRequestInSidebar(requestId)
                    },
                    onCloseOthers = { idx ->
                        val ids = state.openTabs.indices.filter { it != idx }.map { state.openTabs[it].id }
                        closeManyTabs(ids)
                    },
                    onCloseToLeft = { idx ->
                        val ids = (0 until idx).map { state.openTabs[it].id }
                        closeManyTabs(ids)
                    },
                    onCloseToRight = { idx ->
                        val ids = (idx + 1..state.openTabs.lastIndex).map { state.openTabs[it].id }
                        closeManyTabs(ids)
                    },
                    onCloseAll = {
                        closeManyTabs(state.openTabs.map { it.id })
                    },
                )

                val tab = state.activeTab
                if (tab != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Tab &&
                                    event.isShiftPressed &&
                                    !event.isCtrlPressed &&
                                    !event.isMetaPressed &&
                                    !event.isAltPressed
                                ) {
                                    state.selectNextTab()
                                    true
                                } else {
                                    false
                                }
                            },
                    ) {
                        if (tab.kind == RequestKind.FOLDER) {
                            key(tab.id) {
                                FolderEditor(
                                    tab = tab,
                                    state = state,
                                    onSave = { saveRequest(scope, state, tab) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else if (tab.kind == RequestKind.COLLECTION) {
                            key(tab.id) {
                                CollectionEditor(
                                    tab = tab,
                                    state = state,
                                    onSave = { saveRequest(scope, state, tab) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else if (tab.kind == RequestKind.WEBSOCKET) {
                            key(tab.id) {
                                WebSocketRequestEditor(
                                    tab = tab,
                                    state = state,
                                    onSave = { saveRequest(scope, state, tab) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else if (tab.kind == RequestKind.MCP) {
                            if (state.settings.responseLayout == ResponseLayout.RIGHT) {
                                HorizontalSplitPane(
                                    modifier = Modifier.fillMaxSize().testTag("mcp-workspace"),
                                    splitFraction = state.requestResponseSplit,
                                    onSplitChanged = { state.requestResponseSplit = it },
                                    first = { McpPanel(state, tab) },
                                    second = { McpWorkspaceResponse(state, tab) },
                                )
                            } else {
                                VerticalSplitPane(
                                    modifier = Modifier.fillMaxSize().testTag("mcp-workspace"),
                                    splitFraction = state.mainVerticalSplit,
                                    onSplitChanged = { state.mainVerticalSplit = it },
                                    first = { McpPanel(state, tab) },
                                    second = { McpWorkspaceResponse(state, tab) },
                                )
                            }
                        } else if (state.settings.responseLayout == ResponseLayout.RIGHT) {
                            HorizontalSplitPane(
                                modifier = Modifier.fillMaxSize().testTag("response-layout-right"),
                                splitFraction = state.requestResponseSplit,
                                onSplitChanged = { state.requestResponseSplit = it },
                                first = {
                                    key(tab.id) {
                                        RequestEditor(
                                            tab = tab,
                                            state = state,
                                            onSend = { sendRequest(scope, state, tab) },
                                            onCancel = { tab.currentJob?.cancel(); tab.isLoading = false },
                                            onSave = { saveRequest(scope, state, tab) },
                                        )
                                    }
                                },
                                second = {
                                    key(tab.id) {
                                        ResponseViewer(tab, showCursorPosition = state.settings.showEditorPositionIndicator)
                                    }
                                },
                            )
                        } else {
                            VerticalSplitPane(
                                modifier = Modifier.fillMaxSize().testTag("response-layout-bottom"),
                                splitFraction = state.mainVerticalSplit,
                                onSplitChanged = { state.mainVerticalSplit = it },
                                first = {
                                    key(tab.id) {
                                        RequestEditor(
                                            tab = tab,
                                            state = state,
                                            onSend = { sendRequest(scope, state, tab) },
                                            onCancel = { tab.currentJob?.cancel(); tab.isLoading = false },
                                            onSave = { saveRequest(scope, state, tab) },
                                        )
                                    }
                                },
                                second = {
                                    key(tab.id) {
                                        ResponseViewer(tab, showCursorPosition = state.settings.showEditorPositionIndicator)
                                    }
                                },
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(ClapiColors.Background)
                            .testTag("empty-workspace-view"),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = Strings.noRequestSelected,
                                color = ClapiColors.OnSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = Strings.openRequestToStart,
                                color = ClapiColors.OnSurfaceDim,
                                fontSize = 13.sp,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ClapiColors.Primary)
                                        .clickable { state.addTabInSelectedCollection() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("empty-create-request"),
                                ) {
                                    Text(Strings.t("create_request"), color = ClapiColors.OnPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
    }
}