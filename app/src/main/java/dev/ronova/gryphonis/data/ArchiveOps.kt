package dev.ronova.gryphonis.data

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry

data class ArchiveEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

class ArchiveIndex(val all: List<ArchiveEntry>) {
    private val byParent: Map<String, List<ArchiveEntry>> = all.groupBy { it.path.substringBeforeLast('/', "") }

    /** dir — путь папки внутри архива без слэшей по краям; "" — корень. */
    fun children(dir: String): List<ArchiveEntry> = byParent[dir] ?: emptyList()
}

/** Форматы, которые проводник умеет открывать как папку. RAR сюда не входит. */
enum class ArchiveFormat {
    ZIP, SEVENZ, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ, BZ2, XZ;

    companion object {
        fun of(name: String): ArchiveFormat? {
            val n = name.lowercase()
            return when {
                n.endsWith(".zip") -> ZIP
                n.endsWith(".7z") -> SEVENZ
                n.endsWith(".tar.gz") || n.endsWith(".tgz") -> TAR_GZ
                n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> TAR_BZ2
                n.endsWith(".tar.xz") || n.endsWith(".txz") -> TAR_XZ
                n.endsWith(".tar") -> TAR
                n.endsWith(".gz") -> GZ
                n.endsWith(".bz2") -> BZ2
                n.endsWith(".xz") -> XZ
                else -> null
            }
        }
    }
}

/** Просмотр архивов без распаковки, потоковое чтение отдельных файлов и их извлечение. */
object ArchiveOps {

    private const val BUF = 128 * 1024

    fun isBrowsable(name: String): Boolean = ArchiveFormat.of(name) != null

    /** Имя без расширения архива: «a.tar.gz» -> «a». */
    fun baseName(name: String): String {
        val n = name.lowercase()
        for (suf in listOf(".tar.gz", ".tar.bz2", ".tar.xz")) if (n.endsWith(suf)) return name.dropLast(suf.length)
        return name.substringBeforeLast('.')
    }

    private fun formatOf(file: File): ArchiveFormat = ArchiveFormat.of(file.name) ?: ArchiveFormat.ZIP

    /** Убирает «./», двойные и крайние слэши; обратные слэши (Windows) превращает в обычные. */
    private fun clean(name: String): String =
        name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")

    private fun unsafe(path: String): Boolean = path.isEmpty() || path.split('/').any { it == ".." }

    private fun buildIndex(raw: List<ArchiveEntry>): ArchiveIndex {
        val map = LinkedHashMap<String, ArchiveEntry>()
        for (e in raw) {
            map[e.path] = e
            var p = e.path.substringBeforeLast('/', "")
            while (p.isNotEmpty()) {
                if (!map.containsKey(p)) map[p] = ArchiveEntry(p, p.substringAfterLast('/'), true, 0L, 0L)
                p = p.substringBeforeLast('/', "")
            }
        }
        return ArchiveIndex(map.values.toList())
    }

    // ------------------------------------------------------------------ декодеры

