package com.amiraq.nabd.extensions

import android.content.Context
import android.util.Log
import com.amiraq.nabd.agent.AgentBridgeMessageDelegate
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Manages embedded WebExtensions for Nabd Browser.
 */
class ExtensionManager(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val settings: SettingsRepository
) {
    private val installedExtensions = mutableMapOf<String, WebExtension>()

    /**
     * Installs all enabled embedded extensions.
     * Call after GeckoRuntime is created.
     */
    fun installEnabledExtensions(
        onSummarizerInstalled: (WebExtension) -> Unit = {}
    ) {
        if (settings.isSummarizerExtensionEnabled()) {
            installExtension(SUMMARIZER_ID, SUMMARIZER_LOCATION) { extension ->
                onSummarizerInstalled(extension)
            }
        } else {
            Log.d(TAG, "Summarizer extension is disabled in settings")
        }

        if (settings.isDarkModeExtensionEnabled()) {
            installExtension(DARK_MODE_ID, DARK_MODE_LOCATION)
        } else {
            Log.d(TAG, "Dark Mode Helper is disabled in settings")
        }

        if (settings.isSponsorBlockEnabled()) {
            installExtensionIfAssetsExist(
                SPONSORBLOCK_ID,
                SPONSORBLOCK_LOCATION,
                SPONSORBLOCK_ASSET_PATH
            )
        }

        installAdBlock()

        if (settings.isAgentBridgeEnabled()) {
            installAgentBridge()
        }
    }

    fun refreshAdBlockState() {
        val extension = installedExtensions[AD_BLOCK_ID] ?: return
        setAdBlockEnabled(extension, settings.isAdBlockEnabled())
    }

    fun getExtension(id: String): WebExtension? = installedExtensions[id]

    fun getAgentBridgeExtension(): WebExtension? = installedExtensions[AGENT_BRIDGE_ID]

    private fun installAdBlock() {
        if (!extensionFilesExist(AD_BLOCK_ASSET_PATH)) {
            Log.w(TAG, "Ad blocker extension files are missing; skipping install")
            return
        }

        installExtension(AD_BLOCK_ID, AD_BLOCK_LOCATION) { extension ->
            runtime.webExtensionController
                .setAllowedInPrivateBrowsing(extension, true)
                .accept({ updatedExtension ->
                    val enabledExtension = updatedExtension ?: extension
                    installedExtensions[AD_BLOCK_ID] = enabledExtension
                    setAdBlockEnabled(enabledExtension, settings.isAdBlockEnabled())
                }, { throwable ->
                    Log.e(TAG, "Failed to allow ad blocker in private browsing", throwable)
                    setAdBlockEnabled(extension, settings.isAdBlockEnabled())
                })
        }
    }

    private fun installAgentBridge() {
        if (!extensionFilesExist(AGENT_BRIDGE_ASSET_PATH)) {
            Log.w(TAG, "Agent Bridge extension files are missing; skipping install")
            return
        }

        installExtension(AGENT_BRIDGE_ID, AGENT_BRIDGE_LOCATION) { extension ->
            extension.setMessageDelegate(
                AgentBridgeMessageDelegate(settings),
                AgentBridgeMessageDelegate.NATIVE_APP_ID
            )
            Log.d(TAG, "Agent Bridge extension installed and delegate registered")
        }
    }

    private fun installExtensionIfAssetsExist(
        id: String,
        location: String,
        assetPath: String,
        onSuccess: (WebExtension) -> Unit = {}
    ) {
        if (!extensionFilesExist(assetPath)) {
            Log.w(TAG, "Extension files are missing for $id; skipping install")
            return
        }
        installExtension(id, location, onSuccess)
    }

    private fun installExtension(
        id: String,
        location: String,
        onSuccess: (WebExtension) -> Unit = {}
    ) {
        runtime.webExtensionController.ensureBuiltIn(location, id).accept({ extension ->
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

    private fun setAdBlockEnabled(extension: WebExtension, enabled: Boolean) {
        val controller = runtime.webExtensionController
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.APP)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.APP)
        }

        result.accept({ updatedExtension ->
            if (updatedExtension != null) {
                installedExtensions[AD_BLOCK_ID] = updatedExtension
            }
            Log.d(TAG, "Ad blocker extension enabled=$enabled")
        }, { throwable ->
            Log.e(TAG, "Failed to update ad blocker extension state", throwable)
        })
    }

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
        private const val EXTENSIONS_BASE_LOCATION = "resource://android/assets/extensions/"

        const val SUMMARIZER_ID = "summarizer@example.com"
        private const val SUMMARIZER_LOCATION = "${EXTENSIONS_BASE_LOCATION}summarizer/"

        const val DARK_MODE_ID = "dark-mode-helper@nabd.example.com"
        private const val DARK_MODE_LOCATION = "${EXTENSIONS_BASE_LOCATION}dark-mode-helper/"

        const val AD_BLOCK_ID = "adblock@nabd.local"
        private const val AD_BLOCK_LOCATION = "resource://android/assets/adblock-extension/"
        private const val AD_BLOCK_ASSET_PATH = "adblock-extension"

        private const val SPONSORBLOCK_ID = "nicedoc@nicedoc.io"
        private const val SPONSORBLOCK_LOCATION = "${EXTENSIONS_BASE_LOCATION}sponsorblock/"
        private const val SPONSORBLOCK_ASSET_PATH = "extensions/sponsorblock"

        const val AGENT_BRIDGE_ID = "agent-bridge@browserpro.local"
        private const val AGENT_BRIDGE_LOCATION = "resource://android/assets/agent-bridge/"
        private const val AGENT_BRIDGE_ASSET_PATH = "agent-bridge"
    }
}
