package dev.ronova.gryphonis.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.TransferManager
import dev.ronova.gryphonis.media.MusicPlayer
import dev.ronova.gryphonis.ui.components.FolderIcon
import dev.ronova.gryphonis.ui.components.HashDialog
import dev.ronova.gryphonis.ui.components.MiniPlayer
import dev.ronova.gryphonis.ui.components.PermissionDialog
import dev.ronova.gryphonis.ui.components.TransferDialog
import dev.ronova.gryphonis.ui.components.ZipChoiceDialog
import dev.ronova.gryphonis.ui.screens.AnalyzerScreen
import dev.ronova.gryphonis.ui.screens.ArchiveScreen
import dev.ronova.gryphonis.ui.screens.AudioPlayerScreen
import dev.ronova.gryphonis.ui.screens.BrowserScreen
import dev.ronova.gryphonis.ui.screens.FtpServerScreen
import dev.ronova.gryphonis.ui.screens.ImageViewerScreen
import dev.ronova.gryphonis.ui.screens.RemoteScreen
import dev.ronova.gryphonis.ui.screens.SettingsScreen
import dev.ronova.gryphonis.ui.screens.TextViewerScreen
import dev.ronova.gryphonis.ui.screens.TrashScreen
import dev.ronova.gryphonis.ui.screens.VideoPlayerScreen
import dev.ronova.gryphonis.util.StorageUtil

private data class OverlayState(val screen: Screen?, val depth: Int)

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

    // Чтобы уведомление-плеер показывалось на Android 13+, один раз просим разрешение на уведомления.
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(MusicPlayer.hasMedia) {
        if (MusicPlayer.hasMedia && !askedNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedNotifications = true
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val top = vm.overlays.lastOrNull()
    val showMini = vm.hasAccess && MusicPlayer.hasMedia && top !is Screen.Audio && top !is Screen.Video

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (showMini) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
        ) {
            if (!vm.hasAccess) {
                PermissionScreen(onGrant = requestAccess)
            } else {
                BrowserScreen(vm)
                BackHandler(enabled = vm.overlays.isNotEmpty()) { vm.pop() }
                // Экраны поверх проводника выезжают справа и уходят обратно плавно
                AnimatedContent(
                    targetState = OverlayState(vm.overlays.lastOrNull(), vm.overlays.size),
                    transitionSpec = {
                        if (targetState.depth >= initialState.depth) {
                            ContentTransform(
                                targetContentEnter = slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 5 } +
                                    fadeIn(tween(240)),
                                initialContentExit = ExitTransition.None,
                                targetContentZIndex = 1f,
                                sizeTransform = null,
                            )
                        } else {
                            ContentTransform(
                                targetContentEnter = EnterTransition.None,
                                initialContentExit = slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 5 } +
                                    fadeOut(tween(220)),
                                targetContentZIndex = -1f,
                                sizeTransform = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "overlay",
                ) { state ->
                    val screen = state.screen
                    if (screen != null) Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                        when (screen) {
                            is Screen.Settings -> SettingsScreen(vm, onBack = { vm.pop() })
                            is Screen.FtpServer -> FtpServerScreen(vm, onBack = { vm.pop() })
                            is Screen.Remote -> RemoteScreen(vm, onBack = { vm.pop() })
                            is Screen.Trash -> TrashScreen(vm, onBack = { vm.pop() })
                            is Screen.Archive -> ArchiveScreen(vm, screen.path, onBack = { vm.pop() })
                            is Screen.Analyzer -> AnalyzerScreen(
                                vm = vm,
                                path = screen.path,
                                onBack = { vm.pop() },
                            )
                            is Screen.Text -> TextViewerScreen(screen.path, screen.queue, onBack = { vm.pop() })
                            is Screen.Image -> ImageViewerScreen(screen.path, screen.queue, onBack = { vm.pop() })
                            is Screen.Audio -> AudioPlayerScreen(screen.path, onBack = { vm.pop() })
                            is Screen.Video -> VideoPlayerScreen(
                                startPath = screen.path,
                                queue = screen.queue,
                                onBack = { vm.pop() },
                                onOpenWith = { vm.openExternalRef(it) },
                            )
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

        AnimatedVisibility(
            visible = showMini,
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            MiniPlayer(
                onOpen = { vm.showPlayer() },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }

    transfer?.let { TransferDialog(it) }

    if (vm.showPermissionDialog) {
        PermissionDialog(onDismiss = { vm.showPermissionDialog = false }, onGrant = requestAccess)
    }

    zipTarget?.let { item ->
        ZipChoiceDialog(
            item = item,
            onDismiss = { vm.zipTarget.value = null },
            onBrowse = {
                vm.zipTarget.value = null
                vm.push(Screen.Archive(item.path))
            },
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
            FolderIcon(Modifier.size(112.dp))
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
