package io.github.wrightdk.repertory

import android.content.Context
import android.util.Log
import io.github.wrightdk.repertory.data.FilmDetailsSync
import io.github.wrightdk.repertory.data.TmdbRepository
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.remote.ApiKeyProvider
import io.github.wrightdk.repertory.data.remote.TmdbClient
import io.github.wrightdk.repertory.data.remote.TmdbService
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency wiring. One instance lives in [RepertoryApplication].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: RepertoryDatabase by lazy { RepertoryDatabase.build(appContext) }

    // The Settings "use my own TMDB API key" override plugs in here once it exists;
    // the provider is read per request, so no client rebuild is needed.
    private val apiKeyProvider = ApiKeyProvider { BuildConfig.TMDB_API_KEY }

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
            log = { Log.i("Repertory", it) },
        )
    }
}
