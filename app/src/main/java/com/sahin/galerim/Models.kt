package com.sahin.galerim

import android.net.Uri

data class MediaItem(
    val uri: Uri, 
    val isVideo: Boolean, 
    val bucketId: Long, 
    val bucketName: String, 
    var dateAdded: Long, 
    val duration: Long, 
    val size: Long, 
    val path: String
)

data class Album(
    val bucketId: Long?, 
    val locationName: String?, 
    val name: String, 
    val thumbnail: Uri, 
    val count: Int
)

sealed class GalleryItem
data class HeaderItem(val title: String, val location: String? = null) : GalleryItem()
data class MediaContentItem(val media: MediaItem) : GalleryItem()
