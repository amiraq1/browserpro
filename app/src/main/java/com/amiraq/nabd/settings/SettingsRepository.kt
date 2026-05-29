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

    fun isCookieProtectionEnabled(): Boolean = prefs.getBoolean(KEY_COOKIE_PROTECTION, true)
    fun setCookieProtectionEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_COOKIE_PROTECTION, enabled).apply() }

    fun isEnhancedBlockingEnabled(): Boolean = prefs.getBoolean(KEY_ENHANCED_BLOCKING, true)
    fun setEnhancedBlockingEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_ENHANCED_BLOCKING, enabled).apply() }

    // ─── Extensions ──────────────────────────────────────────────────────────────

    fun isSponsorBlockEnabled(): Boolean = prefs.getBoolean(KEY_SPONSORBLOCK, false)
    fun setSponsorBlockEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SPONSORBLOCK, enabled).apply() }

    // ─── Agent Bridge ────────────────────────────────────────────────────────────

    fun isAgentBridgeEnabled(): Boolean = prefs.getBoolean(KEY_AGENT_ENABLED, false)
    fun setAgentBridgeEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_AGENT_ENABLED, enabled).apply() }

    fun isAgentDomReadAllowed(): Boolean = prefs.getBoolean(KEY_AGENT_DOM_READ, false)
    fun setAgentDomReadAllowed(enabled: Boolean) { prefs.edit().putBoolean(KEY_AGENT_DOM_READ, enabled).apply() }

    fun isAgentClickAllowed(): Boolean = prefs.getBoolean(KEY_AGENT_CLICK, false)
    fun setAgentClickAllowed(enabled: Boolean) { prefs.edit().putBoolean(KEY_AGENT_CLICK, enabled).apply() }

    fun isAgentTypingAllowed(): Boolean = prefs.getBoolean(KEY_AGENT_TYPING, false)
    fun setAgentTypingAllowed(enabled: Boolean) { prefs.edit().putBoolean(KEY_AGENT_TYPING, enabled).apply() }

    fun isAgentScrollAllowed(): Boolean = prefs.getBoolean(KEY_AGENT_SCROLL, false)
    fun setAgentScrollAllowed(enabled: Boolean) { prefs.edit().putBoolean(KEY_AGENT_SCROLL, enabled).apply() }

    fun isAgentSubmitAllowed(): Boolean = prefs.getBoolean(KEY_AGENT_SUBMIT, false)
    fun setAgentSubmitAllowed(enabled: Boolean) { prefs.edit().putBoolean(KEY_AGENT_SUBMIT, enabled).apply() }

    fun isImmersiveBrowsingEnabled(): Boolean = prefs.getBoolean(KEY_IMMERSIVE_BROWSING, false)
    fun setImmersiveBrowsingEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_IMMERSIVE_BROWSING, enabled).apply() }

    fun isCustomHomepageEnabled(): Boolean = prefs.getBoolean(KEY_CUSTOM_HOMEPAGE, true)
    fun setCustomHomepageEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_CUSTOM_HOMEPAGE, enabled).apply() }

    fun isClearOnExitEnabled(): Boolean = prefs.getBoolean(KEY_CLEAR_ON_EXIT, false)
    fun setClearOnExitEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_CLEAR_ON_EXIT, enabled).apply() }

    // ─── Search Engine Settings ──────────────────────────────────────────────────

    fun getSearchEngineId(): String = prefs.getString(KEY_SEARCH_ENGINE_ID, DEFAULT_SEARCH_ENGINE_ID) ?: DEFAULT_SEARCH_ENGINE_ID
    fun setSearchEngineId(id: String) { prefs.edit().putString(KEY_SEARCH_ENGINE_ID, id).apply() }

    fun getCustomSearchEngineName(): String = prefs.getString(KEY_CUSTOM_SEARCH_NAME, DEFAULT_CUSTOM_SEARCH_NAME) ?: DEFAULT_CUSTOM_SEARCH_NAME
    fun setCustomSearchEngineName(name: String) { prefs.edit().putString(KEY_CUSTOM_SEARCH_NAME, name).apply() }

    fun getCustomSearchEngineTemplate(): String = prefs.getString(KEY_CUSTOM_SEARCH_TEMPLATE, DEFAULT_CUSTOM_SEARCH_TEMPLATE) ?: DEFAULT_CUSTOM_SEARCH_TEMPLATE
    fun setCustomSearchEngineTemplate(template: String) { prefs.edit().putString(KEY_CUSTOM_SEARCH_TEMPLATE, template).apply() }

    // ─── Gesture Settings ────────────────────────────────────────────────────────

    fun isSwipeNavigationEnabled(): Boolean = prefs.getBoolean(KEY_SWIPE_NAV, true)
    fun setSwipeNavigationEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SWIPE_NAV, enabled).apply() }

    fun isPullToRefreshEnabled(): Boolean = prefs.getBoolean(KEY_PULL_REFRESH, true)
    fun setPullToRefreshEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_PULL_REFRESH, enabled).apply() }

    // ─── Session Restore ─────────────────────────────────────────────────────────

    fun isSessionRestoreEnabled(): Boolean = prefs.getBoolean(KEY_SESSION_RESTORE, true)
    fun setSessionRestoreEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SESSION_RESTORE, enabled).apply() }

    // ─── Performance ─────────────────────────────────────────────────────────────

    fun isPerformanceModeEnabled(): Boolean = prefs.getBoolean(KEY_PERFORMANCE_MODE, false)
    fun setPerformanceModeEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_PERFORMANCE_MODE, enabled).apply() }

    // ─── Extension Settings ──────────────────────────────────────────────────────

    fun isSummarizerExtensionEnabled(): Boolean = prefs.getBoolean(KEY_SUMMARIZER_EXT, true)
    fun setSummarizerExtensionEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SUMMARIZER_EXT, enabled).apply() }

    fun isDarkModeExtensionEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE_EXT, false)
    fun setDarkModeExtensionEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_DARK_MODE_EXT, enabled).apply() }

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
            .putBoolean(KEY_CUSTOM_HOMEPAGE, true)
            .putBoolean(KEY_CLEAR_ON_EXIT, false)
            .putString(KEY_SEARCH_ENGINE_ID, DEFAULT_SEARCH_ENGINE_ID)
            .putString(KEY_CUSTOM_SEARCH_NAME, DEFAULT_CUSTOM_SEARCH_NAME)
            .putString(KEY_CUSTOM_SEARCH_TEMPLATE, DEFAULT_CUSTOM_SEARCH_TEMPLATE)
            .putBoolean(KEY_SWIPE_NAV, true)
            .putBoolean(KEY_PULL_REFRESH, true)
            .putBoolean(KEY_SESSION_RESTORE, true)
            .putBoolean(KEY_SUMMARIZER_EXT, true)
            .putBoolean(KEY_DARK_MODE_EXT, false)
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
        private const val KEY_COOKIE_PROTECTION = "cookie_protection_enabled"
        private const val KEY_ENHANCED_BLOCKING = "enhanced_blocking_enabled"
        private const val KEY_SPONSORBLOCK = "sponsorblock_extension_enabled"
        private const val KEY_AGENT_ENABLED = "agent_bridge_enabled"
        private const val KEY_AGENT_DOM_READ = "agent_dom_read_allowed"
        private const val KEY_AGENT_CLICK = "agent_click_allowed"
        private const val KEY_AGENT_TYPING = "agent_typing_allowed"
        private const val KEY_AGENT_SCROLL = "agent_scroll_allowed"
        private const val KEY_AGENT_SUBMIT = "agent_submit_allowed"
        private const val KEY_IMMERSIVE_BROWSING = "immersive_browsing"
        private const val KEY_CUSTOM_HOMEPAGE = "custom_homepage"
        private const val KEY_CLEAR_ON_EXIT = "clear_on_exit"
        private const val KEY_SEARCH_ENGINE_ID = "search_engine_id"
        private const val KEY_CUSTOM_SEARCH_NAME = "custom_search_name"
        private const val KEY_CUSTOM_SEARCH_TEMPLATE = "custom_search_template"
        private const val KEY_SWIPE_NAV = "swipe_navigation_enabled"
        private const val KEY_PULL_REFRESH = "pull_to_refresh_enabled"
        private const val KEY_SESSION_RESTORE = "session_restore_enabled"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_SUMMARIZER_EXT = "summarizer_extension_enabled"
        private const val KEY_DARK_MODE_EXT = "dark_mode_extension_enabled"
        private const val DEFAULT_THEME_MODE = "system"
        private const val DEFAULT_SEARCH_ENGINE_ID = "google"
        private const val DEFAULT_CUSTOM_SEARCH_NAME = "Custom"
        private const val DEFAULT_CUSTOM_SEARCH_TEMPLATE = "https://example.com/search?q=%s"
    }
}
