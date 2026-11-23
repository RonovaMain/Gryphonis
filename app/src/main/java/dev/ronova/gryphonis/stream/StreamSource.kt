package dev.ronova.gryphonis.stream

import android.net.Uri
import dev.ronova.gryphonis.data.ArchiveOps
import dev.ronova.gryphonis.ftp.RemoteSession
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Файл, который не лежит на диске телефона: запись внутри архива или файл на FTP-сервере.
 * Данные читаются потоком прямо из источника, на диск ничего не копируется.
 */
data class StreamSource(
    val kind: Kind,
    /** Для архива — путь к архиву на телефоне, для FTP — пусто. */
    val container: String,
    /** Путь записи внутри архива или путь файла на сервере. */
    val path: String,
    /** Размер в байтах; 0 — неизвестен. */
    val size: Long,
) {
    enum class Kind { ARCHIVE, FTP }

    val name: String get() = path.substringAfterLast('/').ifEmpty { "file" }
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    /** Поток данных, начиная с байта [offset]. Закрывать после использования. */
    fun open(offset: Long = 0L): InputStream = when (kind) {
        Kind.ARCHIVE -> {
            val input = ArchiveOps.openEntry(File(container), path)
            if (offset > 0) {
                try {
                    skipFully(input, offset)
                } catch (e: Throwable) {
                    try {
                        input.close()
                    } catch (_: Exception) {
                    }
                    throw e
                }
            }
            input
        }
        Kind.FTP -> ResumableStream(this, offset)
    }

    /** Если размер неизвестен (например, bz2), один раз прочитываем поток до конца и считаем. */
    fun resolveSize(): Long {
        if (size > 0) return size
        var total = 0L
        open(0).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                total += r
            }
        }
        return total
    }

    /** Открытие без обёртки повтора: для FTP — отдельное соединение с докачкой с нужного байта. */
    internal fun openRaw(offset: Long): InputStream = when (kind) {
        Kind.FTP -> RemoteSession.openStream(path, offset)
        Kind.ARCHIVE -> open(offset)
    }

    private fun base(b: Uri.Builder): Uri.Builder = b
        .appendPath(name)
        .appendQueryParameter("c", container)
        .appendQueryParameter("p", path)
        .appendQueryParameter("s", size.toString())

    /** Внутренний адрес для Coil / ExoPlayer / экранов. */
    fun toUri(): Uri = base(Uri.Builder().scheme(SCHEME).authority(kind.name.lowercase())).build()

    /** Адрес для других приложений: отдаётся через [StreamProvider]. */
    fun toContentUri(authority: String): Uri =
        base(Uri.Builder().scheme("content").authority(authority)).appendQueryParameter("k", kind.name.lowercase()).build()

    override fun toString(): String = toUri().toString()

    companion object {
        const val SCHEME = "gstream"

        fun archive(archive: File, entryPath: String, size: Long) =
            StreamSource(Kind.ARCHIVE, archive.absolutePath, entryPath, size)

        fun ftp(path: String, size: Long) = StreamSource(Kind.FTP, "", path, size)

        fun isStreamId(id: String): Boolean = id.startsWith("$SCHEME:")

        fun parse(id: String): StreamSource? = if (isStreamId(id)) parse(Uri.parse(id)) else null

        fun parse(uri: Uri): StreamSource? {
            if (uri.scheme != SCHEME) return null
            val kind = Kind.values().firstOrNull { it.name.equals(uri.authority, ignoreCase = true) } ?: return null
            return fromParams(kind, uri)
        }

        fun parseContent(uri: Uri): StreamSource? {
            val kind = Kind.values().firstOrNull { it.name.equals(uri.getQueryParameter("k"), ignoreCase = true) } ?: return null
            return fromParams(kind, uri)
        }

        private fun fromParams(kind: Kind, uri: Uri): StreamSource? {
            val path = uri.getQueryParameter("p") ?: return null
            return StreamSource(
                kind = kind,
                container = uri.getQueryParameter("c") ?: "",
                path = path,
                size = uri.getQueryParameter("s")?.toLongOrNull() ?: 0L,
            )
        }

        fun skipFully(input: InputStream, count: Long) {
            var left = count
            while (left > 0) {
                val n = input.skip(left)
                if (n > 0) {
                    left -= n
                } else {
                    if (input.read() < 0) throw IOException("Unexpected end of stream")
                    left--
                }
            }
        }
    }
}

/**
 * Поток FTP-файла: если соединение оборвалось (сервер закрыл простаивающий канал, пока играла пауза),
 * один раз тихо переподключаемся и продолжаем с того же байта.
 */
internal class ResumableStream(private val src: StreamSource, start: Long) : InputStream() {
    private var input: InputStream = src.openRaw(start)
    private var pos: Long = start

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n <= 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val n = try {
            input.read(b, off, len)
        } catch (e: IOException) {
            try {
                input.close()
            } catch (_: Exception) {
            }
            input = src.openRaw(pos)
            input.read(b, off, len)
        }
        if (n > 0) pos += n
        return n
    }

    override fun close() {
        input.close()
    }
}
