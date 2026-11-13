package dev.ronova.gryphonis.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ronova.gryphonis.media.AudioMeta
import dev.ronova.gryphonis.media.AudioMetaLoader
import dev.ronova.gryphonis.media.MusicPlayer
import dev.ronova.gryphonis.ui.theme.GryphLilac
import dev.ronova.gryphonis.ui.theme.GryphPink
import dev.ronova.gryphonis.ui.theme.GryphSky
import kotlinx.coroutines.delay
import java.io.File

/** Компактный плеер внизу экрана: открывается по нажатию, музыка при этом не прерывается. */
@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            position = MusicPlayer.positionMs()
            delay(500)
        }
    }

    val path = MusicPlayer.currentPath
    val meta by produceState<AudioMeta?>(null, path) {
        value = path?.let { AudioMetaLoader.load(it) }
    }
    val title = meta?.title?.takeIf { it.isNotBlank() }
        ?: MusicPlayer.title
        ?: path?.let { File(it).nameWithoutExtension }
        ?: ""
    val artist = (meta?.artist ?: MusicPlayer.artist).orEmpty()
    val duration = MusicPlayer.durationMs
    val fraction = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(GryphPink, GryphLilac, GryphSky))),
                    contentAlignment = Alignment.Center,
                ) {
                    val cover = meta?.cover
                    if (cover != null) {
                        Image(bitmap = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    if (artist.isNotEmpty()) {
                        Text(
                            artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { MusicPlayer.togglePlay() }) {
                    Icon(
                        if (MusicPlayer.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = { MusicPlayer.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null)
                }
                IconButton(onClick = { MusicPlayer.stopAndClose() }) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
            }
        }
    }
}
