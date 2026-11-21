package io.github.makeforge.clapi.ui.shared.components.dialogs.dynamicvar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.core.scripting.RequestContext
import io.github.makeforge.clapi.ui.shared.components.CodeEditor
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.components.SyntaxLanguage
import io.github.makeforge.clapi.ui.shared.components.VariableHighlightColor
import io.github.makeforge.clapi.ui.shared.components.dialogEscape
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_add
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_close
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_code
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_delete
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_help_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_open_in_new
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.insetScrollbar
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
fun DynamicVariableEditorDialog(
    kv: MutableKeyValue,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
    initialRequestContext: RequestContext? = null,
    modifier: Modifier = Modifier,
) {
    DynamicVariableEditorDialog(
        initialKey = kv.key,
        initialScript = kv.dynamicScript,
        initialParameters = kv.dynamicParameters,
        initialDescription = kv.description,
        onSave = { newKey, newScript, newParams, newDesc ->
            kv.key = newKey
            kv.isDynamic = true
            kv.secret = false
            kv.dynamicScript = newScript
            kv.dynamicParameters.clear()
            kv.dynamicParameters.addAll(newParams)
            kv.description = newDesc
            onSaved()
            onDismiss()
        },
        onDismiss = onDismiss,
        initialRequestContext = initialRequestContext,
        modifier = modifier,
    )
}

