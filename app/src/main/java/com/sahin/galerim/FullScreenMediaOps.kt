package com.sahin.galerim

import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import android.os.Build
import android.app.Dialog

fun FullScreenActivity.handleEdit() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    if (!item.isVideo) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_EDIT).apply { setDataAndType(item.uri, "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Fotoğrafı düzenle"))
    } else { showFsCustomToast("Videolar şimdilik düzenlenemez.", 0) }
}

fun FullScreenActivity.handleShare() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = if (item.isVideo) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, item.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Paylaş"))
}

fun FullScreenActivity.moveToAppTrash() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]

    lifecycleScope.launch(Dispatchers.IO) {
        var success = false
        try {
            val trashFolder = File(filesDir, ".galerim_trash")
            if (!trashFolder.exists()) {
                trashFolder.mkdirs()
            }

            val sourceFile = File(item.path)
            if (sourceFile.exists()) {
                val destFile = File(trashFolder, "${System.currentTimeMillis()}_${sourceFile.name}")

                val originalLastModified = sourceFile.lastModified()

                val moved = sourceFile.renameTo(destFile)
                if (!moved) {
                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    sourceFile.delete()
                }

                if (destFile.exists()) {
                    destFile.setLastModified(originalLastModified)

                    MainActivity.trashedPaths.add(destFile.absolutePath)
                    MainActivity.trashedOriginalPaths[destFile.absolutePath] = sourceFile.absolutePath
                    MainActivity.trashedTimestamps[destFile.absolutePath] = System.currentTimeMillis()
                    MainActivity.trashedIsVideo[destFile.absolutePath] = item.isVideo
                    MainActivity.trashedDurations[destFile.absolutePath] = item.duration
                    MainActivity.trashedSizes[destFile.absolutePath] = item.size

                    contentResolver.delete(item.uri, null, null)
                    success = true
                }
            }
        } catch (e: Exception) {
        }

        withContext(Dispatchers.Main) {
            if (success) {
                MainActivity.saveTrashedPaths(this@moveToAppTrash)
                MainActivity.mediaList.remove(item)
                MainActivity.trashList.add(item)
                MainActivity.displayedMediaList.removeAt(currentPosition)

                MainActivity.forceReload = true

                if (currentPosition >= MainActivity.displayedMediaList.size && currentPosition > 0) {
                    currentPosition--
                }

                viewPager.adapter?.notifyDataSetChanged()
                filmstripRecycler.adapter?.notifyDataSetChanged()

                val msg = if (item.isVideo) "1 video çöpe taşındı" else "1 fotoğraf çöpe taşındı"
                showFsCustomToast(msg, 0)
                if (MainActivity.displayedMediaList.isEmpty()) finish()
            } else {
                showFsCustomToast("Taşıma başarısız oldu", 0)
            }
        }
    }
}

fun FullScreenActivity.deletePermanently() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]

    lifecycleScope.launch(Dispatchers.IO) {
        var success = false
        try {
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
                success = true
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
                        success = true
                    }
                }
            }
        } catch (e: Exception) {}

        withContext(Dispatchers.Main) {
            if (success) {
                MainActivity.saveTrashedPaths(this@deletePermanently)
                MainActivity.mediaList.remove(item)
                MainActivity.trashList.remove(item)
                MainActivity.displayedMediaList.removeAt(currentPosition)

                MainActivity.forceReload = true

                if (currentPosition >= MainActivity.displayedMediaList.size && currentPosition > 0) {
                    currentPosition--
                }

                viewPager.adapter?.notifyDataSetChanged()
                filmstripRecycler.adapter?.notifyDataSetChanged()

                val msg = if (item.isVideo) "1 video kalıcı olarak silindi" else "1 fotoğraf kalıcı olarak silindi"
                showFsCustomToast(msg, 0)
                if (MainActivity.displayedMediaList.isEmpty()) finish()
            } else {
                showFsCustomToast("Silme başarısız oldu", 0)
            }
        }
    }
}

