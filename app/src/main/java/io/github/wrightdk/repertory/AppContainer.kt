package io.github.wrightdk.repertory

import android.content.Context
import android.util.Log
import io.github.wrightdk.repertory.data.FilmDetailsSync
import io.github.wrightdk.repertory.data.SettingsStore
import io.github.wrightdk.repertory.data.Suggestion
import io.github.wrightdk.repertory.data.TmdbRepository
import io.github.wrightdk.repertory.data.backup.Backup
import io.github.wrightdk.repertory.data.backup.BackupManager
import io.github.wrightdk.repertory.data.backup.RestoreMode
import io.github.wrightdk.repertory.data.parseSuggestions
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.remote.ApiKeyProvider
import io.github.wrightdk.repertory.data.remote.RetryPolicy
import io.github.wrightdk.repertory.data.remote.TmdbClient
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.data.remote.TmdbService
import io.github.wrightdk.repertory.data.remote.map
import io.github.wrightdk.repertory.data.remote.tmdbCall
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Manual dependency wiring. One instance lives in [RepertoryApplication].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: RepertoryDatabase by lazy { RepertoryDatabase.build(appContext) }

    /** Read on the main thread at startup for the theme; a tiny prefs file, so that's fine. */
    val settings: SettingsStore by lazy {
        SettingsStore(
            prefs = appContext.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE),
            keyPrefs = appContext.getSharedPreferences(SettingsStore.KEY_PREFS_NAME, Context.MODE_PRIVATE),
        )
    }

    // The user's own key from Settings wins over the built-in one. Read per
    // request, so a change applies without rebuilding the client.
    private val apiKeyProvider = ApiKeyProvider { settings.ownTmdbApiKey.value ?: BuildConfig.TMDB_API_KEY }

    private val tmdbService: TmdbService by lazy {
        TmdbClient.createService(
            keyProvider = apiKeyProvider,
            cacheDir = File(appContext.cacheDir, "tmdb-http"),
        )
    }

    val tmdbRepository: TmdbRepository by lazy { TmdbRepository(tmdbService, database) }

    /**
     * Outlives every screen, for work that must finish once started. A failure
     * in one job is logged and doesn't cancel the others or crash the app.
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e("Repertory", "Background job failed", e) },
    )

    val filmDetailsSync: FilmDetailsSync by lazy {
        FilmDetailsSync(
            scope = appScope,
            trackedList = database.trackedPersonDao()::get,
            listIdsMissingDetails = database.filmDao().observeListIdsMissingDetails(),
            fillMissingFilmDetails = tmdbRepository::fillMissingFilmDetails,
            listIdsMissingPerson = database.trackedPersonDao().observeIdsMissingPerson(),
            fetchPersonIfMissing = tmdbRepository::fetchPersonIfMissing,
            log = { Log.i("Repertory", it) },
        )
    }

    /**
     * Asks TMDB whether [key] works, before Settings saves it. Uses its own
     * uncached client so the answer is about this key, not a cached response.
     */
    suspend fun checkTmdbApiKey(key: String): TmdbResult<Unit> = withContext(Dispatchers.IO) {
        val service = TmdbClient.createService(keyProvider = { key })
        tmdbCall(RetryPolicy.NONE) { service.configuration() }.map { }
    }

    /** Saves (or with null, clears) the user's own TMDB key and retries anything that stopped on the old one. */
    fun setOwnTmdbApiKey(key: String?) {
        settings.setOwnTmdbApiKey(key)
        filmDetailsSync.retryFailed()
    }

    val backupManager: BackupManager by lazy {
        BackupManager(database = database, settings = settings, appVersion = BuildConfig.VERSION_NAME)
    }

    /**
     * Restores [backup], then fetches from TMDB whatever the restored lists
     * need that this phone hasn't cached (people, credits, film details).
     */
    suspend fun restoreBackup(backup: Backup, mode: RestoreMode) {
        backupManager.restore(backup, mode)
        filmDetailsSync.backfill()
    }

    /** The people offered on the empty Home screen, from `assets/suggestions.json` (see `scripts/`). */
    suspend fun suggestions(): List<Suggestion> = withContext(Dispatchers.IO) {
        appContext.assets.open(SUGGESTIONS_ASSET).bufferedReader().use { parseSuggestions(it.readText()) }
    }

    private companion object {
        const val SUGGESTIONS_ASSET = "suggestions.json"
    }
}
