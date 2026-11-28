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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.RecentFile
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.components.DeleteDialog
import dev.ronova.gryphonis.ui.components.FileThumb
import dev.ronova.gryphonis.ui.components.NowPlaying
import dev.ronova.gryphonis.ui.components.PropertiesDialog
import dev.ronova.gryphonis.ui.components.nowPlayingOf
import dev.ronova.gryphonis.util.StorageRoot
import dev.ronova.gryphonis.util.formatSize
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

private const val DAY_MS = 86_400_000L

private val PERIOD_DAYS = intArrayOf(1, 3, 7, 30)

private enum class KindFilter(val label: Int, val kinds: Set<FileKind>?) {
    ALL(R.string.recent_kind_all, null),
    IMAGES(R.string.recent_kind_images, setOf(FileKind.IMAGE)),
    VIDEO(R.string.recent_kind_video, setOf(FileKind.VIDEO)),
    AUDIO(R.string.recent_kind_audio, setOf(FileKind.AUDIO)),
    DOCS(R.string.recent_kind_docs, setOf(FileKind.PDF, FileKind.DOC, FileKind.TEXT)),
    ARCHIVES(R.string.recent_kind_archives, setOf(FileKind.ARCHIVE)),
    APK(R.string.recent_kind_apk, setOf(FileKind.APK)),
}

private fun periodLabel(days: Int): Int = when (days) {
    1 -> R.string.recent_period_1
    3 -> R.string.recent_period_3
    7 -> R.string.recent_period_7
    else -> R.string.recent_period_30
}

