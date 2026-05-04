package com.sahin.galerim.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.UrlEntity
import kotlinx.coroutines.*
import java.io.File

object ScreenshotDetector {

    private var lastCapturedUrl: String? = null
    private var urlExpiryTime: Long = 0L
    private var contentObserver: ContentObserver? = null
    private val detectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var context: Context? = null

    fun notifyUrlCaptured(url: String) {
        lastCapturedUrl = url
        urlExpiryTime = System.currentTimeMillis() + 30000 // 30 saniye geçerli
        Log.d("ScreenshotDetector", "URL alındı, 30 saniye süreyle hazır: $url")
    }

    fun startMonitoring(ctx: Context) {
        context = ctx.applicationContext
        startMediaObserver()
    }

    private fun startMediaObserver() {
        val contentResolver = context?.contentResolver ?: return
        val handler = Handler(Looper.getMainLooper())

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    if (it.toString().contains("images/media")) {
                        detectorScope.launch {
                            handleNewMedia(it)
                        }
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver!!
        )
        Log.d("ScreenshotDetector", "MediaStore gözlemcisi başlatıldı")
    }

    private suspend fun handleNewMedia(uri: Uri) {
        val resolver = context?.contentResolver ?: return
        
        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED),
            null, null, null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))

                val isScreenshot = displayName?.contains("Screenshot", ignoreCase = true) == true ||
                        displayName?.contains("Ekran", ignoreCase = true) == true

                if (isScreenshot && !path.isNullOrEmpty()) {
                    Log.d("ScreenshotDetector", "Ekran görüntüsü algılandı: $path")
                    
                    // Dosyanın tam yazılması için 1 saniye bekle
                    delay(1000)
                    
                    withContext(Dispatchers.Main) {
                        val isUrlValid = lastCapturedUrl != null && System.currentTimeMillis() < urlExpiryTime
                        
                        if (isUrlValid) {
                            lastCapturedUrl?.let { url ->
                                detectorScope.launch {
                                    val db = AppDatabase.getDatabase(context!!)
                                    db.urlDao().insertUrl(UrlEntity(path, url))
                                    Log.d("ScreenshotDetector", "KAYDEDİLDİ: $path -> $url")
                                    showNotification("✅ URL Kaydedildi", "Ekran görüntüsü eşleşti!")
                                    lastCapturedUrl = null
                                }
                            }
                        } else {
                            val reason = if (lastCapturedUrl == null) "URL yok" else "Süre doldu (${(System.currentTimeMillis() - urlExpiryTime)/1000} sn geç)"
                            Log.d("ScreenshotDetector", "Eşleşme başarısız: $reason")
                            showNotification("⚠️ URL Kaydedilemedi", reason)
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(title: String, content: String) {
        context?.let { ctx ->
            val builder = NotificationCompat.Builder(ctx, "url_capture_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)

            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "url_capture_channel",
                    "URL Yakalama Bildirimleri",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
            manager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        }
    }

    fun stopMonitoring() {
        contentObserver?.let {
            context?.contentResolver?.unregisterContentObserver(it)
        }
        contentObserver = null
        detectorScope.cancel()
        context = null
    }
}