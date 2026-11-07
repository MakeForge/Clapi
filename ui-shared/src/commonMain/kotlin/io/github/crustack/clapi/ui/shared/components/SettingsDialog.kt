package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import io.github.crustack.clapi.ui.shared.i18n.AppLanguage
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.state.AppSettings
import io.github.crustack.clapi.ui.shared.state.ResponseLayout
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.AppTheme
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import io.github.crustack.clapi.ui.shared.theme.ClapiColors

private enum class SettingsSection(val icon: ImageVector) {
    GENERAL(Icons.Default.Settings),
    EDITOR(Icons.Default.FormatAlignLeft),
    THEME(Icons.Default.LightMode),
    LANGUAGE(Icons.Default.Language),
    NETWORK(Icons.Default.NetworkCheck),
    PROXY(Icons.Default.SettingsApplications),
    SCRIPTING(Icons.Default.DataObject),
    DATA(Icons.Default.Storage),
}

@Composable
private fun settingsSectionLabel(section: SettingsSection): String = when (section) {
    SettingsSection.GENERAL -> Strings.general
    SettingsSection.EDITOR -> Strings.t("editor")
    SettingsSection.THEME -> Strings.theme
    SettingsSection.LANGUAGE -> Strings.language
    SettingsSection.NETWORK -> Strings.network
    SettingsSection.PROXY -> Strings.proxy
    SettingsSection.SCRIPTING -> Strings.t("scripts")
    SettingsSection.DATA -> Strings.t("data")
}

