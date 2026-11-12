package dev.ronova.gryphonis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.kindOf
import dev.ronova.gryphonis.ui.components.FileTypeBadge
import dev.ronova.gryphonis.util.OpenUtil
import java.io.File

@Composable
fun ImageViewerScreen(startPath: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val images = remember(startPath) {
        val start = File(startPath)
        val siblings = start.parentFile?.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && kindOf(false, it.extension.lowercase()) == FileKind.IMAGE }
            ?.sortedBy { it.name.lowercase() }
            ?: listOf(start)
        if (siblings.isEmpty()) listOf(start) else siblings
    }
    var index by remember(startPath) { mutableIntStateOf(images.indexOfFirst { it.path == startPath }.coerceAtLeast(0)) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    val file = images[index]

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
                actions = {
                    if (images.size > 1) Text("${index + 1} / ${images.size}", modifier = Modifier.padding(end = 4.dp))
                    IconButton(onClick = { OpenUtil.share(ctx, listOf(file)) }) { Icon(Icons.Rounded.Share, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(index) {
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    })
                }
                .pointerInput(index) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            FileTypeBadge(FileKind.IMAGE, Modifier.padding(64.dp))
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(file).crossfade(true).build(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
            if (images.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                ) {
                    IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.rotate(180f),
                        )
                    }
                    IconButton(onClick = { if (index < images.lastIndex) index++ }, enabled = index < images.lastIndex) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}
