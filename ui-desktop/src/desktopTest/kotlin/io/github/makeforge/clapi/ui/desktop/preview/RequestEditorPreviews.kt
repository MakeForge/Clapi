package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import io.github.makeforge.clapi.ui.shared.components.RequestEditor
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

@Preview
@Composable
fun RequestEditorPreview(){
    ClapiTheme(){
        RequestEditor(tab = RequestTabState(), state = AppState(), {}, {}){
        }
    }
}