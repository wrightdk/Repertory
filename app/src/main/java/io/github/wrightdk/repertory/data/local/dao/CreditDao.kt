package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.github.wrightdk.repertory.data.local.entity.Credit
import io.github.wrightdk.repertory.data.local.entity.Film

@Dao
interface CreditDao {
    @Insert
    suspend fun insertAll(credits: List<Credit>)

    @Query("DELETE FROM credit WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Int)

    /**
     * Every credit [personId] has, with its department and film, for counting
     * what each department's list would show (see `TmdbRepository.roles`).
     */
    @Query(
        """
        SELECT credit.department AS department, film.*, credit.job AS credit_job,
            credit.character AS credit_character, credit.`order` AS credit_order, credit.isSelf AS credit_isSelf
        FROM credit JOIN film ON film.mediaType = credit.mediaType AND film.tmdbId = credit.filmId
        WHERE credit.personId = :personId
        """
    )
    suspend fun creditsWithFilms(personId: Int): List<DepartmentFilmCredit>

    /** Crew jobs [personId] has, with how many credits each, most used first within each department. */
    @Query(
        """
        SELECT department, job, COUNT(*) AS creditCount FROM credit
        WHERE personId = :personId AND job IS NOT NULL AND job != ''
        GROUP BY department, job ORDER BY department, creditCount DESC, job
        """
    )
    suspend fun jobCounts(personId: Int): List<JobCount>

    /** Swap a person's cached credits for a fresh set from TMDB. */
    @Transaction
    suspend fun replaceForPerson(personId: Int, credits: List<Credit>) {
        deleteForPerson(personId)
        insertAll(credits)
    }
}

data class DepartmentCount(val department: String, val filmCount: Int)

/** One of a person's credits with its department and film. */
data class DepartmentFilmCredit(
    val department: String,
    @Embedded val film: Film,
    @Embedded(prefix = "credit_") val credit: CreditInfo,
)

data class JobCount(val department: String, val job: String, val creditCount: Int)
