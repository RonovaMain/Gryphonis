package dev.ronova.gryphonis.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.kindOf
import dev.ronova.gryphonis.media.MusicPlayer
import kotlinx.coroutines.delay
import java.io.File

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(s: Float): String = if (s == s.toInt().toFloat()) "${s.toInt()}×" else "$s×"

/**
 * Видео прямо в проводнике. Если формат не нравится или не поддерживается —
 * в верхней панели и на экране ошибки есть кнопка «Открыть в другом приложении».
 */
@Composable
fun VideoPlayerScreen(startPath: String, onBack: () -> Unit, onOpenWith: (File) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(ctx) { ctx.findActivity() }

    val playlist = remember(startPath) {
        val start = File(startPath)
        start.parentFile?.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && kindOf(false, it.extension.lowercase()) == FileKind.VIDEO }
            ?.sortedWith(compareBy<File, String>(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(start)
    }
    var index by remember(startPath) { mutableIntStateOf(playlist.indexOfFirst { it.path == startPath }.coerceAtLeast(0)) }
    val file = playlist[index.coerceIn(0, playlist.lastIndex)]

    val exo = remember {
        ExoPlayer.Builder(ctx)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var fill by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var menuOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintId by remember { mutableIntStateOf(0) }

    fun seekBy(delta: Long) {
        val max = if (duration > 0) duration else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + delta).coerceIn(0L, max))
    }

    fun togglePlay() {
        if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0L)
            exo.play()
        } else if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
    }

    // ---- слушатель плеера и освобождение
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exo.duration.coerceAtLeast(0L)
                    failed = false
                }
                if (state == Player.STATE_ENDED) {
                    if (index < playlist.lastIndex) {
                        index += 1
                    } else {
                        controlsVisible = true
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                buffering = false
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // ---- пауза при уходе приложения в фон
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exo.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- полноэкранный режим (скрываем системные панели), возвращаем всё при выходе
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // музыка и видео одновременно не играют
    LaunchedEffect(Unit) { MusicPlayer.pause() }

    LaunchedEffect(index) {
        failed = false
        buffering = true
        position = 0L
        duration = 0L
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        exo.setPlaybackSpeed(speed)
        exo.playWhenReady = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) position = exo.currentPosition
            delay(250)
        }
    }

    // автоскрытие панели управления
    LaunchedEffect(controlsVisible, isPlaying, interaction, menuOpen) {
        if (controlsVisible && isPlaying && !menuOpen) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(hintId) {
        if (hint != null) {
            delay(700)
            hint = null
        }
    }

    val scrimTop = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))
    val scrimBottom = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))
    val sliderMax = if (duration > 0) duration.toFloat() else 1f

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    this.player = exo
                }
            },
            update = { v ->
                v.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                v.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize(),
        )

        // слой жестов: тап — панель, двойной тап слева/справа — ±10 секунд
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interaction++
                        },
                        onDoubleTap = { offset ->
                            val back = offset.x < size.width / 2f
                            seekBy(if (back) -10_000L else 10_000L)
                            hint = if (back) "−10s" else "+10s"
                            hintId++
                            interaction++
                        },
                    )
                }
        )

        if (buffering && !failed && !controlsVisible) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        hint?.let {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium) }
        }

        // ---- экран ошибки
        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.video_error),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = { onOpenWith(file) }) { Text(stringResource(R.string.open_with)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back), color = Color.White) }
            }
        }

        // ---- панель управления
        AnimatedVisibility(
            visible = controlsVisible && !failed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // верх: назад, имя файла, открыть в другом приложении, меню
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(scrimTop)
                        .displayCutoutPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Text(
                        file.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        exo.pause()
                        onOpenWith(file)
                    }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = stringResource(R.string.open_with), tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text(
                                stringResource(R.string.video_speed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            for (s in SPEEDS) {
                                DropdownMenuItem(
                                    text = { Text(speedLabel(s)) },
                                    trailingIcon = {
                                        if (speed == s) Icon(Icons.Rounded.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        speed = s
                                        exo.setPlaybackSpeed(s)
                                        menuOpen = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(if (fill) R.string.video_fit else R.string.video_fill)) },
                                leadingIcon = { Icon(Icons.Rounded.AspectRatio, contentDescription = null) },
                                onClick = {
                                    fill = !fill
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }

                // центр: предыдущее, -10, play/pause, +10, следующее
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playlist.size > 1) {
                        IconButton(onClick = { if (index > 0) index -= 1 }, enabled = index > 0) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                    IconButton(onClick = { seekBy(-10_000L) }) {
                        Icon(Icons.Rounded.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (buffering) {
                            CircularProgressIndicator(Modifier.size(56.dp), color = Color.White)
                        }
                        IconButton(onClick = { togglePlay() }, modifier = Modifier.size(72.dp)) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                    IconButton(onClick = { seekBy(10_000L) }) {
                        Icon(Icons.Rounded.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    if (playlist.size > 1) {
                        IconButton(onClick = { if (index < playlist.lastIndex) index += 1 }, enabled = index < playlist.lastIndex) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                }

                // низ: перемотка, время, поворот экрана
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(scrimBottom)
                        .displayCutoutPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Slider(
                        value = if (dragging) dragValue else position.toFloat().coerceIn(0f, sliderMax),
                        onValueChange = {
                            dragging = true
                            dragValue = it
                            interaction++
                        },
                        onValueChangeFinished = {
                            exo.seekTo(dragValue.toLong())
                            position = dragValue.toLong()
                            dragging = false
                        },
                        valueRange = 0f..sliderMax,
                        enabled = duration > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(if (dragging) dragValue.toLong() else position) + " / " + formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.weight(1f))
                        if (playlist.size > 1) {
                            Text(
                                "${index + 1} / ${playlist.size}",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = {
                            landscape = !landscape
                            activity?.requestedOrientation =
                                if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            interaction++
                        }) {
                            Icon(
                                Icons.Rounded.ScreenRotation,
                                contentDescription = stringResource(R.string.rotate),
                                tint = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
