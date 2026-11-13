package dev.ronova.gryphonis.media

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioMeta(val title: String?, val artist: String?, val album: String?, val cover: ImageBitmap?)

/** Читает теги и обложку из файла (в фоне) и держит несколько последних результатов в кэше. */
object AudioMetaLoader {

    private val cache = object : LinkedHashMap<String, AudioMeta>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioMeta>?): Boolean = size > 12
    }

    suspend fun load(path: String): AudioMeta = withContext(Dispatchers.IO) {
        val cached = synchronized(cache) { cache[path] }
        if (cached != null) return@withContext cached
        val meta = read(File(path))
        synchronized(cache) { cache[path] = meta }
        meta
    }

    private fun read(file: File): AudioMeta {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val pic = r.embeddedPicture
            AudioMeta(
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                cover = if (pic != null) decode(pic, 600) else null,
            )
        } catch (e: Exception) {
            AudioMeta(null, null, null, null)
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun decode(bytes: ByteArray, target: Int): ImageBitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