fun FullScreenActivity.performHideMedia() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]

    try {
        getViewHolder(currentPosition)?.let { holder ->
            if (holder.videoView.isPlaying) {
                holder.videoView.pause()
                holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
            }
        }
    } catch (e: Exception) {}

    showFsCustomToast("Dosya gizleniyor...", 0)

    lifecycleScope.launch(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(this@performHideMedia)
        val hiddenFolder = File(filesDir, "hidden_vault")
        if (!hiddenFolder.exists()) hiddenFolder.mkdirs()

        var success = false
        try {
            val sourceFile = File(item.path)
            if (sourceFile.exists()) {
                val originalDate = item.dateAdded * 1000L
                val destFile = File(hiddenFolder, "${System.currentTimeMillis()}_${sourceFile.name}")

                val moved = sourceFile.renameTo(destFile)
                if (!moved) {
                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    sourceFile.delete()
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

                    contentResolver.delete(item.uri, null, null)
                    success = true
                }
            }
        } catch (e: Exception) {}

        withContext(Dispatchers.Main) {
            if (success) {
                val msg = if (item.isVideo) "1 video gizlendi" else "1 fotoğraf gizlendi"
                showFsCustomToast(msg, 0)

                MainActivity.mediaList.remove(item)
                MainActivity.displayedMediaList.removeAt(currentPosition)
                MainActivity.forceReload = true

                if (currentPosition >= MainActivity.displayedMediaList.size && currentPosition > 0) {
                    currentPosition--
                }

                viewPager.adapter?.notifyDataSetChanged()
                filmstripRecycler.adapter?.notifyDataSetChanged()
                if (MainActivity.displayedMediaList.isEmpty()) finish()
            } else {
                showFsCustomToast("Gizleme başarısız oldu", 0)
            }
        }
    }
}

fun FullScreenActivity.processCopyMove(action: String, items: List<MediaItem>, destFolder: File) {
    showFsCustomToast("İşlem başlatıldı...", 0)
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

                    MediaScannerConnection.scanFile(this@processCopyMove, arrayOf(dest.absolutePath), null, null)

                    if (action == "MOVE") {
                        source.delete()
                        contentResolver.delete(item.uri, null, null)
                    }
                    if (item.isVideo) vCount++ else pCount++
                }
            } catch (e: Exception) {}
        }
        withContext(Dispatchers.Main) {
            val actionText = if (action == "COPY") "kopyalandı" else "taşındı"
            val msg = when {
                pCount > 0 && vCount > 0 -> "$pCount fotoğraf ve $vCount video $actionText"
                pCount > 0 -> "$pCount fotoğraf $actionText"
                vCount > 0 -> "$vCount video $actionText"
                else -> ""
            }
            if (msg.isNotEmpty()) showFsCustomToast(msg, 0)
            if (action == "MOVE") {
                MainActivity.forceReload = true
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing) finish()
                }, 1500)
            }
        }
    }
}

fun FullScreenActivity.renameMediaFileFs(item: MediaItem, newNameWithExt: String) {
    showFsCustomToast("Yeniden isimlendiriliyor...", 0)
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
                    
                    MediaScannerConnection.scanFile(this@renameMediaFileFs, arrayOf(destFile.absolutePath), null, null)
                    
                    withContext(Dispatchers.Main) {
                        showFsCustomToast("Yeniden isimlendirildi", 0)
                        MainActivity.forceReload = true
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isFinishing) finish()
                        }, 1500)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showFsCustomToast("İsim değiştirilemedi", 0)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    showFsCustomToast("Bu isimde bir dosya zaten var", 0)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showFsCustomToast("Bir hata oluştu", 0)
            }
        }
    }
}

