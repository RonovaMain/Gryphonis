package dev.ronova.gryphonis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.AnalysisResult
import dev.ronova.gryphonis.data.Analyzer
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.components.FileThumb
import dev.ronova.gryphonis.ui.components.GradientFolderIcon
import dev.ronova.gryphonis.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

private fun kindColor(kind: FileKind): Color = when (kind) {
    FileKind.IMAGE -> Color(0xFF26A69A)
    FileKind.VIDEO -> Color(0xFFEF5350)
    FileKind.AUDIO -> Color(0xFFAB47BC)
    FileKind.TEXT -> Color(0xFF42A5F5)
    FileKind.ARCHIVE -> Color(0xFFFFA726)
    FileKind.PDF -> Color(0xFFE53935)
    FileKind.APK -> Color(0xFF66BB6A)
    FileKind.DOC -> Color(0xFF5C6BC0)
    else -> Color(0xFF90A4AE)
}

@Composable
private fun kindLabel(kind: FileKind): String = stringResource(
    when (kind) {
        FileKind.IMAGE -> R.string.kind_images
        FileKind.VIDEO -> R.string.kind_video
        FileKind.AUDIO -> R.string.kind_audio
        FileKind.TEXT -> R.string.kind_text
        FileKind.ARCHIVE -> R.string.kind_archives
        FileKind.PDF -> R.string.kind_pdf
        FileKind.APK -> R.string.kind_apk
        FileKind.DOC -> R.string.kind_docs
        else -> R.string.kind_other
    }
)

@Composable
fun AnalyzerScreen(vm: MainViewModel, path: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val root = remember(path) { File(path) }
    var scanned by remember(path) { mutableIntStateOf(0) }
    var result by remember(path) { mutableStateOf<AnalysisResult?>(null) }

    LaunchedEffect(path) {
        result = null
        scanned = 0
        result = withContext(Dispatchers.IO) {
            Analyzer.analyze(root, { isActive }) { scanned = it }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tool_analyzer))
                        Text(
                            root.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        val res = result
        if (res == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.analyzer_scanning, scanned), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                item {
                    Text(formatSize(ctx, res.totalBytes), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.analyzer_files, res.totalFiles),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    // сегментированная полоса по типам
                    val kinds = res.byKind.entries.filter { it.value.bytes > 0 }.sortedByDescending { it.value.bytes }
                    if (kinds.isNotEmpty() && res.totalBytes > 0) {
                        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                            for (e in kinds) {
                                Box(
                                    Modifier
                                        .weight((e.value.bytes.toFloat() / res.totalBytes).coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(kindColor(e.key))
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        for (e in kinds) {
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(kindColor(e.key)))
                                Spacer(Modifier.width(10.dp))
                                Text(kindLabel(e.key), modifier = Modifier.weight(1f))
                                Text(
                                    formatSize(ctx, e.value.bytes) + "  (" + e.value.count + ")",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                if (res.folders.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.analyzer_folders), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(res.folders, key = { "f:" + it.first.path }) { (folder, size) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    vm.openDir(folder)
                                    vm.pop()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GradientFolderIcon(Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth(if (res.totalBytes > 0) (size.toFloat() / res.totalBytes).coerceIn(0.02f, 1f) else 0.02f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(formatSize(ctx, size), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (res.largest.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.analyzer_largest), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(res.largest, key = { "l:" + it.path }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    item.file.parentFile?.let { vm.openDir(it) }
                                    vm.pop()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FileThumb(item, showThumbnail = false, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    item.file.parent ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(formatSize(ctx, item.size), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