    private fun decoded(file: File, fmt: ArchiveFormat): InputStream {
        val raw = BufferedInputStream(FileInputStream(file), BUF)
        return try {
            when (fmt) {
                ArchiveFormat.TAR_GZ, ArchiveFormat.GZ -> GzipCompressorInputStream(raw, true)
                ArchiveFormat.TAR_BZ2, ArchiveFormat.BZ2 -> BZip2CompressorInputStream(raw, true)
                ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> XZCompressorInputStream(raw, true)
                else -> raw
            }
        } catch (e: Throwable) {
            try {
                raw.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun singleName(file: File): String = file.name.substringBeforeLast('.')

    /** gzip хранит размер распакованных данных в последних 4 байтах (по модулю 4 ГБ). */
    private fun gzSize(file: File): Long = try {
        RandomAccessFile(file, "r").use { r ->
            if (r.length() < 4) {
                0L
            } else {
                r.seek(r.length() - 4)
                val b = ByteArray(4)
                r.readFully(b)
                (b[0].toLong() and 0xff) or ((b[1].toLong() and 0xff) shl 8) or
                    ((b[2].toLong() and 0xff) shl 16) or ((b[3].toLong() and 0xff) shl 24)
            }
        }
    } catch (e: Exception) {
        0L
    }

    // ------------------------------------------------------------------ оглавление

    fun readIndex(file: File): ArchiveIndex = when (val fmt = formatOf(file)) {
        ArchiveFormat.ZIP -> readZipIndex(file)
        ArchiveFormat.SEVENZ -> read7zIndex(file)
        else -> {
            val list = ArrayList<ArchiveEntry>()
            walk(file, fmt) { e, _ ->
                list.add(e)
                true
            }
            buildIndex(list)
        }
    }

    private fun readZipIndex(zip: File): ArchiveIndex {
        FileOps.openZip(zip).use { zf ->
            val list = ArrayList<ArchiveEntry>()
            for (e in zf.entries().toList()) {
                val c = clean(e.name)
                if (unsafe(c)) continue
                list.add(
                    ArchiveEntry(
                        path = c,
                        name = c.substringAfterLast('/'),
                        isDir = e.isDirectory,
                        size = if (e.isDirectory) 0L else maxOf(e.size, 0L),
                        modified = maxOf(e.time, 0L),
                    )
                )
            }
            return buildIndex(list)
        }
    }

    private fun read7zIndex(file: File): ArchiveIndex {
        SevenZFile(file).use { sz ->
            val list = ArrayList<ArchiveEntry>()
            for (e in sz.entries) {
                if (e.isAntiItem) continue
                val c = clean(e.name)
                if (unsafe(c)) continue
                list.add(
                    ArchiveEntry(
                        path = c,
                        name = c.substringAfterLast('/'),
                        isDir = e.isDirectory,
                        size = if (e.isDirectory) 0L else maxOf(e.size, 0L),
                        modified = if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L,
                    )
                )
            }
            return buildIndex(list)
        }
    }

    /**
     * Последовательный обход tar и одиночных сжатых файлов. visit возвращает false, чтобы остановиться.
     * Поток внутри visit читает только данные текущей записи.
     */
    private fun walk(file: File, fmt: ArchiveFormat, visit: (ArchiveEntry, InputStream) -> Boolean) {
        when (fmt) {
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ -> {
                TarArchiveInputStream(decoded(file, fmt)).use { tar ->
                    while (true) {
                        val te = tar.nextTarEntry ?: break
                        val c = clean(te.name)
                        if (unsafe(c)) continue
                        if (te.isSymbolicLink || te.isLink) continue
                        val dir = te.isDirectory
                        val e = ArchiveEntry(
                            path = c,
                            name = c.substringAfterLast('/'),
                            isDir = dir,
                            size = if (dir) 0L else maxOf(te.size, 0L),
                            modified = te.modTime?.time ?: 0L,
                        )
                        if (!visit(e, tar)) break
                    }
                }
            }
            ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ -> {
                decoded(file, fmt).use { input ->
                    val name = singleName(file)
                    val size = if (fmt == ArchiveFormat.GZ) gzSize(file) else 0L
                    visit(ArchiveEntry(name, name, false, size, file.lastModified()), input)
                }
            }
            ArchiveFormat.SEVENZ -> {
                SevenZFile(file).use { sz ->
                    val stream = object : InputStream() {
                        override fun read(): Int = sz.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int = sz.read(b, off, len)
                    }
                    while (true) {
                        val se = sz.nextEntry ?: break
                        if (se.isAntiItem) continue
                        val c = clean(se.name)
                        if (unsafe(c)) continue
                        val dir = se.isDirectory
                        val e = ArchiveEntry(
                            path = c,
                            name = c.substringAfterLast('/'),
                            isDir = dir,
                            size = if (dir) 0L else maxOf(se.size, 0L),
                            modified = if (se.hasLastModifiedDate) se.lastModifiedDate.time else 0L,
                        )
                        if (!visit(e, stream)) break
                    }
                }
            }
            ArchiveFormat.ZIP -> throw IllegalStateException("ZIP is read through ZipFile")
        }
    }

    // ------------------------------------------------------------------ потоковое чтение одной записи

    /**
     * Открывает поток данных одной записи без распаковки на диск.
     * Закрытие потока закрывает и сам архив. ZIP и 7z читаются с произвольного места, tar — подряд.
     */
    fun openEntry(file: File, path: String): InputStream {
        val want = clean(path)
        return when (val fmt = formatOf(file)) {
            ArchiveFormat.ZIP -> openZipEntry(file, want, path)
            ArchiveFormat.SEVENZ -> open7zEntry(file, want, path)
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ ->
                openTarEntry(file, fmt, want, path)
            ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ -> {
                if (want != singleName(file)) throw FileNotFoundException(path)
                decoded(file, fmt)
            }
        }
    }

    private fun openZipEntry(file: File, want: String, path: String): InputStream {
        val zf = FileOps.openZip(file)
        try {
            val e = zf.entries().asSequence().firstOrNull { !it.isDirectory && clean(it.name) == want }
                ?: throw FileNotFoundException(path)
            val input = zf.getInputStream(e)
            return object : FilterInputStream(input) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        zf.close()
                    }
                }
            }
        } catch (ex: Throwable) {
            zf.close()
            throw ex
        }
    }

