package dev.ronova.gryphonis.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import androidx.core.content.IntentCompat
import dev.ronova.gryphonis.R
import kotlinx.coroutines.flow.MutableSharedFlow

/** Сообщения об установке для экрана (если он открыт) — иначе показываем обычный Toast. */
object InstallBus {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun post(context: Context, text: String) {
        if (events.subscriptionCount.value > 0) events.tryEmit(text)
        else Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
    }
}

/** Принимает от PackageInstaller итог установки и показывает окно подтверждения, когда система его просит. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        InstallBus.post(context, context.getString(R.string.install_failed, e.message ?: ""))
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                InstallBus.post(context, context.getString(R.string.install_success))
                val obb = intent.getIntExtra(EXTRA_OBB_FAILED, 0)
                if (obb > 0) InstallBus.post(context, context.getString(R.string.install_obb_failed, obb))
            }
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                InstallBus.post(context, context.getString(R.string.install_cancelled))
            else -> {
                val why = when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> context.getString(R.string.install_err_conflict)
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> context.getString(R.string.install_err_incompatible)
                    PackageInstaller.STATUS_FAILURE_STORAGE -> context.getString(R.string.install_err_storage)
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> context.getString(R.string.install_err_blocked)
                    PackageInstaller.STATUS_FAILURE_INVALID -> context.getString(R.string.install_err_invalid)
                    else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                }
                InstallBus.post(context, context.getString(R.string.install_failed, why))
            }
        }
    }

    companion object {
        const val ACTION = "dev.ronova.gryphonis.INSTALL_RESULT"
        const val EXTRA_OBB_FAILED = "obb_failed"
    }
}
