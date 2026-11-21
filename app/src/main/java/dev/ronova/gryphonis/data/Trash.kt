package dev.ronova.gryphonis.data

import android.content.Context
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

private const val DAY_MS = 86_400_000L

/** Один элемент в корзине приложения. Лежит в папке <корзина>/<id>/ вместе с meta.json. */
data class TrashItem(
    val id: String,
    val entryDir: File,
    val name: String,
    val originalPath: String,
    val deletedAt: Long,
    val size: Long,
    val isDirectory: Boolean,
) {
    val payload: File get() = File(entryDir, "data")

    fun daysLeft(retentionDays: Int, now: Long = System.currentTimeMillis()): Int {
        val left = deletedAt + retentionDays * DAY_MS - now
        return if (left <= 0L) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    fun asFileItem(): FileItem {
        val ext = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
        return FileItem(
            file = payload,
            name = name,
            isDirectory = isDirectory,
            size = size,
            modified = deletedAt,
            extension = ext,
            kind = kindOf(isDirectory, ext),
            isHidden = false,
        )
    }
}

/**
 * Корзина хранится в папке приложения на том же накопителе, где лежал файл
 * (Android/data/<пакет>/files/trash), поэтому перемещение — мгновенное переименование.
 * При удалении приложения корзина удаляется вместе с ним.
 */
object Trash {
    private const val DIR = "trash"
    private const val DATA = "data"
    private const val META = "meta.json"

    fun roots(ctx: Context): List<File> {
        val list = ArrayList<File>()
        for (d in ContextCompat.getExternalFilesDirs(ctx, null)) {
            if (d != null) list.add(File(d, DIR))
        }
        list.add(File(ctx.filesDir, DIR))
        return list
    }

    private fun rootFor(ctx: Context, f: File): File {
        val path = f.absolutePath
        val dirs = ContextCompat.getExternalFilesDirs(ctx, null)
        for (d in dirs) {
            if (d == null) continue
            val p = d.absolutePath
            val idx = p.indexOf("/Android/data")
            if (idx > 0 && path.startsWith(p.substring(0, idx) + "/")) return File(d, DIR)
        }
        val first = dirs.firstOrNull { it != null }
        return File(first ?: ctx.filesDir, DIR)
    }

    fun isInTrash(ctx: Context, f: File): Boolean {
        val path = f.absolutePath
        return roots(ctx).any { path.startsWith(it.absolutePath + File.separator) }
    }

    fun list(ctx: Context): List<TrashItem> {
        val out = ArrayList<TrashItem>()
        for (root in roots(ctx)) {
            val dirs = root.listFiles() ?: continue
            for (d in dirs) {
                val meta = File(d, META)
                if (!d.isDirectory || !meta.isFile) continue
                try {
                    val j = JSONObject(meta.readText())
                    if (!File(d, DATA).exists()) continue
                    out.add(
                        TrashItem(
                            id = d.name,
                            entryDir = d,
                            name = j.getString("name"),
                            originalPath = j.optString("path", ""),
                            deletedAt = j.optLong("time", d.lastModified()),
                            size = j.optLong("size", 0L),
                            isDirectory = j.optBoolean("dir", false),
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
        return out.sortedByDescending { it.deletedAt }
    }

    private fun writeMeta(entry: File, original: File, bytes: Long, isDir: Boolean) {
        val j = JSONObject()
        j.put("name", original.name)
        j.put("path", original.absolutePath)
        j.put("time", System.currentTimeMillis())
        j.put("size", bytes)
        j.put("dir", isDir)
        File(entry, META).writeText(j.toString())
    }

    fun moveToTrash(ctx: Context, files: List<File>, t: ProgressTracker) {
        val stats = files.map { FileOps.scan(it, t) }
        t.begin(stats.sumOf { it.bytes }, stats.sumOf { it.files })
        files.forEachIndexed { i, f ->
            t.check()
            t.file(f.name)
            if (isInTrash(ctx, f)) {
                // то, что уже лежит внутри корзины, удаляем окончательно
                FileOps.deleteQuiet(f)
                t.add(stats[i].bytes)
                t.fileDone(stats[i].files)
                return@forEachIndexed
            }
            val entry = File(rootFor(ctx, f), UUID.randomUUID().toString())
            if (!entry.mkdirs()) throw IOException("Cannot create trash folder")
            val payload = File(entry, DATA)
            var renamed = false
            try {
                writeMeta(entry, f, stats[i].bytes, f.isDirectory)
                renamed = f.renameTo(payload)
                if (!renamed) {
                    FileOps.copyRec(f, payload, t)
                    FileOps.deleteQuiet(f)
                }
            } catch (e: Throwable) {
                FileOps.deleteQuiet(entry)
                throw e
            }
            if (renamed) {
                t.add(stats[i].bytes)
                t.fileDone(stats[i].files)
            }
        }
    }

    fun restore(items: List<TrashItem>, t: ProgressTracker) {
        t.begin(items.sumOf { it.size }, items.size)
        for (item in items) {
            t.check()
            t.file(item.name)
            val parent = File(item.originalPath).parentFile ?: throw IOException("Unknown location: ${item.name}")
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create folder: ${parent.path}")
            val target = FileOps.uniqueTarget(parent, item.name)
            val moved = item.payload.renameTo(target)
            if (!moved) {
                FileOps.copyRec(item.payload, target, t)
                FileOps.deleteQuiet(item.payload)
            }
            FileOps.deleteQuiet(item.entryDir)
            if (moved) t.add(item.size)
            t.fileDone()
        }
    }

    fun deleteForever(items: List<TrashItem>, t: ProgressTracker) {
        t.begin(items.size.toLong(), items.size)
        for (item in items) {
            t.check()
            t.file(item.name)
            FileOps.deleteQuiet(item.entryDir)
            t.add(1)
            t.fileDone()
        }
    }

    /** Удаляет всё, что пролежало дольше срока хранения, и осиротевшие записи. */
    fun purgeExpired(ctx: Context, retentionDays: Int, now: Long = System.currentTimeMillis()) {
        val limit = now - retentionDays * DAY_MS
        for (root in roots(ctx)) {
            val dirs = root.listFiles() ?: continue
            for (d in dirs) {
                if (!d.isDirectory) continue
                val meta = File(d, META)
                val time = try {
                    if (meta.isFile) JSONObject(meta.readText()).optLong("time", d.lastModified()) else d.lastModified()
                } catch (_: Exception) {
                    d.lastModified()
                }
                val orphan = !File(d, DATA).exists() && now - d.lastModified() > DAY_MS
                if (time < limit || orphan) FileOps.deleteQuiet(d)
            }
        }
    }
}
