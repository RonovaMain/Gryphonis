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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import dev.ronova.gryphonis.media.Thumbnails
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.data.FileKind
import androidx.compose.material3.MaterialTheme
import dev.ronova.gryphonis.ui.theme.GryphLilac
import dev.ronova.gryphonis.ui.theme.GryphPink
import dev.ronova.gryphonis.ui.theme.GryphSky
import dev.ronova.gryphonis.ui.theme.GryphTheme

/**
 * Минималистичная папка. В фирменной теме — розово-небесный перелив (как иконка приложения),
 * во всех остальных — один цвет: задняя стенка темнее, передняя панель светлее, поэтому
 * папка не сливается ни с фоном, ни сама с собой.
 */
@Composable
fun FolderIcon(modifier: Modifier = Modifier) {
    val extras = GryphTheme.extras
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.12f
        if (extras.rainbow) {
            val back = Brush.linearGradient(
                colors = listOf(GryphPink, GryphLilac, GryphSky),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            drawRoundRect(back, Offset(w * 0.08f, h * 0.14f), Size(w * 0.40f, h * 0.30f), CornerRadius(r, r))
            drawRoundRect(back, Offset(w * 0.08f, h * 0.26f), Size(w * 0.84f, h * 0.60f), CornerRadius(r, r))
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
        } else {
            val back = extras.folderBack
            // язычок и задняя стенка
            drawRoundRect(back, Offset(w * 0.08f, h * 0.14f), Size(w * 0.40f, h * 0.30f), CornerRadius(r, r))
            drawRoundRect(back, Offset(w * 0.08f, h * 0.26f), Size(w * 0.84f, h * 0.60f), CornerRadius(r, r))
            // передняя панель того же цвета, но светлее
            drawRoundRect(
                color = extras.folderFront,
                topLeft = Offset(w * 0.08f, h * 0.40f),
                size = Size(w * 0.84f, h * 0.46f),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

/** Цвет значка типа файла. В фирменной теме — разноцветный, в остальных — один акцентный цвет. */
@Composable
fun kindAccent(kind: FileKind): Color {
    if (!GryphTheme.extras.rainbow) return MaterialTheme.colorScheme.primary
    return when (kind) {
        FileKind.IMAGE -> Color(0xFF26A69A)
        FileKind.VIDEO -> Color(0xFFEF5350)
        FileKind.AUDIO -> Color(0xFFAB47BC)
        FileKind.TEXT -> Color(0xFF42A5F5)
        FileKind.ARCHIVE -> Color(0xFFFFA726)
        FileKind.PDF -> Color(0xFFE53935)
        FileKind.APK -> Color(0xFF66BB6A)
        FileKind.DOC -> Color(0xFF5C6BC0)
        FileKind.FOLDER, FileKind.OTHER -> Color(0xFF90A4AE)
    }
}

@Composable
fun FileTypeBadge(kind: FileKind, modifier: Modifier = Modifier) {
    val icon = when (kind) {
        FileKind.IMAGE -> Icons.Rounded.Image
        FileKind.VIDEO -> Icons.Rounded.Movie
        FileKind.AUDIO -> Icons.Rounded.MusicNote
        FileKind.TEXT -> Icons.Rounded.Description
        FileKind.ARCHIVE -> Icons.Rounded.Archive
        FileKind.PDF -> Icons.Rounded.PictureAsPdf
        FileKind.APK -> Icons.Rounded.Android
        FileKind.DOC -> Icons.Rounded.Description
        FileKind.FOLDER, FileKind.OTHER -> Icons.Rounded.Description
    }
    val color = kindAccent(kind)
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
        FolderIcon(modifier.then(faded))
    } else {
        val ctx = LocalContext.current
        val hasPicture = item.kind == FileKind.AUDIO || item.kind == FileKind.APK
        // Обложка трека / иконка приложения: сначала берём из памяти (без мигания), остальное грузится в фоне
        var picture by remember(item.path, item.modified, showThumbnail) {
            mutableStateOf<ImageBitmap?>(
                if (!showThumbnail) null
                else when (item.kind) {
                    FileKind.AUDIO -> Thumbnails.peekAudio(item)
                    FileKind.APK -> Thumbnails.peekApk(item)
                    else -> null
                }
            )
        }
        if (showThumbnail && hasPicture) {
            LaunchedEffect(item.path, item.modified, item.size) {
                if (picture == null) {
                    picture = if (item.kind == FileKind.AUDIO) Thumbnails.audio(item) else Thumbnails.apk(ctx, item)
                }
            }
        }
        Box(modifier.then(faded).clip(RoundedCornerShape(10.dp))) {
            // у иконок приложений бывают прозрачные углы — заглушку под ними прячем, под обложками она не видна
            if (picture == null || item.kind == FileKind.AUDIO) FileTypeBadge(item.kind, Modifier.fillMaxSize())
            if (showThumbnail && item.kind == FileKind.IMAGE) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(item.file)
                        .size(160)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Crossfade(targetState = picture, animationSpec = tween(180), label = "thumb") { bmp ->
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = if (item.kind == FileKind.APK) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (item.kind == FileKind.AUDIO) NowPlayingOverlay(nowPlayingOf(item.path))
        }
    }
}
