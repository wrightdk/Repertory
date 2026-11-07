package io.github.wrightdk.repertory.data.remote

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Supplies the TMDB key for each request. Read per request so a user-supplied
 * key from Settings takes effect immediately, without rebuilding the client.
 */
fun interface ApiKeyProvider {
    fun apiKey(): String
}

/** Appends `api_key` to every request. */
class ApiKeyInterceptor(private val keyProvider: ApiKeyProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder()
            .setQueryParameter("api_key", keyProvider.apiKey())
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}

object TmdbClient {
    val DEFAULT_BASE_URL: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl()

    /** Lenient on purpose: TMDB adds fields and sometimes sends null for documented ones. */
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun createService(
        keyProvider: ApiKeyProvider,
        baseUrl: HttpUrl = DEFAULT_BASE_URL,
        cacheDir: File? = null,
    ): TmdbService {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(keyProvider))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .apply { if (cacheDir != null) cache(Cache(cacheDir, HTTP_CACHE_BYTES)) }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbService::class.java)
    }

    private const val HTTP_CACHE_BYTES = 10L * 1024 * 1024
}
