package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.Film
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmDao {
    @Upsert
    suspend fun upsertAll(films: List<Film>)

    @Upsert
    suspend fun upsert(film: Film)

    @Query("SELECT * FROM film WHERE tmdbId = :tmdbId")
    suspend fun get(tmdbId: Int): Film?

    /** Callers keep [tmdbIds] under SQLite's bind-variable limit (chunk by 500). */
    @Query("SELECT * FROM film WHERE tmdbId IN (:tmdbIds)")
    suspend fun getByIds(tmdbIds: List<Int>): List<Film>

    @Query("SELECT * FROM film WHERE tmdbId = :tmdbId")
    fun observe(tmdbId: Int): Flow<Film?>

    /** Films credited to [personId] whose `/movie/{id}` details have never been fetched. */
    @Query(
        """
        SELECT DISTINCT film.tmdbId FROM film
        JOIN credit ON credit.filmId = film.tmdbId
        WHERE credit.personId = :personId AND film.detailsCachedAt IS NULL
        """
    )
    suspend fun idsMissingDetails(personId: Int): List<Int>

    /**
     * Every film [personId] is credited on in [department], one row per credit
     * (so a director who also edited appears once per job), oldest first with
     * undated films last. Filtering is left to the caller.
     */
    @Query(
        """
        SELECT film.*, credit.job AS credit_job, credit.character AS credit_character,
            credit.`order` AS credit_order
        FROM credit JOIN film ON film.tmdbId = credit.filmId
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
)
