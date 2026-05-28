package com.amiraq.nabd.tabs

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * Manages multiple browser tabs, each backed by its own [GeckoSession].
 *
 * Handles creation, switching, closing, and session lifecycle. Only one tab
 * is "active" at a time and attached to the [GeckoView].
 */
class TabManager(
    private val runtime: GeckoRuntime,
    private val geckoView: GeckoView,
    private val onActiveTabChanged: (BrowserTab) -> Unit
) {

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId: String? = null

    fun getTabs(): List<BrowserTab> = tabs.toList()

    fun getActiveTab(): BrowserTab? = tabs.find { it.id == activeTabId }

    fun getTabCount(): Int = tabs.size

    /**
     * Creates a new tab, opens its session, and switches to it.
     */
    fun createTab(initialUrl: String, isPrivate: Boolean = false): BrowserTab {
        val session = if (isPrivate) {
            GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(true).build())
        } else {
            GeckoSession()
        }
        val tab = BrowserTab(url = initialUrl, session = session, isPrivate = isPrivate)
        tab.session.open(runtime)
        tabs.add(tab)
        switchToTab(tab.id)
        return tab
    }

    fun getPrivateTabs(): List<BrowserTab> = tabs.filter { it.isPrivate }
    fun getNormalTabs(): List<BrowserTab> = tabs.filter { !it.isPrivate }

    /**
     * Switches the active tab. Detaches the current session from GeckoView
     * and attaches the new one.
     */
    fun switchToTab(tabId: String): BrowserTab? {
        val tab = tabs.find { it.id == tabId } ?: return null

        if (activeTabId == tabId) {
            // Already active, just ensure it's attached
            ensureSessionAttached(tab)
            onActiveTabChanged(tab)
            return tab
        }

        // Detach current session
        try {
            geckoView.releaseSession()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing current session", e)
        }

        activeTabId = tabId

        // Attach new session
        ensureSessionAttached(tab)
        onActiveTabChanged(tab)
        return tab
    }

    /**
     * Closes a tab and its session. If the active tab is closed, switches to
     * a nearby tab. If no tabs remain, creates a new one with the given URL.
     */
    fun closeTab(tabId: String, fallbackUrl: String): BrowserTab? {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return getActiveTab()

        val closingTab = tabs.removeAt(index)
        val wasActive = closingTab.id == activeTabId

        // Close the GeckoSession to free resources
        try {
            if (wasActive) {
                geckoView.releaseSession()
            }
            closingTab.session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tab session", e)
        }

        if (tabs.isEmpty()) {
            // Must always have at least one tab
            return createTab(fallbackUrl)
        }

        if (wasActive) {
            // Switch to nearest tab
            val newIndex = index.coerceAtMost(tabs.size - 1)
            return switchToTab(tabs[newIndex].id)
        }

        return getActiveTab()
    }

    /**
     * Updates the URL of the active tab.
     */
    fun updateActiveTabUrl(url: String) {
        getActiveTab()?.url = url
    }

    /**
     * Updates the title of the active tab.
     */
    fun updateActiveTabTitle(title: String) {
        getActiveTab()?.title = title
    }

    /**
     * Updates loading state and progress of the active tab.
     */
    fun updateActiveTabLoading(isLoading: Boolean, progress: Int) {
        getActiveTab()?.let {
            it.isLoading = isLoading
            it.progress = progress
        }
    }

    /**
     * Updates navigation state of the active tab.
     */
    fun updateActiveTabNavigation(canGoBack: Boolean, canGoForward: Boolean) {
        getActiveTab()?.let {
            it.canGoBack = canGoBack
            it.canGoForward = canGoForward
        }
    }

    /**
     * Returns the GeckoSession for the active tab, or null if none.
     */
    fun getActiveSession(): GeckoSession? = getActiveTab()?.session

    /**
     * Closes all sessions. Call during Activity destruction.
     */
    fun destroy() {
        for (tab in tabs) {
            try {
                tab.session.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing session on destroy", e)
            }
        }
        tabs.clear()
        activeTabId = null
    }

    private fun ensureSessionAttached(tab: BrowserTab) {
        try {
            geckoView.setSession(tab.session)
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching session to GeckoView", e)
        }
    }

    /**
     * Toggles desktop mode for the given tab by setting/clearing the User-Agent override.
     */
    fun toggleDesktopMode(tabId: String): BrowserTab? {
        val tab = tabs.find { it.id == tabId } ?: return null
        tab.isDesktopMode = !tab.isDesktopMode
        applyUserAgent(tab)
        return tab
    }

    private fun applyUserAgent(tab: BrowserTab) {
        try {
            tab.session.settings.userAgentOverride = if (tab.isDesktopMode) {
                DESKTOP_USER_AGENT
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set User-Agent override", e)
        }
    }

    companion object {
        private const val TAG = "TabManager"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0"
    }
}
