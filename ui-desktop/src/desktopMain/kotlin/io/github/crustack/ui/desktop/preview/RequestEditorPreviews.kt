package io.github.crustack.ui.desktop.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import io.github.crustack.ui.shared.components.RequestEditor
import io.github.crustack.ui.shared.state.AppState
import io.github.crustack.ui.shared.state.RequestTabState
import io.github.crustack.ui.shared.theme.ClapiTheme

@Preview
@Composable
fun RequestEditorPreview(){
    ClapiTheme(){
        RequestEditor(tab = RequestTabState(), state = AppState(), {}, {}){
        }
    }
}