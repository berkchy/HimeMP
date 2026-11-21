package hime.mp.discord

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import hime.mp.core.PlaybackState
import hime.mp.core.Track
import hime.mp.data.MusicRepository
import hime.mp.util.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object DiscordRpcManager {
    private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
    private const val ASSET_KEY = "himemp_icon"
    const val RPC_APPROVED = false
    const val DEFAULT_NAME = "HimeMP"
    const val DEFAULT_DETAILS = "<hime.player.song.name>"
    const val DEFAULT_STATE = "<hime.player.status> • <hime.player.song.info>"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private lateinit var store: DiscordTokenStore
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var callbackJob: Job? = null
    private var reconnectJob: Job? = null
    private var heartbeatInterval = 41_250L
    private var sessionId: String? = null
    private var sequence: Int? = null
    private var currentActivity: JSONObject? = null
    private var currentPlaying = false
    private var activityStartMs = 0L
    private var reconnectAttempts = 0
    private var pendingResume = false
    private var pendingVerifier: String? = null
    private var pendingState: String? = null
    private var activityName = DEFAULT_NAME
    private var detailsTemplate = DEFAULT_DETAILS
    private var stateTemplate = DEFAULT_STATE
    private var activityType = 2
    private var presenceStatus = "auto"
    private var lastPresenceKey = ""
    private var lastPresenceAt = 0L

    private val _status = MutableStateFlow("Disconnected")
    val status: StateFlow<String> = _status

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = DiscordTokenStore(context)
        if (!RPC_APPROVED) {
            _status.value = "Approval pending"
            return
        }
        sessionId = store.sessionId
        sequence = store.sequence
        scope.launch {
            runCatching { MusicRepository.settings() }.onSuccess { applyOptions(it) }
        }
        if (store.enabled && !store.accessToken.isNullOrBlank()) {
            scope.launch { connect(resume = false) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!RPC_APPROVED || !::store.isInitialized) return
        store.enabled = enabled
        if (!enabled) {
            disconnect(clearTokens = false)
        } else if (!store.accessToken.isNullOrBlank()) {
            scope.launch { connect(resume = false) }
        }
    }

    fun updateOptions(settings: Map<String, String>) {
        scope.launch { applyOptions(settings) }
    }

    fun authorize(context: Context) {
        if (!RPC_APPROVED) {
            _status.value = "Approval pending"
            return
        }
        if (!::store.isInitialized || callbackJob?.isActive == true) return
        val verifier = DiscordAuth.generateVerifier()
        val state = DiscordAuth.generateVerifier()
        pendingVerifier = verifier
        pendingState = state
        _status.value = "Waiting for Discord"

        val url = DiscordAuth.authorizationUrl(verifier, state, DiscordAuth.DISCORD_REDIRECT_URI)
        val customTabs = CustomTabsIntent.Builder().build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { customTabs.launchUrl(context, Uri.parse(url)) }
            .onFailure {
                pendingVerifier = null
                pendingState = null
                _status.value = "No browser available"
            }
    }

    fun handleOAuthIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "discord-${DiscordAuth.CLIENT_ID}") return
        val verifier = pendingVerifier ?: return
        val error = uri.getQueryParameter("error")
        if (error != null) {
            pendingVerifier = null
            pendingState = null
            _status.value = if (error == "access_denied") "Discord cancelled" else "Discord error: $error"
            return
        }
        val code = uri.getQueryParameter("code") ?: return
        val state = uri.getQueryParameter("state")
        if (pendingState != null && state != pendingState) {
            pendingVerifier = null
            pendingState = null
            _status.value = "Discord state mismatch"
            return
        }
        pendingVerifier = null
        pendingState = null
        callbackJob?.cancel()
        callbackJob = scope.launch {
            try {
                completeAuthorization(
                    DiscordAuth.Callback(code, state),
                    verifier,
                    DiscordAuth.DISCORD_REDIRECT_URI,
                )
            } catch (error: Exception) {
                _status.value = error.message?.take(80) ?: "Connect failed"
            }
        }
    }

    private suspend fun completeAuthorization(
        callback: DiscordAuth.Callback,
        verifier: String,
        redirectUri: String,
    ) {
        try {
            val tokens = DiscordAuth.exchange(callback.code, verifier, redirectUri)
            store.accessToken = tokens.accessToken
            tokens.refreshToken?.let { store.refreshToken = it }
            store.expiresAt = System.currentTimeMillis() + tokens.expiresInMs
            store.clearSession()
            store.enabled = true
            _status.value = "Connected"
            connect(resume = false)
        } finally {
            pendingVerifier = null
            pendingState = null
        }
    }

    fun logout() {
        if (!::store.isInitialized) return
        store.enabled = false
        store.clear()
        disconnect(clearTokens = false)
        _status.value = "Disconnected"
    }

    fun updatePresence(state: PlaybackState, track: Track?) {
        if (!RPC_APPROVED || !::store.isInitialized || !store.enabled || store.accessToken.isNullOrBlank()) return
        val key = "${track?.id.orEmpty()}|${state.playing}|${state.positionMs / 1000}"
        if (key == lastPresenceKey && System.currentTimeMillis() - lastPresenceAt < 1000) return
        lastPresenceKey = key
        lastPresenceAt = System.currentTimeMillis()

        currentActivity = buildActivity(state, track)
        currentPlaying = state.playing
        activityStartMs = if (state.playing && state.durationMs > 0) {
            System.currentTimeMillis() - state.positionMs
        } else {
            0L
        }
        scope.launch {
            if (webSocket == null) connect(resume = false)
            sendPresence()
        }
    }

    private suspend fun connect(resume: Boolean) {
        if (!store.enabled || webSocket != null || store.accessToken.isNullOrBlank()) return
        val refreshed = runCatching { ensureValidToken() }
        if (refreshed.isFailure) {
            _status.value = refreshed.exceptionOrNull()?.message?.take(80) ?: "Token error"
            return
        }
        _status.value = "Connecting"
        pendingResume = resume
        val request = Request.Builder().url(GATEWAY_URL).build()
        webSocket = http.newWebSocket(request, GatewayListener())
    }

    private suspend fun ensureValidToken() {
        if (store.expiresAt > System.currentTimeMillis() + 60_000L) return
        val refreshToken = store.refreshToken ?: error("Discord token expired")
        val tokens = DiscordAuth.refresh(refreshToken)
        store.accessToken = tokens.accessToken
        tokens.refreshToken?.let { store.refreshToken = it }
        store.expiresAt = System.currentTimeMillis() + tokens.expiresInMs
    }

    private fun sendIdentify() {
        val token = store.accessToken ?: return
        val properties = JSONObject()
            .put("\$os", "android")
            .put("\$browser", "Discord Android")
            .put("\$device", "Discord Android")
            .put("client_build_number", 123456)
            .put("client_version", "220.0.0")
            .put("release_channel", "stable")
        val payload = JSONObject()
            .put("op", 2)
            .put(
                "d",
                JSONObject()
                    .put("token", token)
                    .put("properties", properties)
                    .put("presence", presenceObject()),
            )
        webSocket?.send(payload.toString())
    }

    private fun sendResume() {
        val id = sessionId
        if (id.isNullOrBlank()) {
            sendIdentify()
            return
        }
        val payload = JSONObject()
            .put("op", 6)
            .put(
                "d",
                JSONObject()
                    .put("token", store.accessToken)
                    .put("session_id", id)
                    .put("seq", sequence ?: JSONObject.NULL),
            )
        webSocket?.send(payload.toString())
    }

    private fun sendHeartbeat() {
        webSocket?.send(
            JSONObject()
                .put("op", 1)
                .put("d", sequence ?: JSONObject.NULL)
                .toString(),
        )
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay(Random.nextLong(0, heartbeatInterval.coerceAtLeast(1L)))
            while (webSocket != null) {
                sendHeartbeat()
                delay(heartbeatInterval)
            }
        }
    }

    private fun sendPresence() {
        val socket = webSocket ?: return
        socket.send(
            JSONObject()
                .put("op", 3)
                .put("d", presenceObject())
                .toString(),
        )
    }

    private fun applyOptions(settings: Map<String, String>) {
        activityName = settings["discordName"]?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        detailsTemplate = settings["discordDetails"] ?: DEFAULT_DETAILS
        stateTemplate = settings["discordState"] ?: DEFAULT_STATE
        activityType = settings["discordActivityType"]?.toIntOrNull()?.takeIf { it in 0..3 } ?: 2
        presenceStatus = settings["discordPresenceStatus"] ?: "auto"
        if (currentActivity != null) sendPresence()
    }

    fun renderTemplate(template: String, state: PlaybackState, track: Track?): String {
        val info = listOfNotNull(
            track?.artist?.takeIf { it.isNotBlank() },
            track?.album?.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
        val status = if (state.playing) "" else "Paused"
        val title = track?.title?.takeIf { it.isNotBlank() } ?: "Unknown track"
        val artist = track?.artist.orEmpty()
        val album = track?.album.orEmpty()
        val position = formatDuration(state.positionMs.coerceAtLeast(0))
        val duration = formatDuration(state.durationMs.coerceAtLeast(0))
        val remainingText = formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))
        return template
            .replace("<hime.player.song.name>", title)
            .replace("<hime.player.song.title>", title)
            .replace("{title}", title)
            .replace("<hime.player.song.artist>", artist)
            .replace("{artist}", artist)
            .replace("<hime.player.song.album>", album)
            .replace("{album}", album)
            .replace("<hime.player.song.info>", info)
            .replace("{info}", info)
            .replace("<hime.player.status>", status)
            .replace("{status}", status)
            .replace("<hime.player.song.position>", position)
            .replace("{position}", position)
            .replace("<hime.player.song.duration>", duration)
            .replace("{duration}", duration)
            .replace("<hime.player.song.remaining>", remainingText)
            .replace("{remaining}", remainingText)
            .trim()
            .trim('•')
            .trim()
            .take(128)
    }

    private fun presenceObject(): JSONObject {
        val activities = JSONArray()
        currentActivity?.let { activities.put(it) }
        val status = if (presenceStatus == "auto") {
            if (currentPlaying) "online" else "idle"
        } else {
            presenceStatus
        }
        return JSONObject()
            .put("since", if (currentPlaying && activityStartMs > 0) activityStartMs else JSONObject.NULL)
            .put("activities", activities)
            .put("status", status)
            .put("afk", false)
    }

    private fun buildActivity(state: PlaybackState, track: Track?): JSONObject? {
        if (track == null) return null
        val name = activityName.take(128).ifBlank { DEFAULT_NAME }
        val details = renderTemplate(detailsTemplate, state, track).ifBlank { "Unknown track" }
        val stateText = renderTemplate(stateTemplate, state, track)
        val activity = JSONObject()
            .put("name", name)
            .put("type", activityType)
            .put("details", details)
            .put("state", stateText)
            .put(
                "assets",
                JSONObject()
                    .put("large_image", ASSET_KEY)
                    .put("large_text", name),
            )
        if (state.playing && state.durationMs > 0) {
            val start = System.currentTimeMillis() - state.positionMs
            activity.put(
                "timestamps",
                JSONObject()
                    .put("start", start)
                    .put("end", start + state.durationMs),
            )
        }
        return activity
    }

    internal fun handleMessage(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        val op = message.optInt("op")
        if (op == 0) {
            val seq = message.optInt("s", -1)
            if (seq >= 0) {
                sequence = seq
                store.sequence = seq
            }
            when (message.optString("t")) {
                "READY" -> {
                    sessionId = message.optJSONObject("d")?.optString("session_id")
                    store.sessionId = sessionId
                    reconnectAttempts = 0
                    _status.value = "Connected"
                    sendPresence()
                }
                "RESUMED" -> {
                    reconnectAttempts = 0
                    _status.value = "Connected"
                    sendPresence()
                }
            }
        }
        when (op) {
            10 -> {
                heartbeatInterval = message.optJSONObject("d")?.optLong("heartbeat_interval") ?: 41_250L
                if (pendingResume && !sessionId.isNullOrBlank()) sendResume()
                else sendIdentify()
                startHeartbeat()
            }
            9 -> {
                if (!message.optBoolean("d", false)) {
                    store.clearSession()
                    sessionId = null
                    sequence = null
                    sendIdentify()
                } else {
                    sendResume()
                }
            }
        }
        if (op == 11) Unit
    }

    internal fun handleClose(code: Int) {
        webSocket = null
        heartbeatJob?.cancel()
        if (code == 1000 || !store.enabled) {
            _status.value = "Disconnected"
            return
        }
        if (code == 4004 || code == 4014) {
            store.clear()
            store.enabled = false
            _status.value = "Discord authorization expired"
            return
        }
        _status.value = "Reconnecting"
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = 1_000L * (1L shl reconnectAttempts.coerceAtMost(5))
            reconnectAttempts++
            delay(delayMs)
            val resumable = code in setOf(4000, 4001, 4002, 4003, 4005, 4007)
            connect(resume = resumable && !sessionId.isNullOrBlank())
        }
    }

    private fun disconnect(clearTokens: Boolean) {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Disconnect")
        webSocket = null
        store.clearSession()
        sessionId = null
        sequence = null
        currentActivity = null
        currentPlaying = false
        activityStartMs = 0L
        lastPresenceKey = ""
        if (clearTokens) store.clear()
    }

    private class GatewayListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            DiscordRpcManager.handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            DiscordRpcManager.handleClose(code)
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            DiscordRpcManager.handleClose(0)
        }
    }
}
