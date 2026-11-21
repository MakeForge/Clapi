package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.editor.core.LanguageMode
import io.github.makeforge.clapi.editor.ui.EditorViewModel
import io.github.makeforge.clapi.ui.shared.components.executor.buildAuthConfig
import io.github.makeforge.clapi.ui.shared.components.executor.enabledHeadersForSend
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for the per-tab EditorViewModel cache and URL undo stack added to
 * [RequestTabState] as part of the undo/redo persistence fix.
 */
class RequestTabStateTest {

    // ═══════════════════════════════════════════════════════════════════════
    // Body ViewModel cache
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bodyViewModelCache_returns_same_instance_for_same_body_type() {
        val tab = RequestTabState()
        val vm1 = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        val vm2 = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        assertSame(vm1, vm2, "Same BodyType must return the same EditorViewModel instance")
    }

    @Test
    fun bodyViewModelCache_creates_new_instance_for_different_body_type() {
        val tab = RequestTabState()
        val jsonVm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        val xmlVm  = tab.getOrCreateBodyViewModel(BodyType.XML, "<r/>", LanguageMode.XML)
        assertNotSame(jsonVm, xmlVm, "Different BodyTypes must produce distinct EditorViewModel instances")
    }

    @Test
    fun bodyViewModelCache_initial_text_is_used_only_on_first_creation() {
        val tab = RequestTabState()
        // First call with "initial text"
        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "initial text", LanguageMode.JSON)
        assertEquals("initial text", vm.getFullText(), "Initial text should be set on first creation")

