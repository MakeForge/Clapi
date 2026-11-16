package io.github.crustack.clapi.ui.shared.components.websocket

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessage
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageType
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.crustack.clapi.core.network.ConnectionState
import io.github.crustack.clapi.ui.shared.components.CodeEditor
import io.github.crustack.clapi.ui.shared.components.SyntaxLanguage
import io.github.crustack.clapi.ui.shared.components.dialogEscape
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_arrow_drop_down
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_edit
import io.github.crustack.clapi.ui.shared.generated.resources.ic_send
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.shortcuts.AppHotkeys
import io.github.crustack.clapi.ui.shared.shortcuts.hotkeys
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import io.github.crustack.clapi.ui.shared.components.toSyntaxLanguage
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalSerializationApi::class)
@Composable
internal fun WebSocketMessagePane(
    tab: RequestTabState,
    markDirty: () -> Unit,
    onSend: () -> Unit,
) {
    var renamingVariantIndex by remember { mutableStateOf<Int?>(null) }

    if (renamingVariantIndex != null) {
        val idx = renamingVariantIndex!!
        val currentTitle = tab.webSocketMessageVariants.getOrNull(idx)?.title.orEmpty()
        var newTitle by remember(idx) { mutableStateOf(currentTitle) }

        RenameVariantDialog(
            title = "Rename Message Variant",
            value = newTitle,
            onValueChange = { newTitle = it },
            onDismiss = { renamingVariantIndex = null },
            onConfirm = {
                if (idx in tab.webSocketMessageVariants.indices && newTitle.isNotBlank()) {
                    val variant = tab.webSocketMessageVariants[idx]
                    tab.webSocketMessageVariants[idx] = variant.copy(title = newTitle)
                    markDirty()
                }
                renamingVariantIndex = null
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Variants Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab.webSocketMessageVariants.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.SurfaceContainer)
                            .clickable {
                                tab.webSocketMessageVariants.add(
                                    WebSocketMessageVariant(
                                        title = "Default",
                                        selected = true,
                                        message = WebSocketMessage(
                                            type = tab.webSocketMessageFormat,
                                            data = tab.webSocketOutgoingPayload,
                                        ),
                                    )
                                )
                                tab.activeWebSocketVariantIndex = 0
                                markDirty()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("websocket-add-variant"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_add),
                            contentDescription = "Add Variant",
                            tint = ClapiColors.Primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Add Variant",
                            fontSize = 11.sp,
                            color = ClapiColors.Primary,
                        )
                    }
                } else {
                    tab.webSocketMessageVariants.forEachIndexed { index, variant ->
                        val isActive = index == tab.activeWebSocketVariantIndex
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) ClapiColors.Primary.copy(alpha = 0.15f) else ClapiColors.SurfaceContainer)
                                .border(
                                    1.dp,
                                    if (isActive) ClapiColors.Primary.copy(alpha = 0.5f) else ClapiColors.Border,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    tab.activeWebSocketVariantIndex = index
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = variant.title,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isActive) ClapiColors.Primary else ClapiColors.OnSurface,
                            )

                            // Edit icon
                            Icon(
                                painter = painterResource(Res.drawable.ic_edit),
                                contentDescription = "Rename",
                                tint = ClapiColors.OnSurfaceDim,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { renamingVariantIndex = index },
                            )

                            // Delete icon (if more than 1 variant)
                            if (tab.webSocketMessageVariants.size > 1) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    tint = ClapiColors.OnSurfaceDim,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable {
                                            tab.webSocketMessageVariants.removeAt(index)
                                            if (tab.activeWebSocketVariantIndex >= tab.webSocketMessageVariants.size) {
                                                tab.activeWebSocketVariantIndex = tab.webSocketMessageVariants.size - 1
                                            }
                                            markDirty()
                                        },
                                )
                            }
                        }
                    }

                    // Plus button to add another variant
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = "Add Variant",
                        tint = ClapiColors.Primary,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val nextNumber = tab.webSocketMessageVariants.size + 1
                                tab.webSocketMessageVariants.add(
                                    WebSocketMessageVariant(
                                        title = "Variant $nextNumber",
                                        selected = false,
                                        message = WebSocketMessage(
                                            type = tab.effectiveWebSocketFormat,
                                            data = "",
                                        ),
                                    )
                                )
                                tab.activeWebSocketVariantIndex = tab.webSocketMessageVariants.size - 1
                                markDirty()
                            }
                            .testTag("websocket-add-variant"),
                    )
                }
            }

            // Message Format Selector and Send Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormatDropdown(
                    currentFormat = tab.effectiveWebSocketFormat,
                    onFormatSelected = { format ->
                        tab.effectiveWebSocketFormat = format
                        markDirty()
                    },
                )

                // Send Button
                val isConnected = tab.webSocketConnectionState == ConnectionState.CONNECTED
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isConnected) ClapiColors.Primary else ClapiColors.Primary.copy(alpha = 0.35f))
                        .clickable(enabled = isConnected) { onSend() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("websocket-send-button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_send),
                        contentDescription = "Send",
                        tint = ClapiColors.OnPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = Strings.t("websocket_send"),
                        color = ClapiColors.OnPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Code Editor for Active Payload
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp)),
        ) {
            CodeEditor(
                text = tab.webSocketOutgoingMessage,
                onTextChange = {
                    tab.webSocketOutgoingMessage = it
                    markDirty()
                },
                language = tab.effectiveWebSocketFormat.toSyntaxLanguage(),
                modifier = Modifier.fillMaxSize(),
                placeholder = "Compose outgoing message payload…",
                testTagPrefix = "websocket-message-editor",
            )
        }
    }
}

@Composable
internal fun FormatDropdown(
    currentFormat: WebSocketMessageType,
    onFormatSelected: (WebSocketMessageType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                .handPointerHover()
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("websocket-format-selector"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = currentFormat.name,
                color = ClapiColors.OnSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = ClapiColors.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            WebSocketMessageType.entries.forEach { format ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = format.name,
                            fontSize = 12.sp,
                            fontWeight = if (format == currentFormat) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onFormatSelected(format)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun RenameVariantDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .dialogEscape(onDismiss = onDismiss)
                .widthIn(min = 320.dp, max = 480.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ClapiColors.Surface)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(12.dp))
                .hotkeys {
                    on(AppHotkeys.Save) { onConfirm() }
                    on(AppHotkeys.Escape) { onDismiss() }
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = ClapiColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            BasicTextField(
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
                    .testTag("rename-variant-dialog-input"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Text(
                    text = Strings.cancel,
                    color = ClapiColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    text = Strings.save,
                    color = ClapiColors.OnPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ClapiColors.Primary)
                        .clickable { onConfirm() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
