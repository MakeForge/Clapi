package io.github.makeforge.clapi.ui.shared.components.rabbitmq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily

@Composable
fun RabbitMqRpcReplyPane(
    tab: RequestTabState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClapiColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tab.rabbitMqMode == RabbitMqOperationMode.PUBLISH) {
            Text("Publish Result", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val res = tab.rabbitMqLastPublishResult
            if (res == null) {
                Text("Click Publish to send a message to RabbitMQ.", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
            } else {
                val statusColor = if (res.isSuccess) ClapiColors.StatusSuccess else ClapiColors.Error
                val statusLabel = when {
                    res.isSuccess && res.confirmed -> "CONFIRMED"
                    res.isSuccess -> "PUBLISHED"
                    res.returnedUnroutable -> "RETURNED (UNROUTABLE ${res.returnReplyCode})"
                    else -> "ERROR"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${res.elapsedMs} ms", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                    Text("${res.byteLength} B", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                }
                Text("Exchange: ${res.exchange.ifBlank { "(default)" }}  |  Routing Key: ${res.routingKey}", color = ClapiColors.OnSurface, fontSize = 12.sp, fontFamily = CodeFontFamily)
                res.returnReplyText?.let {
                    Text("Return reason: $it", color = ClapiColors.Error, fontSize = 12.sp)
                }
                res.errorMessage?.let {
                    Text(it, color = ClapiColors.Error, fontSize = 12.sp)
                }
            }
        } else {
            Text("RPC Reply", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val res = tab.rabbitMqLastRpcResult
            if (res == null) {
                Text("Click Send RPC to publish a correlated request and await a reply.", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
            } else {
                val statusColor = if (res.isSuccess) ClapiColors.StatusSuccess else ClapiColors.Error
                val statusLabel = when {
                    res.isSuccess -> "OK"
                    res.timedOut -> "TIMEOUT"
                    else -> "ERROR"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${res.elapsedMs} ms", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                        res.reply?.let {
                            Text("${it.byteLength} B", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                        }
                    }
                    res.reply?.let { reply ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                .handPointerHover()
                                .clickable { copyToClipboard(reply.payloadText ?: reply.payloadBytesBase64.orEmpty()) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Copy Reply", color = ClapiColors.Primary, fontSize = 11.sp)
                        }
                    }
                }
                Text("correlation_id: ${res.correlationId}  |  reply_to: ${res.replyTo}", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
                res.errorMessage?.let {
                    Text(it, color = ClapiColors.Error, fontSize = 12.sp)
                }
                res.reply?.let { reply ->
                    HorizontalDivider(color = ClapiColors.Border)
                    val body = if (reply.isBinary) {
                        "Binary payload (${reply.byteLength} bytes, Base64):\n${reply.payloadBytesBase64.orEmpty()}"
                    } else {
                        reply.payloadText.orEmpty().ifBlank { "(empty reply body)" }
                    }
                    Text(
                        text = body,
                        color = ClapiColors.OnSurface,
                        fontSize = 12.sp,
                        fontFamily = CodeFontFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClapiColors.SurfaceVariant)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                    )
                    if (reply.headers.isNotEmpty()) {
                        Text("Reply Headers", color = ClapiColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        reply.headers.forEach { (k, v) ->
                            Text("$k: $v", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
                        }
                    }
                }
            }
        }

        if (tab.testResults.isNotEmpty()) {
            HorizontalDivider(color = ClapiColors.Border)
            Text("Test Assertions", color = ClapiColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            tab.testResults.forEach { a ->
                Text(
                    text = "${if (a.passed) "✓" else "✗"} ${a.name}${a.message.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()}",
                    color = if (a.passed) ClapiColors.StatusSuccess else ClapiColors.Error,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