        // Second call with different initial text — should return same VM (not re-init)
        val vm2 = tab.getOrCreateBodyViewModel(BodyType.JSON, "different text", LanguageMode.JSON)
        assertSame(vm, vm2, "Must return same VM instance on second call")
        assertEquals("initial text", vm2.getFullText(), "Text should NOT be overwritten by second call")
    }

    @Test
    fun disposeBodyViewModels_clears_cache_and_cancels_scopes() {
        val tab = RequestTabState()
        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)

        // Make an edit so there's something in the undo stack
        vm.moveCursorTo(1)
        vm.insertAtCursor("a")

        tab.disposeBodyViewModels()

        // After dispose, a new call must return a fresh instance (cache was cleared)
        val vmAfterDispose = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        assertNotSame(vm, vmAfterDispose, "After disposeBodyViewModels, a new VM instance must be created")
    }

    @Test
    fun undo_history_is_preserved_across_multiple_cache_retrievals() {
        val tab = RequestTabState()

        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        vm.moveCursorTo(1)
        vm.insertAtCursor("x")
        vm.insertAtCursor("y")
        assertEquals("{xy}", vm.getFullText())

        // Simulate re-entering the Body tab: get the same VM, verify undo still works
        val vmAgain = tab.getOrCreateBodyViewModel(BodyType.JSON, "{xy}", LanguageMode.JSON)
        assertSame(vm, vmAgain)
        vmAgain.undo()
        assertEquals("{x}", vmAgain.getFullText(), "Undo history must survive tab re-entry via cache")
        vmAgain.undo()
        assertEquals("{}", vmAgain.getFullText())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // URL undo stack
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun urlUndoStack_is_initially_empty() {
        val tab = RequestTabState()
        assertTrue(tab.urlUndoStack.isEmpty(), "urlUndoStack must start empty")
    }

    @Test
    fun urlUndoStack_accumulates_entries_on_text_change() {
        val tab = RequestTabState()
        tab.urlUndoStack.addLast("https://example.com")
        tab.urlUndoStack.addLast("https://example.com/api")
        assertEquals(2, tab.urlUndoStack.size)
        assertEquals("https://example.com/api", tab.urlUndoStack.last())
    }

    @Test
    fun urlUndoStack_pop_restores_previous_value() {
        val tab = RequestTabState()
        tab.urlUndoStack.addLast("https://before.com")
        val restored = tab.urlUndoStack.removeLastOrNull()
        assertEquals("https://before.com", restored, "Pop must return the last pushed value")
        assertTrue(tab.urlUndoStack.isEmpty(), "Stack must be empty after pop")
    }

    @Test
    fun urlUndoStack_survives_body_type_switch() {
        // The URL undo stack is on RequestTabState — body type changes must not affect it
        val tab = RequestTabState()
        tab.urlUndoStack.addLast("https://example.com")

        tab.bodyType = BodyType.JSON
        tab.bodyType = BodyType.XML

        assertEquals(1, tab.urlUndoStack.size, "urlUndoStack must be unaffected by bodyType changes")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // closeTab / closeTabsByIds lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun closeTab_disposes_cached_viewModels_before_removal() {
        val state = AppState(openDefaultTab = false)
        val tab = RequestTabState()
        state.openTabs.add(tab)

        // Pre-populate the cache
        val vm = tab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        vm.moveCursorTo(1)
        vm.insertAtCursor("z")

        state.closeTab(0)

        // The tab is gone from openTabs
        assertEquals(0, state.openTabs.size)

        // A completely new tab's cache is fresh — creating a VM there must differ from the disposed one.
        val newTab = RequestTabState()
        val freshVm = newTab.getOrCreateBodyViewModel(BodyType.JSON, "{}", LanguageMode.JSON)
        assertNotSame(vm, freshVm, "After tab close, old VM must not be reused by a new tab")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // US3: Inherited Settings Resolution & Wire Overrides
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun inherited_headers_display_with_HeaderKind_INHERITED_and_keyLocked() {
        val state = AppState(openDefaultTab = false)
        val folder = CollectionNode(
            id = "f-api",
            name = "API Folder",
            isFolder = true,
            headerEntries = listOf(
                KeyValueEntry("X-Api-Version", "v2", enabled = true),
                KeyValueEntry("X-Shared-Secret", "xyz", enabled = true),
            ),
            children = mutableStateListOf(
                CollectionNode(
                    id = "r-child",
                    name = "Get Item",
                    isFolder = false,
                    method = HttpMethodType.GET,
                    url = "https://example.com/items",
                ),
            ),
        )
        state.collections.add(folder)

        state.openRequest(requestId = "r-child", name = "Get Item", method = HttpMethodType.GET, url = "https://example.com/items")

        val tab = state.openTabs.first { it.id == "r-child" }
        val inheritedVersion = tab.headers.firstOrNull { it.key.equals("X-Api-Version", ignoreCase = true) }
        val inheritedSecret = tab.headers.firstOrNull { it.key.equals("X-Shared-Secret", ignoreCase = true) }

        assertTrue(inheritedVersion != null, "Inherited header X-Api-Version must be present")
        assertEquals("v2", inheritedVersion.value)
        assertEquals(HeaderKind.INHERITED, inheritedVersion.kind)
        assertTrue(inheritedVersion.keyLocked)

        assertTrue(inheritedSecret != null, "Inherited header X-Shared-Secret must be present")
        assertEquals(HeaderKind.INHERITED, inheritedSecret.kind)
        assertTrue(inheritedSecret.keyLocked)
    }

    @Test
    fun inherited_headers_case_insensitive_override_during_send() {
        val tab = RequestTabState(name = "Override Test")
        // Folder inherited header
        tab.headers.add(
            MutableKeyValue(
                key = "authorization",
                value = "Bearer folder-token",
                kind = HeaderKind.INHERITED,
                keyLocked = true,
            )
        )
        // User overrides it with different casing
        tab.headers.add(
            MutableKeyValue(
                key = "Authorization",
                value = "Bearer user-token",
                kind = HeaderKind.USER,
            )
        )
        tab.headers.add(
            MutableKeyValue(
                key = "X-Other",
                value = "kept",
                kind = HeaderKind.INHERITED,
                keyLocked = true,
            )
        )

        val wireHeaders = enabledHeadersForSend(tab)
        val authHeaders = wireHeaders.filter { it.key.equals("authorization", ignoreCase = true) }

        assertEquals(1, authHeaders.size, "User header must override inherited header case-insensitively")
        assertEquals("Bearer user-token", authHeaders[0].value)
        assertTrue(wireHeaders.any { it.key == "X-Other" && it.value == "kept" })
    }

    @Test
    fun auth_inheritance_resolves_closest_ancestor() {
        val state = AppState(openDefaultTab = false)
        val childFolder = CollectionNode(
            id = "f-sub",
            name = "Subfolder",
            isFolder = true,
            authType = AuthType.BEARER,
            authToken = "child-token-123",
            children = mutableStateListOf(
                CollectionNode(
                    id = "r-leaf",
                    name = "Leaf Request",
                    isFolder = false,
                    method = HttpMethodType.GET,
                    url = "https://example.com/leaf",
                ),
            ),
        )
        val rootFolder = CollectionNode(
            id = "f-root",
            name = "Root Collection",
            isFolder = true,
            authType = AuthType.BASIC,
            authUsername = "rootUser",
            authPassword = "rootPassword",
            children = mutableStateListOf(childFolder),
        )
        state.collections.add(rootFolder)

        state.openRequest(requestId = "r-leaf", name = "Leaf Request", method = HttpMethodType.GET, url = "https://example.com/leaf")
        val tab = state.openTabs.first { it.id == "r-leaf" }
        tab.authType = AuthType.INHERIT

        val resolved = state.resolveInheritedAuth(tab)
        assertTrue(resolved != null)
        assertEquals("Subfolder", resolved.folderName)
        assertEquals(AuthType.BEARER, resolved.authType)
        assertEquals("child-token-123", resolved.token)

        val config = buildAuthConfig(tab, state)
        assertEquals(AuthType.BEARER, config.type)
        assertEquals(mapOf("token" to "child-token-123"), config.params)
    }

    @Test
    fun auth_inheritance_cascades_to_root_when_intermediate_has_no_auth() {
        val state = AppState(openDefaultTab = false)
        val childFolder = CollectionNode(
            id = "f-sub-no-auth",
            name = "Subfolder No Auth",
            isFolder = true,
            authType = AuthType.NONE,
            children = mutableStateListOf(
                CollectionNode(
                    id = "r-deep",
                    name = "Deep Request",
                    isFolder = false,
                    method = HttpMethodType.GET,
                    url = "https://example.com/deep",
                ),
            ),
        )
        val rootFolder = CollectionNode(
            id = "f-root-auth",
            name = "Root Auth Collection",
            isFolder = true,
            authType = AuthType.API_KEY,
            authApiKey = "X-Api-Key",
            authApiValue = "secret-key",
            authApiPlacement = "header",
            children = mutableStateListOf(childFolder),
        )
        state.collections.add(rootFolder)

        state.openRequest(requestId = "r-deep", name = "Deep Request", method = HttpMethodType.GET, url = "https://example.com/deep")
        val tab = state.openTabs.first { it.id == "r-deep" }
        tab.authType = AuthType.INHERIT

        val resolved = state.resolveInheritedAuth(tab)
        assertTrue(resolved != null)
        assertEquals("Root Auth Collection", resolved.folderName)
        assertEquals(AuthType.API_KEY, resolved.authType)
        assertEquals("X-Api-Key", resolved.apiKey)
        assertEquals("secret-key", resolved.apiValue)

        val config = buildAuthConfig(tab, state)
        assertEquals(AuthType.API_KEY, config.type)
        assertEquals(mapOf("key" to "X-Api-Key", "value" to "secret-key"), config.params)
        assertEquals("header", config.placement)
    }
}
