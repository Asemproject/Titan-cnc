package com.titancnc

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TitanCNCApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize OpenCV if needed
        // System.loadLibrary("opencv_java4")
    }
}
