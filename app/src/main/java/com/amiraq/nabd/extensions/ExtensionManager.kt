package com.amiraq.nabd.extensions

 feature/phases-2-to-16
import android.content.Context

 main
import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 feature/phases-2-to-16
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
    private var agentBridgeExtension: WebExtension? = null

    /**
     * Installs all enabled embedded extensions.
     * Call after GeckoRuntime is created.
     */
    fun installEnabledExtensions() {
        if (settings.isSponsorBlockEnabled()) {
            installSponsorBlock()
        }
        if (settings.isAgentBridgeEnabled()) {
            installAgentBridge()
        }
    }

    /**
     * Returns the installed Agent Bridge extension for delegate registration.
     */
    fun getAgentBridgeExtension(): WebExtension? = agentBridgeExtension

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

    /**
     * Installs Agent Bridge extension if its files exist in assets.
     */
    private fun installAgentBridge() {
        if (!extensionFilesExist(AGENT_BRIDGE_ASSET_PATH)) {
            Log.w(TAG, "Agent Bridge extension files are missing; skipping install")
            return
        }

        runtime.webExtensionController
            .ensureBuiltIn(AGENT_BRIDGE_LOCATION, AGENT_BRIDGE_ID)
            .accept({ extension ->
                if (extension != null) {
                    agentBridgeExtension = extension
                    // Register message delegate for native messaging
                    extension.setMessageDelegate(
                        com.amiraq.nabd.agent.AgentBridgeMessageDelegate(settings),
                        com.amiraq.nabd.agent.AgentBridgeMessageDelegate.NATIVE_APP_ID
                    )
                    Log.d(TAG, "Agent Bridge extension installed and delegate registered")
                } else {
                    Log.w(TAG, "Agent Bridge extension returned null from ensureBuiltIn")
                }
            }, { throwable ->
                Log.e(TAG, "Failed to install Agent Bridge extension", throwable)
            })
    }

    companion object {
        private const val TAG = "ExtensionManager"

        // SponsorBlock
        private const val SPONSORBLOCK_LOCATION = "resource://android/assets/extensions/sponsorblock/"
        private const val SPONSORBLOCK_ID = "nicedoc@nicedoc.io"
        private const val SPONSORBLOCK_ASSET_PATH = "extensions/sponsorblock"

        // Agent Bridge
        const val AGENT_BRIDGE_LOCATION = "resource://android/assets/agent-bridge/"
        const val AGENT_BRIDGE_ID = "agent-bridge@browserpro.local"
        private const val AGENT_BRIDGE_ASSET_PATH = "agent-bridge"
    }

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
 main
}
