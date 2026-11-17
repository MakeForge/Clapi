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
 * Shown when the user tries to close multiple tabs where at least one has unsaved changes.
 * Offers Save All / Discard All / Cancel.
 */
@Composable
fun DirtyMultiCloseDialog(
    dirtyCount: Int,
    onSaveAll: () -> Unit,
    onDiscardAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragState = rememberDraggableDialogState()

    Dialog(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier.draggableDialogBackdrop(dragState, onDismiss = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .draggableDialogCard(dragState)
                    .widthIn(min = 400.dp, max = 540.dp)
                    .testTag("dirty-multi-close-dialog"),
            ) {
                DialogTitleBar(
                    title = "Unsaved Changes",
                    onClose = onCancel,
                    closeButtonTag = "dirty-multi-close-dialog-close",
                    onDrag = dragState::onDrag,
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = "$dirtyCount ${Strings.t("requests_unsaved_changes_suffix")}",
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("dirty-multi-cancel"),
                        ) {
                            Text(Strings.cancel, fontSize = 13.sp, color = ClapiColors.OnSurfaceVariant)
                        }

                        // Discard All
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onDiscardAll)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("dirty-multi-discard"),
                        ) {
                            Text(Strings.t("discard_all"), fontSize = 13.sp, color = ClapiColors.Error, fontWeight = FontWeight.Medium)
                        }

                        // Save All
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClapiColors.Primary)
                                .clickable(onClick = onSaveAll)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("dirty-multi-save-all"),
                        ) {
                            Text(Strings.t("save_all"), fontSize = 13.sp, color = ClapiColors.OnPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
