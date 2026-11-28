package dev.ronova.gryphonis.data

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.PriorityQueue

/** Недавний файл. [appeared] — когда он появился на устройстве (см. [RecentScanner]). */
data class RecentFile(val item: FileItem, val appeared: Long)

/**
 * Ищет файлы, которые появились на накопителе недавно: скачанные, снятые, скопированные, распакованные.
 *
 * Время «появления» — не дата изменения, а большее из mtime и ctime. Браузеры и мессенджеры часто ставят
 * скачанному файлу старую дату (с сервера или из исходника), и по mtime он ушёл бы в самый конец списка, а ctime
 * системой выставляется в момент, когда файл реально оказался на диске (создан, скопирован, перемещён).
 *
 * Обход идёт одним проходом по всем накопителям с одним системным вызовом lstat на запись; корзина,
 * символьные ссылки, а также Android/data и Android/obb (туда всё равно нет доступа) пропускаются.
 * Скрытые файлы и папки учитываются только если включён показ скрытых.
 */
object RecentScanner {

    const val MAX_RESULTS = 3000

    fun scan(
        roots: List<File>,
        sinceMs: Long,
        showHidden: Boolean,
        shouldContinue: () -> Boolean,
    ): List<RecentFile> {
        // «кучей» держим только самые новые MAX_RESULTS файлов: память не растёт, даже если файлов очень много
        val heap = PriorityQueue<RecentFile>(256, compareBy<RecentFile> { it.appeared })

        fun walk(dir: File) {
            if (!shouldContinue()) return
            val names = dir.list() ?: return
            for (name in names) {
                if (!shouldContinue()) return
                if (name.startsWith(".") && (!showHidden || Trash.isTrashName(name))) continue
                val f = File(dir, name)
                val st = try {
                    Os.lstat(f.path)
                } catch (e: Exception) {
                    continue
                }
                val mode = st.st_mode
                when {
                    OsConstants.S_ISLNK(mode) -> {}
                    OsConstants.S_ISDIR(mode) -> if (!isUnreachableAndroidDir(dir, name)) walk(f)
                    OsConstants.S_ISREG(mode) -> {
                        val mtime = st.st_mtime * 1000L
                        val appeared = maxOf(mtime, st.st_ctime * 1000L)
                        if (appeared >= sinceMs) {
                            if (heap.size >= MAX_RESULTS && appeared <= heap.peek()!!.appeared) continue
                            val ext = f.extension.lowercase()
                            heap.add(
                                RecentFile(
                                    FileItem(
                                        file = f,
                                        name = name,
                                        isDirectory = false,
                                        size = st.st_size,
                                        modified = mtime,
                                        extension = ext,
                                        kind = kindOf(false, ext),
                                        isHidden = name.startsWith("."),
                                    ),
                                    appeared,
                                )
                            )
                            if (heap.size > MAX_RESULTS) heap.poll()
                        }
                    }
                }
            }
        }

        for (root in roots) walk(root)
        return heap.sortedByDescending { it.appeared }
    }

    private fun isUnreachableAndroidDir(parent: File, name: String): Boolean =
        parent.name == "Android" && (name == "data" || name == "obb")
}
