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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeType
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqReplyStrategy
import io.github.makeforge.clapi.ui.shared.components.DenseDropdownMenuItem
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors

@Composable
fun RabbitMqAmqpTab(
    tab: RequestTabState,
    modifier: Modifier = Modifier,
) {
    var exTypeOpen by remember { mutableStateOf(false) }
    var replyStratOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Broker Connection", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Host", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                CompactField(
                    value = tab.rabbitMqConnection.host,
                    onValueChange = {
                        tab.rabbitMqConnection = tab.rabbitMqConnection.copy(host = it)
                        tab.markDirty()
                    },
                    placeholder = "localhost",
                )
            }
            Column(modifier = Modifier.weight(0.45f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Port", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                CompactField(
                    value = tab.rabbitMqConnection.port?.toString().orEmpty(),
                    onValueChange = { raw ->
                        tab.rabbitMqConnection = tab.rabbitMqConnection.copy(port = raw.filter { it.isDigit() }.toIntOrNull())
                        tab.markDirty()
                    },
                    placeholder = "5672",
                )
            }
            Column(modifier = Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Virtual Host", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                CompactField(
                    value = tab.rabbitMqConnection.vhost,
                    onValueChange = {
                        tab.rabbitMqConnection = tab.rabbitMqConnection.copy(vhost = it)
                        tab.markDirty()
                    },
                    placeholder = "/",
                )
            }
            LabeledCheckbox(
                label = "TLS (amqps)",
                checked = tab.rabbitMqConnection.useTls,
                onCheckedChange = {
                    tab.rabbitMqConnection = tab.rabbitMqConnection.copy(useTls = it)
                    tab.markDirty()
                },
            )
        }

        Text("Exchange & Routing", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Exchange Name (empty = default)", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                CompactField(
                    value = tab.rabbitMqExchange.name,
                    onValueChange = {
                        tab.rabbitMqExchange = tab.rabbitMqExchange.copy(name = it)
                        tab.markDirty()
                    },
                    placeholder = "orders.events",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Exchange Type", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClapiColors.Surface)
                            .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                            .handPointerHover()
                            .clickable { exTypeOpen = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(tab.rabbitMqExchange.type.value, color = ClapiColors.OnSurface, fontSize = 12.sp)
                        Text("▾", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                    }
                    DropdownMenu(expanded = exTypeOpen, onDismissRequest = { exTypeOpen = false }) {
                        RabbitMqExchangeType.entries.forEach { t ->
                            DenseDropdownMenuItem(
                                text = { Text(t.value) },
                                onClick = {
                                    tab.rabbitMqExchange = tab.rabbitMqExchange.copy(type = t)
                                    tab.markDirty()
                                    exTypeOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE) "Routing Key / Binding Pattern" else "Routing Key",
                color = ClapiColors.OnSurfaceDim,
                fontSize = 11.sp,
            )
            CompactField(
                value = tab.rabbitMqRoutingKey,
                onValueChange = {
                    tab.rabbitMqRoutingKey = it
                    tab.markDirty()
                },
                placeholder = if (tab.rabbitMqMode == RabbitMqOperationMode.SUBSCRIBE) "order.#" else "order.created",
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LabeledCheckbox(
                label = "Exchange Durable",
                checked = tab.rabbitMqExchange.durable,
                onCheckedChange = {
                    tab.rabbitMqExchange = tab.rabbitMqExchange.copy(durable = it)
                    tab.markDirty()
                },
            )
            LabeledCheckbox(
                label = "Exchange Auto-Delete",
                checked = tab.rabbitMqExchange.autoDelete,
                onCheckedChange = {
                    tab.rabbitMqExchange = tab.rabbitMqExchange.copy(autoDelete = it)
                    tab.markDirty()
                },
            )
        }

        Text("Queue", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Queue Name (leave blank in Subscribe mode for exclusive auto-delete queue)",
                color = ClapiColors.OnSurfaceDim,
                fontSize = 11.sp,
            )
            CompactField(
                value = tab.rabbitMqQueue.name,
                onValueChange = {
                    tab.rabbitMqQueue = tab.rabbitMqQueue.copy(name = it)
                    tab.markDirty()
                },
                placeholder = "orders.queue",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LabeledCheckbox(
                label = "Queue Durable",
                checked = tab.rabbitMqQueue.durable,
                onCheckedChange = {
                    tab.rabbitMqQueue = tab.rabbitMqQueue.copy(durable = it)
                    tab.markDirty()
                },
            )
            LabeledCheckbox(
                label = "Exclusive",
                checked = tab.rabbitMqQueue.exclusive,
                onCheckedChange = {
                    tab.rabbitMqQueue = tab.rabbitMqQueue.copy(exclusive = it)
                    tab.markDirty()
                },
            )
            LabeledCheckbox(
                label = "Auto-Delete",
                checked = tab.rabbitMqQueue.autoDelete,
                onCheckedChange = {
                    tab.rabbitMqQueue = tab.rabbitMqQueue.copy(autoDelete = it)
                    tab.markDirty()
                },
            )
        }

        if (tab.rabbitMqMode == RabbitMqOperationMode.RPC) {
            Text("RPC Reply Configuration", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reply Strategy", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClapiColors.Surface)
                                .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
                                .handPointerHover()
                                .clickable { replyStratOpen = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(tab.rabbitMqSettings.replyStrategy.value, color = ClapiColors.OnSurface, fontSize = 12.sp)
                            Text("▾", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                        }
                        DropdownMenu(expanded = replyStratOpen, onDismissRequest = { replyStratOpen = false }) {
                            RabbitMqReplyStrategy.entries.forEach { strat ->
                                DenseDropdownMenuItem(
                                    text = { Text(strat.value) },
                                    onClick = {
                                        tab.rabbitMqSettings = tab.rabbitMqSettings.copy(replyStrategy = strat)
                                        tab.markDirty()
                                        replyStratOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (tab.rabbitMqSettings.replyStrategy == RabbitMqReplyStrategy.NAMED_QUEUE) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Named Reply Queue", color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
                        CompactField(
                            value = tab.rabbitMqReplyQueue,
                            onValueChange = {
                                tab.rabbitMqReplyQueue = it
                                tab.markDirty()
                            },
                            placeholder = "orders.rpc.replies",
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .handPointerHover()
            .clickable { onCheckedChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = ClapiColors.OnSurface, fontSize = 12.sp)
    }
}
