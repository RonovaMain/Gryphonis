package dev.ronova.gryphonis.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.PowerManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.kindOf
import dev.ronova.gryphonis.ui.theme.GryphLilac
import dev.ronova.gryphonis.ui.theme.GryphPink
import dev.ronova.gryphonis.ui.theme.GryphSky
import dev.ronova.gryphonis.util.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private class AudioMeta(val title: String?, val artist: String?, val album: String?, val cover: Bitmap?)

private fun readMeta(file: File): AudioMeta {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        val pic = r.embeddedPicture
        AudioMeta(
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            cover = if (pic != null) BitmapFactory.decodeByteArray(pic, 0, pic.size) else null,
        )
    } catch (e: Exception) {
        AudioMeta(null, null, null, null)
    } finally {
        try {
            r.release()
        } catch (_: Exception) {
        }
    }
}

@Composable
fun AudioPlayerScreen(startPath: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val playlist = remember(startPath) {
        val start = File(startPath)
        start.parentFile?.listFiles()
            ?.filter { it.isFile && kindOf(false, it.extension.lowercase()) == FileKind.AUDIO }
            ?.sortedBy { it.name.lowercase() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(start)
    }
    var index by remember(startPath) { mutableIntStateOf(playlist.indexOfFirst { it.path == startPath }.coerceAtLeast(0)) }
    val player = remember {
        MediaPlayer().apply { setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK) }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf<AudioMeta?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val errorText = stringResource(R.string.audio_error)

    DisposableEffect(Unit) {
        onDispose {
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(index) {
        val f = playlist[index]
        prepared = false
        error = null
        position = 0
        duration = 0
        try {
            player.reset()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(f.absolutePath)
            player.setOnPreparedListener { mp ->
                mp.start()
                duration = mp.duration
                prepared = true
                isPlaying = true
            }
            player.setOnCompletionListener {
                if (index < playlist.lastIndex) {
                    index += 1
                } else {
                    isPlaying = false
                    position = 0
                }
            }
            player.setOnErrorListener { _, _, _ ->
                error = errorText
                isPlaying = false
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            error = errorText
            isPlaying = false
        }
        meta = withContext(Dispatchers.IO) { readMeta(f) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!dragging) {
                position = try {
                    player.currentPosition
                } catch (e: Exception) {
                    0
                }
            }
            delay(250)
        }
    }

    val current = playlist[index]
    val m = meta

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(GryphPink, GryphLilac, GryphSky))),
                contentAlignment = Alignment.Center,
            ) {
                val cover = m?.cover
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(96.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                m?.title?.takeIf { it.isNotBlank() } ?: current.nameWithoutExtension,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            val sub = listOfNotNull(m?.artist, m?.album).filter { it.isNotBlank() }.joinToString(" — ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

            val sliderMax = if (duration > 0) duration.toFloat() else 1f
            Slider(
                value = if (dragging) dragValue else position.toFloat().coerceIn(0f, sliderMax),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    if (prepared) {
                        try {
                            player.seekTo(dragValue.toInt())
                        } catch (_: Exception) {
                        }
                        position = dragValue.toInt()
                    }
                    dragging = false
                },
                valueRange = 0f..sliderMax,
                enabled = prepared,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                Text(formatClock(if (dragging) dragValue.toInt() else position), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(formatClock(duration), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (index > 0) index -= 1 }, enabled = index > 0) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = prepared) {
                            try {
                                if (player.isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.start()
                                    isPlaying = true
                                }
                            } catch (_: Exception) {
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { if (index < playlist.lastIndex) index += 1 }, enabled = index < playlist.lastIndex) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }

            if (playlist.size > 1) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(playlist, key = { _, f -> f.path }) { i, f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { index = i }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                f.nameWithoutExtension,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
