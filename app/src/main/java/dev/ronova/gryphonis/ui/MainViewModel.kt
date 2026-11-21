package dev.ronova.gryphonis.ui

import android.app.Application
import android.os.FileObserver
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ronova.gryphonis.GryphonisApp
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.AppSettings
import dev.ronova.gryphonis.data.ArchiveOps
import dev.ronova.gryphonis.data.ClipHub
import dev.ronova.gryphonis.data.ExtClip
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.data.FileKind
import dev.ronova.gryphonis.data.FileOps
import dev.ronova.gryphonis.data.ProgressTracker
import dev.ronova.gryphonis.data.TransferManager
import dev.ronova.gryphonis.data.TransferResult
import dev.ronova.gryphonis.data.Trash
import dev.ronova.gryphonis.data.TrashItem
import dev.ronova.gryphonis.data.toFileItem
import dev.ronova.gryphonis.data.sortedFor
import dev.ronova.gryphonis.ftp.RemoteSession
import dev.ronova.gryphonis.media.MusicPlayer
import dev.ronova.gryphonis.util.OpenUtil
import dev.ronova.gryphonis.util.StorageRoot
import dev.ronova.gryphonis.util.StorageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Settings : Screen
    data object FtpServer : Screen
    data object Remote : Screen
    data object Trash : Screen
    data class Archive(val path: String) : Screen
    data class Analyzer(val path: String) : Screen
    data class Text(val path: String, val queue: List<String> = emptyList()) : Screen
    data class Image(val path: String, val queue: List<String> = emptyList()) : Screen
    data class Audio(val path: String?) : Screen
    data class Video(val path: String, val queue: List<String> = emptyList()) : Screen
}

data class Clip(val files: List<File>, val cut: Boolean)

