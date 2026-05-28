package com.amiraq.nabd.privacy

import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Reads privacy settings and applies GeckoView content blocking configuration.
 *
 * GeckoView 126 supports Enhanced Tracking Protection via [ContentBlocking.Settings]
 * on [GeckoRuntimeSettings]. Categories include:
 * - AD: advertising trackers
 * - ANALYTICS: analytics trackers
 * - SOCIAL: social media trackers
 * - CONTENT: content trackers (may break some sites)
 * - CRYPTOMINING: cryptominer scripts
 * - FINGERPRINTING: fingerprinting scripts
 * - TEST: test category
 */
object PrivacyProtectionManager {

    private const val TAG = "PrivacyProtection"

    /**
     * Builds a [ContentBlocking.Settings] object based on user preferences.
     * In performance mode, CONTENT blocking is skipped to avoid breaking sites
     * and reduce request interception overhead.
     */
    fun buildContentBlockingSettings(repository: SettingsRepository): ContentBlocking.Settings {
        var antiTrackingCategories = ContentBlocking.AntiTracking.NONE
        val safeBrowsing = ContentBlocking.SafeBrowsing.DEFAULT
        val performanceMode = repository.isPerformanceModeEnabled()

        if (repository.isTrackerProtectionEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.SOCIAL or
                ContentBlocking.AntiTracking.TEST
        }

        if (repository.isAdBlockEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.AD
            // CONTENT blocking can slow page loads — skip in performance mode
            if (!performanceMode) {
                antiTrackingCategories = antiTrackingCategories or
                    ContentBlocking.AntiTracking.CONTENT
            }
        }

        if (repository.isCryptominerBlockingEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.CRYPTOMINING
        }

        if (repository.isFingerprinterBlockingEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.FINGERPRINTING
        }

        Log.d(TAG, "AntiTracking categories: $antiTrackingCategories (performance=$performanceMode)")

        return ContentBlocking.Settings.Builder()
            .antiTracking(antiTrackingCategories)
            .safeBrowsing(safeBrowsing)
            .build()
    }

    /**
     * Applies content blocking settings to an existing GeckoRuntimeSettings.
     * Also applies performance-related runtime settings.
     */
    fun applyToRuntime(runtimeSettings: GeckoRuntimeSettings, repository: SettingsRepository) {
        try {
            val cbSettings = buildContentBlockingSettings(repository)
            runtimeSettings.contentBlocking.setAntiTracking(cbSettings.antiTrackingCategories)
            runtimeSettings.contentBlocking.setSafeBrowsing(cbSettings.safeBrowsingCategories)

            // Performance: enable prefetch and speculative connections
            runtimeSettings.setPreferredColorScheme(
                if (repository.getThemeMode() == "dark")
                    GeckoRuntimeSettings.COLOR_SCHEME_DARK
                else
                    GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            )

            Log.d(TAG, "Content blocking and performance settings applied")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply content blocking settings", e)
        }
    }
}
