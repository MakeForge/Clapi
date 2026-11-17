package io.github.makeforge.clapi.ui.shared.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
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
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors

@Composable
fun OperationProgressDialog(
    title: String,
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = { }) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .draggableDialogCard(dragState, cornerRadius = 12.dp)
                    .widthIn(min = 360.dp, max = 520.dp)
                    .testTag("operation-progress-dialog"),
            ) {
                DialogTitleBar(
                    title = title,
                    onClose = onCancel,
                    onDrag = dragState::onDrag,
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.testTag("operation-progress-spinner"),
                            color = ClapiColors.Primary,
                            strokeWidth = 2.5.dp,
                        )
                        SelectionContainer {
                            Text(message, color = ClapiColors.OnSurfaceVariant, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = "Cancel",
                            color = ClapiColors.Error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Error.copy(alpha = 0.14f))
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("operation-progress-cancel"),
                        )
                    }
                }
            }
        }
    }
}
