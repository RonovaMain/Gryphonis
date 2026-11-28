package dev.ronova.gryphonis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.media.AudioMeta
import dev.ronova.gryphonis.media.AudioMetaLoader
import dev.ronova.gryphonis.media.MusicPlayer
import dev.ronova.gryphonis.stream.displayNameOf
import dev.ronova.gryphonis.ui.components.CoverArt
import dev.ronova.gryphonis.util.formatClock
import kotlinx.coroutines.delay
import java.io.File

/**
 * Полноэкранный плеер. Музыка играет в службе, поэтому «назад» просто сворачивает плеер
 * (останется мини-плеер и уведомление). Очередь спрятана в нижнюю шторку.
 *
 * Раскладка выбирается по размеру окна, а не по повороту: в портрете обложка сверху, а управление
 * внизу (всё помещается без прокрутки, обложка сжимается по высоте); в альбомной ориентации
 * обложка слева, а название, перемотка и кнопки — справа.
 */
@Composable
fun AudioPlayerScreen(path: String?, onBack: () -> Unit, onReveal: (File) -> Unit = {}) {
    var showQueue by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(false) }

    val hasMedia = MusicPlayer.hasMedia
    LaunchedEffect(hasMedia) {
        if (hasMedia) seen = true else if (seen) onBack()
    }

    val currentPath = MusicPlayer.currentPath
    // «Показать в папке» есть только у файлов с телефона (не у треков из архива и с FTP)
    val revealFile = remember(currentPath) {
        currentPath?.takeIf { it.startsWith("/") }?.let { File(it) }?.takeIf { it.exists() }
    }
    val reveal: (() -> Unit)? = revealFile?.let { f -> { onReveal(f) } }
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

    val fallbackName = currentPath?.let { displayNameOf(it) }
        ?: (path?.let { displayNameOf(it) } ?: "")
    val title = meta?.title?.takeIf { it.isNotBlank() } ?: MusicPlayer.title ?: fallbackName
    val sub = listOfNotNull(meta?.artist ?: MusicPlayer.artist, meta?.album)
        .filter { it.isNotBlank() }
        .joinToString(" — ")
    val duration = MusicPlayer.durationMs

    val seek = SeekState(
        value = if (dragging) dragValue else position.toFloat().coerceIn(0f, (if (duration > 0) duration.toFloat() else 1f)),
        max = if (duration > 0) duration.toFloat() else 1f,
        enabled = duration > 0,
        positionMs = if (dragging) dragValue.toLong() else position,
        durationMs = duration,
        onChange = {
            dragging = true
            dragValue = it
        },
        onFinished = {
            MusicPlayer.seekTo(dragValue.toLong())
            position = dragValue.toLong()
            dragging = false
        },
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            LandscapeLayout(
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                cover = meta?.cover,
                title = title,
                sub = sub,
                seek = seek,
                onBack = onBack,
                onQueue = { showQueue = true },
                onReveal = reveal,
                compact = maxHeight < 380.dp,
            )
        } else {
            PortraitLayout(
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                cover = meta?.cover,
                title = title,
                sub = sub,
                seek = seek,
                onBack = onBack,
                onQueue = { showQueue = true },
                onReveal = reveal,
            )
        }
    }

    if (showQueue) QueueSheet(onDismiss = { showQueue = false })
}

private class SeekState(
    val value: Float,
    val max: Float,
    val enabled: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val onChange: (Float) -> Unit,
    val onFinished: () -> Unit,
)

// ------------------------------------------------------------------ раскладки

