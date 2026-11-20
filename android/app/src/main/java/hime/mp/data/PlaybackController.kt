package hime.mp.data

import android.content.Context
import android.content.Intent
import hime.mp.core.NativeBridge
import hime.mp.core.PlaybackState
import hime.mp.core.Track
import hime.mp.playback.PlaybackService
import hime.mp.util.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object PlaybackController : NativeBridge.EventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _settings = MutableStateFlow<Map<String, String>>(emptyMap())
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    private var initialized = false
    private lateinit var app: Context
    private var lastTrackId: String? = null

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        app = context.applicationContext
        NativeBridge.init(app.filesDir.absolutePath)
        NativeBridge.setEventListener(this)
        refreshState()
        refreshSettings()
    }

    override fun onEvent(event: String, payload: String) {
        scope.launch {
            when (event) {
                "state" -> {
                    _state.value = PlaybackState.from(payload)
                    syncTrack(_state.value.trackId)
                }
                "trackChanged" -> syncTrack(payload.ifBlank { _state.value.trackId }, force = true)
                "error" -> _messages.tryEmit(payload)
                "fatal" -> CrashHandler.reportFatal(payload)
                "sleepTimerFired" -> _messages.tryEmit("Sleep timer reached")
            }
        }
    }

    private fun syncTrack(id: String?, force: Boolean = false) {
        if (id == null) {
            lastTrackId = null
            _currentTrack.value = null
            return
        }
        if (!force && id == lastTrackId && _currentTrack.value != null) return
        lastTrackId = id
        scope.launch(Dispatchers.IO) {
            val fetched = runCatching { MusicRepository.track(id) }.getOrNull()
            if (fetched != null) _currentTrack.value = fetched
        }
    }

    fun refreshState() {
        scope.launch {
            _state.value = MusicRepository.state()
            syncTrack(_state.value.trackId)
        }
    }

    fun refreshSettings() {
        scope.launch {
            runCatching { _settings.value = MusicRepository.settings() }
        }
    }

    fun updateSetting(key: String, value: String) {
        _settings.value = _settings.value + (key to value)
    }

    fun playTracks(tracks: List<Track>, index: Int) {
        val track = tracks.getOrNull(index) ?: return
        _currentTrack.value = track
        lastTrackId = track.id
        playQueue(tracks.map { it.id }, index)
    }

    fun playQueue(ids: List<String>, index: Int) {
        if (ids.isEmpty()) return
        NativeBridge.setQueue(ids.toTypedArray(), index)
        NativeBridge.play()
        startService()
    }

    fun togglePlay() {
        if (_state.value.playing) {
            NativeBridge.pause()
        } else {
            NativeBridge.play()
            startService()
        }
    }

    fun play() {
        NativeBridge.play()
        startService()
    }

    fun pause() = NativeBridge.pause()

    fun stop() {
        NativeBridge.stop()
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    fun next() = NativeBridge.next()
    fun previous() = NativeBridge.previous()
    fun seek(ms: Long) = NativeBridge.seek(ms)
    fun setRepeat(mode: Int) = NativeBridge.setRepeat(mode)
    fun setShuffle(enabled: Boolean) = NativeBridge.setShuffle(enabled)
    fun setSpeed(speed: Float) = NativeBridge.setSpeed(speed)
    fun setSleepTimer(durationMs: Long) = NativeBridge.setSleepTimer(durationMs)
    fun queueAdd(ids: List<String>, at: Int) = NativeBridge.queueAdd(ids.toTypedArray(), at)
    fun queueMove(from: Int, to: Int) = NativeBridge.queueMove(from, to)
    fun queueRemove(index: Int) = NativeBridge.queueRemove(index)
    fun queueClear() = NativeBridge.queueClear()

    fun startService() {
        runCatching {
            app.startForegroundService(Intent(app, PlaybackService::class.java))
        }
    }
}
