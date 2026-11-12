package dev.ronova.gryphonis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.ftp.RemoteItem
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.RemoteViewModel
import dev.ronova.gryphonis.ui.components.ConfirmDialog
import dev.ronova.gryphonis.ui.components.FileTypeBadge
import dev.ronova.gryphonis.ui.components.GradientFolderIcon
import dev.ronova.gryphonis.ui.components.LocalPickerDialog
import dev.ronova.gryphonis.ui.components.TextInputDialog
import dev.ronova.gryphonis.data.kindOf
import dev.ronova.gryphonis.util.StorageUtil
import dev.ronova.gryphonis.util.formatDate
import dev.ronova.gryphonis.util.formatSize
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rvm: RemoteViewModel = viewModel()
    val ctx = LocalContext.current
    val connected by rvm.connected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { rvm.events.collect { snackbar.showSnackbar(it) } }

    var host by remember(settings.remoteHost) { mutableStateOf(settings.remoteHost) }
    var portText by remember(settings.remotePort) { mutableStateOf(settings.remotePort.toString()) }
    var user by remember(settings.remoteUser) { mutableStateOf(settings.remoteUser) }
    var pass by remember { mutableStateOf("") }

    var showMkdir by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteItem?>(null) }
    var pickDownload by remember { mutableStateOf(false) }
    var pickUpload by remember { mutableStateOf(false) }

    // куда скачивать по умолчанию — текущая папка проводника или Download
    val defaultDownload = remember {
        vm.currentDir.value ?: File(StorageUtil.internalRoot(), "Download").takeIf { it.isDirectory } ?: StorageUtil.internalRoot()
    }
    var downloadDir by remember { mutableStateOf(defaultDownload) }

    BackHandler(enabled = connected) {
        if (rvm.selected.isNotEmpty()) rvm.clearSelection() else if (!rvm.up()) onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (connected && rvm.selected.isNotEmpty()) {
                val one = rvm.items.firstOrNull { it.path in rvm.selected }.takeIf { rvm.selected.size == 1 }
                TopAppBar(
                    title = { Text(rvm.selected.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { rvm.clearSelection() }) { Icon(Icons.Rounded.Close, contentDescription = null) }
                    },
                    actions = {
                        IconButton(onClick = { rvm.selectAll() }) { Icon(Icons.Rounded.SelectAll, contentDescription = null) }
                        IconButton(onClick = { rvm.download(downloadDir) }) { Icon(Icons.Rounded.CloudDownload, contentDescription = null) }
                        if (one != null) {
                            IconButton(onClick = { renameTarget = one }) { Icon(Icons.Rounded.Edit, contentDescription = null) }
                        }
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = null) }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.tool_ftp_client))
                            if (connected) {
                                Text(
                                    rvm.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                    },
                    actions = {
                        if (connected) {
                            IconButton(onClick = { pickUpload = true }) { Icon(Icons.Rounded.CloudUpload, contentDescription = null) }
                            IconButton(onClick = { showMkdir = true }) { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) }
                            IconButton(onClick = { rvm.refresh() }) { Icon(Icons.Rounded.Refresh, contentDescription = null) }
                            IconButton(onClick = { rvm.disconnect() }) { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (connected) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.remote_download_to), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(downloadDir.absolutePath, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { pickDownload = true }) { Text(stringResource(R.string.change)) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!connected) {
                Column(
                    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.remote_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.remote_host)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.ftp_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text(stringResource(R.string.ftp_user)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text(stringResource(R.string.ftp_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    rvm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                val p = portText.toIntOrNull() ?: 21
                                val u = user.trim().ifEmpty { "anonymous" }
                                val pw = if (pass.isEmpty() && u == "anonymous") "anonymous@" else pass
                                rvm.connect(host, p, u, pw)
                            },
                            enabled = host.isNotBlank() && !rvm.connecting,
                        ) { Text(stringResource(R.string.connect)) }
                        if (rvm.connecting) {
                            Spacer(Modifier.width(16.dp))
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (rvm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (rvm.items.isEmpty() && !rvm.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.folder_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(rvm.items, key = { it.path }) { item ->
                                val isSel = item.path in rvm.selected
                                val anySel = rvm.selected.isNotEmpty()
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (anySel || !item.isDir) rvm.toggle(item) else rvm.open(item.path)
                                            },
                                            onLongClick = { rvm.toggle(item) },
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (item.isDir) {
                                        GradientFolderIcon(Modifier.size(40.dp))
                                    } else {
                                        FileTypeBadge(kindOf(false, item.name.substringAfterLast('.', "").lowercase()), Modifier.size(40.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val sub = if (item.isDir) formatDate(item.modified)
                                        else formatSize(ctx, item.size) + "  •  " + formatDate(item.modified)
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (anySel) Checkbox(checked = isSel, onCheckedChange = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMkdir) {
        TextInputDialog(
            title = stringResource(R.string.create_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showMkdir = false },
            onConfirm = {
                showMkdir = false
                rvm.mkdir(it)
            },
        )
    }
    renameTarget?.let { target ->
        TextInputDialog(
            title = stringResource(R.string.menu_rename),
            initial = target.name,
            confirmLabel = stringResource(R.string.ok),
            onDismiss = { renameTarget = null },
            onConfirm = {
                renameTarget = null
                rvm.rename(target, it)
            },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            message = stringResource(R.string.delete_message, rvm.selected.size),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                rvm.deleteSelected()
            },
        )
    }
    if (pickDownload) {
        LocalPickerDialog(
            startDir = downloadDir,
            folderMode = true,
            title = stringResource(R.string.remote_download_to),
            confirmLabel = stringResource(R.string.choose_this_folder),
            onDismiss = { pickDownload = false },
            onPicked = {
                pickDownload = false
                it.firstOrNull()?.let { f -> downloadDir = f }
            },
        )
    }
    if (pickUpload) {
        LocalPickerDialog(
            startDir = vm.currentDir.value ?: StorageUtil.internalRoot(),
            folderMode = false,
            title = stringResource(R.string.remote_upload_pick),
            confirmLabel = stringResource(R.string.upload),
            onDismiss = { pickUpload = false },
            onPicked = {
                pickUpload = false
                if (vm.ensureAccess()) rvm.upload(it)
            },
        )
    }
}