/**
 * Только что появившиеся файлы: скачанные, снятые, скопированные. Файлы можно открыть, выделить (удалить,
 * поделиться, копировать/переместить) и перейти к ним в папке — там файл прокручивается в видимую область и подсвечивается.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by vm.recentAll.collectAsStateWithLifecycle()
    val scanning by vm.recentScanning.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val fsVersion by vm.fsVersion.collectAsStateWithLifecycle()

    var periodIndex by rememberSaveable { mutableStateOf(1) }
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var moreOpen by remember { mutableStateOf(false) }
    var deleteTargets by remember { mutableStateOf<List<File>?>(null) }
    var propsTarget by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(Unit) { vm.scanRecent() }
    // после удаления/переноса файлы пропадают из списка сразу, без нового долгого поиска
    LaunchedEffect(fsVersion) { if (fsVersion > 0) vm.pruneRecent() }

    val days = PERIOD_DAYS[periodIndex]
    val filter = KindFilter.entries[filterIndex]
    val now = remember(all) { System.currentTimeMillis() }
    val shown = remember(all, days, filter, now) {
        all.filter { r ->
            r.appeared >= now - days * DAY_MS && (filter.kinds == null || r.item.kind in filter.kinds)
        }
    }
    // выбранное, чего в списке уже нет, из выбора убираем
    LaunchedEffect(shown) { selected = selected.filter { p -> shown.any { it.item.path == p } }.toSet() }

    val selectionMode = selected.isNotEmpty()
    val chosen = shown.filter { it.item.path in selected }.map { it.item }
    val single = chosen.singleOrNull()

    BackHandler(enabled = selectionMode) { selected = emptySet() }

    fun requestDelete() {
        val files = chosen.map { it.file }
        if (files.isEmpty()) return
        if (settings.confirmDelete) {
            deleteTargets = files
        } else {
            selected = emptySet()
            vm.deleteFiles(files, false)
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(selected.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, contentDescription = null) }
                    },
                    actions = {
                        IconButton(onClick = { selected = shown.map { it.item.path }.toSet() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = null)
                        }
                        IconButton(onClick = {
                            if (vm.copyFiles(chosen.map { it.file }, cut = false, hintOpenFolder = true)) {
                                selected = emptySet()
                                onBack()
                            }
                        }) { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }
                        IconButton(onClick = {
                            if (vm.copyFiles(chosen.map { it.file }, cut = true, hintOpenFolder = true)) {
                                selected = emptySet()
                                onBack()
                            }
                        }) { Icon(Icons.Rounded.ContentCut, contentDescription = null) }
                        IconButton(onClick = { requestDelete() }) { Icon(Icons.Rounded.Delete, contentDescription = null) }
                        Box {
                            IconButton(onClick = { moreOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                if (single != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.show_in_folder)) },
                                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                                        onClick = {
                                            moreOpen = false
                                            selected = emptySet()
                                            vm.revealInFolder(single.file)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_share)) },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                    onClick = {
                                        moreOpen = false
                                        vm.share(chosen.map { it.file })
                                    },
                                )
                                if (single != null) {
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
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_recent)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                    },
                    actions = {
                        IconButton(onClick = { vm.scanRecent() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.menu_refresh))
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // период
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(PERIOD_DAYS.size) { i ->
                    FilterChip(
                        selected = periodIndex == i,
                        onClick = { periodIndex = i },
                        label = { Text(stringResource(periodLabel(PERIOD_DAYS[i]))) },
                    )
                }
            }
            // тип файлов
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(KindFilter.entries.size) { i ->
                    FilterChip(
                        selected = filterIndex == i,
                        onClick = { filterIndex = i },
                        label = { Text(stringResource(KindFilter.entries[i].label)) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (shown.isNotEmpty()) {
                Text(
                    stringResource(R.string.recent_summary, shown.size, formatSize(ctx, shown.sumOf { it.item.size })),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = scanning,
                onRefresh = { vm.scanRecent() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (shown.isEmpty()) {
                    // LazyColumn, чтобы жест «потянуть вниз» работал и в пустом списке
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                    Icon(
                                        Icons.Rounded.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(if (scanning) R.string.recent_searching else R.string.recent_empty),
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!scanning) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stringResource(R.string.recent_empty_hint),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val pool = remember(shown) { shown.map { it.item } }
                    val groups = remember(shown) { shown.groupBy { dayIndex(it.appeared) }.toList() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        groups.forEach { (day, files) ->
                            stickyHeader(key = "day:$day") {
                                DayHeader(files.first().appeared, now)
                            }
                            items(files, key = { it.item.path }) { rf ->
                                val isSel = rf.item.path in selected
                                RecentRow(
                                    modifier = Modifier.animateItem(),
                                    recent = rf,
                                    selected = isSel,
                                    selectionMode = selectionMode,
                                    showThumb = settings.showThumbnails,
                                    roots = roots,
                                    onClick = {
                                        if (selectionMode) {
                                            selected = if (isSel) selected - rf.item.path else selected + rf.item.path
                                        } else {
                                            vm.openFrom(rf.item, pool)
                                        }
                                    },
                                    onLong = {
                                        selected = if (isSel) selected - rf.item.path else selected + rf.item.path
                                    },
                                    onReveal = { vm.revealInFolder(rf.item.file) },
                                )
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
    propsTarget?.let { target -> PropertiesDialog(target) { propsTarget = null } }
}

// ---------------------------------------------------------------------- parts

@Composable
private fun DayHeader(appeared: Long, now: Long) {
    val label = when (dayIndex(now) - dayIndex(appeared)) {
        0L -> stringResource(R.string.recent_today)
        1L -> stringResource(R.string.recent_yesterday)
        else -> remember(appeared) { DateFormat.getDateInstance(DateFormat.LONG).format(Date(appeared)) }
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    modifier: Modifier,
    recent: RecentFile,
    selected: Boolean,
    selectionMode: Boolean,
    showThumb: Boolean,
    roots: List<StorageRoot>,
    onClick: () -> Unit,
    onLong: () -> Unit,
    onReveal: () -> Unit,
) {
    val ctx = LocalContext.current
    val item = recent.item
    val np = if (item.kind == FileKind.AUDIO) nowPlayingOf(item.path) else NowPlaying.NONE
    val time = remember(recent.appeared) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(recent.appeared)) }
    val folderLabel = remember(item.path, roots) { shortFolder(item.file.parentFile, roots) }
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumb(item, showThumb, Modifier.size(44.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (np != NowPlaying.NONE) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight = if (np != NowPlaying.NONE) FontWeight.SemiBold else null,
            )
            Text(
                formatSize(ctx, item.size) + "  •  " + time,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                folderLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        } else {
            IconButton(onClick = onReveal) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = stringResource(R.string.show_in_folder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Номер календарного дня (в часовом поясе устройства): по нему файлы группируются под заголовки «Сегодня», «Вчера»... */
private fun dayIndex(ms: Long): Long = (ms + TimeZone.getDefault().getOffset(ms)) / DAY_MS

/** Папка файла без начала пути накопителя: «Download/Telegram» вместо «/storage/emulated/0/Download/Telegram». */
private fun shortFolder(dir: File?, roots: List<StorageRoot>): String {
    if (dir == null) return ""
    val root = roots.firstOrNull { dir.path == it.dir.path || dir.path.startsWith(it.dir.path + "/") }
    if (root == null) return dir.path
    val rel = dir.relativeTo(root.dir).path
    return if (rel.isEmpty()) root.name else rel
}
