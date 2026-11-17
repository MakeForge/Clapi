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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import io.github.makeforge.clapi.core.network.aws.KtorAwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.azure.KtorAzureKeyVaultClient
import io.github.makeforge.clapi.core.network.gcp.DefaultGcpTokenProvider
import io.github.makeforge.clapi.core.network.gcp.GcpAuthMethod
import io.github.makeforge.clapi.core.network.gcp.KtorGcpSecretManagerClient
import io.github.makeforge.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.makeforge.clapi.core.network.keepassxc.KeePassXcStatus
import io.github.makeforge.clapi.core.network.keepassxc.createKeePassXcClient
import io.github.makeforge.clapi.core.network.keeper.createKeeperClient
import io.github.makeforge.clapi.core.network.keeper.initializeAndBindKeeperStorage
import io.github.makeforge.clapi.ui.shared.components.DocsHelpButton
import io.github.makeforge.clapi.ui.shared.components.externalSecretIcon
import io.github.makeforge.clapi.ui.shared.components.externalSecretSetupUrl
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.components.IconButtonWithTooltip
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import io.github.makeforge.clapi.ui.shared.i18n.AppLanguage
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.platform.DocsLinks
import io.github.makeforge.clapi.ui.shared.platform.*
import io.github.makeforge.clapi.ui.shared.state.*
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SecretsSettings(s: AppSettings) {
    val setupUrl = externalSecretSetupUrl(s.vaultProviderType)

    SettingDropdown(
        label = Strings.t("secret_provider"),
        description = Strings.t("secret_provider_desc"),
        options = listOf(
            ExternalSecretType.HASHICORP_VAULT_SERVER to "HashiCorp Vault (Server)",
            ExternalSecretType.KEEPASSXC to "KeePassXC",
            ExternalSecretType.AWS_SECRETS_MANAGER to "AWS Secrets Manager",
            ExternalSecretType.AZURE_KEY_VAULT to "Azure Key Vault",
            ExternalSecretType.GCP_SECRETS_MANAGER to "GCP Secret Manager",
            ExternalSecretType.KEEPER_SECURITY_CLOUD to "Keeper Security Cloud",
        ),
        selected = s.vaultProviderType,
        onSelected = { s.vaultProviderType = it },
        tagPrefix = "secrets-provider",
        optionIcon = { externalSecretIcon(it) },
        headerAction = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                setupUrl?.let { url ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { openUrlInBrowser(url) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("secret-manager-setup-btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_open_in_new),
                            contentDescription = "Setup Secrets",
                            tint = ClapiColors.Primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Setup Secrets",
                            fontSize = 11.sp,
                            color = ClapiColors.Primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                DocsHelpButton(
                    url = DocsLinks.secretManagers,
                    tooltip = "Secret Managers documentation",
                    tag = "secret-managers-docs-btn",
                    size = 20.dp,
                    iconSize = 14.dp,
                )
            }
        },
    )
    SettingsDivider()

    when (s.vaultProviderType) {
        ExternalSecretType.KEEPASSXC -> KeePassXcSettings()
        ExternalSecretType.AWS_SECRETS_MANAGER -> AwsSecretsSettings(s)
        ExternalSecretType.AZURE_KEY_VAULT -> AzureKeyVaultSettings(s)
        ExternalSecretType.GCP_SECRETS_MANAGER -> GcpSecretsSettings(s)
        ExternalSecretType.KEEPER_SECURITY_CLOUD -> KeeperSecretsSettings(s)
        else -> VaultSettings(s)
    }
}

@Composable
internal fun SecretManagerConnectionSection(
    statusText: String,
    statusColor: Color,
    statusBg: Color,
    statusBorder: Color,
    isTesting: Boolean,
    onTestConnection: () -> Unit,
    tagPrefix: String,
    title: String = "Connection Status",
    subtitle: String? = null,
    actionMessage: String? = null,
    isError: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = ClapiColors.OnSurface,
            fontWeight = FontWeight.Medium,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = ClapiColors.OnSurfaceDim,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusBg)
                    .border(1.dp, statusBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("$tagPrefix-status-badge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                    .clickable(enabled = !isTesting) { onTestConnection() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("$tagPrefix-test-connection-btn"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = "Test Connection",
                        tint = if (isTesting) ClapiColors.OnSurfaceDim else ClapiColors.Primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = if (isTesting) "Testing..." else "Test Connection",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isTesting) ClapiColors.OnSurfaceDim else ClapiColors.Primary,
                    )
                }
            }
        }

        if (!actionMessage.isNullOrBlank()) {
            SelectionContainer {
                Text(
                    text = actionMessage,
                    fontSize = 11.sp,
                    color = if (isError) ClapiColors.Error else ClapiColors.Primary,
                    modifier = Modifier.padding(top = 2.dp).testTag("$tagPrefix-action-message"),
                )
            }
        }
    }
}

