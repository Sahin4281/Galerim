package com.sahin.galerim.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class UrlCaptureService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastCapturedUrl: String? = null
    private val supportedBrowsers = setOf(
        "com.android.chrome",           // Chrome Stable
        "com.chrome.beta",              // Chrome Beta
        "com.chrome.dev",               // Chrome Dev
        "org.mozilla.firefox",          // Firefox Stable
        "org.mozilla.firefox_beta",     // Firefox Beta
        "org.mozilla.fenix",            // Firefox Nightly/Rebrand
        "org.mozilla.fennec",           // Firefox ESR
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.sec.android.app.sbrowser.beta"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (!supportedBrowsers.contains(packageName)) return

        mainHandler.postDelayed({
            val url = extractUrlFromNode(rootInActiveWindow)
            if (!url.isNullOrEmpty() && url != lastCapturedUrl) {
                lastCapturedUrl = url
                showNotification("URL Yakalandı", url.take(50) + "...")
                ScreenshotDetector.notifyUrlCaptured(url)
            }
        }, 500)
    }

    private fun extractUrlFromNode(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        return findUrlInNodeHierarchy(node)
    }

    private fun findUrlInNodeHierarchy(node: AccessibilityNodeInfo): String? {
        // Chrome URL bar ID
        val chromeUrlBar = node.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        if (chromeUrlBar.isNotEmpty()) {
            val text = chromeUrlBar[0].text?.toString()
            if (!text.isNullOrEmpty()) return text
        }

        // Samsung Internet URL bar ID
        val samsungUrlBar = node.findAccessibilityNodeInfosByViewId("com.sec.android.app.sbrowser:id/url_bar")
        if (samsungUrlBar.isNotEmpty()) {
            val text = samsungUrlBar[0].text?.toString()
            if (!text.isNullOrEmpty()) return text
        }

        // Genel EditText taraması (Firefox ve diğerleri için)
        if (node.className?.toString()?.contains("EditText") == true) {
            val text = node.text?.toString()
            if (!text.isNullOrEmpty() && (text.startsWith("http") || text.contains("www.") || text.contains("."))) {
                return text
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                val result = findUrlInNodeHierarchy(it)
                if (result != null) return result
            }
        }
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "url_capture_channel",
                "URL Yakalama Bildirimleri",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, content: String) {
        val builder = NotificationCompat.Builder(this, "url_capture_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }

    override fun onInterrupt() {
        lastCapturedUrl = null
    }
}