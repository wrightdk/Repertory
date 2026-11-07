package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.Film
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmDao {
    @Upsert
    suspend fun upsertAll(films: List<Film>)

    @Query("SELECT * FROM film WHERE tmdbId = :tmdbId")
    fun observe(tmdbId: Int): Flow<Film?>
}
