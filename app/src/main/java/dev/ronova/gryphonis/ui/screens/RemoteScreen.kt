package dev.ronova.gryphonis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import dev.ronova.gryphonis.data.FtpProfile
import dev.ronova.gryphonis.data.FtpProfiles
import dev.ronova.gryphonis.data.kindOf
import dev.ronova.gryphonis.ftp.RemoteItem
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.RemoteViewModel
import dev.ronova.gryphonis.ui.components.ConfirmDialog
import dev.ronova.gryphonis.ui.components.FileTypeBadge
import dev.ronova.gryphonis.ui.components.FolderIcon
import dev.ronova.gryphonis.ui.components.LocalPickerDialog
import dev.ronova.gryphonis.ui.components.TextInputDialog
import dev.ronova.gryphonis.util.StorageUtil
import dev.ronova.gryphonis.util.formatDate
import dev.ronova.gryphonis.util.formatSize
import java.io.File

private fun effectivePassword(user: String, pass: String): String =
    if (pass.isEmpty() && (user.isEmpty() || user == "anonymous")) "anonymous@" else pass

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rvm: RemoteViewModel = viewModel()
    val ctx = LocalContext.current
    val connected by rvm.connected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val localClip by vm.clipboard.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { rvm.events.collect { snackbar.showSnackbar(it) } }

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FtpProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<FtpProfile?>(null) }
    var showMkdir by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteItem?>(null) }
    var pickDownload by remember { mutableStateOf(false) }
    var pickUpload by remember { mutableStateOf(false) }

    val editorOpen = creating || editing != null

    LaunchedEffect(connected) {
        if (connected) {
            creating = false
            editing = null
        }
    }

    BackHandler(enabled = connected || editorOpen) {
        when {
            !connected && editorOpen -> {
                creating = false
                editing = null
            }
            rvm.selected.isNotEmpty() -> rvm.clearSelection()
            !rvm.up() -> onBack()
        }
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
                        IconButton(onClick = { rvm.copySelected() }) { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }
                        IconButton(onClick = { pickDownload = true }) { Icon(Icons.Rounded.CloudDownload, contentDescription = null) }
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
                            Text(
                                when {
                                    !connected && editing != null -> stringResource(R.string.ftp_edit_connection)
                                    !connected && creating -> stringResource(R.string.ftp_new_connection)
                                    else -> stringResource(R.string.tool_ftp_client)
                                }
                            )
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
                        IconButton(onClick = {
                            if (!connected && editorOpen) {
                                creating = false
                                editing = null
                            } else {
                                onBack()
                            }
                        }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
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
            val clip = localClip
            if (connected && clip != null && rvm.selected.isEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.ftp_paste_here_n, clip.files.size),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.clearClipboard() }) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            rvm.upload(clip.files, moveAfter = clip.cut)
                            if (clip.cut) vm.clearClipboard()
                        }) { Text(stringResource(R.string.paste)) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !connected && editorOpen -> {
                    val initial = editing
                    ProfileEditor(
                        initial = initial,
                        initialPassword = if (initial != null) FtpProfiles.password(initial) else "",
                        connecting = rvm.connecting,
                        error = rvm.error,
                        onConnect = { profile, plain, save ->
                            rvm.connect(profile, effectivePassword(profile.user, plain), save)
                        },
                        onSave = { profile, plain ->
                            rvm.saveProfile(profile, plain)
                            creating = false
                            editing = null
                        },
                    )
                }
                !connected -> {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.remote_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { creating = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ftp_new_connection))
                        }
                        if (rvm.connecting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.ftp_connecting))
                            }
                        }
                        rvm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (settings.ftpProfiles.isNotEmpty()) {
                            Text(
                                stringResource(R.string.ftp_saved),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        for (p in settings.ftpProfiles) {
                            Card(
                                onClick = {
                                    rvm.connect(p, effectivePassword(p.user, FtpProfiles.password(p)), false)
                                },
                                enabled = !rvm.connecting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            (if (p.user.isEmpty()) "" else p.user + "@") + p.host + ":" + p.port + (if (p.path == "/") "" else "  " + p.path),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = { editing = p }) { Icon(Icons.Rounded.Edit, contentDescription = null) }
                                    IconButton(onClick = { deleteProfile = p }) { Icon(Icons.Rounded.Delete, contentDescription = null) }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        if (rvm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        PullToRefreshBox(
                            isRefreshing = rvm.refreshing,
                            onRefresh = { rvm.pullRefresh() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                if (rvm.items.isEmpty() && !rvm.loading) {
                                    item {
                                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(stringResource(R.string.folder_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                items(rvm.items, key = { it.path }) { item ->
                                    val isSel = item.path in rvm.selected
                                    val anySel = rvm.selected.isNotEmpty()
                                    Row(
                                        Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    when {
                                                        anySel -> rvm.toggle(item)
                                                        item.isDir -> rvm.open(item.path)
                                                        else -> vm.openRemoteFile(item, rvm.items)
                                                    }
                                                },
                                                onLongClick = { rvm.toggle(item) },
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (item.isDir) {
                                            FolderIcon(Modifier.size(40.dp))
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
    deleteProfile?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.ftp_delete_title),
            message = stringResource(R.string.ftp_delete_message, p.title),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleteProfile = null },
            onConfirm = {
                deleteProfile = null
                rvm.deleteProfile(p.id)
            },
        )
    }
    if (pickDownload) {
        val start = vm.currentDir.value ?: File(StorageUtil.internalRoot(), "Download").takeIf { it.isDirectory } ?: StorageUtil.internalRoot()
        LocalPickerDialog(
            startDir = start,
            folderMode = true,
            title = stringResource(R.string.remote_download_to),
            confirmLabel = stringResource(R.string.choose_this_folder),
            onDismiss = { pickDownload = false },
            onPicked = {
                pickDownload = false
                it.firstOrNull()?.let { f -> if (vm.ensureAccess()) rvm.download(f) }
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

@Composable
private fun ProfileEditor(
    initial: FtpProfile?,
    initialPassword: String,
    connecting: Boolean,
    error: String?,
    onConnect: (FtpProfile, String, Boolean) -> Unit,
    onSave: (FtpProfile, String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var host by remember(initial) { mutableStateOf(initial?.host ?: "") }
    var portText by remember(initial) { mutableStateOf((initial?.port ?: 21).toString()) }
    var user by remember(initial) { mutableStateOf(initial?.user ?: "") }
    var pass by remember(initial) { mutableStateOf(initialPassword) }
    var path by remember(initial) { mutableStateOf(initial?.path ?: "/") }
    var save by remember(initial) { mutableStateOf(true) }

    fun build(): FtpProfile {
        val u = user.trim().ifEmpty { "anonymous" }
        var p = path.trim().ifEmpty { "/" }
        if (!p.startsWith("/")) p = "/$p"
        return FtpProfile(
            id = initial?.id ?: FtpProfiles.newId(),
            name = name.trim(),
            host = host.trim(),
            port = portText.toIntOrNull() ?: 21,
            user = u,
            secret = "",
            path = p,
        )
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.ftp_profile_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text(stringResource(R.string.ftp_start_folder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().clickable { save = !save },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = save, onCheckedChange = { save = it })
            Text(stringResource(R.string.ftp_save_connection))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onConnect(build(), pass, save) },
                enabled = host.isNotBlank() && !connecting,
            ) { Text(stringResource(R.string.connect)) }
            OutlinedButton(
                onClick = { onSave(build(), pass) },
                enabled = host.isNotBlank() && !connecting,
            ) { Text(stringResource(R.string.save)) }
            if (connecting) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