@Composable
fun SettingsDialog(state: AppState) {
    if (!state.showSettingsDialog) return

    var selectedSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    val settings = state.settings

    // M-1: Make the dialog draggable (same pattern as EnvironmentEditDialog).
    var settingsOffsetX by remember { mutableStateOf(0f) }
    var settingsOffsetY by remember { mutableStateOf(0f) }
    var settingsViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var settingsCardSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = { state.showSettingsDialog = false }) {
        // Full-screen invisible backdrop — captures taps outside the card to dismiss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { settingsViewportSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { state.showSettingsDialog = false }
                },
            contentAlignment = Alignment.Center,
        ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(settingsOffsetX.roundToInt(), settingsOffsetY.roundToInt()) }
                .widthIn(min = 680.dp, max = 860.dp)
                .height(520.dp)
                .onSizeChanged { settingsCardSize = it }
                .clip(RoundedCornerShape(12.dp))
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                .draggableNoSlop { dx, dy ->
                    val (cx, cy) = clampDialogOffsetFromCenter(
                        offsetX = settingsOffsetX + dx,
                        offsetY = settingsOffsetY + dy,
                        cardSize = settingsCardSize,
                        viewportSize = settingsViewportSize,
                    )
                    settingsOffsetX = cx
                    settingsOffsetY = cy
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("settings-dialog"),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Left nav ──────────────────────────────────
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight()
                        .background(ClapiColors.SurfaceVariant)
                        .padding(12.dp),
                ) {
                    Text(
                        Strings.settings,
                        style = MaterialTheme.typography.titleMedium,
                        color = ClapiColors.OnSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    SettingsSection.entries.forEach { section ->
                        val isActive = section == selectedSection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) ClapiColors.SelectedItem else Color.Transparent)
                                .clickable { selectedSection = section }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("settings-section-${section.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                section.icon,
                                contentDescription = null,
                                tint = if (isActive) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                settingsSectionLabel(section),
                                fontSize = 13.sp,
                                color = if (isActive) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Right border
                Box(Modifier.width(1.dp).fillMaxHeight().background(ClapiColors.Border))

                // ── Right content ─────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        settingsSectionLabel(selectedSection),
                        style = MaterialTheme.typography.titleMedium,
                        color = ClapiColors.OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    when (selectedSection) {
                        SettingsSection.GENERAL  -> GeneralSettings(settings)
                        SettingsSection.EDITOR   -> EditorSettings(settings)
                        SettingsSection.THEME    -> ThemeSettings(settings)
                        SettingsSection.LANGUAGE -> LanguageSettings(settings)
                        SettingsSection.NETWORK  -> NetworkSettings(settings)
                        SettingsSection.PROXY    -> ProxySettings(settings)
                        SettingsSection.SCRIPTING -> ScriptingSettings(settings)
                        SettingsSection.DATA     -> DataSettings(state)
                    }

                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .clickable { state.showHelpDialog = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("settings-help-about-button"),
                    ) {
                        Text(
                            text = "Open Help & About",
                            color = ClapiColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Close button (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .clickable { state.showSettingsDialog = false }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("settings-close-button"),
            ) {
                Text(Strings.t("done"), color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }  // closes settings card Box
        }  // closes full-screen backdrop Box
    }
}

// ── Section composables ─────────────────────────────────────────

@Composable
private fun GeneralSettings(s: AppSettings) {
    SettingToggle(
        label = Strings.autoSave,
        description = Strings.t("settings_auto_save_desc"),
        checked = s.autoSaveRequests,
        onCheckedChange = { s.autoSaveRequests = it },
        tag = "auto-save-toggle",
    )
    SettingsDivider()
    SettingToggle(
        label = Strings.t("confirm_before_deleting"),
        description = Strings.t("settings_confirm_delete_desc"),
        checked = s.confirmBeforeDelete,
        onCheckedChange = { s.confirmBeforeDelete = it },
    )
    SettingsDivider()
    SettingToggle(
        label = Strings.t("json5_in_json_bodies"),
        description = Strings.t("settings_json5_in_json_bodies_desc"),
        checked = s.allowJson5InJsonBodies,
        onCheckedChange = { s.allowJson5InJsonBodies = it },
        tag = "json5-body-toggle",
    )
    SettingsDivider()
    SettingNumberField(
        label = Strings.t("default_request_timeout_seconds"),
        value = s.defaultTimeoutSec,
        onValueChange = { s.defaultTimeoutSec = it },
    )
    SettingsDivider()
    SettingChoice(
        label = Strings.responseLayout,
        description = Strings.t("settings_response_layout_desc"),
        options = listOf(ResponseLayout.RIGHT to Strings.t("right_side"), ResponseLayout.BOTTOM to Strings.t("bottom_panel")),
        selected = s.responseLayout,
        onSelected = { s.responseLayout = it },
        tagPrefix = "response-layout",
    )
}

@Composable
private fun EditorSettings(s: AppSettings) {
    SettingToggle(
        label = Strings.t("show_editor_position_indicator"),
        description = Strings.t("settings_editor_position_indicator_desc"),
        checked = s.showEditorPositionIndicator,
        onCheckedChange = { s.showEditorPositionIndicator = it },
        tag = "editor-position-indicator-toggle",
    )
}

@Composable
private fun ThemeSettings(s: AppSettings) {
    Text(Strings.theme, style = MaterialTheme.typography.labelMedium, color = ClapiColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(AppTheme.DARK, Strings.darkMode, Icons.Default.DarkMode),
            Triple(AppTheme.LIGHT, Strings.lightMode, Icons.Default.LightMode),
            Triple(AppTheme.SYSTEM, Strings.systemTheme, Icons.Default.SettingsApplications),
        ).forEach { (theme, label, icon) ->
            val isSelected = s.theme == theme
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ClapiColors.SelectedItem else ClapiColors.SurfaceContainer)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ClapiColors.Primary else ClapiColors.Border,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { s.theme = theme }
                    .padding(16.dp)
                    .testTag("theme-${label.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = label, tint = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurfaceDim, modifier = Modifier.size(24.dp))
                Text(label, color = if (isSelected) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LanguageSettings(s: AppSettings) {
    Text(Strings.language, style = MaterialTheme.typography.labelMedium, color = ClapiColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { lang ->
            val isSelected = s.language == lang
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ClapiColors.SelectedItem else ClapiColors.SurfaceContainer)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ClapiColors.Primary else ClapiColors.Border,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { s.language = lang }
                    .padding(16.dp)
                    .testTag("language-${lang.code}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(lang.code.uppercase(), color = if (isSelected) ClapiColors.Primary else ClapiColors.OnSurfaceDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(lang.displayName, color = if (isSelected) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant, fontSize = 12.sp)
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        Strings.t("settings_language_change_hint"),
        color = ClapiColors.OnSurfaceDim,
        fontSize = 11.sp,
    )
}

@Composable
private fun NetworkSettings(s: AppSettings) {
    SettingNumberField(Strings.t("request_timeout_seconds"), s.requestTimeoutSec) { s.requestTimeoutSec = it }
    SettingsDivider()
    SettingToggle(
        Strings.followRedirects,
        Strings.t("settings_follow_redirects_desc"),
        s.followRedirects,
        { s.followRedirects = it },
        tag = "follow-redirects-toggle",
    )
}

@Composable
private fun ProxySettings(s: AppSettings) {
    SettingToggle(Strings.t("enable_proxy"), Strings.t("settings_enable_proxy_desc"), s.proxyEnabled, { s.proxyEnabled = it })
    SettingsDivider()
    SettingTextField(Strings.t("http_proxy"), s.httpProxy, { s.httpProxy = it }, "http://proxy:8080")
    SettingsDivider()
    SettingTextField(Strings.t("https_proxy"), s.httpsProxy, { s.httpsProxy = it }, "https://proxy:8443")
}

@Composable
private fun ScriptingSettings(s: AppSettings) {
    SettingTextField(
        label = Strings.t("script_namespace_prefix"),
        value = s.scriptPrefix,
        onValueChange = { v ->
            val clean = v.trim().filter { it.isLetterOrDigit() || it == '_' }
            if (clean.isNotEmpty()) s.scriptPrefix = clean
        },
        placeholder = "clapi",
    )
    SettingsDivider()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            Strings.t("script_namespace_desc"),
            color = ClapiColors.OnSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(
            Strings.t("example_with_prefix") + " \"${s.scriptPrefix}\":",
            color = ClapiColors.OnSurfaceDim,
            fontSize = 12.sp,
        )
        Text(
            "${s.scriptPrefix}.test(\"status\", () => {\n" +
            "  ${s.scriptPrefix}.expect(${s.scriptPrefix}.response.code).to.equal(200)\n" +
            "})",
            color = ClapiColors.Primary,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Text(
            Strings.t("script_namespace_default_hint"),
            color = ClapiColors.OnSurfaceDim,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DataSettings(state: AppState) {
    val clearHistoryDesc = Strings.t("clear_history_desc")
    val clearLabel = Strings.t("clear")
    val clearHistoryTitle = Strings.t("clear_history_title")
    val clearHistoryConfirmMessage = Strings.t("clear_history_confirm_message")
    val historyCleared = Strings.t("history_cleared")
    val clearConsoleLogs = Strings.t("clear_console_logs")
    val clearConsoleLogsDesc = Strings.t("clear_console_logs_desc")

    SettingAction(
        Strings.clearHistory,
        clearHistoryDesc,
        actionLabel = clearLabel,
        actionColor = ClapiColors.Error,
        tag = "clear-history",
    ) {
        state.showConfirm(
            title = clearHistoryTitle,
            message = clearHistoryConfirmMessage,
            action = {
                state.clearHistory()
                state.log(historyCleared, LogLevel.INFO)
            },
        )
    }
    SettingsDivider()
    SettingAction(
        clearConsoleLogs,
        clearConsoleLogsDesc,
        actionLabel = clearLabel,
        actionColor = ClapiColors.Error,
        tag = "clear-console",
    ) {
        state.consoleLogs.clear()
    }
}

@Composable
private fun <T> SettingChoice(
    label: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    tagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
        if (description.isNotEmpty()) {
            Text(description, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) ClapiColors.SelectedItem else ClapiColors.SurfaceContainer)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) ClapiColors.Primary else ClapiColors.Border,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelected(value) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("$tagPrefix-${text.lowercase().replace(" ", "-")}"),
                ) {
                    Text(
                        text = text,
                        color = if (active) ClapiColors.OnSurface else ClapiColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Setting primitives ──────────────────────────────────────────

@Composable
private fun SettingToggle(
    label: String,
    description: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = ClapiColors.OnPrimary,
                checkedTrackColor  = ClapiColors.Primary,
                uncheckedThumbColor= ClapiColors.OnSurfaceDim,
                uncheckedTrackColor= ClapiColors.SurfaceHigh,
            ),
        )
    }
}

@Composable
private fun SettingNumberField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    // M-10: Maintain local text state so mid-edit values (e.g. empty string
    // while the user is deleting digits) are never silently discarded.
    // A red border signals that the current text cannot be parsed as a number.
    var localText by remember(value) { mutableStateOf(value.toString()) }
    val isValid = localText.toIntOrNull() != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface, modifier = Modifier.weight(1f))
        BasicTextField(
            value = localText,
            onValueChange = { newText ->
                // Allow any text while typing; only commit valid integers.
                localText = newText
                newText.toIntOrNull()?.let(onValueChange)
            },
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier
                .width(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceContainer)
                // Red border for invalid input so the user gets immediate feedback.
                .border(
                    1.dp,
                    if (isValid) ClapiColors.Border else ClapiColors.Error,
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SettingTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ClapiColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                inner()
            },
        )
    }
}

@Composable
private fun SettingAction(
    label: String,
    description: String = "",
    actionLabel: String = "Run",
    actionColor: Color = ClapiColors.Primary,
    tag: String = "",
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, fontSize = 13.sp, color = ClapiColors.OnSurface)
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(actionColor.copy(alpha = 0.12f))
                .border(1.dp, actionColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .then(if (tag.isNotEmpty()) Modifier.testTag("$tag-action") else Modifier),
        ) {
            Text(actionLabel, color = actionColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = ClapiColors.Border.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
}
