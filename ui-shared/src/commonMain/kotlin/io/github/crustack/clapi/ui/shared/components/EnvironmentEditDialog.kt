package io.github.crustack.clapi.ui.shared.components

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.min
import kotlin.math.roundToInt

enum class EnvironmentEditTab {
    VARIABLES,
    EXTERNAL_SECRETS,
}

private const val ENV_DIALOG_MIN_WIDTH_DP = 600f
private const val ENV_DIALOG_MIN_HEIGHT_DP = 460f
private const val ENV_DIALOG_MARGIN_DP = 16f
private const val ENV_RESIZE_EDGE_DP = 12f
private const val ENV_RESIZE_CORNER_DP = 20f

internal enum class EnvironmentResizeHandle(
    val affectsLeft: Boolean = false,
    val affectsRight: Boolean = false,
    val affectsTop: Boolean = false,
    val affectsBottom: Boolean = false,
) {
    LEFT(affectsLeft = true),
    RIGHT(affectsRight = true),
    TOP(affectsTop = true),
    BOTTOM(affectsBottom = true),
    TOP_LEFT(affectsLeft = true, affectsTop = true),
    TOP_RIGHT(affectsRight = true, affectsTop = true),
    BOTTOM_LEFT(affectsLeft = true, affectsBottom = true),
    BOTTOM_RIGHT(affectsRight = true, affectsBottom = true),
}

internal data class EnvironmentDialogGeometry(
    val widthDp: Float,
    val heightDp: Float,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
)

internal fun resizeEnvironmentDialog(
    geometry: EnvironmentDialogGeometry,
    handle: EnvironmentResizeHandle,
    deltaXDp: Float,
    deltaYDp: Float,
    minWidthDp: Float,
    minHeightDp: Float,
    maxWidthDp: Float,
    maxHeightDp: Float,
): EnvironmentDialogGeometry {
    val widthDelta = when {
        handle.affectsLeft -> -deltaXDp
        handle.affectsRight -> deltaXDp
        else -> 0f
    }
    val heightDelta = when {
        handle.affectsTop -> -deltaYDp
        handle.affectsBottom -> deltaYDp
        else -> 0f
    }
    val width = (geometry.widthDp + widthDelta).coerceIn(minWidthDp, maxWidthDp)
    val height = (geometry.heightDp + heightDelta).coerceIn(minHeightDp, maxHeightDp)
    val appliedWidth = width - geometry.widthDp
    val appliedHeight = height - geometry.heightDp
    return geometry.copy(
        widthDp = width,
        heightDp = height,
        offsetXDp = geometry.offsetXDp + when {
            handle.affectsLeft -> -appliedWidth / 2f
            handle.affectsRight -> appliedWidth / 2f
            else -> 0f
        },
        offsetYDp = geometry.offsetYDp + when {
            handle.affectsTop -> -appliedHeight / 2f
            handle.affectsBottom -> appliedHeight / 2f
            else -> 0f
        },
    )
}

