package dev.ronova.gryphonis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.Keys
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.util.StorageRoot
import dev.ronova.gryphonis.util.StorageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Настройка «Быстрого доступа» на главном экране: включить/выключить, выбрать любые папки,
 * расставить их по порядку (стрелки вверх/вниз), убрать лишние и вернуть стандартные.
 */
@Composable
fun QuickAccessScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    val paths = s.quickFolders ?: remember { StorageUtil.defaultQuickFolders().map { it.absolutePath } }
    // стандартные папки, которых сейчас нет в списке — их можно вернуть одним нажатием
    val missingDefaults = remember(paths) {
        StorageUtil.defaultQuickFolders().filter { it.absolutePath !in paths }
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in paths.indices) return
        val list = paths.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        vm.setQuickFolders(list)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_quick_folders)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { vm.setQuickFolders(null) }, enabled = s.quickFolders != null) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.quick_reset))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setPref(Keys.QUICK_ENABLED, !s.quickAccess) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_quick_show), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.set_quick_show_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.quickAccess, onCheckedChange = { vm.setPref(Keys.QUICK_ENABLED, it) })
                }
                HorizontalDivider()
            }

            if (paths.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.quick_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }

            items(paths.size, key = { paths[it] }) { index ->
                val path = paths[index]
                val file = File(path)
                val exists = file.isDirectory
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp),
                    )
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = if (s.quickAccess && exists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name.ifEmpty { path },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            if (exists) path else stringResource(R.string.quick_missing),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (exists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.quick_move_up))
                    }
                    IconButton(onClick = { move(index, 1) }, enabled = index < paths.lastIndex) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.quick_move_down))
                    }
                    IconButton(onClick = { vm.setQuickFolders(paths - path) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.quick_remove))
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { picking = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.quick_add_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (missingDefaults.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.quick_suggest),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                    )
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (f in missingDefaults) {
                            AssistChip(
                                onClick = { vm.setQuickFolders(paths + f.absolutePath) },
                                label = { Text(f.name) },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }

    if (picking) {
        FolderPickerDialog(
            roots = roots,
            onDismiss = { picking = false },
            onPick = { dir ->
                picking = false
                if (dir.absolutePath !in paths) vm.setQuickFolders(paths + dir.absolutePath)
            },
        )
    }
}

/** Выбор папки внутри приложения: заходим вглубь, «Выбрать» берёт открытую папку. */
@Composable
private fun FolderPickerDialog(roots: List<StorageRoot>, onDismiss: () -> Unit, onPick: (File) -> Unit) {
    var cur by remember { mutableStateOf<File?>(null) }
    val children by produceState<List<File>>(emptyList(), cur, roots) {
        val c = cur
        value = if (c == null) {
            roots.map { it.dir }
        } else {
            withContext(Dispatchers.IO) {
                (c.listFiles() ?: emptyArray<File>())
                    .filter { it.isDirectory && !it.name.startsWith(".") }
                    .sortedBy { it.name.lowercase() }
            }
        }
    }
    fun label(f: File): String = roots.firstOrNull { it.dir == f }?.name ?: f.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_pick_title)) },
        text = {
            Column {
                val here = cur
                if (here != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val parent = here.parentFile
                                cur = if (roots.any { it.dir == here } || parent == null) null else parent
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SubdirectoryArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.quick_pick_up), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                here.absolutePath,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
                if (children.isEmpty()) {
                    Text(
                        stringResource(R.string.quick_pick_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(children, key = { it.absolutePath }) { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { cur = f }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Text(label(f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { cur?.let(onPick) }, enabled = cur != null) { Text(stringResource(R.string.quick_pick_here)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
