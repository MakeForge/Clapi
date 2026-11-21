package io.github.crustack.clapi.ui.shared.components.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.core.model.*
import io.github.crustack.clapi.core.network.mcp.*
import io.github.crustack.clapi.ui.shared.components.*
import io.github.crustack.clapi.ui.shared.components.executor.buildAuthConfig
import io.github.crustack.clapi.ui.shared.generated.resources.*
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.mcp.*
import io.github.crustack.clapi.ui.shared.platform.*
import io.github.crustack.clapi.ui.shared.state.*
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.painterResource

internal data class McpSegment(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
internal fun McpSegmentedControl(segments: List<McpSegment>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp)),
    ) {
        segments.forEach { segment ->
            Text(
                segment.label,
                fontSize = 12.sp,
                fontWeight = if (segment.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (segment.selected) ClapiColors.Primary else ClapiColors.OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (segment.selected) ClapiColors.SelectedItem else Color.Transparent)
                    .clickable(onClick = segment.onClick)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun McpSectionTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClapiColors.Surface)
            .testTag("mcp-tabs"),
    ) {
        Row(Modifier.fillMaxWidth().height(36.dp)) {
            labels.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) ClapiColors.Primary else ClapiColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(ClapiColors.Primary),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(ClapiColors.Border),
        )
    }
}

@Composable
internal fun ScrollableLazyColumn(
    modifier: Modifier,
    listTestTag: String,
    scrollbarTag: String,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 10.dp).testTag(listTestTag),
            content = content,
        )
        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
            testTag = scrollbarTag,
        )
    }
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    state: AppState? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ClapiColors.Background)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        VariableAwareTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            state = state,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
    }
}

@Composable
internal fun ListRow(
    title: String,
    subtitle: String?,
    active: Boolean,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) ClapiColors.Primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = ClapiColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.let {
                Text(it, color = ClapiColors.StatusSuccess, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = ClapiColors.OnSurfaceDim,
                fontSize = 11.sp,
                fontFamily = CodeFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RunButton(
    busy: Boolean,
    enabled: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    onStop: () -> Unit,
) {
    if (busy) {
        OutlinedButton(onClick = onStop, modifier = Modifier.testTag("$testTag-stop")) {
            Text(Strings.t("stop"))
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(testTag)) {
            Text(label)
        }
    }
}

@Composable
internal fun AnnotationChip(label: String, destructive: Boolean) {
    Text(
        label,
        color = if (destructive) ClapiColors.Error else ClapiColors.OnSurfaceDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (destructive) ClapiColors.Error else ClapiColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
internal fun EmptyState(text: String) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp)).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
    }
}

@Composable
internal fun SplitCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ClapiColors.Surface)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp)),
    ) {
        content()
    }
}

@Composable
internal fun SplitColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}


internal fun countSuffix(n: Int): String = if (n > 0) " ($n)" else ""
