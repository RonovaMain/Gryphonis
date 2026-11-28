package dev.ronova.gryphonis.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.gryphonis.R
import dev.ronova.gryphonis.data.AppPalette
import dev.ronova.gryphonis.data.Keys
import dev.ronova.gryphonis.data.SortBy
import dev.ronova.gryphonis.data.ThemeMode
import dev.ronova.gryphonis.ui.MainViewModel
import dev.ronova.gryphonis.ui.Screen
import dev.ronova.gryphonis.ui.components.FolderIcon
import dev.ronova.gryphonis.ui.components.SortDialog
import dev.ronova.gryphonis.ui.theme.GryphLilac
import dev.ronova.gryphonis.ui.theme.GryphPink
import dev.ronova.gryphonis.ui.theme.GryphSky
import dev.ronova.gryphonis.ui.theme.GryphTheme
import dev.ronova.gryphonis.ui.theme.previewColors
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.luminance
import dev.ronova.gryphonis.util.OpenUtil
import dev.ronova.gryphonis.util.StorageUtil

private const val AUTHOR_NAME = "RonovaDev"
private const val GITHUB_USER = "RonovaMain"
private const val GITHUB_URL = "https://github.com/RonovaMain"
private const val DONATE_URL = "https://boosty.to/ronovamain/donate"

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    var showTheme by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showTrashDays by remember { mutableStateOf(false) }

    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {

            SettingsHeader(stringResource(R.string.set_appearance))
            ClickRow(
                Icons.Rounded.Brightness4,
                stringResource(R.string.set_theme),
                stringResource(
                    when (s.themeMode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                ),
            ) { showTheme = true }
            ClickRow(
                Icons.Rounded.ColorLens,
                stringResource(R.string.set_palette),
                stringResource(
                    if (s.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.set_palette_dynamic_on
                    else paletteName(s.palette)
                ),
            ) { showPalette = true }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    Icons.Rounded.Palette,
                    stringResource(R.string.set_dynamic),
                    stringResource(R.string.set_dynamic_sub),
                    s.dynamicColor,
                ) { vm.setPref(Keys.DYNAMIC, it) }
            }
            SwitchRow(Icons.Rounded.ViewModule, stringResource(R.string.set_grid), null, s.gridView) { vm.setPref(Keys.GRID, it) }
            SwitchRow(
                Icons.Rounded.Image,
                stringResource(R.string.set_thumbs),
                stringResource(R.string.set_thumbs_sub),
                s.showThumbnails,
            ) { vm.setPref(Keys.THUMBS, it) }

            SettingsHeader(stringResource(R.string.set_home))
            SwitchRow(
                Icons.Rounded.Folder,
                stringResource(R.string.set_quick_show),
                stringResource(R.string.set_quick_show_sub),
                s.quickAccess,
            ) { vm.setPref(Keys.QUICK_ENABLED, it) }
            ClickRow(
                Icons.Rounded.Sort,
                stringResource(R.string.set_quick_folders),
                if (s.quickAccess) {
                    stringResource(R.string.set_quick_folders_count, s.quickFolders?.size ?: StorageUtil.defaultQuickFolders().size)
                } else {
                    stringResource(R.string.set_quick_off)
                },
            ) { vm.push(Screen.QuickAccess) }

            SettingsHeader(stringResource(R.string.set_files))
            SwitchRow(
                Icons.Rounded.Visibility,
                stringResource(R.string.menu_hidden),
                stringResource(R.string.set_hidden_sub),
                s.showHidden,
            ) { vm.setPref(Keys.HIDDEN, it) }
            ClickRow(
                Icons.Rounded.Sort,
                stringResource(R.string.menu_sort),
                stringResource(
                    when (s.sortBy) {
                        SortBy.NAME -> R.string.sort_name
                        SortBy.DATE -> R.string.sort_date
                        SortBy.TYPE -> R.string.sort_type
                        SortBy.SIZE -> R.string.sort_size
                    }
                ) + if (s.sortAscending) " ↑" else " ↓",
            ) { showSort = true }
            SwitchRow(
                Icons.Rounded.Delete,
                stringResource(R.string.set_confirm_delete),
                stringResource(R.string.set_confirm_delete_sub),
                s.confirmDelete,
            ) { vm.setPref(Keys.CONFIRM_DELETE, it) }
            ClickRow(
                Icons.Rounded.Delete,
                stringResource(R.string.set_trash_days),
                stringResource(R.string.set_trash_days_value, s.trashDays),
            ) { showTrashDays = true }

            SettingsHeader(stringResource(R.string.set_network))
            ClickRow(
                Icons.Rounded.Dns,
                stringResource(R.string.tool_ftp_server),
                stringResource(R.string.set_ftp_sub),
            ) { vm.push(Screen.FtpServer) }

            SettingsHeader(stringResource(R.string.set_access))
            ClickRow(
                Icons.Rounded.Lock,
                stringResource(R.string.set_all_files),
                stringResource(if (vm.hasAccess) R.string.set_access_granted else R.string.set_access_missing),
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) StorageUtil.openAllFilesSettings(ctx)
            }

            SettingsHeader(stringResource(R.string.set_about))
            AboutCard(version)
            ClickRow(
                Icons.Rounded.Person,
                stringResource(R.string.about_author),
                AUTHOR_NAME,
            ) { OpenUtil.openUrl(ctx, GITHUB_URL) }
            ClickRow(
                Icons.Rounded.Code,
                "GitHub",
                "github.com/$GITHUB_USER",
                trailing = Icons.Rounded.OpenInNew,
            ) { OpenUtil.openUrl(ctx, GITHUB_URL) }
            ClickRow(
                Icons.Rounded.Favorite,
                stringResource(R.string.about_support),
                stringResource(R.string.about_support_sub),
                trailing = Icons.Rounded.OpenInNew,
            ) { OpenUtil.openUrl(ctx, DONATE_URL) }
            Text(
                stringResource(R.string.about_libs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text(stringResource(R.string.set_theme)) },
            text = {
                Column {
                    val options = listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    )
                    for ((mode, label) in options) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setPref(Keys.THEME, mode.name)
                                    showTheme = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.themeMode == mode, onClick = {
                                vm.setPref(Keys.THEME, mode.name)
                                showTheme = false
                            })
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTheme = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPalette) {
        PaletteDialog(
            current = s.palette,
            onPick = {
                vm.setPref(Keys.PALETTE, it.name)
                // выбор схемы сразу «включает» её: динамические цвета её бы перекрывали
                if (s.dynamicColor) vm.setPref(Keys.DYNAMIC, false)
            },
            onDismiss = { showPalette = false },
        )
    }
    if (showTrashDays) {
        AlertDialog(
            onDismissRequest = { showTrashDays = false },
            title = { Text(stringResource(R.string.set_trash_days)) },
            text = {
                Column {
                    for (days in listOf(7, 14, 30, 60, 90)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setPref(Keys.TRASH_DAYS, days)
                                    showTrashDays = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.trashDays == days, onClick = {
                                vm.setPref(Keys.TRASH_DAYS, days)
                                showTrashDays = false
                            })
                            Text(stringResource(R.string.set_trash_days_value, days))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTrashDays = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showSort) {
        SortDialog(
            sortBy = s.sortBy,
            ascending = s.sortAscending,
            foldersFirst = s.foldersFirst,
            onSortBy = { vm.setPref(Keys.SORT_BY, it.name) },
            onAscending = { vm.setPref(Keys.SORT_ASC, it) },
            onFoldersFirst = { vm.setPref(Keys.FOLDERS_FIRST, it) },
            onDismiss = { showSort = false },
        )
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun ClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun AboutCard(version: String) {
    val rainbow = GryphTheme.extras.rainbow
    val onCard = if (rainbow) androidx.compose.ui.graphics.Color(0xFF2B1633) else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (rainbow) Brush.linearGradient(listOf(GryphPink, GryphLilac, GryphSky))
                else Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer))
            ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FolderIcon(Modifier.size(80.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = onCard,
            )
            Text(
                if (version.isEmpty()) "" else "v$version",
                style = MaterialTheme.typography.bodyMedium,
                color = onCard.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_tagline),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = onCard,
            )
        }
    }
}


