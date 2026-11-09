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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import io.github.crustack.clapi.ui.shared.theme.ClapiColors

/**
 * Editor panel for the request authentication configuration.
 * Shows auth-type selector chips and type-specific input fields.
 */
@Composable
fun AuthEditor(tab: RequestTabState, state: AppState, onDirty: () -> Unit) {
    val hasFolderAncestor = !tab.isCollectionTab && (tab.folderPath.isNotEmpty() || tab.collectionId != null || state.getAncestorFolders(tab).isNotEmpty())
    val selectableAuthTypes = AuthType.entries.filterNot {
        it == AuthType.OAUTH2 || (!hasFolderAncestor && it == AuthType.INHERIT)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
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
                // H-4: Use a password field with show/hide toggle so the password is masked
                PasswordLabeledTextField(Strings.t("password"), tab.authPassword, state = state) { tab.authPassword = it; onDirty() }
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
                Text(Strings.t("oauth2_coming_soon"), color = ClapiColors.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
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
                    if (resolved == null || resolved.authType == AuthType.NONE) {
                        Text(
                            text = if (resolved?.folderName?.isNotBlank() == true) "Inheriting from folder ${resolved.folderName}" else "Inheriting from folder",
                            style = MaterialTheme.typography.titleSmall,
                            color = ClapiColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Folder has no authentication configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClapiColors.OnSurfaceDim,
                        )
                    } else {
                        Text(
                            text = "Inheriting from folder ${resolved.folderName} (${resolved.authType.name.replace('_', ' ')})",
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
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

// ── Labeled text field helper ───────────────────────────────────

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    state: AppState,
    tab: RequestTabState? = state.activeTab,
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            placeholder = "Enter $label…",
        )
    }
}

/**
 * H-4: Password field with a reveal/hide eye toggle.
 * Characters are masked by default; clicking the eye icon toggles plaintext display.
 * This mirrors the "Secret" toggle in EnvironmentEditDialog.
 * Uses BasicTextField directly (not VariableAwareTextField) because
 * PasswordVisualTransformation is incompatible with the annotation-based variable highlighting.
 */
@Composable
fun PasswordLabeledTextField(
    label: String,
    value: String,
    state: AppState,
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
                .testTag("auth-password-field"),
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
                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
