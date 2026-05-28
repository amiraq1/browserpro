package com.amiraq.nabd.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.amiraq.nabd.settings.SettingsRepository

/**
 * Manages the app's theme mode (system/light/dark).
 * Reads the preference from [SettingsRepository] and applies it
 * via [AppCompatDelegate.setDefaultNightMode].
 */
object ThemeManager {

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    /**
     * Applies the saved theme preference. Call early in the app lifecycle
     * (e.g., in Application.onCreate or before setContentView in the launcher activity).
     */
    fun applyTheme(context: Context) {
        val repository = SettingsRepository(context)
        applyThemeMode(repository.getThemeMode())
    }

    /**
     * Applies a specific theme mode string.
     */
    fun applyThemeMode(mode: String) {
        val nightMode = when (mode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
