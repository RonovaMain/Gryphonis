package dev.ronova.gryphonis.stream

import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Один элемент очереди просмотрщика или плеера: либо обычный файл на телефоне,
 * либо файл из архива / с FTP, который читается потоком.
 * [id] — строка, которой экраны обмениваются между собой: путь к файлу или адрес gstream:.
 */
class MediaRef private constructor(val id: String, val file: File?, val stream: StreamSource?) {
    val name: String get() = file?.name ?: stream!!.name
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val isLocal: Boolean get() = file != null

    /** Адрес для ExoPlayer. */
    val uri: Uri get() = file?.let { Uri.fromFile(it) } ?: stream!!.toUri()

    /** Что отдавать Coil в качестве модели. */
    val model: Any get() = file ?: stream!!

    fun open(): InputStream = if (file != null) FileInputStream(file) else stream!!.open(0)

    companion object {
        fun of(id: String): MediaRef {
            val s = StreamSource.parse(id)
            return if (s != null) MediaRef(id, null, s) else MediaRef(id, File(id), null)
        }
    }
}

/** Очередь для просмотрщика: порядок как в списке; выбранный файл всегда внутри. */
fun queueRefs(start: String, queue: List<String>): List<MediaRef> {
    val list = queue.map { MediaRef.of(it) }
    return when {
        list.isEmpty() -> listOf(MediaRef.of(start))
        list.any { it.id == start } -> list
        else -> listOf(MediaRef.of(start)) + list
    }
}

/** Подпись по id: для плеера, который знает только строку. */
fun displayNameOf(id: String): String = MediaRef.of(id).nameWithoutExtension
