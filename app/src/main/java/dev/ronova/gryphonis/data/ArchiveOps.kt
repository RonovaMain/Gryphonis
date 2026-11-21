package dev.ronova.gryphonis.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

/** Просмотр ZIP без распаковки и извлечение отдельных файлов. */
object ArchiveOps {

    private const val BUF = 128 * 1024

    private fun clean(name: String): String = name.replace('\\', '/').trim('/')

    fun readIndex(zip: File): ArchiveIndex {
        FileOps.openZip(zip).use { zf ->
            val map = LinkedHashMap<String, ArchiveEntry>()
            for (e in zf.entries().toList()) {
                val c = clean(e.name)
                if (c.isEmpty() || c.split('/').any { it == ".." }) continue
                val isDir = e.isDirectory
                map[c] = ArchiveEntry(
                    path = c,
                    name = c.substringAfterLast('/'),
                    isDir = isDir,
                    size = if (isDir) 0L else maxOf(e.size, 0L),
                    modified = maxOf(e.time, 0L),
                )
                var p = c.substringBeforeLast('/', "")
                while (p.isNotEmpty()) {
                    if (!map.containsKey(p)) {
                        map[p] = ArchiveEntry(p, p.substringAfterLast('/'), true, 0L, 0L)
                    }
                    p = p.substringBeforeLast('/', "")
                }
            }
            return ArchiveIndex(map.values.toList())
        }
    }

    /**
     * Извлекает выбранные записи (файлы и целые папки) в destDir.
     * Выбранный элемент попадает в destDir под своим именем, без родительских папок архива.
     */
    fun extractSelected(zip: File, selected: List<String>, destDir: File, t: ProgressTracker) {
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
                target.parentFile?.mkdirs()
                t.file(target.name)
                try {
                    zf.getInputStream(e).use { input ->
                        FileOutputStream(target).use { output ->
                            val buf = ByteArray(BUF)
                            while (true) {
                                val r = input.read(buf)
                                if (r < 0) break
                                output.write(buf, 0, r)
                                t.add(r.toLong())
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    target.delete()
                    throw ex
                }
                if (e.time > 0) target.setLastModified(e.time)
                t.fileDone()
            }
        }
    }
}
