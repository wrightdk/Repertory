package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.WatchProvidersCache

@Dao
interface WatchProvidersDao {
    @Upsert
    suspend fun upsert(entry: WatchProvidersCache)

    @Query("SELECT * FROM watch_providers WHERE movieId = :movieId AND region = :region")
    suspend fun get(movieId: Int, region: String): WatchProvidersCache?

    @Query("DELETE FROM watch_providers WHERE movieId = :movieId AND region = :region")
    suspend fun delete(movieId: Int, region: String)

    /** Drops rows fetched before [cutoff], or stamped after [now] (the clock went back). */
    @Query("DELETE FROM watch_providers WHERE fetchedAt < :cutoff OR fetchedAt > :now")
    suspend fun deleteFetchedOutside(cutoff: Long, now: Long): Int
}
