package dev.ronova.gryphonis.stream

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/** Источник данных для ExoPlayer: читает файлы из архивов и FTP потоком, без временных файлов. */
class StreamDataSource : BaseDataSource(true) {

    private var stream: InputStream? = null
    private var currentUri: Uri? = null
    private var remaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        val src = StreamSource.parse(dataSpec.uri) ?: throw IOException("Bad stream address: ${dataSpec.uri}")
        transferInitializing(dataSpec)
        stream = src.open(dataSpec.position)
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            src.size > 0 -> max(0L, src.size - dataSpec.position)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (remaining == C.LENGTH_UNSET.toLong()) length else min(remaining, length.toLong()).toInt()
        val n = stream!!.read(buffer, offset, toRead)
        if (n < 0) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        try {
            stream?.close()
        } finally {
            stream = null
            currentUri = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

/** Обычные адреса (файлы, http) отдаёт стандартный источник, адреса gstream: — наш. */
class AppDataSourceFactory(context: Context) : DataSource.Factory {
    private val default = DefaultDataSource.Factory(context.applicationContext)

    override fun createDataSource(): DataSource = RoutingDataSource(default.createDataSource(), StreamDataSource())
}

private class RoutingDataSource(
    private val base: DataSource,
    private val stream: StreamDataSource,
) : DataSource {

    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        base.addTransferListener(transferListener)
        stream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val d: DataSource = if (dataSpec.uri.scheme == StreamSource.SCHEME) stream else base
        active = d
        return d.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
