package io.github.wrightdk.repertory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.wrightdk.repertory.data.local.entity.ImageConfig

@Dao
interface ImageConfigDao {
    @Upsert
    suspend fun upsert(config: ImageConfig)

    @Query("SELECT * FROM image_config WHERE id = ${ImageConfig.SINGLE_ROW_ID}")
    suspend fun get(): ImageConfig?
}