@Composable
fun DynamicVariableEditorDialog(
    initialKey: String,
    initialScript: String,
    initialParameters: List<DynamicVariableParameter>,
    initialDescription: String = "",
    onSave: (key: String, script: String, parameters: List<DynamicVariableParameter>, description: String) -> Unit,
    onDismiss: () -> Unit,
    initialRequestContext: RequestContext? = null,
    modifier: Modifier = Modifier,
) {
    var workingKey by remember { mutableStateOf(initialKey) }
    var workingDescription by remember { mutableStateOf(initialDescription) }
    var workingScript by remember {
        mutableStateOf(
            initialScript.ifBlank { DEFAULT_DYNAMIC_SCRIPT_TEMPLATE }
        )
    }
    val workingParameters = remember {
        mutableStateListOf<DynamicVariableParameter>().apply {
            addAll(initialParameters)
        }
    }

    val paramsListState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    val validParams = workingParameters.filter { it.name.isNotBlank() }
    val signaturePreview = if (validParams.isEmpty()) {
        "{{${workingKey.ifBlank { "variableName" }}}}"
    } else {
        "{{${workingKey.ifBlank { "variableName" }}(${validParams.joinToString(", ") { it.name.trim() }})}}"
    }

    fun commitSave() {
        val cleanedParams = workingParameters
            .mapNotNull { p ->
                val trimmedName = p.name.trim()
                if (trimmedName.isEmpty()) null
                else DynamicVariableParameter(
                    name = trimmedName,
                    defaultValue = p.defaultValue?.takeIf { it.isNotEmpty() },
                    description = p.description?.takeIf { it.isNotBlank() },
                )
            }
        onSave(workingKey.trim(), workingScript, cleanedParams, workingDescription.trim())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .width(1040.dp)
                .height(680.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                .dialogEscape { onDismiss() }
                .hotkeys {
                    on(AppHotkeys.Save) { commitSave() }
                }
                .testTag("dynamic-var-editor-dialog"),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClapiColors.SurfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_code),
                    contentDescription = null,
                    tint = ClapiColors.Tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = Strings.dynamicVariableEditor,
                    style = MaterialTheme.typography.titleSmall,
                    color = ClapiColors.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(VariableHighlightColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .semantics(mergeDescendants = true) {}
                        .testTag("dynamic-var-signature-preview"),
                ) {
                    Text(
                        text = signaturePreview,
                        fontSize = 12.sp,
                        fontFamily = CodeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = VariableHighlightColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp).testTag("dynamic-var-close-button"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = Strings.close,
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            HorizontalDivider(color = ClapiColors.Border)

            // Variable Name & Description bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClapiColors.SurfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.weight(0.4f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = Strings.variableName,
                        fontSize = 12.sp,
                        color = ClapiColors.OnSurfaceDim,
                        fontWeight = FontWeight.Medium,
                    )
                    BasicTextField(
                        value = workingKey,
                        onValueChange = { workingKey = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ClapiColors.OnSurface,
                            fontSize = 13.sp,
                            fontFamily = CodeFontFamily,
                        ),
                        cursorBrush = SolidColor(ClapiColors.Primary),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.Surface)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("dynamic-var-name-input"),
                        decorationBox = { inner ->
                            if (workingKey.isEmpty()) {
                                Text(Strings.variableName, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                }

                Row(
                    modifier = Modifier.weight(0.6f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = Strings.summary,
                        fontSize = 12.sp,
                        color = ClapiColors.OnSurfaceDim,
                        fontWeight = FontWeight.Medium,
                    )
                    BasicTextField(
                        value = workingDescription,
                        onValueChange = { workingDescription = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ClapiColors.OnSurface,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(ClapiColors.Primary),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.Surface)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("dynamic-var-description-input"),
                        decorationBox = { inner ->
                            if (workingDescription.isEmpty()) {
                                Text("Optional description…", color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                }
            }

            HorizontalDivider(color = ClapiColors.Border)

            // Two-pane body: Left (Parameters + Code Editor) | Right (Test Runner Panel)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Left pane: Parameters + Script Editor
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Parameters section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = Strings.parameters,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ClapiColors.OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (workingParameters.isNotEmpty()) {
                                    Text(
                                        text = "(${workingParameters.size})",
                                        fontSize = 11.sp,
                                        color = ClapiColors.OnSurfaceDim,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ClapiColors.Primary.copy(alpha = 0.12f))
                                    .clickable {
                                        workingParameters.add(
                                            DynamicVariableParameter(
                                                name = "param${workingParameters.size + 1}",
                                                defaultValue = "",
                                            )
                                        )
                                    }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("dynamic-var-add-param-button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_add),
                                    contentDescription = Strings.addParameter,
                                    tint = ClapiColors.Primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = Strings.addParameter,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClapiColors.Primary,
                                )
                            }
                        }

                        if (workingParameters.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 110.dp),
                            ) {
                                LazyColumn(
                                    state = paramsListState,
                                    modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    itemsIndexed(workingParameters) { idx, param ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            BasicTextField(
                                                value = param.name,
                                                onValueChange = { newName ->
                                                    workingParameters[idx] = param.copy(name = newName)
                                                },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = ClapiColors.OnSurface,
                                                    fontSize = 12.sp,
                                                    fontFamily = CodeFontFamily,
                                                ),
                                                cursorBrush = SolidColor(ClapiColors.Primary),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(ClapiColors.Surface)
                                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    .testTag("dynamic-var-param-name-$idx"),
                                                decorationBox = { inner ->
                                                    if (param.name.isEmpty()) {
                                                        Text(
                                                            Strings.parameterName,
                                                            color = ClapiColors.OnSurfaceDim,
                                                            fontSize = 12.sp,
                                                        )
                                                    }
                                                    inner()
                                                },
                                            )

                                            BasicTextField(
                                                value = param.defaultValue.orEmpty(),
                                                onValueChange = { newDefault ->
                                                    workingParameters[idx] = param.copy(defaultValue = newDefault)
                                                },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = ClapiColors.OnSurface,
                                                    fontSize = 12.sp,
                                                    fontFamily = CodeFontFamily,
                                                ),
                                                cursorBrush = SolidColor(ClapiColors.Primary),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(ClapiColors.Surface)
                                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    .testTag("dynamic-var-param-default-$idx"),
                                                decorationBox = { inner ->
                                                    if (param.defaultValue.isNullOrEmpty()) {
                                                        Text(
                                                            Strings.defaultValue,
                                                            color = ClapiColors.OnSurfaceDim,
                                                            fontSize = 12.sp,
                                                        )
                                                    }
                                                    inner()
                                                },
                                            )

                                            IconButtonWithTooltip(
                                                onClick = { workingParameters.removeAt(idx) },
                                                tooltip = Strings.delete,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .testTag("dynamic-var-param-delete-$idx"),
                                            ) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_delete),
                                                    contentDescription = Strings.delete,
                                                    tint = ClapiColors.OnSurfaceDim,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                PlatformLazyVerticalScrollbar(
                                    listState = paramsListState,
                                    modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
                                    testTag = "dynamic-var-params-scrollbar",
                                )
                            }
                        }
                    }

                    // Request context & documentation hint banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_help_outline),
                            contentDescription = null,
                            tint = ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = Strings.dynamicScriptContextHint,
                                fontSize = 11.sp,
                                color = ClapiColors.OnSurfaceDim,
                                lineHeight = 15.sp,
                                modifier = Modifier.testTag("dynamic-var-context-hint"),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable {
                                        runCatching {
                                            uriHandler.openUri(DocsLinks.dynamicVariables)
                                        }
                                    }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("dynamic-var-docs-link"),
                            ) {
                                Text(
                                    text = Strings.dynamicVariablesDocs,
                                    fontSize = 11.sp,
                                    color = ClapiColors.Primary,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painter = painterResource(Res.drawable.ic_open_in_new),
                                    contentDescription = Strings.dynamicVariablesDocs,
                                    tint = ClapiColors.Primary,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    }

                    // Script code editor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                            .testTag("dynamic-var-script-editor"),
                    ) {
                        CodeEditor(
                            text = workingScript,
                            onTextChange = { workingScript = it },
                            language = SyntaxLanguage.JAVASCRIPT,
                            modifier = Modifier.fillMaxSize(),
                            showToolbar = true,
                            enableFolding = false,
                            enableSearch = true,
                            enableFormat = true,
                            enableWordWrap = true,
                            enableCopy = true,
                            enableDownload = false,
                            showCursorPosition = true,
                            placeholder = DYNAMIC_SCRIPT_HINT,
                            testTagPrefix = "dynamic-var-script",
                        )
                    }
                }

                // Right pane: Interactive Test Runner
                DynamicVariableTestPanel(
                    variableName = workingKey,
                    script = workingScript,
                    parameters = workingParameters,
                    initialRequestContext = initialRequestContext,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
            }

            HorizontalDivider(color = ClapiColors.Border)

            // Footer bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClapiColors.SurfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .testTag("dynamic-var-cancel-button"),
                ) {
                    Text(Strings.cancel, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { commitSave() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClapiColors.Primary,
                        contentColor = ClapiColors.OnPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .testTag("dynamic-var-save-button"),
                ) {
                    Text(Strings.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private const val DEFAULT_DYNAMIC_SCRIPT_TEMPLATE = """// Return the dynamic value for this request
return new Date().toISOString();
"""

private val DYNAMIC_SCRIPT_HINT = """
// Dynamic Variable Script — evaluated fresh on every request execution.
//
// Return a value (string, number, boolean, or object):
//   return new Date(Date.now() + Number(seconds || 0) * 1000).toISOString();
//
// Access declared parameters:
//   args.seconds  // or directly as `seconds`, or positional `args[0]`
//
// Access read-only outgoing request context:
//   context.request.url      // e.g. "https://api.example.com/v1/items"
//   context.request.method   // e.g. "GET", "POST"
//   context.request.headers  // e.g. context.request.headers["Accept"]
//   context.request.name     // request name
""".trimIndent()
