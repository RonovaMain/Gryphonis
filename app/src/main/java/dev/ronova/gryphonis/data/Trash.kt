package dev.ronova.gryphonis.data

import android.content.Context
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID

private const val DAY_MS = 86_400_000L

/** Один элемент корзины. */
data class TrashItem(
    val id: String,
    val name: String,
    val originalPath: String,
    val deletedAt: Long,
    val size: Long,
    val isDirectory: Boolean,
    /** Где файл лежит сейчас (скрытое имя рядом с оригиналом или файл из корзины старой версии). */
    val payload: File,
    /** Только для корзины старой версии (папка в данных приложения). */
    val entryDir: File? = null,
) {
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
 * Корзина «как в системе»: удаление — это мгновенное переименование файла на месте в скрытое имя
 * `.trashed-<срок>-<имя>` (так же называет такие файлы сама Android). Данные не копируются и не
 * перезаписываются, носитель не изнашивается, приложение не растёт. Файл исчезает из списков других
 * приложений, а место на накопителе освобождается только когда срок хранения истёк или корзину очистили.
 *
 * В данных приложения лежит лишь крошечный список (trash_index.json): что и откуда удалено.
 */
object Trash {
    const val PREFIX = ".trashed-"

    // запасное имя на случай, если система не даст переименовать в «.trashed-…»
    private const val ALT_PREFIX = ".gtrash-"

    fun isTrashName(name: String): Boolean = name.startsWith(PREFIX) || name.startsWith(ALT_PREFIX)
    private const val INDEX = "trash_index.json"

    // скрытое имя получает срок «с запасом»: наш собственный срок хранения (до 90 дней) всегда наступает раньше
    private const val NAME_EXPIRY_DAYS = 100L

    private val lock = Any()

    private class Entry(
        val id: String,
        val name: String,
        val original: String,
        var stored: String,
        val time: Long,
        var size: Long,
        val dir: Boolean,
    )

    // ------------------------------------------------------------------ индекс

    private fun indexFile(ctx: Context) = File(ctx.filesDir, INDEX)

    private fun load(ctx: Context): MutableList<Entry> {
        val f = indexFile(ctx)
        val out = ArrayList<Entry>()
        if (!f.isFile) return out
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Entry(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        original = o.getString("path"),
                        stored = o.getString("stored"),
                        time = o.optLong("time", 0L),
                        size = o.optLong("size", -1L),
                        dir = o.optBoolean("dir", false),
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("name", e.name)
            o.put("path", e.original)
            o.put("stored", e.stored)
            o.put("time", e.time)
            o.put("size", e.size)
            o.put("dir", e.dir)
            arr.put(o)
        }
        val f = indexFile(ctx)
        val tmp = File(f.parentFile, "$INDEX.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(arr.toString())
            tmp.delete()
        }
    }

    private fun exists(f: File): Boolean = try {
        Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)
    } catch (_: Exception) {
        f.exists()
    }

    /** После переноса папки её содержимое в индексе указывает на новое место. */
    private fun rebase(index: List<Entry>, oldPath: String, newPath: String) {
        val prefix = oldPath + File.separator
        for (e in index) {
            if (e.stored.startsWith(prefix)) e.stored = newPath + File.separator + e.stored.substring(prefix.length)
        }
    }

    private fun removeWithNested(index: MutableList<Entry>, stored: String) {
        val prefix = stored + File.separator
        index.removeAll { it.stored == stored || it.stored.startsWith(prefix) }
    }

    private fun hiddenTarget(f: File, alt: Boolean = false): File {
        val parent = f.parentFile ?: throw IOException("No parent folder: ${f.path}")
        val expiry = System.currentTimeMillis() / 1000 + NAME_EXPIRY_DAYS * 86_400L
        val prefix = if (alt) "$ALT_PREFIX${UUID.randomUUID().toString().take(6)}-" else "$PREFIX$expiry-"
        var name = prefix + f.name
        // у имени файла есть предел длины — тогда берём короткое, настоящее имя хранится в списке
        if (name.toByteArray(Charsets.UTF_8).size > 250) name = prefix + UUID.randomUUID().toString().take(8)
        return FileOps.uniqueTarget(parent, name)
    }

    // ------------------------------------------------------------------ корзина старой версии

    private fun legacyRoots(ctx: Context): List<File> {
        val list = ArrayList<File>()
        for (d in ContextCompat.getExternalFilesDirs(ctx, null)) {
            if (d != null) list.add(File(d, "trash"))
        }
        list.add(File(ctx.filesDir, "trash"))
        return list
    }

    private fun legacyList(ctx: Context): List<TrashItem> {
        val out = ArrayList<TrashItem>()
        for (root in legacyRoots(ctx)) {
            val dirs = root.listFiles() ?: continue
            for (d in dirs) {
                val meta = File(d, "meta.json")
                if (!d.isDirectory || !meta.isFile) continue
                try {
                    val j = JSONObject(meta.readText())
                    val payload = File(d, "data")
                    if (!payload.exists()) continue
                    out.add(
                        TrashItem(
                            id = d.name,
                            name = j.getString("name"),
                            originalPath = j.optString("path", ""),
                            deletedAt = j.optLong("time", d.lastModified()),
                            size = j.optLong("size", 0L),
                            isDirectory = j.optBoolean("dir", false),
                            payload = payload,
                            entryDir = d,
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    private fun purgeLegacy(ctx: Context, limit: Long, now: Long) {
        for (root in legacyRoots(ctx)) {
            val dirs = root.listFiles() ?: continue
            for (d in dirs) {
                if (!d.isDirectory) continue
                val meta = File(d, "meta.json")
                val time = try {
                    if (meta.isFile) JSONObject(meta.readText()).optLong("time", d.lastModified()) else d.lastModified()
                } catch (_: Exception) {
                    d.lastModified()
                }
                val orphan = !File(d, "data").exists() && now - d.lastModified() > DAY_MS
                if (time < limit || orphan) FileOps.deleteQuiet(d)
            }
        }
    }

    // ------------------------------------------------------------------ операции

    private fun inTrash(ctx: Context, index: List<Entry>, f: File): Boolean {
        val p = f.absolutePath
        if (index.any { p == it.stored || p.startsWith(it.stored + File.separator) }) return true
        return legacyRoots(ctx).any { p.startsWith(it.absolutePath + File.separator) }
    }

    fun list(ctx: Context): List<TrashItem> = synchronized(lock) {
        val index = load(ctx)
        var changed = false
        val out = ArrayList<TrashItem>()
        val iter = index.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            val stored = File(e.stored)
            if (!exists(stored)) {
                // файла нет, а папка на месте — его удалили вручную; если папки нет, возможно, вынута карта памяти
                if (stored.parentFile?.exists() == true) {
                    iter.remove()
                    changed = true
                }
                continue
            }
            if (e.size < 0) {
                e.size = FileOps.deepStats(listOf(stored)).bytes
                changed = true
            }
            out.add(TrashItem(e.id, e.name, e.original, e.time, e.size, e.dir, stored))
        }
        if (changed) save(ctx, index)
        out.addAll(legacyList(ctx))
        out.sortedByDescending { it.deletedAt }
    }

    /** Мгновенно: каждый элемент просто переименовывается рядом со своим местом. */
    fun moveToTrash(ctx: Context, files: List<File>, t: ProgressTracker) {
        synchronized(lock) {
            val index = load(ctx)
            try {
                t.begin(files.size.toLong(), files.size)
                for (f in files) {
                    t.check()
                    t.file(f.name)
                    if (inTrash(ctx, index, f)) {
                        // то, что уже лежит в корзине, удаляем окончательно
                        removeWithNested(index, f.absolutePath)
                        FileOps.deleteQuiet(f)
                    } else {
                        val isDir = f.isDirectory
                        var stored = hiddenTarget(f)
                        if (!f.renameTo(stored)) {
                            stored = hiddenTarget(f, alt = true)
                            if (!f.renameTo(stored)) throw IOException("Cannot move to trash: ${f.name}")
                        }
                        rebase(index, f.absolutePath, stored.absolutePath)
                        // размер папки посчитаем позже в фоне, чтобы удаление было мгновенным
                        index.add(
                            Entry(
                                id = UUID.randomUUID().toString(),
                                name = f.name,
                                original = f.absolutePath,
                                stored = stored.absolutePath,
                                time = System.currentTimeMillis(),
                                size = if (isDir) -1L else stored.length(),
                                dir = isDir,
                            )
                        )
                    }
                    t.add(1)
                    t.fileDone()
                }
            } finally {
                save(ctx, index)
            }
        }
    }

    fun restore(ctx: Context, items: List<TrashItem>, t: ProgressTracker) {
        synchronized(lock) {
            val index = load(ctx)
            try {
                t.begin(items.size.toLong(), items.size)
                for (item in items) {
                    t.check()
                    t.file(item.name)
                    val parent = File(item.originalPath).parentFile ?: throw IOException("Unknown location: ${item.name}")
                    if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create folder: ${parent.path}")
                    val target = FileOps.uniqueTarget(parent, item.name)
                    val src = item.payload
                    if (!src.renameTo(target)) {
                        FileOps.copyRec(src, target, t)
                        FileOps.deleteQuiet(src)
                    }
                    if (item.entryDir != null) {
                        FileOps.deleteQuiet(item.entryDir)
                    } else {
                        rebase(index, src.absolutePath, target.absolutePath)
                        index.removeAll { it.id == item.id }
                    }
                    t.add(1)
                    t.fileDone()
                }
            } finally {
                save(ctx, index)
            }
        }
    }

    fun deleteForever(ctx: Context, items: List<TrashItem>, t: ProgressTracker) {
        synchronized(lock) {
            val index = load(ctx)
            try {
                t.begin(items.size.toLong(), items.size)
                for (item in items) {
                    t.check()
                    t.file(item.name)
                    if (item.entryDir != null) {
                        FileOps.deleteQuiet(item.entryDir)
                    } else {
                        removeWithNested(index, item.payload.absolutePath)
                        FileOps.deleteQuiet(item.payload)
                    }
                    t.add(1)
                    t.fileDone()
                }
            } finally {
                save(ctx, index)
            }
        }
    }

    /** Окончательно удаляет всё, что пролежало дольше срока хранения. */
    fun purgeExpired(ctx: Context, retentionDays: Int, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val limit = now - retentionDays * DAY_MS
            val index = load(ctx)
            var changed = false
            for (e in index.toList()) {
                if (e.time < limit) {
                    FileOps.deleteQuiet(File(e.stored))
                    removeWithNested(index, e.stored)
                    changed = true
                }
            }
            if (changed) save(ctx, index)
            purgeLegacy(ctx, limit, now)
        }
    }
}
