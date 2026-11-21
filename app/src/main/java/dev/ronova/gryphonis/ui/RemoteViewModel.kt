package dev.ronova.gryphonis.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ronova.gryphonis.GryphonisApp
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.ClipHub
import dev.ronova.gryphonis.data.ExtClip
import dev.ronova.gryphonis.data.FileOps
import dev.ronova.gryphonis.data.FtpProfile
import dev.ronova.gryphonis.data.FtpProfiles
import dev.ronova.gryphonis.data.ProgressTracker
import dev.ronova.gryphonis.data.TransferManager
import dev.ronova.gryphonis.data.TransferResult
import dev.ronova.gryphonis.ftp.RemoteItem
import dev.ronova.gryphonis.ftp.RemoteSession
import dev.ronova.gryphonis.ftp.joinRemote
import dev.ronova.gryphonis.ftp.parentRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as GryphonisApp).settingsRepo

    val connected = RemoteSession.connected
    val events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    var connecting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var path by mutableStateOf("/")
        private set
    var items by mutableStateOf<List<RemoteItem>>(emptyList())
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun str(@StringRes id: Int): String = getApplication<Application>().getString(id)

    private fun say(text: String) {
        events.tryEmit(text)
    }

    /** Подключение. Если save = true, настройки (и зашифрованный пароль) сохраняются как профиль. */
    fun connect(profile: FtpProfile, plainPassword: String, save: Boolean) {
        if (connecting) return
        viewModelScope.launch {
            connecting = true
            error = null
            try {
                withContext(Dispatchers.IO) {
                    RemoteSession.connect(profile.host.trim(), profile.port, profile.user, plainPassword)
                }
                if (save) repo.saveFtpProfile(profile.copy(secret = FtpProfiles.seal(plainPassword)))
                val start = profile.path.trim().ifEmpty { "/" }
                if (!load(start) && start != "/") load("/")
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                connecting = false
            }
        }
    }

    fun saveProfile(profile: FtpProfile, plainPassword: String) {
        viewModelScope.launch { repo.saveFtpProfile(profile.copy(secret = FtpProfiles.seal(plainPassword))) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { repo.deleteFtpProfile(id) }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) { RemoteSession.disconnect() }
        if (ClipHub.state.value is ExtClip.Remote) ClipHub.state.value = null
        items = emptyList()
        selected = emptySet()
        path = "/"
    }

    private suspend fun load(target: String): Boolean {
        loading = true
        return try {
            val list = withContext(Dispatchers.IO) { RemoteSession.list(target) }
            items = list.sortedWith(compareBy<RemoteItem> { !it.isDir }.thenBy { it.name.lowercase() })
            path = target
            RemoteSession.cwd = target
            selected = emptySet()
            true
        } catch (e: Exception) {
            say(e.message ?: e.javaClass.simpleName)
            false
        } finally {
            loading = false
        }
    }

    fun open(target: String) {
        viewModelScope.launch { load(target) }
    }

    fun refresh() = open(path)

    /** Жест «потянуть вниз». */
    fun pullRefresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            load(path)
            refreshing = false
        }
    }

    fun up(): Boolean {
        if (path == "/" || path.isEmpty()) return false
        open(parentRemote(path))
        return true
    }

    fun toggle(item: RemoteItem) {
        selected = if (item.path in selected) selected - item.path else selected + item.path
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun selectAll() {
        selected = items.map { it.path }.toSet()
    }

    /** Как в обычных FTP-проводниках: «копировать» здесь, «вставить» — в любой папке на телефоне. */
    fun copySelected() {
        val chosen = items.filter { it.path in selected }
        if (chosen.isEmpty()) return
        ClipHub.state.value = ExtClip.Remote(chosen)
        selected = emptySet()
        say(getApplication<Application>().getString(R.string.ftp_copied_n, chosen.size))
    }

    private fun run(title: String, bytesMode: Boolean, block: (ProgressTracker) -> Unit) {
        val started = TransferManager.run(title, bytesMode, onResult = { r ->
            when (r) {
                TransferResult.Success -> say(str(R.string.done))
                TransferResult.Cancelled -> say(str(R.string.cancelled))
                is TransferResult.Failed -> {}
            }
            refresh()
        }, block = block)
        if (!started) say(str(R.string.busy))
    }

    fun download(dest: File) {
        val chosen = items.filter { it.path in selected }
        if (chosen.isEmpty()) return
        selected = emptySet()
        run(str(R.string.downloading), true) { t -> RemoteSession.download(chosen, dest, t) }
    }

    /** moveAfter = true — «вырезали» на телефоне: после успешной отправки исходники удаляются. */
    fun upload(files: List<File>, moveAfter: Boolean = false) {
        if (files.isEmpty()) return
        val target = path
        run(str(R.string.uploading), true) { t ->
            RemoteSession.upload(files, target, t)
            if (moveAfter) files.forEach { FileOps.deleteQuiet(it) }
        }
    }

    fun deleteSelected() {
        val chosen = items.filter { it.path in selected }
        if (chosen.isEmpty()) return
        selected = emptySet()
        run(str(R.string.deleting), false) { t -> RemoteSession.delete(chosen, t) }
    }

    fun mkdir(name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) return
        val target = joinRemote(path, n)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { RemoteSession.mkdir(target) }
                refresh()
            } catch (e: Exception) {
                say(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun rename(item: RemoteItem, newName: String) {
        val n = newName.trim()
        if (n.isEmpty() || n.contains('/') || n == item.name) return
        val to = joinRemote(parentRemote(item.path), n)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { RemoteSession.rename(item.path, to) }
                refresh()
            } catch (e: Exception) {
                say(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
