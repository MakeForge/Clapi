package io.github.makeforge.clapi.ui.shared.components.graphql

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.components.GraphQLBadge
import io.github.makeforge.clapi.ui.shared.components.SaveButton
import io.github.makeforge.clapi.ui.shared.components.VariableAwareTextField
import io.github.makeforge.clapi.ui.shared.components.handPointerHover
import io.github.makeforge.clapi.ui.shared.generated.resources.Res
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_arrow_drop_down
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_send
import io.github.makeforge.clapi.ui.shared.generated.resources.ic_stop
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
fun GraphQLAddressBar(
    tab: RequestTabState,
    state: AppState,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    markDirty: () -> Unit,
) {
    var methodMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .border(width = 1.dp, color = ClapiColors.Border)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GraphQLBadge(compact = false)

        // Method selector dropdown (POST / GET)
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClapiColors.SurfaceContainer)
                    .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
                    .clickable { methodMenuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("graphql-method-selector"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tab.graphQlMethod,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = CodeFontFamily,
                    color = ClapiColors.MethodGraphQl,
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = ClapiColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }

            DropdownMenu(
                expanded = methodMenuExpanded,
                onDismissRequest = { methodMenuExpanded = false },
            ) {
                listOf("POST", "GET").forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = m,
                                fontWeight = if (tab.graphQlMethod == m) FontWeight.Bold else FontWeight.Normal,
                                color = if (tab.graphQlMethod == m) ClapiColors.MethodGraphQl else ClapiColors.OnSurface,
                                fontFamily = CodeFontFamily,
                            )
                        },
                        onClick = {
                            tab.graphQlMethod = m
                            methodMenuExpanded = false
                            markDirty()
                        },
                        modifier = Modifier.testTag("graphql-method-option-$m"),
                    )
                }
            }
        }

        // URL input with variable token resolution
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceContainer)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            VariableAwareTextField(
                value = tab.url,
                onValueChange = { newUrl ->
                    tab.url = newUrl
                    markDirty()
                },
                placeholder = "https://example.com/graphql",
                textStyle = TextStyle(
                    color = ClapiColors.OnSurface,
                    fontSize = 14.sp,
                    fontFamily = CodeFontFamily,
                ),
                state = state,
                tab = tab,
                undoStack = tab.urlUndoStack,
                modifier = Modifier.fillMaxWidth().testTag("graphql-url-input"),
            )
        }

        // Send / Stop Button
        val sendInteraction = remember { MutableInteractionSource() }
        val sendHovered by sendInteraction.collectIsHoveredAsState()

        if (tab.isLoading) {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sendHovered) ClapiColors.Error.copy(alpha = 0.85f) else ClapiColors.Error)
                    .hoverable(sendInteraction)
                    .handPointerHover()
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("graphql-stop-button"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_stop),
                        contentDescription = Strings.t("stop_request"),
                        tint = ClapiColors.OnPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = Strings.t("stop"),
                        color = ClapiColors.OnPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sendHovered) ClapiColors.MethodGraphQl.copy(alpha = 0.85f) else ClapiColors.MethodGraphQl)
                    .hoverable(sendInteraction)
                    .handPointerHover()
                    .clickable(onClick = onSend)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("graphql-send-button"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Strings.send,
                        color = ClapiColors.OnPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Icon(
                        painter = painterResource(Res.drawable.ic_send),
                        contentDescription = null,
                        tint = ClapiColors.OnPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Save Button
        SaveButton(isLoading = false, onClick = onSave)
    }
}
