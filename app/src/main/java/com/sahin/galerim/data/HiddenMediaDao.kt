package com.sahin.galerim.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HiddenMediaDao {
    @Query("SELECT * FROM hidden_media ORDER BY dateAdded DESC")
    suspend fun getAllHiddenMedia(): List<HiddenMedia>

    @Insert
    suspend fun insert(hiddenMedia: HiddenMedia)

    @Delete
    suspend fun delete(hiddenMedia: HiddenMedia)

    @Query("SELECT * FROM hidden_media WHERE originalPath = :path LIMIT 1")
    suspend fun getByOriginalPath(path: String): HiddenMedia?
    
    @Query("SELECT * FROM hidden_media WHERE hiddenPath = :path LIMIT 1")
    suspend fun getByHiddenPath(path: String): HiddenMedia?
}
