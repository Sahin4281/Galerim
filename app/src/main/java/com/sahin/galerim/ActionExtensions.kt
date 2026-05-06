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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia

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
                    
                    FileInputStream(trashFile).use { input ->
                        FileOutputStream(destFile).use { output -> 
                            input.copyTo(output) 
                        }
                    }
                    
                    if (destFile.exists()) {
                        destFile.setLastModified(trashFile.lastModified())
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
                    
                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(destFile).use { output -> 
                            input.copyTo(output) 
                        }
                    }
                    
                    if (destFile.exists()) {
                        destFile.setLastModified(sourceFile.lastModified())
                        
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
    Toast.makeText(this, "İşlem başlatıldı...", Toast.LENGTH_SHORT).show()
    
    lifecycleScope.launch(Dispatchers.IO) {
        var pCount = 0
        var vCount = 0
        
        for (item in items) {
            try {
                val source = File(item.path)
                val dest = File(destFolder, source.name)
                
                if (source.exists() && source.absolutePath != dest.absolutePath) {
                    source.copyTo(dest, overwrite = true)
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
            
            Toast.makeText(this@processCopyMove, msg, Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            loadAllMedia()
        }
    }
}

fun MainActivity.saveNewDateToItems(items: List<MediaItem>, cal: Calendar) {
    Toast.makeText(this, "Tarih güncelleniyor...", Toast.LENGTH_SHORT).show()
    
    lifecycleScope.launch(Dispatchers.IO) {
        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val dateStr = format.format(cal.time)
        val timeInMillis = cal.timeInMillis
        val timeInSeconds = timeInMillis / 1000L
        val pathsToScan = mutableListOf<String>()

        for (item in items) {
            item.dateAdded = timeInSeconds 
            
            try {
                if (!item.isVideo) {
                    val ext = File(item.path).extension.lowercase(Locale("tr"))
                    if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                        val exif = ExifInterface(item.path)
                        exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                        exif.saveAttributes()
                    }
                }
                
                File(item.path).setLastModified(timeInMillis)
                
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, timeInSeconds)
                    put(MediaStore.MediaColumns.DATE_ADDED, timeInSeconds)
                    if (!item.isVideo) {
                        put(MediaStore.Images.Media.DATE_TAKEN, timeInMillis)
                    } else {
                        put(MediaStore.Video.Media.DATE_TAKEN, timeInMillis)
                    }
                }
                
                contentResolver.update(item.uri, values, null, null)
                pathsToScan.add(item.path)
                
            } catch(e: Exception) {
            }
        }
        
        withContext(Dispatchers.Main) {
            if (pathsToScan.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(this@saveNewDateToItems, pathsToScan.toTypedArray(), null, null)
            }
            
            val prefs = getSharedPreferences("GalleryPrefs", android.content.Context.MODE_PRIVATE)
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
            
            Toast.makeText(this@saveNewDateToItems, "Tarih başarıyla güncellendi", Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            loadDisplayedList()
        }
    }
}

fun MainActivity.clearLocationData(items: List<MediaItem>) {
    Toast.makeText(this, "Konum temizleniyor...", Toast.LENGTH_SHORT).show()
    
    lifecycleScope.launch(Dispatchers.IO) {
        var successCount = 0

        for (item in items) {
            var isCleaned = false
            
            // 1. Fiziksel olarak desteklenenlerin (JPG, PNG vb.) Exif verisini temizle
            if (!item.isVideo) {
                val ext = File(item.path).extension.lowercase(Locale("tr"))
                if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                    try {
                        val exif = ExifInterface(item.path)
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                        exif.saveAttributes()
                        isCleaned = true
                    } catch(e: Exception) {}
                }
            }

            // 2. Videolar ve diğer formatlar için MediaStore veritabanı temizliği
            try {
                val values = ContentValues().apply {
                    putNull("latitude")
                    putNull("longitude")
                }
                contentResolver.update(item.uri, values, null, null)
                isCleaned = true
            } catch (e: Exception) {}

            // 3. Uygulamanın kendi önbelleğinden (cache) konumu sil ki klasörlerden hemen düşsün
            if (isCleaned || item.isVideo) {
                MainActivity.itemLocationCache.remove(item.path)
                MainActivity.geocodeCache.remove(item.path)
                successCount++
            }
        }
        
        withContext(Dispatchers.Main) {
            val msg = if (successCount > 0) "$successCount dosyanın konum verileri temizlendi" else "İşlem başarısız oldu"
            showCustomToast(this@clearLocationData, msg, android.R.drawable.ic_menu_info_details)
            exitSelectionMode()
            loadAllMedia()
        }
    }
}

