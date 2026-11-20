package com.berkchy.himemp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.berkchy.himemp.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LruCache

object ArtCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4096)
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun cached(track: Track): Bitmap? = cache.get(track.id)

    fun loadBlocking(track: Track): Bitmap? {
        cached(track)?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(track.path)
            val bytes = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) cache.put(track.id, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun load(track: Track): Bitmap? = withContext(Dispatchers.IO) { loadBlocking(track) }

    fun clear() = cache.evictAll()
}
