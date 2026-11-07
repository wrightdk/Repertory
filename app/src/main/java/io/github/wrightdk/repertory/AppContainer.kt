package io.github.wrightdk.repertory

import android.content.Context
import io.github.wrightdk.repertory.data.TmdbRepository
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.remote.ApiKeyProvider
import io.github.wrightdk.repertory.data.remote.TmdbClient
import io.github.wrightdk.repertory.data.remote.TmdbService
import java.io.File

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
}
