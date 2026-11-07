package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.Viewing
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewingDao {
    @Upsert
    suspend fun upsert(viewing: Viewing)

    @Query("DELETE FROM viewing WHERE filmId = :filmId")
    suspend fun delete(filmId: Int)

    @Query("SELECT * FROM viewing WHERE filmId = :filmId")
    fun observe(filmId: Int): Flow<Viewing?>
}
