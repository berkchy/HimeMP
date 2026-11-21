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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import hime.mp.MainActivity
import hime.mp.R
import hime.mp.core.PlaybackState
import hime.mp.data.PlaybackController
import hime.mp.util.ArtCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
        const val ACTION_FAVORITE = "hime.mp.action.FAVORITE"
        const val ACTION_CLOSE = "hime.mp.action.CLOSE"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat
    private var focusRequest: AudioFocusRequest? = null
    private val deviceReceiver = DeviceEventReceiver()
    private var receiverRegistered = false
    private var closing = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "HimeMP").apply {
            setCallback(SessionCallback())
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setSessionActivity(openAppIntent())
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
            combine(
                PlaybackController.state,
                PlaybackController.currentTrack,
            ) { state, _ -> state }.collectLatest { render(it) }
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
            ACTION_FAVORITE -> PlaybackController.toggleFavoriteCurrent()
            ACTION_CLOSE -> {
                closing = true
                stopForeground(STOP_FOREGROUND_REMOVE)
                PlaybackController.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val initial = PlaybackController.state.value
        startForeground(NOTIFICATION_ID, buildNotification(initial, null))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class SessionCallback : MediaSessionCompat.Callback() {
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

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
        if (closing) return
        val track = PlaybackController.currentTrack.value
        val art = withContext(Dispatchers.IO) { track?.let { ArtCache.loadBlocking(it) } }
        val metadata = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                track?.title ?: getString(R.string.now_playing),
            )
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track?.artist.orEmpty())
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track?.album.orEmpty())
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
        if (art != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
        }
        session.setMetadata(metadata.build())
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    if (state.playing) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    state.positionMs.coerceAtLeast(0),
                    state.speed,
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
        if (state.playing || state.queue.isNotEmpty()) {
            startForeground(NOTIFICATION_ID, buildNotification(state, art))
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun buildNotification(state: PlaybackState, art: Bitmap?): Notification {
        val track = PlaybackController.currentTrack.value
        fun action(icon: Int, title: String, action: String): NotificationCompat.Action =
            NotificationCompat.Action.Builder(icon, title, serviceIntent(action)).build()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track?.title ?: getString(R.string.now_playing))
            .setContentText(track?.artist.orEmpty())
            .setSubText(track?.album)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setLargeIcon(art)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_CLOSE))
            .setOngoing(state.playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setProgress(
                state.durationMs.toInt().coerceAtLeast(1),
                state.positionMs.coerceIn(0, state.durationMs).toInt(),
                false,
            )
            .setColor(resolveAccent())
            .setColorized(true)
            .addAction(
                action(
                    R.drawable.ic_notif_previous,
                    "Previous",
                    ACTION_PREVIOUS,
                ),
            )
            .addAction(
                action(
                    if (state.playing) R.drawable.ic_notif_pause
                    else R.drawable.ic_notif_play,
                    if (state.playing) "Pause" else "Play",
                    ACTION_TOGGLE,
                ),
            )
            .addAction(action(R.drawable.ic_notif_next, "Next", ACTION_NEXT))
            .addAction(
                action(
                    R.drawable.ic_notif_favorite,
                    if (track?.favorite == true) "Unfavorite" else "Favorite",
                    ACTION_FAVORITE,
                ),
            )
            .addAction(action(R.drawable.ic_notif_close, "Close", ACTION_CLOSE))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )

        return builder.build()
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
