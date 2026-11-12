package dev.ronova.gryphonis.data

import java.io.File

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, TEXT, ARCHIVE, PDF, APK, DOC, OTHER }

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv")
private val AUDIO_EXT = setOf("mp3", "wav", "flac", "ogg", "oga", "opus", "m4a", "aac", "amr", "wma", "mid", "midi")
private val TEXT_EXT = setOf(
    "txt", "md", "log", "json", "xml", "csv", "tsv", "ini", "conf", "cfg", "yml", "yaml", "toml",
    "properties", "kt", "kts", "java", "js", "ts", "py", "sh", "bat", "html", "htm", "css", "c", "h",
    "cpp", "hpp", "cs", "go", "rs", "php", "sql", "gradle", "srt", "vtt", "lrc", "gitignore", "env",
)
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
private val DOC_EXT = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub", "fb2")

fun kindOf(isDir: Boolean, ext: String): FileKind = when {
    isDir -> FileKind.FOLDER
    ext in IMAGE_EXT -> FileKind.IMAGE
    ext in VIDEO_EXT -> FileKind.VIDEO
    ext in AUDIO_EXT -> FileKind.AUDIO
    ext in TEXT_EXT -> FileKind.TEXT
    ext in ARCHIVE_EXT -> FileKind.ARCHIVE
    ext == "pdf" -> FileKind.PDF
    ext == "apk" -> FileKind.APK
    ext in DOC_EXT -> FileKind.DOC
    else -> FileKind.OTHER
}

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val extension: String,
    val kind: FileKind,
    val isHidden: Boolean,
) {
    val path: String get() = file.absolutePath
}

fun File.toFileItem(): FileItem {
    val dir = isDirectory
    val ext = if (dir) "" else extension.lowercase()
    return FileItem(
        file = this,
        name = name,
        isDirectory = dir,
        size = if (dir) 0L else length(),
        modified = lastModified(),
        extension = ext,
        kind = kindOf(dir, ext),
        isHidden = name.startsWith("."),
    )
}

enum class SortBy { NAME, DATE, TYPE, SIZE }

fun List<FileItem>.sortedFor(by: SortBy, ascending: Boolean, foldersFirst: Boolean): List<FileItem> {
    val ci = String.CASE_INSENSITIVE_ORDER
    val base: Comparator<FileItem> = when (by) {
        SortBy.NAME -> compareBy<FileItem, String>(ci) { it.name }
        SortBy.DATE -> compareBy<FileItem> { it.modified }.thenBy(ci) { it.name }
        SortBy.TYPE -> compareBy<FileItem, String>(ci) { it.extension }.thenBy(ci) { it.name }
        SortBy.SIZE -> compareBy<FileItem> { it.size }.thenBy(ci) { it.name }
    }
    val cmp = if (ascending) base else base.reversed()
    return sortedWith(Comparator { a, b ->
        if (foldersFirst && a.isDirectory != b.isDirectory) {
            if (a.isDirectory) -1 else 1
        } else {
            cmp.compare(a, b)
        }
    })
}
