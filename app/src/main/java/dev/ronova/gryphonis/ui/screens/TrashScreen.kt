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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Checkbox
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.components.ConfirmDialog
import dev.ronova.gryphonis.ui.components.FileThumb
import dev.ronova.gryphonis.util.formatDate
import dev.ronova.gryphonis.util.formatSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val items by vm.trashItems.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmForever by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshTrash() }
    // выбранное, чего в корзине уже нет (восстановили, истёк срок), из выбора убираем
    LaunchedEffect(items) { selected = selected.filter { id -> items.any { it.id == id } }.toSet() }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val chosen = items.filter { it.id in selected }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(selected.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, contentDescription = null) }
                    },
                    actions = {
                        IconButton(onClick = { selected = items.map { it.id }.toSet() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = null)
                        }
                        IconButton(onClick = {
                            vm.restoreTrash(chosen)
                            selected = emptySet()
                        }) { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null) }
                        IconButton(onClick = { confirmForever = true }) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_trash)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                    },
                    actions = {
                        if (items.isNotEmpty()) {
                            IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) }
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.trash_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.trash_auto_clean, settings.trashDays),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    Text(
                        stringResource(R.string.trash_auto_clean, settings.trashDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(items, key = { it.id }) { item ->
                    val isSel = item.id in selected
                    val toggle = {
                        selected = if (item.id in selected) selected - item.id else selected + item.id
                    }
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
                            .combinedClickable(onClick = toggle, onLongClick = toggle)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileThumb(item.asFileItem(), showThumbnail = false, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                item.originalPath.substringBeforeLast('/', ""),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.trash_row_info,
                                    formatSize(ctx, item.size),
                                    formatDate(item.deletedAt),
                                    item.daysLeft(settings.trashDays),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Checkbox(checked = isSel, onCheckedChange = null)
                    }
                }
            }
        }
    }

    if (confirmForever) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            message = stringResource(R.string.delete_message, chosen.size),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { confirmForever = false },
            onConfirm = {
                confirmForever = false
                vm.deleteTrashForever(chosen)
                selected = emptySet()
            },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_title),
            message = stringResource(R.string.delete_message, items.size),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { confirmEmpty = false },
            onConfirm = {
                confirmEmpty = false
                vm.deleteTrashForever(items)
                selected = emptySet()
            },
        )
    }
}
