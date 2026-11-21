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
internal fun ThemeSettings(s: AppSettings) {
    Text(Strings.theme, style = MaterialTheme.typography.labelMedium, color = ClapiColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(AppTheme.DARK, Strings.darkMode, painterResource(Res.drawable.ic_dark_mode)),
            Triple(AppTheme.LIGHT, Strings.lightMode, painterResource(Res.drawable.ic_light_mode)),
            Triple(AppTheme.SYSTEM, Strings.systemTheme, painterResource(Res.drawable.ic_settings_applications)),
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
internal fun LanguageSettings(s: AppSettings) {
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
internal fun NetworkSettings(s: AppSettings) {
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
internal fun ProxySettings(s: AppSettings) {
    SettingToggle(Strings.t("enable_proxy"), Strings.t("settings_enable_proxy_desc"), s.proxyEnabled, { s.proxyEnabled = it })
    SettingsDivider()
    SettingTextField(Strings.t("http_proxy"), s.httpProxy, { s.httpProxy = it }, "http://proxy:8080")
    SettingsDivider()
    SettingTextField(Strings.t("https_proxy"), s.httpsProxy, { s.httpsProxy = it }, "https://proxy:8443")
}
