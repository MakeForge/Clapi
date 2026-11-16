package io.github.crustack.clapi.ui.shared.components.sidebar

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import io.github.crustack.clapi.ui.shared.generated.resources.Res
import io.github.crustack.clapi.ui.shared.generated.resources.ic_history
import io.github.crustack.clapi.ui.shared.i18n.Strings
import io.github.crustack.clapi.ui.shared.state.AppState
import org.jetbrains.compose.resources.painterResource

internal fun LazyListScope.sidebarHistorySection(state: AppState) {
    item {
        SectionHeader(
            icon = painterResource(Res.drawable.ic_history),
            title = Strings.history,
            expanded = state.historyExpanded,
            onToggle = { state.historyExpanded = !state.historyExpanded },
        )
    }
    if (state.historyExpanded) {
        val historyQuery = normalizeSidebarSearchQuery(state.sidebarSearchQuery)
        val visibleHistory = if (historyQuery.isBlank()) {
            state.historyItems
        } else {
            state.historyItems.filter { item ->
                item.name.contains(historyQuery, ignoreCase = true) ||
                    item.url.contains(historyQuery, ignoreCase = true)
            }
        }
        items(visibleHistory, key = { "hist-${it.requestId}-${it.timestamp}" }) { item ->
            HistoryRow(state = state, item = item, onClick = {
                state.openHistoryItem(item)
            })
        }
        item { SectionSpacer() }
    }
}
