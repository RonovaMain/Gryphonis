package dev.ronova.gryphonis.ftp

import dev.ronova.gryphonis.data.FileOps
import dev.ronova.gryphonis.data.ProgressTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class RemoteItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

fun joinRemote(dir: String, name: String): String = if (dir.endsWith("/")) dir + name else "$dir/$name"

fun parentRemote(path: String): String {
    val t = path.trimEnd('/')
    val i = t.lastIndexOf('/')
    return if (i <= 0) "/" else t.substring(0, i)
}

/** Одна активная FTP-сессия (клиент). Все методы блокирующие — вызывать из IO. */
object RemoteSession {
    private val lock = Any()
    private var client: FTPClient? = null
    private var host = ""
    private var port = 21
    private var user = ""
    private var pass = ""

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    var cwd: String = "/"

    @Volatile
    var label: String = ""

    private fun open(): FTPClient {
        val c = FTPClient()
        c.connectTimeout = 15_000
        c.defaultTimeout = 30_000
        c.controlEncoding = "UTF-8"
        try {
            c.connect(host, port)
            if (!FTPReply.isPositiveCompletion(c.replyCode)) throw IOException(c.replyString.trim())
            if (!c.login(user, pass)) throw IOException("Login failed: " + c.replyString.trim())
            c.enterLocalPassiveMode()
            c.setFileType(FTP.BINARY_FILE_TYPE)
            c.bufferSize = 256 * 1024
            return c
        } catch (e: Exception) {
            try {
                c.disconnect()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun ensure(): FTPClient {
        val c = client
        if (c != null && c.isConnected) {
            try {
                if (c.sendNoOp()) return c
            } catch (_: Exception) {
            }
            try {
                c.disconnect()
            } catch (_: Exception) {
            }
        }
        if (host.isEmpty()) throw IOException("Not connected")
        val n = open()
        client = n
        return n
    }

    fun connect(h: String, p: Int, u: String, pw: String) {
        synchronized(lock) {
            disconnectLocked()
            host = h
            port = p
            user = u
            pass = pw
            client = open()
            cwd = "/"
            label = "$h:$p"
            _connected.value = true
        }
    }

    fun disconnect() {
        synchronized(lock) { disconnectLocked() }
    }

    private fun disconnectLocked() {
        val c = client
        client = null
        host = ""
        _connected.value = false
        if (c != null) {
            try {
                if (c.isConnected) c.logout()
            } catch (_: Exception) {
            }
            try {
                c.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ browsing

    fun list(path: String): List<RemoteItem> = synchronized(lock) { listRaw(ensure(), path) }

    private fun listRaw(c: FTPClient, path: String): List<RemoteItem> {
        if (!c.changeWorkingDirectory(path)) throw IOException("Cannot open $path: " + c.replyString.trim())
        val arr = c.listFiles() ?: return emptyList()
        return arr.filterNotNull()
            .filter { it.name != "." && it.name != ".." }
            .map { f ->
                val name = f.name.substringAfterLast('/')
                RemoteItem(name, joinRemote(path, name), f.isDirectory, f.size, f.timestamp?.timeInMillis ?: 0L)
            }
    }

    fun mkdir(path: String) {
        synchronized(lock) {
            if (!ensure().makeDirectory(path)) throw IOException("Cannot create folder: $path")
        }
    }

    fun rename(from: String, to: String) {
        synchronized(lock) {
            if (!ensure().rename(from, to)) throw IOException("Cannot rename: $from")
        }
    }

    fun delete(items: List<RemoteItem>, t: ProgressTracker) {
        synchronized(lock) {
            val c = ensure()
            t.begin(items.size.toLong(), items.size)
            for (it in items) {
                t.check()
                t.file(it.name)
                deleteRec(c, it, t)
                t.add(1)
                t.fileDone()
            }
        }
    }

    private fun deleteRec(c: FTPClient, item: RemoteItem, t: ProgressTracker) {
        t.check()
        if (item.isDir) {
            for (child in listRaw(c, item.path)) deleteRec(c, child, t)
            if (!c.removeDirectory(item.path)) throw IOException("Cannot delete folder: ${item.name}")
        } else {
            if (!c.deleteFile(item.path)) throw IOException("Cannot delete: ${item.name}")
        }
    }

    // ------------------------------------------------------------------ download

    fun download(items: List<RemoteItem>, destDir: File, t: ProgressTracker) {
        synchronized(lock) {
            val c = ensure()
            var bytes = 0L
            var files = 0
            for (item in items) {
                if (item.isDir) {
                    val (b, n) = scanRemote(c, item.path, t)
                    bytes += b
                    files += n
                } else {
                    bytes += item.size
                    files += 1
                }
            }
            t.begin(bytes, files)
            for (item in items) downloadRec(c, item, destDir, t)
        }
    }

    private fun scanRemote(c: FTPClient, path: String, t: ProgressTracker): Pair<Long, Int> {
        t.check()
        var bytes = 0L
        var files = 0
        for (child in listRaw(c, path)) {
            if (child.isDir) {
                val (b, n) = scanRemote(c, child.path, t)
                bytes += b
                files += n
            } else {
                bytes += child.size
                files += 1
            }
        }
        return bytes to files
    }

    private fun downloadRec(c: FTPClient, item: RemoteItem, destDir: File, t: ProgressTracker) {
        t.check()
        val target = FileOps.uniqueTarget(destDir, item.name)
        if (item.isDir) {
            if (!target.mkdirs()) throw IOException("Cannot create folder: ${target.name}")
            for (child in listRaw(c, item.path)) downloadRec(c, child, target, t)
        } else {
            t.file(item.name)
            retrieve(c, item.path, target, t)
            t.fileDone()
        }
    }

    private fun retrieve(c: FTPClient, remote: String, local: File, t: ProgressTracker) {
        val input = c.retrieveFileStream(remote) ?: throw IOException(c.replyString.trim())
        try {
            FileOutputStream(local).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    t.add(n.toLong())
                }
            }
        } catch (e: Throwable) {
            try {
                input.close()
            } catch (_: Exception) {
            }
            try {
                c.disconnect()
            } catch (_: Exception) {
            }
            local.delete()
            throw e
        }
        input.close()
        if (!c.completePendingCommand()) throw IOException("Download failed: " + c.replyString.trim())
    }

    // ------------------------------------------------------------------ upload

    fun upload(files: List<File>, remoteDir: String, t: ProgressTracker) {
        synchronized(lock) {
            val c = ensure()
            val stats = files.map { FileOps.scan(it, t) }
            t.begin(stats.sumOf { it.bytes }, stats.sumOf { it.files })
            for (f in files) uploadRec(c, f, remoteDir, t)
        }
    }

    private fun uploadRec(c: FTPClient, local: File, remoteDir: String, t: ProgressTracker) {
        t.check()
        if (FileOps.isSymlink(local)) return
        val remote = joinRemote(remoteDir, local.name)
        if (local.isDirectory) {
            c.makeDirectory(remote)
            local.listFiles()?.forEach { uploadRec(c, it, remote, t) }
        } else {
            t.file(local.name)
            store(c, local, remote, t)
            t.fileDone()
        }
    }

    private fun store(c: FTPClient, local: File, remote: String, t: ProgressTracker) {
        val out = c.storeFileStream(remote) ?: throw IOException(c.replyString.trim())
        try {
            FileInputStream(local).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    t.add(n.toLong())
                }
            }
        } catch (e: Throwable) {
            try {
                out.close()
            } catch (_: Exception) {
            }
            try {
                c.disconnect()
            } catch (_: Exception) {
            }
            throw e
        }
        out.close()
        if (!c.completePendingCommand()) throw IOException("Upload failed: " + c.replyString.trim())
    }
}
