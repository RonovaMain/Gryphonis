package dev.ronova.gryphonis.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileOps {

    data class Stats(val bytes: Long, val files: Int)

    private const val BUF = 128 * 1024

    fun isSymlink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    fun list(dir: File): List<FileItem>? = dir.listFiles()?.map { it.toFileItem() }

    fun scan(f: File, t: ProgressTracker? = null): Stats {
        t?.check()
        if (isSymlink(f) || !f.isDirectory) return Stats(if (f.isFile) f.length() else 0L, 1)
        var bytes = 0L
        var files = 0
        f.listFiles()?.forEach { c ->
            val s = scan(c, t)
            bytes += s.bytes
            files += s.files
        }
        return Stats(bytes, files)
    }

    fun countAll(f: File): Int {
        var n = 1
        if (f.isDirectory && !isSymlink(f)) f.listFiles()?.forEach { n += countAll(it) }
        return n
    }

    /** name, name (1), name (2) ... */
    fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (target.exists()) {
            target = File(dir, "$base ($i)$ext")
            i++
        }
        return target
    }

    // ------------------------------------------------------------------ copy / move

    fun copy(sources: List<File>, destDir: File, t: ProgressTracker, move: Boolean) {
        val destCanon = destDir.canonicalFile
        for (s in sources) {
            if (s.isDirectory) {
                val sc = s.canonicalFile
                if (destCanon == sc || destCanon.path.startsWith(sc.path + File.separator)) {
                    throw IOException("Cannot copy a folder into itself: ${s.name}")
                }
            }
        }
        val stats = sources.map { scan(it, t) }
        t.begin(stats.sumOf { it.bytes }, stats.sumOf { it.files })
        sources.forEachIndexed { i, src ->
            t.check()
            if (move && src.parentFile?.canonicalFile == destCanon) {
                t.add(stats[i].bytes)
                t.fileDone(stats[i].files)
                return@forEachIndexed
            }
            val target = uniqueTarget(destDir, src.name)
            if (move) {
                t.file(src.name)
                if (src.renameTo(target)) {
                    t.add(stats[i].bytes)
                    t.fileDone(stats[i].files)
                } else {
                    copyRec(src, target, t)
                    deleteQuiet(src)
                }
            } else {
                copyRec(src, target, t)
            }
        }
    }

    private fun copyRec(src: File, dst: File, t: ProgressTracker) {
        t.check()
        if (isSymlink(src)) return
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) throw IOException("Cannot create folder: ${dst.name}")
            src.listFiles()?.forEach { copyRec(it, File(dst, it.name), t) }
        } else {
            copyFile(src, dst, t)
        }
    }

    private fun copyFile(src: File, dst: File, t: ProgressTracker) {
        t.file(src.name)
        dst.parentFile?.mkdirs()
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    val buf = ByteArray(BUF)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        t.add(n.toLong())
                    }
                }
            }
        } catch (e: Throwable) {
            dst.delete()
            throw e
        }
        dst.setLastModified(src.lastModified())
        t.fileDone()
    }

    private fun deleteQuiet(f: File) {
        try {
            f.deleteRecursively()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ delete

    fun delete(files: List<File>, t: ProgressTracker) {
        val total = files.sumOf { countAll(it) }
        t.begin(total.toLong(), total)
        files.forEach { deleteRec(it, t) }
    }

    private fun deleteRec(f: File, t: ProgressTracker) {
        t.check()
        if (f.isDirectory && !isSymlink(f)) f.listFiles()?.forEach { deleteRec(it, t) }
        t.file(f.name)
        if (!f.delete() && f.exists()) throw IOException("Cannot delete: ${f.name}")
        t.add(1)
        t.fileDone()
    }

    // ------------------------------------------------------------------ zip

    fun zip(sources: List<File>, out: File, t: ProgressTracker) {
        val stats = sources.map { scan(it, t) }
        t.begin(stats.sumOf { it.bytes }, stats.sumOf { it.files })
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
                zos.setLevel(6)
                for (s in sources) addToZip(zos, s, s.parentFile ?: s, out, t)
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }

    private fun addToZip(zos: ZipOutputStream, f: File, base: File, out: File, t: ProgressTracker) {
        t.check()
        if (f.absolutePath == out.absolutePath || isSymlink(f)) return
        val rel = f.relativeTo(base).path.replace(File.separatorChar, '/')
        if (f.isDirectory) {
            zos.putNextEntry(ZipEntry("$rel/"))
            zos.closeEntry()
            f.listFiles()?.forEach { addToZip(zos, it, base, out, t) }
        } else {
            t.file(f.name)
            val entry = ZipEntry(rel)
            entry.time = f.lastModified()
            zos.putNextEntry(entry)
            FileInputStream(f).use { input ->
                val buf = ByteArray(BUF)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    zos.write(buf, 0, n)
                    t.add(n.toLong())
                }
            }
            zos.closeEntry()
            t.fileDone()
        }
    }

    private fun openZip(f: File): ZipFile = try {
        ZipFile(f)
    } catch (e: IllegalArgumentException) {
        ZipFile(f, Charset.forName("IBM866"))
    }

    fun unzip(zip: File, destDir: File, t: ProgressTracker) {
        openZip(zip).use { zf ->
            val entries = zf.entries().toList()
            t.begin(entries.sumOf { maxOf(it.size, 0L) }, entries.count { !it.isDirectory })
            destDir.mkdirs()
            val destCanon = destDir.canonicalPath
            for (e in entries) {
                t.check()
                val target = File(destDir, e.name)
                val canon = target.canonicalPath
                if (canon != destCanon && !canon.startsWith(destCanon + File.separator)) {
                    throw IOException("Unsafe zip entry: ${e.name}")
                }
                if (e.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                t.file(target.name)
                zf.getInputStream(e).use { input ->
                    FileOutputStream(target).use { output ->
                        val buf = ByteArray(BUF)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            t.add(n.toLong())
                        }
                    }
                }
                if (e.time > 0) target.setLastModified(e.time)
                t.fileDone()
            }
        }
    }

    // ------------------------------------------------------------------ search / hash

    fun search(
        root: File,
        query: String,
        showHidden: Boolean,
        shouldContinue: () -> Boolean,
        onFound: (FileItem) -> Unit,
        limit: Int = 1000,
    ) {
        var found = 0
        val walk = root.walkTopDown().onEnter { dir -> dir == root || showHidden || !dir.name.startsWith(".") }
        for (f in walk) {
            if (!shouldContinue()) return
            if (f == root) continue
            if (!showHidden && f.name.startsWith(".")) continue
            if (f.name.contains(query, ignoreCase = true)) {
                onFound(f.toFileItem())
                found++
                if (found >= limit) return
            }
        }
    }

    fun sha256(file: File, t: ProgressTracker): String {
        t.begin(file.length(), 1)
        t.file(file.name)
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(BUF)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                t.add(n.toLong())
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
