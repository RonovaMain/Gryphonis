package dev.ronova.gryphonis.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.ui.components.FileTypeBadge
import dev.ronova.gryphonis.stream.MediaRef
import dev.ronova.gryphonis.stream.StreamImages
import dev.ronova.gryphonis.stream.queueRefs
import dev.ronova.gryphonis.util.OpenUtil
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 2.5f

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun ImageViewerScreen(startPath: String, queue: List<String>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val images = remember(startPath, queue) { queueRefs(startPath, queue) }
    val startIndex = remember(startPath, images) { images.indexOfFirst { it.id == startPath }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }

    var uiVisible by remember { mutableStateOf(true) }
    // true, пока текущее фото приближено: тогда листание пальцем отключено, чтобы двигать фото
    var zoomed by remember { mutableStateOf(false) }

    val file = images[pagerState.currentPage.coerceIn(0, images.lastIndex)]

    // при смене фото сбрасываем флаг приближения
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    // системные панели скрываются и возвращаются вместе с верхним меню
    val activity = remember(ctx) { ctx.findActivity() }
    DisposableEffect(uiVisible) {
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (uiVisible) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    // при выходе из просмотрщика обязательно возвращаем системные панели
    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window
            if (window != null) WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            pageSpacing = 12.dp,
            key = { images[it].id },
        ) { page ->
            ZoomableImagePage(
                file = images[page],
                isCurrent = pagerState.currentPage == page,
                onZoomedChange = { if (pagerState.currentPage == page) zoomed = it },
                onTap = { uiVisible = !uiVisible },
            )
        }

        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
                actions = {
                    if (images.size > 1) {
                        Text("${pagerState.currentPage + 1} / ${images.size}", modifier = Modifier.padding(end = 4.dp))
                    }
                    IconButton(onClick = { OpenUtil.shareRef(ctx, file) }) { Icon(Icons.Rounded.Share, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun ZoomableImagePage(
    file: MediaRef,
    isCurrent: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(Offset.Zero) } // реальные размеры картинки (w, h)
    var painterState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val onZoomedChangeState = rememberUpdatedState(onZoomedChange)
    val isCurrentState = rememberUpdatedState(isCurrent)

    // насколько можно сдвигать картинку при данном масштабе: края картинки не уходят за края экрана
    fun maxOffset(forScale: Float): Offset {
        val bw = boxSize.width.toFloat()
        val bh = boxSize.height.toFloat()
        if (bw <= 0f || bh <= 0f) return Offset.Zero
        val iw = imageSize.x
        val ih = imageSize.y
        val fitW: Float
        val fitH: Float
        if (iw > 0f && ih > 0f) {
            val fit = min(bw / iw, bh / ih)
            fitW = iw * fit
            fitH = ih * fit
        } else {
            fitW = bw
            fitH = bh
        }
        return Offset(
            max(0f, (fitW * forScale - bw) / 2f),
            max(0f, (fitH * forScale - bh) / 2f),
        )
    }

    fun clampOffset(o: Offset, forScale: Float): Offset {
        val m = maxOffset(forScale)
        return Offset(o.x.coerceIn(-m.x, m.x), o.y.coerceIn(-m.y, m.y))
    }

    fun publishZoom() {
        onZoomedChangeState.value(scale > 1.01f)
    }

    // жест щипка и перетаскивания: масштаб вокруг точки между пальцами, смещение всегда ограничено
    fun applyGesture(centroid: Offset, pan: Offset, zoom: Float) {
        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val realZoom = newScale / scale
        val c = centroid - center
        val raw = c * (1f - realZoom) + offset * realZoom + pan
        scale = newScale
        offset = if (newScale <= 1f) Offset.Zero else clampOffset(raw, newScale)
        publishZoom()
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val s0 = scale
        val o0 = offset
        scope.launch {
            animate(0f, 1f) { t, _ ->
                scale = s0 + (targetScale - s0) * t
                offset = if (scale <= 1f) Offset.Zero else clampOffset(o0 + (targetOffset - o0) * t, scale)
                publishZoom()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1.01f) {
                            animateTo(1f, Offset.Zero)
                        } else {
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val c = tap - center
                            val target = c * (1f - DOUBLE_TAP_SCALE) + offset * DOUBLE_TAP_SCALE
                            animateTo(DOUBLE_TAP_SCALE, clampOffset(target, DOUBLE_TAP_SCALE))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectZoomAndPan(
                    isZoomed = { scale > 1.01f },
                    onGesture = { centroid, pan, zoom -> applyGesture(centroid, pan, zoom) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(file.model)
                .size(2048, 2048)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            imageLoader = StreamImages.loader(ctx),
            contentScale = ContentScale.Fit,
            onState = { st ->
                painterState = st
                if (st is AsyncImagePainter.State.Success) {
                    val d = st.painter.intrinsicSize
                    if (d.width > 0f && d.height > 0f) imageSize = Offset(d.width, d.height)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
        // индикатор виден только пока фото грузится, поэтому за прозрачными областями ничего не просвечивает
        when (painterState) {
            is AsyncImagePainter.State.Loading -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
            is AsyncImagePainter.State.Error -> FileTypeBadge(FileKind.IMAGE, Modifier.padding(64.dp))
            else -> Unit
        }
    }

    // если страницу пролистали, возвращаем её в исходное состояние
    LaunchedEffect(isCurrent) {
        if (!isCurrentState.value) {
            scale = 1f
            offset = Offset.Zero
        }
    }
}

/**
 * Щипок и перетаскивание. Одним пальцем при масштабе 1x события не забираем,
 * чтобы пейджер мог листать фото свайпом.
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pastSlop = false
        var zoomAcc = 1f
        var panAcc = Offset.Zero
        val slop = viewConfiguration.touchSlop
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastSlop) {
                    zoomAcc *= zoomChange
                    panAcc += panChange
                    val size = event.calculateCentroidSize(useCurrent = false)
                    if (abs(1f - zoomAcc) * size > slop || panAcc.getDistance() > slop) pastSlop = true
                }
                if (pastSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    val multiTouch = event.changes.count { it.pressed } > 1
                    if (multiTouch || isZoomed()) {
                        onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
