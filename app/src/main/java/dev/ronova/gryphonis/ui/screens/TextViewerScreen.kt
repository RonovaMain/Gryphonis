package dev.ronova.gryphonis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.ui.components.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

private const val MAX_BYTES = 1024 * 1024

private class LoadedText(val text: String, val truncated: Boolean, val binary: Boolean, val charset: Charset)

private fun loadText(file: File): LoadedText {
    val out = ByteArrayOutputStream()
    var truncated = false
    FileInputStream(file).use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) {
                out.write(buf, 0, MAX_BYTES - out.size())
                truncated = true
                break
            }
            out.write(buf, 0, n)
        }
    }
    val bytes = out.toByteArray()
    val head = minOf(bytes.size, 8000)
    var binary = false
    for (i in 0 until head) {
        if (bytes[i].toInt() == 0) {
            binary = true
            break
        }
    }
    val utf8 = String(bytes, Charsets.UTF_8)
    val charset = if (!truncated && utf8.contains('\uFFFD')) Charset.forName("windows-1251") else Charsets.UTF_8
    val text = if (charset == Charsets.UTF_8) utf8 else String(bytes, charset)
    return LoadedText(text, truncated, binary, charset)
}

@Composable
fun TextViewerScreen(path: String, onBack: () -> Unit) {
    val file = remember(path) { File(path) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.saved)
    val errorMsg = stringResource(R.string.save_failed)

    var loaded by remember(path) { mutableStateOf<LoadedText?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var forceOpen by remember(path) { mutableStateOf(false) }
    var editing by remember(path) { mutableStateOf(false) }
    var draft by remember(path) { mutableStateOf("") }
    var fontSize by remember { mutableIntStateOf(14) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        try {
            val l = withContext(Dispatchers.IO) { loadText(file) }
            loaded = l
            draft = l.text
        } catch (e: Exception) {
            failed = true
        }
    }

    val l = loaded
    val dirty = editing && l != null && draft != l.text
    val canEdit = l != null && !l.truncated && !l.binary

    BackHandler(enabled = true) {
        if (dirty) confirmDiscard = true else onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { fontSize = if (fontSize >= 24) 10 else fontSize + 2 }) {
                        Icon(Icons.Rounded.FormatSize, contentDescription = null)
                    }
                    if (l != null) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(if (editing) draft else l.text)) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        }
                    }
                    if (canEdit && l != null) {
                        if (editing) {
                            IconButton(
                                enabled = dirty,
                                onClick = {
                                    val content = draft
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            try {
                                                file.writeText(content, l.charset)
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                        }
                                        if (ok) {
                                            loaded = LoadedText(content, false, false, l.charset)
                                            snackbar.showSnackbar(savedMsg)
                                        } else {
                                            snackbar.showSnackbar(errorMsg)
                                        }
                                    }
                                },
                            ) { Icon(Icons.Rounded.Save, contentDescription = null) }
                        } else {
                            IconButton(onClick = {
                                draft = l.text
                                editing = true
                            }) { Icon(Icons.Rounded.Edit, contentDescription = null) }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cannot_read_file), color = MaterialTheme.colorScheme.error)
                }
                l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                l.binary && !forceOpen -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.text_binary_warning))
                    TextButton(onClick = { forceOpen = true }) { Text(stringResource(R.string.open_anyway)) }
                }
                editing -> OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp),
                    modifier = Modifier.fillMaxSize().imePadding().padding(8.dp),
                )
                else -> {
                    val lines = remember(l.text) { l.text.split('\n') }
                    Column(Modifier.fillMaxSize()) {
                        if (l.truncated) {
                            Text(
                                stringResource(R.string.text_truncated),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
                            itemsIndexed(lines) { index, line ->
                                Text(
                                    line.ifEmpty { " " }.trimEnd('\r'),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmLabel = stringResource(R.string.discard),
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
        )
    }
}