    private fun open7zEntry(file: File, want: String, path: String): InputStream {
        val sz = SevenZFile(file)
        try {
            val e = sz.entries.firstOrNull { !it.isDirectory && !it.isAntiItem && clean(it.name) == want }
                ?: throw FileNotFoundException(path)
            val input = sz.getInputStream(e)
            return object : FilterInputStream(input) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        sz.close()
                    }
                }
            }
        } catch (ex: Throwable) {
            sz.close()
            throw ex
        }
    }

    private fun openTarEntry(file: File, fmt: ArchiveFormat, want: String, path: String): InputStream {
        val tar = TarArchiveInputStream(decoded(file, fmt))
        try {
            while (true) {
                val te = tar.nextTarEntry ?: throw FileNotFoundException(path)
                if (!te.isDirectory && !te.isSymbolicLink && !te.isLink && clean(te.name) == want) {
                    return object : FilterInputStream(tar) {
                        override fun close() {
                            tar.close()
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            tar.close()
            throw ex
        }
    }

    // ------------------------------------------------------------------ извлечение

    /**
     * Извлекает выбранные записи (файлы и целые папки) в destDir.
     * Выбранный элемент попадает в destDir под своим именем, без родительских папок архива.
     */
    fun extractSelected(archive: File, selected: List<String>, destDir: File, t: ProgressTracker) {
        if (formatOf(archive) == ArchiveFormat.ZIP) {
            extractZip(archive, selected, destDir, t)
        } else {
            extractSequential(archive, selected, destDir, t)
        }
    }

    /** Полная распаковка не-ZIP архива в destDir. */
    fun extractAll(archive: File, destDir: File, t: ProgressTracker) {
        val tops = readIndex(archive).children("").map { it.path }
        extractSelected(archive, tops, destDir, t)
    }

    private fun copyEntry(input: InputStream, target: File, modified: Long, t: ProgressTracker) {
        target.parentFile?.mkdirs()
        t.file(target.name)
        try {
            FileOutputStream(target).use { output ->
                val buf = ByteArray(BUF)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    output.write(buf, 0, r)
                    t.add(r.toLong())
                }
            }
        } catch (ex: Throwable) {
            target.delete()
            throw ex
        }
        if (modified > 0) target.setLastModified(modified)
        t.fileDone()
    }

    private fun extractSequential(archive: File, selected: List<String>, destDir: File, t: ProgressTracker) {
        val fmt = formatOf(archive)
        val index = readIndex(archive)
        val byPath = index.all.associateBy { it.path }
        destDir.mkdirs()
        val destCanon = destDir.canonicalPath
        val targets = LinkedHashMap<String, MutableList<File>>()
        val used = HashSet<String>()
        var bytes = 0L
        var files = 0
        for (sel in selected) {
            val top = clean(sel)
            if (top.isEmpty()) continue
            val topName = top.substringAfterLast('/')
            var topTarget = FileOps.uniqueTarget(destDir, topName)
            var n = 1
            while (!used.add(topTarget.absolutePath)) {
                topTarget = FileOps.uniqueTarget(destDir, "$topName ($n)")
                n++
            }
            for (e in index.all) {
                if (e.path == top || e.path.startsWith("$top/")) {
                    val rest = e.path.removePrefix(top).trimStart('/')
                    val target = if (rest.isEmpty()) topTarget else File(topTarget, rest)
                    val canon = target.canonicalPath
                    if (canon != destCanon && !canon.startsWith(destCanon + File.separator)) {
                        throw IOException("Unsafe archive entry: ${e.path}")
                    }
                    targets.getOrPut(e.path) { ArrayList() }.add(target)
                    if (!e.isDir) {
                        bytes += e.size
                        files++
                    }
                }
            }
        }
        t.begin(bytes, files)
        val pending = HashSet<String>()
        for ((path, list) in targets) {
            if (byPath[path]?.isDir == true) list.forEach { it.mkdirs() } else pending.add(path)
        }
        if (pending.isEmpty()) return
        val visitor = { e: ArchiveEntry, input: InputStream ->
            t.check()
            val list = targets[e.path]
            if (list != null && !e.isDir && pending.remove(e.path)) {
                copyEntry(input, list[0], e.modified, t)
                for (i in 1 until list.size) {
                    // выбрали и папку, и файл из неё: второй экземпляр копируем уже с диска
                    list[i].parentFile?.mkdirs()
                    list[0].copyTo(list[i], overwrite = true)
                }
            }
            pending.isNotEmpty()
        }
        walk(archive, fmt, visitor)
    }

    private fun extractZip(zip: File, selected: List<String>, destDir: File, t: ProgressTracker) {
        FileOps.openZip(zip).use { zf ->
            val named = zf.entries().toList().map { it to clean(it.name) }
            destDir.mkdirs()
            val destCanon = destDir.canonicalPath
            val plan = ArrayList<Triple<ZipEntry, Boolean, File>>()
            val used = HashSet<String>()
            var bytes = 0L
            var files = 0
            for (sel in selected) {
                val top = clean(sel)
                if (top.isEmpty()) continue
                val topName = top.substringAfterLast('/')
                var topTarget = FileOps.uniqueTarget(destDir, topName)
                var n = 1
                while (!used.add(topTarget.absolutePath)) {
                    topTarget = FileOps.uniqueTarget(destDir, "$topName ($n)")
                    n++
                }
                for ((e, c) in named) {
                    if (c == top || c.startsWith("$top/")) {
                        val rest = c.removePrefix(top).trimStart('/')
                        val target = if (rest.isEmpty()) topTarget else File(topTarget, rest)
                        val canon = target.canonicalPath
                        if (canon != destCanon && !canon.startsWith(destCanon + File.separator)) {
                            throw IOException("Unsafe zip entry: ${e.name}")
                        }
                        plan.add(Triple(e, e.isDirectory, target))
                        if (!e.isDirectory) {
                            bytes += maxOf(e.size, 0L)
                            files++
                        }
                    }
                }
            }
            t.begin(bytes, files)
            for ((e, isDir, target) in plan) {
                t.check()
                if (isDir) {
                    target.mkdirs()
                    continue
                }
                zf.getInputStream(e).use { input -> copyEntry(input, target, e.time, t) }
            }
        }
    }
}
