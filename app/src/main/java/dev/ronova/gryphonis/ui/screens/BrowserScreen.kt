package dev.ronova.gryphonis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.Keys
import dev.ronova.gryphonis.ftp.RemoteSession
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.Screen
import dev.ronova.gryphonis.ui.components.CreateDialog
import dev.ronova.gryphonis.ui.components.ConfirmDialog
import dev.ronova.gryphonis.ui.components.FileThumb
import dev.ronova.gryphonis.ui.components.GradientFolderIcon
import dev.ronova.gryphonis.ui.components.PropertiesDialog
import dev.ronova.gryphonis.ui.components.SortDialog
import dev.ronova.gryphonis.ui.components.TextInputDialog
import dev.ronova.gryphonis.util.StorageRoot
import dev.ronova.gryphonis.util.StorageUtil
import dev.ronova.gryphonis.util.formatDate
import dev.ronova.gryphonis.util.formatSize
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val dir by vm.currentDir.collectAsStateWithLifecycle()
    val items by vm.displayed.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val searchOpen by vm.searchOpen.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val recursive by vm.searchRecursive.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val remoteConnected by RemoteSession.connected.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showZipName by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var propsTarget by remember { mutableStateOf<FileItem?>(null) }

    val selectionMode = selected.isNotEmpty()
    val selectedItems = items.filter { it.path in selected }
    val single = if (selectedItems.size == 1) selectedItems[0] else null
    val currentDirectory = dir

    BackHandler(enabled = selectionMode || searchOpen || dir != null) {
        when {
            selectionMode -> vm.clearSelection()
            searchOpen -> vm.closeSearch()
            else -> vm.goUp()
        }
    }

    fun requestDelete() {
        if (settings.confirmDelete) showDelete = true else vm.deleteSelected()
    }

    Scaffold(
        topBar = {
            when {
                selectionMode -> TopAppBar(
                    title = { Text(selected.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Rounded.Close, contentDescription = null) }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll() }) { Icon(Icons.Rounded.SelectAll, contentDescription = null) }
                        IconButton(onClick = { vm.copySelected(false) }) { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }
                        IconButton(onClick = { vm.copySelected(true) }) { Icon(Icons.Rounded.ContentCut, contentDescription = null) }
                        IconButton(onClick = { requestDelete() }) { Icon(Icons.Rounded.Delete, contentDescription = null) }
                        Box {
                            IconButton(onClick = { moreOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                if (single != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_rename)) },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                        onClick = {
                                            moreOpen = false
                                            renameTarget = single
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_zip)) },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                    onClick = {
                                        moreOpen = false
                                        showZipName = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_share)) },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                    onClick = {
                                        moreOpen = false
                                        vm.share(selectedItems.map { it.file })
                                    },
                                )
                                if (remoteConnected) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_upload_ftp)) },
                                        leadingIcon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                                        onClick = {
                                            moreOpen = false
                                            vm.uploadSelectedToRemote()
                                        },
                                    )
                                }
                                if (single != null) {
                                    if (!single.isDirectory) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.open_with)) },
                                            leadingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                                            onClick = {
                                                moreOpen = false
                                                vm.openExternal(single.file)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_open_text)) },
                                            leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null) },
                                            onClick = {
                                                moreOpen = false
                                                vm.openAsText(single.file)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_sha)) },
                                            leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                                            onClick = {
                                                moreOpen = false
                                                vm.hash(single.file)
                                            },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_bookmark_toggle)) },
                                            leadingIcon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                                            onClick = {
                                                moreOpen = false
                                                vm.toggleBookmark(single.file)
                                                vm.clearSelection()
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_properties)) },
                                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                        onClick = {
                                            moreOpen = false
                                            propsTarget = single
                                        },
                                    )
                                }
                            }
                        }
                    },
                )

                searchOpen -> TopAppBar(
                    title = { SearchField(query) { vm.setQuery(it) } },
                    navigationIcon = {
                        IconButton(onClick = { vm.closeSearch() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                    },
                )

                else -> TopAppBar(
                    title = {
                        val name = when {
                            currentDirectory == null -> stringResource(R.string.app_name)
                            else -> roots.firstOrNull { it.dir == currentDirectory }?.name ?: currentDirectory.name
                        }
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        if (currentDirectory != null) {
                            IconButton(onClick = { vm.goUp() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                        }
                    },
                    actions = {
                        if (currentDirectory != null) {
                            IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Rounded.Search, contentDescription = null) }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (currentDirectory != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_sort)) },
                                        leadingIcon = { Icon(Icons.Rounded.Sort, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            showSort = true
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_hidden)) },
                                    trailingIcon = { Checkbox(checked = settings.showHidden, onCheckedChange = null) },
                                    onClick = { vm.setPref(Keys.HIDDEN, !settings.showHidden) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_grid)) },
                                    leadingIcon = { Icon(Icons.Rounded.ViewModule, contentDescription = null) },
                                    trailingIcon = { Checkbox(checked = settings.gridView, onCheckedChange = null) },
                                    onClick = { vm.setPref(Keys.GRID, !settings.gridView) },
                                )
                                if (currentDirectory != null) {
                                    val marked = currentDirectory.absolutePath in settings.bookmarks
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (marked) R.string.menu_bookmark_remove else R.string.menu_bookmark_add
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            vm.toggleBookmark(currentDirectory)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_refresh)) },
                                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            vm.reload()
                                        },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tool_ftp_server)) },
                                    leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.push(Screen.FtpServer)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tool_ftp_client)) },
                                    leadingIcon = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.push(Screen.Remote)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tool_analyzer)) },
                                    leadingIcon = { Icon(Icons.Rounded.PieChart, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.openAnalyzer()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tool_settings)) },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.push(Screen.Settings)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (currentDirectory != null && !selectionMode && !searchOpen) {
                FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, contentDescription = null) }
            }
        },
        bottomBar = {
            val clip = clipboard
            if (clip != null && currentDirectory != null && !selectionMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (clip.cut) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(if (clip.cut) R.string.paste_move_n else R.string.paste_copy_n, clip.files.size),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.clearClipboard() }) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = { vm.paste() }) { Text(stringResource(R.string.paste)) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (currentDirectory != null && !searchOpen) {
                Breadcrumbs(currentDirectory, roots) { vm.openDir(it) }
            }
            if (searchOpen) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = recursive,
                        onClick = { vm.toggleRecursive() },
                        label = { Text(stringResource(R.string.search_subfolders)) },
                    )
                }
            }
            if (searching || loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (currentDirectory == null) {
                    HomeContent(vm, roots, settings.bookmarks)
                } else if (loadError != null) {
                    CenterMessage(loadError ?: "")
                } else if (items.isEmpty() && !loading && !searching) {
                    CenterMessage(
                        stringResource(if (searchOpen && query.isNotBlank()) R.string.nothing_found else R.string.folder_empty)
                    )
                } else if (settings.gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(items, key = { it.path }) { item ->
                            FileCell(
                                item = item,
                                selected = item.path in selected,
                                showThumb = settings.showThumbnails,
                                onClick = { if (selectionMode) vm.toggleSelect(item) else vm.open(item) },
                                onLong = { vm.toggleSelect(item) },
                            )
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                selected = item.path in selected,
                                selectionMode = selectionMode,
                                showThumb = settings.showThumbnails,
                                showPath = searchOpen && recursive && query.isNotBlank(),
                                baseDir = currentDirectory,
                                onClick = { if (selectionMode) vm.toggleSelect(item) else vm.open(item) },
                                onLong = { vm.toggleSelect(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs
    if (showCreate) {
        CreateDialog(onDismiss = { showCreate = false }) { name, isFolder ->
            showCreate = false
            vm.create(name, isFolder)
        }
    }
    if (showSort) {
        SortDialog(
            sortBy = settings.sortBy,
            ascending = settings.sortAscending,
            foldersFirst = settings.foldersFirst,
            onSortBy = { vm.setPref(Keys.SORT_BY, it.name) },
            onAscending = { vm.setPref(Keys.SORT_ASC, it) },
            onFoldersFirst = { vm.setPref(Keys.FOLDERS_FIRST, it) },
            onDismiss = { showSort = false },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            message = stringResource(R.string.delete_message, selected.size),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                vm.deleteSelected()
            },
        )
    }
    if (showZipName) {
        TextInputDialog(
            title = stringResource(R.string.zip_name_title),
            initial = if (single != null) single.file.nameWithoutExtension.ifEmpty { single.name } else "Archive",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showZipName = false },
            onConfirm = {
                showZipName = false
                vm.zipSelected(it)
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
                vm.rename(target, it)
            },
        )
    }
    propsTarget?.let { target ->
        PropertiesDialog(target) { propsTarget = null }
    }
}

// ---------------------------------------------------------------------- parts

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            GradientFolderIcon(Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Breadcrumbs(dir: File, roots: List<StorageRoot>, onOpen: (File) -> Unit) {
    val segments = remember(dir, roots) {
        val list = ArrayList<Pair<String, File>>()
        val root = roots.firstOrNull { dir.path == it.dir.path || dir.path.startsWith(it.dir.path + "/") }
        if (root != null) {
            list.add(root.name to root.dir)
            var cur = root.dir
            dir.relativeTo(root.dir).path.split('/').filter { it.isNotEmpty() }.forEach {
                cur = File(cur, it)
                list.add(it to cur)
            }
        } else {
            list.add(dir.name to dir)
        }
        list
    }
    val scroll = rememberScrollState()
    LaunchedEffect(dir, segments.size) {
        delay(50)
        scroll.scrollTo(scroll.maxValue)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { i, (label, file) ->
            if (i > 0) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val last = i == segments.lastIndex
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !last) { onOpen(file) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    showThumb: Boolean,
    showPath: Boolean,
    baseDir: File?,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    val ctx = LocalContext.current
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumb(item, showThumb, Modifier.size(44.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val sub = if (item.isDirectory) formatDate(item.modified) else formatSize(ctx, item.size) + "  •  " + formatDate(item.modified)
            Text(
                sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showPath) {
                val parent = item.file.parentFile
                val rel = if (parent != null && baseDir != null) parent.relativeToOrSelf(baseDir).path else ""
                Text(
                    if (rel.isEmpty()) "." else rel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = null)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCell(
    item: FileItem,
    selected: Boolean,
    showThumb: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileThumb(item, showThumb, Modifier.size(64.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ---------------------------------------------------------------------- home

@Composable
private fun HomeContent(vm: MainViewModel, roots: List<StorageRoot>, bookmarkPaths: Set<String>) {
    val ctx = LocalContext.current
    val quick = remember { StorageUtil.quickFolders() }
    val bookmarks = remember(bookmarkPaths) { bookmarkPaths.map { File(it) }.filter { it.isDirectory }.sortedBy { it.name.lowercase() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionTitle(stringResource(R.string.home_storage)) }
        items(roots, key = { it.dir.path }) { root ->
            Card(
                onClick = { vm.openDir(root.dir) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (root.removable) Icons.Rounded.SdCard else Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(root.name, style = MaterialTheme.typography.titleMedium)
                        if (root.total > 0) {
                            val used = (root.total - root.free).coerceAtLeast(0)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (used.toFloat() / root.total.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.free_of, formatSize(ctx, root.free), formatSize(ctx, root.total)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (quick.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_quick)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quick, key = { it.path }) { f ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { vm.openDir(f) }
                                .padding(10.dp)
                                .width(72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GradientFolderIcon(Modifier.size(48.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_bookmarks)) }
            items(bookmarks, key = { "bm:" + it.path }) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.openDir(f) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GradientFolderIcon(Modifier.size(40.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            f.absolutePath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.toggleBookmark(f) }) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.home_tools)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolCard(Icons.Rounded.Dns, stringResource(R.string.tool_ftp_server), Modifier.weight(1f)) { vm.push(Screen.FtpServer) }
                    ToolCard(Icons.Rounded.Cloud, stringResource(R.string.tool_ftp_client), Modifier.weight(1f)) { vm.push(Screen.Remote) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolCard(Icons.Rounded.PieChart, stringResource(R.string.tool_analyzer), Modifier.weight(1f)) { vm.openAnalyzer() }
                    ToolCard(Icons.Rounded.Settings, stringResource(R.string.tool_settings), Modifier.weight(1f)) { vm.push(Screen.Settings) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
