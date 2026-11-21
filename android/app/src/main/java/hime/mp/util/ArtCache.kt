package hime.mp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import hime.mp.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtCache {
    private const val MaxArtDimension = 512
    private const val MaxCacheKb = 24 * 1024

    private val cache = object : LruCache<String, Bitmap>(MaxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun cached(track: Track): Bitmap? = cache.get(track.id)

    fun loadBlocking(track: Track): Bitmap? {
        cached(track)?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(track.path)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MaxArtDimension) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            )
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
