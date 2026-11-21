package hime.mp.discord

import android.net.Uri
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
    const val DISCORD_REDIRECT_URI = "discord-$CLIENT_ID:/authorize/callback"

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

    fun authorizationUrl(
        verifier: String,
        state: String,
        redirectUri: String = DISCORD_REDIRECT_URI,
    ): String {
        val challenge = challenge(verifier)
        return AUTHORIZE_URL +
            "?response_type=code" +
            "&client_id=$CLIENT_ID" +
            "&redirect_uri=${Uri.encode(redirectUri)}" +
            "&scope=${Uri.encode(SCOPES)}" +
            "&state=$state" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&prompt=consent" +
            "&integration_type=0"
    }

    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    suspend fun exchange(
        code: String,
        verifier: String,
        redirectUri: String = DISCORD_REDIRECT_URI,
    ): Tokens = withContext(Dispatchers.IO) {
        postForm(
            mapOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
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
