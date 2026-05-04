package com.sahin.galerim.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaRestoreHelper {

    fun restoreWithOriginalDate(
        context: Context,
        hiddenFile: File,
        originalPath: String,
        originalDateMillis: Long
    ): Boolean {
        return try {
            val destFile = File(originalPath)
            destFile.parentFile?.mkdirs()

            FileInputStream(hiddenFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists()) {
                // 1. Fiziksel tarihi mühürle
                destFile.setLastModified(originalDateMillis)

                // 2. EXIF güncelle (JPG ise)
                val lowerPath = destFile.absolutePath.lowercase(Locale.US)
                if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
                    try {
                        val exif = android.media.ExifInterface(destFile.absolutePath)
                        val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(originalDateMillis))
                        exif.setAttribute(android.media.ExifInterface.TAG_DATETIME, dateStr)
                        exif.setAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                        exif.saveAttributes()
                    } catch (e: Exception) {}
                }

                // 3. Sistemi ZORLA haberdar et ve eski tarihi mühürle
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null) { _, uri ->
                    if (uri != null) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DATE_ADDED, originalDateMillis / 1000L)
                            put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateMillis / 1000L)
                            put(MediaStore.Images.Media.DATE_TAKEN, originalDateMillis)
                        }
                        context.contentResolver.update(uri, values, null, null)
                        
                        // Samsung ve diğer galerilerin önbelleğini tazelet
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                    }
                }
                
                hiddenFile.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
