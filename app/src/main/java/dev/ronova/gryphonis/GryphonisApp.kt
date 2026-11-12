package dev.ronova.gryphonis

import android.app.Application
import dev.ronova.gryphonis.data.SettingsRepository

class GryphonisApp : Application() {
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(this) }
}
