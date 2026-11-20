package io.github.makeforge.clapi.ui.shared.components.variable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.network.VariableResolver
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.ui.shared.components.DirtyCloseDialog
import io.github.makeforge.clapi.ui.shared.components.VariableAwareTextField
import io.github.makeforge.clapi.ui.shared.components.VariableHighlightColor
import io.github.makeforge.clapi.ui.shared.components.autoFocus
import io.github.makeforge.clapi.ui.shared.components.clampPopupOffsetToViewportF
import io.github.makeforge.clapi.ui.shared.components.draggableNoSlop
import io.github.makeforge.clapi.ui.shared.components.normalizeVariableDisplayName
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

data class VariableSourceInfo(
    val cleanName: String,
    val environmentName: String?,
    val environmentValue: String?,
    val hasEnvironmentValue: Boolean,
    val folderName: String?,
    val folderValue: String?,
    val hasFolderValue: Boolean,
    val currentResolvedValue: String?,
    val isDynamic: Boolean = false,
)

fun resolveVariableSourceInfo(
    variableName: String,
    state: AppState,
    tab: RequestTabState? = state.activeTab,
): VariableSourceInfo {
    val rawCleanName = normalizeVariableDisplayName(variableName)
    val parsedToken = VariableResolver.parseVariableToken(rawCleanName)
    val cleanName = parsedToken.name.ifBlank { rawCleanName }
    val env = state.selectedEnvironment
    val envName = env?.name
    val envEffectiveMap = env?.toVariableMap(state.environments).orEmpty()
    val envVar = env?.variables?.firstOrNull { it.key == cleanName && it.enabled }
    val envValue = when {
        envVar != null && envVar.isDynamic -> "[Dynamic Script]"
        envVar != null -> envVar.value
        else -> envEffectiveMap[cleanName]
    }
    val hasEnvValue = envVar != null || cleanName in envEffectiveMap

    val targetTab = tab ?: state.activeTab
    var folderName: String? = null
    var folderValue: String? = null
    var hasFolderValue = false

    if (targetTab != null) {
        if (targetTab.isFolderTab) {
            folderName = targetTab.name
            val fVar = targetTab.variables.firstOrNull { it.key == cleanName && it.enabled }
            if (fVar != null) {
                folderValue = if (fVar.isDynamic) "[Dynamic Script]" else fVar.value
                hasFolderValue = true
            }
        } else {
            val ancestors = state.getAncestorFolders(targetTab)
            val currentFolderNode = ancestors.lastOrNull { it.isFolder }
            if (currentFolderNode != null) {
                val definingFolderNode = ancestors.asReversed().firstOrNull { fNode ->
                    val openTab = state.openTabs.firstOrNull { it.id == fNode.id && it.isFolderTab }
                    if (openTab != null) {
                        openTab.variables.any { it.key == cleanName && it.enabled }
                    } else {
                        fNode.variableEntries?.any { it.key == cleanName && it.enabled } == true
                    }
                } ?: currentFolderNode

                folderName = definingFolderNode.name
                val openTab = state.openTabs.firstOrNull { it.id == definingFolderNode.id && it.isFolderTab }
                val fVarValue = if (openTab != null) {
                    val v = openTab.variables.firstOrNull { it.key == cleanName && it.enabled }
                    if (v != null) (if (v.isDynamic) "[Dynamic Script]" else v.value) else null
                } else {
                    val v = definingFolderNode.variableEntries?.firstOrNull { it.key == cleanName && it.enabled }
                    if (v != null) (if (v.isDynamic) "[Dynamic Script]" else v.value) else null
                }
                if (fVarValue != null) {
                    folderValue = fVarValue
                    hasFolderValue = true
                }
            }
        }
    }

    // Current value: environment always takes precedence over folder variable
    val currentResolvedValue = if (hasEnvValue) {
        envValue
    } else if (hasFolderValue) {
        folderValue
    } else {
        state.activeVariableLayers(targetTab).firstNotNullOfOrNull { it[cleanName] }
            ?: state.activeDynamicVariables(targetTab).firstOrNull { it.name == cleanName }?.let { "[Dynamic Script]" }
            ?: VariableResolver.resolveDynamicToken(rawCleanName)
    }

    val isDynamic = (envVar != null && envVar.isDynamic) ||
        (hasFolderValue && folderValue == "[Dynamic Script]") ||
        state.activeDynamicVariables(targetTab).any { it.name == cleanName } ||
        VariableResolver.isDynamicToken(cleanName) ||
        VariableResolver.isDynamicToken(rawCleanName) ||
        currentResolvedValue == "[Dynamic Script]"

    return VariableSourceInfo(
        cleanName = cleanName,
        environmentName = envName,
        environmentValue = envValue,
        hasEnvironmentValue = hasEnvValue,
        folderName = folderName,
        folderValue = folderValue,
        hasFolderValue = hasFolderValue,
        currentResolvedValue = currentResolvedValue,
        isDynamic = isDynamic,
    )
}