@Composable
private fun PortraitLayout(
    modifier: Modifier,
    cover: androidx.compose.ui.graphics.ImageBitmap?,
    title: String,
    sub: String,
    seek: SeekState,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onReveal: (() -> Unit)?,
) {
    Column(modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar(onBack = onBack, onQueue = onQueue, onReveal = onReveal, modifier = Modifier.padding(top = 4.dp))

        // Обложка занимает всё свободное место по высоте и никогда не выталкивает кнопки за экран
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth * 0.88f, 360.dp, maxHeight - 16.dp).coerceAtLeast(96.dp)
            CoverArt(
                cover = cover,
                iconSize = side * 0.32f,
                modifier = Modifier
                    .size(side)
                    .shadow(14.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)),
            )
        }

        Spacer(Modifier.height(16.dp))
        TrackInfo(title, sub, centered = true)
        Spacer(Modifier.height(10.dp))
        SeekSection(seek)
        Spacer(Modifier.height(6.dp))
        Controls(playSize = 76.dp, sideIcon = 44.dp, toggleIcon = 28.dp)
        Spacer(Modifier.height(8.dp))
        TrackCounter()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LandscapeLayout(
    modifier: Modifier,
    cover: androidx.compose.ui.graphics.ImageBitmap?,
    title: String,
    sub: String,
    seek: SeekState,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onReveal: (() -> Unit)?,
    compact: Boolean,
) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // слева — обложка квадратом по высоте экрана
        BoxWithConstraints(Modifier.fillMaxHeight().weight(0.42f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight - 8.dp).coerceAtLeast(96.dp)
            CoverArt(
                cover = cover,
                iconSize = side * 0.32f,
                modifier = Modifier
                    .size(side)
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp)),
            )
        }
        Spacer(Modifier.width(20.dp))
        // справа — всё остальное
        Column(
            Modifier.fillMaxHeight().weight(0.58f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onBack = onBack, onQueue = onQueue, onReveal = onReveal, showTitle = false)
            Spacer(Modifier.weight(1f))
            TrackInfo(title, sub, centered = false)
            Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
            SeekSection(seek)
            Controls(
                playSize = if (compact) 58.dp else 68.dp,
                sideIcon = if (compact) 34.dp else 40.dp,
                toggleIcon = 26.dp,
            )
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                TrackCounter()
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ части экрана

@Composable
private fun TopBar(
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onReveal: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.minimize),
                modifier = Modifier.size(32.dp),
            )
        }
        if (showTitle) {
            Text(
                stringResource(R.string.now_playing),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onQueue) {
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
                        onQueue()
                    },
                )
                if (onReveal != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.show_in_folder)) },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onReveal()
                        },
                    )
                }
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
}

@Composable
private fun TrackInfo(title: String, sub: String, centered: Boolean) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val horizontal = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontal) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
            textAlign = align,
        )
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                sub,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = align,
            )
        }
        if (MusicPlayer.error != null) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    stringResource(R.string.audio_error),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SeekSection(seek: SeekState) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = seek.value,
            onValueChange = seek.onChange,
            onValueChangeFinished = seek.onFinished,
            valueRange = 0f..seek.max,
            enabled = seek.enabled,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.onSurface.copy(alpha = 0.22f),
                disabledThumbColor = scheme.onSurface.copy(alpha = 0.38f),
                disabledActiveTrackColor = scheme.onSurface.copy(alpha = 0.38f),
                disabledInactiveTrackColor = scheme.onSurface.copy(alpha = 0.16f),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                formatClock(seek.positionMs.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatClock(seek.durationMs.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Перемешать · назад · play/pause · вперёд · повтор. Включённые режимы подсвечены «таблеткой». */
@Composable
private fun Controls(playSize: Dp, sideIcon: Dp, toggleIcon: Dp) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleButton(active = MusicPlayer.shuffle, onClick = { MusicPlayer.toggleShuffle() }) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = if (MusicPlayer.shuffle) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(toggleIcon),
            )
        }
        IconButton(onClick = { MusicPlayer.previous() }, modifier = Modifier.size(sideIcon + 16.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(sideIcon),
            )
        }
        Box(
            Modifier
                .size(playSize)
                .clip(CircleShape)
                .background(scheme.primary)
                .clickable { MusicPlayer.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (MusicPlayer.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(playSize * 0.58f),
            )
        }
        IconButton(onClick = { MusicPlayer.next() }, modifier = Modifier.size(sideIcon + 16.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(sideIcon),
            )
        }
        val repeatOn = MusicPlayer.repeatMode != Player.REPEAT_MODE_OFF
        ToggleButton(active = repeatOn, onClick = { MusicPlayer.cycleRepeat() }) {
            Icon(
                if (MusicPlayer.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = stringResource(R.string.repeat),
                tint = if (repeatOn) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(toggleIcon),
            )
        }
    }
}

@Composable
private fun ToggleButton(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TrackCounter() {
    Text(
        stringResource(R.string.track_of, MusicPlayer.index + 1, MusicPlayer.count.coerceAtLeast(1)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ------------------------------------------------------------------ очередь

@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (MusicPlayer.index - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(R.string.queue_count, MusicPlayer.count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            items(MusicPlayer.queue, key = { it.index }) { q ->
                val current = q.index == MusicPlayer.index
                val fg = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { MusicPlayer.skipTo(q.index) }
                        .background(if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (current) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (current) fg else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        q.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = fg,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
