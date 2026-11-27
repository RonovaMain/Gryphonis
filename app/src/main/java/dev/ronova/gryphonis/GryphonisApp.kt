package dev.ronova.gryphonis

import android.app.Application
import dev.ronova.gryphonis.data.SettingsRepository
import dev.ronova.gryphonis.install.PackageInstall

class GryphonisApp : Application() {
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // временные копии пакетов могли остаться, если приложение закрыли посреди установки
        Thread { PackageInstall.cleanCache(this) }.start()
    }
}
