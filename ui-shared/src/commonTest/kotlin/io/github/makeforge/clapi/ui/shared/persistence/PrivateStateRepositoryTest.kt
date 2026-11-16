package io.github.makeforge.clapi.ui.shared.persistence

import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.state.HistoryItem
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivateStateRepositoryTest {

    @Test
    fun saveAndLoadRoundTripUsesNoClapiType() {
        val files = mutableMapOf<String, String>()
        val directory = "/tmp/clapi-private-state"
        val repository = PrivateStateRepository(
            baseDirectory = directory,
            readText = { path -> files[path] },
            writeText = { path, content -> files[path] = content },
        )
        val state = PrivateWorkspaceState(
            globalVariables = listOf(MutableKeyValue(key = "host", value = "localhost", secret = true).toStoredVariable()),
            history = listOf(
                HistoryItem(
                    requestId = "req-1",
                    method = HttpMethodType.POST,
                    name = "Create",
                    url = "https://example.test",
                    timestamp = 10L,
                    collectionId = "col-1",
                    folderPath = listOf("API"),
                ).toStoredHistoryItem()
            ),
            expandedState = mapOf("col-1" to false),
        )

        repository.save(state)
        val path = "$directory/$PRIVATE_STATE_FILENAME"
        val raw = files.getValue(path)
        assertFalse(raw.contains("\"type\""), raw)
        assertFalse(raw.contains("clapi"), raw)

        val loaded = PrivateStateRepository(
            baseDirectory = directory,
            readText = { files[it] },
            writeText = { filePath, content -> files[filePath] = content },
        ).load()

        assertEquals(state, loaded)
        assertEquals(HttpMethodType.POST, loaded!!.history.single().toHistoryItem().method)
    }

    @Test
    fun loadReturnsNullWhenDirectoryIsEmpty() {
        val repository = PrivateStateRepository(
            baseDirectory = "/unused",
            readText = { null },
            writeText = { _, _ -> error("not used") },
        )
        assertNull(repository.load())
    }

    @Test
    fun memoryStoreRoundTripsWithoutADirectory() {
        val repository = PrivateStateRepository()
        repository.save(PrivateWorkspaceState(expandedState = mapOf("folder" to true)))
        assertTrue(repository.load()!!.expandedState["folder"] == true)
    }
}
