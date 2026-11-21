package io.github.crustack.clapi.ui.shared.components.websocket

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.crustack.clapi.core.model.MessageDirection
import io.github.crustack.clapi.core.model.WebSocketLogMessage
import io.github.crustack.clapi.ui.shared.components.handPointerHover
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_clear
import io.github.crustack.clapi.ui.shared.generated.resources.ic_content_copy
import io.github.crustack.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.crustack.clapi.ui.shared.platform.copyToClipboard
import io.github.crustack.clapi.ui.shared.platform.formatTimestamp
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun WebSocketTimelinePane(
    messages: List<WebSocketLogMessage>,
    onClear: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMessages = remember(messages.toList(), searchQuery) {
        if (searchQuery.isBlank()) {
            messages.toList()
        } else {
            messages.filter { it.payload.contains(searchQuery, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to latest item on new incoming/outgoing message
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClapiColors.Surface)
            .testTag("websocket-timeline"),
    ) {
        // Timeline Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Timeline",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClapiColors.OnSurface,
                )
                Text(
                    text = "${messages.size} msgs",
                    fontSize = 10.sp,
                    color = ClapiColors.OnSurfaceDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClapiColors.Surface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Search filter
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = ClapiColors.OnSurface, fontSize = 11.sp, fontFamily = CodeFontFamily),
                    cursorBrush = SolidColor(ClapiColors.Primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClapiColors.Surface)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text("Filter…", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                            }
                            innerTextField()
                        }
                    },
                )

                // Clear button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onClear() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("websocket-clear-log"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = "Clear Log",
                        tint = ClapiColors.OnSurfaceDim,
                        modifier = Modifier.size(13.dp),
                    )
                    Text("Clear", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                }
            }
        }

        // Messages List or Empty State
        if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (messages.isEmpty()) {
                        "No messages yet. Connect and send messages to view the timeline."
                    } else {
                        "No messages matching filter."
                    },
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 12.sp,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        TimelineMessageItem(msg)
                    }
                }
                PlatformLazyVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
internal fun TimelineMessageItem(msg: WebSocketLogMessage) {
    val isSent = msg.direction == MessageDirection.SENT
    val directionColor = if (isSent) ClapiColors.StatusSuccess else ClapiColors.Primary
    val directionLabel = if (isSent) "↑ SENT" else "↓ RECEIVED"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.SurfaceContainer)
            .border(0.5.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Direction Badge
                Text(
                    text = directionLabel,
                    color = directionColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(directionColor.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )

                // Format Badge
                Text(
                    text = msg.format.uppercase(),
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(ClapiColors.Surface)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )

                // Byte size
                Text(
                    text = "${msg.byteLength} B",
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 9.sp,
                    fontFamily = CodeFontFamily,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Timestamp
                Text(
                    text = formatTimestamp(msg.timestamp),
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 10.sp,
                    fontFamily = CodeFontFamily,
                )

                // Copy button
                Icon(
                    painter = painterResource(Res.drawable.ic_content_copy),
                    contentDescription = "Copy message payload",
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier
                        .size(13.dp)
                        .handPointerHover()
                        .clickable { copyToClipboard(msg.payload) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Payload content preview
        Text(
            text = msg.payload,
            color = ClapiColors.OnSurface,
            fontSize = 11.sp,
            fontFamily = CodeFontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
    }
}
