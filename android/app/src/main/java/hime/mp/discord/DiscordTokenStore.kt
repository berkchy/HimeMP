package hime.mp.discord

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DiscordTokenStore(context: Context) {
    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hime_discord_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    var accessToken: String?
        get() = prefs?.getString(KEY_ACCESS, null)
        set(value) = put(KEY_ACCESS, value)

    var refreshToken: String?
        get() = prefs?.getString(KEY_REFRESH, null)
        set(value) = put(KEY_REFRESH, value)

    var expiresAt: Long
        get() = prefs?.getLong(KEY_EXPIRES, 0L) ?: 0L
        set(value) = put(KEY_EXPIRES, value)

    var sessionId: String?
        get() = prefs?.getString(KEY_SESSION, null)
        set(value) = put(KEY_SESSION, value)

    var sequence: Int?
        get() = prefs?.takeIf { it.contains(KEY_SEQUENCE) }
            ?.getInt(KEY_SEQUENCE, -1)
            ?.takeIf { it >= 0 }
        set(value) = if (value == null) remove(KEY_SEQUENCE) else put(KEY_SEQUENCE, value)

    var enabled: Boolean
        get() = prefs?.getBoolean(KEY_ENABLED, false) ?: false
        set(value) = put(KEY_ENABLED, value)

    fun clearSession() {
        sessionId = null
        sequence = null
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    private fun put(key: String, value: String?) {
        prefs?.edit()?.apply { if (value == null) remove(key) else putString(key, value) }?.apply()
    }

    private fun put(key: String, value: Long) {
        prefs?.edit()?.putLong(key, value)?.apply()
    }

    private fun put(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    private fun put(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    private fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_SESSION = "session_id"
        const val KEY_SEQUENCE = "sequence"
        const val KEY_ENABLED = "enabled"
    }
}
