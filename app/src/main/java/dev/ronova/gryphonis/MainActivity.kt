package dev.ronova.gryphonis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.text.DateFormat
import java.util.Date

private val Bg = Color(0xFF10101A)
private val Surface = Color(0xFF1B1B2A)
private val Muted = Color(0xFFAAA8C4)
private val Pink = Color(0xFFFF91D2)
private val Blue = Color(0xFF71C8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GryphonisApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GryphonisApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gryphonis", 0) }
    var rootUri by remember { mutableStateOf(prefs.getString("tree", null)?.let(Uri::parse)) }
    var currentDir by remember { mutableStateOf(rootUri?.let { DocumentFile.fromTreeUri(context, it) }) }
    val path = remember { mutableStateListOf<DocumentFile>() }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("name") }
    var showCreate by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DocumentFile?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var recentUris by remember { mutableStateOf(prefs.getString("recent", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()) }
    var tab by remember { mutableStateOf("files") }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
            rootUri = uri
            prefs.edit().putString("tree", uri.toString()).apply()
            currentDir = DocumentFile.fromTreeUri(context, uri)
            path.clear()
            refresh++
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB99AFF), secondary = Pink, background = Bg,
        surface = Surface, onBackground = Color(0xFFF5F2FF), onSurface = Color(0xFFF5F2FF)
    )) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                Column(Modifier.background(Bg).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Gryphonis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(when (tab) { "favorites" -> "Избранное"; "recent" -> "Недавние файлы"; else -> "Ваши файлы под контролем" }, color = Muted, style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { showHidden = !showHidden }) { Icon(if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Скрытые файлы", tint = Muted) }
                        IconButton(onClick = { sortBy = if (sortBy == "name") "date" else "name" }) { Icon(Icons.Default.Sort, "Сортировка", tint = Muted) }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск файлов и папок…", color = Muted) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } },
                        singleLine = true, shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Surface, focusedBorderColor = Color(0xFFB99AFF))
                    )
                    if (currentDir != null && tab == "files") {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, null, tint = Color(0xFFB99AFF), modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text((listOf("Хранилище") + path.map { it.name ?: "Папка" }).joinToString(" / "), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            floatingActionButton = {
                if (tab == "files" && currentDir != null) {
                    FloatingActionButton(onClick = { showCreate = true }, containerColor = Color(0xFFB99AFF), contentColor = Bg) { Icon(Icons.Default.CreateNewFolder, "Создать папку") }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Surface) {
                    NavigationBarItem(selected = tab == "files", onClick = { tab = "files" }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Файлы") })
                    NavigationBarItem(selected = tab == "favorites", onClick = { tab = "favorites" }, icon = { Icon(Icons.Default.Star, null) }, label = { Text("Избранное") })
                    NavigationBarItem(selected = tab == "recent", onClick = { tab = "recent" }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Недавние") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                if (rootUri == null) {
                    Spacer(Modifier.height(60.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FolderMark(90)
                            Spacer(Modifier.height(18.dp))
                            Text("Начнём с выбора папки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("Выберите папку хранилища. Gryphonis получит доступ только к выбранному вами расположению.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { treePicker.launch(null) }) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Выбрать папку") }
                        }
                    }
                } else {
                    val allFiles = remember(currentDir, refresh, showHidden, sortBy, query, tab, favorites, recentUris) {
                        val source = when (tab) {
                            "favorites" -> favorites.mapNotNull { DocumentFile.fromSingleUri(context, Uri.parse(it)) }
                            "recent" -> recentUris.mapNotNull { DocumentFile.fromSingleUri(context, Uri.parse(it)) }
                            else -> currentDir?.listFiles()?.toList() ?: emptyList()
                        }
                        val filtered = source.filter { showHidden || !(it.name ?: "").startsWith(".") }
                            .filter { (it.name ?: "").contains(query, ignoreCase = true) }
                        if (sortBy == "date") filtered.sortedByDescending { it.lastModified() } else filtered.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })
                    }
                    if (tab == "files" && path.isNotEmpty()) {
                        TextButton(onClick = { currentDir = path.removeAt(path.lastIndex); refresh++ }) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Назад") }
                    }
                    if (tab == "recent") {
                        Text("Последние открытые файлы", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    if (allFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOpen, null, tint = Muted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(if (query.isNotBlank()) "Ничего не найдено" else "Здесь пока пусто", color = Muted)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(allFiles, key = { it.uri.toString() }) { file ->
                                FileRow(file = file, favorite = favorites.contains(file.uri.toString()), onClick = {
                                    if (file.isDirectory) { currentDir?.let { path.add(it) }; currentDir = file; query = ""; refresh++ }
                                    else { recordRecent(file, recentUris) { updated -> recentUris = updated; prefs.edit().putString("recent", updated.joinToString("\n")).apply() }; openFile(context, file) }
                                }, onMore = { selected = file; showActions = true })
                            }
                        }
                    }
                    if (tab == "files") {
                        TextButton(onClick = { treePicker.launch(rootUri) }, modifier = Modifier.align(Alignment.Start)) { Icon(Icons.Default.FolderSpecial, null); Spacer(Modifier.width(6.dp)); Text("Сменить папку хранилища") }
                    }
                }
            }
        }

        if (showCreate) AlertDialog(
            onDismissRequest = { showCreate = false; folderName = "" },
            title = { Text("Новая папка") },
            text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (folderName.isNotBlank()) currentDir?.createDirectory(folderName.trim()); refresh++; showCreate = false; folderName = "" }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } }
        )

        if (showActions && selected != null) AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(selected?.name ?: "Элемент", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionLine(Icons.Default.OpenInNew, "Открыть") { selected?.let { if (it.isDirectory) { currentDir?.let { d -> path.add(d) }; currentDir = it; refresh++ } else { recordRecent(it, recentUris) { updated -> recentUris = updated; prefs.edit().putString("recent", updated.joinToString("\n")).apply() }; openFile(context, it) } }; showActions = false }
                    ActionLine(Icons.Default.Star, if (selected?.let { favorites.contains(it.uri.toString()) } == true) "Убрать из избранного" else "В избранное") {
                        selected?.let { item -> favorites = favorites.toMutableSet().apply { if (!add(item.uri.toString())) remove(item.uri.toString()) }; prefs.edit().putStringSet("favorites", favorites).apply() }; showActions = false
                    }
                    ActionLine(Icons.Default.DriveFileRenameOutline, "Переименовать") { renameText = selected?.name ?: ""; showRename = true; showActions = false }
                    ActionLine(Icons.Default.Share, "Поделиться") { selected?.let { shareFile(context, it) }; showActions = false }
                    ActionLine(Icons.Default.Info, "Свойства") { selected?.let { android.widget.Toast.makeText(context, "${if (it.isDirectory) "Папка" else "Файл"} • ${formatSize(it.length())}\n${it.uri}", android.widget.Toast.LENGTH_LONG).show() }; showActions = false }
                    ActionLine(Icons.Default.DeleteOutline, "Удалить", Color(0xFFFF8DAB)) { showDelete = true; showActions = false }
                }
            }, confirmButton = { TextButton(onClick = { showActions = false }) { Text("Готово") } }
        )
        if (showRename) AlertDialog(
            onDismissRequest = { showRename = false }, title = { Text("Переименовать") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) selected?.renameTo(renameText.trim()); refresh++; showRename = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Отмена") } }
        )
        if (showDelete) AlertDialog(
            onDismissRequest = { showDelete = false }, title = { Text("Удалить элемент?") },
            text = { Text("«${selected?.name}» будет удалён из выбранного хранилища.") },
            confirmButton = { TextButton(onClick = { selected?.delete(); refresh++; showDelete = false }) { Text("Удалить", color = Color(0xFFFF8DAB)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun FileRow(file: DocumentFile, favorite: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFA4DA), Color(0xFF79C9FF)))), contentAlignment = Alignment.Center) {
            Icon(if (file.isDirectory) Icons.Default.Folder else fileIcon(file.name), null, tint = Color(0xFF202038), modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name ?: "Без названия", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (file.isDirectory) "Папка • ${file.listFiles().size} элементов" else formatSize(file.length()), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (favorite) Icon(Icons.Default.Star, null, tint = Color(0xFFFFD36D), modifier = Modifier.size(17.dp))
        IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Действия", tint = Muted) }
    }
}

