package com.amiraq.nabd.extensions

import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Manages embedded WebExtensions for Nabd Browser.
 * Handles installation, initialization, and enabling/disabling based on settings.
 */
class ExtensionManager(
    private val geckoRuntime: GeckoRuntime,
    private val settingsRepository: SettingsRepository
) {
    private val installedExtensions = mutableMapOf<String, WebExtension>()

    companion object {
        private const val TAG = "ExtensionManager"
        const val BASE_PATH = "resource://android/assets/extensions/"
        
        const val SUMMARIZER_ID = "summarizer@example.com"
        const val SUMMARIZER_PATH = "${BASE_PATH}summarizer/"
        
        const val DARK_MODE_ID = "dark-mode-helper@nabd.example.com"
        const val DARK_MODE_PATH = "${BASE_PATH}dark-mode-helper/"
    }

    /**
     * Installs all enabled embedded extensions.
     */
    fun installExtensions(
        onSummarizerInstalled: (WebExtension) -> Unit = {}
    ) {
        // Install Summarizer if enabled
        if (settingsRepository.isSummarizerExtensionEnabled()) {
            installExtension(SUMMARIZER_ID, SUMMARIZER_PATH) { extension ->
                onSummarizerInstalled(extension)
            }
        } else {
            Log.d(TAG, "Summarizer extension is disabled in settings")
        }

        // Install Dark Mode Helper if enabled
        if (settingsRepository.isDarkModeExtensionEnabled()) {
            installExtension(DARK_MODE_ID, DARK_MODE_PATH)
        } else {
            Log.d(TAG, "Dark Mode Helper is disabled in settings")
        }
    }

    private fun installExtension(
        id: String,
        location: String,
        onSuccess: (WebExtension) -> Unit = {}
    ) {
        geckoRuntime.webExtensionController.ensureBuiltIn(location, id).accept({ extension ->
            if (extension != null) {
                installedExtensions[id] = extension
                Log.d(TAG, "Extension installed: $id from $location")
                onSuccess(extension)
            } else {
                Log.e(TAG, "Failed to install extension $id: ensureBuiltIn returned null")
            }
        }, { throwable ->
            Log.e(TAG, "Exception while installing extension $id", throwable)
        })
    }

    fun getExtension(id: String): WebExtension? = installedExtensions[id]
}
