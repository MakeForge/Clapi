package io.github.crustack.clapi.ui.shared.components.dialogs.env

import io.github.crustack.clapi.ui.shared.components.VariableAwareTextField
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.components.dialogEscape
import io.github.crustack.clapi.ui.shared.components.DirtyCloseDialog
import io.github.crustack.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.crustack.clapi.ui.shared.components.draggableNoSlop


import org.jetbrains.compose.resources.painterResource
import io.github.crustack.clapi.ui.shared.generated.resources.Res

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_close
import io.github.crustack.clapi.ui.shared.generated.resources.ic_close_fullscreen
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.crustack.clapi.ui.shared.generated.resources.ic_open_in_full
import io.github.crustack.clapi.ui.shared.generated.resources.ic_refresh
import io.github.crustack.clapi.ui.shared.generated.resources.ic_search
import io.github.crustack.clapi.ui.shared.generated.resources.ic_visibility
import io.github.crustack.clapi.ui.shared.generated.resources.ic_visibility_off
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.crustack.clapi.ui.shared.platform.horizontalResizeCursor
import io.github.crustack.clapi.ui.shared.platform.insetScrollbar
import io.github.crustack.clapi.ui.shared.platform.nwseResizeCursor
import io.github.crustack.clapi.ui.shared.platform.platformDiagonalResizeCursorStyle
import io.github.crustack.clapi.ui.shared.platform.platformResizeCursorStyle
import io.github.crustack.clapi.ui.shared.platform.verticalResizeCursor
import io.github.crustack.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.crustack.clapi.ui.shared.shortcuts.hotkeys
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.crustack.clapi.core.network.keeper.createKeeperClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.min
import kotlin.math.roundToInt

enum class EnvironmentEditTab {
    VARIABLES,
    EXTERNAL_SECRETS,
}

