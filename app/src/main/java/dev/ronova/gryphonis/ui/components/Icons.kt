package dev.ronova.gryphonis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.ui.theme.GryphLilac
import dev.ronova.gryphonis.ui.theme.GryphPink
import dev.ronova.gryphonis.ui.theme.GryphSky

/** Минималистичная папка с розово-небесным переливом — тот же стиль, что у иконки приложения. */
@Composable
fun GradientFolderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.12f
        val back = Brush.linearGradient(
            colors = listOf(GryphPink, GryphLilac, GryphSky),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )
        // язычок
        drawRoundRect(
            brush = back,
            topLeft = Offset(w * 0.08f, h * 0.14f),
            size = Size(w * 0.40f, h * 0.30f),
            cornerRadius = CornerRadius(r, r),
        )
        // задняя стенка
        drawRoundRect(
            brush = back,
            topLeft = Offset(w * 0.08f, h * 0.26f),
            size = Size(w * 0.84f, h * 0.60f),
            cornerRadius = CornerRadius(r, r),
        )
        // передняя «стеклянная» панель
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f)),
                start = Offset(0f, h * 0.40f),
                end = Offset(w, h * 0.90f),
            ),
            topLeft = Offset(w * 0.08f, h * 0.40f),
            size = Size(w * 0.84f, h * 0.46f),
            cornerRadius = CornerRadius(r, r),
        )
    }
}

@Composable
fun FileTypeBadge(kind: FileKind, modifier: Modifier = Modifier) {
    val (icon, color) = when (kind) {
        FileKind.IMAGE -> Icons.Rounded.Image to Color(0xFF26A69A)
        FileKind.VIDEO -> Icons.Rounded.Movie to Color(0xFFEF5350)
        FileKind.AUDIO -> Icons.Rounded.MusicNote to Color(0xFFAB47BC)
        FileKind.TEXT -> Icons.Rounded.Description to Color(0xFF42A5F5)
        FileKind.ARCHIVE -> Icons.Rounded.Archive to Color(0xFFFFA726)
        FileKind.PDF -> Icons.Rounded.PictureAsPdf to Color(0xFFE53935)
        FileKind.APK -> Icons.Rounded.Android to Color(0xFF66BB6A)
        FileKind.DOC -> Icons.Rounded.Description to Color(0xFF5C6BC0)
        FileKind.FOLDER, FileKind.OTHER -> Icons.Rounded.Description to Color(0xFF90A4AE)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize(0.58f))
    }
}

@Composable
fun FileThumb(item: FileItem, showThumbnail: Boolean, modifier: Modifier = Modifier) {
    val faded = if (item.isHidden) Modifier.alpha(0.6f) else Modifier
    if (item.isDirectory) {
        GradientFolderIcon(modifier.then(faded))
    } else {
        Box(modifier.then(faded).clip(RoundedCornerShape(10.dp))) {
            FileTypeBadge(item.kind, Modifier.fillMaxSize())
            if (showThumbnail && item.kind == FileKind.IMAGE) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.file)
                        .size(160)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
