package io.github.makeforge.clapi.ui.shared.components.dialogs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.makeforge.clapi.core.network.keepassxc.KeePassXcStatus
import io.github.makeforge.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.makeforge.clapi.core.network.keeper.createKeeperClient
import io.github.makeforge.clapi.core.network.keeper.initializeAndBindKeeperStorage
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.platform.*
import io.github.makeforge.clapi.ui.shared.state.*
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun GeneralSettings(s: AppSettings) {
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
internal fun EditorSettings(s: AppSettings) {
    SettingToggle(
        label = Strings.t("show_editor_position_indicator"),
        description = Strings.t("settings_editor_position_indicator_desc"),
        checked = s.showEditorPositionIndicator,
        onCheckedChange = { s.showEditorPositionIndicator = it },
        tag = "editor-position-indicator-toggle",
    )
}