@Composable
internal fun GcpSecretsSettings(s: AppSettings) {
    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Not Configured") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val hasCreds = s.gcpAccessToken.isNotBlank() || DefaultGcpTokenProvider.hasCredentials(
        serviceAccountKey = s.gcpServiceAccountKey,
        authMethod = s.gcpAuthMethod,
        adcFilePath = s.gcpAdcFilePath,
    )

    fun runTest() {
        if (!hasCreds) {
            connectionStatus = "Not Configured"
            actionMessage = "Please provide GCP credentials"
            isError = true
            return
        }
        scope.launch {
            isTesting = true
            connectionStatus = "Testing..."
            actionMessage = null
            val result = KtorGcpSecretManagerClient.Shared.testConnection(
                projectId = s.gcpProjectId,
                serviceAccountKey = s.gcpServiceAccountKey,
                authMethod = s.gcpAuthMethod,
                adcFilePath = s.gcpAdcFilePath,
                accessToken = s.gcpAccessToken,
                endpointUrl = s.gcpEndpointUrl.ifBlank { "https://secretmanager.googleapis.com" },
            )
            result.onSuccess { msg ->
                connectionStatus = "Connected"
                actionMessage = msg
                isError = false
            }.onFailure { err ->
                connectionStatus = "Connection Failed"
                actionMessage = err.message ?: "Connection failed"
                isError = true
            }
            isTesting = false
        }
    }

    LaunchedEffect(s.gcpProjectId, s.gcpAccessToken, s.gcpServiceAccountKey, s.gcpAuthMethod, s.gcpAdcFilePath, s.gcpEndpointUrl) {
        if (hasCreds) {
            runTest()
        } else {
            connectionStatus = "Not Configured"
            actionMessage = null
            isError = false
        }
    }

    val (statusBg, statusBorder, statusFg) = when (connectionStatus) {
        "Connected" -> Triple(ClapiColors.Primary.copy(alpha = 0.15f), ClapiColors.Primary.copy(alpha = 0.4f), ClapiColors.Primary)
        "Connection Failed" -> Triple(ClapiColors.Error.copy(alpha = 0.15f), ClapiColors.Error.copy(alpha = 0.4f), ClapiColors.Error)
        "Testing..." -> Triple(ClapiColors.SurfaceContainer, ClapiColors.Border, ClapiColors.OnSurfaceDim)
        else -> Triple(ClapiColors.SurfaceContainer, ClapiColors.Border, ClapiColors.OnSurfaceDim)
    }

    SecretManagerConnectionSection(
        statusText = connectionStatus,
        statusColor = statusFg,
        statusBg = statusBg,
        statusBorder = statusBorder,
        isTesting = isTesting,
        onTestConnection = { runTest() },
        tagPrefix = "gcp",
        title = "Connection Status",
        subtitle = if (hasCreds) "Google Cloud Secret Manager" else "Configure credentials below to connect",
        actionMessage = actionMessage,
        isError = isError,
    )
    SettingsDivider()

    SettingTextField(
        label = Strings.t("gcp_project_id"),
        value = s.gcpProjectId,
        onValueChange = { s.gcpProjectId = it },
        placeholder = "my-cloud-project",
        tag = "gcp-project-id",
    )
    SettingsDivider()
    SettingDropdown(
        label = "Authentication Method",
        description = "Select how to authenticate with Google Cloud",
        options = listOf(
            GcpAuthMethod.SERVICE_ACCOUNT_KEY to "Service Account Key (JSON / File)",
            GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS to "Application Default Credentials (ADC)",
        ),
        selected = s.gcpAuthMethod,
        onSelected = { s.gcpAuthMethod = it },
        tagPrefix = "gcp-auth-method",
    )
    SettingsDivider()
    when (s.gcpAuthMethod) {
        GcpAuthMethod.SERVICE_ACCOUNT_KEY -> {
            SettingPasswordField(
                label = "Service Account Key",
                value = s.gcpServiceAccountKey,
                onValueChange = { s.gcpServiceAccountKey = it },
                placeholder = "Paste service account JSON or enter key file path",
                tag = "gcp-service-account-key",
            )
        }
        GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS -> {
            SettingTextField(
                label = "Credentials File Path (Optional)",
                value = s.gcpAdcFilePath,
                onValueChange = { s.gcpAdcFilePath = it },
                placeholder = "Default: ~/.config/gcloud/application_default_credentials.json",
                tag = "gcp-adc-file-path",
            )
            Text(
                text = "Credentials from 'gcloud auth application-default login' (or GOOGLE_APPLICATION_CREDENTIALS) are used automatically.",
                fontSize = 11.sp,
                color = ClapiColors.OnSurfaceDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    SettingsDivider()
    SettingTextField(
        label = Strings.t("gcp_endpoint_url"),
        value = s.gcpEndpointUrl,
        onValueChange = { s.gcpEndpointUrl = it },
        placeholder = "https://secretmanager.googleapis.com",
        tag = "gcp-endpoint-url",
    )
}

private data class KeeperStatusStyle(val text: String, val bg: Color, val border: Color, val fg: Color)

@Composable
internal fun KeeperSecretsSettings(s: AppSettings) {
    val scope = rememberCoroutineScope()
    var oneTimeToken by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var isCheckingHealth by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val hasConfig = s.keeperConfigJson.isNotBlank()

    LaunchedEffect(s.keeperConfigJson) {
        if (hasConfig) {
            isCheckingHealth = true
            s.keeperConnectionStatus = KeeperConnectionStatus.CHECKING
            try {
                val client = createKeeperClient(s.keeperConfigJson)
                val ok = client.testConnection()
                s.keeperConnectionStatus = if (ok) KeeperConnectionStatus.CONNECTED else KeeperConnectionStatus.ERROR
            } catch (_: Exception) {
                s.keeperConnectionStatus = KeeperConnectionStatus.ERROR
            } finally {
                isCheckingHealth = false
            }
        } else {
            s.keeperConnectionStatus = KeeperConnectionStatus.DISCONNECTED
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Status header & description
        Text(
            text = "Connection Status",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ClapiColors.OnSurface,
        )
        Text(
            text = if (hasConfig) "Application credentials bound via Keeper Secrets Manager" else "No active Keeper client configuration",
            fontSize = 11.sp,
            color = ClapiColors.OnSurfaceDim,
        )

        val style = when {
            isConnecting -> KeeperStatusStyle(
                "Connecting...",
                ClapiColors.SurfaceContainer,
                ClapiColors.Border,
                ClapiColors.OnSurfaceDim
            )
            isCheckingHealth || s.keeperConnectionStatus == KeeperConnectionStatus.CHECKING -> KeeperStatusStyle(
                "Checking...",
                ClapiColors.SurfaceContainer,
                ClapiColors.Border,
                ClapiColors.OnSurfaceDim
            )
            s.keeperConnectionStatus == KeeperConnectionStatus.CONNECTED -> KeeperStatusStyle(
                "Connected",
                ClapiColors.Primary.copy(alpha = 0.15f),
                ClapiColors.Primary.copy(alpha = 0.4f),
                ClapiColors.Primary
            )
            s.keeperConnectionStatus == KeeperConnectionStatus.ERROR -> KeeperStatusStyle(
                "Connection Failed",
                ClapiColors.Error.copy(alpha = 0.15f),
                ClapiColors.Error.copy(alpha = 0.4f),
                ClapiColors.Error
            )
            else -> KeeperStatusStyle(
                "Not Connected",
                ClapiColors.SurfaceContainer,
                ClapiColors.Border,
                ClapiColors.OnSurfaceDim
            )
        }

        // Status badge + test connection + disconnect row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(style.bg)
                    .border(1.dp, style.border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("keeper-status-badge"),
            ) {
                Text(
                    text = style.text,
                    color = style.fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                    .clickable(enabled = !isCheckingHealth && !isConnecting) {
                        if (!hasConfig) {
                            actionMessage = "No active configuration. Please enter a one-time access token below to connect."
                            isError = true
                            return@clickable
                        }
                        scope.launch {
                            isCheckingHealth = true
                            s.keeperConnectionStatus = KeeperConnectionStatus.CHECKING
                            try {
                                val client = createKeeperClient(s.keeperConfigJson)
                                val ok = client.testConnection()
                                s.keeperConnectionStatus = if (ok) KeeperConnectionStatus.CONNECTED else KeeperConnectionStatus.ERROR
                                actionMessage = if (ok) "Connected to Keeper Secrets Manager" else "Connection failed"
                                isError = !ok
                            } catch (e: Exception) {
                                s.keeperConnectionStatus = KeeperConnectionStatus.ERROR
                                actionMessage = e.message ?: "Connection failed"
                                isError = true
                            } finally {
                                isCheckingHealth = false
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("keeper-test-connection-btn"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = "Test Connection",
                        tint = if (isCheckingHealth || isConnecting) ClapiColors.OnSurfaceDim else ClapiColors.Primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = if (isCheckingHealth) "Testing..." else "Test Connection",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isCheckingHealth || isConnecting) ClapiColors.OnSurfaceDim else ClapiColors.Primary,
                    )
                }
            }

            if (hasConfig) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                        .clickable {
                            s.keeperConfigJson = ""
                            s.keeperConnectionStatus = KeeperConnectionStatus.DISCONNECTED
                            SettingsRepository.save(s)
                            actionMessage = "Configuration removed"
                            isError = false
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .testTag("keeper-disconnect-btn"),
                ) {
                    Text("Disconnect", fontSize = 11.sp, color = ClapiColors.OnSurfaceVariant)
                }
            }
        }

        SettingsDivider()

        // One-time token input
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (hasConfig) "Re-connect with New Token" else "One-Time Access Token",
                fontSize = 13.sp,
                color = ClapiColors.OnSurface,
            )
            Text(
                text = "One-time token is redeemed immediately to bind configuration and is never stored.",
                fontSize = 11.sp,
                color = ClapiColors.OnSurfaceDim,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = oneTimeToken,
                    onValueChange = { oneTimeToken = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = ClapiColors.OnSurface, fontSize = 12.sp),
                    cursorBrush = SolidColor(ClapiColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("keeper-one-time-token"),
                    decorationBox = { innerTextField ->
                        if (oneTimeToken.isEmpty()) {
                            Text(
                                "US:XXXX... or XXXX...",
                                color = ClapiColors.OnSurfaceDim,
                                fontSize = 12.sp,
                            )
                        }
                        innerTextField()
                    },
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (oneTimeToken.isNotBlank() && !isConnecting) ClapiColors.Primary else ClapiColors.SurfaceContainer)
                        .border(1.dp, if (oneTimeToken.isNotBlank() && !isConnecting) ClapiColors.Primary else ClapiColors.Border, RoundedCornerShape(6.dp))
                        .clickable(enabled = oneTimeToken.isNotBlank() && !isConnecting) {
                            scope.launch {
                                isConnecting = true
                                isError = false
                                actionMessage = "Connecting and binding storage..."
                                try {
                                    val boundConfig = initializeAndBindKeeperStorage(oneTimeToken.trim())
                                    s.keeperConfigJson = boundConfig
                                    SettingsRepository.save(s)
                                    s.keeperConnectionStatus = KeeperConnectionStatus.CONNECTED
                                    oneTimeToken = ""
                                    actionMessage = "Successfully connected to Keeper Secrets Manager"
                                } catch (e: Exception) {
                                    isError = true
                                    actionMessage = e.message ?: "Failed to initialize Keeper storage"
                                    s.keeperConnectionStatus = KeeperConnectionStatus.ERROR
                                } finally {
                                    isConnecting = false
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("keeper-connect-btn"),
                ) {
                    Text(
                        text = if (isConnecting) "Connecting..." else "Connect",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (oneTimeToken.isNotBlank() && !isConnecting) ClapiColors.OnPrimary else ClapiColors.OnSurfaceDim,
                    )
                }
            }
        }

        // Instructions Hint and Docs link
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = "To get a one time token: Login to Keeper > Secrets Manager > Create Application > Generate Access Token (Read Only Record Permissions are enough)",
                    fontSize = 11.sp,
                    color = ClapiColors.OnSurfaceDim,
                )
            }

            DocsHelpButton(
                url = "https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#create-a-secrets-manager-application",
                tooltip = "Help Documentation",
                tag = "keeper-docs-link-btn",
                size = 24.dp,
                iconSize = 16.dp,
                tint = ClapiColors.Primary,
            )
        }

        if (actionMessage != null) {
            SelectionContainer {
                Text(
                    text = actionMessage.orEmpty(),
                    fontSize = 11.sp,
                    color = if (isError) ClapiColors.Error else ClapiColors.Primary,
                    modifier = Modifier.testTag("keeper-action-message"),
                )
            }
        }
    }
}