@Composable
private fun ActionLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color(0xFFEDE9FF), action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = action).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint); Spacer(Modifier.width(13.dp)); Text(label, color = tint)
    }
}

@Composable
private fun FolderMark(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .23f).dp)).background(Brush.linearGradient(listOf(Pink, Color(0xFFB79AFF), Blue))), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Folder, null, tint = Color(0xFF29223F), modifier = Modifier.size((size * .62f).dp))
    }
}

private fun fileIcon(name: String?): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = name?.substringAfterLast('.', "")?.lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "mp4", "mkv", "mov", "webm" -> Icons.Default.Movie
        "mp3", "wav", "ogg", "flac" -> Icons.Default.AudioFile
        "pdf" -> Icons.Default.PictureAsPdf
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "txt", "md", "log" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

private fun recordRecent(file: DocumentFile, current: List<String>, update: (List<String>) -> Unit) {
    update((listOf(file.uri.toString()) + current.filterNot { it == file.uri.toString() }).take(30))
}

private fun openFile(context: android.content.Context, file: DocumentFile) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, context.contentResolver.getType(file.uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть с помощью"))
    } catch (_: Exception) { android.widget.Toast.makeText(context, "Не найдено приложение для открытия файла", android.widget.Toast.LENGTH_SHORT).show() }
}

private fun shareFile(context: android.content.Context, file: DocumentFile) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(file.uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться"))
    } catch (_: Exception) { android.widget.Toast.makeText(context, "Не удалось поделиться файлом", android.widget.Toast.LENGTH_SHORT).show() }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "%.1f КБ".format(bytes / 1024f)
    bytes < 1024L * 1024L * 1024L -> "%.1f МБ".format(bytes / (1024f * 1024f))
    else -> "%.2f ГБ".format(bytes / (1024f * 1024f * 1024f))
}
