package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Viewing
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewingDao {
    /** Ticks the film, or changes its seen date. */
    @Upsert
    suspend fun upsert(viewing: Viewing)

    /** Unticks the film. */
    @Query("DELETE FROM viewing WHERE mediaType = :mediaType AND filmId = :filmId")
    suspend fun delete(mediaType: MediaType, filmId: Int)

    @Query("SELECT * FROM viewing WHERE mediaType = :mediaType AND filmId = :filmId")
    fun observe(mediaType: MediaType, filmId: Int): Flow<Viewing?>

    @Query("SELECT * FROM viewing")
    fun observeAll(): Flow<List<Viewing>>

    @Query("SELECT * FROM viewing WHERE mediaType = :mediaType AND filmId = :filmId")
    suspend fun get(mediaType: MediaType, filmId: Int): Viewing?

    /** Every ticked film, in a stable order, for a backup. */
    @Query("SELECT * FROM viewing ORDER BY mediaType, filmId")
    suspend fun getAll(): List<Viewing>

    @Upsert
    suspend fun upsertAll(viewings: List<Viewing>)

    /** Unticks everything, for restoring a backup over it. */
    @Query("DELETE FROM viewing")
    suspend fun deleteAll()
}
