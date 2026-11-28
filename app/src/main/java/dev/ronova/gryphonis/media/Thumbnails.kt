package dev.ronova.gryphonis.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.ronova.gryphonis.data.FileItem
import dev.ronova.gryphonis.data.PACKAGE_BUNDLE_EXT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Миниатюры для списков файлов: обложки музыки (из тегов, а если их нет — cover.jpg / folder.jpg рядом)
 * и иконки приложений из APK / APKS / XAPK / APKM.
 *
 * Загрузка идёт в фоне, не больше нескольких файлов одновременно; результаты (в том числе «обложки нет»)
 * держатся в небольшом кэше в памяти, поэтому прокрутка вверх-вниз не перечитывает файлы заново.
 * Иконки приложений дополнительно лежат крошечными PNG в кэше приложения (не больше нескольких МБ).
 */
object Thumbnails {

    private const val COVER_PX = 160
    private const val ICON_PX = 128
    private const val MAX_BUNDLE_APK_BYTES = 120L * 1024 * 1024
    private const val DISK_LIMIT = 8L * 1024 * 1024
    private const val DISK_TRIM_TO = 5L * 1024 * 1024

    private class Entry(val bitmap: ImageBitmap?)

    private val memory = object : LruCache<String, Entry>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Entry): Int =
            (value.bitmap?.let { it.width * it.height * 4 } ?: 0) + 64
    }

    private val gate = Semaphore(3)

    private fun audioKey(item: FileItem) = "a:${item.path}:${item.modified}:${item.size}"
    private fun apkKey(item: FileItem) = "p:${item.path}:${item.modified}:${item.size}"

    /** То, что уже есть в памяти (без чтения диска) — чтобы при прокрутке не мигали заглушки. */
    fun peekAudio(item: FileItem): ImageBitmap? = memory.get(audioKey(item))?.bitmap
    fun peekApk(item: FileItem): ImageBitmap? = memory.get(apkKey(item))?.bitmap

    suspend fun audio(item: FileItem): ImageBitmap? =
        load(audioKey(item)) { loadAudioCover(item.file)?.asImageBitmap() }

    suspend fun apk(context: Context, item: FileItem): ImageBitmap? {
        val app = context.applicationContext
        return load(apkKey(item)) { loadApkIcon(app, item.file)?.asImageBitmap() }
    }

    private suspend fun load(key: String, block: () -> ImageBitmap?): ImageBitmap? {
        memory.get(key)?.let { return it.bitmap }
        // место в очереди ждём «отменяемо»: если строка уже прокручена, она просто не дойдёт до чтения
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                memory.get(key)?.let { return@withContext it.bitmap }
                val bmp = try {
                    block()
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
                // кладём в кэш внутри этого блока: если строку успели убрать с экрана, результат не пропадёт
                memory.put(key, Entry(bmp))
                bmp
            }
        }
    }

    // ------------------------------------------------------------------ обложки музыки

    private fun loadAudioCover(file: File): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val pic = r.embeddedPicture
            if (pic != null) decodeBytes(pic, COVER_PX)?.let { return it }
        } catch (_: Exception) {
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
        val folder = file.parentFile?.let { folderCover(it) } ?: return null
        return decodeFile(folder, COVER_PX)
    }

    private val COVER_NAMES = listOf("cover", "folder", "album", "front", "albumart", "albumartsmall", "albumartlarge")
    private val COVER_EXT = listOf("jpg", "jpeg", "png", "webp")
    private val folderCovers = object : LinkedHashMap<String, File?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, File?>?): Boolean = size > 64
    }

    /** Картинка-обложка рядом с треками (cover.jpg, folder.png ...). Результат для папки запоминается. */
    private fun folderCover(dir: File): File? {
        val key = dir.absolutePath
        synchronized(folderCovers) {
            if (folderCovers.containsKey(key)) return folderCovers[key]
        }
        val names = dir.list() ?: emptyArray()
        val lower = names.associateBy { it.lowercase() }
        var found: File? = null
        outer@ for (base in COVER_NAMES) {
            for (ext in COVER_EXT) {
                val real = lower["$base.$ext"] ?: continue
                val f = File(dir, real)
                if (f.isFile && f.length() in 1..(12L * 1024 * 1024)) {
                    found = f
                    break@outer
                }
            }
        }
        synchronized(folderCovers) { folderCovers[key] = found }
        return found
    }

    private fun decodeBytes(bytes: ByteArray, target: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, target) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (e: Exception) {
        null
    }

    private fun decodeFile(file: File, target: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, target) }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (e: Exception) {
        null
    }

    private fun sampleFor(w: Int, h: Int, target: Int): Int {
        var sample = 1
        while (w / (sample * 2) >= target && h / (sample * 2) >= target) sample *= 2
        return sample
    }

    // ------------------------------------------------------------------ иконки приложений

    private fun loadApkIcon(context: Context, file: File): Bitmap? {
        val dir = File(context.cacheDir, "apk_icons").apply { mkdirs() }
        val cached = File(dir, sha1("${file.absolutePath}|${file.lastModified()}|${file.length()}") + ".png")
        if (cached.isFile) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { return it }
        }
        val bmp = if (file.extension.lowercase() in PACKAGE_BUNDLE_EXT) bundleIcon(context, file) else archiveIcon(context, file)
        if (bmp != null) {
            try {
                FileOutputStream(cached).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                trimDiskCache(dir)
            } catch (_: Exception) {
            }
        }
        return bmp
    }

    /** Иконка из обычного .apk: системный разбор пакета без установки. */
    private fun archiveIcon(context: Context, apk: File): Bitmap? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apk.absolutePath, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = apk.absolutePath
        app.publicSourceDir = apk.absolutePath
        val drawable = app.loadIcon(pm) ?: return null
        return drawable.toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    }

    /**
     * .xapk / .apkm обычно содержат готовый icon.png. Если его нет (.apks), берём основной APK внутри
     * архива: он на минуту копируется в кэш (не больше [MAX_BUNDLE_APK_BYTES]), из него читается иконка, копия удаляется.
     */
    private fun bundleIcon(context: Context, bundle: File): Bitmap? {
        return ZipFile(bundle).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory }
            val iconEntry = entries.firstOrNull { it.name.equals("icon.png", ignoreCase = true) }
            val ready = iconEntry?.let { e -> zip.getInputStream(e).use { BitmapFactory.decodeStream(it) } }
            if (ready != null) {
                Bitmap.createScaledBitmap(ready, ICON_PX, ICON_PX, true)
            } else {
                val apks = entries.filter { it.name.endsWith(".apk", ignoreCase = true) }
                val base = apks.sortedWith(compareBy({ rankOfApk(it.name) }, { -it.size })).firstOrNull()
                if (base == null || base.size <= 0 || base.size > MAX_BUNDLE_APK_BYTES) {
                    null
                } else {
                    val tmpDir = File(context.cacheDir, "apk_tmp").apply { mkdirs() }
                    val tmp = File(tmpDir, UUID.randomUUID().toString() + ".apk")
                    try {
                        zip.getInputStream(base).use { input -> FileOutputStream(tmp).use { input.copyTo(it) } }
                        archiveIcon(context, tmp)
                    } finally {
                        tmp.delete()
                    }
                }
            }
        }
    }

    /** Какой APK внутри пакета основной: 0 — base, 1 — прочие, 2 — разделённые конфигурации (без иконки). */
    private fun rankOfApk(name: String): Int {
        val n = name.substringAfterLast('/').lowercase()
        return when {
            n == "base.apk" || n == "base-master.apk" || n == "master.apk" -> 0
            n.startsWith("config.") || n.startsWith("split_") || n.contains("config") || n.startsWith("base-") -> 2
            else -> 1
        }
    }

    private fun trimDiskCache(dir: File) {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_LIMIT) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_TRIM_TO) break
            total -= f.length()
            f.delete()
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