@Composable
fun EnvironmentEditDialog(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    if (!state.showEnvEditDialog) return
    val envIndex = state.editingEnvIndex
    val env = state.environments.getOrNull(envIndex) ?: return
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val workingName = remember(envIndex) { mutableStateOf(env.name) }
    val workingVars = remember(envIndex) {
        mutableStateListOf<MutableKeyValue>().also { list ->
            env.variables.forEach { v -> list.add(MutableKeyValue(v.key, v.value, v.enabled, v.secret)) }
        }
    }
    var searchValue by remember(envIndex) { mutableStateOf(TextFieldValue("")) }
    var activeEditingUid by remember(envIndex) { mutableStateOf<String?>(null) }
    var lastInteractedUid by remember(envIndex) { mutableStateOf<String?>(null) }
    var focusNewRowUid by remember(envIndex) { mutableStateOf<String?>(null) }
    val searchFocusRequester = remember(envIndex) { FocusRequester() }
    val listState = rememberLazyListState()

    var activeTab by remember(envIndex) { mutableStateOf(EnvironmentEditTab.VARIABLES) }
    var workingProvider by remember(envIndex) { mutableStateOf(env.externalSecretsProvider) }
    var providerMenuExpanded by remember(envIndex) { mutableStateOf(false) }
    val workingSecrets = remember(envIndex) {
        mutableStateListOf<MutableExternalSecret>().also { list ->
            env.externalSecrets.forEach { s ->
                list.add(
                    MutableExternalSecret(
                        name = s.name,
                        path = s.path,
                        description = s.description,
                        enabled = s.enabled,
                        url = s.url,
                        targetField = s.targetField,
                        customFilters = s.customFilters.toMap(),
                    )
                )
            }
        }
    }
    val secretsListState = rememberLazyListState()

    var preferredWidthDp by remember { mutableStateOf(state.settings.environmentDialogWidthDp) }
    var preferredHeightDp by remember { mutableStateOf(state.settings.environmentDialogHeightDp) }
    var geometry by remember(envIndex) {
        mutableStateOf(EnvironmentDialogGeometry(preferredWidthDp, preferredHeightDp))
    }
    var restoreGeometry by remember(envIndex) { mutableStateOf<EnvironmentDialogGeometry?>(null) }
    var isMaximized by remember(envIndex) { mutableStateOf(false) }
    var manualSizeChanged by remember(envIndex) { mutableStateOf(false) }
    var activeResizeHandle by remember(envIndex) { mutableStateOf<EnvironmentResizeHandle?>(null) }
    var resizeOrigin by remember(envIndex) { mutableStateOf<EnvironmentDialogGeometry?>(null) }
    val resizeActive = remember(envIndex) { mutableStateOf(false) }

    fun persistManualSize() {
        if (!manualSizeChanged || isMaximized) return
        preferredWidthDp = geometry.widthDp
        preferredHeightDp = geometry.heightDp
        state.settings.environmentDialogWidthDp = geometry.widthDp
        state.settings.environmentDialogHeightDp = geometry.heightDp
        manualSizeChanged = false
    }

    fun dismissDialog() {
        persistManualSize()
        state.dialogs.dismissEnvironmentEdit()
    }

    val captureSnapshot: () -> List<Any?> = {
        listOf(
            workingName.value,
            workingProvider,
            workingVars.filter { it.key.isNotBlank() || it.value.isNotBlank() }
                .map { listOf(it.key, it.value, it.enabled, it.secret) },
            workingSecrets.filter { it.name.isNotBlank() || it.path.isNotBlank() || it.url.isNotBlank() }
                .map { listOf(it.name, it.path, it.description, it.enabled, it.url, it.targetField, it.customFilters.toMap()) },
        )
    }

    var baselineSnapshot by remember(envIndex) { mutableStateOf(captureSnapshot()) }
    var showUnsavedConfirm by remember(envIndex) { mutableStateOf(false) }

    fun isDirty(): Boolean = captureSnapshot() != baselineSnapshot

    fun saveEnvironment(closeDialog: Boolean = false) {
        env.name = workingName.value
        env.variables.clear()
        workingVars.forEach { v ->
            if (v.key.isNotBlank() || v.value.isNotBlank()) {
                env.variables.add(MutableKeyValue(v.key, v.value, v.enabled, v.secret))
            }
        }
        state.pruneEmptyVariablesForEnvironment(envIndex)

        env.externalSecretsProvider = workingProvider
        env.externalSecrets.clear()
        val isKeePassXc = workingProvider.equals("keepassxc", ignoreCase = true)
        val isAws = workingProvider.equals("aws-secrets-manager", ignoreCase = true)
        val isAzure = workingProvider.equals("azure-key-vault", ignoreCase = true)
        val isGcp = workingProvider.equals("gcp-secrets-manager", ignoreCase = true)
        val isKeeper = workingProvider.equals("keeper-security-cloud", ignoreCase = true)
        workingSecrets.forEach { s ->
            if (isKeePassXc) {
                if (s.name.isNotBlank() || s.url.isNotBlank()) {
                    env.externalSecrets.add(
                        MutableExternalSecret(
                            name = s.name,
                            path = s.path,
                            description = s.description,
                            enabled = s.enabled,
                            url = s.url,
                            targetField = s.targetField,
                            customFilters = s.customFilters.toMap(),
                        )
                    )
                }
            } else if (isAws) {
                if (s.name.isNotBlank() || s.path.isNotBlank() || s.url.isNotBlank()) {
                    env.externalSecrets.add(
                        MutableExternalSecret(
                            name = s.name,
                            path = s.path.ifBlank { s.url },
                            description = s.description,
                            enabled = s.enabled,
                            targetField = s.targetField,
                            customFilters = s.customFilters.toMap(),
                        )
                    )
                }
            } else if (isAzure) {
                if (s.name.isNotBlank() || s.path.isNotBlank() || s.url.isNotBlank()) {
                    env.externalSecrets.add(
                        MutableExternalSecret(
                            name = s.name,
                            path = s.path,
                            url = s.url,
                            description = s.description,
                            enabled = s.enabled,
                            targetField = s.targetField,
                            customFilters = s.customFilters.toMap(),
                        )
                    )
                }
            } else if (isGcp) {
                if (s.name.isNotBlank() || s.path.isNotBlank() || s.url.isNotBlank()) {
                    env.externalSecrets.add(
                        MutableExternalSecret(
                            name = s.name,
                            path = s.path,
                            url = s.url,
                            description = s.description,
                            enabled = s.enabled,
                            targetField = s.targetField,
                            customFilters = s.customFilters.toMap(),
                        )
                    )
                }
            } else if (isKeeper) {
                if (s.name.isNotBlank() || s.path.isNotBlank()) {
                    env.externalSecrets.add(
                        MutableExternalSecret(
                            name = s.name,
                            path = s.path,
                            description = s.description,
                            enabled = s.enabled,
                            targetField = s.targetField,
                            customFilters = s.customFilters.toMap(),
                        )
                    )
                }
            } else {
                if (s.name.isNotBlank() || s.path.isNotBlank()) {
                    env.externalSecrets.add(
                        MutableExternalSecret(
                            name = s.name,
                            path = s.path,
                            description = s.description,
                            enabled = s.enabled,
                        )
                    )
                }
            }
        }

        baselineSnapshot = captureSnapshot()
        state.log("✓ Environment '${env.name}' saved (${env.variables.size} variables, ${env.externalSecrets.size} secrets)", io.github.crustack.clapi.ui.shared.state.LogLevel.SUCCESS)
        if (closeDialog) {
            dismissDialog()
        }
    }

    fun requestClose() {
        if (isDirty()) {
            showUnsavedConfirm = true
        } else {
            dismissDialog()
        }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .dialogEscape(onDismiss = ::requestClose)
                .lockedResizeCursor(activeResizeHandle)
                .pointerInput(envIndex) { detectTapGestures { if (!resizeActive.value) requestClose() } },
            contentAlignment = Alignment.Center,
        ) {
            val viewportWidthDp = maxWidth.value
            val viewportHeightDp = maxHeight.value
            val maxDialogWidthDp = (viewportWidthDp - ENV_DIALOG_MARGIN_DP * 2f).coerceAtLeast(1f)
            val maxDialogHeightDp = (viewportHeightDp - ENV_DIALOG_MARGIN_DP * 2f).coerceAtLeast(1f)
            val minDialogWidthDp = min(ENV_DIALOG_MIN_WIDTH_DP, maxDialogWidthDp)
            val minDialogHeightDp = min(ENV_DIALOG_MIN_HEIGHT_DP, maxDialogHeightDp)

            LaunchedEffect(viewportWidthDp, viewportHeightDp, isMaximized) {
                geometry = if (isMaximized) {
                    EnvironmentDialogGeometry(maxDialogWidthDp, maxDialogHeightDp)
                } else {
                    clampEnvironmentDialog(geometry, viewportWidthDp, viewportHeightDp)
                }
            }

            val exactMatches = visibleEnvironmentVariables(workingVars, searchValue.text)
            val visibleVars = visibleEnvironmentVariables(
                workingVars,
                searchValue.text,
                pinnedUid = activeEditingUid,
            )

            LaunchedEffect(searchValue.text) {
                if (searchValue.text.trim().isNotEmpty()) {
                    activeEditingUid = null
                    if (visibleVars.isNotEmpty()) listState.scrollToItem(0)
                }
            }

            fun clearSearch(keepUidVisible: String? = lastInteractedUid) {
                searchValue = TextFieldValue("")
                scope.launch {
                    yield()
                    val index = keepUidVisible?.let { uid -> workingVars.indexOfFirst { it.uid == uid } } ?: -1
                    if (index >= 0) listState.scrollToItem(index)
                }
            }

            fun applyGeometry(candidate: EnvironmentDialogGeometry) {
                geometry = clampEnvironmentDialog(candidate, viewportWidthDp, viewportHeightDp)
            }

            Box(
                modifier = modifier
                    .offset {
                        IntOffset(
                            (geometry.offsetXDp * density).roundToInt(),
                            (geometry.offsetYDp * density).roundToInt(),
                        )
                    }
                    .width(geometry.widthDp.dp)
                    .height(geometry.heightDp.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClapiColors.Surface)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .hotkeys {
                            on(AppHotkeys.Save) {
                                saveEnvironment(closeDialog = false)
                            }
                            on(AppHotkeys.Find) {
                                searchValue = searchValue.copy(
                                    selection = TextRange(0, searchValue.text.length),
                                )
                                searchFocusRequester.requestFocus()
                            }
                            onConditional(AppHotkeys.Escape) {
                                if (searchValue.text.isNotEmpty()) {
                                    clearSearch()
                                    true
                                } else false
                            }
                        }
                        .testTag("env-edit-dialog"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggableNoSlop { dx, dy ->
                                applyGeometry(
                                    geometry.copy(
                                        offsetXDp = geometry.offsetXDp + dx / density,
                                        offsetYDp = geometry.offsetYDp + dy / density,
                                    ),
                                )
                            }
                            .padding(bottom = 12.dp)
                            .testTag("env-dialog-title-bar"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Strings.t("edit_environment"),
                            style = MaterialTheme.typography.titleLarge,
                            color = ClapiColors.OnSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        val maxTooltip = if (isMaximized) Strings.restoreDialog else Strings.maximizeDialog
                        IconButtonWithTooltip(
                            onClick = {
                                if (isMaximized) {
                                    isMaximized = false
                                    geometry = clampEnvironmentDialog(
                                        restoreGeometry ?: EnvironmentDialogGeometry(preferredWidthDp, preferredHeightDp),
                                        viewportWidthDp,
                                        viewportHeightDp,
                                    )
                                    restoreGeometry = null
                                } else {
                                    restoreGeometry = geometry
                                    isMaximized = true
                                    geometry = EnvironmentDialogGeometry(maxDialogWidthDp, maxDialogHeightDp)
                                }
                            },
                            tooltip = maxTooltip,
                            modifier = Modifier.size(28.dp).testTag("env-dialog-maximize"),
                        ) {
                            Icon(
                                if (isMaximized) painterResource(Res.drawable.ic_close_fullscreen) else painterResource(Res.drawable.ic_open_in_full),
                                contentDescription = maxTooltip,
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Text(Strings.t("name"), style = MaterialTheme.typography.labelMedium, color = ClapiColors.OnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    EnvTextField(
                        value = workingName.value,
                        onValueChange = { workingName.value = it },
                        placeholder = Strings.t("environment_name"),
                        tag = "env-name-field",
                    )
                    Spacer(Modifier.height(12.dp))

                    // Tab Selector: Variables vs External Secrets
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == EnvironmentEditTab.VARIABLES) ClapiColors.SelectedItem else ClapiColors.SurfaceContainer)
                                .border(
                                    1.dp,
                                    if (activeTab == EnvironmentEditTab.VARIABLES) ClapiColors.Primary else ClapiColors.Border,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable { activeTab = EnvironmentEditTab.VARIABLES }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("env-tab-variables"),
                        ) {
                            Text(
                                Strings.variables,
                                color = if (activeTab == EnvironmentEditTab.VARIABLES) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == EnvironmentEditTab.VARIABLES) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == EnvironmentEditTab.EXTERNAL_SECRETS) ClapiColors.SelectedItem else ClapiColors.SurfaceContainer)
                                .border(
                                    1.dp,
                                    if (activeTab == EnvironmentEditTab.EXTERNAL_SECRETS) ClapiColors.Primary else ClapiColors.Border,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable { activeTab = EnvironmentEditTab.EXTERNAL_SECRETS }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("env-tab-secrets"),
                        ) {
                            Text(
                                Strings.t("external_secrets"),
                                color = if (activeTab == EnvironmentEditTab.EXTERNAL_SECRETS) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == EnvironmentEditTab.EXTERNAL_SECRETS) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }

                    if (activeTab == EnvironmentEditTab.VARIABLES) {
                        EnvironmentVariableSearch(
                            value = searchValue,
                            onValueChange = { searchValue = it },
                            onClear = { clearSearch() },
                            shown = exactMatches.size,
                            total = workingVars.size,
                            focusRequester = searchFocusRequester,
                        )
                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(ClapiColors.SurfaceHigh)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Spacer(Modifier.width(32.dp))
                            TableHeader(Strings.t("key_upper"), Modifier.weight(1f))
                            TableHeader(Strings.t("value_upper"), Modifier.weight(1f))
                            TableHeader(Strings.t("type_upper"), Modifier.width(90.dp))
                            Spacer(Modifier.width(36.dp))
                            Spacer(Modifier.width(10.dp))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 160.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(ClapiColors.Surface)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 10.dp)
                                    .testTag("env-variables-list"),
                            ) {
                            itemsIndexed(visibleVars, key = { _, kv -> kv.uid }) { _, kv ->
                                val originalIndex = workingVars.indexOf(kv)
                                EnvVariableRow(
                                    kv = kv,
                                    index = originalIndex,
                                    focusKey = focusNewRowUid == kv.uid,
                                    onFocusConsumed = { focusNewRowUid = null },
                                    onRowFocusChanged = { focused ->
                                        if (focused) {
                                            activeEditingUid = kv.uid
                                            lastInteractedUid = kv.uid
                                        } else if (activeEditingUid == kv.uid) {
                                            activeEditingUid = null
                                        }
                                    },
                                    onDelete = {
                                        workingVars.remove(kv)
                                        if (activeEditingUid == kv.uid) activeEditingUid = null
                                        if (lastInteractedUid == kv.uid) lastInteractedUid = null
                                    },
                                    state = state,
                                    workingVars = workingVars,
                                )
                                Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
                            }
                            if (visibleVars.isEmpty() && searchValue.text.trim().isNotEmpty()) {
                                item(key = "no-matches") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("env-no-search-results"),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(Strings.noMatchingVariables, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                                        Text(
                                            Strings.clearSearch,
                                            color = ClapiColors.Primary,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .clickable { clearSearch(null) }
                                                .testTag("env-no-results-clear"),
                                        )
                                    }
                                }
                            }
                            }
                            PlatformLazyVerticalScrollbar(
                                listState = listState,
                                modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
                                testTag = "env-variables-list-vscrollbar",
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val added = MutableKeyValue()
                                    workingVars.add(added)
                                    searchValue = TextFieldValue("")
                                    focusNewRowUid = added.uid
                                    lastInteractedUid = added.uid
                                    activeEditingUid = added.uid
                                    scope.launch {
                                        yield()
                                        listState.scrollToItem(workingVars.lastIndex)
                                    }
                                }
                                .testTag("env-add-variable")
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, tint = ClapiColors.Primary, modifier = Modifier.size(16.dp))
                            Text(Strings.addVariable, color = ClapiColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        // External Secrets Tab
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                Strings.t("secret_provider"),
                                fontSize = 12.sp,
                                color = ClapiColors.OnSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ClapiColors.SurfaceContainer)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                    .clickable { providerMenuExpanded = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("env-secrets-provider-selector"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        when (workingProvider) {
                                            "keepassxc" -> "KeePassXC"
                                            "keeper-security-cloud" -> "Keeper Security Cloud"
                                            "hashicorp-vault-server" -> "HashiCorp Vault (Server)"
                                            "aws-secrets-manager" -> "AWS Secrets Manager"
                                            "azure-key-vault" -> "Azure Key Vault"
                                            "gcp-secrets-manager" -> "GCP Secret Manager"
                                            else -> workingProvider
                                        },
                                        color = ClapiColors.OnSurface,
                                        fontSize = 12.sp,
                                    )
                                    Icon(painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                                        contentDescription = null,
                                        tint = ClapiColors.OnSurfaceDim,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = providerMenuExpanded,
                                    onDismissRequest = { providerMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("HashiCorp Vault (Server)", fontSize = 12.sp) },
                                        onClick = {
                                            workingProvider = "hashicorp-vault-server"
                                            providerMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-provider-option-hashicorp-vault-server"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("KeePassXC", fontSize = 12.sp) },
                                        onClick = {
                                            workingProvider = "keepassxc"
                                            providerMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-provider-option-keepassxc"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Keeper Security Cloud", fontSize = 12.sp) },
                                        onClick = {
                                            workingProvider = "keeper-security-cloud"
                                            providerMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-provider-option-keeper-security-cloud"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("AWS Secrets Manager", fontSize = 12.sp) },
                                        onClick = {
                                            workingProvider = "aws-secrets-manager"
                                            providerMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-provider-option-aws-secrets-manager"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Azure Key Vault", fontSize = 12.sp) },
                                        onClick = {
                                            workingProvider = "azure-key-vault"
                                            providerMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-provider-option-azure-key-vault"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("GCP Secret Manager", fontSize = 12.sp) },
                                        onClick = {
                                            workingProvider = "gcp-secrets-manager"
                                            providerMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-provider-option-gcp-secrets-manager"),
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(ClapiColors.SurfaceHigh)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Spacer(Modifier.width(32.dp))
                            if (workingProvider == "keepassxc") {
                                TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                TableHeader("ENTRY URL", Modifier.weight(1.3f))
                                TableHeader("TARGET FIELD", Modifier.weight(0.9f))
                                TableHeader("STATUS", Modifier.weight(1.0f))
                                TableHeader("FILTERS", Modifier.weight(0.9f))
                                TableHeader("DESCRIPTION", Modifier.weight(1.0f))
                            } else if (workingProvider == "keeper-security-cloud") {
                                TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                TableHeader("RECORD / TITLE", Modifier.weight(1.3f))
                                TableHeader("TARGET FIELD", Modifier.weight(0.9f))
                                TableHeader("STATUS", Modifier.weight(1.0f))
                                TableHeader("FILTERS", Modifier.weight(0.9f))
                                TableHeader("DESCRIPTION", Modifier.weight(1.0f))
                            } else if (workingProvider == "aws-secrets-manager") {
                                TableHeader(Strings.t("name"), Modifier.weight(1.1f))
                                TableHeader("SECRET ID / NAME", Modifier.weight(1.4f))
                                TableHeader("TARGET FIELD", Modifier.weight(1.0f))
                                TableHeader("FILTERS", Modifier.weight(0.9f))
                                TableHeader("DESCRIPTION", Modifier.weight(1.1f))
                            } else if (workingProvider == "azure-key-vault") {
                                TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                TableHeader("SECRET NAME", Modifier.weight(1.2f))
                                TableHeader("VAULT OVERRIDE", Modifier.weight(1.1f))
                                TableHeader("TARGET FIELD", Modifier.weight(0.9f))
                                TableHeader("FILTERS", Modifier.weight(0.8f))
                                TableHeader("DESCRIPTION", Modifier.weight(1.0f))
                            } else if (workingProvider == "gcp-secrets-manager") {
                                TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                TableHeader("SECRET ID", Modifier.weight(1.2f))
                                TableHeader("PROJECT OVERRIDE", Modifier.weight(1.1f))
                                TableHeader("TARGET FIELD", Modifier.weight(0.9f))
                                TableHeader("FILTERS", Modifier.weight(0.8f))
                                TableHeader("DESCRIPTION", Modifier.weight(1.0f))
                            } else {
                                TableHeader(Strings.t("name"), Modifier.weight(1.2f))
                                TableHeader("PATH", Modifier.weight(1.5f))
                                TableHeader("DESCRIPTION", Modifier.weight(1.5f))
                            }
                            Spacer(Modifier.width(36.dp))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 160.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(ClapiColors.Surface)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                        ) {
                            LazyColumn(
                                state = secretsListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 10.dp)
                                    .testTag("env-secrets-list"),
                            ) {
                                itemsIndexed(workingSecrets, key = { _, secret -> secret.uid }) { sIdx, secret ->
                                    when (workingProvider) {
                                        "keepassxc" -> EnvKeePassXcSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        "keeper-security-cloud" -> EnvKeeperSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        "aws-secrets-manager" -> EnvAwsSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        "azure-key-vault" -> EnvAzureSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        "gcp-secrets-manager" -> EnvGcpSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        else -> EnvExternalSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                    }
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(ClapiColors.Border))
                                }
                                if (workingSecrets.isEmpty()) {
                                    item(key = "no-secrets") {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().height(120.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                "No external secrets defined. Click 'Add Secret' below.",
                                                color = ClapiColors.OnSurfaceDim,
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                                }
                            }
                            PlatformLazyVerticalScrollbar(
                                listState = secretsListState,
                                modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
                                testTag = "env-secrets-list-vscrollbar",
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    workingSecrets.add(MutableExternalSecret())
                                    scope.launch {
                                        yield()
                                        secretsListState.scrollToItem(workingSecrets.lastIndex)
                                    }
                                }
                                .testTag("env-add-secret")
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null, tint = ClapiColors.Primary, modifier = Modifier.size(16.dp))
                            Text("Add Secret", color = ClapiColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .clickable { requestClose() }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                .testTag("env-cancel-button"),
                        ) { Text(Strings.cancel, color = ClapiColors.OnSurface, fontSize = 13.sp) }

                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.Primary)
                                .clickable { saveEnvironment(closeDialog = true) }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                .testTag("env-save-button"),
                        ) { Text(Strings.save, color = ClapiColors.OnPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }

                if (!isMaximized) {
                    EnvironmentResizeHandles(
                        onResizeStart = { handle ->
                            activeResizeHandle = handle
                            resizeOrigin = geometry
                            resizeActive.value = true
                            manualSizeChanged = true
                        },
                        onResize = { handle, dxDp, dyDp ->
                            val origin = resizeOrigin ?: return@EnvironmentResizeHandles
                            applyGeometry(
                                resizeEnvironmentDialog(
                                    origin,
                                    handle,
                                    dxDp,
                                    dyDp,
                                    minDialogWidthDp,
                                    minDialogHeightDp,
                                    maxDialogWidthDp,
                                    maxDialogHeightDp,
                                ),
                            )
                        },
                        onResizeEnd = {
                            activeResizeHandle = null
                            resizeOrigin = null
                            resizeActive.value = false
                            persistManualSize()
                        },
                    )
                }
            }
        }
    }

    if (showUnsavedConfirm) {
        DirtyCloseDialog(
            title = "Unsaved Changes",
            message = Strings.t("request_unsaved_changes_message"),
            onSave = {
                saveEnvironment(closeDialog = true)
                showUnsavedConfirm = false
            },
            onDiscard = {
                showUnsavedConfirm = false
                dismissDialog()
            },
            onCancel = {
                showUnsavedConfirm = false
            },
        )
    }
}

