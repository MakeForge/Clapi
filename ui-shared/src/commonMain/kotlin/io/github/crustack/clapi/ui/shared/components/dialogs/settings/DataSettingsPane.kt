package io.github.crustack.clapi.ui.shared.components.dialogs.settings

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
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcStatus
import io.github.crustack.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.crustack.clapi.core.network.keeper.createKeeperClient
import io.github.crustack.clapi.core.network.keeper.initializeAndBindKeeperStorage
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.crustack.clapi.ui.shared.generated.resources.*
import io.github.crustack.clapi.ui.shared.i18n.AppLanguage
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.persistence.SettingsRepository
import io.github.crustack.clapi.ui.shared.platform.*
import io.github.crustack.clapi.ui.shared.state.*
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ScriptingSettings(s: AppSettings) {
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
internal fun DataSettings(state: AppState) {
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
