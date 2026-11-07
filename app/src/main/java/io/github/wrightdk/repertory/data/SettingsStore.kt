package io.github.wrightdk.repertory.data

import android.content.SharedPreferences
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** How Home orders the lists, from the sort chips. */
enum class HomeSort { NAME, PROGRESS, RECENT }

/**
 * App-wide preferences: from Settings, the default filters every list follows
 * unless it has its own, the theme and the user's own TMDB API key; from Home,
 * the chosen sort. Small and read at startup (the theme
 * must be known before the first frame), so plain SharedPreferences rather
 * than Room. One instance, in [io.github.wrightdk.repertory.AppContainer].
 */
class SettingsStore(private val prefs: SharedPreferences) {

    private val _filterDefaults = MutableStateFlow(readFilters())
    val filterDefaults: StateFlow<FilmographyFilters> = _filterDefaults.asStateFlow()

    private val _themeMode = MutableStateFlow(readTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _homeSort = MutableStateFlow(readHomeSort())
    val homeSort: StateFlow<HomeSort> = _homeSort.asStateFlow()

    private val _ownTmdbApiKey = MutableStateFlow(prefs.getString(KEY_OWN_TMDB_API_KEY, null)?.takeIf { it.isNotBlank() })

    /**
     * The user's own TMDB API key from Settings, or null to use the built-in one.
     * Read per request, so a change applies to the next call.
     */
    val ownTmdbApiKey: StateFlow<String?> = _ownTmdbApiKey.asStateFlow()

    fun setFilterDefaults(filters: FilmographyFilters) {
        prefs.edit()
            .putBoolean(KEY_SHORTS, filters.showShorts)
            .putBoolean(KEY_TV, filters.showTv)
            .putBoolean(KEY_DOCUMENTARIES, filters.showDocumentaries)
            .putBoolean(KEY_SELF, filters.showSelf)
            .putBoolean(KEY_MINOR_ROLES, filters.showMinorRoles)
            .apply()
        _filterDefaults.value = filters
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setHomeSort(sort: HomeSort) {
        prefs.edit().putString(KEY_HOME_SORT, sort.name).apply()
        _homeSort.value = sort
    }

    /** Saves the user's own TMDB key; null or blank goes back to the built-in key. */
    fun setOwnTmdbApiKey(key: String?) {
        val value = key?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().apply { if (value == null) remove(KEY_OWN_TMDB_API_KEY) else putString(KEY_OWN_TMDB_API_KEY, value) }.apply()
        _ownTmdbApiKey.value = value
    }

    private fun readFilters(): FilmographyFilters {
        val d = FilmographyFilters.DEFAULTS
        return FilmographyFilters(
            showShorts = prefs.getBoolean(KEY_SHORTS, d.showShorts),
            showTv = prefs.getBoolean(KEY_TV, d.showTv),
            showDocumentaries = prefs.getBoolean(KEY_DOCUMENTARIES, d.showDocumentaries),
            showSelf = prefs.getBoolean(KEY_SELF, d.showSelf),
            showMinorRoles = prefs.getBoolean(KEY_MINOR_ROLES, d.showMinorRoles),
        )
    }

    private fun readTheme(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM

    private fun readHomeSort(): HomeSort =
        HomeSort.entries.firstOrNull { it.name == prefs.getString(KEY_HOME_SORT, null) } ?: HomeSort.NAME

    companion object {
        const val PREFS_NAME = "settings"
        private const val KEY_SHORTS = "filter_show_shorts"
        private const val KEY_TV = "filter_show_tv"
        private const val KEY_DOCUMENTARIES = "filter_show_documentaries"
        private const val KEY_SELF = "filter_show_self"
        private const val KEY_MINOR_ROLES = "filter_show_minor_roles"
        private const val KEY_THEME = "theme"
        private const val KEY_HOME_SORT = "home_sort"
        private const val KEY_OWN_TMDB_API_KEY = "own_tmdb_api_key"
    }
}
