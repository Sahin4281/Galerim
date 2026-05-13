package com.sahin.galerim

import android.content.ContentValues
import android.media.ExifInterface
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
import android.os.Build
import com.coremedia.iso.IsoFile
import com.coremedia.iso.boxes.TrackBox

fun MainActivity.performTrashRestore(items: List<MediaItem>) {
    if (items.isEmpty()) return

    var restoredPhotoCount = 0
    var restoredVideoCount = 0
    
    items.forEach { item ->
        try {
            if (item.path.contains(".galerim_trash")) {
                val trashFile = File(item.path)
                val origPath = MainActivity.trashedOriginalPaths[item.path]
                
                if (trashFile.exists() && origPath != null) {
                    val destFile = File(origPath)
                    destFile.parentFile?.mkdirs()
                    
                    val originalLastModified = trashFile.lastModified()
                    trashFile.copyTo(destFile, overwrite = true)
                    
                    if (destFile.exists()) {
                        destFile.setLastModified(originalLastModified)
                        android.media.MediaScannerConnection.scanFile(this, arrayOf(destFile.absolutePath), null, null)
                        
                        trashFile.delete()
                        MainActivity.trashedPaths.remove(item.path)
                        MainActivity.trashedOriginalPaths.remove(item.path)
                        MainActivity.trashedTimestamps.remove(item.path)
                        MainActivity.trashedIsVideo.remove(item.path)
                        MainActivity.trashedDurations.remove(item.path)
                        MainActivity.trashedSizes.remove(item.path)
                        
                        if (item.isVideo) {
                            restoredVideoCount++ 
                        } else {
                            restoredPhotoCount++
                        }
                    }
                }
            } else {
                MainActivity.trashedPaths.remove(item.path)
                MainActivity.trashedTimestamps.remove(item.path)
                if (item.isVideo) {
                    restoredVideoCount++ 
                } else {
                    restoredPhotoCount++
                }
            }
        } catch (e: Exception) {
        }
    }
    
    MainActivity.saveTrashedPaths(this)
    
    val messageText = when {
        restoredPhotoCount > 0 && restoredVideoCount > 0 -> "$restoredPhotoCount fotoğraf ve $restoredVideoCount video geri yüklendi"
        restoredPhotoCount > 0 -> "$restoredPhotoCount fotoğraf geri yüklendi"
        restoredVideoCount > 0 -> "$restoredVideoCount video geri yüklendi"
        else -> return
    }
    
    showCustomToast(this, messageText, R.drawable.ic_undo)
    exitSelectionMode()
    loadAllMedia()
}

