package dev.ronova.gryphonis.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.media.AudioMeta
import dev.ronova.gryphonis.media.AudioMetaLoader
import dev.ronova.gryphonis.media.MusicPlayer
import dev.ronova.gryphonis.ui.theme.GryphLilac
import dev.ronova.gryphonis.ui.theme.GryphPink
import dev.ronova.gryphonis.ui.theme.GryphSky
import dev.ronova.gryphonis.util.formatClock
import kotlinx.coroutines.delay
import java.io.File

/**
 * Полноэкранный плеер. Музыка играет в службе, поэтому «назад» просто сворачивает плеер
 * (останется мини-плеер и уведомление). Очередь спрятана в нижнюю шторку.
 */
@Composable
fun AudioPlayerScreen(path: String?, onBack: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(false) }

    val hasMedia = MusicPlayer.hasMedia
    LaunchedEffect(hasMedia) {
        if (hasMedia) seen = true else if (seen) onBack()
    }

    val currentPath = MusicPlayer.currentPath
    val meta by produceState<AudioMeta?>(null, currentPath) {
        value = currentPath?.let { AudioMetaLoader.load(it) }
    }

    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) position = MusicPlayer.positionMs()
            delay(300)
        }
    }

    val fallbackName = currentPath?.let { File(it).nameWithoutExtension }
        ?: (path?.let { File(it).nameWithoutExtension } ?: "")
    val title = meta?.title?.takeIf { it.isNotBlank() } ?: MusicPlayer.title ?: fallbackName
    val sub = listOfNotNull(meta?.artist ?: MusicPlayer.artist, meta?.album)
        .filter { it.isNotBlank() }
        .joinToString(" — ")
    val duration = MusicPlayer.durationMs
    val sliderMax = if (duration > 0) duration.toFloat() else 1f

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- верхняя строка
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.minimize),
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = stringResource(R.string.queue))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue_count, MusicPlayer.count)) },
                            leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showQueue = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stop_and_close)) },
                            leadingIcon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                MusicPlayer.stopAndClose()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- обложка
            Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 340.dp)
                    .aspectRatio(1f)
                    .shadow(18.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(GryphPink, GryphLilac, GryphSky))),
                contentAlignment = Alignment.Center,
            ) {
                val cover = meta?.cover
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(110.dp))
                }
            }

            Spacer(Modifier.height(28.dp))

            // ---- название
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
                textAlign = TextAlign.Center,
            )
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (MusicPlayer.error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.audio_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- перемотка
            Slider(
                value = if (dragging) dragValue else position.toFloat().coerceIn(0f, sliderMax),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    MusicPlayer.seekTo(dragValue.toLong())
                    position = dragValue.toLong()
                    dragging = false
                },
                valueRange = 0f..sliderMax,
                enabled = duration > 0,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    formatClock((if (dragging) dragValue.toLong() else position).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.weight(1f))
                Text(formatClock(duration.toInt()), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))

            // ---- перемешать / назад / play / вперёд / повтор
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { MusicPlayer.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (MusicPlayer.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = { MusicPlayer.previous() }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .size(74.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GryphPink, GryphSky)))
                        .clickable { MusicPlayer.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (MusicPlayer.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { MusicPlayer.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { MusicPlayer.cycleRepeat() }) {
                    Icon(
                        if (MusicPlayer.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.repeat),
                        tint = if (MusicPlayer.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.track_of, MusicPlayer.index + 1, MusicPlayer.count.coerceAtLeast(1)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showQueue) QueueSheet(onDismiss = { showQueue = false })
}

@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (MusicPlayer.index - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(R.string.queue_count, MusicPlayer.count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            items(MusicPlayer.queue, key = { it.index }) { q ->
                val current = q.index == MusicPlayer.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { MusicPlayer.skipTo(q.index) }
                        .background(
                            if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (current) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        q.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