fun MainActivity.updateLocationData(items: List<MediaItem>, lat: Double, lng: Double) {
    Toast.makeText(this, "Konum güncelleniyor...", Toast.LENGTH_SHORT).show()
    
    lifecycleScope.launch(Dispatchers.IO) {
        val latStr = convertDecimalToDMS(lat)
        val lngStr = convertDecimalToDMS(lng)
        val latRef = if (lat >= 0) "N" else "S"
        val lngRef = if (lng >= 0) "E" else "W"

        var successCount = 0

        for (item in items) {
            var isUpdated = false

            // 1. Fiziksel olarak desteklenenlere Exif verisini yaz
            if (!item.isVideo) {
                val ext = File(item.path).extension.lowercase(Locale("tr"))
                if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                    try {
                        val exif = ExifInterface(item.path)
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latStr)
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lngStr)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)
                        exif.saveAttributes()
                        isUpdated = true
                    } catch(e: Exception) {}
                }
            }

            // 2. Sistem veritabanında (MediaStore) videonun konumunu güncelle
            try {
                val values = ContentValues().apply {
                    put("latitude", lat)
                    put("longitude", lng)
                }
                contentResolver.update(item.uri, values, null, null)
                isUpdated = true
            } catch(e: Exception) {}

            // 3. Videoların anında "Yerler" ve "Konumlar" sekmelerinde görünmesi için önbelleğe yaz
            if (isUpdated || item.isVideo) {
                MainActivity.itemLocationCache[item.path] = "$lat,$lng"
                successCount++
            }
        }
        
        withContext(Dispatchers.Main) {
            val msg = if (successCount > 0) "$successCount dosyanın konumu güncellendi" else "Konum güncellenemedi"
            showCustomToast(this@updateLocationData, msg, android.R.drawable.ic_menu_info_details)
            exitSelectionMode()
            loadAllMedia()
        }
    }
}

fun MainActivity.performHideMedia(items: List<MediaItem>) {
    showCustomToast(this, "Dosyalar gizleniyor...", android.R.drawable.ic_menu_info_details)
    
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
                    val originalDate = item.dateAdded * 1000L
                    val destFile = File(hiddenFolder, "${System.currentTimeMillis()}_${sourceFile.name}")
                    
                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    if (destFile.exists()) {
                        destFile.setLastModified(originalDate)
                        
                        val hiddenEntry = HiddenMedia(
                            originalPath = sourceFile.absolutePath,
                            hiddenPath = destFile.absolutePath,
                            isVideo = item.isVideo,
                            dateAdded = System.currentTimeMillis(),
                            originalDate = originalDate
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
                showCustomToast(this@performHideMedia, msg, android.R.drawable.ic_secure)
                exitSelectionMode()
                MainActivity.forceReload = true
                loadAllMedia()
            } else {
                showCustomToast(this@performHideMedia, "Gizleme başarısız oldu", android.R.drawable.ic_menu_info_details)
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

        for (item in MainActivity.mediaList.toList()) {
            var targetMillis: Long? = null
            val file = File(item.path)

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
                } catch (e: Exception) {
                }
            }

            if (targetMillis == null && !item.isVideo) {
                try {
                    val exif = ExifInterface(item.path)
                    val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    
                    if (dt != null) {
                        val parsed = format.parse(dt)
                        if (parsed != null && parsed.time < todayStartMillis) {
                            targetMillis = parsed.time
                        }
                    }
                } catch (e: Exception) {
                }
            }

            if (targetMillis != null) {
                val currentSecs = item.dateAdded
                val targetSecs = targetMillis / 1000L
                
                if (Math.abs(currentSecs - targetSecs) > 60) {
                    try {
                        if (!item.isVideo) {
                            val ext = File(item.path).extension.lowercase(Locale("tr"))
                            if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                                val exif = ExifInterface(item.path)
                                val dateStr = format.format(Date(targetMillis))
                                exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                                exif.saveAttributes()
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
                        
                    } catch (e: Exception) {
                    }
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
