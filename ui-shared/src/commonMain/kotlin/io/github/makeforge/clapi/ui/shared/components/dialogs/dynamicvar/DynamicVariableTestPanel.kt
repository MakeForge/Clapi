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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.scripting.DynamicScriptContext
import io.github.makeforge.clapi.core.scripting.DynamicScriptExecutionResult
import io.github.makeforge.clapi.core.scripting.RequestContext
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_check_circle
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_error_outline
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_keyboard_arrow_right
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_play_arrow
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.PlatformColumnVerticalScrollbar
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.platform.insetScrollbar
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@Composable
fun DynamicVariableTestPanel(
    variableName: String,
    script: String,
    parameters: List<DynamicVariableParameter>,
    expectedType: VariableType = VariableType.STRING,
    initialRequestContext: RequestContext? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val argInputs = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(parameters) {
        parameters.forEach { param ->
            if (param.name.isNotBlank() && !argInputs.containsKey(param.name)) {
                argInputs[param.name] = param.defaultValue ?: ""
            }
        }
    }

    var showMockContext by remember { mutableStateOf(false) }
    var mockUrl by remember {
        mutableStateOf(
            initialRequestContext?.url?.takeIf { it.isNotBlank() }
                ?: "https://api.example.com/v1/resource"
        )
    }
    var mockMethod by remember {
        mutableStateOf(
            initialRequestContext?.method?.takeIf { it.isNotBlank() }
                ?: "GET"
        )
    }
    var mockName by remember {
        mutableStateOf(
            initialRequestContext?.name?.takeIf { it.isNotBlank() }
                ?: "Sample Request"
        )
    }
    var mockHeadersText by remember {
        mutableStateOf(
            initialRequestContext?.headers?.takeIf { it.isNotEmpty() }
                ?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }
                ?: "Accept: application/json\nUser-Agent: Clapi"
        )
    }

    var isRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<DynamicScriptExecutionResult?>(null) }

    fun executeTest() {
        if (isRunning) return
        isRunning = true
        val validParams = parameters.filter { it.name.isNotBlank() }
        val namedArgs = linkedMapOf<String, Any?>()
        val positionalArgs = mutableListOf<Any?>()
        validParams.forEach { param ->
            val rawVal = argInputs[param.name] ?: param.defaultValue ?: ""
            val parsedVal = parsePrimitiveTestInput(rawVal)
            namedArgs[param.name] = parsedVal
            positionalArgs.add(parsedVal)
        }
        val parsedHeaders = mockHeadersText.lines()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val k = line.substring(0, idx).trim()
                    val v = line.substring(idx + 1).trim()
                    if (k.isNotEmpty()) k to v else null
                } else null
            }
            .toMap()

        val ctx = DynamicScriptContext(
            args = namedArgs,
            positionalArgs = positionalArgs,
            request = RequestContext(
                url = mockUrl,
                method = mockMethod.ifBlank { "GET" },
                headers = parsedHeaders,
                name = mockName,
            ),
        )

        scope.launch {
            val result = withContext(Dispatchers.Default) {
                VariableResolver.defaultDynamicEngine.executeDynamicVariable(script, ctx)
            }
            lastResult = result
            isRunning = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Surface)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .testTag("dynamic-var-test-panel"),
    ) {
        // Header bar with title and Run Test button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.SurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = Strings.testExecution,
                style = MaterialTheme.typography.labelLarge,
                color = ClapiColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
            )

            Button(
                onClick = { executeTest() },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClapiColors.Primary,
                    contentColor = ClapiColors.OnPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(30.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("dynamic-var-run-test-button"),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ClapiColors.OnPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.ic_play_arrow),
                        contentDescription = Strings.runTest,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(Strings.runTest, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        HorizontalDivider(color = ClapiColors.Border)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Parameter test inputs
                val activeParams = parameters.filter { it.name.isNotBlank() }
                if (activeParams.isNotEmpty()) {
                    Text(
                        text = Strings.parameters,
                        style = MaterialTheme.typography.labelSmall,
                        color = ClapiColors.OnSurfaceDim,
                        fontWeight = FontWeight.SemiBold,
                    )
                    activeParams.forEachIndexed { idx, param ->
                        val currentVal = argInputs[param.name] ?: param.defaultValue ?: ""
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
                                modifier = Modifier.width(100.dp),
                            )
                            BasicTextField(
                                value = currentVal,
                                onValueChange = { argInputs[param.name] = it },
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
                                    .background(ClapiColors.SurfaceContainer)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("dynamic-var-test-arg-$idx"),
                                decorationBox = { inner ->
                                    if (currentVal.isEmpty()) {
                                        Text(
                                            text = param.defaultValue ?: Strings.value,
                                            color = ClapiColors.OnSurfaceDim,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = ClapiColors.Border)
                }

                // Collapsible Mock Request Context section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMockContext = !showMockContext }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("dynamic-var-mock-context-toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (showMockContext) Res.drawable.ic_keyboard_arrow_down
                                else Res.drawable.ic_keyboard_arrow_right
                            ),
                            contentDescription = null,
                            tint = ClapiColors.OnSurfaceDim,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = Strings.mockRequestContext,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ClapiColors.OnSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "context.request",
                            fontSize = 11.sp,
                            fontFamily = CodeFontFamily,
                            color = ClapiColors.OnSurfaceDim,
                        )
                    }

                    if (showMockContext) {
                        HorizontalDivider(color = ClapiColors.Border)
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicTextField(
                                    value = mockMethod,
                                    onValueChange = { mockMethod = it.uppercase() },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = ClapiColors.Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CodeFontFamily,
                                    ),
                                    cursorBrush = SolidColor(ClapiColors.Primary),
                                    modifier = Modifier
                                        .width(72.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ClapiColors.Surface)
                                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("dynamic-var-mock-method"),
                                )
                                BasicTextField(
                                    value = mockUrl,
                                    onValueChange = { mockUrl = it },
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
                                        .testTag("dynamic-var-mock-url"),
                                )
                            }

                            BasicTextField(
                                value = mockHeadersText,
                                onValueChange = { mockHeadersText = it },
                                textStyle = TextStyle(
                                    color = ClapiColors.OnSurface,
                                    fontSize = 11.sp,
                                    fontFamily = CodeFontFamily,
                                ),
                                cursorBrush = SolidColor(ClapiColors.Primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp, max = 96.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ClapiColors.Surface)
                                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("dynamic-var-mock-headers"),
                                decorationBox = { inner ->
                                    if (mockHeadersText.isEmpty()) {
                                        Text(
                                            text = "Header-Name: value",
                                            color = ClapiColors.OnSurfaceDim,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                    }
                }

                // Output section
                val result = lastResult
                if (result == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .testTag("dynamic-var-test-empty"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = Strings.noOutputYet,
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    // Status & Duration row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val statusColor = if (result.success) ClapiColors.StatusSuccess else ClapiColors.Error
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("dynamic-var-test-status"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (result.success) Res.drawable.ic_check_circle
                                    else Res.drawable.ic_error_outline
                                ),
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (result.success) Strings.t("success") else Strings.t("mcp_status_error"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor,
                            )
                        }

                        Text(
                            text = "${result.durationMs} ms",
                            fontSize = 11.sp,
                            fontFamily = CodeFontFamily,
                            color = ClapiColors.OnSurfaceDim,
                            modifier = Modifier.testTag("dynamic-var-test-duration"),
                        )
                    }

                    if (result.success) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = Strings.returnValue,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ClapiColors.OnSurfaceDim,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                IconButtonWithTooltip(
                                    onClick = { copyToClipboard(result.returnValue.orEmpty()) },
                                    tooltip = Strings.copy,
                                    modifier = Modifier.size(22.dp).testTag("dynamic-var-test-copy-button"),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_content_copy),
                                        contentDescription = Strings.copy,
                                        tint = ClapiColors.OnSurfaceDim,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                            SelectionContainer {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClapiColors.SurfaceContainer)
                                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("dynamic-var-test-return-value"),
                                ) {
                                    Text(
                                        text = result.returnValue.orEmpty(),
                                        fontSize = 13.sp,
                                        fontFamily = CodeFontFamily,
                                        color = ClapiColors.OnSurface,
                                    )
                                }
                            }
                            val rawVal = result.returnValue.orEmpty().trim()
                            val typeMismatchWarning = when (expectedType) {
                                VariableType.NUMBER -> if (rawVal.toDoubleOrNull() == null) {
                                    "Warning: Script returned '$rawVal', which is not a valid Number."
                                } else null
                                VariableType.BOOLEAN -> if (rawVal != "true" && rawVal != "false") {
                                    "Warning: Script returned '$rawVal', which is not a valid Boolean (expected 'true' or 'false')."
                                } else null
                                VariableType.OBJECT -> if (!((rawVal.startsWith("{") && rawVal.endsWith("}")) || (rawVal.startsWith("[") && rawVal.endsWith("]")))) {
                                    "Warning: Script returned '$rawVal', which is not a valid JSON Object or Array."
                                } else null
                                else -> null
                            }
                            if (typeMismatchWarning != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClapiColors.StatusWarning.copy(alpha = 0.12f))
                                        .border(1.dp, ClapiColors.StatusWarning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("dynamic-var-test-type-warning"),
                                ) {
                                    Text(
                                        text = typeMismatchWarning,
                                        fontSize = 11.sp,
                                        color = ClapiColors.StatusWarning,
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = Strings.scriptError,
                                style = MaterialTheme.typography.labelSmall,
                                color = ClapiColors.Error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SelectionContainer {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClapiColors.Error.copy(alpha = 0.1f))
                                        .border(1.dp, ClapiColors.Error.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("dynamic-var-test-error"),
                                ) {
                                    Text(
                                        text = result.errorMessage ?: Strings.t("unknown_error"),
                                        fontSize = 12.sp,
                                        fontFamily = CodeFontFamily,
                                        color = ClapiColors.Error,
                                    )
                                }
                            }
                        }
                    }

                    if (result.consoleLogs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = Strings.consoleOutput,
                                style = MaterialTheme.typography.labelSmall,
                                color = ClapiColors.OnSurfaceDim,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ClapiColors.SurfaceContainer)
                                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("dynamic-var-test-logs"),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    for (logLine in result.consoleLogs) {
                                        Text(
                                            text = logLine,
                                            fontSize = 11.sp,
                                            fontFamily = CodeFontFamily,
                                            color = ClapiColors.OnSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PlatformColumnVerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().insetScrollbar(),
                testTag = "dynamic-var-test-scrollbar",
            )
        }
    }
}

internal fun parsePrimitiveTestInput(raw: String): Any? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.equals("true", ignoreCase = true)) return true
    if (trimmed.equals("false", ignoreCase = true)) return false
    if (trimmed.equals("null", ignoreCase = true)) return null
    trimmed.toLongOrNull()?.let { return it }
    trimmed.toDoubleOrNull()?.let { return it }
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
        (trimmed.startsWith("'") && trimmed.endsWith("'"))
    ) {
        if (trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
        }
    }
    return raw
}
