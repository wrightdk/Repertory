package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM tracked_person WHERE id = :id")
    fun observe(id: Long): Flow<TrackedPerson?>

    /** Marks the list as just updated, for the Recent sort. */
    @Query("UPDATE tracked_person SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT * FROM tracked_person ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackedPerson>>

    /** TMDB ids of everyone on the list in at least one role. */
    @Query("SELECT DISTINCT personId FROM tracked_person")
    fun observeTrackedPersonIds(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM tracked_person")
    fun observeCount(): Flow<Int>

    /** The departments [personId] is already tracked in. */
    @Query("SELECT department FROM tracked_person WHERE personId = :personId")
    fun observeDepartments(personId: Int): Flow<List<String>>

    @Query("SELECT id FROM tracked_person WHERE personId = :personId AND department = :department")
    suspend fun idFor(personId: Int, department: String): Long?

    /**
     * Tracks [personId] in each of [departments], skipping any already tracked.
     * Returns the list ids in the same order, existing lists included.
     */
    @Transaction
    suspend fun track(personId: Int, departments: List<String>, now: Long): List<Long> =
        departments.map { department ->
            val id = insert(TrackedPerson(personId = personId, department = department, addedAt = now))
            if (id != -1L) id else checkNotNull(idFor(personId, department))
        }
}