private fun paletteName(p: AppPalette): Int = when (p) {
    AppPalette.DEFAULT -> R.string.palette_default
    AppPalette.GRYPHONIS -> R.string.palette_gryphonis
    AppPalette.OCEAN -> R.string.palette_ocean
    AppPalette.FOREST -> R.string.palette_forest
    AppPalette.VIOLET -> R.string.palette_violet
    AppPalette.ROSE -> R.string.palette_rose
    AppPalette.AMBER -> R.string.palette_amber
    AppPalette.GRAPHITE -> R.string.palette_graphite
}

private fun paletteHint(p: AppPalette): Int = when (p) {
    AppPalette.DEFAULT -> R.string.palette_default_sub
    AppPalette.GRYPHONIS -> R.string.palette_gryphonis_sub
    else -> R.string.palette_mono_sub
}

/** Маленький предпросмотр схемы: фон в текущем режиме (светлый/тёмный) и акцентный кружок. */
@Composable
private fun PaletteSwatch(palette: AppPalette, dark: Boolean, modifier: Modifier = Modifier) {
    val (bg, accent) = previewColors(palette, dark)
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val dot = Modifier.size(24.dp).clip(CircleShape)
        if (palette == AppPalette.GRYPHONIS) {
            Box(dot.background(Brush.linearGradient(listOf(GryphPink, GryphLilac, GryphSky))))
        } else {
            Box(dot.background(accent))
        }
    }
}

@Composable
private fun PaletteDialog(current: AppPalette, onPick: (AppPalette) -> Unit, onDismiss: () -> Unit) {
    // какой вариант показывать в предпросмотре — смотрим на то, что сейчас на экране
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_palette)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (p in AppPalette.values()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPick(p) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PaletteSwatch(p, dark, Modifier.size(46.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(paletteName(p)), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(paletteHint(p)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RadioButton(selected = current == p, onClick = { onPick(p) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
