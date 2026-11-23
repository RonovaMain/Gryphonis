package dev.ronova.gryphonis.stream

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Отдаёт другим приложениям файлы из архивов и с FTP как обычные content://-адреса.
 * Данные читаются по запросу прямо из источника (с перемоткой), на диск ничего не копируется:
 * так «Открыть в другом приложении», установка APK и «Поделиться» работают без распаковки.
 */
class StreamProvider : ContentProvider() {

    companion object {
        fun authority(context: Context): String = "${context.packageName}.stream"

        private val thread: HandlerThread by lazy { HandlerThread("gryphonis-stream").apply { start() } }

        fun mimeOfName(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = StreamSource.parseContent(uri)?.let { mimeOfName(it.name) }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val src = StreamSource.parseContent(uri) ?: return null
        val cols = projection?.map { it } ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols.toTypedArray())
        val row = cols.map<String, Any?> {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> src.name
                OpenableColumns.SIZE -> if (src.size > 0) src.size else null
                else -> null
            }
        }
        cursor.addRow(row.toTypedArray())
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode.any { it == 'w' || it == 'a' }) throw FileNotFoundException("Read-only")
        val src = StreamSource.parseContent(uri) ?: throw FileNotFoundException(uri.toString())
        val ctx = context ?: throw FileNotFoundException()
        val total = try {
            src.resolveSize()
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        return sm.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            Reader(src, total),
            Handler(thread.looper),
        )
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private class Reader(private val src: StreamSource, private val total: Long) : ProxyFileDescriptorCallback() {
        private var stream: InputStream? = null
        private var pos = -1L

        override fun onGetSize(): Long = total

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            try {
                if (offset >= total) return 0
                var s = stream
                if (s == null || offset != pos) {
                    s?.close()
                    s = src.open(offset)
                    stream = s
                    pos = offset
                }
                val want = minOf(size.toLong(), total - offset).toInt()
                var got = 0
                while (got < want) {
                    val r = s.read(data, got, want - got)
                    if (r < 0) break
                    got += r
                }
                pos += got
                return got
            } catch (e: IOException) {
                throw ErrnoException("onRead", OsConstants.EIO)
            }
        }

        override fun onRelease() {
            try {
                stream?.close()
            } catch (_: Exception) {
            }
            stream = null
        }
    }
}
