package io.github.wrightdk.repertory.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val keyPrefs = context.getSharedPreferences(SettingsStore.KEY_PREFS_NAME, Context.MODE_PRIVATE)

    private fun store() = SettingsStore(prefs, keyPrefs)

    @Test fun `own key is saved apart from the backed-up settings`() {
        store().setOwnTmdbApiKey(" abc123 ")
        assertEquals("abc123", keyPrefs.getString("own_tmdb_api_key", null))
        assertFalse(prefs.contains("own_tmdb_api_key"))
        assertEquals("abc123", store().ownTmdbApiKey.value)

        store().setOwnTmdbApiKey(null)
        assertNull(store().ownTmdbApiKey.value)
        assertFalse(keyPrefs.contains("own_tmdb_api_key"))
    }

    @Test fun `the where to watch country is saved, cleared, and only ever a country code`() {
        assertNull(store().watchRegion.value)

        store().setWatchRegion("FR")
        assertEquals("FR", store().watchRegion.value)

        store().setWatchRegion("France")
        assertEquals("FR", store().watchRegion.value)

        store().setWatchRegion(null)
        assertNull(store().watchRegion.value)
        assertFalse(prefs.contains("watch_region"))
    }

    @Test fun `a key saved with the other settings moves to its own file`() {
        prefs.edit().putString("own_tmdb_api_key", "old-key").putString("theme", "DARK").commit()

        val store = store()
        assertEquals("old-key", store.ownTmdbApiKey.value)
        assertEquals(ThemeMode.DARK, store.themeMode.value)
        assertFalse(prefs.contains("own_tmdb_api_key"))
        assertEquals("old-key", keyPrefs.getString("own_tmdb_api_key", null))
    }
}
