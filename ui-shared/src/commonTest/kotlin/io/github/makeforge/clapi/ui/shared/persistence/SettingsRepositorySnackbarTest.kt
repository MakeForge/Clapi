package io.github.makeforge.clapi.ui.shared.persistence

import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.github.makeforge.clapi.ui.shared.state.DEFAULT_SNACKBAR_AUTO_DISMISS_SEC
import io.github.makeforge.clapi.ui.shared.state.MAX_SNACKBAR_AUTO_DISMISS_SEC
import io.github.makeforge.clapi.ui.shared.state.MIN_SNACKBAR_AUTO_DISMISS_SEC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositorySnackbarTest {

    private val key = "settings.snackbarAutoDismissSec"

    @BeforeTest
    fun setUp() {
        PlatformStorage.remove(key)
    }

    @AfterTest
    fun tearDown() {
        PlatformStorage.remove(key)
    }

    @Test
    fun defaults_to_5_seconds_when_not_persisted() {
        val settings = AppSettings()
        SettingsRepository.load(settings)
        assertEquals(DEFAULT_SNACKBAR_AUTO_DISMISS_SEC, settings.snackbarAutoDismissSec)
        assertEquals(5, settings.snackbarAutoDismissSec)
    }

    @Test
    fun save_and_load_round_trip_persists_valid_duration() {
        val toSave = AppSettings().apply { snackbarAutoDismissSec = 12 }
        SettingsRepository.save(toSave)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)
        assertEquals(12, loaded.snackbarAutoDismissSec)
    }

    @Test
    fun out_of_range_values_are_clamped_on_load_and_save() {
        PlatformStorage.putString(key, "0")
        val lowLoaded = AppSettings()
        SettingsRepository.load(lowLoaded)
        assertEquals(MIN_SNACKBAR_AUTO_DISMISS_SEC, lowLoaded.snackbarAutoDismissSec)

        PlatformStorage.putString(key, "999")
        val highLoaded = AppSettings()
        SettingsRepository.load(highLoaded)
        assertEquals(MAX_SNACKBAR_AUTO_DISMISS_SEC, highLoaded.snackbarAutoDismissSec)

        val invalidSave = AppSettings().apply { snackbarAutoDismissSec = -5 }
        SettingsRepository.save(invalidSave)
        val afterSave = AppSettings()
        SettingsRepository.load(afterSave)
        assertEquals(MIN_SNACKBAR_AUTO_DISMISS_SEC, afterSave.snackbarAutoDismissSec)
    }
}
