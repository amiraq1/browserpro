package com.amiraq.nabd.extensions

import android.content.Context
import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Manages embedded WebExtensions for the Nabd browser.
 *
 * Extensions are loaded from APK assets using GeckoView's ensureBuiltIn API.
 * Each extension can be enabled/disabled from Settings.
 *
 * Currently supported extensions:
 * - Summarizer (always loaded, managed separately in MainActivity)
 * - SponsorBlock for YouTube (optional, user-enabled)
 *
 * NOTE: Full ad blocking (uBlock Origin style) is NOT implemented here.
 * SponsorBlock only skips sponsor segments on YouTube videos.
 */
class ExtensionManager(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val settings: SettingsRepository
) {

    private var sponsorBlockExtension: WebExtension? = null

    /**
     * Installs all enabled embedded extensions.
     * Call after GeckoRuntime is created.
     */
    fun installEnabledExtensions() {
        if (settings.isSponsorBlockEnabled()) {
            installSponsorBlock()
        }
    }

    /**
     * Installs SponsorBlock extension if its files exist in assets.
     * Gracefully skips if files are missing.
     */
    private fun installSponsorBlock() {
        if (!extensionFilesExist(SPONSORBLOCK_ASSET_PATH)) {
            Log.w(TAG, "SponsorBlock extension files are missing; skipping install")
            return
        }

        runtime.webExtensionController
            .ensureBuiltIn(SPONSORBLOCK_LOCATION, SPONSORBLOCK_ID)
            .accept({ extension ->
                if (extension != null) {
                    sponsorBlockExtension = extension
                    Log.d(TAG, "SponsorBlock extension installed successfully")
                } else {
                    Log.w(TAG, "SponsorBlock extension returned null from ensureBuiltIn")
                }
            }, { throwable ->
                Log.e(TAG, "Failed to install SponsorBlock extension", throwable)
            })
    }

    /**
     * Checks if the extension's manifest.json exists in assets.
     */
    private fun extensionFilesExist(assetPath: String): Boolean {
        return try {
            val files = context.assets.list(assetPath)
            files != null && files.contains("manifest.json")
        } catch (e: Exception) {
            Log.w(TAG, "Could not check extension files at $assetPath", e)
            false
        }
    }

    companion object {
        private const val TAG = "ExtensionManager"

        // SponsorBlock
        private const val SPONSORBLOCK_LOCATION = "resource://android/assets/extensions/sponsorblock/"
        private const val SPONSORBLOCK_ID = "nicedoc@nicedoc.io" // placeholder until real extension is added
        private const val SPONSORBLOCK_ASSET_PATH = "extensions/sponsorblock"
    }
}
