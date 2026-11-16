package io.github.makeforge.clapi.ui.shared.state

import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.MessageDirection
import io.github.makeforge.clapi.core.model.WebSocketLogMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase3StateIsolationAndBoundsTest {

    @Test
    fun per_tab_testResults_remain_isolated_across_tab_switches() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        val tabA = RequestTabState(id = "tab-a", name = "Tab A").apply {
            testResults.add(TestResultEntry(name = "A-1", passed = true))
            testResults.add(TestResultEntry(name = "A-2", passed = true))
        }
        val tabB = RequestTabState(id = "tab-b", name = "Tab B").apply {
            testResults.add(TestResultEntry(name = "B-1", passed = false, message = "Expected 200"))
        }
        state.openTabs.add(tabA)
        state.openTabs.add(tabB)

        state.activeTabIndex = 0
        assertEquals(2, state.testResults.size)
        assertEquals("A-1", state.testResults[0].name)

        state.activeTabIndex = 1
        assertEquals(1, state.testResults.size)
        assertEquals("B-1", state.testResults[0].name)
        assertTrue(!state.testResults[0].passed)

        state.activeTabIndex = 0
        assertEquals(2, state.testResults.size)
        assertEquals("A-2", state.testResults[1].name)
    }

    @Test
    fun consoleLogs_and_networkEventLogs_enforce_maximum_bounds_and_evict_oldest_entries() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        for (i in 1..1_200) {
            state.logNetworkEvent("event-$i", LogLevel.INFO, echoToConsole = true)
        }

        assertEquals(DiagnosticsLogState.MAX_CONSOLE_LOGS, state.consoleLogs.size)
        assertEquals(DiagnosticsLogState.MAX_NETWORK_EVENT_LOGS, state.networkEventLogs.size)
        assertEquals("event-1200", state.consoleLogs.first().message)
        assertEquals("event-201", state.consoleLogs.last().message)
        assertEquals("event-1200", state.networkEventLogs.first().message)
        assertEquals("event-201", state.networkEventLogs.last().message)
    }

    @Test
    fun historyItems_streamChunks_and_webSocketLogMessages_enforce_maximum_bounds() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        val rootFolder = CollectionNode(
            id = "col-root",
            name = "Root",
            isFolder = true,
        )
        for (i in 1..600) {
            rootFolder.children.add(
                CollectionNode(
                    id = "req-$i",
                    requestRef = "ref-$i",
                    name = "Req $i",
                    isFolder = false,
                    method = HttpMethodType.GET,
                    url = "https://example.com/$i",
                )
            )
        }
        state.collections.add(rootFolder)

        for (i in 1..600) {
            state.recordHistory(
                requestId = "req-$i",
                method = HttpMethodType.GET,
                name = "Req $i",
                url = "https://example.com/$i",
            )
        }
        assertEquals(DiagnosticsLogState.MAX_HISTORY_ITEMS, state.historyItems.size)
        assertEquals("req-600", state.historyItems.first().requestId)
        assertEquals("req-101", state.historyItems.last().requestId)

        val tab = RequestTabState(id = "tab-stream", name = "Stream Tab")
        for (i in 1..5_200) {
            tab.appendStreamChunk("chunk-$i")
        }
        assertEquals(RequestTabState.MAX_STREAM_CHUNKS, tab.streamChunks.size)
        assertEquals("chunk-201", tab.streamChunks.first())
        assertEquals("chunk-5200", tab.streamChunks.last())

        for (i in 1..1_100) {
            tab.appendWebSocketLogMessage(
                WebSocketLogMessage(
                    id = "ws-$i",
                    timestamp = i.toLong(),
                    direction = MessageDirection.RECEIVED,
                    format = "text",
                    payload = "msg-$i",
                    byteLength = 5L,
                )
            )
        }
        assertEquals(RequestTabState.MAX_WEBSOCKET_LOGS, tab.webSocketLogMessages.size)
        assertEquals("msg-101", tab.webSocketLogMessages.first().payload)
        assertEquals("msg-1100", tab.webSocketLogMessages.last().payload)
    }
}
