// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e.support

import io.github.makeforge.clapi.core.storage.opencollection.CanonicalCollectionRepository
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppState
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files

/**
 * Isolates canonical collection storage, [PlatformStorage] keys, and [AppState] disposal per test
 * so UI E2E tests never leak preferences or active sessions across test methods.
 */
class IsolatedWorkspaceRule : ExternalResource() {

    lateinit var tempDir: File
        private set

    lateinit var repository: DefaultCanonicalCollectionRepository
        private set

    private var previousRepository: CanonicalCollectionRepository? = null
    private val trackedStates = mutableListOf<AppState>()

    fun track(state: AppState): AppState {
        trackedStates += state
        return state
    }

    fun newAppState(openDefaultTab: Boolean = true): AppState = track(AppState(openDefaultTab = openDefaultTab))

    override fun before() {
        tempDir = Files.createTempDirectory("clapi-ui-e2e-workspace-").toFile()
        previousRepository = WorkspaceRepository.canonicalRepository
        repository = DefaultCanonicalCollectionRepository(baseDirectory = tempDir.absolutePath)
        WorkspaceRepository.canonicalRepository = repository
        clearStorageKeys()
    }

    override fun after() {
        trackedStates.forEach { state ->
            runCatching { runBlocking { state.disposeAndAwait() } }
        }
        trackedStates.clear()
        clearStorageKeys()
        previousRepository?.let { WorkspaceRepository.canonicalRepository = it }
        previousRepository = null
        if (::tempDir.isInitialized) {
            tempDir.deleteRecursively()
        }
    }

    private fun clearStorageKeys() {
        STORAGE_KEYS.forEach { PlatformStorage.remove(it) }
    }

    companion object {
        private val STORAGE_KEYS = listOf(
            "clapi.tabs",
            "clapi.workspace",
            "clapi.settings",
            "clapi.private_state",
        )
    }
}
