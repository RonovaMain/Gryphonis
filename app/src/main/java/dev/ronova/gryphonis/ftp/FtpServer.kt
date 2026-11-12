package dev.ronova.gryphonis.ftp

import dev.ronova.gryphonis.util.NetUtil
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private val PRE_LOGIN = setOf("USER", "PASS", "QUIT", "FEAT", "OPTS", "SYST", "NOOP", "AUTH", "PBSZ", "PROT")

/**
 * Небольшой FTP-сервер (RFC 959 + часть расширений): работает поверх обычных сокетов,
 * без сторонних библиотек. Корень сервера — [root], доступ только по логину и паролю.
 */
class FtpServer(
    private val root: File,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val readOnly: Boolean,
    private val log: (String) -> Unit,
    private val onClients: (Int) -> Unit,
) {
    val bytesSent = AtomicLong()
    val bytesReceived = AtomicLong()

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val sessions: MutableSet<Session> = Collections.synchronizedSet(HashSet<Session>())

    @Volatile
    private var running = false

    fun start() {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(port))
        server = s
        running = true
        pool.execute {
            while (running) {
                try {
                    val c = s.accept()
                    val session = Session(c)
                    sessions.add(session)
                    onClients(sessions.size)
                    pool.execute(session)
                } catch (e: IOException) {
                    if (!running) break
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (_: Exception) {
        }
        val copy = synchronized(sessions) { sessions.toList() }
        copy.forEach { it.close() }
        pool.shutdownNow()
    }

    private fun pump(input: InputStream, output: OutputStream, counter: AtomicLong) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            counter.addAndGet(n.toLong())
        }
    }

    private fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (p in path.split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(p)
            }
        }
        return "/" + parts.joinToString("/")
    }

    private inner class Session(private val control: Socket) : Runnable {
        private val reader = BufferedReader(InputStreamReader(control.getInputStream(), Charsets.UTF_8))
        private val out: OutputStream = control.getOutputStream()
        private val peerName: String = control.inetAddress?.hostAddress ?: "?"
        private var cwd = "/"
        private var pendingUser: String? = null
        private var loggedIn = false
        private var pasv: ServerSocket? = null
        private var active: InetSocketAddress? = null
        private var renameFrom: File? = null
        private var restart = 0L
        private var failCount = 0
        private val recentFmt = SimpleDateFormat("MMM dd HH:mm", Locale.US)
        private val oldFmt = SimpleDateFormat("MMM dd  yyyy", Locale.US)

        fun close() {
            try {
                control.close()
            } catch (_: Exception) {
            }
            closePasv()
        }

        override fun run() {
            try {
                control.soTimeout = 15 * 60 * 1000
                log("[$peerName] connected")
                reply("220 Gryphonis FTP ready")
                while (running) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    if (!handle(line.trim())) break
                }
            } catch (e: Exception) {
                // клиент отключился
            } finally {
                close()
                sessions.remove(this)
                onClients(sessions.size)
                log("[$peerName] disconnected")
            }
        }

        @Synchronized
        private fun reply(text: String) {
            out.write((text + "\r\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }

        private fun vpath(arg: String): String = normalize(
            when {
                arg.isEmpty() -> cwd
                arg.startsWith("/") -> arg
                cwd == "/" -> "/$arg"
                else -> "$cwd/$arg"
            }
        )

        private fun resolve(arg: String): File = File(root, vpath(arg).removePrefix("/"))

        private fun writeGuard(): Boolean {
            if (readOnly) {
                reply("550 Server is in read-only mode")
                return false
            }
            return true
        }

        private fun handle(line: String): Boolean {
            val sp = line.indexOf(' ')
            val cmd = (if (sp < 0) line else line.substring(0, sp)).uppercase(Locale.ROOT)
            val arg = if (sp < 0) "" else line.substring(sp + 1).trim()
            log("[$peerName] " + (if (cmd == "PASS") "PASS ****" else line))

            if (!loggedIn && cmd !in PRE_LOGIN) {
                reply("530 Please log in with USER and PASS")
                return true
            }
            when (cmd) {
                "USER" -> {
                    pendingUser = arg
                    loggedIn = false
                    reply("331 Password required")
                }
                "PASS" -> doPass(arg)
                "QUIT" -> {
                    reply("221 Goodbye")
                    return false
                }
                "SYST" -> reply("215 UNIX Type: L8")
                "FEAT" -> reply("211-Features:\r\n UTF8\r\n SIZE\r\n MDTM\r\n REST STREAM\r\n EPSV\r\n211 End")
                "OPTS" -> reply(if (arg.uppercase(Locale.ROOT).startsWith("UTF8")) "200 UTF8 enabled" else "501 Option not supported")
                "NOOP" -> reply("200 OK")
                "TYPE", "MODE", "STRU" -> reply("200 OK")
                "PWD", "XPWD" -> reply("257 \"" + cwd.replace("\"", "\"\"") + "\" is the current directory")
                "CWD", "XCWD" -> {
                    if (resolve(arg).isDirectory) {
                        cwd = vpath(arg)
                        reply("250 Directory changed")
                    } else {
                        reply("550 No such directory")
                    }
                }
                "CDUP", "XCUP" -> {
                    cwd = normalize("$cwd/..")
                    reply("250 Directory changed")
                }
                "PASV" -> doPasv()
                "EPSV" -> {
                    val ss = newPasv()
                    reply("229 Entering Extended Passive Mode (|||${ss.localPort}|)")
                }
                "PORT" -> doPort(arg)
                "LIST" -> doList(arg, false)
                "NLST" -> doList(arg, true)
                "RETR" -> doRetr(arg)
                "STOR" -> doStor(arg, false)
                "APPE" -> doStor(arg, true)
                "REST" -> {
                    restart = arg.toLongOrNull() ?: 0L
                    reply("350 Restarting at $restart")
                }
                "DELE" -> {
                    if (writeGuard()) {
                        val f = resolve(arg)
                        if (f.isFile && f.delete()) reply("250 Deleted") else reply("550 Cannot delete")
                    }
                }
                "MKD", "XMKD" -> {
                    if (writeGuard()) {
                        val f = resolve(arg)
                        if (!f.exists() && f.mkdirs()) {
                            reply("257 \"" + vpath(arg).replace("\"", "\"\"") + "\" created")
                        } else {
                            reply("550 Cannot create directory")
                        }
                    }
                }
                "RMD", "XRMD" -> {
                    if (writeGuard()) {
                        val f = resolve(arg)
                        if (f.isDirectory && f != root && f.delete()) reply("250 Removed") else reply("550 Cannot remove directory (not empty?)")
                    }
                }
                "RNFR" -> {
                    if (writeGuard()) {
                        val f = resolve(arg)
                        if (f.exists()) {
                            renameFrom = f
                            reply("350 Ready for RNTO")
                        } else {
                            reply("550 File not found")
                        }
                    }
                }
                "RNTO" -> {
                    if (writeGuard()) {
                        val from = renameFrom
                        renameFrom = null
                        if (from == null) {
                            reply("503 Send RNFR first")
                        } else if (from.renameTo(resolve(arg))) {
                            reply("250 Renamed")
                        } else {
                            reply("550 Rename failed")
                        }
                    }
                }
                "SIZE" -> {
                    val f = resolve(arg)
                    if (f.isFile) reply("213 ${f.length()}") else reply("550 Not a file")
                }
                "MDTM" -> {
                    val f = resolve(arg)
                    if (f.exists()) {
                        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                        fmt.timeZone = TimeZone.getTimeZone("UTC")
                        reply("213 " + fmt.format(Date(f.lastModified())))
                    } else {
                        reply("550 Not found")
                    }
                }
                "ABOR" -> reply("226 OK")
                "AUTH", "PBSZ", "PROT" -> reply("502 TLS is not supported")
                "STAT" -> reply("211 Gryphonis FTP")
                else -> reply("502 Command not implemented")
            }
            return true
        }

        private fun doPass(pw: String) {
            val u = pendingUser
            if (u == null) {
                reply("503 Send USER first")
                return
            }
            val okUser = MessageDigest.isEqual(u.toByteArray(Charsets.UTF_8), username.toByteArray(Charsets.UTF_8))
            val okPass = MessageDigest.isEqual(pw.toByteArray(Charsets.UTF_8), password.toByteArray(Charsets.UTF_8))
            if (okUser && okPass) {
                loggedIn = true
                failCount = 0
                reply("230 Login successful")
            } else {
                failCount++
                Thread.sleep(1000L * minOf(failCount, 5))
                reply("530 Login incorrect")
                if (failCount >= 5) close()
            }
        }

        // ---------------------------------------------------------- data channel

        private fun closePasv() {
            try {
                pasv?.close()
            } catch (_: Exception) {
            }
            pasv = null
        }

        private fun newPasv(): ServerSocket {
            closePasv()
            active = null
            val ss = ServerSocket(0)
            ss.soTimeout = 30_000
            pasv = ss
            return ss
        }

        private fun ipv4Bytes(): ByteArray {
            val a = control.localAddress?.address
            if (a != null && a.size == 4) return a
            val ip = NetUtil.localIps().firstOrNull()?.split('.')?.mapNotNull { it.toIntOrNull()?.toByte() }
            return if (ip != null && ip.size == 4) ip.toByteArray() else byteArrayOf(127, 0, 0, 1)
        }

        private fun doPasv() {
            val ss = newPasv()
            val ip = ipv4Bytes().joinToString(",") { (it.toInt() and 0xFF).toString() }
            val p = ss.localPort
            reply("227 Entering Passive Mode ($ip,${p shr 8},${p and 0xFF})")
        }

        private fun doPort(arg: String) {
            val p = arg.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (p.size != 6) {
                reply("501 Bad PORT argument")
                return
            }
            val host = p.take(4).joinToString(".")
            if (host != control.inetAddress?.hostAddress) {
                reply("501 PORT address must match the client address")
                return
            }
            closePasv()
            active = InetSocketAddress(host, p[4] * 256 + p[5])
            reply("200 PORT command successful")
        }

        private fun hasDataChannel(): Boolean {
            if (pasv == null && active == null) {
                reply("425 Use PASV or PORT first")
                return false
            }
            return true
        }

        private fun openData(): Socket? {
            val ps = pasv
            if (ps != null) {
                val s = ps.accept()
                closePasv()
                if (s.inetAddress != control.inetAddress) {
                    s.close()
                    return null
                }
                s.soTimeout = 60_000
                return s
            }
            val target = active
            if (target != null) {
                val s = Socket()
                s.connect(target, 15_000)
                s.soTimeout = 60_000
                return s
            }
            return null
        }

        private fun withData(block: (Socket) -> Unit) {
            reply("150 Opening data connection")
            var data: Socket? = null
            try {
                val s = openData()
                data = s
                if (s == null) {
                    reply("425 Cannot open data connection")
                    return
                }
                block(s)
                s.close()
                reply("226 Transfer complete")
            } catch (e: Exception) {
                reply("426 Transfer aborted")
            } finally {
                try {
                    data?.close()
                } catch (_: Exception) {
                }
                closePasv()
                active = null
            }
        }

        // ---------------------------------------------------------- commands

        private fun unixLine(f: File): String {
            val dir = f.isDirectory
            val perm = if (readOnly) (if (dir) "dr-xr-xr-x" else "-r--r--r--") else (if (dir) "drwxr-xr-x" else "-rw-r--r--")
            val size = if (dir) 4096L else f.length()
            val recent = System.currentTimeMillis() - f.lastModified() < 180L * 24 * 3600 * 1000
            val date = (if (recent) recentFmt else oldFmt).format(Date(f.lastModified()))
            return "$perm 1 gryphonis gryphonis $size $date ${f.name}"
        }

        private fun doList(argRaw: String, namesOnly: Boolean) {
            var arg = argRaw
            if (arg.startsWith("-")) arg = arg.substringAfter(' ', "").trim()
            val target = resolve(arg)
            if (!target.exists()) {
                reply("550 No such file or directory")
                return
            }
            if (!hasDataChannel()) return
            val entries: List<File> = if (target.isDirectory) {
                (target.listFiles()?.toList() ?: emptyList()).sortedBy { it.name.lowercase() }
            } else {
                listOf(target)
            }
            withData { s ->
                val w = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                for (f in entries) {
                    w.write(if (namesOnly) f.name else unixLine(f))
                    w.write("\r\n")
                }
                w.flush()
            }
        }

        private fun doRetr(arg: String) {
            val f = resolve(arg)
            if (!f.isFile || !f.canRead()) {
                reply("550 File unavailable")
                return
            }
            if (!hasDataChannel()) return
            val offset = restart
            restart = 0L
            withData { s ->
                FileInputStream(f).use { input ->
                    if (offset > 0) input.skip(offset)
                    pump(input, s.getOutputStream(), bytesSent)
                }
                s.getOutputStream().flush()
            }
        }

        private fun doStor(arg: String, append: Boolean) {
            if (!writeGuard()) return
            val f = resolve(arg)
            if (f.isDirectory || f.parentFile?.isDirectory != true) {
                reply("550 Cannot write file")
                return
            }
            if (!hasDataChannel()) return
            val offset = restart
            restart = 0L
            withData { s ->
                if (!append && offset > 0) {
                    RandomAccessFile(f, "rw").use { raf ->
                        raf.seek(offset)
                        val buf = ByteArray(64 * 1024)
                        val input = s.getInputStream()
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            bytesReceived.addAndGet(n.toLong())
                        }
                    }
                } else {
                    FileOutputStream(f, append).use { fo -> pump(s.getInputStream(), fo, bytesReceived) }
                }
            }
        }
    }
}
