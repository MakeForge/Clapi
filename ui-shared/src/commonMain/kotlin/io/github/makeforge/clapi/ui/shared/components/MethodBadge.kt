package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.httpMethodColor

fun compactMethodName(method: HttpMethodType): String = when (method) {
    HttpMethodType.DELETE -> "DEL"
    HttpMethodType.OPTIONS -> "OPT"
    else -> method.name
}

@Composable
fun MethodBadge(
    method: HttpMethodType,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    sse: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = httpMethodColor(method)
    val clickModifier = if (onClick != null) {
        Modifier.handPointerHover().clickable(onClick = onClick)
    } else Modifier
    Text(
        text = if (sse) "SSE" else if (compact) compactMethodName(method) else method.name,
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .then(clickModifier)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun HttpBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = ClapiColors.BadgeHttp,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.handPointerHover().clickable(onClick = onClick)
    } else Modifier
    Text(
        text = "HTTP",
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .then(clickModifier)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun McpMethodBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = ClapiColors.BadgeMcp,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.handPointerHover().clickable(onClick = onClick)
    } else Modifier
    Text(
        text = "MCP",
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .then(clickModifier)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun GrpcBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = ClapiColors.BadgeGrpc,
) {
    Text(
        text = "gRPC",
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun WebSocketBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = ClapiColors.MethodWebSocket,
) {
    Text(
        text = "WS",
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun GraphQLBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = ClapiColors.MethodGraphQl,
) {
    Text(
        text = "GQL",
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun RabbitMqBadge(
    modeBadge: String = "PUB",
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = ClapiColors.MethodRabbitMq,
) {
    Text(
        text = modeBadge.ifBlank { "RMQ" },
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}
