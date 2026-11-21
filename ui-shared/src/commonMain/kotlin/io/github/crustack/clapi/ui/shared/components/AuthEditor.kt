package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenResponse
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenState
import io.github.crustack.clapi.ui.shared.components.executor.resolveExternalSecretsForTab
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.crustack.clapi.ui.shared.generated.resources.ic_visibility
import io.github.crustack.clapi.ui.shared.generated.resources.ic_visibility_off
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.copyToClipboard
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * Editor panel for the request authentication configuration.
 * Shows auth-type selector chips and type-specific input fields.
 */
@Composable
fun AuthEditor(
    tab: RequestTabState,
    state: AppState,
    onDirty: () -> Unit,
) {
    AuthEditor(
        tab = tab,
        state = state,
        tokenClient = remember { KtorOAuth2TokenClient() },
        onDirty = onDirty,
    )
}

@Composable
fun AuthEditor(
    tab: RequestTabState,
    state: AppState,
    tokenClient: OAuth2TokenClient,
    onDirty: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val hasFolderAncestor = !tab.isCollectionTab && (tab.folderPath.isNotEmpty() || tab.collectionId != null || state.getAncestorFolders(tab).isNotEmpty())
    val selectableAuthTypes = AuthType.entries.filterNot {
        !hasFolderAncestor && it == AuthType.INHERIT
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Auth-type selector
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            selectableAuthTypes.forEach { at ->
                val selected = at == tab.authType
                Text(
                    text = at.name.replace('_', ' '),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ClapiColors.SelectedItem else Color.Transparent)
                        .clickable {
                            if (at != tab.authType) {
                                tab.authType = at
                                onDirty()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("auth-chip-${at.name.lowercase()}"),
                )
            }
        }

        // Type-specific fields
        when (tab.authType) {
            AuthType.NONE -> {
                Text(Strings.t("no_authentication"), color = ClapiColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
            AuthType.BASIC -> {
                LabeledTextField(Strings.t("username"), tab.authUsername, state = state, tab = tab) { tab.authUsername = it; onDirty() }
                PasswordLabeledTextField(Strings.t("password"), tab.authPassword, state = state, testTag = "auth-password-field") { tab.authPassword = it; onDirty() }
            }
            AuthType.BEARER, AuthType.JWT -> {
                LabeledTextField(Strings.t("token"), tab.authToken, state = state, tab = tab) { tab.authToken = it; onDirty() }
            }
            AuthType.API_KEY -> {
                LabeledTextField(Strings.t("key_upper"), tab.authApiKey, state = state, tab = tab) { tab.authApiKey = it; onDirty() }
                LabeledTextField(Strings.value, tab.authApiValue, state = state, tab = tab) { tab.authApiValue = it; onDirty() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add to", style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceVariant)
                    listOf("header" to "Header", "query" to "Query").forEach { (placement, label) ->
                        val selected = tab.authApiPlacement == placement
                        Text(
                            label,
                            color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) ClapiColors.SelectedItem else Color.Transparent)
                                .clickable { tab.authApiPlacement = placement; onDirty() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("api-key-placement-$placement"),
                        )
                    }
                }
            }
            AuthType.OAUTH2 -> {
                OAuth2EditorSection(
                    tab = tab,
                    state = state,
                    tokenClient = tokenClient,
                    scope = coroutineScope,
                    onDirty = onDirty,
                )
            }
            AuthType.INHERIT -> {
                val resolved = state.resolveInheritedAuth(tab)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClapiColors.SurfaceContainer)
                        .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                        .testTag("auth-inherit-panel"),
                ) {
                    val isCollectionSource = state.collections.any { it.name == resolved?.folderName && it.isCollection }
                    val sourceLabel = if (isCollectionSource) "collection" else "folder"
                    if (resolved == null || resolved.authType == AuthType.NONE) {
                        Text(
                            text = if (resolved?.folderName?.isNotBlank() == true) "Inheriting from $sourceLabel '${resolved.folderName}'" else "Inheriting from $sourceLabel",
                            style = MaterialTheme.typography.titleSmall,
                            color = ClapiColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${sourceLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} has no authentication configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClapiColors.OnSurfaceDim,
                        )
                    } else {
                        Text(
                            text = "Inheriting from $sourceLabel '${resolved.folderName}' (${resolved.authType.name.replace('_', ' ')})",
                            style = MaterialTheme.typography.titleSmall,
                            color = ClapiColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        when (resolved.authType) {
                            AuthType.BASIC -> {
                                Text("Username: ${resolved.username ?: ""}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                                Text("Password: ••••••••", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                            }
                            AuthType.BEARER, AuthType.JWT -> {
                                Text("Token: ${resolved.token ?: ""}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                            }
                            AuthType.API_KEY -> {
                                Text("Key: ${resolved.apiKey ?: ""}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                                Text("Value: ${resolved.apiValue ?: ""}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                                Text("Placement: ${resolved.apiPlacement ?: "header"}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                            }
                            AuthType.OAUTH2 -> {
                                Text("Flow: ${resolved.oauth2Flow.value}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                                if (!resolved.oauth2AccessTokenUrl.isNullOrBlank()) {
                                    Text("Token URL: ${resolved.oauth2AccessTokenUrl}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                                }
                                if (!resolved.oauth2ClientId.isNullOrBlank()) {
                                    Text("Client ID: ${resolved.oauth2ClientId}", style = MaterialTheme.typography.bodySmall, color = ClapiColors.OnSurface)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OAuth2EditorSection(
    tab: RequestTabState,
    state: AppState,
    tokenClient: OAuth2TokenClient,
    scope: CoroutineScope,
    onDirty: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Flow Selector
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Strings.t("oauth2_grant_type"), style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    OAuth2Flow.CLIENT_CREDENTIALS to Strings.t("oauth2_client_credentials"),
                    OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS to Strings.t("oauth2_password"),
                ).forEach { (flow, label) ->
                    val selected = tab.authOAuth2Flow == flow
                    Text(
                        text = label,
                        color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) ClapiColors.SelectedItem else Color.Transparent)
                            .clickable {
                                if (tab.authOAuth2Flow != flow) {
                                    tab.authOAuth2Flow = flow
                                    onDirty()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("oauth2-flow-${flow.value}"),
                    )
                }
            }
        }

        // Access Token URL
        LabeledTextField(
            label = Strings.t("oauth2_access_token_url"),
            value = tab.authOAuth2AccessTokenUrl,
            state = state,
            tab = tab,
            testTag = "oauth2-access-token-url",
        ) {
            tab.authOAuth2AccessTokenUrl = it
            onDirty()
        }

        // Scope
        LabeledTextField(
            label = Strings.t("oauth2_scope"),
            value = tab.authOAuth2Scope,
            state = state,
            tab = tab,
            testTag = "oauth2-scope",
        ) {
            tab.authOAuth2Scope = it
            onDirty()
        }

        // Client ID
        LabeledTextField(
            label = Strings.t("oauth2_client_id"),
            value = tab.authOAuth2ClientId,
            state = state,
            tab = tab,
            testTag = "oauth2-client-id",
        ) {
            tab.authOAuth2ClientId = it
            onDirty()
        }

        // Client Secret
        PasswordLabeledTextField(
            label = Strings.t("oauth2_client_secret"),
            value = tab.authOAuth2ClientSecret,
            state = state,
            testTag = "oauth2-client-secret-field",
        ) {
            tab.authOAuth2ClientSecret = it
            onDirty()
        }

        // Credentials Placement
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.t("oauth2_credentials_placement"), style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceVariant)
            listOf(
                OAuth2CredentialsPlacement.BASIC_AUTH_HEADER to Strings.t("oauth2_basic_auth_header"),
                OAuth2CredentialsPlacement.BODY to Strings.t("oauth2_request_body"),
            ).forEach { (placement, label) ->
                val selected = tab.authOAuth2CredentialsPlacement == placement
                Text(
                    text = label,
                    color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ClapiColors.SelectedItem else Color.Transparent)
                        .clickable {
                            if (tab.authOAuth2CredentialsPlacement != placement) {
                                tab.authOAuth2CredentialsPlacement = placement
                                onDirty()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("oauth2-placement-${placement.value}"),
                )
            }
        }

        // Resource Owner fields (Password flow only)
        if (tab.authOAuth2Flow == OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS) {
            LabeledTextField(
                label = Strings.t("oauth2_username"),
                value = tab.authOAuth2Username,
                state = state,
                tab = tab,
                testTag = "oauth2-username",
            ) {
                tab.authOAuth2Username = it
                onDirty()
            }
            PasswordLabeledTextField(
                label = Strings.t("oauth2_password_field"),
                value = tab.authOAuth2Password,
                state = state,
                testTag = "oauth2-password-field",
            ) {
                tab.authOAuth2Password = it
                onDirty()
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = ClapiColors.Border)

        // Token Inspection & Testing Section
        OAuth2TokenInspectionSection(
            tab = tab,
            state = state,
            tokenClient = tokenClient,
            scope = scope,
        )
    }
}

@Composable
private fun OAuth2TokenInspectionSection(
    tab: RequestTabState,
    state: AppState,
    tokenClient: OAuth2TokenClient,
    scope: CoroutineScope,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    fetchOAuth2TokensForTab(
                        scope = scope,
                        tab = tab,
                        state = state,
                        client = tokenClient,
                    )
                },
                enabled = !tab.authOAuth2IsFetching,
                colors = ButtonDefaults.buttonColors(containerColor = ClapiColors.Primary),
                modifier = Modifier.testTag("auth-oauth2-fetch-tokens-button"),
            ) {
                if (tab.authOAuth2IsFetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = " " + Strings.t("oauth2_fetching_tokens"),
                        fontSize = 12.sp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = Strings.t("oauth2_fetch_tokens"),
                        fontSize = 12.sp,
                        color = Color.White,
                    )
                }
            }

            // Expiration status
            if (tab.authOAuth2AccessToken.isNotBlank()) {
                val tokenState = OAuth2TokenState(
                    accessToken = tab.authOAuth2AccessToken,
                    expiresAtEpochMs = tab.authOAuth2ExpiresAtEpochMs,
                )
                val isValid = tokenState.isValid()
                val statusText = if (isValid) Strings.t("oauth2_token_status_active") else Strings.t("oauth2_token_status_expired")
                val statusColor = if (isValid) ClapiColors.StatusSuccess else ClapiColors.StatusClientError
                Text(
                    text = "● $statusText",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    modifier = Modifier.testTag("auth-oauth2-status"),
                )
            } else {
                Text(
                    text = Strings.t("oauth2_token_status_none"),
                    fontSize = 12.sp,
                    color = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.testTag("auth-oauth2-status"),
                )
            }
        }

        // Error message if fetch failed
        if (tab.authOAuth2FetchError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF3B1E1E))
                    .border(1.dp, Color(0xFF8B2525), RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .testTag("auth-oauth2-error-banner"),
            ) {
                Text(
                    text = tab.authOAuth2FetchError ?: "",
                    color = Color(0xFFFF8B8B),
                    fontSize = 12.sp,
                )
            }
        }

        // Copyable Access Token
        if (tab.authOAuth2AccessToken.isNotBlank()) {
            CopyableTokenField(
                label = Strings.t("oauth2_access_token"),
                token = tab.authOAuth2AccessToken,
                testTag = "auth-oauth2-access-token",
                copyTestTag = "auth-oauth2-copy-access-token",
            )
        }

        // Copyable Refresh Token
        if (tab.authOAuth2RefreshToken.isNotBlank()) {
            CopyableTokenField(
                label = Strings.t("oauth2_refresh_token"),
                token = tab.authOAuth2RefreshToken,
                testTag = "auth-oauth2-refresh-token",
                copyTestTag = "auth-oauth2-copy-refresh-token",
            )
        }

        // Copyable ID Token
        if (tab.authOAuth2IdToken.isNotBlank()) {
            CopyableTokenField(
                label = Strings.t("oauth2_id_token"),
                token = tab.authOAuth2IdToken,
                testTag = "auth-oauth2-id-token",
                copyTestTag = "auth-oauth2-copy-id-token",
            )
        }
    }
}

@Composable
fun CopyableTokenField(
    label: String,
    token: String,
    testTag: String,
    copyTestTag: String,
) {
    var copied by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceVariant)
            if (copied) {
                Text(
                    text = Strings.t("oauth2_token_copied"),
                    fontSize = 11.sp,
                    color = ClapiColors.StatusSuccess,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = token,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ClapiColors.Primary),
                modifier = Modifier
                    .weight(1f)
                    .testTag(testTag),
            )
            IconButton(
                onClick = {
                    if (token.isNotBlank()) {
                        copyToClipboard(token)
                        copied = true
                    }
                },
                modifier = Modifier.size(32.dp).testTag(copyTestTag),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_content_copy),
                    contentDescription = "Copy token",
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

fun fetchOAuth2TokensForTab(
    scope: CoroutineScope,
    tab: RequestTabState,
    state: AppState,
    client: OAuth2TokenClient,
    onComplete: (() -> Unit)? = null,
) {
    if (tab.authOAuth2IsFetching) return
    tab.authOAuth2IsFetching = true
    tab.authOAuth2FetchError = null

    scope.launch {
        try {
            val textsToScan = listOf(
                tab.authOAuth2ClientId,
                tab.authOAuth2ClientSecret,
                tab.authOAuth2Username,
                tab.authOAuth2Password,
                tab.authOAuth2AccessTokenUrl,
                tab.authOAuth2RefreshTokenUrl,
                tab.authOAuth2Scope,
            )
            val resolvedSecrets = resolveExternalSecretsForTab(state, tab, textsToScan) ?: emptyMap()
            val variableLayers = if (resolvedSecrets.isNotEmpty()) {
                listOf(resolvedSecrets) + state.activeVariableLayers(tab)
            } else {
                state.activeVariableLayers(tab)
            }

            val resolvedTokenUrl = VariableResolver.resolve(tab.authOAuth2AccessTokenUrl, variableLayers)
            val resolvedClientId = VariableResolver.resolve(tab.authOAuth2ClientId, variableLayers)
            val resolvedClientSecret = VariableResolver.resolve(tab.authOAuth2ClientSecret, variableLayers)
            val resolvedScope = VariableResolver.resolve(tab.authOAuth2Scope, variableLayers).ifBlank { null }

            val result: Result<OAuth2TokenResponse> = when (tab.authOAuth2Flow) {
                OAuth2Flow.CLIENT_CREDENTIALS -> {
                    client.fetchClientCredentialsToken(
                        tokenUrl = resolvedTokenUrl,
                        clientId = resolvedClientId,
                        clientSecret = resolvedClientSecret,
                        placement = tab.authOAuth2CredentialsPlacement,
                        scope = resolvedScope,
                    )
                }
                OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS -> {
                    val resolvedUsername = VariableResolver.resolve(tab.authOAuth2Username, variableLayers)
                    val resolvedPassword = VariableResolver.resolve(tab.authOAuth2Password, variableLayers)
                    client.fetchPasswordToken(
                        tokenUrl = resolvedTokenUrl,
                        clientId = resolvedClientId,
                        clientSecret = resolvedClientSecret,
                        placement = tab.authOAuth2CredentialsPlacement,
                        username = resolvedUsername,
                        password = resolvedPassword,
                        scope = resolvedScope,
                    )
                }
                else -> {
                    Result.failure(IllegalArgumentException("Unsupported OAuth2 flow: ${tab.authOAuth2Flow.value}"))
                }
            }

            result.fold(
                onSuccess = { resp ->
                    tab.authOAuth2AccessToken = resp.accessToken
                    tab.authOAuth2RefreshToken = resp.refreshToken.orEmpty()
                    tab.authOAuth2IdToken = resp.idToken.orEmpty()
                    val tokenState = OAuth2TokenState.fromResponse(resp)
                    tab.authOAuth2ExpiresAtEpochMs = tokenState.expiresAtEpochMs
                    tab.authOAuth2FetchError = null
                    state.log("OAuth2 tokens fetched successfully for '${tab.name}'")
                },
                onFailure = { err ->
                    val msg = err.message ?: "Failed to fetch OAuth2 tokens"
                    tab.authOAuth2FetchError = msg
                    state.log("OAuth2 token fetch failed: $msg", LogLevel.ERROR)
                }
            )
        } catch (t: Throwable) {
            val msg = t.message ?: "Unexpected error fetching OAuth2 tokens"
            tab.authOAuth2FetchError = msg
            state.log("OAuth2 token fetch exception: $msg", LogLevel.ERROR)
        } finally {
            tab.authOAuth2IsFetching = false
            onComplete?.invoke()
        }
    }
}

// ── Labeled text field helpers ───────────────────────────────────

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    state: AppState,
    tab: RequestTabState? = state.activeTab,
    testTag: String? = null,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceVariant)
        VariableAwareTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            state = state,
            tab = tab,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            placeholder = "Enter $label…",
        )
    }
}

/**
 * Password field with reveal/hide eye toggle.
 */
@Composable
fun PasswordLabeledTextField(
    label: String,
    value: String,
    state: AppState,
    testTag: String = "auth-password-field",
    onValueChange: (String) -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ClapiColors.Primary),
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    if (value.isEmpty() && !showPassword) {
                        Text(
                            text = "••••••••",
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 13.sp,
                        )
                    } else if (value.isEmpty()) {
                        Text(
                            text = "Enter $label…",
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                },
            )
            IconButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier.size(36.dp).padding(end = 4.dp),
            ) {
                Icon(
                    painter = if (showPassword) painterResource(Res.drawable.ic_visibility_off) else painterResource(Res.drawable.ic_visibility),
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
