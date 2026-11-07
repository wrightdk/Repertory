package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.github.wrightdk.repertory.data.local.entity.WatchRegion

@Dao
interface WatchRegionDao {
    @Query("SELECT * FROM watch_region")
    suspend fun getAll(): List<WatchRegion>

    @Query("DELETE FROM watch_region")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(regions: List<WatchRegion>)

    /** Swaps in a newly fetched list. */
    @Transaction
    suspend fun replaceAll(regions: List<WatchRegion>) {
        deleteAll()
        insertAll(regions)
    }

    /** Drops the list if it was fetched before [cutoff], or stamped after [now] (the clock went back). */
    @Query("DELETE FROM watch_region WHERE fetchedAt < :cutoff OR fetchedAt > :now")
    suspend fun deleteFetchedOutside(cutoff: Long, now: Long): Int
}
