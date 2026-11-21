package io.github.crustack.clapi.ui.shared.components.dialogs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.clapi.ui.shared.components.clampDialogOffsetFromCenter
import io.github.crustack.clapi.ui.shared.components.dialogEscape
import io.github.crustack.clapi.ui.shared.components.draggableNoSlop
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.generated.resources.*
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.PlatformColumnVerticalScrollbar
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private enum class SettingsSection(val icon: DrawableResource) {
    GENERAL(Res.drawable.ic_settings),
    EDITOR(Res.drawable.ic_format_align_left),
    THEME(Res.drawable.ic_light_mode),
    LANGUAGE(Res.drawable.ic_language),
    NETWORK(Res.drawable.ic_network_check),
    PROXY(Res.drawable.ic_settings_applications),
    SECRETS(Res.drawable.ic_key),
    SCRIPTING(Res.drawable.ic_data_object),
    DATA(Res.drawable.ic_storage),
}

@Composable
private fun settingsSectionLabel(section: SettingsSection): String = when (section) {
    SettingsSection.GENERAL -> Strings.general
    SettingsSection.EDITOR -> Strings.t("editor")
    SettingsSection.THEME -> Strings.theme
    SettingsSection.LANGUAGE -> Strings.language
    SettingsSection.NETWORK -> Strings.network
    SettingsSection.PROXY -> Strings.proxy
    SettingsSection.SECRETS -> Strings.t("secrets_manager")
    SettingsSection.SCRIPTING -> Strings.t("scripts")
    SettingsSection.DATA -> Strings.t("data")
}

@Composable
fun SettingsDialog(
    state: AppState,
    modifier: Modifier = Modifier,
) {
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
                .dialogEscape(onDismiss = { state.showSettingsDialog = false })
                .onSizeChanged { settingsViewportSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { state.showSettingsDialog = false }
                },
            contentAlignment = Alignment.Center,
        ) {
        Box(
            modifier = modifier
                .offset { IntOffset(settingsOffsetX.roundToInt(), settingsOffsetY.roundToInt()) }
                .widthIn(min = 680.dp, max = 860.dp)
                .height(660.dp)
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
                                .handPointerHover()
                                .clickable { selectedSection = section }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("settings-section-${section.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painterResource(section.icon),
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
                val settingsScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(settingsScrollState)
                            .padding(24.dp)
                            .padding(end = 12.dp),
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
                        SettingsSection.SECRETS  -> SecretsSettings(settings)
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
                PlatformColumnVerticalScrollbar(
                    scrollState = settingsScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
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