private data class SearchReq(val query: String, val recursive: Boolean, val dir: File?, val hidden: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as GryphonisApp).settingsRepo

    val settings: StateFlow<AppSettings> = repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // ---------------------------------------------------------------- permissions
    var hasAccess by mutableStateOf(StorageUtil.hasAccess(app))
        private set
    var showPermissionDialog by mutableStateOf(false)

    fun refreshAccess() {
        val now = StorageUtil.hasAccess(getApplication())
        val changed = now != hasAccess
        hasAccess = now
        if (changed) {
            refreshRoots()
            reload()
        }
    }

    /** Вызывается перед любой файловой операцией. */
    fun ensureAccess(): Boolean {
        refreshAccess()
        if (!hasAccess) {
            showPermissionDialog = true
            return false
        }
        return true
    }

    // ---------------------------------------------------------------- overlay navigation
    var overlays by mutableStateOf<List<Screen>>(emptyList())
        private set

    fun push(s: Screen) {
        overlays = overlays + s
    }

    fun pop() {
        if (overlays.isNotEmpty()) overlays = overlays.dropLast(1)
    }

    // ---------------------------------------------------------------- browser state
    val currentDir = MutableStateFlow<File?>(null)
    private val rawItems = MutableStateFlow<List<FileItem>>(emptyList())
    val loading = MutableStateFlow(false)
    val loadError = MutableStateFlow<String?>(null)
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val clipboard = MutableStateFlow<Clip?>(null)
    val searchOpen = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val searchRecursive = MutableStateFlow(false)
    val searching = MutableStateFlow(false)
    private val searchResults = MutableStateFlow<List<FileItem>?>(null)
    val roots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val zipTarget = MutableStateFlow<FileItem?>(null)
    val hashResult = MutableStateFlow<Pair<String, String>?>(null)
    val events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val refreshing = MutableStateFlow(false)
    val trashItems = MutableStateFlow<List<TrashItem>>(emptyList())

    /** Растёт после каждой завершённой файловой операции — экраны могут перечитать данные. */
    val fsVersion = MutableStateFlow(0)
    val extClip: StateFlow<ExtClip?> = ClipHub.state

    val displayed: StateFlow<List<FileItem>> = combine(
        rawItems, searchResults, settings, searchQuery, searchRecursive,
    ) { raw, found, s, q, rec ->
        val query = q.trim()
        var list = when {
            rec && query.isNotEmpty() -> found ?: emptyList()
            query.isNotEmpty() -> raw.filter { it.name.contains(query, ignoreCase = true) }
            else -> raw
        }
        if (!s.showHidden) list = list.filter { !it.isHidden }
        list.sortedFor(s.sortBy, s.sortAscending, s.foldersFirst)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var loadJob: Job? = null

    // ---------------------------------------------------------------- автообновление
    private var foreground = false
    private var observer: FileObserver? = null
    private var lastSignature: Long? = null
    private val changeTicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        refreshRoots()
        startSearchRunner()
        startAutoRefresh()
        purgeTrash()
        // новый буфер из FTP/архива заменяет обычный буфер обмена
        viewModelScope.launch {
            ClipHub.state.collect { if (it != null) clipboard.value = null }
        }
        // кэш файлов, открытых прямо из архивов, чистим при старте
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(getApplication<Application>().cacheDir, "archive_open").deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }

    private fun startAutoRefresh() {
        // 1) слушаем папку: файл скачали, создали, удалили — список обновляется сразу
        viewModelScope.launch {
            currentDir.collect {
                lastSignature = null
                restartObserver()
            }
        }
        viewModelScope.launch {
            changeTicks.debounce(400).collect { if (foreground && hasAccess) reload(silent = true) }
        }
        // 2) запасной вариант: некоторые системы не присылают события — раз в пару секунд сверяем состояние папки
        viewModelScope.launch {
            while (true) {
                delay(2500)
                if (foreground && hasAccess) pollOnce()
            }
        }
    }

    private suspend fun pollOnce() {
        val dir = currentDir.value ?: return
        if (loading.value || TransferManager.busy) return
        val sig = withContext(Dispatchers.IO) {
            val names = dir.list()
            if (names == null) -1L else dir.lastModified() * 31L + names.size
        }
        val prev = lastSignature
        lastSignature = sig
        if (prev != null && prev != sig) reload(silent = true)
    }

    private fun restartObserver() {
        observer?.stopWatching()
        observer = null
        val dir = currentDir.value
        if (!foreground || !hasAccess || dir == null) return
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF
        @Suppress("DEPRECATION")
        val obs = object : FileObserver(dir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                changeTicks.tryEmit(Unit)
            }
        }
        obs.startWatching()
        observer = obs
    }

    /** Приложение вышло на передний план / свернулось. */
    fun onForeground(value: Boolean) {
        foreground = value
        if (value) {
            refreshAccess()
            if (hasAccess) {
                reload(silent = true)
                refreshRoots()
                refreshTrash()
                purgeTrash()
            }
        }
        restartObserver()
    }

    override fun onCleared() {
        observer?.stopWatching()
        observer = null
        super.onCleared()
    }

    private fun startSearchRunner() {
        viewModelScope.launch {
            combine(
                searchQuery, searchRecursive, currentDir,
                settings.map { it.showHidden }.distinctUntilChanged(),
            ) { q, rec, dir, hid -> SearchReq(q.trim(), rec, dir, hid) }
                .debounce(250)
                .collectLatest { req ->
                    val dir = req.dir
                    if (req.query.isEmpty() || !req.recursive || dir == null) {
                        searchResults.value = null
                        searching.value = false
                    } else {
                        searching.value = true
                        searchResults.value = emptyList()
                        val found = ArrayList<FileItem>()
                        withContext(Dispatchers.IO) {
                            FileOps.search(dir, req.query, req.hidden, { isActive }, { item ->
                                found.add(item)
                                if (found.size % 25 == 0) searchResults.value = ArrayList(found)
                            })
                        }
                        searchResults.value = ArrayList(found)
                        searching.value = false
                    }
                }
        }
    }

    fun refreshRoots() {
        viewModelScope.launch {
            roots.value = withContext(Dispatchers.IO) { StorageUtil.roots(getApplication()) }
        }
    }

    // ---------------------------------------------------------------- helpers
    private fun say(@StringRes id: Int, vararg args: Any) {
        events.tryEmit(getApplication<Application>().getString(id, *args))
    }

    fun <T> setPref(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repo.set(key, value) }
    }

    fun toggleBookmark(dir: File) {
        viewModelScope.launch { repo.toggleBookmark(dir.absolutePath) }
    }

    // ---------------------------------------------------------------- navigation
    /** silent = true — тихое обновление без полосы загрузки (автообновление, возврат в приложение). */
    fun reload(silent: Boolean = false): Job? {
        val dir = currentDir.value
        loadJob?.cancel()
        if (dir == null) {
            rawItems.value = emptyList()
            loading.value = false
            return null
        }
        val job = viewModelScope.launch {
            if (!silent) {
                loading.value = true
                loadError.value = null
            }
            val res = withContext(Dispatchers.IO) { FileOps.list(dir) }
            if (currentDir.value == dir) {
                if (res == null) {
                    loadError.value = getApplication<Application>().getString(R.string.cannot_read_folder)
                    rawItems.value = emptyList()
                } else {
                    loadError.value = null
                    rawItems.value = res
                }
                // убираем из выделения то, чего уже нет
                selected.update { sel -> sel.filter { File(it).exists() }.toSet() }
                loading.value = false
            }
        }
        loadJob = job
        return job
    }

    /** Жест «потянуть вниз» на главной и в папках. */
    fun pullRefresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            refreshRoots()
            refreshTrash()
            reload()?.join()
            delay(350)
            refreshing.value = false
        }
    }

    fun openDir(dir: File) {
        if (!ensureAccess()) return
        currentDir.value = dir
        selected.value = emptySet()
        closeSearch()
        reload()
    }

    fun goHome() {
        currentDir.value = null
        selected.value = emptySet()
        closeSearch()
        reload()
    }

    /** true, если переход выполнен. */
    fun goUp(): Boolean {
        val dir = currentDir.value ?: return false
        val isRoot = roots.value.any { it.dir == dir } || dir == StorageUtil.internalRoot()
        val parent = dir.parentFile
        selected.value = emptySet()
        closeSearch()
        if (isRoot || parent == null) {
            currentDir.value = null
            reload()
        } else {
            currentDir.value = parent
            reload()
        }
        return true
    }

    fun open(item: FileItem) {
        if (!ensureAccess()) return
        // очередь = файлы того же типа в том порядке, в котором они сейчас показаны (сортировка, скрытые, поиск)
        val queue = displayed.value.filter { !it.isDirectory && it.kind == item.kind }.map { it.path }
        when (item.kind) {
            FileKind.FOLDER -> openDir(item.file)
            FileKind.AUDIO -> {
                MusicPlayer.play(getApplication(), item.file, queue.map { File(it) })
                push(Screen.Audio(item.path))
            }
            FileKind.VIDEO -> push(Screen.Video(item.path, queue))
            FileKind.IMAGE -> push(Screen.Image(item.path, queue))
            FileKind.TEXT -> push(Screen.Text(item.path, queue))
            else -> if (item.extension == "zip") zipTarget.value = item else openExternal(item.file)
        }
    }

    fun openExternal(file: File) {
        if (!OpenUtil.open(getApplication(), file)) say(R.string.no_app_to_open)
    }

    /** Открывает полноэкранный плеер для уже играющей музыки (из мини-плеера или уведомления). */
    fun showPlayer() {
        if (!MusicPlayer.hasMedia) return
        if (overlays.lastOrNull() !is Screen.Audio) push(Screen.Audio(null))
    }

    fun openAsText(file: File) {
        push(Screen.Text(file.absolutePath))
    }

    fun share(files: List<File>) {
        val only = files.filter { it.isFile }
        if (only.isEmpty() || !OpenUtil.share(getApplication(), only)) say(R.string.cannot_share)
    }

    fun openAnalyzer() {
        if (!ensureAccess()) return
        val p = currentDir.value ?: StorageUtil.internalRoot()
        push(Screen.Analyzer(p.absolutePath))
    }

    // ---------------------------------------------------------------- search
    fun openSearch() {
        searchOpen.value = true
    }

    fun closeSearch() {
        searchOpen.value = false
        searchQuery.value = ""
        searchResults.value = null
    }

    fun setQuery(q: String) {
        searchQuery.value = q
    }

    fun toggleRecursive() {
        searchRecursive.value = !searchRecursive.value
    }

    // ---------------------------------------------------------------- selection / clipboard
    fun toggleSelect(item: FileItem) {
        selected.update { if (item.path in it) it - item.path else it + item.path }
    }

    fun selectAll() {
        selected.value = displayed.value.map { it.path }.toSet()
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    private fun selectedFiles(): List<File> {
        val sel = selected.value
        return displayed.value.filter { it.path in sel }.map { it.file }
    }

    fun copySelected(cut: Boolean) {
        if (!ensureAccess()) return
        val files = selectedFiles()
        if (files.isEmpty()) return
        ClipHub.state.value = null
        clipboard.value = Clip(files, cut)
        clearSelection()
        say(if (cut) R.string.cut_n else R.string.copied_n, files.size)
    }

    fun clearClipboard() {
        clipboard.value = null
    }

    // ---------------------------------------------------------------- operations
    private fun transfer(title: String, bytesMode: Boolean = true, block: (ProgressTracker) -> Unit): Boolean {
        val started = TransferManager.run(title, bytesMode, onResult = { r -> onTransferResult(r) }, block = block)
        if (!started) say(R.string.busy)
        return started
    }

    private fun onTransferResult(r: TransferResult) {
        reload(silent = true)
        refreshRoots()
        refreshTrash()
        fsVersion.update { it + 1 }
        when (r) {
            TransferResult.Success -> say(R.string.done)
            TransferResult.Cancelled -> say(R.string.cancelled)
            is TransferResult.Failed -> {}
        }
    }

    private fun str(@StringRes id: Int): String = getApplication<Application>().getString(id)

    fun paste() {
        if (!ensureAccess()) return
        val clip = clipboard.value ?: return
        val dest = currentDir.value ?: return
        val started = transfer(str(if (clip.cut) R.string.moving else R.string.copying)) { t ->
            FileOps.copy(clip.files, dest, t, clip.cut)
        }
        if (started && clip.cut) clipboard.value = null
    }

    /** permanent = false — в корзину приложения, true — безвозвратно. */
    fun deleteFiles(files: List<File>, permanent: Boolean) {
        if (!ensureAccess()) return
        val targets = FileOps.dropNested(files)
        if (targets.isEmpty()) return
        if (permanent) {
            transfer(str(R.string.deleting), bytesMode = false) { t -> FileOps.delete(targets, t) }
        } else {
            val app = getApplication<Application>()
            transfer(str(R.string.moving_to_trash)) { t -> Trash.moveToTrash(app, targets, t) }
        }
    }

    fun deleteSelected(permanent: Boolean) {
        val files = selectedFiles()
        if (files.isEmpty()) return
        clearSelection()
        deleteFiles(files, permanent)
    }

    // ---------------------------------------------------------------- корзина
    fun refreshTrash() {
        viewModelScope.launch {
            trashItems.value = withContext(Dispatchers.IO) { Trash.list(getApplication()) }
        }
    }

    private fun purgeTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            if (TransferManager.busy) return@launch
            val days = repo.flow.first().trashDays
            Trash.purgeExpired(getApplication(), days)
            trashItems.value = Trash.list(getApplication())
        }
    }

    fun restoreTrash(items: List<TrashItem>) {
        if (!ensureAccess() || items.isEmpty()) return
        transfer(str(R.string.restoring)) { t -> Trash.restore(items, t) }
    }

    fun deleteTrashForever(items: List<TrashItem>) {
        if (items.isEmpty()) return
        transfer(str(R.string.deleting), bytesMode = false) { t -> Trash.deleteForever(items, t) }
    }

    // ---------------------------------------------------------------- вставка из FTP и архивов
    fun pasteExternal() {
        if (!ensureAccess()) return
        val dest = currentDir.value ?: return
        when (val c = ClipHub.state.value) {
            is ExtClip.Remote -> {
                if (!RemoteSession.connected.value) {
                    say(R.string.ftp_not_connected)
                    ClipHub.state.value = null
                    return
                }
                transfer(str(R.string.downloading)) { t -> RemoteSession.download(c.items, dest, t) }
            }
            is ExtClip.Archive -> {
                transfer(str(R.string.unzipping)) { t -> ArchiveOps.extractSelected(c.zip, c.paths, dest, t) }
            }
            null -> {}
        }
    }

    fun clearExtClip() {
        ClipHub.state.value = null
    }

    fun extractFromArchive(zip: File, paths: List<String>, dest: File) {
        if (!ensureAccess() || paths.isEmpty()) return
        transfer(str(R.string.unzipping)) { t -> ArchiveOps.extractSelected(zip, paths, dest, t) }
    }

    fun copyFromArchive(zip: File, paths: List<String>) {
        if (paths.isEmpty()) return
        ClipHub.state.value = ExtClip.Archive(zip, paths)
        say(R.string.archive_copied_n, paths.size)
    }

    /** Открывает файл из архива: сначала достаём его во временную папку. */
    fun openArchiveEntry(zip: File, entryPath: String) {
        if (!ensureAccess()) return
        val cache = File(getApplication<Application>().cacheDir, "archive_open")
        var extracted: File? = null
        TransferManager.run(
            str(R.string.unzipping),
            true,
            onResult = { r ->
                val f = extracted
                if (r is TransferResult.Success && f != null) viewModelScope.launch { openFile(f) }
                if (r is TransferResult.Cancelled) say(R.string.cancelled)
            },
        ) { t ->
            val dir = File(cache, System.nanoTime().toString())
            ArchiveOps.extractSelected(zip, listOf(entryPath), dir, t)
            extracted = File(dir, entryPath.trim('/').substringAfterLast('/'))
        }
    }

    /** Открывает один файл по пути (не из текущей папки): для анализатора и архивов. */
    fun openFile(file: File) {
        if (!ensureAccess()) return
        val item = file.toFileItem()
        val queue = listOf(item.path)
        when (item.kind) {
            FileKind.AUDIO -> {
                MusicPlayer.play(getApplication(), file, listOf(file))
                push(Screen.Audio(item.path))
            }
            FileKind.VIDEO -> push(Screen.Video(item.path, queue))
            FileKind.IMAGE -> push(Screen.Image(item.path, queue))
            FileKind.TEXT -> push(Screen.Text(item.path, queue))
            else -> if (item.extension == "zip") push(Screen.Archive(item.path)) else openExternal(file)
        }
    }

    fun rename(item: FileItem, newName: String) {
        if (!ensureAccess()) return
        val name = newName.trim()
        if (name.isEmpty() || name.contains('/') || name == item.name) return
        val target = File(item.file.parentFile, name)
        if (target.exists()) {
            say(R.string.already_exists)
            return
        }
        if (item.file.renameTo(target)) {
            clearSelection()
            reload()
        } else {
            say(R.string.rename_failed)
        }
    }

    fun create(name: String, isFolder: Boolean) {
        if (!ensureAccess()) return
        val dir = currentDir.value ?: return
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) return
        val target = File(dir, n)
        if (target.exists()) {
            say(R.string.already_exists)
            return
        }
        val ok = try {
            if (isFolder) target.mkdirs() else target.createNewFile()
        } catch (e: Exception) {
            false
        }
        if (ok) reload() else say(R.string.create_failed)
    }

    fun zipSelected(name: String) {
        if (!ensureAccess()) return
        val dir = currentDir.value ?: return
        val files = selectedFiles()
        if (files.isEmpty()) return
        val fileName = if (name.trim().endsWith(".zip", ignoreCase = true)) name.trim() else name.trim() + ".zip"
        val out = FileOps.uniqueTarget(dir, fileName)
        clearSelection()
        transfer(str(R.string.zipping)) { t -> FileOps.zip(files, out, t) }
    }

    fun unzip(item: FileItem, here: Boolean) {
        if (!ensureAccess()) return
        val parent = item.file.parentFile ?: return
        val dest = if (here) parent else FileOps.uniqueTarget(parent, item.file.nameWithoutExtension)
        transfer(str(R.string.unzipping)) { t -> FileOps.unzip(item.file, dest, t) }
    }

    fun hash(file: File) {
        if (!ensureAccess()) return
        var result = ""
        TransferManager.run(
            str(R.string.calculating_hash),
            true,
            onResult = { r ->
                if (r is TransferResult.Success) hashResult.value = file.name to result
                if (r is TransferResult.Cancelled) say(R.string.cancelled)
            },
        ) { t -> result = FileOps.sha256(file, t) }
    }

    fun uploadSelectedToRemote() {
        if (!RemoteSession.connected.value) {
            say(R.string.ftp_not_connected)
            return
        }
        val files = selectedFiles()
        if (files.isEmpty()) return
        clearSelection()
        val target = RemoteSession.cwd
        transfer(str(R.string.uploading)) { t -> RemoteSession.upload(files, target, t) }
    }
}
