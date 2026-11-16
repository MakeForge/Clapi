package io.github.crustack.clapi.ui.shared.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_add
import io.github.crustack.clapi.ui.shared.generated.resources.ic_delete
import io.github.crustack.clapi.ui.shared.generated.resources.ic_lock
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.platform.PlatformLazyVerticalScrollbar
import io.github.crustack.clapi.ui.shared.platform.insetScrollbar
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.HeaderKind
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.state.SystemHeaderRules
import io.github.crustack.clapi.ui.shared.theme.ClapiColors
import io.github.crustack.clapi.ui.shared.theme.CodeFontFamily
import org.jetbrains.compose.resources.painterResource

/**
 * Generic key-value table editor used for both Params and Headers.
 *
 * @param entries the mutable list of entries (backed by Compose snapshot state)
 * @param tag     semantic label used for test-tags and "Add …" button text
 * @param onDirty callback invoked whenever any entry changes
 */
@Composable
fun KeyValueEditor(
    entries: MutableList<MutableKeyValue>,
    tag: String,
    state: AppState? = null,
    tab: RequestTabState? = null,
    onDirty: () -> Unit,
) {
    val isHeaderEditor = tag == "header"
    val showTypeColumn = isHeaderEditor || entries.any { it.kind == HeaderKind.INHERITED }
    Column(modifier = Modifier.fillMaxSize().background(ClapiColors.Background).padding(8.dp)) {
        // Column header labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))
            Text(Strings.t("key_upper"),   style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceDim, modifier = Modifier.weight(1f))
            Text(Strings.t("value_upper"), style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceDim, modifier = Modifier.weight(1f))
            if (showTypeColumn) {
                Text(Strings.t("type_upper"), style = MaterialTheme.typography.labelSmall, color = ClapiColors.OnSurfaceDim, modifier = Modifier.size(70.dp, 20.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))
        }

        val listState = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 10.dp),
            ) {
                itemsIndexed(entries, key = { _, kv -> kv.uid }) { idx, kv ->
                    KeyValueRow(
                        kv = kv,
                        onDelete = {
                            if (!((isHeaderEditor && kv.kind == HeaderKind.SYSTEM) || kv.kind == HeaderKind.INHERITED)) {
                                entries.removeAt(idx)
                                onDirty()
                            }
                        },
                        onDirty = onDirty,
                        isHeaderEditor = isHeaderEditor,
                        showTypeColumn = showTypeColumn,
                        state = state,
                        tab = tab,
                        testTag = "$tag-row-$idx",
                        tag = tag,
                    )
                }
            }
            PlatformLazyVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).insetScrollbar(),
                testTag = "$tag-list-vscrollbar",
            )
        }

        // "Add …" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    entries.add(
                        if (isHeaderEditor) MutableKeyValue(kind = HeaderKind.USER)
                        else MutableKeyValue()
                    )
                    onDirty()
                }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = Strings.add, tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
            Text("${Strings.add} ${tag.replaceFirstChar { it.uppercaseChar() }}", color = ClapiColors.OnSurfaceDim, fontSize = 12.sp)
        }
    }
}

// ── Key-value row ───────────────────────────────────────────────

@Composable
private fun KeyValueRow(
    kv: MutableKeyValue,
    onDelete: () -> Unit,
    onDirty: () -> Unit,
    isHeaderEditor: Boolean,
    showTypeColumn: Boolean = false,
    state: AppState? = null,
    tab: RequestTabState? = null,
    testTag: String,
    tag: String = "",
) {
    val isSystemHeader = isHeaderEditor &&
        (kv.kind == HeaderKind.SYSTEM || SystemHeaderRules.isSystemHeader(kv.key))
    val isInherited = kv.kind == HeaderKind.INHERITED

    val initialKey = remember { kv.key }
    val excludeVars = remember(kv.key, initialKey, tag) {
        if (tag == "variable") {
            val set = mutableSetOf<String>()
            if (kv.key.isNotBlank()) set.add(kv.key.trim())
            if (initialKey.isNotBlank()) set.add(initialKey.trim())
            set
        } else {
            emptySet()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = kv.enabled,
            onCheckedChange = { if (!isInherited) { kv.enabled = it; onDirty() } },
            enabled = !isInherited,
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = ClapiColors.Primary,
                uncheckedColor = ClapiColors.OnSurfaceDim,
                checkmarkColor = ClapiColors.OnPrimary,
            ),
        )

        InlineTextField(
            value = kv.key,
            onValueChange = { if (!isSystemHeader && !isInherited) { kv.key = it; onDirty() } },
            placeholder = Strings.t("key_upper"),
            readOnly = isSystemHeader || isInherited,
            modifier = Modifier.weight(1f).testTag("$testTag-key"),
            state = state,
            tab = tab,
            excludeVariables = excludeVars,
        )
        InlineTextField(
            value = kv.value,
            onValueChange = { if (!isInherited) { kv.value = it; onDirty() } },
            placeholder = Strings.t("value_upper"),
            readOnly = isInherited,
            modifier = Modifier.weight(1f).testTag("$testTag-value"),
            state = state,
            tab = tab,
            excludeVariables = excludeVars,
        )

        if (showTypeColumn) {
            Row(
                modifier = Modifier.size(70.dp, 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when {
                    isSystemHeader -> {
                        Icon(painter = painterResource(Res.drawable.ic_lock), contentDescription = Strings.t("system_header"), tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(12.dp))
                        Text(Strings.t("system"), fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                    }
                    isInherited -> {
                        Text(
                            text = Strings.inherited,
                            fontSize = 11.sp,
                            color = ClapiColors.Tertiary,
                            modifier = Modifier.testTag("$testTag-type-inherited"),
                        )
                    }
                    else -> {
                        Text(Strings.t("user"), fontSize = 11.sp, color = ClapiColors.OnSurfaceDim)
                    }
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).testTag("$testTag-remove"), enabled = !isSystemHeader && !isInherited) {
            Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = Strings.t("remove"), tint = ClapiColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Shared inline text field ────────────────────────────────────

@Composable
fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    state: AppState? = null,
    tab: RequestTabState? = null,
    readOnly: Boolean = false,
    excludeVariables: Set<String> = emptySet(),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (readOnly) ClapiColors.SurfaceContainer.copy(alpha = 0.6f) else ClapiColors.SurfaceContainer)
            .border(1.dp, ClapiColors.Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (state != null) {
            VariableAwareTextField(
                value = value,
                onValueChange = if (readOnly) ({}) else onValueChange,
                placeholder = placeholder,
                textStyle = TextStyle(
                    color = if (readOnly) ClapiColors.OnSurfaceDim else ClapiColors.OnSurface,
                    fontSize = 13.sp,
                    fontFamily = CodeFontFamily,
                ),
                state = state,
                tab = tab,
                readOnly = readOnly,
                excludeVariables = excludeVariables,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = if (readOnly) ({}) else onValueChange,
                readOnly = readOnly,
                singleLine = true,
                textStyle = TextStyle(
                    color = if (readOnly) ClapiColors.OnSurfaceDim else ClapiColors.OnSurface,
                    fontSize = 13.sp,
                    fontFamily = CodeFontFamily,
                ),
                cursorBrush = SolidColor(ClapiColors.Primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = ClapiColors.OnSurfaceDim, fontSize = 13.sp)
                    inner()
                },
            )
        }
    }
}
