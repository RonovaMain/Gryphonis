package dev.ronova.gryphonis.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.kindOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class QueueItem(val index: Int, val title: String)

private suspend fun <T> ListenableFuture<T>.awaitOn(executor: Executor): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            executor,
        )
        cont.invokeOnCancellation { cancel(false) }
    }

/**
 * Единая точка управления музыкой. Плеер живёт в [PlaybackService], а здесь —
 * подключение к нему (MediaController) и состояние для Compose-интерфейса.
 */
object MusicPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting: Deferred<MediaController>? = null

    // ---- состояние для UI
    var hasMedia by mutableStateOf(false)
        private set
    var playWhenReady by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var currentPath by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf<String?>(null)
        private set
    var index by mutableStateOf(0)
        private set
    var count by mutableStateOf(0)
        private set
    var queue by mutableStateOf<List<QueueItem>>(emptyList())
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player, events.containsAny(Player.EVENT_TIMELINE_CHANGED))
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_READY) {
                error = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            this@MusicPlayer.error = error.errorCodeName
        }
    }

    private fun sync(p: Player, rebuildQueue: Boolean) {
        hasMedia = p.mediaItemCount > 0
        playWhenReady = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        buffering = p.playbackState == Player.STATE_BUFFERING
        index = p.currentMediaItemIndex
        count = p.mediaItemCount
        currentPath = p.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        val d = p.duration
        durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
        title = p.mediaMetadata.title?.toString()
        artist = p.mediaMetadata.artist?.toString()
        if (rebuildQueue || queue.size != p.mediaItemCount) {
            queue = (0 until p.mediaItemCount).map { i ->
                QueueItem(i, p.getMediaItemAt(i).mediaMetadata.title?.toString() ?: "…")
            }
        }
    }

    private suspend fun awaitController(context: Context): MediaController {
        val existing = controller
        if (existing != null && existing.isConnected) return existing
        val app = context.applicationContext
        val job = connecting ?: scope.async {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = MediaController.Builder(app, token).buildAsync().awaitOn(ContextCompat.getMainExecutor(app))
            c.addListener(listener)
            controller = c
            sync(c, true)
            c
        }.also { connecting = it }
        try {
            return job.await()
        } finally {
            if (job.isCompleted) connecting = null
        }
    }

    // ---------------------------------------------------------------- запуск

    private fun buildQueue(file: File): Pair<List<MediaItem>, Int> {
        val siblings = file.parentFile?.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && kindOf(false, it.extension.lowercase()) == FileKind.AUDIO }
            ?.sortedWith(compareBy<File, String>(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(file)
        var startIndex = siblings.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)
        var list = siblings
        // очень большие папки режем окном вокруг выбранного трека (иначе очередь не пролезет через Binder)
        if (list.size > 800) {
            val from = (startIndex - 100).coerceAtLeast(0)
            val to = (from + 800).coerceAtMost(list.size)
            list = list.subList(from, to)
            startIndex -= from
        }
        val items = list.map { f ->
            MediaItem.Builder()
                .setMediaId(f.absolutePath)
                .setUri(Uri.fromFile(f))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(f.nameWithoutExtension).build())
                .build()
        }
        return items to startIndex
    }

    /** Играет выбранный файл, очередь — аудиофайлы из той же папки. */
    fun play(context: Context, file: File) {
        scope.launch {
            try {
                val (items, start) = withContext(Dispatchers.IO) { buildQueue(file) }
                val c = awaitController(context)
                if (c.mediaItemCount > 0 && c.currentMediaItem?.mediaId == file.absolutePath) {
                    if (!c.isPlaying) c.play()
                    return@launch
                }
                c.setMediaItems(items, start, 0L)
                c.prepare()
                c.play()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    // ---------------------------------------------------------------- управление

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) {
            c.seekToDefaultPosition(0)
            c.play()
        } else if (c.playWhenReady) {
            c.pause()
        } else {
            c.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun skipTo(i: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(i)
        c.play()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Останавливает музыку и убирает уведомление. */
    fun stopAndClose() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
    }
}
