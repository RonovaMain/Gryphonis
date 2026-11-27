package dev.ronova.gryphonis.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.ProgressTracker
import dev.ronova.gryphonis.stream.StreamSource
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.abs

/**
 * Установка составных пакетов: .apks (bundletool / SAI), .xapk, .apkm (APKMirror).
 * Все они — обычный ZIP с несколькими APK (основной + «сплиты» под ABI, плотность экрана и язык).
 * Из пакета берутся только подходящие этому устройству части, которые одной сессией отдаются
 * системному установщику. Данные игр (OBB) из XAPK копируются в Android/obb.
 */
object PackageInstall {

    // ------------------------------------------------------------------ разрешение

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Открывает экран «Установка неизвестных приложений» для Gryphonis. */
    fun openInstallPermissionSettings(context: Context) {
        try {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ установка

    /**
     * Ставит пакет из файла. Блокирующая: вызывать из [dev.ronova.gryphonis.data.TransferManager].
     * Когда всё передано системе, Android сам покажет окно подтверждения (см. [InstallResultReceiver]).
     */
    fun installBundle(context: Context, file: File, t: ProgressTracker) {
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw IOException(context.getString(R.string.install_bad_package))
        }
        zip.use { z ->
            val plan = buildPlan(z, DeviceEnv.of(context))
            if (plan.apks.isEmpty()) throw IOException(context.getString(R.string.install_no_apk))

            val total = plan.apks.sumOf { e -> maxOf(e.size, 0L) } + plan.obbs.sumOf { e -> maxOf(e.size, 0L) }
            t.restart(total, plan.apks.size + plan.obbs.size)

            // 1) данные игры (OBB): путь внутри пакета уже содержит Android/obb/<пакет>/...
            var obbFailed = 0
            for (e in plan.obbs) {
                t.file(e.name.substringAfterLast('/'))
                if (!copyObb(z, e, t)) obbFailed++
                t.fileDone()
            }

            // 2) сами APK одной сессией установки
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (total > 0) params.setSize(plan.apks.sumOf { e -> maxOf(e.size, 0L) })
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                plan.apks.forEachIndexed { i, e ->
                    t.file(e.name.substringAfterLast('/'))
                    z.getInputStream(e).use { input ->
                        session.openWrite("part_$i.apk", 0, if (e.size >= 0) e.size else -1).use { out ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                t.add(n.toLong())
                            }
                            session.fsync(out)
                        }
                    }
                    t.fileDone()
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION)
                    .putExtra(InstallResultReceiver.EXTRA_OBB_FAILED, obbFailed)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            } catch (e: Throwable) {
                try {
                    session.abandon()
                } catch (_: Exception) {
                }
                throw e
            } finally {
                session.close()
            }
        }
    }

    /**
     * Пакет лежит внутри архива или на FTP: единственный раз копируем его во временный файл (кэш приложения),
     * ставим и сразу удаляем копию. Постоянно место на диске не занимается.
     */
    fun installBundleStream(context: Context, src: StreamSource, t: ProgressTracker) {
        val dir = File(context.cacheDir, "install").apply { mkdirs() }
        val tmp = File(dir, "pkg_${System.nanoTime()}.${src.extension.ifEmpty { "apks" }}")
        try {
            val size = src.size
            t.begin(size.coerceAtLeast(0L), 1) // этап 1: копия во временный файл
            t.file(src.name)
            src.open(0).use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        t.add(n.toLong())
                    }
                }
            }
            installBundle(context, tmp, t)
        } finally {
            tmp.delete()
        }
    }

    /** Чистит временные копии, если приложение когда-то закрылось посреди установки. */
    fun cleanCache(context: Context) {
        try {
            File(context.cacheDir, "install").deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun copyObb(zip: ZipFile, e: ZipEntry, t: ProgressTracker): Boolean {
        val rel = e.name.replace('\\', '/').trimStart('/')
        if (rel.split('/').any { it == ".." }) return false
        return try {
            val dest = File(Environment.getExternalStorageDirectory(), rel)
            dest.parentFile?.mkdirs()
            zip.getInputStream(e).use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        t.add(n.toLong())
                    }
                }
            }
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ выбор частей пакета

    /** Что нужно знать об устройстве, чтобы выбрать из пакета подходящие части. */
    class DeviceEnv(val abis: List<String>, val dpi: Int, val languages: Set<String>) {
        companion object {
            fun of(context: Context): DeviceEnv {
                val abis = Build.SUPPORTED_ABIS.map { it.lowercase().replace('-', '_') }
                val dpi = context.resources.displayMetrics.densityDpi
                val locales = context.resources.configuration.locales
                val langs = (0 until locales.size()).map { locales.get(it).language.lowercase() }.toMutableSet()
                if ("iw" in langs) langs += "he"
                if ("in" in langs) langs += "id"
                return DeviceEnv(abis, dpi, langs)
            }
        }
    }

    class Plan(val apks: List<ZipEntry>, val obbs: List<ZipEntry>)

    private val ABI_KEYS = setOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "mips64", "mips", "riscv64")
    private val DENSITY = mapOf(
        "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
        "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
    )
    private val LANG_RE = Regex("^[a-z]{2,3}(_[a-z0-9]{2,8})?$")

    private fun norm(e: ZipEntry): String = e.name.replace('\\', '/').trimStart('/')

    fun buildPlan(zip: ZipFile, dev: DeviceEnv): Plan {
        val all = zip.entries().toList().filter { !it.isDirectory }
        val apks = all.filter { norm(it).lowercase().endsWith(".apk") }
        val obbs = all.filter { norm(it).startsWith("Android/obb/") }
        if (apks.isEmpty()) return Plan(emptyList(), obbs)

        fun path(e: ZipEntry) = norm(e).lowercase()
        val inSplits = apks.filter { path(it).startsWith("splits/") || "/splits/" in path(it) }
        val standalones = apks.filter { path(it).startsWith("standalones/") || "/standalones/" in path(it) }

        val chosen: List<ZipEntry> = when {
            // bundletool: base-master.apk + base-<abi/плотность/язык>.apk + функции и asset-pack'и
            inSplits.isNotEmpty() -> inSplits + apks.filter { "asset-slices/" in path(it) }
            standalones.isNotEmpty() && apks.size == standalones.size -> listOf(bestStandalone(standalones, dev))
            else -> {
                // XAPK/APKM/SAI: все APK в корне; если есть manifest.json — берём то, что в нём перечислено
                val listed = manifestFiles(zip)
                val base = if (listed != null) {
                    apks.filter { path(it).substringAfterLast('/') in listed }.ifEmpty { apks }
                } else apks
                base.filter { e ->
                    val p = path(e)
                    !(p.startsWith("instant/") || p.startsWith("system/") || p.startsWith("archive/") || p.startsWith("standalones/"))
                }.ifEmpty { base }
            }
        }
        return Plan(pickForDevice(chosen, dev), obbs)
    }

    private fun manifestFiles(zip: ZipFile): Set<String>? {
        val e = zip.getEntry("manifest.json") ?: return null
        return try {
            val text = zip.getInputStream(e).bufferedReader().use { it.readText() }
            val arr = JSONObject(text).optJSONArray("split_apks") ?: return null
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it)?.optString("file")?.takeIf { f -> f.isNotBlank() } }
                .map { it.lowercase().replace('\\', '/').substringAfterLast('/') }
                .toSet()
        } catch (_: Exception) {
            null
        }
    }

    private enum class TagKind { NONE, MASTER, ABI, DENSITY, LANG, OTHER }

    /** Конфигурационный «хвост» имени: base-xxhdpi -> xxhdpi, split_config.arm64_v8a -> arm64_v8a. */
    private fun tagOf(entryPath: String): String? {
        val file = entryPath.substringAfterLast('/')
        val base = file.removeSuffix(".apk").removeSuffix(".APK").lowercase()
        val p = entryPath.lowercase()
        val inBundleDir = p.startsWith("splits/") || "/splits/" in p || p.startsWith("asset-slices/") || "/asset-slices/" in p
        return when {
            base.startsWith("split_config.") -> base.removePrefix("split_config.")
            base.startsWith("config.") -> base.removePrefix("config.")
            inBundleDir && base.contains('-') -> base.substringAfterLast('-')
            else -> null
        }?.replace('-', '_')
    }

    private fun kindOfTag(tag: String?): TagKind = when {
        tag == null -> TagKind.NONE
        tag.startsWith("master") -> TagKind.MASTER
        tag in ABI_KEYS -> TagKind.ABI
        tag in DENSITY -> TagKind.DENSITY
        tag.startsWith("tcf") || tag == "nodpi" || tag == "watch" -> TagKind.OTHER
        LANG_RE.matches(tag) -> TagKind.LANG
        else -> TagKind.OTHER
    }

    private fun pickForDevice(cands: List<ZipEntry>, dev: DeviceEnv): List<ZipEntry> {
        val tagged = cands.map { it to tagOf(norm(it)) }
        val abiTags = tagged.mapNotNull { (_, t) -> t?.takeIf { kindOfTag(it) == TagKind.ABI } }.toSet()
        val bestAbi = dev.abis.firstOrNull { it in abiTags } // нет подходящего — оставим все, пусть решает система
        val densTags = tagged.mapNotNull { (_, t) -> t?.takeIf { kindOfTag(it) == TagKind.DENSITY } }.toSet()
        val bestDens = densTags.minWithOrNull(
            compareBy<String> { abs(DENSITY.getValue(it) - dev.dpi) }.thenByDescending { DENSITY.getValue(it) }
        )

        val result = tagged.filter { (_, t) ->
            when (kindOfTag(t)) {
                TagKind.NONE, TagKind.MASTER, TagKind.OTHER -> true
                TagKind.ABI -> bestAbi == null || t == bestAbi
                TagKind.DENSITY -> t == bestDens
                TagKind.LANG -> t!!.substringBefore('_') in dev.languages
            }
        }.map { it.first }
        return result.ifEmpty { cands }
    }

    private fun tokenRe(key: String) = Regex("(?<![a-z0-9])" + Regex.escape(key) + "(?![a-z0-9])")

    private fun bestStandalone(list: List<ZipEntry>, dev: DeviceEnv): ZipEntry {
        fun score(e: ZipEntry): Long {
            val name = norm(e).substringAfterLast('/').lowercase()
            val abi = ABI_KEYS.filter { tokenRe(it).containsMatchIn(name) }.maxByOrNull { it.length }
            val abiRank = if (abi == null) 50 else dev.abis.indexOf(abi).let { if (it < 0) 90 else it }
            val dens = DENSITY.entries.firstOrNull { tokenRe(it.key).containsMatchIn(name) }?.value
            val densDiff = if (dens == null) 5000 else abs(dens - dev.dpi)
            return abiRank * 100_000L + densDiff
        }
        return list.minByOrNull { score(it) } ?: list.first()
    }
}
