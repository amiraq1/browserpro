package com.amiraq.nabd.settings

import android.content.Context
import android.content.SharedPreferences
import com.amiraq.nabd.config.AppConfig

/**
 * Reads and writes summarizer settings using SharedPreferences.
 * Provides default values from [AppConfig] when no user preference is stored.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRemoteSummarizerEnabled(): Boolean {
        return prefs.getBoolean(KEY_USE_REMOTE, AppConfig.DEFAULT_USE_REMOTE_SUMMARIZER)
    }

    fun setRemoteSummarizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_REMOTE, enabled).apply()
    }

    fun getSummarizerEndpoint(): String {
        return prefs.getString(KEY_ENDPOINT, AppConfig.DEFAULT_SUMMARIZER_ENDPOINT)
            ?: AppConfig.DEFAULT_SUMMARIZER_ENDPOINT
    }

    fun setSummarizerEndpoint(url: String) {
        prefs.edit().putString(KEY_ENDPOINT, url).apply()
    }

    fun getSummaryLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, AppConfig.DEFAULT_SUMMARY_LANGUAGE)
            ?: AppConfig.DEFAULT_SUMMARY_LANGUAGE
    }

    fun setSummaryLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getSummaryMaxLength(): Int {
        return prefs.getInt(KEY_MAX_LENGTH, AppConfig.DEFAULT_SUMMARY_MAX_LENGTH)
    }

    fun setSummaryMaxLength(length: Int) {
        prefs.edit().putInt(KEY_MAX_LENGTH, length).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    // ─── Privacy Settings ────────────────────────────────────────────────────────

    fun isAdBlockEnabled(): Boolean = prefs.getBoolean(KEY_AD_BLOCK, true)
    fun setAdBlockEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_AD_BLOCK, enabled).apply() }

    fun isTrackerProtectionEnabled(): Boolean = prefs.getBoolean(KEY_TRACKER_PROTECTION, true)
    fun setTrackerProtectionEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_TRACKER_PROTECTION, enabled).apply() }

    fun isCryptominerBlockingEnabled(): Boolean = prefs.getBoolean(KEY_CRYPTOMINER_BLOCK, true)
    fun setCryptominerBlockingEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_CRYPTOMINER_BLOCK, enabled).apply() }

    fun isFingerprinterBlockingEnabled(): Boolean = prefs.getBoolean(KEY_FINGERPRINTER_BLOCK, true)
    fun setFingerprinterBlockingEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_FINGERPRINTER_BLOCK, enabled).apply() }

    fun isImmersiveBrowsingEnabled(): Boolean = prefs.getBoolean(KEY_IMMERSIVE_BROWSING, false)
    fun setImmersiveBrowsingEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_IMMERSIVE_BROWSING, enabled).apply() }

    fun isCustomHomepageEnabled(): Boolean = prefs.getBoolean(KEY_CUSTOM_HOMEPAGE, true)
    fun setCustomHomepageEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_CUSTOM_HOMEPAGE, enabled).apply() }

    fun isClearOnExitEnabled(): Boolean = prefs.getBoolean(KEY_CLEAR_ON_EXIT, false)
    fun setClearOnExitEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_CLEAR_ON_EXIT, enabled).apply() }

    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_USE_REMOTE, AppConfig.DEFAULT_USE_REMOTE_SUMMARIZER)
            .putString(KEY_ENDPOINT, AppConfig.DEFAULT_SUMMARIZER_ENDPOINT)
            .putString(KEY_LANGUAGE, AppConfig.DEFAULT_SUMMARY_LANGUAGE)
            .putInt(KEY_MAX_LENGTH, AppConfig.DEFAULT_SUMMARY_MAX_LENGTH)
            .putString(KEY_THEME_MODE, DEFAULT_THEME_MODE)
            .putBoolean(KEY_AD_BLOCK, true)
            .putBoolean(KEY_TRACKER_PROTECTION, true)
            .putBoolean(KEY_CRYPTOMINER_BLOCK, true)
            .putBoolean(KEY_FINGERPRINTER_BLOCK, true)
            .putBoolean(KEY_IMMERSIVE_BROWSING, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "nabd_summarizer_settings"
        private const val KEY_USE_REMOTE = "use_remote_summarizer"
        private const val KEY_ENDPOINT = "summarizer_endpoint"
        private const val KEY_LANGUAGE = "summary_language"
        private const val KEY_MAX_LENGTH = "summary_max_length"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AD_BLOCK = "ad_block_enabled"
        private const val KEY_TRACKER_PROTECTION = "tracker_protection_enabled"
        private const val KEY_CRYPTOMINER_BLOCK = "cryptominer_block_enabled"
        private const val KEY_FINGERPRINTER_BLOCK = "fingerprinter_block_enabled"
        private const val KEY_IMMERSIVE_BROWSING = "immersive_browsing"
        private const val KEY_CUSTOM_HOMEPAGE = "custom_homepage"
        private const val KEY_CLEAR_ON_EXIT = "clear_on_exit"
        private const val DEFAULT_THEME_MODE = "system"
    }
}
