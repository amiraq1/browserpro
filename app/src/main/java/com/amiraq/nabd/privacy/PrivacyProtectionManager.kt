package com.amiraq.nabd.privacy

import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Reads privacy settings and applies GeckoView content blocking configuration.
 *
 * GeckoView content blocking complements the built-in request-blocking
 * WebExtension used for ad blocking.
 */
object PrivacyProtectionManager {

    private const val TAG = "PrivacyProtection"

    fun buildContentBlockingSettings(repository: SettingsRepository): ContentBlocking.Settings {
        var antiTrackingCategories = ContentBlocking.AntiTracking.NONE
        val safeBrowsing = ContentBlocking.SafeBrowsing.DEFAULT
        val performanceMode = repository.isPerformanceModeEnabled()

        if (repository.isTrackerProtectionEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL
        }

        if (repository.isAdBlockEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.AD
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

        if (repository.isEnhancedBlockingEnabled()) {
            antiTrackingCategories = antiTrackingCategories or
                ContentBlocking.AntiTracking.STP
        }

        val cookieBehavior = if (repository.isCookieProtectionEnabled()) {
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
     */
    fun applyToRuntime(runtimeSettings: GeckoRuntimeSettings, repository: SettingsRepository) {
        try {
            val cbSettings = buildContentBlockingSettings(repository)
            runtimeSettings.contentBlocking.setAntiTracking(cbSettings.antiTrackingCategories)
            runtimeSettings.contentBlocking.setSafeBrowsing(cbSettings.safeBrowsingCategories)
            runtimeSettings.contentBlocking.setCookieBehavior(cbSettings.cookieBehavior)

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
