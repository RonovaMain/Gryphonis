package dev.ronova.gryphonis.data

import java.io.File
import java.util.PriorityQueue

data class KindStat(val count: Int, val bytes: Long)

data class AnalysisResult(
    val totalBytes: Long,
    val totalFiles: Int,
    val byKind: Map<FileKind, KindStat>,
    val largest: List<FileItem>,
    val folders: List<Pair<File, Long>>,
)

object Analyzer {

    fun analyze(root: File, shouldContinue: () -> Boolean, onProgress: (Int) -> Unit): AnalysisResult {
        val counts = HashMap<FileKind, Int>()
        val bytes = HashMap<FileKind, Long>()
        val heap = PriorityQueue<FileItem>(64, compareBy<FileItem> { it.size })
        var totalBytes = 0L
        var totalFiles = 0

        fun scan(f: File): Long {
            if (!shouldContinue()) return 0L
            if (FileOps.isSymlink(f)) return 0L
            if (f.isDirectory) {
                var sum = 0L
                f.listFiles()?.forEach { sum += scan(it) }
                return sum
            }
            val item = f.toFileItem()
            totalFiles++
            totalBytes += item.size
            counts[item.kind] = (counts[item.kind] ?: 0) + 1
            bytes[item.kind] = (bytes[item.kind] ?: 0L) + item.size
            heap.add(item)
            if (heap.size > 50) heap.poll()
            if (totalFiles % 200 == 0) onProgress(totalFiles)
            return item.size
        }

        val folders = ArrayList<Pair<File, Long>>()
        root.listFiles()?.forEach { child ->
            val s = scan(child)
            if (child.isDirectory) folders.add(child to s)
        }
        onProgress(totalFiles)

        val byKind = HashMap<FileKind, KindStat>()
        for (k in counts.keys) byKind[k] = KindStat(counts[k] ?: 0, bytes[k] ?: 0L)

        return AnalysisResult(
            totalBytes = totalBytes,
            totalFiles = totalFiles,
            byKind = byKind,
            largest = heap.sortedByDescending { it.size },
            folders = folders.sortedByDescending { it.second }.take(12),
        )
    }
}
