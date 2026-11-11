package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.httpMethodColor

@Composable
fun MethodBadge(
    method: HttpMethodType,
    compact: Boolean = false,
    sse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = httpMethodColor(method)
    Text(
        text = if (sse) "SSE" else if (compact) method.name.take(3) else method.name,
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
fun McpMethodBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "MCP",
        color = ClapiColors.Primary,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.Primary.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun GrpcBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "gRPC",
        color = ClapiColors.MethodPatch,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.MethodPatch.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun WebSocketBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "WS",
        color = ClapiColors.MethodWebSocket,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.MethodWebSocket.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun GraphQLBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = "GQL",
        color = ClapiColors.MethodGraphQl,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ClapiColors.MethodGraphQl.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}
