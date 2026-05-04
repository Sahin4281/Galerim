package com.sahin.galerim.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_media")
data class HiddenMedia(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalPath: String,
    val hiddenPath: String,
    val isVideo: Boolean,
    val dateAdded: Long,
    val originalDate: Long
)
