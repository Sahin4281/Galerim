package com.sahin.galerim.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screenshot_urls")
data class UrlEntity(
    @PrimaryKey
    val screenshotPath: String, // Ekran görüntüsünün tam dosya yolu
    val capturedUrl: String,    // Yakalanan web adresi
    val timestamp: Long = System.currentTimeMillis()
)