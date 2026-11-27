package dev.ronova.gryphonis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.AnalysisResult
import dev.ronova.gryphonis.data.Analyzer
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.components.DeleteDialog
import dev.ronova.gryphonis.ui.components.FileThumb
import dev.ronova.gryphonis.ui.components.FolderIcon
import dev.ronova.gryphonis.ui.components.kindAccent
import dev.ronova.gryphonis.ui.theme.GryphTheme
import androidx.compose.ui.graphics.lerp
import dev.ronova.gryphonis.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Цвет типа в диаграмме. В фирменной теме — свой цвет у каждого типа, в однотонных — оттенки
 * одного акцентного цвета (светлее/темнее), чтобы полоса читалась, но «радуги» не было.
 */
@Composable
private fun kindColor(kind: FileKind): Color {
    if (GryphTheme.extras.rainbow) return kindAccent(kind)
    val base = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surface
    val steps = when (kind) {
        FileKind.IMAGE -> 0.00f
        FileKind.VIDEO -> 0.22f
        FileKind.AUDIO -> 0.44f
        FileKind.TEXT -> 0.62f
        FileKind.ARCHIVE -> -0.20f
        FileKind.PDF -> -0.38f
        FileKind.APK -> 0.78f
        FileKind.DOC -> -0.55f
        else -> 0f
    }
    return when {
        kind == FileKind.OTHER || kind == FileKind.FOLDER -> MaterialTheme.colorScheme.outline
        steps >= 0f -> lerp(base, bg, steps * 0.75f)
        else -> lerp(base, Color.Black, -steps)
    }
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

/**
 * Анализ места. Можно заходить в подпапки (анализ продолжается внутри), а долгим нажатием
 * выбирать папки и файлы и удалять их прямо отсюда.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnalyzerScreen(vm: MainViewModel, path: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val version by vm.fsVersion.collectAsStateWithLifecycle()

    var current by remember(path) { mutableStateOf(File(path)) }
    val stack = remember(path) { mutableStateListOf<File>() }
    var scanned by remember(path) { mutableIntStateOf(0) }
    var result by remember(path) { mutableStateOf<AnalysisResult?>(null) }
    var selected by remember(path) { mutableStateOf(setOf<String>()) }
    var deleteTargets by remember { mutableStateOf<List<File>?>(null) }

    // перечитываем при заходе в папку и после любого удаления/копирования
    LaunchedEffect(current, version) {
        result = null
        scanned = 0
        result = withContext(Dispatchers.IO) {
            Analyzer.analyze(current, { isActive }) { scanned = it }
        }
    }

    fun goInto(folder: File) {
        stack.add(current)
        current = folder
        selected = emptySet()
    }

    fun goBack() {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            stack.isNotEmpty() -> {
                current = stack.removeAt(stack.lastIndex)
                selected = emptySet()
            }
            else -> onBack()
        }
    }

    fun toggle(p: String) {
        selected = if (p in selected) selected - p else selected + p
    }

    BackHandler { goBack() }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(selected.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, contentDescription = null) }
                    },
                    actions = {
                        IconButton(onClick = { deleteTargets = selected.map { File(it) } }) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.tool_analyzer))
                            Text(
                                current.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                    },
                )
            }
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
            val anySelected = selected.isNotEmpty()
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
                        Text(
                            stringResource(R.string.analyzer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(res.folders, key = { "f:" + it.first.path }) { (folder, size) ->
                        val isSel = folder.absolutePath in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { if (anySelected) toggle(folder.absolutePath) else goInto(folder) },
                                    onLongClick = { toggle(folder.absolutePath) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FolderIcon(Modifier.size(36.dp))
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
                            if (anySelected) {
                                Checkbox(checked = isSel, onCheckedChange = null)
                            } else {
                                IconButton(onClick = {
                                    vm.openDir(folder)
                                    vm.pop()
                                }) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (res.largest.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.analyzer_largest), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(res.largest, key = { "l:" + it.path }) { item ->
                        val isSel = item.path in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { if (anySelected) toggle(item.path) else vm.openFile(item.file) },
                                    onLongClick = { toggle(item.path) },
                                )
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
                            if (anySelected) {
                                Checkbox(checked = isSel, onCheckedChange = null)
                            } else {
                                IconButton(onClick = {
                                    item.file.parentFile?.let { vm.openDir(it) }
                                    vm.pop()
                                }) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTargets?.let { targets ->
        DeleteDialog(
            files = targets,
            retentionDays = settings.trashDays,
            onDismiss = { deleteTargets = null },
            onConfirm = { permanent ->
                deleteTargets = null
                selected = emptySet()
                vm.deleteFiles(targets, permanent)
            },
        )
    }
}
