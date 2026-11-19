package io.github.makeforge.clapi.ui.shared.components.rabbitmq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors

@Composable
fun RabbitMqHeadersPropsTab(
    tab: RequestTabState,
    state: io.github.makeforge.clapi.ui.shared.state.AppState,
    modifier: Modifier = Modifier,
) {
    val props = tab.rabbitMqProperties
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("AMQP Basic Properties", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LabeledField(
                label = "Content-Type",
                value = props.contentType.orEmpty(),
                placeholder = "application/json",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(contentType = it.ifBlank { null })
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "Delivery Mode (1=transient, 2=persistent)",
                value = props.deliveryMode?.toString().orEmpty(),
                placeholder = "2",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(deliveryMode = it.toIntOrNull())
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LabeledField(
                label = "Correlation ID",
                value = props.correlationId.orEmpty(),
                placeholder = "(auto-UUID for RPC)",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(correlationId = it.ifBlank { null })
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "Reply-To Override",
                value = props.replyTo.orEmpty(),
                placeholder = "amq.rabbitmq.reply-to",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(replyTo = it.ifBlank { null })
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LabeledField(
                label = "Message ID",
                value = props.messageId.orEmpty(),
                placeholder = "",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(messageId = it.ifBlank { null })
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "Expiration (ms)",
                value = props.expiration.orEmpty(),
                placeholder = "60000",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(expiration = it.ifBlank { null })
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LabeledField(
                label = "Type",
                value = props.type.orEmpty(),
                placeholder = "",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(type = it.ifBlank { null })
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "App ID",
                value = props.appId.orEmpty(),
                placeholder = "clapi",
                onValueChange = {
                    tab.rabbitMqProperties = props.copy(appId = it.ifBlank { null })
                    tab.markDirty()
                },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = ClapiColors.Border)
        Text("Custom AMQP Headers", color = ClapiColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        io.github.makeforge.clapi.ui.shared.components.KeyValueEditor(
            entries = tab.headers,
            tag = "rabbitmq-headers",
            state = state,
            onDirty = { tab.markDirty() },
        )
    }
}

@Composable
internal fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = ClapiColors.OnSurfaceDim, fontSize = 11.sp)
        CompactField(value = value, onValueChange = onValueChange, placeholder = placeholder)
    }
}
