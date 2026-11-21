package io.github.makeforge.clapi.ui.shared.components.dialogs.env

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.network.aws.KtorAwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.azure.AzureKeyVaultConfig
import io.github.makeforge.clapi.core.network.azure.KtorAzureKeyVaultClient
import io.github.makeforge.clapi.core.network.gcp.DefaultGcpTokenProvider
import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerConfig
import io.github.makeforge.clapi.core.network.gcp.KtorGcpSecretManagerClient
import io.github.makeforge.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.makeforge.clapi.core.network.keeper.KeeperRecordFilter
import io.github.makeforge.clapi.core.network.keeper.createKeeperClient
import io.github.makeforge.clapi.core.network.vault.KtorHashiCorpVaultClient
import io.github.makeforge.clapi.ui.shared.components.externalSecretHelpUrl
import io.github.makeforge.clapi.ui.shared.platform.openUrlInBrowser
import io.github.makeforge.clapi.ui.shared.components.VariableAwareTextField
import io.github.makeforge.clapi.ui.shared.components.autoFocus
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.components.dialogEscape
import io.github.makeforge.clapi.ui.shared.components.DirtyCloseDialog
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.draggableNoSlop


import org.jetbrains.compose.resources.painterResource
import io.github.makeforge.clapi.ui.shared.generated.resources.*

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_arrow_drop_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_code
import io.github.makeforge.clapi.core.model.opencollection.VariableKind
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.ui.shared.components.dialogs.dynamicvar.DynamicVariableEditorDialog
import io.github.makeforge.clapi.ui.shared.state.getEligibleParentEnvironments
import io.github.makeforge.clapi.ui.shared.state.resolveEffectiveDynamicVariables
import io.github.makeforge.clapi.ui.shared.state.resolveEffectiveVariables
import androidx.compose.ui.input.pointer.PointerIcon
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
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_add
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close_fullscreen
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_up
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_full
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_refresh
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_search
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_visibility
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_visibility_off
import io.github.makeforge.clapi.ui.shared.components.DialogTitleBar
import io.github.makeforge.clapi.ui.shared.components.DocsHelpButton
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.horizontalResizeCursor
import io.github.makeforge.clapi.ui.shared.platform.insetScrollbar
import io.github.makeforge.clapi.ui.shared.platform.nwseResizeCursor
import io.github.makeforge.clapi.ui.shared.platform.platformDiagonalResizeCursorStyle
import io.github.makeforge.clapi.ui.shared.platform.platformResizeCursorStyle
import io.github.makeforge.clapi.ui.shared.platform.verticalResizeCursor
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.makeforge.clapi.core.network.keeper.createKeeperClient
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
    useDialogWindow: Boolean = true,
) {
    if (!state.showEnvEditDialog) return
    val envIndex = state.editingEnvIndex
    val env = state.environments.getOrNull(envIndex) ?: return
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val workingName = remember(envIndex) { mutableStateOf(env.name) }
    val workingExtends = remember(envIndex) { mutableStateOf(env.extends) }
    val workingColor = remember(envIndex) { mutableStateOf(env.color) }
    var extendsMenuExpanded by remember(envIndex) { mutableStateOf(false) }

    var quickAddVarKey by remember(envIndex) { mutableStateOf("") }
    var quickAddVarValue by remember(envIndex) { mutableStateOf("") }
    var quickAddVarSecret by remember(envIndex) { mutableStateOf(false) }
    var quickAddVarDynamic by remember(envIndex) { mutableStateOf(false) }
    var quickAddVarType by remember(envIndex) { mutableStateOf(VariableType.STRING) }
    var quickAddEditingKv by remember(envIndex) { mutableStateOf<MutableKeyValue?>(null) }

    var quickAddSecName by remember(envIndex) { mutableStateOf("") }
    var quickAddSecPath by remember(envIndex) { mutableStateOf("") }
    var quickAddSecVault by remember(envIndex) { mutableStateOf("") }
    var quickAddSecTargetField by remember(envIndex) { mutableStateOf("") }
    var quickAddSecDesc by remember(envIndex) { mutableStateOf("") }
    val workingVars = remember(envIndex) {
        mutableStateListOf<MutableKeyValue>().also { list ->
            env.variables.forEach { v ->
                list.add(
                    MutableKeyValue(
                        key = v.key,
                        value = v.value,
                        enabled = v.enabled,
                        secret = v.secret,
                        isDynamic = v.isDynamic,
                        variableType = v.variableType,
                        dynamicScript = v.dynamicScript,
                        dynamicParameters = v.dynamicParameters,
                        description = v.description,
                    )
                )
            }
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
            workingExtends.value,
            workingColor.value,
            workingProvider,
            workingVars.filter { it.key.isNotBlank() || it.value.isNotBlank() || it.isDynamic }
                .map {
                    listOf(
                        it.key,
                        it.value,
                        it.enabled,
                        it.secret,
                        it.isDynamic,
                        it.variableType,
                        it.dynamicScript,
                        it.dynamicParameters,
                        it.description,
                    )
                },
            workingSecrets.filter { it.name.isNotBlank() || it.path.isNotBlank() || it.url.isNotBlank() }
                .map { listOf(it.name, it.path, it.description, it.enabled, it.url, it.targetField, it.customFilters.toMap()) },
        )
    }

    var baselineSnapshot by remember(envIndex) { mutableStateOf(captureSnapshot()) }
    var showUnsavedConfirm by remember(envIndex) { mutableStateOf(false) }

    fun isDirty(): Boolean = captureSnapshot() != baselineSnapshot

    fun saveEnvironment(closeDialog: Boolean = false) {
        env.name = workingName.value
        env.extends = workingExtends.value
        env.color = workingColor.value
        env.variables.clear()
        workingVars.forEach { v ->
            if (v.key.isNotBlank() || v.value.isNotBlank() || v.isDynamic) {
                env.variables.add(
                    MutableKeyValue(
                        key = v.key,
                        value = v.value,
                        enabled = v.enabled,
                        secret = v.secret,
                        isDynamic = v.isDynamic,
                        variableType = v.variableType,
                        dynamicScript = v.dynamicScript,
                        dynamicParameters = v.dynamicParameters,
                        description = v.description,
                    )
                )
            }
        }
        state.pruneEmptyVariablesForEnvironment(envIndex)

        env.externalSecretsProvider = workingProvider
        env.externalSecrets.clear()
        val providerType = ExternalSecretType.fromValue(workingProvider)
        workingSecrets.forEach { s ->
            when (providerType) {
                ExternalSecretType.KEEPASSXC -> {
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
                }
                ExternalSecretType.AWS_SECRETS_MANAGER -> {
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
                }
                ExternalSecretType.AZURE_KEY_VAULT,
                ExternalSecretType.GCP_SECRETS_MANAGER -> {
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
                }
                ExternalSecretType.KEEPER_SECURITY_CLOUD -> {
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
                }
                else -> {
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
        }

        baselineSnapshot = captureSnapshot()
        state.log("✓ Environment '${env.name}' saved (${env.variables.size} variables, ${env.externalSecrets.size} secrets)", io.github.makeforge.clapi.ui.shared.state.LogLevel.SUCCESS)
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

    val dialogBody: @Composable () -> Unit = {
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

            val exactMatches = remember(workingVars.toList(), searchValue.text) {
                visibleEnvironmentVariables(workingVars, searchValue.text)
            }
            val visibleVars = remember(workingVars.toList(), searchValue.text, activeEditingUid) {
                visibleEnvironmentVariables(
                    workingVars,
                    searchValue.text,
                    pinnedUid = activeEditingUid,
                )
            }

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
                    DialogTitleBar(
                        title = Strings.t("edit_environment"),
                        actions = {
                            val maxTooltip = if (isMaximized) Strings.restoreDialog else Strings.maximizeDialog
                            DocsHelpButton(
                                url = DocsLinks.environments,
                                tooltip = Strings.t("environments_documentation"),
                                tag = "env-docs-btn",
                                size = 28.dp,
                            )
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
                        },
                        onClose = ::requestClose,
                        closeButtonTag = "env-dialog-close",
                        onDrag = { dx, dy ->
                            applyGeometry(
                                geometry.copy(
                                    offsetXDp = geometry.offsetXDp + dx / density,
                                    offsetYDp = geometry.offsetYDp + dy / density,
                                ),
                            )
                        },
                        testTag = "env-dialog-title-bar",
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        Text(Strings.t("name"), style = MaterialTheme.typography.labelMedium, color = ClapiColors.OnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    EnvTextField(
                        value = workingName.value,
                        onValueChange = { workingName.value = it },
                        placeholder = Strings.t("environment_name"),
                        tag = "env-name-field",
                        selectAllOnStart = true,
                        modifier = Modifier.autoFocus(),
                    )
                    Spacer(Modifier.height(10.dp))

                    // Extends selector & Color picker row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Extends selector
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Strings.t("extends"),
                                style = MaterialTheme.typography.labelMedium,
                                color = ClapiColors.OnSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            val eligibleEnvs = remember(state.environments.toList(), env) {
                                getEligibleParentEnvironments(env, state.environments)
                            }
                            val isExtendsEnabled = eligibleEnvs.isNotEmpty()
                            Box {
                                OutlinedButton(
                                    onClick = { if (isExtendsEnabled) extendsMenuExpanded = true },
                                    enabled = isExtendsEnabled,
                                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("env-extends-dropdown"),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = workingExtends.value ?: if (isExtendsEnabled) Strings.t("none_optional") else Strings.t("no_other_environments"),
                                        color = if (isExtendsEnabled) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_arrow_drop_down),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isExtendsEnabled) ClapiColors.OnSurfaceDim else ClapiColors.OnSurfaceDim.copy(alpha = 0.5f),
                                    )
                                }
                                DropdownMenu(
                                    expanded = extendsMenuExpanded,
                                    onDismissRequest = { extendsMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(Strings.t("none"), fontSize = 12.sp) },
                                        onClick = {
                                            workingExtends.value = null
                                            extendsMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("env-extends-option-none"),
                                    )
                                    eligibleEnvs.forEach { eligible ->
                                        DropdownMenuItem(
                                            text = { Text(eligible.name, fontSize = 12.sp) },
                                            onClick = {
                                                workingExtends.value = eligible.name
                                                extendsMenuExpanded = false
                                            },
                                            modifier = Modifier.testTag("env-extends-option-${eligible.name}"),
                                        )
                                    }
                                }
                            }
                        }

                        // Color picker
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Strings.t("color"),
                                style = MaterialTheme.typography.labelMedium,
                                color = ClapiColors.OnSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ENVIRONMENT_COLOR_PRESETS.forEach { preset ->
                                    val presetColor = parseHexColor(preset.hex) ?: Color.Gray
                                    val isSelected = workingColor.value?.equals(preset.hex, ignoreCase = true) == true
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(presetColor)
                                            .then(
                                                if (isSelected) Modifier.border(2.dp, ClapiColors.OnSurface, RoundedCornerShape(50))
                                                else Modifier.border(1.dp, ClapiColors.Border, RoundedCornerShape(50))
                                            )
                                            .handPointerHover()
                                            .clickable { workingColor.value = preset.hex }
                                            .testTag("env-color-preset-${preset.name.lowercase()}"),
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                BasicTextField(
                                    value = workingColor.value ?: "",
                                    onValueChange = { workingColor.value = it },
                                    textStyle = TextStyle(
                                        color = ClapiColors.OnSurface,
                                        fontSize = 11.sp,
                                        fontFamily = CodeFontFamily,
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(ClapiColors.Primary),
                                    modifier = Modifier
                                        .width(76.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ClapiColors.SurfaceContainer)
                                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                        .testTag("env-color-hex-field"),
                                    decorationBox = { innerTextField ->
                                        if (workingColor.value.isNullOrBlank()) {
                                            Text("#HEX", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
                                        }
                                        innerTextField()
                                    },
                                )
                                if (workingColor.value != null) {
                                    IconButtonWithTooltip(
                                        onClick = { workingColor.value = null },
                                        tooltip = Strings.t("clear_color"),
                                        modifier = Modifier.size(20.dp).testTag("env-color-clear-button"),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_close),
                                            contentDescription = Strings.t("clear_color"),
                                            tint = ClapiColors.OnSurfaceDim,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // Tab Selector: Variables vs External Secrets
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EnvironmentTabButton(
                            title = Strings.variables,
                            isSelected = activeTab == EnvironmentEditTab.VARIABLES,
                            onClick = { activeTab = EnvironmentEditTab.VARIABLES },
                            tag = "env-tab-variables",
                        )
                        EnvironmentTabButton(
                            title = Strings.t("external_secrets"),
                            isSelected = activeTab == EnvironmentEditTab.EXTERNAL_SECRETS,
                            onClick = { activeTab = EnvironmentEditTab.EXTERNAL_SECRETS },
                            tag = "env-tab-secrets",
                        )
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
                                .padding(start = 8.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Spacer(Modifier.size(24.dp))
                            TableHeader(Strings.t("key_upper"), Modifier.weight(1f))
                            TableHeader(Strings.t("value_upper"), Modifier.weight(1f))
                            TableHeader(Strings.t("kind_upper"), Modifier.width(88.dp))
                            TableHeader(Strings.t("type_upper"), Modifier.width(88.dp))
                            Spacer(Modifier.size(24.dp))
                            Spacer(Modifier.size(24.dp))
                        }

                        val inheritedKeys = remember(workingExtends.value, state.environments.toList()) {
                            val parentName = workingExtends.value?.trim()
                            if (parentName.isNullOrEmpty()) emptySet<String>()
                            else {
                                val parent = state.environments.firstOrNull { it.name == parentName }
                                if (parent != null) {
                                    parent.resolveEffectiveVariables(state.environments).keys +
                                        parent.resolveEffectiveDynamicVariables(state.environments).map { it.name }
                                } else emptySet()
                            }
                        }

                        fun addQuickVariable(): MutableKeyValue? {
                            val trimmed = quickAddVarKey.trim()
                            if (trimmed.isEmpty()) return null
                            val added = MutableKeyValue(
                                key = trimmed,
                                value = if (quickAddVarDynamic) "" else quickAddVarValue,
                                enabled = true,
                                secret = quickAddVarSecret,
                                isDynamic = quickAddVarDynamic,
                                variableType = quickAddVarType,
                                dynamicScript = if (quickAddVarDynamic) "return new Date().toISOString();" else "",
                            )
                            workingVars.add(0, added)
                            quickAddVarKey = ""
                            quickAddVarValue = ""
                            quickAddVarSecret = false
                            quickAddVarDynamic = false
                            quickAddVarType = VariableType.STRING
                            return added
                        }

                        // Quick-add row on top for variables
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border)
                                .padding(start = 8.dp, end = 18.dp, top = 5.dp, bottom = 5.dp)
                                .testTag("env-var-quick-add-row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Spacer(Modifier.size(24.dp))
                            EnvQuickAddTextField(
                                value = quickAddVarKey,
                                onValueChange = { quickAddVarKey = it },
                                placeholder = Strings.variableName,
                                modifier = Modifier.weight(1f),
                                tag = "env-quick-add-key",
                                onEnter = { addQuickVariable() },
                            )
                            if (quickAddVarDynamic) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ClapiColors.Primary.copy(alpha = 0.1f))
                                        .border(1.dp, ClapiColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .clickable {
                                            val added = addQuickVariable()
                                            if (added != null) {
                                                quickAddEditingKv = added
                                            }
                                        }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("env-quick-add-edit-script"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_code),
                                        contentDescription = Strings.editScript,
                                        tint = ClapiColors.Primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = Strings.editScript,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ClapiColors.Primary,
                                    )
                                }
                            } else {
                                EnvQuickAddTextField(
                                    value = quickAddVarValue,
                                    onValueChange = { quickAddVarValue = it },
                                    placeholder = Strings.value,
                                    modifier = Modifier.weight(1f),
                                    tag = "env-quick-add-value",
                                    onEnter = { addQuickVariable() },
                                )
                            }
                            val currentQuickKind = when {
                                quickAddVarDynamic -> VariableKind.DYNAMIC
                                quickAddVarSecret -> VariableKind.SECRET
                                else -> VariableKind.REGULAR
                            }
                            VariableKindSelector(
                                selectedKind = currentQuickKind,
                                onKindSelected = { newKind ->
                                    when (newKind) {
                                        VariableKind.REGULAR -> {
                                            quickAddVarSecret = false
                                            quickAddVarDynamic = false
                                        }
                                        VariableKind.SECRET -> {
                                            quickAddVarSecret = true
                                            quickAddVarDynamic = false
                                        }
                                        VariableKind.DYNAMIC -> {
                                            quickAddVarSecret = false
                                            quickAddVarDynamic = true
                                        }
                                    }
                                },
                                tag = "env-quick-add-secret-toggle",
                                modifier = Modifier.width(88.dp),
                            )
                            VariableTypeSelector(
                                selectedType = quickAddVarType,
                                onTypeSelected = { quickAddVarType = it },
                                tag = "env-quick-add-datatype-select",
                                modifier = Modifier.width(88.dp),
                            )
                            Spacer(Modifier.size(24.dp))
                            EnvQuickAddButton(
                                onClick = { addQuickVariable() },
                                tooltip = Strings.addVariable,
                                tag = "env-quick-add-variable-button",
                            )
                        }

                        quickAddEditingKv?.let { editingKv ->
                            DynamicVariableEditorDialog(
                                kv = editingKv,
                                onDismiss = { quickAddEditingKv = null },
                            )
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
                                    isOverridden = kv.key.trim().isNotEmpty() && kv.key.trim() in inheritedKeys,
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
                                                .handPointerHover()
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
                                .handPointerHover()
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
                        val secretType = ExternalSecretType.fromValue(workingProvider)
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
                            ExternalSecretProviderDropdown(
                                selectedProvider = workingProvider,
                                onProviderSelected = { workingProvider = it },
                            )

                            val helpUrl = externalSecretHelpUrl(workingProvider)
                            if (helpUrl != null) {
                                DocsHelpButton(
                                    url = helpUrl,
                                    tooltip = "Secret creation documentation",
                                    tooltipTag = "env-provider-help-btn",
                                    tag = "env-provider-help-btn",
                                    size = 28.dp,
                                    tint = ClapiColors.Primary,
                                )
                            }
                        }

                        var quickAddMatchState by remember { mutableStateOf("—") }
                        var quickAddErrorMessage by remember { mutableStateOf<String?>(null) }
                        var quickAddRefreshTrigger by remember { mutableStateOf(0) }

                        val s = state.settings
                        LaunchedEffect(
                            secretType,
                            quickAddSecPath,
                            quickAddSecVault,
                            quickAddSecTargetField,
                            quickAddRefreshTrigger,
                            s.keeperConfigJson,
                            s.awsAccessKeyId,
                            s.awsSecretAccessKey,
                            s.azureVaultName,
                            s.azureTenantId,
                            s.azureClientId,
                            s.azureClientSecret,
                            s.gcpProjectId,
                            s.gcpAccessToken,
                            s.gcpServiceAccountKey,
                            s.gcpAuthMethod,
                            s.gcpAdcFilePath,
                            s.vaultServerUrl,
                            s.vaultToken,
                        ) {
                            val trimmedPath = quickAddSecPath.trim()
                            val trimmedTarget = quickAddSecTargetField.trim()
                            quickAddErrorMessage = null

                            when (secretType) {
                                ExternalSecretType.KEEPASSXC -> {
                                    if (trimmedPath.isBlank()) {
                                        quickAddMatchState = "—"
                                        return@LaunchedEffect
                                    }
                                    quickAddMatchState = "Checking..."
                                    delay(350)
                                    try {
                                        val client = createKeePassXcClient()
                                        val count = client.countMatchingLogins(trimmedPath)
                                        quickAddMatchState = when {
                                            count == 0 -> "✖ 0 found"
                                            count == 1 -> "✔ 1 match"
                                            else -> "✔ $count matches"
                                        }
                                    } catch (e: Exception) {
                                        val msg = e.message.orEmpty()
                                        quickAddMatchState = when {
                                            msg.contains("locked", ignoreCase = true) -> "Locked"
                                            msg.contains("connect", ignoreCase = true) || msg.contains("proxy", ignoreCase = true) -> "Closed"
                                            msg.contains("denied", ignoreCase = true) -> "Unassociated"
                                            else -> {
                                                quickAddErrorMessage = msg.ifBlank { e.toString() }
                                                "Error"
                                            }
                                        }
                                    }
                                }
                                ExternalSecretType.KEEPER_SECURITY_CLOUD -> {
                                    val configJson = s.keeperConfigJson
                                    if (configJson.isBlank()) {
                                        quickAddMatchState = "Not Connected"
                                        return@LaunchedEffect
                                    }
                                    if (trimmedPath.isBlank()) {
                                        quickAddMatchState = "—"
                                        return@LaunchedEffect
                                    }
                                    quickAddMatchState = "Checking..."
                                    delay(350)
                                    try {
                                        val client = createKeeperClient(configJson)
                                        val records = client.getRecords()
                                        val matched = KeeperRecordFilter.filterRecords(records, trimmedPath)
                                        if (matched.isEmpty()) {
                                            quickAddMatchState = "✖ 0 found"
                                        } else {
                                            val target = trimmedTarget.ifBlank { "password" }
                                            val withField = matched.filter {
                                                KeeperRecordFilter.extractTargetFieldValue(it, target) != null
                                            }
                                            quickAddMatchState = when {
                                                withField.isEmpty() -> "✖ Field not found"
                                                withField.size == 1 -> "✔ 1 match"
                                                else -> "✔ ${withField.size} matches"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        quickAddMatchState = "Error"
                                        quickAddErrorMessage = e.message?.ifBlank { e.toString() } ?: e.toString()
                                    }
                                }
                                ExternalSecretType.AWS_SECRETS_MANAGER -> {
                                    if (s.awsAccessKeyId.isBlank() || s.awsSecretAccessKey.isBlank()) {
                                        quickAddMatchState = "Not Connected"
                                        return@LaunchedEffect
                                    }
                                    if (trimmedPath.isBlank()) {
                                        quickAddMatchState = "—"
                                        return@LaunchedEffect
                                    }
                                    quickAddMatchState = "Checking..."
                                    delay(350)
                                    try {
                                        val secretData = KtorAwsSecretsManagerClient.Shared.fetchSecret(
                                            secretId = trimmedPath,
                                            accessKey = s.awsAccessKeyId,
                                            secretKey = s.awsSecretAccessKey,
                                            region = s.awsRegion.ifBlank { "us-east-1" },
                                            sessionToken = s.awsSessionToken.ifBlank { null },
                                            endpointUrl = s.awsEndpointUrl.ifBlank { null },
                                            versionStage = s.awsVersionStage.ifBlank { "AWSCURRENT" },
                                        )
                                        if (trimmedTarget.isNotBlank()) {
                                            val hasField = secretData.containsKey(trimmedTarget) || secretData.containsKey(trimmedTarget.lowercase())
                                            quickAddMatchState = if (hasField) "✔ 1 match" else "✖ Field not found"
                                        } else {
                                            quickAddMatchState = "✔ 1 match"
                                        }
                                    } catch (e: Exception) {
                                        val msg = e.message.orEmpty()
                                        quickAddMatchState = if (msg.contains("ResourceNotFound", ignoreCase = true) || msg.contains("404") || msg.contains("not found", ignoreCase = true)) {
                                            "✖ 0 found"
                                        } else {
                                            quickAddErrorMessage = msg.ifBlank { e.toString() }
                                            "Error"
                                        }
                                    }
                                }
                                ExternalSecretType.AZURE_KEY_VAULT -> {
                                    val effectiveVault = quickAddSecVault.ifBlank { s.azureVaultName }.trim()
                                    if (effectiveVault.isBlank() || s.azureTenantId.isBlank() || s.azureClientId.isBlank() || s.azureClientSecret.isBlank()) {
                                        quickAddMatchState = "Not Connected"
                                        return@LaunchedEffect
                                    }
                                    if (trimmedPath.isBlank()) {
                                        quickAddMatchState = "—"
                                        return@LaunchedEffect
                                    }
                                    quickAddMatchState = "Checking..."
                                    delay(350)
                                    try {
                                        val config = AzureKeyVaultConfig(
                                            vaultName = effectiveVault,
                                            secretName = trimmedPath,
                                            tenantId = s.azureTenantId,
                                            clientId = s.azureClientId,
                                            clientSecret = s.azureClientSecret,
                                            authorityHost = s.azureAuthorityHost.ifBlank { "https://login.microsoftonline.com" },
                                            apiVersion = s.azureApiVersion.ifBlank { "7.4" },
                                        )
                                        val secretData = KtorAzureKeyVaultClient.Shared.fetchSecret(config)
                                        if (trimmedTarget.isNotBlank()) {
                                            val hasField = secretData.containsKey(trimmedTarget) || secretData.containsKey(trimmedTarget.lowercase())
                                            quickAddMatchState = if (hasField) "✔ 1 match" else "✖ Field not found"
                                        } else {
                                            quickAddMatchState = "✔ 1 match"
                                        }
                                    } catch (e: Exception) {
                                        val msg = e.message.orEmpty()
                                        quickAddMatchState = if (msg.contains("SecretNotFound", ignoreCase = true) || msg.contains("404") || msg.contains("not found", ignoreCase = true)) {
                                            "✖ 0 found"
                                        } else {
                                            quickAddErrorMessage = msg.ifBlank { e.toString() }
                                            "Error"
                                        }
                                    }
                                }
                                ExternalSecretType.GCP_SECRETS_MANAGER -> {
                                    val hasCreds = s.gcpAccessToken.isNotBlank() || DefaultGcpTokenProvider.hasCredentials(
                                        serviceAccountKey = s.gcpServiceAccountKey,
                                        authMethod = s.gcpAuthMethod,
                                        adcFilePath = s.gcpAdcFilePath,
                                    )
                                    val extractedProject = if (s.gcpServiceAccountKey.isNotBlank()) {
                                        DefaultGcpTokenProvider.extractProjectId(s.gcpServiceAccountKey).orEmpty()
                                    } else ""
                                    val effectiveProject = quickAddSecVault.ifBlank { s.gcpProjectId }.ifBlank { extractedProject }.trim()

                                    if (effectiveProject.isBlank() || !hasCreds) {
                                        quickAddMatchState = "Not Connected"
                                        quickAddErrorMessage = null
                                        return@LaunchedEffect
                                    }
                                    if (trimmedPath.isBlank()) {
                                        quickAddMatchState = "—"
                                        return@LaunchedEffect
                                    }
                                    quickAddMatchState = "Checking..."
                                    delay(350)
                                    try {
                                        val config = GcpSecretManagerConfig(
                                            projectId = effectiveProject,
                                            secretId = trimmedPath,
                                            version = "latest",
                                            accessToken = s.gcpAccessToken,
                                            serviceAccountKey = s.gcpServiceAccountKey,
                                            authMethod = s.gcpAuthMethod,
                                            adcFilePath = s.gcpAdcFilePath,
                                            endpointUrl = s.gcpEndpointUrl.ifBlank { "https://secretmanager.googleapis.com" },
                                        )
                                        val secretData = KtorGcpSecretManagerClient.Shared.fetchSecret(config)
                                        if (trimmedTarget.isNotBlank()) {
                                            val hasField = secretData.containsKey(trimmedTarget) || secretData.containsKey(trimmedTarget.lowercase())
                                            quickAddMatchState = if (hasField) "✔ 1 match" else "✖ Field not found"
                                        } else {
                                            quickAddMatchState = "✔ 1 match"
                                        }
                                    } catch (e: Exception) {
                                        val msg = e.message.orEmpty()
                                        quickAddMatchState = if (msg.contains("SecretNotFound", ignoreCase = true) || msg.contains("404") || msg.contains("not found", ignoreCase = true)) {
                                            "✖ 0 found"
                                        } else {
                                            quickAddErrorMessage = msg.ifBlank { e.toString() }
                                            "Error"
                                        }
                                    }
                                }
                                else -> {
                                    if (s.vaultServerUrl.isBlank() || s.vaultToken.isBlank()) {
                                        quickAddMatchState = "Not Connected"
                                        return@LaunchedEffect
                                    }
                                    if (trimmedPath.isBlank()) {
                                        quickAddMatchState = "—"
                                        return@LaunchedEffect
                                    }
                                    quickAddMatchState = "Checking..."
                                    delay(350)
                                    try {
                                        val secretData = KtorHashiCorpVaultClient.Shared.fetchSecret(
                                            vaultUrl = s.vaultServerUrl,
                                            token = s.vaultToken,
                                            secretPath = trimmedPath,
                                            mountPath = s.vaultMountPath.ifBlank { "secret" },
                                            namespace = s.vaultNamespace,
                                        )
                                        quickAddMatchState = if (secretData.isNotEmpty()) "✔ 1 match" else "✖ 0 found"
                                    } catch (e: Exception) {
                                        val msg = e.message.orEmpty()
                                        quickAddMatchState = if (msg.contains("404") || msg.contains("not found", ignoreCase = true)) {
                                            "✖ 0 found"
                                        } else {
                                            quickAddErrorMessage = msg.ifBlank { e.toString() }
                                            "Error"
                                        }
                                    }
                                }
                            }
                        }

                        fun addQuickSecret() {
                            val trimmedName = quickAddSecName.trim()
                            val trimmedPath = quickAddSecPath.trim()
                            val trimmedVault = quickAddSecVault.trim()
                            val trimmedDesc = quickAddSecDesc.trim()
                            val effectiveName = trimmedName.ifBlank {
                                trimmedPath.substringAfterLast('/').substringAfterLast(':').ifBlank { "secret" }
                            }
                            if (effectiveName.isNotEmpty() && trimmedPath.isNotEmpty()) {
                                val defaultTarget = when (secretType) {
                                    ExternalSecretType.KEEPASSXC, ExternalSecretType.KEEPER_SECURITY_CLOUD -> "password"
                                    else -> ""
                                }
                                val sec = when (secretType) {
                                    ExternalSecretType.KEEPASSXC -> MutableExternalSecret(
                                        name = effectiveName,
                                        url = trimmedPath,
                                        path = trimmedPath,
                                        targetField = quickAddSecTargetField.trim().ifBlank { defaultTarget },
                                        description = trimmedDesc,
                                    )
                                    ExternalSecretType.AZURE_KEY_VAULT, ExternalSecretType.GCP_SECRETS_MANAGER -> MutableExternalSecret(
                                        name = effectiveName,
                                        path = trimmedPath,
                                        url = trimmedVault,
                                        targetField = quickAddSecTargetField.trim().ifBlank { defaultTarget },
                                        description = trimmedDesc,
                                    )
                                    else -> MutableExternalSecret(
                                        name = effectiveName,
                                        path = trimmedPath,
                                        targetField = quickAddSecTargetField.trim().ifBlank { defaultTarget },
                                        description = trimmedDesc,
                                    )
                                }
                                workingSecrets.add(0, sec)
                                quickAddSecName = ""
                                quickAddSecPath = ""
                                quickAddSecVault = ""
                                quickAddSecTargetField = ""
                                quickAddSecDesc = ""
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(ClapiColors.SurfaceHigh)
                                .padding(start = 8.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Spacer(Modifier.size(24.dp))
                            when (secretType) {
                                ExternalSecretType.KEEPASSXC -> {
                                    TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                    TableHeader("ENTRY URL", Modifier.weight(1.3f))
                                    TableHeader("TARGET FIELD", Modifier.weight(0.9f))
                                    TableHeader("STATUS", Modifier.weight(1.0f))
                                    TableHeader("FILTERS", Modifier.weight(0.9f))
                                    TableHeader("DESCRIPTION", Modifier.weight(1.0f))
                                }
                                ExternalSecretType.KEEPER_SECURITY_CLOUD -> {
                                    TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                    TableHeader("RECORD / TITLE", Modifier.weight(1.3f))
                                    TableHeader("TARGET FIELD", Modifier.weight(0.9f))
                                    TableHeader("STATUS", Modifier.weight(1.0f))
                                    TableHeader("FILTERS", Modifier.weight(0.9f))
                                    TableHeader("DESCRIPTION", Modifier.weight(1.0f))
                                }
                                ExternalSecretType.AWS_SECRETS_MANAGER -> {
                                    TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                    TableHeader("SECRET ID / NAME", Modifier.weight(1.3f))
                                    TableHeader("TARGET FIELD", Modifier.weight(0.9f))
                                    TableHeader("STATUS", Modifier.weight(1.0f))
                                    TableHeader("FILTERS", Modifier.weight(0.9f))
                                    TableHeader("DESCRIPTION", Modifier.weight(1.0f))
                                }
                                ExternalSecretType.AZURE_KEY_VAULT -> {
                                    TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                    TableHeader("SECRET NAME", Modifier.weight(1.2f))
                                    TableHeader("VAULT OVERRIDE", Modifier.weight(1.0f))
                                    TableHeader("TARGET FIELD", Modifier.weight(0.8f))
                                    TableHeader("STATUS", Modifier.weight(1.0f))
                                    TableHeader("FILTERS", Modifier.weight(0.8f))
                                    TableHeader("DESCRIPTION", Modifier.weight(0.9f))
                                }
                                ExternalSecretType.GCP_SECRETS_MANAGER -> {
                                    TableHeader(Strings.t("name"), Modifier.weight(1.0f))
                                    TableHeader("SECRET ID", Modifier.weight(1.2f))
                                    TableHeader("PROJECT OVERRIDE", Modifier.weight(1.0f))
                                    TableHeader("TARGET FIELD", Modifier.weight(0.8f))
                                    TableHeader("STATUS", Modifier.weight(1.0f))
                                    TableHeader("FILTERS", Modifier.weight(0.8f))
                                    TableHeader("DESCRIPTION", Modifier.weight(0.9f))
                                }
                                else -> {
                                    TableHeader(Strings.t("name"), Modifier.weight(1.1f))
                                    TableHeader("PATH", Modifier.weight(1.4f))
                                    TableHeader("STATUS", Modifier.weight(1.0f))
                                    TableHeader("DESCRIPTION", Modifier.weight(1.3f))
                                }
                            }
                            Spacer(Modifier.size(24.dp))
                        }

                        // Quick-add row on top for external secrets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border)
                                .padding(start = 8.dp, end = 18.dp, top = 5.dp, bottom = 5.dp)
                                .testTag("env-secret-quick-add-row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Spacer(Modifier.size(24.dp))
                            when (secretType) {
                                ExternalSecretType.KEEPASSXC -> {
                                    EnvQuickAddTextField(
                                        value = quickAddSecName,
                                        onValueChange = { quickAddSecName = it },
                                        placeholder = Strings.t("name"),
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-name",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecPath,
                                        onValueChange = { quickAddSecPath = it },
                                        placeholder = "https://entry.url",
                                        modifier = Modifier.weight(1.3f),
                                        tag = "env-quick-add-secret-path",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecTargetField,
                                        onValueChange = { quickAddSecTargetField = it },
                                        placeholder = "password",
                                        modifier = Modifier.weight(0.9f),
                                        tag = "env-quick-add-secret-target",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvSecretStatusCell(
                                        status = quickAddMatchState,
                                        errorMessage = quickAddErrorMessage,
                                        modifier = Modifier.weight(1.0f),
                                        statusTag = "env-quick-add-secret-status",
                                        refreshTag = "env-quick-add-secret-refresh-status",
                                        onRefresh = { quickAddRefreshTrigger++ },
                                    )
                                    Spacer(Modifier.weight(0.9f))
                                    EnvQuickAddTextField(
                                        value = quickAddSecDesc,
                                        onValueChange = { quickAddSecDesc = it },
                                        placeholder = "Description (optional)",
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-desc",
                                        onEnter = { addQuickSecret() },
                                    )
                                }
                                ExternalSecretType.KEEPER_SECURITY_CLOUD -> {
                                    EnvQuickAddTextField(
                                        value = quickAddSecName,
                                        onValueChange = { quickAddSecName = it },
                                        placeholder = Strings.t("name"),
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-name",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecPath,
                                        onValueChange = { quickAddSecPath = it },
                                        placeholder = "Record Title or UID",
                                        modifier = Modifier.weight(1.3f),
                                        tag = "env-quick-add-secret-path",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecTargetField,
                                        onValueChange = { quickAddSecTargetField = it },
                                        placeholder = "password",
                                        modifier = Modifier.weight(0.9f),
                                        tag = "env-quick-add-secret-target",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvSecretStatusCell(
                                        status = quickAddMatchState,
                                        errorMessage = quickAddErrorMessage,
                                        modifier = Modifier.weight(1.0f),
                                        statusTag = "env-quick-add-secret-status",
                                        refreshTag = "env-quick-add-secret-refresh-status",
                                        onRefresh = { quickAddRefreshTrigger++ },
                                    )
                                    Spacer(Modifier.weight(0.9f))
                                    EnvQuickAddTextField(
                                        value = quickAddSecDesc,
                                        onValueChange = { quickAddSecDesc = it },
                                        placeholder = "Description (optional)",
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-desc",
                                        onEnter = { addQuickSecret() },
                                    )
                                }
                                ExternalSecretType.AWS_SECRETS_MANAGER -> {
                                    EnvQuickAddTextField(
                                        value = quickAddSecName,
                                        onValueChange = { quickAddSecName = it },
                                        placeholder = Strings.t("name"),
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-name",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecPath,
                                        onValueChange = { quickAddSecPath = it },
                                        placeholder = "Secret ID / Name",
                                        modifier = Modifier.weight(1.3f),
                                        tag = "env-quick-add-secret-path",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecTargetField,
                                        onValueChange = { quickAddSecTargetField = it },
                                        placeholder = "Target Field",
                                        modifier = Modifier.weight(0.9f),
                                        tag = "env-quick-add-secret-target",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvSecretStatusCell(
                                        status = quickAddMatchState,
                                        errorMessage = quickAddErrorMessage,
                                        modifier = Modifier.weight(1.0f),
                                        statusTag = "env-quick-add-secret-status",
                                        refreshTag = "env-quick-add-secret-refresh-status",
                                        onRefresh = { quickAddRefreshTrigger++ },
                                    )
                                    Spacer(Modifier.weight(0.9f))
                                    EnvQuickAddTextField(
                                        value = quickAddSecDesc,
                                        onValueChange = { quickAddSecDesc = it },
                                        placeholder = "Description (optional)",
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-desc",
                                        onEnter = { addQuickSecret() },
                                    )
                                }
                                ExternalSecretType.AZURE_KEY_VAULT -> {
                                    EnvQuickAddTextField(
                                        value = quickAddSecName,
                                        onValueChange = { quickAddSecName = it },
                                        placeholder = Strings.t("name"),
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-name",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecPath,
                                        onValueChange = { quickAddSecPath = it },
                                        placeholder = "Secret Name",
                                        modifier = Modifier.weight(1.2f),
                                        tag = "env-quick-add-secret-path",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecVault,
                                        onValueChange = { quickAddSecVault = it },
                                        placeholder = "Vault Override (optional)",
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-vault",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecTargetField,
                                        onValueChange = { quickAddSecTargetField = it },
                                        placeholder = "Target Field",
                                        modifier = Modifier.weight(0.8f),
                                        tag = "env-quick-add-secret-target",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvSecretStatusCell(
                                        status = quickAddMatchState,
                                        errorMessage = quickAddErrorMessage,
                                        modifier = Modifier.weight(1.0f),
                                        statusTag = "env-quick-add-secret-status",
                                        refreshTag = "env-quick-add-secret-refresh-status",
                                        onRefresh = { quickAddRefreshTrigger++ },
                                    )
                                    Spacer(Modifier.weight(0.8f))
                                    EnvQuickAddTextField(
                                        value = quickAddSecDesc,
                                        onValueChange = { quickAddSecDesc = it },
                                        placeholder = "Description (optional)",
                                        modifier = Modifier.weight(0.9f),
                                        tag = "env-quick-add-secret-desc",
                                        onEnter = { addQuickSecret() },
                                    )
                                }
                                ExternalSecretType.GCP_SECRETS_MANAGER -> {
                                    EnvQuickAddTextField(
                                        value = quickAddSecName,
                                        onValueChange = { quickAddSecName = it },
                                        placeholder = Strings.t("name"),
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-name",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecPath,
                                        onValueChange = { quickAddSecPath = it },
                                        placeholder = "Secret ID",
                                        modifier = Modifier.weight(1.2f),
                                        tag = "env-quick-add-secret-path",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecVault,
                                        onValueChange = { quickAddSecVault = it },
                                        placeholder = "Project Override (optional)",
                                        modifier = Modifier.weight(1.0f),
                                        tag = "env-quick-add-secret-vault",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecTargetField,
                                        onValueChange = { quickAddSecTargetField = it },
                                        placeholder = "Target Field",
                                        modifier = Modifier.weight(0.8f),
                                        tag = "env-quick-add-secret-target",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvSecretStatusCell(
                                        status = quickAddMatchState,
                                        errorMessage = quickAddErrorMessage,
                                        modifier = Modifier.weight(1.0f),
                                        statusTag = "env-quick-add-secret-status",
                                        refreshTag = "env-quick-add-secret-refresh-status",
                                        onRefresh = { quickAddRefreshTrigger++ },
                                    )
                                    Spacer(Modifier.weight(0.8f))
                                    EnvQuickAddTextField(
                                        value = quickAddSecDesc,
                                        onValueChange = { quickAddSecDesc = it },
                                        placeholder = "Description (optional)",
                                        modifier = Modifier.weight(0.9f),
                                        tag = "env-quick-add-secret-desc",
                                        onEnter = { addQuickSecret() },
                                    )
                                }
                                else -> {
                                    EnvQuickAddTextField(
                                        value = quickAddSecName,
                                        onValueChange = { quickAddSecName = it },
                                        placeholder = Strings.t("name"),
                                        modifier = Modifier.weight(1.1f),
                                        tag = "env-quick-add-secret-name",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecPath,
                                        onValueChange = { quickAddSecPath = it },
                                        placeholder = "Path",
                                        modifier = Modifier.weight(1.4f),
                                        tag = "env-quick-add-secret-path",
                                        onEnter = { addQuickSecret() },
                                    )
                                    EnvSecretStatusCell(
                                        status = quickAddMatchState,
                                        errorMessage = quickAddErrorMessage,
                                        modifier = Modifier.weight(1.0f),
                                        statusTag = "env-quick-add-secret-status",
                                        refreshTag = "env-quick-add-secret-refresh-status",
                                        onRefresh = { quickAddRefreshTrigger++ },
                                    )
                                    EnvQuickAddTextField(
                                        value = quickAddSecDesc,
                                        onValueChange = { quickAddSecDesc = it },
                                        placeholder = "Description (optional)",
                                        modifier = Modifier.weight(1.3f),
                                        tag = "env-quick-add-secret-desc",
                                        onEnter = { addQuickSecret() },
                                    )
                                }
                            }
                            EnvQuickAddButton(
                                onClick = { addQuickSecret() },
                                tooltip = Strings.addSecret,
                                tag = "env-quick-add-secret-button",
                            )
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
                                    when (secretType) {
                                        ExternalSecretType.KEEPASSXC -> EnvKeePassXcSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        ExternalSecretType.KEEPER_SECURITY_CLOUD -> EnvKeeperSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        ExternalSecretType.AWS_SECRETS_MANAGER -> EnvAwsSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        ExternalSecretType.AZURE_KEY_VAULT -> EnvAzureSecretRow(
                                            secret = secret,
                                            index = sIdx,
                                            workingVars = workingVars,
                                            state = state,
                                            onDelete = { workingSecrets.removeAt(sIdx) },
                                        )
                                        ExternalSecretType.GCP_SECRETS_MANAGER -> EnvGcpSecretRow(
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
                                                Strings.t("no_external_secrets_hint"),
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
                                .handPointerHover()
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
                            Text(Strings.addSecret, color = ClapiColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                                .handPointerHover()
                                .clickable { requestClose() }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                .testTag("env-cancel-button"),
                        ) { Text(Strings.cancel, color = ClapiColors.OnSurface, fontSize = 13.sp) }

                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.Primary)
                                .handPointerHover()
                                .clickable { saveEnvironment(closeDialog = true) }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                .testTag("env-save-button"),
                        ) { Text(Strings.save, color = ClapiColors.OnPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
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

    if (useDialogWindow) {
        Dialog(
            onDismissRequest = ::requestClose,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            content = dialogBody,
        )
    } else {
        dialogBody()
    }

    if (showUnsavedConfirm) {
        DirtyCloseDialog(
            title = Strings.t("unsaved_changes"),
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

