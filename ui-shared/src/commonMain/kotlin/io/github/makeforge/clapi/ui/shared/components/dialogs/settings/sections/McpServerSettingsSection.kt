package io.github.makeforge.clapi.ui.shared.components.dialogs.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.components.dialogs.settings.SettingNumberField
import io.github.makeforge.clapi.ui.shared.components.dialogs.settings.SettingToggle
import io.github.makeforge.clapi.ui.shared.components.dialogs.settings.SettingsDivider
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily

@Composable
fun McpServerSettingsSection(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val controller = state.embeddedMcpServerController
    var copiedSnippet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingToggle(
            label = Strings.t("mcp_server_enable"),
            description = Strings.t("mcp_server_enable_desc"),
            checked = settings.mcpServerEnabled,
            onCheckedChange = { enabled ->
                settings.mcpServerEnabled = enabled
                SettingsRepository.save(settings)
                controller.applySettings()
            },
            tag = "mcp-server-enable-toggle",
        )

        SettingsDivider()

        SettingNumberField(
            label = Strings.t("mcp_server_port"),
            value = settings.mcpServerPort,
            onValueChange = { newPort ->
                val clamped = newPort.coerceIn(1, 65535)
                settings.mcpServerPort = clamped
                SettingsRepository.save(settings)
                if (settings.mcpServerEnabled) {
                    controller.applySettings()
                }
            },
        )

        SettingsDivider()

        // Status Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceVariant)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .testTag("mcp-server-status-card"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (controller.isRunning) ClapiColors.MethodGet else ClapiColors.OnSurfaceDim),
                )
                Column {
                    Text(
                        text = if (controller.isRunning) {
                            "${Strings.t("mcp_server_running")}: ${controller.endpointUrl}"
                        } else {
                            Strings.t("mcp_server_stopped")
                        },
                        color = ClapiColors.OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    controller.lastError?.let { err ->
                        Text(
                            text = err,
                            color = ClapiColors.Error,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val snippet = """
            {
              "mcpServers": {
                "clapi": {
                  "url": "http://127.0.0.1:${settings.mcpServerPort}/mcp"
                }
              }
            }
        """.trimIndent()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = Strings.t("mcp_server_client_config"),
                color = ClapiColors.OnSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .handPointerHover()
                    .clickable {
                        copyToClipboard(snippet)
                        copiedSnippet = true
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("mcp-server-copy-config-btn"),
            ) {
                Text(
                    text = if (copiedSnippet) Strings.t("copied") else Strings.t("copy"),
                    color = ClapiColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClapiColors.SurfaceVariant)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = snippet,
                    color = ClapiColors.OnSurface,
                    fontFamily = CodeFontFamily,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
