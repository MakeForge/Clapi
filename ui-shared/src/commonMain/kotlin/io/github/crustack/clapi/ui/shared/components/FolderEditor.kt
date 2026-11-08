package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestEditorTab
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors

/**
 * Dedicated editor for configuring folder request defaults (variables, headers,
 * authentication, and scripts) which cascade down to descendant requests.
 */
@Composable
fun FolderEditor(
    tab: RequestTabState,
    state: AppState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun markDirty() {
        tab.markDirty()
        tab.recomputeDirty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Background)
            .testTag("folder-editor"),
    ) {
        // Header bar displaying folder icon, name, and Save button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(ClapiColors.Surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = ClapiColors.Tertiary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = tab.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ClapiColors.OnSurface,
                modifier = Modifier.weight(1f).testTag("folder-editor-title"),
            )
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClapiColors.Primary,
                    contentColor = ClapiColors.OnPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp).testTag("folder-save-button"),
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                )
                Text(Strings.save, fontSize = 12.sp)
            }
        }

        val availableTabs = listOf(
            RequestEditorTab.VARIABLES,
            RequestEditorTab.HEADERS,
            RequestEditorTab.AUTH,
            RequestEditorTab.PRE_REQUEST,
            RequestEditorTab.TESTS,
        )

        if (tab.selectedEditorTab !in availableTabs) {
            tab.selectedEditorTab = RequestEditorTab.VARIABLES
        }

        EditorTabBar(
            selectedTab = tab.selectedEditorTab,
            onTabSelected = { tab.selectedEditorTab = it },
            paramCount = 0,
            variableCount = tab.variables.size,
            headerCount = tab.headers.size,
            availableTabs = availableTabs,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(1.dp)) {
            when (tab.selectedEditorTab) {
                RequestEditorTab.VARIABLES -> KeyValueEditor(tab.variables, "variable", state = state, tab = tab) {
                    markDirty()
                }
                RequestEditorTab.HEADERS -> KeyValueEditor(tab.headers, "header", state = state, tab = tab) {
                    markDirty()
                }
                RequestEditorTab.AUTH -> AuthEditor(tab, state) {
                    markDirty()
                }
                RequestEditorTab.PRE_REQUEST -> ScriptEditor(
                    script = tab.preRequestScript,
                    onScriptChanged = { tab.preRequestScript = it; markDirty() },
                    title = "Pre-request Script",
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    availableVariables = getAvailableVariables(state, tab),
                )
                RequestEditorTab.TESTS -> ScriptEditor(
                    script = tab.testScript,
                    onScriptChanged = { tab.testScript = it; markDirty() },
                    title = "Post-request Script",
                    showCursorPosition = state.settings.showEditorPositionIndicator,
                    availableVariables = getAvailableVariables(state, tab),
                )
                else -> Unit
            }
        }
    }
}
