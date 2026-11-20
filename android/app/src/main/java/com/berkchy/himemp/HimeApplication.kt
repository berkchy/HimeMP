package com.berkchy.himemp

import android.app.Application
import com.berkchy.himemp.data.PlaybackController

class HimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlaybackController.init(this)
    }
}