fun MainActivity.performMultiDelete(items: List<MediaItem>, useTrash: Boolean) {
    var deletedPhotoCount = 0
    var deletedVideoCount = 0
    
    val trashFolder = File(filesDir, ".galerim_trash")
    if (!trashFolder.exists()) {
        trashFolder.mkdirs()
    }

    items.forEach { item ->
        try {
            if (useTrash) {
                val sourceFile = File(item.path)
                if (sourceFile.exists()) {
                    val destFile = File(trashFolder, "${System.currentTimeMillis()}_${sourceFile.name}")
                    
                    val originalLastModified = sourceFile.lastModified()
                    sourceFile.copyTo(destFile, overwrite = true)
                    
                    if (destFile.exists()) {
                        destFile.setLastModified(originalLastModified)
                        
                        MainActivity.trashedPaths.add(destFile.absolutePath)
                        MainActivity.trashedOriginalPaths[destFile.absolutePath] = sourceFile.absolutePath
                        MainActivity.trashedTimestamps[destFile.absolutePath] = System.currentTimeMillis()
                        MainActivity.trashedIsVideo[destFile.absolutePath] = item.isVideo
                        MainActivity.trashedDurations[destFile.absolutePath] = item.duration
                        MainActivity.trashedSizes[destFile.absolutePath] = item.size
                        
                        sourceFile.delete()
                        contentResolver.delete(item.uri, null, null)
                        
                        if (item.isVideo) {
                            deletedVideoCount++ 
                        } else {
                            deletedPhotoCount++
                        }
                    }
                }
            } else {
                val file = File(item.path)
                if (file.exists() && file.delete()) {
                    if (item.uri.scheme != "file") {
                        contentResolver.delete(item.uri, null, null)
                    }
                    MainActivity.trashedPaths.remove(item.path)
                    MainActivity.trashedOriginalPaths.remove(item.path)
                    MainActivity.trashedTimestamps.remove(item.path)
                    MainActivity.trashedIsVideo.remove(item.path)
                    MainActivity.trashedDurations.remove(item.path)
                    MainActivity.trashedSizes.remove(item.path)
                    
                    if (item.isVideo) {
                        deletedVideoCount++ 
                    } else {
                        deletedPhotoCount++
                    }
                } else {
                    if (item.uri.scheme != "file") {
                        val rows = contentResolver.delete(item.uri, null, null)
                        if (rows > 0) {
                            MainActivity.trashedPaths.remove(item.path)
                            MainActivity.trashedOriginalPaths.remove(item.path)
                            MainActivity.trashedTimestamps.remove(item.path)
                            MainActivity.trashedIsVideo.remove(item.path)
                            MainActivity.trashedDurations.remove(item.path)
                            MainActivity.trashedSizes.remove(item.path)
                            
                            if (item.isVideo) {
                                deletedVideoCount++ 
                            } else {
                                deletedPhotoCount++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }
    
    MainActivity.saveTrashedPaths(this)
    
    val actionText = if (useTrash) "çöpe taşındı" else "kalıcı olarak silindi"
    val messageText = when {
        deletedPhotoCount > 0 && deletedVideoCount > 0 -> "$deletedPhotoCount fotoğraf ve $deletedVideoCount video $actionText"
        deletedPhotoCount > 0 -> "$deletedPhotoCount fotoğraf $actionText"
        deletedVideoCount > 0 -> "$deletedVideoCount video $actionText"
        else -> return
    }
    
    showCustomToast(this, messageText, R.drawable.ic_action_delete)
    exitSelectionMode()
    loadAllMedia()
}

fun MainActivity.processCopyMove(action: String, items: List<MediaItem>, destFolder: File) {
    showCustomToast(this, "İşlem başlatıldı...", 0)
    
    lifecycleScope.launch(Dispatchers.IO) {
        var pCount = 0
        var vCount = 0
        
        for (item in items) {
            try {
                val source = File(item.path)
                val dest = File(destFolder, source.name)
                
                if (source.exists() && source.absolutePath != dest.absolutePath) {
                    val originalLastModified = source.lastModified()
                    source.copyTo(dest, overwrite = true)
                    dest.setLastModified(originalLastModified)
                    
                    android.media.MediaScannerConnection.scanFile(this@processCopyMove, arrayOf(dest.absolutePath), null, null)
                    
                    if (action == "MOVE") {
                        source.delete()
                        contentResolver.delete(item.uri, null, null)
                    }
                    
                    if (item.isVideo) {
                        vCount++ 
                    } else {
                        pCount++
                    }
                }
            } catch (e: Exception) {
            }
        }
        
        withContext(Dispatchers.Main) {
            val actionText = if (action == "COPY") "kopyalandı" else "taşındı"
            val msg = when {
                pCount > 0 && vCount > 0 -> "$pCount fotoğraf ve $vCount video $actionText"
                pCount > 0 -> "$pCount fotoğraf $actionText"
                vCount > 0 -> "$vCount video $actionText"
                else -> ""
            }
            
            showCustomToast(this@processCopyMove, msg, 0)
            exitSelectionMode()
            loadAllMedia()
        }
    }
}

fun MainActivity.saveNewDateToItems(items: List<MediaItem>, cal: Calendar) {
    showCustomToast(this, "Tarih güncelleniyor...", 0)
    
    lifecycleScope.launch(Dispatchers.IO) {
        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val dateStr = format.format(cal.time)
        val timeInMillis = cal.timeInMillis
        val timeInSeconds = timeInMillis / 1000L
        
        val prefs = getSharedPreferences("GalleryPrefs", android.content.Context.MODE_PRIVATE)
        val editedSet = prefs.getStringSet("manually_edited_media", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        for (item in items) {
            editedSet.add(item.path)
            item.dateAdded = timeInSeconds 
            
            try {
                if (!item.isVideo) {
                    val ext = File(item.path).extension.lowercase(Locale("tr"))
                    if (listOf("jpg", "jpeg", "png", "webp", "tif", "tiff", "gif").contains(ext)) {
                        if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                            try {
                                val exif = ExifInterface(item.path)
                                exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                                exif.saveAttributes()
                            } catch(e: Exception) {}
                        }
                    }
                } else {
                    val sourceFile = File(item.path)
                    val tempFile = File(sourceFile.parent, "temp_date_${System.currentTimeMillis()}_${sourceFile.name}")
                    val success = modifyVideoDateWithMp4Parser(sourceFile, tempFile, timeInMillis)
                    if (success) {
                        tempFile.copyTo(sourceFile, overwrite = true)
                        tempFile.delete()
                    } else {
                        tempFile.delete()
                    }
                }
                
                File(item.path).setLastModified(timeInMillis)
                
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, timeInSeconds)
                    put(MediaStore.MediaColumns.DATE_ADDED, timeInSeconds)
                    if (!item.isVideo) {
                        put(MediaStore.Images.Media.DATE_TAKEN, timeInMillis)
                    } else {
                        put("datetaken", timeInMillis)
                    }
                }
                
                try {
                    contentResolver.update(item.uri, values, null, null)
                } catch(e: Exception) {}
                
                android.media.MediaScannerConnection.scanFile(this@saveNewDateToItems, arrayOf(item.path), null) { _, uriToUpdate ->
                    val finalUri = uriToUpdate ?: item.uri
                    Thread {
                        android.os.SystemClock.sleep(1000)
                        try {
                            contentResolver.update(finalUri, values, null, null)
                        } catch(e: Exception) {
                            try { contentResolver.update(item.uri, values, null, null) } catch (e2: Exception) {}
                        }
                        android.os.SystemClock.sleep(2000)
                        try { contentResolver.update(finalUri, values, null, null) } catch(e: Exception) {}
                    }.start()
                }
                
            } catch(e: Exception) {}
        }
        
        prefs.edit().putStringSet("manually_edited_media", editedSet).apply()
        
        withContext(Dispatchers.Main) {
            val sortOrder = prefs.getString("sort_order", "Değiştirilme (önce yeni)")
            
            when (sortOrder) {
                "Dosya adı (A - Z)" -> MainActivity.mediaList.sortBy { File(it.path).name.lowercase(Locale("tr")) }
                "Dosya adı (Z - A)" -> MainActivity.mediaList.sortByDescending { File(it.path).name.lowercase(Locale("tr")) }
                "Değiştirilme (önce yeni)" -> MainActivity.mediaList.sortByDescending { it.dateAdded }
                "Değiştirilme (önce eski)" -> MainActivity.mediaList.sortBy { it.dateAdded }
                "Tür (A - Z)" -> MainActivity.mediaList.sortBy { File(it.path).extension.lowercase(Locale("tr")) }
                "Tür (Z - A)" -> MainActivity.mediaList.sortByDescending { File(it.path).extension.lowercase(Locale("tr")) }
                "Boyut (önce en büyük)" -> MainActivity.mediaList.sortByDescending { it.size }
                "Boyut (önce en küçük)" -> MainActivity.mediaList.sortBy { it.size }
            }
            
            showCustomToast(this@saveNewDateToItems, "Tarih başarıyla güncellendi", 0)
            exitSelectionMode()
            loadDisplayedList()
        }
    }
}

fun MainActivity.clearLocationData(items: List<MediaItem>) {
    showCustomToast(this, "Konum temizleniyor", 0)
    
    lifecycleScope.launch(Dispatchers.IO) {
        var successPhotoCount = 0
        var successVideoCount = 0

        for (item in items) {
            var isCleaned = false
            val originalLastModified = File(item.path).lastModified()
            val originalDateAdded = item.dateAdded

            var realDateAdded = originalDateAdded
            var realDateTaken = originalLastModified
            try {
                contentResolver.query(item.uri, arrayOf("date_added", "datetaken"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val daIdx = cursor.getColumnIndex("date_added")
                        val dtIdx = cursor.getColumnIndex("datetaken")
                        if (daIdx >= 0 && !cursor.isNull(daIdx)) realDateAdded = cursor.getLong(daIdx)
                        if (dtIdx >= 0 && !cursor.isNull(dtIdx)) realDateTaken = cursor.getLong(dtIdx)
                    }
                }
            } catch(e: Exception){}

            if (!item.isVideo) {
                val ext = File(item.path).extension.lowercase(Locale("tr"))
                if (listOf("jpg", "jpeg", "png", "webp", "tif", "tiff", "gif").contains(ext)) {
                    try {
                        if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                            try {
                                val exif = ExifInterface(item.path)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
                                exif.saveAttributes()
                            } catch(e: Exception) {}
                        }
                        
                        File(item.path).setLastModified(originalLastModified)
                        android.media.MediaScannerConnection.scanFile(this@clearLocationData, arrayOf(item.path), null) { _, uriToUpdate ->
                            val finalUri = uriToUpdate ?: item.uri
                            try {
                                File(item.path).setLastModified(originalLastModified)
                                val values = ContentValues().apply {
                                    putNull("latitude")
                                    putNull("longitude")
                                    put(MediaStore.MediaColumns.DATE_ADDED, realDateAdded)
                                    put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateAdded)
                                    put(MediaStore.Images.Media.DATE_TAKEN, realDateTaken)
                                }
                                contentResolver.update(finalUri, values, null, null)
                            } catch (e: Exception) {}
                        }
                        isCleaned = true
                    } catch(e: Exception) {}
                }
            } else {
                try {
                    val sourceFile = File(item.path)
                    val tempFile = File(sourceFile.parent, "temp_loc_clear_${System.currentTimeMillis()}_${sourceFile.name}")
                    
                    val success = modifyVideoLocationWithMuxer(sourceFile, tempFile, null, null)
                    
                    if (success) {
                        tempFile.copyTo(sourceFile, overwrite = true)
                        sourceFile.setLastModified(originalLastModified)
                        tempFile.delete()
                        android.media.MediaScannerConnection.scanFile(this@clearLocationData, arrayOf(sourceFile.absolutePath), null) { _, uriToUpdate ->
                            val finalUri = uriToUpdate ?: item.uri
                            try {
                                sourceFile.setLastModified(originalLastModified)
                                val values = ContentValues().apply {
                                    putNull("latitude")
                                    putNull("longitude")
                                    put(MediaStore.MediaColumns.DATE_ADDED, realDateAdded)
                                    put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateAdded)
                                    put(MediaStore.Video.Media.DATE_TAKEN, realDateTaken)
                                }
                                contentResolver.update(finalUri, values, null, null)
                            } catch (e: Exception) {}
                        }
                        isCleaned = true
                    } else {
                        tempFile.delete()
                    }
                } catch (e: Exception) {}
            }

            if (isCleaned) {
                MainActivity.itemLocationCache.remove(item.path)
                MainActivity.geocodeCache.remove(item.path)
                if (item.isVideo) successVideoCount++ else successPhotoCount++
            }
        }
        
        withContext(Dispatchers.Main) {
            val total = successPhotoCount + successVideoCount
            val msg = if (total == 0) {
                "Konum temizlenemedi"
            } else if (successPhotoCount == 0 && successVideoCount > 0) {
                "$successVideoCount videonun konumu temizlendi"
            } else {
                "$total dosyanın konumu temizlendi"
            }
            showCustomToast(this@clearLocationData, msg, 0)
            exitSelectionMode()
            loadAllMedia()
        }
    }
}

fun MainActivity.updateLocationData(items: List<MediaItem>, lat: Double, lng: Double) {
    lifecycleScope.launch(Dispatchers.IO) {
        var isUpdating = false
        val firstItem = items.firstOrNull()
        if (firstItem != null) {
            val cachedLoc = MainActivity.itemLocationCache[firstItem.path]
            if (!cachedLoc.isNullOrEmpty()) {
                isUpdating = true
            } else {
                try {
                    contentResolver.query(firstItem.uri, arrayOf("latitude", "longitude"), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val latIdx = cursor.getColumnIndex("latitude")
                            val lngIdx = cursor.getColumnIndex("longitude")
                            if (latIdx >= 0 && lngIdx >= 0 && !cursor.isNull(latIdx) && !cursor.isNull(lngIdx)) {
                                val cLat = cursor.getDouble(latIdx)
                                val cLng = cursor.getDouble(lngIdx)
                                if (cLat != 0.0 || cLng != 0.0) {
                                    isUpdating = true
                                }
                            }
                        }
                    }
                } catch(e: Exception){}
                if (!isUpdating && !firstItem.isVideo) {
                    val ext = File(firstItem.path).extension.lowercase(Locale("tr"))
                    if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                        try {
                            val exif = ExifInterface(firstItem.path)
                            val latLong = FloatArray(2)
                            if (exif.getLatLong(latLong)) {
                                isUpdating = true
                            }
                        } catch(e: Exception){}
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            showCustomToast(this@updateLocationData, if (isUpdating) "Konum güncelleniyor" else "Konum ekleniyor", 0)
        }

        val latStr = convertDecimalToDMS(lat)
        val lngStr = convertDecimalToDMS(lng)
        val latRef = if (lat >= 0) "N" else "S"
        val lngRef = if (lng >= 0) "E" else "W"

        var addedPhotoCount = 0
        var updatedPhotoCount = 0
        var addedVideoCount = 0
        var updatedVideoCount = 0

        for (item in items) {
            var isUpdatedLocally = false
            val originalLastModified = File(item.path).lastModified()
            val originalDateAdded = item.dateAdded
            
            var hadLoc = false
            val cLoc = MainActivity.itemLocationCache[item.path]
            if (!cLoc.isNullOrEmpty()) {
                hadLoc = true
            } else {
                try {
                    contentResolver.query(item.uri, arrayOf("latitude", "longitude"), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val latIdx = cursor.getColumnIndex("latitude")
                            val lngIdx = cursor.getColumnIndex("longitude")
                            if (latIdx >= 0 && lngIdx >= 0 && !cursor.isNull(latIdx) && !cursor.isNull(lngIdx)) {
                                val cLat = cursor.getDouble(latIdx)
                                val cLng = cursor.getDouble(lngIdx)
                                if (cLat != 0.0 || cLng != 0.0) {
                                    hadLoc = true
                                }
                            }
                        }
                    }
                } catch(e: Exception){}
                if (!hadLoc && !item.isVideo) {
                    val ext = File(item.path).extension.lowercase(Locale("tr"))
                    if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                        try {
                            val exif = ExifInterface(item.path)
                            val latLong = FloatArray(2)
                            if (exif.getLatLong(latLong)) {
                                hadLoc = true
                            }
                        } catch(e: Exception){}
                    }
                }
            }

            var realDateAdded = originalDateAdded
            var realDateTaken = originalLastModified
            try {
                contentResolver.query(item.uri, arrayOf("date_added", "datetaken"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val daIdx = cursor.getColumnIndex("date_added")
                        val dtIdx = cursor.getColumnIndex("datetaken")
                        if (daIdx >= 0 && !cursor.isNull(daIdx)) realDateAdded = cursor.getLong(daIdx)
                        if (dtIdx >= 0 && !cursor.isNull(dtIdx)) realDateTaken = cursor.getLong(dtIdx)
                    }
                }
            } catch(e: Exception){}

            if (!item.isVideo) {
                val ext = File(item.path).extension.lowercase(Locale("tr"))
                if (listOf("jpg", "jpeg", "png", "webp", "tif", "tiff", "gif").contains(ext)) {
                    try {
                        if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                            try {
                                val exif = ExifInterface(item.path)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latStr)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lngStr)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)
                                exif.saveAttributes()
                            } catch(e: Exception) {}
                        }
                        
                        File(item.path).setLastModified(originalLastModified)
                        android.media.MediaScannerConnection.scanFile(this@updateLocationData, arrayOf(item.path), null) { _, uriToUpdate ->
                            val finalUri = uriToUpdate ?: item.uri
                            try {
                                File(item.path).setLastModified(originalLastModified)
                                val values = ContentValues().apply {
                                    put("latitude", lat)
                                    put("longitude", lng)
                                    put(MediaStore.MediaColumns.DATE_ADDED, realDateAdded)
                                    put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateAdded)
                                    put(MediaStore.Images.Media.DATE_TAKEN, realDateTaken)
                                }
                                contentResolver.update(finalUri, values, null, null)
                            } catch(e: Exception) {}
                        }
                        isUpdatedLocally = true
                    } catch(e: Exception) {}
                }
            } else {
                try {
                    val sourceFile = File(item.path)
                    val tempFile = File(sourceFile.parent, "temp_loc_${System.currentTimeMillis()}_${sourceFile.name}")
                    
                    val success = modifyVideoLocationWithMuxer(sourceFile, tempFile, lat, lng)
                    
                    if (success) {
                        tempFile.copyTo(sourceFile, overwrite = true)
                        sourceFile.setLastModified(originalLastModified)
                        tempFile.delete()
                        android.media.MediaScannerConnection.scanFile(this@updateLocationData, arrayOf(sourceFile.absolutePath), null) { _, uriToUpdate ->
                            val finalUri = uriToUpdate ?: item.uri
                            try {
                                sourceFile.setLastModified(originalLastModified)
                                val values = ContentValues().apply {
                                    put("latitude", lat)
                                    put("longitude", lng)
                                    put(MediaStore.MediaColumns.DATE_ADDED, realDateAdded)
                                    put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateAdded)
                                    put(MediaStore.Video.Media.DATE_TAKEN, realDateTaken)
                                }
                                contentResolver.update(finalUri, values, null, null)
                            } catch(e: Exception) {}
                        }
                        isUpdatedLocally = true
                    } else {
                        tempFile.delete()
                    }
                } catch(e: Exception) {}
            }

            if (isUpdatedLocally) {
                MainActivity.itemLocationCache[item.path] = "$lat,$lng"
                MainActivity.geocodeCache.remove(item.path)
                if (item.isVideo) {
                    if (hadLoc) updatedVideoCount++ else addedVideoCount++
                } else {
                    if (hadLoc) updatedPhotoCount++ else addedPhotoCount++
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            val addedTotal = addedPhotoCount + addedVideoCount
            val updatedTotal = updatedPhotoCount + updatedVideoCount
            val totalSuccess = addedTotal + updatedTotal

            val msg = if (totalSuccess == 0) {
                if (isUpdating) "Konum güncellenemedi" else "Konum eklenemedi"
            } else if (addedTotal > 0 && updatedTotal == 0) {
                if (addedPhotoCount == 0 && addedVideoCount > 0) {
                    "$addedVideoCount videoya konum eklendi"
                } else {
                    "$totalSuccess dosyaya konum eklendi"
                }
            } else if (updatedTotal > 0 && addedTotal == 0) {
                if (updatedPhotoCount == 0 && updatedVideoCount > 0) {
                    "$updatedVideoCount videonun konumu güncellendi"
                } else {
                    "$totalSuccess dosyanın konumu güncellendi"
                }
            } else {
                if (updatedPhotoCount == 0 && addedPhotoCount == 0) {
                    "$totalSuccess videonun konumu güncellendi"
                } else {
                    "$totalSuccess dosyanın konumu güncellendi"
                }
            }
            showCustomToast(this@updateLocationData, msg, 0)
            exitSelectionMode()
            loadAllMedia()
        }
    }
}

