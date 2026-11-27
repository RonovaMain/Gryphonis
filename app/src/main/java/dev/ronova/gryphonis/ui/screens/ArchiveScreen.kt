package dev.ronova.gryphonis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.ArchiveEntry
import dev.ronova.gryphonis.data.ArchiveIndex
import dev.ronova.gryphonis.data.ArchiveOps
import dev.ronova.gryphonis.data.kindOf
import dev.ronova.gryphonis.data.toFileItem
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.components.FileTypeBadge
import dev.ronova.gryphonis.ui.components.FolderIcon
import dev.ronova.gryphonis.ui.components.LocalPickerDialog
import dev.ronova.gryphonis.util.StorageUtil
import dev.ronova.gryphonis.util.formatDate
import dev.ronova.gryphonis.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Просмотр ZIP без распаковки: заходим в папки архива, отмечаем нужные файлы и либо копируем их
 * (потом закрываем архив и вставляем в любую папку), либо сразу извлекаем в выбранное место.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(vm: MainViewModel, zipPath: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val zip = remember(zipPath) { File(zipPath) }
    var index by remember(zipPath) { mutableStateOf<ArchiveIndex?>(null) }
    var error by remember(zipPath) { mutableStateOf<String?>(null) }
    var dir by remember(zipPath) { mutableStateOf("") }
    var selected by remember(zipPath) { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickDest by remember { mutableStateOf(false) }

    LaunchedEffect(zipPath) {
        try {
            index = withContext(Dispatchers.IO) { ArchiveOps.readIndex(zip) }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    val entries = remember(index, dir) {
        (index?.children(dir) ?: emptyList())
            .sortedWith(compareBy<ArchiveEntry> { !it.isDir }.thenBy { it.name.lowercase() })
    }

    fun goBack() {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            dir.isNotEmpty() -> dir = dir.substringBeforeLast('/', "")
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
                        IconButton(onClick = { selected = entries.map { it.path }.toSet() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = null)
                        }
                        IconButton(onClick = {
                            vm.copyFromArchive(zip, selected.toList())
                            selected = emptySet()
                        }) { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }
                        IconButton(onClick = { pickDest = true }) { Icon(Icons.Rounded.Unarchive, contentDescription = null) }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(zip.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "/" + dir,
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
                    actions = {
                        // быстрый выход из архива, не поднимаясь по папкам
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = null) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.extract_here)) },
                                    leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.unzip(zip.toFileItem(), here = true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.extract_folder)) },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.unzip(zip.toFileItem(), here = false)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        val err = error
        when {
            err != null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(err, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
            index == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            entries.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.folder_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(entries, key = { it.path }) { e ->
                    val isSel = e.path in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    when {
                                        selected.isNotEmpty() -> toggle(e.path)
                                        e.isDir -> dir = e.path
                                        else -> vm.openArchiveEntry(zip, e, entries)
                                    }
                                },
                                onLongClick = { toggle(e.path) },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (e.isDir) {
                            FolderIcon(Modifier.size(40.dp))
                        } else {
                            FileTypeBadge(kindOf(false, e.name.substringAfterLast('.', "").lowercase()), Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = if (e.isDir) formatDate(e.modified) else formatSize(ctx, e.size) + "  •  " + formatDate(e.modified)
                            if (sub.isNotBlank()) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (selected.isNotEmpty()) Checkbox(checked = isSel, onCheckedChange = null)
                    }
                }
            }
        }
    }

    if (pickDest) {
        LocalPickerDialog(
            startDir = zip.parentFile ?: StorageUtil.internalRoot(),
            folderMode = true,
            title = stringResource(R.string.archive_extract_to),
            confirmLabel = stringResource(R.string.choose_this_folder),
            onDismiss = { pickDest = false },
            onPicked = {
                pickDest = false
                val target = it.firstOrNull()
                if (target != null) {
                    vm.extractFromArchive(zip, selected.toList(), target)
                    selected = emptySet()
                }
            },
        )
    }
}
