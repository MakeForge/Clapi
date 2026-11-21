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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.core.model.AmqpAckStatus
import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.RabbitMqDeliveryMessage
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.copyToClipboard
import io.github.makeforge.clapi.ui.shared.platform.formatTimestamp
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily

@Composable
fun RabbitMqTimelinePane(
    tab: RequestTabState,
    onAck: (Long) -> Unit,
    onNack: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = tab.rabbitMqTimelineFilter.trim()
    val filtered = remember(tab.rabbitMqMessages.toList(), filter) {
        if (filter.isEmpty()) tab.rabbitMqMessages.toList()
        else tab.rabbitMqMessages.filter { m ->
            m.routingKey.contains(filter, ignoreCase = true) ||
                m.exchange.contains(filter, ignoreCase = true) ||
                m.payloadText.orEmpty().contains(filter, ignoreCase = true) ||
                m.headers.any { (k, v) -> k.contains(filter, ignoreCase = true) || v.contains(filter, ignoreCase = true) }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(ClapiColors.Surface)) {
        // Header bar with status, filter, clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClapiColors.SurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusColor = when (tab.rabbitMqConnectionStatus) {
                RabbitMqConnectionStatus.CONNECTED -> ClapiColors.StatusSuccess
                RabbitMqConnectionStatus.ERROR -> ClapiColors.Error
                RabbitMqConnectionStatus.CONNECTING, RabbitMqConnectionStatus.DISCONNECTING -> ClapiColors.MethodRabbitMq
                RabbitMqConnectionStatus.DISCONNECTED -> ClapiColors.OnSurfaceDim
            }
            Text(
                text = "${tab.rabbitMqConnectionStatus.name} (${filtered.size})",
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            CompactField(
                value = tab.rabbitMqTimelineFilter,
                onValueChange = { tab.rabbitMqTimelineFilter = it },
                placeholder = "Filter by text, routing key, or header...",
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                    .handPointerHover()
                    .clickable { tab.rabbitMqMessages.clear() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Clear", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
            }
        }

        tab.rabbitMqError?.let { err ->
            Text(
                text = err,
                color = ClapiColors.Error,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClapiColors.Error.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        HorizontalDivider(color = ClapiColors.Border)

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (tab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.CONNECTED) {
                        "Listening for RabbitMQ messages..."
                    } else {
                        "Click Subscribe to start consuming messages."
                    },
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { msg ->
                    RabbitMqTimelineRow(msg = msg, onAck = onAck, onNack = onNack)
                }
            }
        }
    }
}

@Composable
private fun RabbitMqTimelineRow(
    msg: RabbitMqDeliveryMessage,
    onAck: (Long) -> Unit,
    onNack: (Long, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.SurfaceVariant)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .handPointerHover()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(msg.timestampEpochMillis),
                    color = ClapiColors.OnSurfaceDim,
                    fontSize = 11.sp,
                    fontFamily = CodeFontFamily,
                )
                if (msg.exchange.isNotBlank()) {
                    Text("ex:${msg.exchange}", color = ClapiColors.MethodRabbitMq, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Text("rk:${msg.routingKey}", color = ClapiColors.OnSurface, fontSize = 11.sp, fontFamily = CodeFontFamily)
                Text("${msg.byteLength} B", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                if (msg.isBinary) {
                    Text("BINARY", color = ClapiColors.Tertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (msg.assertionResults.isNotEmpty()) {
                    val passedCount = msg.assertionResults.count { it.passed }
                    val allPassed = passedCount == msg.assertionResults.size
                    val pillColor = if (allPassed) ClapiColors.StatusSuccess else ClapiColors.Error
                    Text(
                        text = "${if (allPassed) "✓" else "✗"} $passedCount/${msg.assertionResults.size}",
                        color = pillColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (msg.ackStatus == AmqpAckStatus.PENDING && msg.deliveryTag != null) {
                    SmallActionChip(Strings.t("rabbitmq_ack"), ClapiColors.StatusSuccess) { onAck(msg.deliveryTag!!) }
                    SmallActionChip(Strings.t("rabbitmq_nack"), ClapiColors.Error) { onNack(msg.deliveryTag!!, false) }
                    SmallActionChip(Strings.t("rabbitmq_requeue"), ClapiColors.MethodRabbitMq) { onNack(msg.deliveryTag!!, true) }
                } else {
                    Text(msg.ackStatus.name, color = ClapiColors.OnSurfaceDim, fontSize = 10.sp)
                }
                SmallActionChip("Copy", ClapiColors.Primary) {
                    copyToClipboard(msg.payloadText ?: msg.payloadBytesBase64.orEmpty())
                }
            }
        }

        val preview = if (msg.isBinary) {
            "Base64 (${msg.byteLength} bytes): ${msg.payloadBytesBase64.orEmpty().take(if (expanded) 4096 else 120)}"
        } else {
            val txt = msg.payloadText.orEmpty()
            if (expanded) txt else txt.take(180)
        }
        Text(
            text = preview.ifBlank { "(empty payload)" },
            color = ClapiColors.OnSurface,
            fontSize = 12.sp,
            fontFamily = CodeFontFamily,
        )

        if (expanded && (msg.headers.isNotEmpty() || !msg.properties.correlationId.isNullOrBlank() || !msg.properties.replyTo.isNullOrBlank() || msg.assertionResults.isNotEmpty())) {
            HorizontalDivider(color = ClapiColors.Border)
            msg.properties.correlationId?.let {
                Text("correlation_id: $it", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
            }
            msg.properties.replyTo?.let {
                Text("reply_to: $it", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
            }
            msg.headers.forEach { (k, v) ->
                Text("$k: $v", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
            }
            if (msg.assertionResults.isNotEmpty()) {
                msg.assertionResults.forEach { a ->
                    Text(
                        text = "${if (a.passed) "✓" else "✗"} ${a.name}${a.message.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()}",
                        color = if (a.passed) ClapiColors.StatusSuccess else ClapiColors.Error,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallActionChip(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .handPointerHover()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