fun MainActivity.performHideMedia(items: List<MediaItem>) {
    showCustomToast(this, "Dosyalar gizleniyor...", 0)
    
    lifecycleScope.launch(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(this@performHideMedia)
        val hiddenFolder = File(filesDir, "hidden_vault")
        if (!hiddenFolder.exists()) hiddenFolder.mkdirs()

        var photoCount = 0
        var videoCount = 0

        for (item in items) {
            try {
                val sourceFile = File(item.path)
                if (sourceFile.exists()) {
                    val originalDate = sourceFile.lastModified()
                    val originalDateSec = item.dateAdded * 1000L
                    val destFile = File(hiddenFolder, "${System.currentTimeMillis()}_${sourceFile.name}")
                    
                    sourceFile.copyTo(destFile, overwrite = true)
                    
                    if (destFile.exists()) {
                        destFile.setLastModified(originalDate)
                        
                        val hiddenEntry = HiddenMedia(
                            originalPath = sourceFile.absolutePath,
                            hiddenPath = destFile.absolutePath,
                            isVideo = item.isVideo,
                            dateAdded = System.currentTimeMillis(),
                            originalDate = originalDateSec
                        )
                        db.hiddenMediaDao().insert(hiddenEntry)

                        sourceFile.delete()
                        try { contentResolver.delete(item.uri, null, null) } catch (e: Exception) {}
                        
                        if (item.isVideo) videoCount++ else photoCount++
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        withContext(Dispatchers.Main) {
            val msg = when {
                photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf ve $videoCount video gizlendi"
                photoCount > 0 -> "$photoCount fotoğraf gizlendi"
                videoCount > 0 -> "$videoCount video gizlendi"
                else -> ""
            }
            
            if (msg.isNotEmpty()) {
                showCustomToast(this@performHideMedia, msg, 0)
                exitSelectionMode()
                MainActivity.forceReload = true
                loadAllMedia()
            } else {
                showCustomToast(this@performHideMedia, "Gizleme başarısız oldu", 0)
            }
        }
    }
}

fun MainActivity.repairMediaDates() {
    showCustomToast(this, "Tarihleri Onarma İşlemi Başladı...", 0)
    
    lifecycleScope.launch(Dispatchers.IO) {
        var repairedCount = 0
        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val namePattern = Pattern.compile("(?:^|[^0-9])(\\d{4})(\\d{2})(\\d{2})(?:[_-]?(\\d{2})(\\d{2})(\\d{2}))?(?:[^0-9]|$)")
        val epochPattern = Pattern.compile("(?:^|[^0-9])(1[0-9]{12})(?:[^0-9]|$)")

        val pathsToScan = mutableListOf<String>()
        val calToday = Calendar.getInstance()
        calToday.set(Calendar.HOUR_OF_DAY, 0)
        calToday.set(Calendar.MINUTE, 0)
        calToday.set(Calendar.SECOND, 0)
        val todayStartMillis = calToday.timeInMillis
        
        val prefs = getSharedPreferences("GalleryPrefs", android.content.Context.MODE_PRIVATE)
        val editedSet = prefs.getStringSet("manually_edited_media", emptySet()) ?: emptySet()

        for (item in MainActivity.mediaList.toList()) {
            if (editedSet.contains(item.path)) continue

            var targetMillis: Long? = null
            val file = File(item.path)

            if (!item.isVideo) {
                try {
                    val exif = ExifInterface(item.path)
                    val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    if (dt != null) {
                        val parsed = format.parse(dt)
                        if (parsed != null && parsed.time < todayStartMillis) {
                            targetMillis = parsed.time
                        }
                    }
                } catch (e: Exception) {}
            }

            if (targetMillis == null) {
                val epochMatcher = epochPattern.matcher(file.name)
                val stdMatcher = namePattern.matcher(file.name)

                if (epochMatcher.find()) {
                    targetMillis = epochMatcher.group(1)?.toLongOrNull()
                } else if (stdMatcher.find()) {
                    try {
                        val year = stdMatcher.group(1)?.toIntOrNull() ?: 0
                        val month = (stdMatcher.group(2)?.toIntOrNull() ?: 1) - 1
                        val day = stdMatcher.group(3)?.toIntOrNull() ?: 1
                        
                        if (year in 2000..2050 && month in 0..11 && day in 1..31) {
                            val hour = stdMatcher.group(4)?.toIntOrNull() ?: 12
                            val minute = stdMatcher.group(5)?.toIntOrNull() ?: 0
                            val second = stdMatcher.group(6)?.toIntOrNull() ?: 0
                            
                            val cal = Calendar.getInstance()
                            cal.set(year, month, day, hour, minute, second)
                            targetMillis = cal.timeInMillis
                        }
                    } catch (e: Exception) {}
                }
            }

            if (targetMillis != null) {
                val currentSecs = item.dateAdded
                val targetSecs = targetMillis / 1000L
                
                if (Math.abs(currentSecs - targetSecs) > 60) {
                    try {
                        if (!item.isVideo) {
                            val ext = File(item.path).extension.lowercase(Locale("tr"))
                            if (listOf("jpg", "jpeg", "png", "webp", "tif", "tiff", "gif").contains(ext)) {
                                if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                                    val exif = ExifInterface(item.path)
                                    val dateStr = format.format(Date(targetMillis))
                                    exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                                    exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                                    exif.saveAttributes()
                                }
                            }
                        } else {
                            val sourceFile = File(item.path)
                            val tempFile = File(sourceFile.parent, "temp_date_${System.currentTimeMillis()}_${sourceFile.name}")
                            val success = modifyVideoDateWithMp4Parser(sourceFile, tempFile, targetMillis)
                            if (success) {
                                tempFile.copyTo(sourceFile, overwrite = true)
                                tempFile.delete()
                            } else {
                                tempFile.delete()
                            }
                        }
                        
                        file.setLastModified(targetMillis)
                        
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DATE_MODIFIED, targetSecs)
                            put(MediaStore.MediaColumns.DATE_ADDED, targetSecs)
                            
                            if (!item.isVideo) {
                                put(MediaStore.Images.Media.DATE_TAKEN, targetMillis)
                            } else {
                                put(MediaStore.Video.Media.DATE_TAKEN, targetMillis)
                            }
                        }
                        
                        contentResolver.update(item.uri, values, null, null)
                        item.dateAdded = targetSecs
                        pathsToScan.add(item.path)
                        repairedCount++
                        
                    } catch (e: Exception) {}
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            if (repairedCount > 0) {
                if (pathsToScan.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(this@repairMediaDates, pathsToScan.toTypedArray(), null, null)
                }
                MainActivity.mediaList.sortByDescending { it.dateAdded }
                loadDisplayedList()
                showCustomToast(this@repairMediaDates, "$repairedCount dosya aslına döndürüldü!", 0)
            } else {
                showCustomToast(this@repairMediaDates, "Kaymış tarih bulunamadı.", 0)
            }
        }
    }
}

private fun convertDecimalToDMS(coord: Double): String {
    val absCoord = Math.abs(coord)
    val degree = absCoord.toInt()
    val minDouble = (absCoord - degree) * 60
    val minute = minDouble.toInt()
    val second = ((minDouble - minute) * 60 * 1000).toInt()
    
    return "$degree/1,$minute/1,$second/1000"
}

fun modifyVideoLocationWithMuxer(sourceFile: File, tempFile: File, lat: Double?, lng: Double?): Boolean {
    try {
        val extractor = MediaExtractor()
        extractor.setDataSource(sourceFile.absolutePath)
        
        val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        if (lat != null && lng != null) {
            muxer.setLocation(lat.toFloat(), lng.toFloat())
        }
        
        val trackCount = extractor.trackCount
        val trackMap = HashMap<Int, Int>()
        
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val muxerTrackIndex = muxer.addTrack(format)
            trackMap[i] = muxerTrackIndex
            extractor.selectTrack(i)
        }
        
        muxer.start()
        
        var maxChunkSize = 1024 * 1024
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                if (size > maxChunkSize) {
                    maxChunkSize = size
                }
            }
        }
        
        val buffer = ByteBuffer.allocate(maxChunkSize)
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        
        while (true) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break
            
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            
            bufferInfo.size = size
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            bufferInfo.offset = 0
            
            muxer.writeSampleData(trackMap[trackIndex]!!, buffer, bufferInfo)
            extractor.advance()
        }
        
        muxer.stop()
        muxer.release()
        extractor.release()
        return true
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

fun MainActivity.renameMediaFile(item: MediaItem, newNameWithExt: String) {
    showNoIconToast("Yeniden isimlendiriliyor...")
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val sourceFile = File(item.path)
            val destFile = File(sourceFile.parentFile, newNameWithExt)
            
            if (sourceFile.exists() && !destFile.exists()) {
                if (sourceFile.renameTo(destFile)) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, newNameWithExt)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                            }
                        }
                        contentResolver.update(item.uri, values, null, null)
                    } catch (e: Exception) {
                        try { contentResolver.delete(item.uri, null, null) } catch (e2: Exception) {}
                    }
                    
                    android.media.MediaScannerConnection.scanFile(this@renameMediaFile, arrayOf(destFile.absolutePath), null, null)
                    
                    withContext(Dispatchers.Main) {
                        showNoIconToast("Yeniden isimlendirildi")
                        exitSelectionMode()
                        MainActivity.forceReload = true
                        loadAllMedia()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showNoIconToast("İsim değiştirilemedi")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    showNoIconToast("Bu isimde bir dosya zaten var")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showNoIconToast("Bir hata oluştu")
            }
        }
    }
}

fun modifyVideoDateWithMp4Parser(sourceFile: File, tempFile: File, newTimeInMillis: Long): Boolean {
    try {
        val isoFile = IsoFile(sourceFile.absolutePath)
        val moov = isoFile.movieBox
        val mvhd = moov.movieHeaderBox
        val newDate = Date(newTimeInMillis)
        
        mvhd.creationTime = newDate
        mvhd.modificationTime = newDate
        
        for (track in moov.getBoxes(TrackBox::class.java)) {
            val tkhd = track.trackHeaderBox
            tkhd.creationTime = newDate
            tkhd.modificationTime = newDate
            val mdhd = track.mediaBox.mediaHeaderBox
            mdhd.creationTime = newDate
            mdhd.modificationTime = newDate
        }
        
        val fc = java.io.FileOutputStream(tempFile).channel
        isoFile.getBox(fc)
        fc.close()
        isoFile.close()
        return true
    } catch (e: Exception) {
        return false
    }
}