/**
 * Floating popup for inline environment variable editing.
 *
 * Displays:
 *  - Current resolved value (environment takes precedence over folder variable)
 *  - Source values: Environment and Current Folder
 *  - Edit field to save/update the value in the active environment
 */
@Composable
fun VariableEditorPopup(
    variableName: String,
    state: AppState,
    onDismiss: () -> Unit,
    tab: RequestTabState? = state.activeTab,
    initialOffset: IntOffset = IntOffset(40, 32),
    onPositionChanged: (IntOffset) -> Unit = {},
    onApply: ((String) -> Unit)? = null,
) {
    val cleanName = remember(variableName) { normalizeVariableDisplayName(variableName) }
    val info = resolveVariableSourceInfo(cleanName, state, tab)
    val env = state.selectedEnvironment

    val parsedToken = remember(cleanName) { VariableResolver.parseVariableToken(cleanName) }
    val baseName = remember(parsedToken, cleanName) { parsedToken.name.ifBlank { cleanName } }

    val builtInHandler = remember(cleanName, baseName) {
        VariableResolver.dynamicVariables.firstOrNull { it.matches(cleanName) || it.matches(baseName) }
    }
    val userDyn = remember(baseName, state, tab) {
        state.activeDynamicVariables(tab).firstOrNull { it.name == baseName }
    }

    val declaredParams = remember(builtInHandler, userDyn) {
        builtInHandler?.parameters?.takeIf { it.isNotEmpty() } ?: userDyn?.parameters.orEmpty()
    }

    val isParameterizedDynamic = remember(info.isDynamic, declaredParams) {
        info.isDynamic && declaredParams.isNotEmpty()
    }

    val paramValues = remember(cleanName, declaredParams) {
        mutableStateMapOf<String, String>().apply {
            declaredParams.forEachIndexed { index, param ->
                val argVal = parsedToken.args.getOrNull(index)?.toString()
                put(param.name, argVal ?: param.defaultValue ?: "")
            }
        }
    }

    val currentArgs = declaredParams.map { param ->
        paramValues[param.name] ?: param.defaultValue ?: ""
    }
    val prefix = if (cleanName.startsWith("$")) "$" else ""
    val nameWithoutDollar = baseName.removePrefix("$")
    val updatedToken = "$prefix$nameWithoutDollar(${currentArgs.joinToString(", ")})"
    val updatedExpression = "{{$updatedToken}}"

    val previewResolvedValue by remember(updatedToken, paramValues.values.toList()) {
        derivedStateOf {
            try {
                if (builtInHandler != null) {
                    builtInHandler.resolve(updatedToken)
                } else if (userDyn != null) {
                    val scriptContext = VariableResolver.buildScriptContext(userDyn, currentArgs, null)
                    val res = VariableResolver.defaultDynamicEngine.evaluateDynamicVariable(userDyn.script, scriptContext)
                    if (res.success) res.returnValue else null
                } else {
                    VariableResolver.resolveDynamicToken(updatedToken)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    var editValue by remember(cleanName, info.isDynamic) {
        val initial = if (info.isDynamic) {
            info.currentResolvedValue ?: "[Dynamic Script]"
        } else {
            info.environmentValue ?: info.folderValue ?: ""
        }
        mutableStateOf(TextFieldValue(initial))
    }
    var baselineValue by remember(cleanName, info.isDynamic) {
        mutableStateOf(editValue.text)
    }
    var showUnsavedConfirm by remember { mutableStateOf(false) }

    fun isDirty(): Boolean = !info.isDynamic && editValue.text != baselineValue

    fun handleSave(closeDialog: Boolean = false) {
        if (isParameterizedDynamic) {
            onApply?.invoke(updatedToken)
            if (closeDialog) {
                onDismiss()
            }
            return
        }
        if (info.isDynamic) {
            if (closeDialog) {
                onDismiss()
            }
            return
        }
        val targetEnv = env ?: run {
            val newEnv = EnvState("Default")
            state.environments.add(newEnv)
            state.selectedEnvIndex = state.environments.size - 1
            newEnv
        }
        val existing = targetEnv.variables.firstOrNull { it.key == cleanName }
        if (existing != null) {
            existing.value = editValue.text
            existing.enabled = true
        } else {
            targetEnv.variables.add(MutableKeyValue(key = cleanName, value = editValue.text, enabled = true))
        }
        baselineValue = editValue.text
        if (closeDialog) {
            onDismiss()
        }
    }

    fun requestDismiss() {
        if (isDirty()) {
            showUnsavedConfirm = true
        } else {
            onDismiss()
        }
    }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    var popupOffsetX by remember(cleanName) { mutableStateOf(initialOffset.x.toFloat()) }
    var popupOffsetY by remember(cleanName) { mutableStateOf(initialOffset.y.toFloat()) }

    fun movePopupBy(dx: Float, dy: Float) {
        val rawX = popupOffsetX + dx
        val rawY = popupOffsetY + dy
        val (cx, cy) = clampPopupOffsetToViewportF(
            x = rawX,
            y = rawY,
            popupSize = popupSize,
            viewportSize = viewportSize,
        )
        popupOffsetX = cx
        popupOffsetY = cy
        onPositionChanged(IntOffset(cx.roundToInt(), cy.roundToInt()))
    }

    Dialog(onDismissRequest = { requestDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .onSizeChanged { size ->
                    viewportSize = size
                    val (cx, cy) = clampPopupOffsetToViewportF(
                        x = popupOffsetX, y = popupOffsetY,
                        popupSize = popupSize, viewportSize = size,
                    )
                    popupOffsetX = cx
                    popupOffsetY = cy
                }
                .pointerInput(Unit) { detectTapGestures { requestDismiss() } }
                .testTag("variable-popup-backdrop"),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(popupOffsetX.roundToInt(), popupOffsetY.roundToInt()) }
                    .widthIn(min = 340.dp, max = 520.dp)
                    .shadow(16.dp, RoundedCornerShape(12.dp), clip = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClapiColors.Surface)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                    .onSizeChanged { size ->
                        popupSize = size
                        val (cx, cy) = clampPopupOffsetToViewportF(
                            x = popupOffsetX, y = popupOffsetY,
                            popupSize = size, viewportSize = viewportSize,
                        )
                        popupOffsetX = cx
                        popupOffsetY = cy
                    }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .hotkeys {
                        on(AppHotkeys.Save) {
                            if (isParameterizedDynamic) {
                                onApply?.invoke(updatedToken)
                                onDismiss()
                            } else if (!info.isDynamic) {
                                handleSave(closeDialog = false)
                            }
                        }
                        on(AppHotkeys.Escape) {
                            requestDismiss()
                        }
                    }
                    .padding(16.dp)
                    .testTag("variable-editor-popup"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .draggableNoSlop(key = cleanName) { dx, dy -> movePopupBy(dx, dy) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("variable-popup-title-bar"),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${Strings.t("variable")}: ${if (isParameterizedDynamic) updatedToken else cleanName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VariableHighlightColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${Strings.environments}: ${info.environmentName ?: Strings.t("none")}",
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { requestDismiss() },
                            modifier = Modifier.size(24.dp).testTag("variable-popup-close"),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = Strings.t("close_variable_editor"),
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceVariant)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Strings.t("current_value"),
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            val sourceBadge = when {
                                info.hasEnvironmentValue -> "Environment"
                                info.hasFolderValue -> "Folder"
                                else -> null
                            }
                            if (sourceBadge != null) {
                                Text(
                                    text = sourceBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClapiColors.Primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ClapiColors.Primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("variable-popup-active-source"),
                                )
                            }
                        }
                        Text(
                            text = (if (isParameterizedDynamic) previewResolvedValue else info.currentResolvedValue) ?: Strings.t("not_defined"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if ((if (isParameterizedDynamic) previewResolvedValue else info.currentResolvedValue) == null) ClapiColors.Error else ClapiColors.OnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("variable-popup-current-value"),
                        )
                        if (isParameterizedDynamic) {
                            Text(
                                text = updatedExpression,
                                fontSize = 11.sp,
                                fontFamily = CodeFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = VariableHighlightColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("variable-popup-preview-expression"),
                            )
                        }

                        HorizontalDivider(color = ClapiColors.Border.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("variable-popup-env-row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val envLabel = if (info.environmentName != null) "Environment (${info.environmentName})" else "Environment"
                            Text(
                                text = envLabel,
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                            )
                            Text(
                                text = if (info.hasEnvironmentValue) info.environmentValue!! else Strings.t("not_defined"),
                                fontSize = 11.sp,
                                fontFamily = CodeFontFamily,
                                fontWeight = if (info.hasEnvironmentValue) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (info.hasEnvironmentValue) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("variable-popup-env-value"),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("variable-popup-folder-row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val folderLabel = if (info.folderName != null) "Folder (${info.folderName})" else "Folder"
                            Text(
                                text = folderLabel,
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                            )
                            Text(
                                text = if (info.hasFolderValue) info.folderValue!! else if (info.folderName != null) Strings.t("not_defined") else Strings.t("none"),
                                fontSize = 11.sp,
                                fontFamily = CodeFontFamily,
                                fontWeight = if (info.hasFolderValue) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (info.hasFolderValue) ClapiColors.OnSurface else ClapiColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("variable-popup-folder-value"),
                            )
                        }
                    }
                    }

                    HorizontalDivider(color = ClapiColors.Border)

                    if (isParameterizedDynamic) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = Strings.parameters,
                                    fontSize = 11.sp,
                                    color = ClapiColors.OnSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${Strings.t("dynamic")} (editable)",
                                    fontSize = 10.sp,
                                    color = ClapiColors.OnSurfaceDim,
                                )
                            }
                            declaredParams.forEachIndexed { idx, param ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = param.name,
                                        fontSize = 12.sp,
                                        fontFamily = CodeFontFamily,
                                        color = ClapiColors.OnSurfaceVariant,
                                        modifier = Modifier.width(90.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    BasicTextField(
                                        value = paramValues[param.name] ?: "",
                                        onValueChange = { paramValues[param.name] = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = ClapiColors.OnSurface,
                                            fontSize = 12.sp,
                                            fontFamily = CodeFontFamily,
                                        ),
                                        cursorBrush = SolidColor(ClapiColors.Primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ClapiColors.SurfaceContainer)
                                            .border(1.dp, ClapiColors.BorderLight, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .testTag("variable-popup-param-${param.name}")
                                            .testTag("variable-popup-param-$idx"),
                                        decorationBox = { inner ->
                                            if ((paramValues[param.name] ?: "").isEmpty()) {
                                                Text(
                                                    text = param.defaultValue ?: Strings.value,
                                                    color = ClapiColors.OnSurfaceDim,
                                                    fontSize = 12.sp,
                                                    fontFamily = CodeFontFamily,
                                                )
                                            }
                                            inner()
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = Strings.value,
                                    fontSize = 11.sp,
                                    color = ClapiColors.OnSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = if (info.isDynamic) "${Strings.t("dynamic")} (${Strings.t("read_only")})" else "Sets in environment",
                                    fontSize = 10.sp,
                                    color = ClapiColors.OnSurfaceDim,
                                )
                            }
                            VariableAwareTextField(
                                value = editValue.text,
                                onValueChange = { if (!info.isDynamic) editValue = TextFieldValue(it) },
                                readOnly = info.isDynamic,
                                singleLine = true,
                                placeholder = Strings.t("enter_value"),
                                textStyle = TextStyle(
                                    color = if (info.isDynamic) ClapiColors.OnSurfaceDim else ClapiColors.OnSurface,
                                    fontSize = 13.sp,
                                    fontFamily = CodeFontFamily,
                                ),
                                state = state,
                                tab = tab,
                                excludeVariables = setOf(cleanName),
                                selectAllOnStart = !info.isDynamic,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (!info.isDynamic) Modifier.autoFocus() else Modifier)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (info.isDynamic) ClapiColors.SurfaceContainer.copy(alpha = 0.5f) else ClapiColors.SurfaceContainer)
                                    .border(1.dp, ClapiColors.BorderLight, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("variable-popup-value-input"),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = Strings.t("open_in_environments"),
                            fontSize = 12.sp,
                            color = ClapiColors.Primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Primary.copy(alpha = 0.10f))
                                .clickable {
                                    val idx = env?.let { state.environments.indexOf(it) } ?: -1
                                    if (idx >= 0) state.openEnvEdit(idx)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("variable-popup-open-env"),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (info.isDynamic && !isParameterizedDynamic) ClapiColors.Primary else ClapiColors.SurfaceContainer)
                                .border(1.dp, if (info.isDynamic && !isParameterizedDynamic) Color.Transparent else ClapiColors.Border, RoundedCornerShape(8.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("variable-popup-cancel"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (info.isDynamic && !isParameterizedDynamic) Strings.t("close") else Strings.cancel,
                                fontSize = 12.sp,
                                color = if (info.isDynamic && !isParameterizedDynamic) ClapiColors.OnPrimary else ClapiColors.OnSurface,
                                fontWeight = if (info.isDynamic && !isParameterizedDynamic) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                        if (isParameterizedDynamic) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClapiColors.Primary)
                                    .clickable {
                                        onApply?.invoke(updatedToken)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("variable-popup-apply"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = Strings.apply,
                                    fontSize = 12.sp,
                                    color = ClapiColors.OnPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        } else if (!info.isDynamic) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ClapiColors.Primary)
                                    .clickable { handleSave(closeDialog = true) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("variable-popup-save"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = Strings.t("save"),
                                    fontSize = 12.sp,
                                    color = ClapiColors.OnPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnsavedConfirm) {
        DirtyCloseDialog(
            title = "Unsaved Changes",
            message = Strings.t("request_unsaved_changes_message"),
            onSave = {
                handleSave(closeDialog = true)
                showUnsavedConfirm = false
            },
            onDiscard = {
                showUnsavedConfirm = false
                onDismiss()
            },
            onCancel = {
                showUnsavedConfirm = false
            },
        )
    }
}