fun FullScreenActivity.saveNewDate(newTime: Long) {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(java.util.Date(newTime))

    val progressDialog = createBlockingProgressDialog(this, "Tarih güncelleniyor, lütfen bekleyin...\nUygulamayı kapatmayın.")
    progressDialog.show()

    lifecycleScope.launch(Dispatchers.IO) {
        val timeInSeconds = newTime / 1000L
        
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val editedSet = prefs.getStringSet("manually_edited_media", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        editedSet.add(item.path)
        prefs.edit().putStringSet("manually_edited_media", editedSet).apply()

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
                        } catch (e: Exception) {}
                    }
                }
            } else {
                val sourceFile = File(item.path)
                val tempFile = File(sourceFile.parent, "temp_date_${System.currentTimeMillis()}_${sourceFile.name}")
                addTempFileToCleanup(this@saveNewDate, tempFile.absolutePath)
                val success = modifyVideoDateWithMp4Parser(sourceFile, tempFile, newTime)
                if (success) {
                    tempFile.copyTo(sourceFile, overwrite = true)
                }
                tempFile.delete()
                removeTempFileFromCleanup(this@saveNewDate, tempFile.absolutePath)
            }

            File(item.path).setLastModified(newTime)
            item.dateAdded = timeInSeconds

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATE_MODIFIED, timeInSeconds)
                put(MediaStore.MediaColumns.DATE_ADDED, timeInSeconds)
                if (!item.isVideo) put(MediaStore.Images.Media.DATE_TAKEN, newTime)
                else put("datetaken", newTime)
            }

            try {
                contentResolver.update(item.uri, values, null, null)
            } catch (e: Exception) {}

            MediaScannerConnection.scanFile(this@saveNewDate, arrayOf(item.path), null) { _, uriToUpdate ->
                val finalUri = uriToUpdate ?: item.uri
                Thread {
                    android.os.SystemClock.sleep(1000)
                    try {
                        contentResolver.update(finalUri, values, null, null)
                    } catch (e: Exception) {
                        try { contentResolver.update(item.uri, values, null, null) } catch (e2: Exception) {}
                    }
                    android.os.SystemClock.sleep(2000)
                    try { contentResolver.update(finalUri, values, null, null) } catch (e: Exception) {}
                }.start()
            }
            
            Handler(Looper.getMainLooper()).post {
                progressDialog.dismiss()
                showFsCustomToast("Tarih başarıyla güncellendi", 0)
                MainActivity.mediaList.sortByDescending { it.dateAdded }
                MainActivity.forceReload = true
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                showFsCustomToast("Hata: İşlem başarısız", 0)
            }
        }
    }
}

fun convertFsDecimalToDMS(coord: Double): String {
    val absCoord = Math.abs(coord)
    val degree = absCoord.toInt()
    val minDouble = (absCoord - degree) * 60
    val minute = minDouble.toInt()
    val second = ((minDouble - minute) * 60 * 1000).toInt()
    return "$degree/1,$minute/1,$second/1000"
}

fun FullScreenActivity.clearLocationData(items: List<MediaItem>) {
    val progressDialog = createBlockingProgressDialog(this, "Konum temizleniyor, lütfen bekleyin...\nUygulamayı kapatmayın.")
    progressDialog.show()
    
    lifecycleScope.launch(Dispatchers.IO) {
        val item = items.first()
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
                    MediaScannerConnection.scanFile(this@clearLocationData, arrayOf(item.path), null) { _, uriToUpdate ->
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
                } catch(e:Exception){}
            }
        } else {
            try {
                val sourceFile = File(item.path)
                val tempFile = File(sourceFile.parent, "temp_loc_clear_${System.currentTimeMillis()}_${sourceFile.name}")
                addTempFileToCleanup(this@clearLocationData, tempFile.absolutePath)

                val success = modifyVideoLocationWithMuxer(sourceFile, tempFile, null, null)

                if (success) {
                    val tempFile2 = File(sourceFile.parent, "temp_loc_date_${System.currentTimeMillis()}_${sourceFile.name}")
                    addTempFileToCleanup(this@clearLocationData, tempFile2.absolutePath)
                    
                    val dateFixed = modifyVideoDateWithMp4Parser(tempFile, tempFile2, realDateTaken)
                    if (dateFixed) {
                        tempFile2.copyTo(sourceFile, overwrite = true)
                    } else {
                        tempFile.copyTo(sourceFile, overwrite = true)
                    }
                    
                    sourceFile.setLastModified(originalLastModified)
                    tempFile.delete()
                    tempFile2.delete()
                    removeTempFileFromCleanup(this@clearLocationData, tempFile.absolutePath)
                    removeTempFileFromCleanup(this@clearLocationData, tempFile2.absolutePath)
                    
                    MediaScannerConnection.scanFile(this@clearLocationData, arrayOf(sourceFile.absolutePath), null) { _, uriToUpdate ->
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
                    removeTempFileFromCleanup(this@clearLocationData, tempFile.absolutePath)
                }
            } catch (e: Exception) {}
        }

        if (isCleaned) {
            MainActivity.itemLocationCache.remove(item.path)
            MainActivity.geocodeCache.remove(item.path)
        }

        withContext(Dispatchers.Main) {
            progressDialog.dismiss()
            if (isCleaned) {
                val typeStr = if (item.isVideo) "videonun" else "dosyanın"
                showFsCustomToast("1 $typeStr konumu temizlendi", 0)
            } else {
                showFsCustomToast("Konum temizlenemedi", 0)
            }
        }
    }
}

