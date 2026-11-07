package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.github.wrightdk.repertory.data.local.entity.Credit

@Dao
interface CreditDao {
    @Insert
    suspend fun insertAll(credits: List<Credit>)

    @Query("DELETE FROM credit WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Int)

    /**
     * The departments [personId] has credits in, with how many distinct titles each
     * (a movie and a series can share a TMDB id, so both columns identify a title). Most first.
     */
    @Query(
        """
        SELECT department, COUNT(DISTINCT mediaType || ':' || filmId) AS filmCount FROM credit
        WHERE personId = :personId GROUP BY department ORDER BY filmCount DESC, department
        """
    )
    suspend fun departments(personId: Int): List<DepartmentCount>

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

data class JobCount(val department: String, val job: String, val creditCount: Int)
