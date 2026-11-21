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
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import kotlin.math.roundToInt

@Composable
fun DirtyCloseDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Unsaved Changes",
    message: String = Strings.t("request_unsaved_changes_message"),
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
                    .widthIn(min = 380.dp, max = 520.dp)
                    .hotkeys {
                        on(AppHotkeys.Escape) { onCancel() }
                        on(AppHotkeys.Save) { onSave() }
                    }
                    .testTag("dirty-close-dialog"),
            ) {
                DialogTitleBar(
                    title = title,
                    onClose = onCancel,
                    closeButtonTag = "dirty-close-dialog-close",
                    onDrag = dragState::onDrag,
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color = ClapiColors.OnSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("dirty-close-cancel"),
                        ) {
                            Text(Strings.cancel, fontSize = 13.sp, color = ClapiColors.OnSurface)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Tertiary.copy(alpha = 0.14f))
                                .border(1.dp, ClapiColors.Tertiary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .clickable(onClick = onDiscard)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("dirty-close-discard"),
                        ) {
                            Text(Strings.t("discard"), fontSize = 13.sp, color = ClapiColors.Tertiary)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Primary)
                                .clickable(onClick = onSave)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("dirty-close-save"),
                        ) {
                            Text(Strings.save, fontSize = 13.sp, color = ClapiColors.OnPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
