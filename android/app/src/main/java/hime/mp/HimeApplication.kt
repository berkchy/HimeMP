package hime.mp

import android.app.Application
import hime.mp.data.PlaybackController
import hime.mp.util.CrashHandler

class HimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        PlaybackController.init(this)
    }
}
