package dev.ronova.gryphonis.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.data.FileOps
import dev.ronova.gryphonis.data.SortBy
import dev.ronova.gryphonis.data.TransferManager
import dev.ronova.gryphonis.data.TransferState
import dev.ronova.gryphonis.data.toFileItem
import dev.ronova.gryphonis.util.StorageUtil
import dev.ronova.gryphonis.util.formatDate
import dev.ronova.gryphonis.util.formatEta
import dev.ronova.gryphonis.util.formatSize
import dev.ronova.gryphonis.util.OpenUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------------- progress

@Composable
fun TransferDialog(state: TransferState) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(state.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.error != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        if (state.current.isEmpty()) "…" else state.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.total > 0) {
                        LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val amount = if (state.bytesMode) {
                        formatSize(ctx, state.done) + " / " + formatSize(ctx, state.total)
                    } else {
                        "${state.done} / ${state.total}"
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(amount, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        if (state.total > 0) {
                            Text("${(state.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(
                        stringResource(R.string.eta_left, formatEta(ctx, state.etaSeconds)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.bytesMode && state.speed > 0) {
                        Text(
                            stringResource(R.string.speed_fmt, formatSize(ctx, state.speed)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.filesTotal > 1) {
                        Text(
                            stringResource(R.string.files_fmt, state.filesDone, state.filesTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.error != null) {
                TextButton(onClick = { TransferManager.dismissError() }) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = {
            if (state.error == null) {
                TextButton(onClick = { TransferManager.cancel() }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

// ---------------------------------------------------------------------- simple dialogs

@Composable
fun CreateDialog(onDismiss: () -> Unit, onCreate: (name: String, isFolder: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = folder, onClick = { folder = true }, label = { Text(stringResource(R.string.folder)) })
                    FilterChip(selected = !folder, onClick = { folder = false }, label = { Text(stringResource(R.string.file)) })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), folder) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PermissionDialog(onDismiss: () -> Unit, onGrant: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.perm_title)) },
        text = { Text(stringResource(R.string.perm_message)) },
        confirmButton = {
            TextButton(onClick = {
                onGrant()
                onDismiss()
            }) { Text(stringResource(R.string.perm_grant)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun SortDialog(
    sortBy: SortBy,
    ascending: Boolean,
    foldersFirst: Boolean,
    onSortBy: (SortBy) -> Unit,
    onAscending: (Boolean) -> Unit,
    onFoldersFirst: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        SortBy.NAME to R.string.sort_name,
        SortBy.DATE to R.string.sort_date,
        SortBy.TYPE to R.string.sort_type,
        SortBy.SIZE to R.string.sort_size,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_title)) },
        text = {
            Column {
                for ((opt, label) in options) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSortBy(opt) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sortBy == opt, onClick = { onSortBy(opt) })
                        Text(stringResource(label))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchLine(stringResource(R.string.sort_ascending), ascending, onAscending)
                SwitchLine(stringResource(R.string.sort_folders_first), foldersFirst, onFoldersFirst)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun ZipChoiceDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onExtractFolder: () -> Unit,
    onExtractHere: () -> Unit,
    onOpenWith: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(onClick = onExtractFolder) { Text(stringResource(R.string.extract_folder)) }
                TextButton(onClick = onExtractHere) { Text(stringResource(R.string.extract_here)) }
                TextButton(onClick = onOpenWith) { Text(stringResource(R.string.open_with)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun HashDialog(name: String, hash: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SHA-256") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(hash, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(hash)) }) { Text(stringResource(R.string.copy)) }
        },
    )
}

@Composable
fun PropertiesDialog(item: FileItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val stats by produceState<FileOps.Stats?>(null, item.path) {
        value = withContext(Dispatchers.IO) { FileOps.scan(item.file) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PropLine(stringResource(R.string.prop_path), item.file.parent ?: "/")
                PropLine(
                    stringResource(R.string.prop_type),
                    if (item.isDirectory) stringResource(R.string.folder) else OpenUtil.mimeOf(item.file),
                )
                val s = stats
                PropLine(
                    stringResource(R.string.prop_size),
                    if (s == null) "…" else formatSize(ctx, s.bytes),
                )
                if (item.isDirectory) {
                    PropLine(stringResource(R.string.prop_files), if (s == null) "…" else s.files.toString())
                }
                PropLine(stringResource(R.string.prop_modified), formatDate(item.modified))
                PropLine(
                    stringResource(R.string.prop_access),
                    (if (item.file.canRead()) "R" else "-") + (if (item.file.canWrite()) "W" else "-"),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun PropLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------- local picker

/**
 * Полноэкранный выбор на телефоне.
 * folderMode = true  → выбирается текущая папка;
 * folderMode = false → отмечаются файлы и папки (для отправки на FTP).
 */
@Composable
fun LocalPickerDialog(
    startDir: File,
    folderMode: Boolean,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onPicked: (List<File>) -> Unit,
) {
    val ctx = LocalContext.current
    val roots = remember { StorageUtil.roots(ctx) }
    var dir by remember { mutableStateOf(if (startDir.isDirectory) startDir else StorageUtil.internalRoot()) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val entries = remember(dir) {
        val all = dir.listFiles()?.map { it.toFileItem() } ?: emptyList()
        all.filter { !it.isHidden && (!folderMode || it.isDirectory) }
            .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }
    val atRoot = roots.any { it.dir == dir }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { dir.parentFile?.let { dir = it } },
                        enabled = !atRoot && dir.parentFile != null,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            dir.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.path }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.isDirectory) {
                                        dir = item.file
                                    } else if (!folderMode) {
                                        picked = if (item.path in picked) picked - item.path else picked + item.path
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FileThumb(item, showThumbnail = false, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!item.isDirectory) {
                                    Text(
                                        formatSize(ctx, item.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!folderMode) {
                                Checkbox(
                                    checked = item.path in picked,
                                    onCheckedChange = {
                                        picked = if (item.path in picked) picked - item.path else picked + item.path
                                    },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    if (folderMode) {
                        Button(onClick = { onPicked(listOf(dir)) }) { Text(confirmLabel) }
                    } else {
                        Button(
                            onClick = { onPicked(picked.map { File(it) }) },
                            enabled = picked.isNotEmpty(),
                        ) { Text("$confirmLabel (${picked.size})") }
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}
