package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmDao {
    @Upsert
    suspend fun upsertAll(films: List<Film>)

    @Upsert
    suspend fun upsert(film: Film)

    @Query("SELECT * FROM film WHERE mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun get(mediaType: MediaType, tmdbId: Int): Film?

    /** Callers keep [tmdbIds] under SQLite's bind-variable limit (chunk by 500). */
    @Query("SELECT * FROM film WHERE mediaType = :mediaType AND tmdbId IN (:tmdbIds)")
    suspend fun getByIds(mediaType: MediaType, tmdbIds: List<Int>): List<Film>

    @Query("SELECT * FROM film WHERE mediaType = :mediaType AND tmdbId = :tmdbId")
    fun observe(mediaType: MediaType, tmdbId: Int): Flow<Film?>

    /** Every movie in one list ([personId] in [department]), once each. */
    @Query(
        """
        SELECT DISTINCT film.* FROM film
        JOIN credit ON credit.mediaType = film.mediaType AND credit.filmId = film.tmdbId
        WHERE credit.personId = :personId AND credit.department = :department AND film.mediaType = 'MOVIE'
        """
    )
    suspend fun listMovies(personId: Int, department: String): List<Film>

    /** How many movies in one list have never had their `/movie/{id}` details fetched. */
    @Query(
        """
        SELECT COUNT(DISTINCT film.tmdbId) FROM film
        JOIN credit ON credit.mediaType = film.mediaType AND credit.filmId = film.tmdbId
        WHERE credit.personId = :personId AND credit.department = :department
            AND film.mediaType = 'MOVIE' AND film.detailsCachedAt IS NULL
        """
    )
    fun observeMissingDetailsCount(personId: Int, department: String): Flow<Int>

    /** Ids of the lists that have at least one movie whose details have never been fetched. */
    @Query(
        """
        SELECT DISTINCT tracked_person.id FROM tracked_person
        JOIN credit ON credit.personId = tracked_person.personId AND credit.department = tracked_person.department
        JOIN film ON film.mediaType = credit.mediaType AND film.tmdbId = credit.filmId
        WHERE film.mediaType = 'MOVIE' AND film.detailsCachedAt IS NULL
        """
    )
    fun observeListIdsMissingDetails(): Flow<List<Long>>

    /**
     * Every film [personId] is credited on in [department], one row per credit
     * (so a director who also edited appears once per job), oldest first with
     * undated films last. Filtering is left to the caller.
     */
    @Query(
        """
        SELECT film.*, credit.job AS credit_job, credit.character AS credit_character,
            credit.`order` AS credit_order, credit.isSelf AS credit_isSelf
        FROM credit JOIN film ON film.mediaType = credit.mediaType AND film.tmdbId = credit.filmId
        WHERE credit.personId = :personId AND credit.department = :department
        ORDER BY film.releaseDate IS NULL, film.releaseDate, film.title
        """
    )
    fun observeFilmography(personId: Int, department: String): Flow<List<FilmCredit>>
}

/** A film joined with one of a person's credits on it. */
data class FilmCredit(
    @Embedded val film: Film,
    @Embedded(prefix = "credit_") val credit: CreditInfo,
)

data class CreditInfo(
    val job: String?,
    val character: String?,
    val order: Int?,
    val isSelf: Boolean,
)
