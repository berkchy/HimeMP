package hime.mp

import android.app.Application
import android.content.ComponentCallbacks2
import hime.mp.data.PlaybackController
import hime.mp.discord.DiscordRpcManager
import hime.mp.lyrics.LyricsManager
import hime.mp.util.ArtCache
import hime.mp.util.CrashHandler

class HimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        PlaybackController.init(this)
        DiscordRpcManager.init(this)
        LyricsManager.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            ArtCache.clear()
        }
    }
}
