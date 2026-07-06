package com.example.lowlevelide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.example.lowlevelide.settings.AppSettings
import com.example.lowlevelide.util.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application entry point. Wires up:
 *  - DataStore-backed [AppSettings] singleton.
 *  - Notification channel for the foreground terminal service.
 *  - Initial theme based on the user's saved preference.
 */
class App : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: AppSettings
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Install the global crash handler first so we capture failures in the rest of startup.
        CrashHandler.install(this)
        settings = AppSettings(this)
        applicationScope.launch {
            // applicationScope runs on Dispatchers.Default; AppCompatDelegate night-mode must be
            // applied on the main thread, so read the preference off-main then switch to Main.
            val theme = settings.themeFlow.first()
            withContext(Dispatchers.Main) { applyTheme(theme) }
        }
        createNotificationChannel()
    }

    private fun applyTheme(themeKey: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (themeKey) {
                AppSettings.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppSettings.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_TERMINALS,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            mgr?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_TERMINALS = "terminals"

        @Volatile
        private var instance: App? = null
        fun get(): App = instance ?: error("App not yet created")
    }
}
