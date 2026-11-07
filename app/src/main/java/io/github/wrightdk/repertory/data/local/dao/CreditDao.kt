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

    /** Swap a person's cached credits for a fresh set from TMDB. */
    @Transaction
    suspend fun replaceForPerson(personId: Int, credits: List<Credit>) {
        deleteForPerson(personId)
        insertAll(credits)
    }
}
