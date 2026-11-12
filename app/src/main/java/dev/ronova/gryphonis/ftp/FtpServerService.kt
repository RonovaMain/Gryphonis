package dev.ronova.gryphonis.ftp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.ronova.gryphonis.MainActivity
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.util.NetUtil
import dev.ronova.gryphonis.util.StorageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FtpUiState(
    val running: Boolean = false,
    val port: Int = 0,
    val addresses: List<String> = emptyList(),
    val clients: Int = 0,
    val sent: Long = 0,
    val received: Long = 0,
    val log: List<String> = emptyList(),
    val error: String? = null,
)

object FtpServerManager {
    private val _state = MutableStateFlow(FtpUiState())
    val state: StateFlow<FtpUiState> = _state.asStateFlow()

    fun update(block: (FtpUiState) -> FtpUiState) {
        _state.update(block)
    }

    fun appendLog(line: String) {
        _state.update { it.copy(log = (it.log + line).takeLast(80)) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

class FtpServerService : Service() {

    companion object {
        const val ACTION_START = "dev.ronova.gryphonis.FTP_START"
        const val ACTION_STOP = "dev.ronova.gryphonis.FTP_STOP"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_USER = "user"
        private const val EXTRA_PASS = "pass"
        private const val EXTRA_RO = "ro"
        private const val CHANNEL_ID = "ftp_server"
        private const val NOTIF_ID = 2121

        fun start(context: Context, port: Int, user: String, pass: String, readOnly: Boolean) {
            val i = Intent(context, FtpServerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PORT, port)
                .putExtra(EXTRA_USER, user)
                .putExtra(EXTRA_PASS, pass)
                .putExtra(EXTRA_RO, readOnly)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FtpServerService::class.java))
        }
    }

    private var server: FtpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var statsJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val port = intent?.getIntExtra(EXTRA_PORT, 2121) ?: 2121
        val user = intent?.getStringExtra(EXTRA_USER).orEmpty()
        val pass = intent?.getStringExtra(EXTRA_PASS).orEmpty()
        val readOnly = intent?.getBooleanExtra(EXTRA_RO, false) ?: false

        val ips = NetUtil.localIps()
        ensureChannel()
        val notification = buildNotification(port, ips)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        startServer(port, user, pass, readOnly, ips)
        return START_NOT_STICKY
    }

    private fun startServer(port: Int, user: String, pass: String, readOnly: Boolean, ips: List<String>) {
        server?.stop()
        server = null
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)
        val srv = FtpServer(
            root = StorageUtil.internalRoot(),
            port = port,
            username = user,
            password = pass,
            readOnly = readOnly,
            log = { FtpServerManager.appendLog(stamp.format(Date()) + "  " + it) },
            onClients = { n -> FtpServerManager.update { s -> s.copy(clients = n) } },
        )
        try {
            srv.start()
            server = srv
            acquireLocks()
            FtpServerManager.update { s ->
                FtpUiState(
                    running = true,
                    port = port,
                    addresses = ips.map { "ftp://$it:$port" },
                    log = s.log,
                )
            }
            statsJob?.cancel()
            statsJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    FtpServerManager.update { s -> s.copy(sent = srv.bytesSent.get(), received = srv.bytesReceived.get()) }
                }
            }
        } catch (e: Exception) {
            FtpServerManager.update { s -> s.copy(running = false, error = e.message ?: e.javaClass.simpleName) }
            stopEverything()
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gryphonis:ftp").apply {
                setReferenceCounted(false)
                acquire()
            }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Gryphonis:ftp").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
        wifiLock = null
    }

    private fun stopEverything() {
        statsJob?.cancel()
        server?.stop()
        server = null
        releaseLocks()
        FtpServerManager.update { s -> s.copy(running = false, clients = 0, addresses = emptyList()) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        statsJob?.cancel()
        server?.stop()
        server = null
        releaseLocks()
        FtpServerManager.update { s -> s.copy(running = false, clients = 0, addresses = emptyList()) }
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.ftp_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(port: Int, ips: List<String>): Notification {
        val address = ips.firstOrNull()?.let { "ftp://$it:$port" } ?: ":$port"
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, FtpServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.ftp_notif_title))
            .setContentText(address)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_notification, getString(R.string.stop), stopIntent)
            .build()
    }
}
