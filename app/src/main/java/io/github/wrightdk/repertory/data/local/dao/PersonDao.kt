package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.Person
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Upsert
    suspend fun upsert(person: Person)

    @Query("SELECT * FROM person WHERE tmdbId = :tmdbId")
    suspend fun get(tmdbId: Int): Person?

    @Query("SELECT * FROM person WHERE tmdbId = :tmdbId")
    fun observe(tmdbId: Int): Flow<Person?>
}
