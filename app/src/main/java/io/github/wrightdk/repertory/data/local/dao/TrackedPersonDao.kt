package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.MediaType
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

    /** Removes the list only. Viewings are keyed by film and stay. */
    @Query("DELETE FROM tracked_person WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tracked_person WHERE id = :id")
    fun observe(id: Long): Flow<TrackedPerson?>

    @Query("SELECT * FROM tracked_person WHERE id = :id")
    suspend fun get(id: Long): TrackedPerson?

    /** Marks the list as just updated, for the Recent sort. */
    @Query("UPDATE tracked_person SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query(
        """
        UPDATE tracked_person SET filter_showShorts = :shorts, filter_showTv = :tv,
            filter_showDocumentaries = :documentaries, filter_showSelf = :self, filter_showMinorRoles = :minorRoles
        WHERE id = :id
        """
    )
    suspend fun setFilterColumns(id: Long, shorts: Boolean?, tv: Boolean?, documentaries: Boolean?, self: Boolean?, minorRoles: Boolean?)

    /** Gives the list its own filters, or with null makes it follow the defaults again. */
    suspend fun setFilterOverride(id: Long, filters: FilmographyFilters?) = setFilterColumns(
        id,
        filters?.showShorts,
        filters?.showTv,
        filters?.showDocumentaries,
        filters?.showSelf,
        filters?.showMinorRoles,
    )

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

    /**
     * Every list the film is in: its person has a credit on it in the list's
     * department. Whether the list's filters show it is left to the caller.
     */
    @Query(
        """
        SELECT DISTINCT tracked_person.* FROM tracked_person
        JOIN credit ON credit.personId = tracked_person.personId AND credit.department = tracked_person.department
        WHERE credit.mediaType = :mediaType AND credit.filmId = :filmId
        ORDER BY tracked_person.addedAt
        """
    )
    fun observeListsWithFilm(mediaType: MediaType, filmId: Int): Flow<List<TrackedPerson>>

    /** Marks each list as just updated, for the Recent sort. */
    @Query("UPDATE tracked_person SET updatedAt = :now WHERE id IN (:ids)")
    suspend fun touchAll(ids: List<Long>, now: Long)

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
