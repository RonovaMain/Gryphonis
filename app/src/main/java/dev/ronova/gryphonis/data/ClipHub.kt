package dev.ronova.gryphonis.data

import dev.ronova.gryphonis.ftp.RemoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Буфер обмена для источников, которые не лежат на телефоне как обычные файлы. */
sealed interface ExtClip {
    data class Remote(val items: List<RemoteItem>) : ExtClip
    data class Archive(val zip: File, val paths: List<String>) : ExtClip
}

object ClipHub {
    val state = MutableStateFlow<ExtClip?>(null)
}