@Composable
internal fun AzureKeyVaultSettings(s: AppSettings) {
    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Not Configured") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val hasCreds = s.azureTenantId.isNotBlank() && s.azureClientId.isNotBlank() && s.azureClientSecret.isNotBlank()

    fun runTest() {
        if (!hasCreds) {
            connectionStatus = "Not Configured"
            actionMessage = "Please provide Azure Tenant ID, Client ID, and Client Secret"
            isError = true
            return
        }
        scope.launch {
            isTesting = true
            connectionStatus = "Testing..."
            actionMessage = null
            val result = KtorAzureKeyVaultClient.Shared.testConnection(
                vaultName = s.azureVaultName,
                tenantId = s.azureTenantId,
                clientId = s.azureClientId,
                clientSecret = s.azureClientSecret,
                authorityHost = s.azureAuthorityHost.ifBlank { "https://login.microsoftonline.com" },
                apiVersion = s.azureApiVersion.ifBlank { "7.4" },
            )
            result.onSuccess { msg ->
                connectionStatus = "Connected"
                actionMessage = msg
                isError = false
            }.onFailure { err ->
                connectionStatus = "Connection Failed"
                actionMessage = err.message ?: "Connection failed"
                isError = true
            }
            isTesting = false
        }
    }

    LaunchedEffect(s.azureVaultName, s.azureTenantId, s.azureClientId, s.azureClientSecret, s.azureAuthorityHost, s.azureApiVersion) {
        if (hasCreds) {
            runTest()
        } else {
            connectionStatus = "Not Configured"
            actionMessage = null
            isError = false
        }
    }

    val (statusBg, statusBorder, statusFg) = when (connectionStatus) {
        "Connected" -> Triple(ClapiColors.Primary.copy(alpha = 0.15f), ClapiColors.Primary.copy(alpha = 0.4f), ClapiColors.Primary)
        "Connection Failed" -> Triple(ClapiColors.Error.copy(alpha = 0.15f), ClapiColors.Error.copy(alpha = 0.4f), ClapiColors.Error)
        "Testing..." -> Triple(ClapiColors.SurfaceContainer, ClapiColors.Border, ClapiColors.OnSurfaceDim)
        else -> Triple(ClapiColors.SurfaceContainer, ClapiColors.Border, ClapiColors.OnSurfaceDim)
    }

    SecretManagerConnectionSection(
        statusText = connectionStatus,
        statusColor = statusFg,
        statusBg = statusBg,
        statusBorder = statusBorder,
        isTesting = isTesting,
        onTestConnection = { runTest() },
        tagPrefix = "azure",
        title = "Connection Status",
        subtitle = if (hasCreds) "Azure Key Vault" else "Configure credentials below to connect",
        actionMessage = actionMessage,
        isError = isError,
    )
    SettingsDivider()

    SettingTextField(
        label = Strings.t("azure_vault_name"),
        value = s.azureVaultName,
        onValueChange = { s.azureVaultName = it },
        placeholder = "https://my-vault.vault.azure.net",
        tag = "azure-vault-name",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("azure_tenant_id"),
        value = s.azureTenantId,
        onValueChange = { s.azureTenantId = it },
        placeholder = "00000000-0000-0000-0000-000000000000",
        tag = "azure-tenant-id",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("azure_client_id"),
        value = s.azureClientId,
        onValueChange = { s.azureClientId = it },
        placeholder = "Client (Application) ID",
        tag = "azure-client-id",
    )
    SettingsDivider()
    SettingPasswordField(
        label = Strings.t("azure_client_secret"),
        value = s.azureClientSecret,
        onValueChange = { s.azureClientSecret = it },
        placeholder = "••••••••••••••••",
        tag = "azure-client-secret",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("azure_authority_host"),
        value = s.azureAuthorityHost,
        onValueChange = { s.azureAuthorityHost = it },
        placeholder = "https://login.microsoftonline.com",
        tag = "azure-authority-host",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("azure_api_version"),
        value = s.azureApiVersion,
        onValueChange = { s.azureApiVersion = it },
        placeholder = "7.4",
        tag = "azure-api-version",
    )
}

