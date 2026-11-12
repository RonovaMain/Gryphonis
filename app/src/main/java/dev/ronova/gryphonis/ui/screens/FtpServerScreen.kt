package dev.ronova.gryphonis.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.Keys
import dev.ronova.gryphonis.ftp.FtpServerManager
import dev.ronova.gryphonis.ftp.FtpServerService
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.util.formatSize
import java.security.SecureRandom

private const val PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

private fun randomPassword(): String {
    val r = SecureRandom()
    return (1..10).map { PASSWORD_CHARS[r.nextInt(PASSWORD_CHARS.length)] }.joinToString("")
}

@Composable
fun FtpServerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by FtpServerManager.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var portText by remember { mutableStateOf(settings.ftpPort.toString()) }
    var user by remember { mutableStateOf(settings.ftpUser) }
    var pass by remember { mutableStateOf(settings.ftpPassword) }
    var showPass by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val errPort = stringResource(R.string.ftp_err_port)
    val errUser = stringResource(R.string.ftp_err_user)
    val errPass = stringResource(R.string.ftp_err_pass)

    val startNow = {
        val port = portText.toIntOrNull() ?: 2121
        vm.setPref(Keys.FTP_PORT, port)
        vm.setPref(Keys.FTP_USER, user.trim())
        vm.setPref(Keys.FTP_PASS, pass)
        FtpServerManager.clearError()
        FtpServerService.start(ctx, port, user.trim(), pass, settings.ftpReadOnly)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startNow() }

    fun onStartClicked() {
        if (!vm.ensureAccess()) return
        val port = portText.toIntOrNull()
        when {
            port == null || port < 1024 || port > 65535 -> localError = errPort
            user.isBlank() -> localError = errUser
            pass.length < 4 -> localError = errPass
            else -> {
                localError = null
                val needNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needNotif) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else startNow()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_ftp_server)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- статус
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.running) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (state.running) R.string.ftp_running else R.string.ftp_stopped),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.running) {
                        if (state.addresses.isEmpty()) {
                            Text(stringResource(R.string.ftp_no_network), color = MaterialTheme.colorScheme.error)
                        }
                        for (a in state.addresses) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                IconButton(onClick = { clipboard.setText(AnnotatedString(a)) }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                }
                            }
                        }
                        Text(
                            stringResource(
                                R.string.ftp_stats,
                                state.clients,
                                formatSize(ctx, state.sent),
                                formatSize(ctx, state.received),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val err = state.error ?: localError
                    if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
                    if (state.running) {
                        OutlinedButton(onClick = { FtpServerService.stop(ctx) }) { Text(stringResource(R.string.stop)) }
                    } else {
                        Button(onClick = { onStartClicked() }) { Text(stringResource(R.string.start)) }
                    }
                }
            }

            // ---- настройки (меняются только при остановленном сервере)
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.ftp_port)) },
                supportingText = { Text(stringResource(R.string.ftp_port_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(stringResource(R.string.ftp_user)) },
                singleLine = true,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(stringResource(R.string.ftp_password)) },
                singleLine = true,
                enabled = !state.running,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { pass = randomPassword() }, enabled = !state.running) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                        }
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ftp_readonly), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.ftp_readonly_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.ftpReadOnly,
                    onCheckedChange = { vm.setPref(Keys.FTP_RO, it) },
                    enabled = !state.running,
                )
            }

            Text(
                stringResource(R.string.ftp_how_to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- лог
            if (state.log.isNotEmpty()) {
                Text(stringResource(R.string.ftp_log), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        for (line in state.log.takeLast(30)) {
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.width(0.dp))
        }
    }
}
