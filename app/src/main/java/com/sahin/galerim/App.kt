package com.sahin.galerim

import android.app.Application
import com.sahin.galerim.service.ScreenshotDetector

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreenshotDetector.startMonitoring(this)
    }
}