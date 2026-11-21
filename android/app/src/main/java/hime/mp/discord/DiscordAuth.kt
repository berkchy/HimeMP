package hime.mp.discord

import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object DiscordAuth {
    const val CLIENT_ID = "1573720763972722699"
    const val REDIRECT_URI = "http://127.0.0.1:9384/callback"

    private const val PORT = 9384
    private const val SCOPES = "identify rpc rpc.activities.write"
    private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"
    private const val AUTHORIZE_URL = "https://discord.com/oauth2/authorize"

    private val http = OkHttpClient()

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInMs: Long,
    )

    data class Callback(val code: String, val state: String?)

    fun authorizationUrl(verifier: String, state: String): String {
        val challenge = challenge(verifier)
        return AUTHORIZE_URL +
            "?response_type=code" +
            "&client_id=$CLIENT_ID" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&scope=${Uri.encode(SCOPES)}" +
            "&state=$state" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&prompt=consent"
    }

    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    suspend fun waitForCallback(): Callback = withContext(Dispatchers.IO) {
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", PORT))
        }
        try {
            val socket = server.accept()
            val requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).use { it.readLine() }
                ?: error("Empty Discord callback")
            val target = requestLine.split(' ').getOrNull(1).orEmpty()
            val uri = Uri.parse("http://127.0.0.1$target")
            val code = uri.getQueryParameter("code") ?: error("Discord callback has no code")
            val state = uri.getQueryParameter("state")
            val html = "<!doctype html><title>HimeMP</title><p>Discord bağlantısı tamamlandı. Sekmeyi kapatabilirsin.</p>"
            val response = (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${html.toByteArray().size}\r\n" +
                    "Connection: close\r\n\r\n" +
                    html
                ).toByteArray()
            socket.getOutputStream().use { it.write(response) }
            socket.close()
            Callback(code, state)
        } finally {
            server.close()
        }
    }

    suspend fun exchange(code: String, verifier: String): Tokens = withContext(Dispatchers.IO) {
        postForm(
            mapOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier,
            ),
        )
    }

    suspend fun refresh(refreshToken: String): Tokens = withContext(Dispatchers.IO) {
        postForm(
            mapOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            ),
        )
    }

    private fun postForm(params: Map<String, String>): Tokens {
        val form = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(form)
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Discord token error ${response.code}: $body")
            val json = JSONObject(body)
            return Tokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").ifBlank { null },
                expiresInMs = json.optLong("expires_in", 3600L) * 1000L,
            )
        }
    }

    private fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
