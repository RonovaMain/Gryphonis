package dev.ronova.gryphonis.stream

import android.media.MediaDataSource
import java.io.InputStream

/** Для MediaMetadataRetriever: теги и обложка музыки из архива или с FTP без копирования файла. */
class StreamMediaDataSource(private val src: StreamSource) : MediaDataSource() {

    private var stream: InputStream? = null
    private var pos = -1L
    private var total = src.size.takeIf { it > 0 } ?: -1L

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        if (total in 1..position) return -1
        var s = stream
        if (s == null || position != pos) {
            s?.close()
            s = src.open(position)
            stream = s
            pos = position
        }
        val r = s.read(buffer, offset, size)
        if (r > 0) pos += r
        return r
    }

    override fun getSize(): Long = total

    @Synchronized
    override fun close() {
        try {
            stream?.close()
        } catch (_: Exception) {
        }
        stream = null
    }
}
