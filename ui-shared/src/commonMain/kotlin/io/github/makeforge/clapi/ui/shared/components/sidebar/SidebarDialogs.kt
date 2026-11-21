package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.ui.shared.components.DialogBasicTextField
import io.github.makeforge.clapi.ui.shared.components.DialogTitleBar
import io.github.makeforge.clapi.ui.shared.components.dialogEscape
import io.github.makeforge.clapi.ui.shared.components.draggableDialogBackdrop
import io.github.makeforge.clapi.ui.shared.components.draggableDialogCard
import io.github.makeforge.clapi.ui.shared.components.rememberDraggableDialogState
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.makeforge.clapi.ui.shared.shortcuts.hotkeys
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily

@Composable
fun RenameItemDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String = Strings.save,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
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
                    .hotkeys {
                        on(AppHotkeys.Save) { onConfirm() }
                        on(AppHotkeys.Escape) { onDismiss() }
                    },
            ) {
                DialogTitleBar(
                    title = title,
                    onClose = onDismiss,
                    onDrag = dragState::onDrag,
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DialogBasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                        cursorBrush = SolidColor(ClapiColors.Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("rename-dialog-input"),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        Text(
                            text = Strings.cancel,
                            color = ClapiColors.OnSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.SurfaceContainer)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("rename-dialog-cancel"),
                        )
                        Text(
                            text = confirmText,
                            color = ClapiColors.OnPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ClapiColors.Primary)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(onClick = onConfirm)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("rename-dialog-save"),
                        )
                    }
                }
            }
        }
    }
}
