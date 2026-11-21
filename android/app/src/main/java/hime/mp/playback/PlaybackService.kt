package hime.mp.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import hime.mp.MainActivity
import hime.mp.R
import hime.mp.core.PlaybackState
import hime.mp.core.Track
import hime.mp.data.PlaybackController
import hime.mp.util.ArtCache
import hime.mp.util.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : Service() {
    companion object {
        const val CHANNEL_ID = "himemp_playback"
        const val NOTIFICATION_ID = 1337
        const val ACTION_PLAY = "hime.mp.action.PLAY"
        const val ACTION_PAUSE = "hime.mp.action.PAUSE"
        const val ACTION_TOGGLE = "hime.mp.action.TOGGLE"
        const val ACTION_NEXT = "hime.mp.action.NEXT"
        const val ACTION_PREVIOUS = "hime.mp.action.PREVIOUS"
        const val ACTION_STOP = "hime.mp.action.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private var focusRequest: AudioFocusRequest? = null
    private val deviceReceiver = DeviceEventReceiver()
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "HimeMP").apply {
            setCallback(SessionCallback())
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
        runCatching {
            registerReceiver(deviceReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            })
            receiverRegistered = true
        }
        scope.launch {
            PlaybackController.state.collectLatest { render(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                requestFocus()
                PlaybackController.play()
            }
            ACTION_PAUSE -> PlaybackController.pause()
            ACTION_TOGGLE -> {
                requestFocus()
                PlaybackController.togglePlay()
            }
            ACTION_NEXT -> PlaybackController.next()
            ACTION_PREVIOUS -> PlaybackController.previous()
            ACTION_STOP -> PlaybackController.stop()
        }
        val initial = PlaybackController.state.value
        startForeground(NOTIFICATION_ID, buildNotification(initial, null))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class SessionCallback : MediaSession.Callback() {
        override fun onPlay() {
            requestFocus()
            PlaybackController.play()
        }

        override fun onPause() = PlaybackController.pause()

        override fun onStop() = PlaybackController.stop()

        override fun onSkipToNext() = PlaybackController.next()

        override fun onSkipToPrevious() = PlaybackController.previous()

        override fun onSeekTo(pos: Long) = PlaybackController.seek(pos)
    }

    private fun requestFocus() {
        if (PlaybackController.settings.value["audioFocus"]?.toBoolean() == false) return
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { focus ->
                        if (focus == AudioManager.AUDIOFOCUS_LOSS ||
                            focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            PlaybackController.pause()
                        }
                    }
                    .build()
            }
            focusRequest?.let { manager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                { focus ->
                    if (focus == AudioManager.AUDIOFOCUS_LOSS ||
                        focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        PlaybackController.pause()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonFocus() {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }

    private suspend fun render(state: PlaybackState) {
        val track = PlaybackController.currentTrack.value
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title ?: getString(R.string.app_name))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.artist.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track?.album.orEmpty())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
        val playbackState = android.media.session.PlaybackState.Builder()
            .setState(
                if (state.playing) android.media.session.PlaybackState.STATE_PLAYING
                else android.media.session.PlaybackState.STATE_PAUSED,
                state.positionMs.coerceAtLeast(0),
                state.speed,
            )
            .setActions(
                android.media.session.PlaybackState.ACTION_PLAY or
                    android.media.session.PlaybackState.ACTION_PAUSE or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    android.media.session.PlaybackState.ACTION_SEEK_TO
            )
            .build()
        session.setPlaybackState(playbackState)
        val art = withContext(Dispatchers.IO) { track?.let { ArtCache.loadBlocking(it) } }
        if (state.playing || state.queue.isNotEmpty()) {
            startForeground(NOTIFICATION_ID, buildNotification(state, art))
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun bitmapFrom(resId: Int, sizeDp: Int = 24): Bitmap {
        val drawable = ContextCompat.getDrawable(this, resId)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val size = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun buildNotification(state: PlaybackState, art: Bitmap?): Notification {
        val track: Track? = PlaybackController.currentTrack.value
        val title = track?.title ?: getString(R.string.now_playing)
        val artist = track?.artist.orEmpty()
        val cover = art ?: bitmapFrom(R.drawable.ic_placeholder_album, 48)
        val playIcon = bitmapFrom(
            if (state.playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
            24,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun serviceIntent(action: String) = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun RemoteViews.bindControls() {
            setImageViewBitmap(R.id.notif_art, cover)
            setTextViewText(R.id.notif_title, title)
            setTextViewText(R.id.notif_artist, artist)
            setImageViewBitmap(R.id.notif_play, playIcon)
            setOnClickPendingIntent(R.id.notif_root, openIntent)
            setOnClickPendingIntent(R.id.notif_prev, serviceIntent(ACTION_PREVIOUS))
            setOnClickPendingIntent(R.id.notif_play, serviceIntent(ACTION_TOGGLE))
            setOnClickPendingIntent(R.id.notif_next, serviceIntent(ACTION_NEXT))
        }

        val compact = RemoteViews(packageName, R.layout.notification_player_compact).apply {
            bindControls()
        }
        val expanded = RemoteViews(packageName, R.layout.notification_player).apply {
            bindControls()
            val max = 1000
            val progress = if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs * max).toInt().coerceIn(0, max)
            } else {
                0
            }
            setProgressBar(R.id.notif_progress, max, progress, false)
            setTextViewText(R.id.notif_position, formatDuration(state.positionMs))
            setTextViewText(R.id.notif_duration, formatDuration(state.durationMs))
            setOnClickPendingIntent(R.id.notif_close, serviceIntent(ACTION_STOP))
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(track?.album)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentIntent(openIntent)
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .setOngoing(state.playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(resolveAccent())
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setCustomHeadsUpContentView(compact)
            .build()
    }

    private fun resolveAccent(): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.colorAccent, value, true)) {
            if (value.type == TypedValue.TYPE_REFERENCE) getColor(value.resourceId) else value.data
        } else {
            getColor(R.color.seed)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(deviceReceiver) }
        abandonFocus()
        session.isActive = false
        session.release()
        scope.cancel()
        super.onDestroy()
    }
}
