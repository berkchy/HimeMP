package com.berkchy.himemp.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.berkchy.himemp.data.PlaybackController

class DeviceEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shouldPause = when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> intent.getIntExtra("state", 1) == 0
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> true
            "android.bluetooth.device.action.ACL_DISCONNECTED" -> true
            else -> false
        }
        if (shouldPause) PlaybackController.pause()
    }
}
