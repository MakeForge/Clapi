package io.github.makeforge.clapi.ui.shared.components.variable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.components.VariableHighlightColor
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors
import io.github.makeforge.clapi.ui.shared.theme.CodeFontFamily

@Composable
fun VariableDropdownList(
    variables: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in variables.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .widthIn(min = 260.dp, max = 380.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .background(ClapiColors.SurfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
        ) {
            itemsIndexed(variables, key = { index, (name, _) -> "${name}_$index" }) { index, (name, value) ->
                val isSelected = index == selectedIndex
                val isSecret = name.startsWith("_secret.")
                val isAuth = name.startsWith("_auth.")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) ClapiColors.SelectedItem else Color.Transparent)
                        .clickable { onSelect(name) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("variable-dropdown-item-$name"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "{{ $name }}",
                        color = VariableHighlightColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = CodeFontFamily,
                    )
                    if (isSecret) {
                        Box(
                            modifier = Modifier
                                .background(ClapiColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, ClapiColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "Secret",
                                color = ClapiColors.Primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else if (isAuth) {
                        Box(
                            modifier = Modifier
                                .background(ClapiColors.Secondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, ClapiColors.Secondary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "AUTH",
                                color = ClapiColors.Secondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (value.isNotEmpty()) {
                        Text(
                            text = if (isSecret) "Path: $value" else if (isAuth) value else "${Strings.value}: $value",
                            color = ClapiColors.OnSurfaceDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                    }
                }
            }
        }
    }
}
