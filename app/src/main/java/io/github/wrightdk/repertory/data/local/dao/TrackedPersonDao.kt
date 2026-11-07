package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedPersonDao {
    /** Returns the new row id, or -1 if this person is already tracked in that department. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(trackedPerson: TrackedPerson): Long

    @Update
    suspend fun update(trackedPerson: TrackedPerson)

    @Delete
    suspend fun delete(trackedPerson: TrackedPerson)

    @Query("SELECT * FROM tracked_person ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackedPerson>>

    @Query("SELECT COUNT(*) FROM tracked_person")
    fun observeCount(): Flow<Int>
}
