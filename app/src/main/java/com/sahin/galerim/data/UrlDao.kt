package com.sahin.galerim.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UrlDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrl(urlEntity: UrlEntity)

    @Query("SELECT capturedUrl FROM screenshot_urls WHERE screenshotPath = :path")
    suspend fun getUrlForPath(path: String): String?

    @Query("DELETE FROM screenshot_urls WHERE screenshotPath = :path")
    suspend fun deleteUrlForPath(path: String)
}