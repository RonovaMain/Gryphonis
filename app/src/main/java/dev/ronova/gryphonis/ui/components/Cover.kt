package dev.ronova.gryphonis.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import dev.ronova.gryphonis.ui.theme.GryphLilac
import dev.ronova.gryphonis.ui.theme.GryphPink
import dev.ronova.gryphonis.ui.theme.GryphSky
import dev.ronova.gryphonis.ui.theme.GryphTheme

/**
 * Обложка трека. Если в файле обложки нет — заглушка в цветах темы: ноту всегда видно
 * (тёмная нота на светлой заливке в фирменной теме, цвета «контейнера» в остальных).
 * Размер и форму задаёт вызывающий через [modifier] (clip, size...).
 */
@Composable
fun CoverArt(cover: ImageBitmap?, iconSize: Dp, modifier: Modifier = Modifier) {
    val rainbow = GryphTheme.extras.rainbow
    val brush = if (rainbow) {
        Brush.linearGradient(listOf(GryphPink, GryphLilac, GryphSky))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer))
    }
    val tint = if (rainbow) Color(0xFF3A1A4A) else MaterialTheme.colorScheme.onPrimaryContainer
    Box(modifier.background(brush), contentAlignment = Alignment.Center) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}
