package dev.ronova.gryphonis.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ronova.gryphonis.media.MusicPlayer

/** Играет ли сейчас этот файл: NONE — это не текущий трек, PAUSED — текущий, но на паузе. */
enum class NowPlaying { NONE, PAUSED, PLAYING }

@Composable
fun nowPlayingOf(path: String): NowPlaying {
    val current = MusicPlayer.currentPath
    return when {
        current == null || current != path -> NowPlaying.NONE
        MusicPlayer.playWhenReady -> NowPlaying.PLAYING
        else -> NowPlaying.PAUSED
    }
}

/** Затемнение поверх обложки с «эквалайзером»: так в списке сразу видно, какой трек сейчас играет. */
@Composable
fun NowPlayingOverlay(state: NowPlaying, modifier: Modifier = Modifier) {
    if (state == NowPlaying.NONE) return
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        val bars = Modifier.fillMaxWidth(0.46f).aspectRatio(1f)
        if (state == NowPlaying.PLAYING) {
            AnimatedBars(Color.White, bars)
        } else {
            StillBars(Color.White.copy(alpha = 0.9f), bars)
        }
    }
}

@Composable
private fun AnimatedBars(color: Color, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a = t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b = t.animateFloat(1f, 0.2f, infiniteRepeatable(tween(470, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c = t.animateFloat(0.4f, 0.95f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    val d = t.animateFloat(0.85f, 0.25f, infiniteRepeatable(tween(390, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    // значения читаются только при рисовании — интерфейс при этом не перекомпонуется на каждом кадре
    Canvas(modifier) { drawBars(color, floatArrayOf(a.value, b.value, c.value, d.value)) }
}

@Composable
private fun StillBars(color: Color, modifier: Modifier) {
    Canvas(modifier) { drawBars(color, floatArrayOf(0.45f, 0.8f, 0.6f, 0.35f)) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(color: Color, values: FloatArray) {
    val n = values.size
    val gap = size.width * 0.12f
    val w = (size.width - gap * (n - 1)) / n
    for (i in 0 until n) {
        val h = (size.height * values[i]).coerceAtLeast(w)
        drawRoundRect(
            color = color,
            topLeft = Offset(i * (w + gap), size.height - h),
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2f, w / 2f),
        )
    }
}
