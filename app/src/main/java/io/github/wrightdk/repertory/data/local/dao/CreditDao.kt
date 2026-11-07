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

    /** The departments [personId] has credits in, with how many distinct films each. Most films first. */
    @Query(
        """
        SELECT department, COUNT(DISTINCT filmId) AS filmCount FROM credit
        WHERE personId = :personId GROUP BY department ORDER BY filmCount DESC, department
        """
    )
    suspend fun departments(personId: Int): List<DepartmentCount>

    /** Swap a person's cached credits for a fresh set from TMDB. */
    @Transaction
    suspend fun replaceForPerson(personId: Int, credits: List<Credit>) {
        deleteForPerson(personId)
        insertAll(credits)
    }
}

data class DepartmentCount(val department: String, val filmCount: Int)
