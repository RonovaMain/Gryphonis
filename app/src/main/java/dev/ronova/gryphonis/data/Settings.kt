package dev.ronova.gryphonis.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gryphonis_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val showHidden: Boolean = false,
    val sortBy: SortBy = SortBy.NAME,
    val sortAscending: Boolean = true,
    val foldersFirst: Boolean = true,
    val gridView: Boolean = false,
    val showThumbnails: Boolean = true,
    val confirmDelete: Boolean = true,
    val ftpPort: Int = 2121,
    val ftpUser: String = "gryphonis",
    val ftpPassword: String = "",
    val ftpReadOnly: Boolean = false,
    val remoteHost: String = "",
    val remotePort: Int = 21,
    val remoteUser: String = "",
    val bookmarks: Set<String> = emptySet(),
)

object Keys {
    val THEME = stringPreferencesKey("theme")
    val DYNAMIC = booleanPreferencesKey("dynamic_color")
    val HIDDEN = booleanPreferencesKey("show_hidden")
    val SORT_BY = stringPreferencesKey("sort_by")
    val SORT_ASC = booleanPreferencesKey("sort_asc")
    val FOLDERS_FIRST = booleanPreferencesKey("folders_first")
    val GRID = booleanPreferencesKey("grid_view")
    val THUMBS = booleanPreferencesKey("thumbnails")
    val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
    val FTP_PORT = intPreferencesKey("ftp_port")
    val FTP_USER = stringPreferencesKey("ftp_user")
    val FTP_PASS = stringPreferencesKey("ftp_pass")
    val FTP_RO = booleanPreferencesKey("ftp_readonly")
    val REMOTE_HOST = stringPreferencesKey("remote_host")
    val REMOTE_PORT = intPreferencesKey("remote_port")
    val REMOTE_USER = stringPreferencesKey("remote_user")
    val BOOKMARKS = stringSetPreferencesKey("bookmarks")
}

class SettingsRepository(private val context: Context) {

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = p[Keys.DYNAMIC] ?: false,
                showHidden = p[Keys.HIDDEN] ?: false,
                sortBy = runCatching { SortBy.valueOf(p[Keys.SORT_BY] ?: "NAME") }.getOrDefault(SortBy.NAME),
                sortAscending = p[Keys.SORT_ASC] ?: true,
                foldersFirst = p[Keys.FOLDERS_FIRST] ?: true,
                gridView = p[Keys.GRID] ?: false,
                showThumbnails = p[Keys.THUMBS] ?: true,
                confirmDelete = p[Keys.CONFIRM_DELETE] ?: true,
                ftpPort = p[Keys.FTP_PORT] ?: 2121,
                ftpUser = p[Keys.FTP_USER] ?: "gryphonis",
                ftpPassword = p[Keys.FTP_PASS] ?: "",
                ftpReadOnly = p[Keys.FTP_RO] ?: false,
                remoteHost = p[Keys.REMOTE_HOST] ?: "",
                remotePort = p[Keys.REMOTE_PORT] ?: 21,
                remoteUser = p[Keys.REMOTE_USER] ?: "",
                bookmarks = p[Keys.BOOKMARKS] ?: emptySet(),
            )
        }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun toggleBookmark(path: String) {
        context.dataStore.edit { p ->
            val cur = p[Keys.BOOKMARKS] ?: emptySet()
            p[Keys.BOOKMARKS] = if (path in cur) cur - path else cur + path
        }
    }
}
