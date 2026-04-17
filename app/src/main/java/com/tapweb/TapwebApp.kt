package com.tapweb

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.tapweb.ui.settings.SettingsActivity

class TapwebApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applySavedTheme()
    }

    private fun applySavedTheme() {
        val mode = SettingsActivity.getThemeMode(this)
        val nightMode = when (mode) {
            SettingsActivity.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsActivity.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