@Composable
internal fun AwsSecretsSettings(s: AppSettings) {
    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Not Configured") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val hasCreds = s.awsAccessKeyId.isNotBlank() && s.awsSecretAccessKey.isNotBlank()

    fun runTest() {
        if (!hasCreds) {
            connectionStatus = "Not Configured"
            actionMessage = "Please provide AWS Access Key ID and Secret Access Key"
            isError = true
            return
        }
        scope.launch {
            isTesting = true
            connectionStatus = "Testing..."
            actionMessage = null
            val result = KtorAwsSecretsManagerClient.Shared.testConnection(
                accessKey = s.awsAccessKeyId,
                secretKey = s.awsSecretAccessKey,
                region = s.awsRegion.ifBlank { "us-east-1" },
                sessionToken = s.awsSessionToken.ifBlank { null },
                endpointUrl = s.awsEndpointUrl.ifBlank { null },
            )
            result.onSuccess { msg ->
                connectionStatus = "Connected"
                actionMessage = msg
                isError = false
            }.onFailure { err ->
                connectionStatus = "Connection Failed"
                actionMessage = err.message ?: "Connection failed"
                isError = true
            }
            isTesting = false
        }
    }

    LaunchedEffect(s.awsAccessKeyId, s.awsSecretAccessKey, s.awsRegion, s.awsSessionToken, s.awsEndpointUrl) {
        if (hasCreds) {
            runTest()
        } else {
            connectionStatus = "Not Configured"
            actionMessage = null
            isError = false
        }
    }

    val (statusBg, statusBorder, statusFg) = when (connectionStatus) {
        "Connected" -> Triple(ClapiColors.Primary.copy(alpha = 0.15f), ClapiColors.Primary.copy(alpha = 0.4f), ClapiColors.Primary)
        "Connection Failed" -> Triple(ClapiColors.Error.copy(alpha = 0.15f), ClapiColors.Error.copy(alpha = 0.4f), ClapiColors.Error)
        "Testing..." -> Triple(ClapiColors.SurfaceContainer, ClapiColors.Border, ClapiColors.OnSurfaceDim)
        else -> Triple(ClapiColors.SurfaceContainer, ClapiColors.Border, ClapiColors.OnSurfaceDim)
    }

    SecretManagerConnectionSection(
        statusText = connectionStatus,
        statusColor = statusFg,
        statusBg = statusBg,
        statusBorder = statusBorder,
        isTesting = isTesting,
        onTestConnection = { runTest() },
        tagPrefix = "aws",
        title = "Connection Status",
        subtitle = if (hasCreds) "AWS Secrets Manager" else "Configure credentials below to connect",
        actionMessage = actionMessage,
        isError = isError,
    )
    SettingsDivider()

    SettingTextField(
        label = Strings.t("aws_region"),
        value = s.awsRegion,
        onValueChange = { s.awsRegion = it },
        placeholder = "us-east-1",
        tag = "aws-region",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("aws_access_key_id"),
        value = s.awsAccessKeyId,
        onValueChange = { s.awsAccessKeyId = it },
        placeholder = "AKIA...",
        tag = "aws-access-key-id",
    )
    SettingsDivider()
    SettingPasswordField(
        label = Strings.t("aws_secret_access_key"),
        value = s.awsSecretAccessKey,
        onValueChange = { s.awsSecretAccessKey = it },
        placeholder = "••••••••••••••••",
        tag = "aws-secret-access-key",
    )
    SettingsDivider()
    SettingPasswordField(
        label = Strings.t("aws_session_token"),
        value = s.awsSessionToken,
        onValueChange = { s.awsSessionToken = it },
        placeholder = "Optional STS Token",
        tag = "aws-session-token",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("aws_endpoint_url"),
        value = s.awsEndpointUrl,
        onValueChange = { s.awsEndpointUrl = it },
        placeholder = "http://localhost:4566",
        tag = "aws-endpoint-url",
    )
    SettingsDivider()
    SettingTextField(
        label = Strings.t("aws_version_stage"),
        value = s.awsVersionStage,
        onValueChange = { s.awsVersionStage = it },
        placeholder = "AWSCURRENT",
        tag = "aws-version-stage",
    )
}