fun FullScreenActivity.updateLocationData(items: List<MediaItem>, lat: Double, lng: Double) {
    val progressDialog = createBlockingProgressDialog(this, "Konum işleniyor, lütfen bekleyin...\nUygulamayı kapatmayın.")
    progressDialog.show()
    
    lifecycleScope.launch(Dispatchers.IO) {
        val latStr = convertFsDecimalToDMS(lat)
        val lngStr = convertFsDecimalToDMS(lng)
        val latRef = if (lat >= 0) "N" else "S"
        val lngRef = if (lng >= 0) "E" else "W"

        val item = items.first()
        var isUpdated = false
        val originalLastModified = File(item.path).lastModified()
        val originalDateAdded = item.dateAdded

        var hadLoc = false
        val cachedLoc = MainActivity.itemLocationCache[item.path]
        if (!cachedLoc.isNullOrEmpty()) {
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
                    MediaScannerConnection.scanFile(this@updateLocationData, arrayOf(item.path), null) { _, uriToUpdate ->
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
                    isUpdated = true
                } catch(e:Exception){}
            }
        } else {
            try {
                val sourceFile = File(item.path)
                val tempFile = File(sourceFile.parent, "temp_loc_${System.currentTimeMillis()}_${sourceFile.name}")
                addTempFileToCleanup(this@updateLocationData, tempFile.absolutePath)

                val success = modifyVideoLocationWithMuxer(sourceFile, tempFile, lat, lng)

                if (success) {
                    val tempFile2 = File(sourceFile.parent, "temp_loc_date_${System.currentTimeMillis()}_${sourceFile.name}")
                    addTempFileToCleanup(this@updateLocationData, tempFile2.absolutePath)
                    
                    val dateFixed = modifyVideoDateWithMp4Parser(tempFile, tempFile2, realDateTaken)
                    if (dateFixed) {
                        tempFile2.copyTo(sourceFile, overwrite = true)
                    } else {
                        tempFile.copyTo(sourceFile, overwrite = true)
                    }
                    
                    sourceFile.setLastModified(originalLastModified)
                    tempFile.delete()
                    tempFile2.delete()
                    removeTempFileFromCleanup(this@updateLocationData, tempFile.absolutePath)
                    removeTempFileFromCleanup(this@updateLocationData, tempFile2.absolutePath)
                    
                    MediaScannerConnection.scanFile(this@updateLocationData, arrayOf(sourceFile.absolutePath), null) { _, uriToUpdate ->
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
                    isUpdated = true
                } else {
                    tempFile.delete()
                    removeTempFileFromCleanup(this@updateLocationData, tempFile.absolutePath)
                }
            } catch(e: Exception) {}
        }

        if (isUpdated) {
            MainActivity.itemLocationCache[item.path] = "$lat,$lng"
            MainActivity.geocodeCache.remove(item.path)
        }

        withContext(Dispatchers.Main) {
            progressDialog.dismiss()
            if (isUpdated) {
                val msg = if (hadLoc) {
                    if (item.isVideo) "1 videonun konumu güncellendi" else "1 dosyanın konumu güncellendi"
                } else {
                    if (item.isVideo) "1 videoya konum eklendi" else "1 dosyaya konum eklendi"
                }
                showFsCustomToast(msg, 0)
            } else {
                val failStr = if (hadLoc) "güncellenemedi" else "eklenemedi"
                showFsCustomToast("Konum $failStr", 0)
            }
        }
    }
}
