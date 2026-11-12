package dev.ronova.gryphonis.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.TransferManager
import dev.ronova.gryphonis.ui.components.GradientFolderIcon
import dev.ronova.gryphonis.ui.components.HashDialog
import dev.ronova.gryphonis.ui.components.PermissionDialog
import dev.ronova.gryphonis.ui.components.TransferDialog
import dev.ronova.gryphonis.ui.components.ZipChoiceDialog
import dev.ronova.gryphonis.ui.screens.AnalyzerScreen
import dev.ronova.gryphonis.ui.screens.AudioPlayerScreen
import dev.ronova.gryphonis.ui.screens.BrowserScreen
import dev.ronova.gryphonis.ui.screens.FtpServerScreen
import dev.ronova.gryphonis.ui.screens.ImageViewerScreen
import dev.ronova.gryphonis.ui.screens.RemoteScreen
import dev.ronova.gryphonis.ui.screens.SettingsScreen
import dev.ronova.gryphonis.ui.screens.TextViewerScreen
import dev.ronova.gryphonis.util.StorageUtil

/** Возвращает функцию, которая запрашивает доступ к файлам (экран настроек на Android 11+). */
@Composable
fun rememberStorageRequester(onResult: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return remember(ctx, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StorageUtil.openAllFilesSettings(ctx)
            } else {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    )
                )
            }
        }
    }
}

@Composable
fun GryphonisRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val transfer by TransferManager.state.collectAsStateWithLifecycle()
    val zipTarget by vm.zipTarget.collectAsStateWithLifecycle()
    val hash by vm.hashResult.collectAsStateWithLifecycle()
    val requestAccess = rememberStorageRequester { vm.refreshAccess() }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        if (!vm.hasAccess) {
            PermissionScreen(onGrant = requestAccess)
        } else {
            BrowserScreen(vm)
            if (vm.overlays.isNotEmpty()) {
                BackHandler { vm.pop() }
                Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                    when (val s = vm.overlays.last()) {
                        is Screen.Settings -> SettingsScreen(vm, onBack = { vm.pop() })
                        is Screen.FtpServer -> FtpServerScreen(vm, onBack = { vm.pop() })
                        is Screen.Remote -> RemoteScreen(vm, onBack = { vm.pop() })
                        is Screen.Analyzer -> AnalyzerScreen(
                            vm = vm,
                            path = s.path,
                            onBack = { vm.pop() },
                        )
                        is Screen.Text -> TextViewerScreen(s.path, onBack = { vm.pop() })
                        is Screen.Image -> ImageViewerScreen(s.path, onBack = { vm.pop() })
                        is Screen.Audio -> AudioPlayerScreen(s.path, onBack = { vm.pop() })
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp),
        )
    }

    transfer?.let { TransferDialog(it) }

    if (vm.showPermissionDialog) {
        PermissionDialog(onDismiss = { vm.showPermissionDialog = false }, onGrant = requestAccess)
    }

    zipTarget?.let { item ->
        ZipChoiceDialog(
            item = item,
            onDismiss = { vm.zipTarget.value = null },
            onExtractFolder = {
                vm.zipTarget.value = null
                vm.unzip(item, here = false)
            },
            onExtractHere = {
                vm.zipTarget.value = null
                vm.unzip(item, here = true)
            },
            onOpenWith = {
                vm.zipTarget.value = null
                vm.openExternal(item.file)
            },
        )
    }

    hash?.let { (name, value) ->
        HashDialog(name, value) { vm.hashResult.value = null }
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GradientFolderIcon(Modifier.size(112.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.perm_screen_message),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}
