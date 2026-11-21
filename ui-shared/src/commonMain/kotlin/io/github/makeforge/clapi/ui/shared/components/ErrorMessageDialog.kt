// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_error_outline
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun ErrorMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .draggableDialogCard(dragState, cornerRadius = 12.dp)
                    .widthIn(min = 360.dp, max = 560.dp)
                    .testTag("error-dialog"),
            ) {
                DialogTitleBar(
                    title = title,
                    icon = painterResource(Res.drawable.ic_error_outline),
                    iconTint = ClapiColors.Error,
                    onClose = onDismiss,
                    onDrag = dragState::onDrag,
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SelectionContainer {
                        Text(message, color = ClapiColors.OnSurface, fontSize = 13.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = "OK",
                            color = ClapiColors.OnPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Primary)
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("error-dialog-ok"),
                        )
                    }
                }
            }
        }
    }
}
