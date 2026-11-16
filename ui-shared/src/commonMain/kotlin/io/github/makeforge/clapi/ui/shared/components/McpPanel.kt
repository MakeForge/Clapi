package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.makeforge.clapi.ui.shared.components.mcp.McpPanel as DelegateMcpPanel
import io.github.makeforge.clapi.ui.shared.components.mcp.SchemaArgsEditor as DelegateSchemaArgsEditor
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.serialization.json.JsonObject

@Composable
fun McpPanel(state: AppState, tab: RequestTabState) {
    DelegateMcpPanel(state = state, tab = tab)
}

@Composable
internal fun SchemaArgsEditor(
    modifier: Modifier = Modifier,
    state: AppState,
    schema: JsonObject,
    args: String,
    onArgsChange: (String) -> Unit,
    testTagPrefix: String = "mcp-schema-args",
) {
    DelegateSchemaArgsEditor(
        modifier = modifier,
        state = state,
        schema = schema,
        args = args,
        onArgsChange = onArgsChange,
        testTagPrefix = testTagPrefix,
    )
}
