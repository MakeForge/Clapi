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
internal fun VaultSettings(s: AppSettings) {
    SettingTextField(
        label = Strings.t("vault_server_url"),
        value = s.vaultServerUrl,
        onValueChange = { s.vaultServerUrl = it },
        placeholder = "http://127.0.0.1:8200",
        tag = "vault-server-url",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("vault_mount_path"),
        value = s.vaultMountPath,
        onValueChange = { s.vaultMountPath = it },
        placeholder = "secret",
        tag = "vault-mount-path",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("vault_namespace"),
        value = s.vaultNamespace,
        onValueChange = { s.vaultNamespace = it },
        placeholder = "admin",
        tag = "vault-namespace",
    )
    SettingsDivider()
    SettingPasswordField(
        label = Strings.t("vault_token"),
        value = s.vaultToken,
        onValueChange = { s.vaultToken = it },
        placeholder = "hvs.CAESIJ...",
        tag = "vault-token",
    )
}

@Composable
internal fun KeePassXcSettings() {
    val scope = rememberCoroutineScope()
    var associateId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<KeePassXcStatus?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val client = remember { createKeePassXcClient() }
    val proxyPath = remember { client.getProxyPath() }

    val associatingMsg = Strings.t("keepassxc_associating")

    LaunchedEffect(refreshKey) {
        isLoading = true
        actionMessage = null
        try {
            associateId = client.getAssociateId()
            status = client.checkStatus()
        } catch (e: Exception) {
            status = KeePassXcStatus.Unavailable(e.message ?: "Failed to check status")
        } finally {
            isLoading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Status header & badge & refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(Strings.t("keepassxc_association_status"), fontSize = 13.sp, color = ClapiColors.OnSurface)
                Text(
                    Strings.t("keepassxc_association_hint"),
                    fontSize = 11.sp,
                    color = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val isAssociated = status == KeePassXcStatus.Connected && !associateId.isNullOrBlank()
                val statusText: String
                val statusBg: Color
                val statusBorder: Color
                val statusFg: Color
                when {
                    isLoading -> {
                        statusText = "Checking..."
                        statusBg = ClapiColors.SurfaceContainer
                        statusBorder = ClapiColors.Border
                        statusFg = ClapiColors.OnSurfaceDim
                    }
                    isAssociated -> {
                        statusText = Strings.t("keepassxc_associated")
                        statusBg = ClapiColors.Primary.copy(alpha = 0.15f)
                        statusBorder = ClapiColors.Primary.copy(alpha = 0.4f)
                        statusFg = ClapiColors.Primary
                    }
                    status == KeePassXcStatus.DatabaseLocked -> {
                        statusText = Strings.t("keepassxc_db_locked")
                        val warnColor = Color(0xFFF59E0B)
                        statusBg = warnColor.copy(alpha = 0.15f)
                        statusBorder = warnColor.copy(alpha = 0.4f)
                        statusFg = warnColor
                    }
                    status is KeePassXcStatus.Unavailable -> {
                        statusText = Strings.t("keepassxc_unavailable")
                        statusBg = ClapiColors.Error.copy(alpha = 0.15f)
                        statusBorder = ClapiColors.Error.copy(alpha = 0.4f)
                        statusFg = ClapiColors.Error
                    }
                    else -> {
                        statusText = Strings.t("keepassxc_not_associated")
                        statusBg = ClapiColors.SurfaceContainer
                        statusBorder = ClapiColors.Border
                        statusFg = ClapiColors.OnSurfaceDim
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBg)
                        .border(1.dp, statusBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("keepassxc-status-badge"),
                ) {
                    Text(
                        text = statusText,
                        color = statusFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                IconButton(
                    onClick = { refreshKey++ },
                    enabled = !isLoading,
                    modifier = Modifier.size(24.dp).testTag("keepassxc-refresh-status"),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Associate ID & Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(Strings.t("keepassxc_associate_id"), fontSize = 12.sp, color = ClapiColors.OnSurfaceDim)
                Text(
                    text = associateId?.takeIf { it.isNotBlank() } ?: "—",
                    fontSize = 13.sp,
                    fontFamily = CodeFontFamily,
                    color = if (!associateId.isNullOrBlank()) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                    modifier = Modifier.testTag("keepassxc-associate-id-val"),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!associateId.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.Error.copy(alpha = 0.12f))
                            .border(1.dp, ClapiColors.Error.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLoading) {
                                scope.launch {
                                    isLoading = true
                                    actionMessage = null
                                    isError = false
                                    try {
                                        client.unassociate()
                                        DefaultKeePassXcClientManager.Shared.shutdown()
                                        associateId = null
                                        status = client.checkStatus()
                                        actionMessage = "Unassociated successfully"
                                    } catch (e: Exception) {
                                        actionMessage = e.message ?: "Failed to unassociate"
                                        isError = true
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("keepassxc-unassociate-btn"),
                    ) {
                        Text(Strings.t("keepassxc_unassociate"), color = ClapiColors.Error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.Primary.copy(alpha = 0.12f))
                            .border(1.dp, ClapiColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLoading) {
                                scope.launch {
                                    isLoading = true
                                    actionMessage = associatingMsg
                                    isError = false
                                    try {
                                        val newId = client.associate()
                                        associateId = newId
                                        status = client.checkStatus()
                                        actionMessage = "Associated successfully with ID: $newId"
                                    } catch (e: Exception) {
                                        actionMessage = e.message ?: "Association failed"
                                        isError = true
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("keepassxc-reassociate-btn"),
                    ) {
                        Text(Strings.t("keepassxc_reassociate"), color = ClapiColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.Primary.copy(alpha = 0.12f))
                            .border(1.dp, ClapiColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLoading) {
                                scope.launch {
                                    isLoading = true
                                    actionMessage = associatingMsg
                                    isError = false
                                    try {
                                        val newId = client.associate()
                                        associateId = newId
                                        status = client.checkStatus()
                                        actionMessage = "Associated successfully with ID: $newId"
                                    } catch (e: Exception) {
                                        actionMessage = e.message ?: "Association failed"
                                        isError = true
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("keepassxc-associate-btn"),
                    ) {
                        Text(Strings.t("keepassxc_associate"), color = ClapiColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (actionMessage != null) {
            Text(
                text = actionMessage!!,
                color = if (isError) ClapiColors.Error else ClapiColors.Primary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp).testTag("keepassxc-action-message"),
            )
        }
    }

    SettingsDivider()

    // Proxy Path Display
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(Strings.t("keepassxc_proxy_path"), fontSize = 13.sp, color = ClapiColors.OnSurface)
            if (proxyPath.isNotBlank() && proxyPath != "-") {
                var copied by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            copyToClipboard(proxyPath)
                            copied = true
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("keepassxc-copy-proxy-path"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_content_copy),
                        contentDescription = "Copy proxy path",
                        tint = ClapiColors.Primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        if (copied) Strings.t("oauth2_token_copied") else Strings.t("copy"),
                        color = ClapiColors.Primary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Text(
            Strings.t("keepassxc_proxy_path_hint"),
            fontSize = 11.sp,
            color = ClapiColors.OnSurfaceDim,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("keepassxc-proxy-path-val"),
        ) {
            SelectionContainer {
                Text(
                    text = proxyPath.ifBlank { "—" },
                    fontSize = 12.sp,
                    fontFamily = CodeFontFamily,
                    color = ClapiColors.OnSurface,
                )
            }
        }
    }
}
