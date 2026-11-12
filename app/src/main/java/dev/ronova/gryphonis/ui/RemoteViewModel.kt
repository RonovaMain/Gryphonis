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
import dev.ronova.gryphonis.data.Keys
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

    fun connect(host: String, port: Int, user: String, pass: String) {
        if (connecting) return
        viewModelScope.launch {
            connecting = true
            error = null
            try {
                withContext(Dispatchers.IO) { RemoteSession.connect(host.trim(), port, user, pass) }
                repo.set(Keys.REMOTE_HOST, host.trim())
                repo.set(Keys.REMOTE_PORT, port)
                repo.set(Keys.REMOTE_USER, user)
                open("/")
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                connecting = false
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) { RemoteSession.disconnect() }
        items = emptyList()
        selected = emptySet()
        path = "/"
    }

    fun open(target: String) {
        viewModelScope.launch {
            loading = true
            try {
                val list = withContext(Dispatchers.IO) { RemoteSession.list(target) }
                items = list.sortedWith(compareBy<RemoteItem> { !it.isDir }.thenBy { it.name.lowercase() })
                path = target
                RemoteSession.cwd = target
                selected = emptySet()
            } catch (e: Exception) {
                say(e.message ?: e.javaClass.simpleName)
            } finally {
                loading = false
            }
        }
    }

    fun refresh() = open(path)

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

    private fun run(title: String, bytesMode: Boolean, block: (dev.ronova.gryphonis.data.ProgressTracker) -> Unit) {
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

    fun upload(files: List<File>) {
        if (files.isEmpty()) return
        val target = path
        run(str(R.string.uploading), true) { t -> RemoteSession.upload(files, target, t) }
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