internal fun clampEnvironmentDialog(
    geometry: EnvironmentDialogGeometry,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
): EnvironmentDialogGeometry {
    if (viewportWidthDp <= 0f || viewportHeightDp <= 0f) return geometry
    val maxWidth = (viewportWidthDp - ENV_DIALOG_MARGIN_DP * 2f).coerceAtLeast(1f)
    val maxHeight = (viewportHeightDp - ENV_DIALOG_MARGIN_DP * 2f).coerceAtLeast(1f)
    val width = geometry.widthDp.coerceIn(min(ENV_DIALOG_MIN_WIDTH_DP, maxWidth), maxWidth)
    val height = geometry.heightDp.coerceIn(min(ENV_DIALOG_MIN_HEIGHT_DP, maxHeight), maxHeight)
    val maxOffsetX = ((viewportWidthDp - width) / 2f - ENV_DIALOG_MARGIN_DP).coerceAtLeast(0f)
    val maxOffsetY = ((viewportHeightDp - height) / 2f - ENV_DIALOG_MARGIN_DP).coerceAtLeast(0f)
    return geometry.copy(
        widthDp = width,
        heightDp = height,
        offsetXDp = geometry.offsetXDp.coerceIn(-maxOffsetX, maxOffsetX),
        offsetYDp = geometry.offsetYDp.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

internal fun environmentVariableMatches(key: String, value: String, query: String): Boolean {
    val normalized = query.trim()
    return normalized.isEmpty() || key.contains(normalized, ignoreCase = true) ||
        value.contains(normalized, ignoreCase = true)
}

internal fun visibleEnvironmentVariables(
    variables: List<MutableKeyValue>,
    query: String,
    pinnedUid: String? = null,
): List<MutableKeyValue> = variables.filter { kv ->
    environmentVariableMatches(kv.key, kv.value, query) || kv.uid == pinnedUid
}

internal fun environmentVariableResultCountLabel(shown: Int, total: Int, template: String): String =
    template.replace("{shown}", shown.toString()).replace("{total}", total.toString())

@Composable
fun EnvironmentEditDialog(state: AppState) {
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
                modifier = Modifier
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
                                            "hashicorp-vault-server" -> "HashiCorp Vault (Server)"
                                            "aws-secrets-manager" -> "AWS Secrets Manager"
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
                                        text = { Text("AWS Secrets Manager", fontSize = 12.sp) },
                                        onClick = {
                                            workingProvider = "aws-secrets-manager"
                                            providerMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-provider-option-aws-secrets-manager"),
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
                            } else if (workingProvider == "aws-secrets-manager") {
                                TableHeader(Strings.t("name"), Modifier.weight(1.1f))
                                TableHeader("SECRET ID / NAME", Modifier.weight(1.4f))
                                TableHeader("TARGET FIELD", Modifier.weight(1.0f))
                                TableHeader("FILTERS", Modifier.weight(0.9f))
                                TableHeader("DESCRIPTION", Modifier.weight(1.1f))
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
                                itemsIndexed(workingSecrets) { sIdx, secret ->
                                    when (workingProvider) {
                                        "keepassxc" -> EnvKeePassXcSecretRow(
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

// ── Sub-components ──────────────────────────────────────────────

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ClapiColors.OnSurfaceDim,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = modifier,
    )
}

@Composable
private fun EnvVariableRow(
    kv: MutableKeyValue,
    index: Int,
    focusKey: Boolean,
    onFocusConsumed: () -> Unit,
    onRowFocusChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    state: AppState? = null,
    workingVars: List<MutableKeyValue> = emptyList(),
) {
    var showValue by remember { mutableStateOf(!kv.secret) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val keyFocusRequester = remember { FocusRequester() }
    val baseRowColor = when (environmentRowTone(index, isHovered)) {
        EnvironmentRowTone.EVEN -> ClapiColors.SurfaceVariant
        EnvironmentRowTone.ODD -> ClapiColors.Surface
        EnvironmentRowTone.HOVERED -> ClapiColors.Primary.copy(alpha = 0.08f)
    }
    val rowColor = baseRowColor

    val initialKey = remember { kv.key }
    val excludeVars = remember(kv.key, initialKey) {
        val set = mutableSetOf<String>()
        if (kv.key.isNotBlank()) set.add(kv.key.trim())
        if (initialKey.isNotBlank()) set.add(initialKey.trim())
        set
    }
    val extraVars = remember(workingVars.size, workingVars.map { it.key to it.value }) {
        workingVars.filter { it.key.isNotBlank() }.map { it.key to it.value }
    }

    LaunchedEffect(focusKey) {
        if (focusKey) {
            yield()
            keyFocusRequester.requestFocus()
            onFocusConsumed()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .hoverable(interactionSource)
            .focusGroup()
            .onFocusChanged { onRowFocusChanged(it.hasFocus) }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("env-var-row-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Enabled checkbox
        Checkbox(
            checked = kv.enabled,
            onCheckedChange = { kv.enabled = it },
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = ClapiColors.Primary,
                uncheckedColor = ClapiColors.OnSurfaceDim,
            ),
        )

        // Key field
        EnvTextField(
            value = kv.key,
            onValueChange = { kv.key = it },
            placeholder = Strings.variableName,
            tag = "env-var-key-$index",
            modifier = Modifier.weight(1f),
            focusRequester = keyFocusRequester,
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeVars,
        )

        // Value field (masked if secret)
        EnvTextField(
            value = kv.value,
            onValueChange = { kv.value = it },
            placeholder = if (kv.secret) "••••••••" else Strings.value,
            masked = kv.secret && !showValue,
            tag = "env-var-value-$index",
            modifier = Modifier.weight(1f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeVars,
        )

        // Type toggle (Normal / Secret)
        Row(
            modifier = Modifier.width(90.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (kv.secret) Strings.t("secret") else Strings.t("normal"),
                fontSize = 11.sp,
                color = if (kv.secret) ClapiColors.Tertiary else ClapiColors.OnSurfaceDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (kv.secret) ClapiColors.Tertiary.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { kv.secret = !kv.secret; if (kv.secret) showValue = false }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        // Reveal / hide value for secrets
        if (kv.secret) {
            val toggleTooltip = if (showValue) "Hide value" else "Show value"
            IconButtonWithTooltip(
                onClick = { showValue = !showValue },
                tooltip = toggleTooltip,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    if (showValue) painterResource(Res.drawable.ic_visibility_off) else painterResource(Res.drawable.ic_visibility),
                    contentDescription = toggleTooltip,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }

        // Delete
        IconButtonWithTooltip(
            onClick = onDelete,
            tooltip = Strings.delete,
            modifier = Modifier.size(24.dp).testTag("env-var-delete-$index"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = Strings.delete, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
}

enum class EnvironmentRowTone {
    EVEN,
    ODD,
    HOVERED,
}

fun environmentRowTone(index: Int, isHovered: Boolean): EnvironmentRowTone {
    if (isHovered) return EnvironmentRowTone.HOVERED
    return if (index % 2 == 0) EnvironmentRowTone.EVEN else EnvironmentRowTone.ODD
}

@Composable
private fun EnvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    masked: Boolean = false,
    tag: String = "",
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    state: AppState? = null,
    extraVariables: List<Pair<String, String>> = emptyList(),
    excludeVariables: Set<String> = emptySet(),
) {
    val displayValue = if (masked) "•".repeat(value.length.coerceAtMost(20)) else value
    var isFocused by remember { mutableStateOf(false) }

    if (state != null && !masked) {
        Box(
            modifier = modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isFocused) ClapiColors.Surface else ClapiColors.SurfaceContainer)
                .border(1.dp, if (isFocused) ClapiColors.Primary else ClapiColors.Border, RoundedCornerShape(4.dp))
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            VariableAwareTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                state = state,
                extraVariables = extraVariables,
                excludeVariables = excludeVariables,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
            )
            if (isFocused && tag.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .align(Alignment.TopStart)
                        .testTag("$tag-focused"),
                )
            }
        }
    } else {
        BasicTextField(
            value = if (masked) displayValue else value,
            onValueChange = if (masked) ({}) else onValueChange,
            singleLine = true,
            readOnly = masked,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isFocused) ClapiColors.Surface else ClapiColors.SurfaceContainer)
                .border(1.dp, if (isFocused) ClapiColors.Primary else ClapiColors.Border, RoundedCornerShape(4.dp))
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && !masked) {
                        Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    }
                    inner()
                    if (isFocused && tag.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(1.dp)
                                .align(Alignment.TopStart)
                                .testTag("$tag-focused"),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun EnvExternalSecretRow(
    secret: MutableExternalSecret,
    index: Int,
    workingVars: List<MutableKeyValue> = emptyList(),
    state: AppState? = null,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val baseRowColor = when (environmentRowTone(index, isHovered)) {
        EnvironmentRowTone.EVEN -> ClapiColors.SurfaceVariant
        EnvironmentRowTone.ODD -> ClapiColors.Surface
        EnvironmentRowTone.HOVERED -> ClapiColors.Primary.copy(alpha = 0.08f)
    }
    val extraVars = remember(workingVars.size, workingVars.map { it.key to it.value }) {
        workingVars.filter { it.key.isNotBlank() }.map { it.key to it.value }
    }
    val initialName = remember { secret.name }
    val excludeSecretVars = remember(secret.name, initialName) {
        val set = mutableSetOf<String>()
        if (secret.name.isNotBlank()) {
            set.add(secret.name.trim())
            set.add("_secret.${secret.name.trim()}")
        }
        if (initialName.isNotBlank()) {
            set.add(initialName.trim())
            set.add("_secret.${initialName.trim()}")
        }
        set
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseRowColor)
            .hoverable(interactionSource)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("env-secret-row-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = secret.enabled,
            onCheckedChange = { secret.enabled = it },
            modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-enabled"),
            colors = CheckboxDefaults.colors(
                checkedColor = ClapiColors.Primary,
                uncheckedColor = ClapiColors.OnSurfaceDim,
            ),
        )

        EnvTextField(
            value = secret.name,
            onValueChange = { secret.name = it },
            placeholder = "client_id",
            tag = "env-secret-row-$index-name",
            modifier = Modifier.weight(1.2f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeSecretVars,
        )

        EnvTextField(
            value = secret.path,
            onValueChange = { secret.path = it },
            placeholder = "payment/foo",
            tag = "env-secret-row-$index-path",
            modifier = Modifier.weight(1.5f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeSecretVars,
        )

        EnvTextField(
            value = secret.description,
            onValueChange = { secret.description = it },
            placeholder = "Description (optional)",
            tag = "env-secret-row-$index-desc",
            modifier = Modifier.weight(1.5f),
            state = state,
            extraVariables = extraVars,
            excludeVariables = excludeSecretVars,
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-delete"),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_delete),
                contentDescription = Strings.delete,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun EnvKeePassXcSecretRow(
    secret: MutableExternalSecret,
    index: Int,
    workingVars: List<MutableKeyValue>,
    state: AppState? = null,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val baseRowColor = when (environmentRowTone(index, isHovered)) {
        EnvironmentRowTone.EVEN -> ClapiColors.SurfaceVariant
        EnvironmentRowTone.ODD -> ClapiColors.Surface
        EnvironmentRowTone.HOVERED -> ClapiColors.Primary.copy(alpha = 0.08f)
    }
    val extraVars = remember(workingVars.size, workingVars.map { it.key to it.value }) {
        workingVars.filter { it.key.isNotBlank() }.map { it.key to it.value }
    }
    val initialName = remember { secret.name }
    val excludeSecretVars = remember(secret.name, initialName) {
        val set = mutableSetOf<String>()
        if (secret.name.isNotBlank()) {
            set.add(secret.name.trim())
            set.add("_secret.${secret.name.trim()}")
        }
        if (initialName.isNotBlank()) {
            set.add(initialName.trim())
            set.add("_secret.${initialName.trim()}")
        }
        set
    }

    var filtersExpanded by remember { mutableStateOf(false) }
    var matchState by remember { mutableStateOf("—") }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(secret.url, secret.customFilters.toMap(), refreshTrigger) {
        val trimmedUrl = secret.url.trim()
        if (trimmedUrl.isBlank()) {
            matchState = "—"
            return@LaunchedEffect
        }
        matchState = "Checking..."
        delay(350)
        try {
            val client = createKeePassXcClient()
            val resolvedFilters = secret.customFilters.mapValues { (_, v) ->
                var text = v
                workingVars.forEach { kv ->
                    if (kv.key.isNotBlank()) {
                        text = text.replace("{{${kv.key.trim()}}}", kv.value)
                    }
                }
                text
            }
            val count = client.countMatchingLogins(trimmedUrl, resolvedFilters)
            matchState = when {
                count == 0 -> "✖ 0 found"
                count == 1 -> "✔ 1 match"
                else -> "✔ $count matches"
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            matchState = when {
                msg.contains("locked", ignoreCase = true) -> "Locked"
                msg.contains("connect", ignoreCase = true) || msg.contains("proxy", ignoreCase = true) -> "Closed"
                msg.contains("denied", ignoreCase = true) -> "Unassociated"
                else -> "Error"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseRowColor)
            .hoverable(interactionSource)
            .testTag("env-secret-row-$index"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = secret.enabled,
                onCheckedChange = { secret.enabled = it },
                modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-enabled"),
                colors = CheckboxDefaults.colors(
                    checkedColor = ClapiColors.Primary,
                    uncheckedColor = ClapiColors.OnSurfaceDim,
                ),
            )

            EnvTextField(
                value = secret.name,
                onValueChange = { secret.name = it },
                placeholder = "secret_name",
                tag = "env-secret-row-$index-name",
                modifier = Modifier.weight(1.0f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            EnvTextField(
                value = secret.url,
                onValueChange = { secret.url = it },
                placeholder = "https://entry.url",
                tag = "env-secret-row-$index-url",
                modifier = Modifier.weight(1.3f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            EnvTextField(
                value = secret.targetField,
                onValueChange = { secret.targetField = it },
                placeholder = "password",
                tag = "env-secret-row-$index-target-field",
                modifier = Modifier.weight(0.9f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            Row(
                modifier = Modifier.weight(1.0f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = matchState,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = when {
                        matchState.startsWith("✔") -> ClapiColors.Primary
                        matchState.startsWith("✖") -> ClapiColors.Error
                        matchState == "Checking..." -> ClapiColors.OnSurfaceDim
                        else -> ClapiColors.OnSurfaceDim
                    },
                    modifier = Modifier.testTag("env-secret-row-$index-match-count"),
                )

                IconButton(
                    onClick = { refreshTrigger++ },
                    modifier = Modifier.size(20.dp).testTag("env-secret-row-$index-refresh-status"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = "Refresh status",
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.weight(0.9f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (filtersExpanded) ClapiColors.Primary.copy(alpha = 0.15f) else ClapiColors.SurfaceContainer)
                        .border(
                            1.dp,
                            if (filtersExpanded) ClapiColors.Primary else ClapiColors.Border,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { filtersExpanded = !filtersExpanded }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("env-secret-row-$index-filter-toggle"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (secret.customFilterList.isEmpty()) "Filters" else "Filters (${secret.customFilterList.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (filtersExpanded) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                        )
                        Icon(
                            painter = if (filtersExpanded) painterResource(Res.drawable.ic_keyboard_arrow_up) else painterResource(Res.drawable.ic_keyboard_arrow_down),
                            contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters",
                            tint = if (filtersExpanded) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            EnvTextField(
                value = secret.description,
                onValueChange = { secret.description = it },
                placeholder = "Description (optional)",
                tag = "env-secret-row-$index-desc",
                modifier = Modifier.weight(1.0f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-delete"),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = Strings.delete,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (filtersExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClapiColors.SurfaceContainer.copy(alpha = 0.4f))
                    .border(1.dp, ClapiColors.Border)
                    .padding(start = 32.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
                    .testTag("env-secret-row-$index-filters-panel"),
            ) {
                Text(
                    "Custom Field Filters (URL entry attributes match):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClapiColors.OnSurfaceDim,
                )
                Spacer(Modifier.height(4.dp))
                secret.customFilterList.forEachIndexed { fIdx, kv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("env-secret-row-$index-filter-${kv.key.ifBlank { fIdx.toString() }}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EnvTextField(
                            value = kv.key,
                            onValueChange = {
                                kv.key = it
                                secret.syncCustomFiltersFromList()
                            },
                            placeholder = "field (e.g. login)",
                            tag = "env-secret-row-$index-filter-key-${kv.key.ifBlank { fIdx.toString() }}",
                            modifier = Modifier.width(130.dp),
                            state = state,
                            extraVariables = extraVars,
                            excludeVariables = excludeSecretVars,
                        )
                        Text("=", fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                        EnvTextField(
                            value = kv.value,
                            onValueChange = {
                                kv.value = it
                                secret.syncCustomFiltersFromList()
                            },
                            placeholder = "match value or {{var}}",
                            tag = "env-secret-row-$index-filter-val-${kv.key.ifBlank { fIdx.toString() }}",
                            modifier = Modifier.weight(1f),
                            state = state,
                            extraVariables = extraVars,
                            excludeVariables = excludeSecretVars,
                        )
                        IconButton(
                            onClick = {
                                secret.customFilterList.remove(kv)
                                secret.syncCustomFiltersFromList()
                            },
                            modifier = Modifier.size(20.dp).testTag("env-secret-row-$index-remove-filter-${kv.key.ifBlank { fIdx.toString() }}"),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = "Remove filter",
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }

                var newFilterKey by remember { mutableStateOf("") }
                var newFilterVal by remember { mutableStateOf("") }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EnvTextField(
                        value = newFilterKey,
                        onValueChange = { newFilterKey = it },
                        placeholder = "field (e.g. login, tier)",
                        tag = "env-secret-row-$index-new-filter-key",
                        modifier = Modifier.width(130.dp),
                        state = state,
                        extraVariables = extraVars,
                        excludeVariables = excludeSecretVars,
                    )
                    Text("=", fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                    EnvTextField(
                        value = newFilterVal,
                        onValueChange = { newFilterVal = it },
                        placeholder = "expected value or {{var}}",
                        tag = "env-secret-row-$index-new-filter-val",
                        modifier = Modifier.weight(1f),
                        state = state,
                        extraVariables = extraVars,
                        excludeVariables = excludeSecretVars,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.Primary)
                            .clickable {
                                if (newFilterKey.isNotBlank()) {
                                    secret.customFilterList.add(MutableKeyValue(key = newFilterKey.trim(), value = newFilterVal.trim()))
                                    secret.syncCustomFiltersFromList()
                                    newFilterKey = ""
                                    newFilterVal = ""
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("env-secret-row-$index-add-filter-btn"),
                    ) {
                        Text("Add Filter", color = ClapiColors.OnPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvAwsSecretRow(
    secret: MutableExternalSecret,
    index: Int,
    workingVars: List<MutableKeyValue>,
    state: AppState? = null,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val baseRowColor = when (environmentRowTone(index, isHovered)) {
        EnvironmentRowTone.EVEN -> ClapiColors.SurfaceVariant
        EnvironmentRowTone.ODD -> ClapiColors.Surface
        EnvironmentRowTone.HOVERED -> ClapiColors.Primary.copy(alpha = 0.08f)
    }
    val extraVars = remember(workingVars.size, workingVars.map { it.key to it.value }) {
        workingVars.filter { it.key.isNotBlank() }.map { it.key to it.value }
    }
    val initialName = remember { secret.name }
    val excludeSecretVars = remember(secret.name, initialName) {
        val set = mutableSetOf<String>()
        if (secret.name.isNotBlank()) {
            set.add(secret.name.trim())
            set.add("_secret.${secret.name.trim()}")
        }
        if (initialName.isNotBlank()) {
            set.add(initialName.trim())
            set.add("_secret.${initialName.trim()}")
        }
        set
    }

    var filtersExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseRowColor)
            .hoverable(interactionSource)
            .testTag("env-secret-row-$index"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = secret.enabled,
                onCheckedChange = { secret.enabled = it },
                modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-enabled"),
                colors = CheckboxDefaults.colors(
                    checkedColor = ClapiColors.Primary,
                    uncheckedColor = ClapiColors.OnSurfaceDim,
                ),
            )

            EnvTextField(
                value = secret.name,
                onValueChange = { secret.name = it },
                placeholder = "secret_name",
                tag = "env-secret-row-$index-name",
                modifier = Modifier.weight(1.1f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            EnvTextField(
                value = secret.path,
                onValueChange = { secret.path = it },
                placeholder = "secret-id or ARN",
                tag = "env-secret-row-$index-path",
                modifier = Modifier.weight(1.4f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            EnvTextField(
                value = secret.targetField,
                onValueChange = { secret.targetField = it },
                placeholder = "target key (e.g. client_id)",
                tag = "env-secret-row-$index-target-field",
                modifier = Modifier.weight(1.0f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            Box(
                modifier = Modifier.weight(0.9f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (filtersExpanded) ClapiColors.Primary.copy(alpha = 0.15f) else ClapiColors.SurfaceContainer)
                        .border(
                            1.dp,
                            if (filtersExpanded) ClapiColors.Primary else ClapiColors.Border,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { filtersExpanded = !filtersExpanded }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("env-secret-row-$index-filter-toggle"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (secret.customFilterList.isEmpty()) "Filters" else "Filters (${secret.customFilterList.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (filtersExpanded) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                        )
                        Icon(
                            painter = if (filtersExpanded) painterResource(Res.drawable.ic_keyboard_arrow_up) else painterResource(Res.drawable.ic_keyboard_arrow_down),
                            contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters",
                            tint = if (filtersExpanded) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            EnvTextField(
                value = secret.description,
                onValueChange = { secret.description = it },
                placeholder = "Description (optional)",
                tag = "env-secret-row-$index-desc",
                modifier = Modifier.weight(1.1f),
                state = state,
                extraVariables = extraVars,
                excludeVariables = excludeSecretVars,
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp).testTag("env-secret-row-$index-delete"),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = Strings.delete,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (filtersExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClapiColors.SurfaceContainer.copy(alpha = 0.4f))
                    .border(1.dp, ClapiColors.Border)
                    .padding(start = 32.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
                    .testTag("env-secret-row-$index-filters-panel"),
            ) {
                Text(
                    "Custom Field Filters (Secret key-value attributes match):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClapiColors.OnSurfaceDim,
                )
                Spacer(Modifier.height(4.dp))
                secret.customFilterList.forEachIndexed { fIdx, kv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("env-secret-row-$index-filter-${kv.key.ifBlank { fIdx.toString() }}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EnvTextField(
                            value = kv.key,
                            onValueChange = {
                                kv.key = it
                                secret.syncCustomFiltersFromList()
                            },
                            placeholder = "field (e.g. stage)",
                            tag = "env-secret-row-$index-filter-key-${kv.key.ifBlank { fIdx.toString() }}",
                            modifier = Modifier.width(130.dp),
                            state = state,
                            extraVariables = extraVars,
                            excludeVariables = excludeSecretVars,
                        )
                        Text("=", fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                        EnvTextField(
                            value = kv.value,
                            onValueChange = {
                                kv.value = it
                                secret.syncCustomFiltersFromList()
                            },
                            placeholder = "expected value or {{var}}",
                            tag = "env-secret-row-$index-filter-val-${kv.key.ifBlank { fIdx.toString() }}",
                            modifier = Modifier.weight(1f),
                            state = state,
                            extraVariables = extraVars,
                            excludeVariables = excludeSecretVars,
                        )
                        IconButton(
                            onClick = {
                                secret.customFilterList.remove(kv)
                                secret.syncCustomFiltersFromList()
                            },
                            modifier = Modifier.size(20.dp).testTag("env-secret-row-$index-remove-filter-${kv.key.ifBlank { fIdx.toString() }}"),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = "Remove filter",
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }

                var newFilterKey by remember { mutableStateOf("") }
                var newFilterVal by remember { mutableStateOf("") }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EnvTextField(
                        value = newFilterKey,
                        onValueChange = { newFilterKey = it },
                        placeholder = "field (e.g. stage)",
                        tag = "env-secret-row-$index-new-filter-key",
                        modifier = Modifier.width(130.dp),
                        state = state,
                        extraVariables = extraVars,
                        excludeVariables = excludeSecretVars,
                    )
                    Text("=", fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                    EnvTextField(
                        value = newFilterVal,
                        onValueChange = { newFilterVal = it },
                        placeholder = "expected value or {{var}}",
                        tag = "env-secret-row-$index-new-filter-val",
                        modifier = Modifier.weight(1f),
                        state = state,
                        extraVariables = extraVars,
                        excludeVariables = excludeSecretVars,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.Primary)
                            .clickable {
                                if (newFilterKey.isNotBlank()) {
                                    secret.customFilterList.add(MutableKeyValue(key = newFilterKey.trim(), value = newFilterVal.trim()))
                                    secret.syncCustomFiltersFromList()
                                    newFilterKey = ""
                                    newFilterVal = ""
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("env-secret-row-$index-add-filter-btn"),
                    ) {
                        Text("Add Filter", color = ClapiColors.OnPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentVariableSearch(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    shown: Int,
    total: Int,
    focusRequester: FocusRequester,
) {
    val placeholder = Strings.searchEnvironmentVariables
    val countLabel = environmentVariableResultCountLabel(
        shown,
        total,
        Strings.t("environment_variable_result_count"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClapiColors.SurfaceContainer)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("env-variable-search"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            tint = ClapiColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .testTag("env-variable-search-input")
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
        if (value.text.isNotEmpty()) {
            Icon(painter = painterResource(Res.drawable.ic_close),
                contentDescription = Strings.clearSearch,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onClear)
                    .testTag("env-search-clear"),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            countLabel,
            color = ClapiColors.OnSurfaceDim,
            fontSize = 11.sp,
            modifier = Modifier
                .testTag("env-search-count")
                .semantics { contentDescription = countLabel },
        )
    }
}

@Composable
private fun EnvironmentResizeHandles(
    onResizeStart: (EnvironmentResizeHandle) -> Unit,
    onResize: (EnvironmentResizeHandle, Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ENV_RESIZE_EDGE_DP.dp)
                .pointerHoverIcon(horizontalResizeCursor)
                .platformResizeCursorStyle(isHorizontal = true)
                .environmentResizeHandle(
                    handle = EnvironmentResizeHandle.RIGHT,
                    onResizeStart = { onResizeStart(EnvironmentResizeHandle.RIGHT) },
                    onResize = { dx, dy -> onResize(EnvironmentResizeHandle.RIGHT, dx, dy) },
                    onResizeEnd = onResizeEnd,
                )
                .testTag("env-resize-right"),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ENV_RESIZE_EDGE_DP.dp)
                .pointerHoverIcon(verticalResizeCursor)
                .platformResizeCursorStyle(isHorizontal = false)
                .environmentResizeHandle(
                    handle = EnvironmentResizeHandle.BOTTOM,
                    onResizeStart = { onResizeStart(EnvironmentResizeHandle.BOTTOM) },
                    onResize = { dx, dy -> onResize(EnvironmentResizeHandle.BOTTOM, dx, dy) },
                    onResizeEnd = onResizeEnd,
                )
                .testTag("env-resize-bottom"),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(ENV_RESIZE_CORNER_DP.dp)
                .pointerHoverIcon(nwseResizeCursor)
                .platformDiagonalResizeCursorStyle(isNwSe = true)
                .environmentResizeHandle(
                    handle = EnvironmentResizeHandle.BOTTOM_RIGHT,
                    onResizeStart = { onResizeStart(EnvironmentResizeHandle.BOTTOM_RIGHT) },
                    onResize = { dx, dy -> onResize(EnvironmentResizeHandle.BOTTOM_RIGHT, dx, dy) },
                    onResizeEnd = onResizeEnd,
                )
                .testTag("env-resize-corner"),
        ) {
            val gripColor = ClapiColors.OnSurfaceDim
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val step = size.minDimension / 4f
                for (i in 1..3) {
                    val o = step * i
                    drawLine(
                        color = gripColor,
                        start = Offset(size.width - o, size.height),
                        end = Offset(size.width, size.height - o),
                        strokeWidth = 1.5f,
                    )
                }
            }
        }
    }
}

private fun Modifier.lockedResizeCursor(handle: EnvironmentResizeHandle?): Modifier = when (handle) {
    EnvironmentResizeHandle.RIGHT ->
        pointerHoverIcon(horizontalResizeCursor, overrideDescendants = true)
            .platformResizeCursorStyle(isHorizontal = true)
    EnvironmentResizeHandle.BOTTOM ->
        pointerHoverIcon(verticalResizeCursor, overrideDescendants = true)
            .platformResizeCursorStyle(isHorizontal = false)
    EnvironmentResizeHandle.BOTTOM_RIGHT ->
        pointerHoverIcon(nwseResizeCursor, overrideDescendants = true)
            .platformDiagonalResizeCursorStyle(isNwSe = true)
    else -> this
}

@Composable
private fun Modifier.environmentResizeHandle(
    handle: EnvironmentResizeHandle,
    onResizeStart: () -> Unit,
    onResize: (dxDp: Float, dyDp: Float) -> Unit,
    onResizeEnd: () -> Unit,
): Modifier {
    val layout = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val onStartState = rememberUpdatedState(onResizeStart)
    val onDragState = rememberUpdatedState(onResize)
    val onEndState = rememberUpdatedState(onResizeEnd)
    return onGloballyPositioned { layout.value = it }
        .pointerInput(handle) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val originRoot = layout.value?.takeIf { it.isAttached }?.localToRoot(down.position)
                    ?: return@awaitEachGesture
                down.consume()
                onStartState.value()
                try {
                    drag(down.id) { change ->
                        val currentRoot = layout.value?.takeIf { it.isAttached }?.localToRoot(change.position)
                        if (currentRoot != null) {
                            change.consume()
                            onDragState.value(
                                (currentRoot.x - originRoot.x).toDp().value,
                                (currentRoot.y - originRoot.y).toDp().value,
                            )
                        }
                    }
                } finally {
                    onEndState.value()
                }
            }
        }
}
