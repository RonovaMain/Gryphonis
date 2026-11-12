package dev.ronova.gryphonis.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.ronova.gryphonis.R
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DateFormat
import java.util.Date

// ------------------------------------------------------------------ formatting

fun formatSize(context: Context, bytes: Long): String = Formatter.formatFileSize(context, bytes)

fun formatDate(millis: Long): String =
    if (millis <= 0L) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun formatClock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatEta(context: Context, seconds: Long): String = when {
    seconds < 0 -> "…"
    seconds < 60 -> context.getString(R.string.eta_s, seconds)
    seconds < 3600 -> context.getString(R.string.eta_m_s, seconds / 60, seconds % 60)
    else -> context.getString(R.string.eta_h_m, seconds / 3600, (seconds % 3600) / 60)
}

// ------------------------------------------------------------------ storage

data class StorageRoot(val name: String, val dir: File, val removable: Boolean, val total: Long, val free: Long)

object StorageUtil {

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val i = Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    fun internalRoot(): File = Environment.getExternalStorageDirectory()

    private fun space(dir: File): Pair<Long, Long> = try {
        val s = StatFs(dir.path)
        s.totalBytes to s.availableBytes
    } catch (e: Exception) {
        0L to 0L
    }

    fun roots(context: Context): List<StorageRoot> {
        val result = ArrayList<StorageRoot>()
        val internal = internalRoot()
        val (t, f) = space(internal)
        result.add(StorageRoot(context.getString(R.string.internal_storage), internal, false, t, f))
        val extra = ArrayList<File>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                for (v in sm.storageVolumes) {
                    val d = v.directory ?: continue
                    if (!v.isPrimary && v.state == Environment.MEDIA_MOUNTED) extra.add(d)
                }
            } else {
                for (d in ContextCompat.getExternalFilesDirs(context, null)) {
                    if (d == null) continue
                    val p = d.absolutePath
                    val idx = p.indexOf("/Android/data")
                    if (idx > 0) {
                        val root = File(p.substring(0, idx))
                        if (root != internal) extra.add(root)
                    }
                }
            }
        } catch (_: Exception) {
        }
        for (d in extra) {
            val (tt, ff) = space(d)
            result.add(StorageRoot(context.getString(R.string.sd_card), d, true, tt, ff))
        }
        return result
    }

    fun quickFolders(): List<File> {
        val root = internalRoot()
        return listOf("Download", "DCIM", "Pictures", "Music", "Movies", "Documents")
            .map { File(root, it) }
            .filter { it.isDirectory }
    }
}

// ------------------------------------------------------------------ network

object NetUtil {
    fun localIps(): List<String> = try {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        val addrs = ifaces
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
        addrs.sortedByDescending { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
    } catch (e: Exception) {
        emptyList()
    }
}

// ------------------------------------------------------------------ open / share

object OpenUtil {
    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

    /** Возвращает false, если подходящего приложения нет. */
    fun open(context: Context, file: File, mime: String? = null): Boolean {
        return try {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(context, file), mime ?: mimeOf(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(view, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun share(context: Context, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        return try {
            val uris = ArrayList<Uri>(files.map { uriFor(context, it) })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND)
                    .setType(mimeOf(files[0]))
                    .putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("*/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
