package dev.ronova.gryphonis.stream

import android.content.Context
import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.buffer
import okio.source

/** Загрузчик картинок Coil, который умеет читать фото из архивов и с FTP потоком (без кэша на диске). */
object StreamImages {
    @Volatile
    private var loader: ImageLoader? = null

    fun loader(context: Context): ImageLoader {
        loader?.let { return it }
        return synchronized(this) {
            loader ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(StreamKeyer())
                    add(StreamFetcher.Factory())
                }
                .build()
                .also { loader = it }
        }
    }
}

private class StreamKeyer : Keyer<StreamSource> {
    override fun key(data: StreamSource, options: Options): String = data.toUri().toString()
}

private class StreamFetcher(private val src: StreamSource, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val input = src.open(0)
        return SourceResult(
            source = ImageSource(input.source().buffer(), options.context),
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(src.extension),
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<StreamSource> {
        override fun create(data: StreamSource, options: Options, imageLoader: ImageLoader): Fetcher =
            StreamFetcher(data, options)
    }
}
