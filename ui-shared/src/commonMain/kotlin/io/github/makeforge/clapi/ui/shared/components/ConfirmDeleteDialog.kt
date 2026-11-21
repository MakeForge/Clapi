// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlin.math.roundToInt

/**
 * Simple yes/no confirmation dialog.
 *
 * @param title      Short heading, e.g. "Delete request?"
 * @param message    Body text with details.
 * @param confirmLabel Label for the destructive/confirm button (default: "Delete").
 * @param onConfirm  Called when user confirms.
 * @param onDismiss  Called when user cancels or dismisses.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Delete",
) {
    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .draggableDialogCard(dragState)
                    .widthIn(min = 360.dp, max = 480.dp)
                    .testTag("confirm-dialog"),
            ) {
                DialogTitleBar(
                    title = title,
                    onClose = onDismiss,
                    closeButtonTag = "confirm-dialog-close",
                    onDrag = dragState::onDrag,
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SelectionContainer {
                        Text(
                            message,
                            fontSize = 13.sp,
                            color = ClapiColors.OnSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        // Cancel
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("confirm-cancel-button"),
                        ) {
                            Text(Strings.cancel, fontSize = 13.sp, color = ClapiColors.OnSurface)
                        }

                        // Confirm (destructive)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Error.copy(alpha = 0.15f))
                                .border(1.dp, ClapiColors.Error.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable(onClick = onConfirm)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("confirm-ok-button"),
                        ) {
                            Text(
                                confirmLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = ClapiColors.Error,
                            )
                        }
                    }
                }
            }
        }
    }
}
