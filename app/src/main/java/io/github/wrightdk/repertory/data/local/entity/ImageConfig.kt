package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * TMDB image configuration from `/configuration`. Single row (id 0). Cache data.
 *
 * Image URLs are `secureBaseUrl + size + path`, e.g.
 * `https://image.tmdb.org/t/p/` + `w342` + `/abc.jpg`.
 */
@Entity(tableName = "image_config")
data class ImageConfig(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val secureBaseUrl: String,
    /** Ascending, e.g. ["w92", "w154", ..., "original"]. */
    val posterSizes: List<String>,
    val profileSizes: List<String>,
    /** Epoch millis. */
    val cachedAt: Long,
) {
    fun posterUrl(path: String?, minWidthPx: Int): String? = url(path, posterSizes, minWidthPx)

    fun profileUrl(path: String?, minWidthPx: Int): String? = url(path, profileSizes, minWidthPx)

    /**
     * A watch provider's logo, always w92: shown at 32dp (83px on the Pixel's
     * 2.6x screen), and TMDB serves w92 for logos as for posters.
     */
    fun logoUrl(path: String?): String? =
        if (path.isNullOrBlank()) null else secureBaseUrl + LOGO_SIZE + path

    private fun url(path: String?, sizes: List<String>, minWidthPx: Int): String? {
        if (path.isNullOrBlank()) return null
        return secureBaseUrl + pickSize(sizes, minWidthPx) + path
    }

    companion object {
        const val SINGLE_ROW_ID = 0

        private const val LOGO_SIZE = "w92"

        /** Used until `/configuration` has been fetched once. Values as TMDB has served for years. */
        val DEFAULT = ImageConfig(
            secureBaseUrl = "https://image.tmdb.org/t/p/",
            posterSizes = listOf("w92", "w154", "w185", "w342", "w500", "w780", "original"),
            profileSizes = listOf("w45", "w185", "h632", "original"),
            cachedAt = 0,
        )

        /** Smallest `wNNN` size at least [minWidthPx] wide, else "original". */
        internal fun pickSize(sizes: List<String>, minWidthPx: Int): String =
            sizes.filter { it.startsWith("w") }
                .mapNotNull { size -> size.drop(1).toIntOrNull()?.let { size to it } }
                .sortedBy { it.second }
                .firstOrNull { it.second >= minWidthPx }
                ?.first
                ?: "original"
    }
}
