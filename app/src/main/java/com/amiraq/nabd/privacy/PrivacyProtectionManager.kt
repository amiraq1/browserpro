package com.amiraq.nabd.privacy

import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Reads privacy settings and applies GeckoView content blocking configuration.
 *
 * GeckoView 126 ContentBlocking supports:
 * - AntiTracking categories: AD, SOCIAL, CONTENT, CRYPTOMINING, FINGERPRINTING, STP, TEST
 * - CookieBehavior: ACCEPT_ALL, ACCEPT_FIRST_PARTY, ACCEPT_NON_TRACKERS,
 *   ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS, ACCEPT_NONE
 * - SafeBrowsing: DEFAULT, MALWARE, UNWANTED, HARMFUL, PHISHING
 *
 * NOTE: GeckoView's ContentBlocking blocks known trackers from Mozilla's
 * disconnect.me lists. This is NOT full ad blocking (like uBlock Origin).
 * Full ad blocking requires a WebExtension with custom filter lists (EasyList, etc.).
 * The categories here block known tracking/ad domains but not all visual ads.
 */
object PrivacyProtectionManager {

    private const val TAG = "PrivacyProtection"

    /**
     * Builds ContentBlocking settings based on user preferences.
     *
     * Categories explained:
     * - AD: blocks known advertising trackers (disconnect.me ad list)
     * - SOCIAL: blocks social media trackers (Facebook pixel, etc.)
     * - CONTENT: blocks third-party content from tracking domains (aggressive, may break sites)
     * - CRYPTOMINING: blocks known cryptomining scripts
     * - FINGERPRINTING: blocks known fingerprinting scripts
     * - STP: Strict Tracking Protection (additional strict list)
     */
    fun buildContentBlockingSettings(repository: SettingsRepository): ContentBlocking.Settings {
        var antiTrackingCategories = ContentBlocking.AntiTracking.NONE
        val safeBrowsing = ContentBlocking.SafeBrowsing.DEFAULT
        val performanceMode = repository.isPerformanceModeEnabled()

        // Tracker blocking: AD + SOCIAL categories
        if (repository.isTrackerProtectionEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.SOCIAL or
                ContentBlocking.AntiTracking.TEST
        }

        // Ad blocking: AD category + CONTENT (aggressive, blocks third-party resources)
        // Note: This is tracker-based ad blocking, not full cosmetic ad blocking.
        // Full ad blocking (hiding visual ad elements) requires a WebExtension.
        if (repository.isAdBlockEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.AD
            // CONTENT blocking is aggressive — skip in performance mode
            if (!performanceMode) {
                antiTrackingCategories = antiTrackingCategories or
                    ContentBlocking.AntiTracking.CONTENT
            }
        }

        // Cryptominer blocking
        if (repository.isCryptominerBlockingEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.CRYPTOMINING
        }

        // Fingerprinting protection
        if (repository.isFingerprinterBlockingEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.FINGERPRINTING
        }

        // Enhanced blocking adds STP (Strict Tracking Protection list)
        if (repository.isEnhancedBlockingEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.STP
        }

        // Cookie behavior based on cookie protection setting
        val cookieBehavior = if (repository.isCookieProtectionEnabled()) {
            // Block third-party tracking cookies, isolate others (dFPI)
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        }

        Log.d(TAG, "AntiTracking: $antiTrackingCategories, CookieBehavior: $cookieBehavior, Performance: $performanceMode")

        return ContentBlocking.Settings.Builder()
            .antiTracking(antiTrackingCategories)
            .safeBrowsing(safeBrowsing)
            .cookieBehavior(cookieBehavior)
            .build()
    }

    /**
     * Applies content blocking settings to an existing GeckoRuntimeSettings.
     * Called on app launch and when returning from Settings.
     * Changes take effect immediately for the runtime (no session recreation needed).
     */
    fun applyToRuntime(runtimeSettings: GeckoRuntimeSettings, repository: SettingsRepository) {
        try {
            val cbSettings = buildContentBlockingSettings(repository)
            runtimeSettings.contentBlocking.setAntiTracking(cbSettings.antiTrackingCategories)
            runtimeSettings.contentBlocking.setSafeBrowsing(cbSettings.safeBrowsingCategories)
            runtimeSettings.contentBlocking.setCookieBehavior(cbSettings.cookieBehavior)

            // Apply preferred color scheme for performance (avoids re-render)
            runtimeSettings.setPreferredColorScheme(
                if (repository.getThemeMode() == "dark")
                    GeckoRuntimeSettings.COLOR_SCHEME_DARK
                else
                    GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            )

            Log.d(TAG, "Content blocking applied to runtime")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply content blocking settings", e)
        }
    }
}
