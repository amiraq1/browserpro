package com.amiraq.nabd.tabs

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.UUID

/**
 * Represents a single browser tab with its own GeckoSession and state.
 */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var url: String = "",
    val session: GeckoSession = GeckoSession(),
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var isHomePage: Boolean = false,
    val isPrivate: Boolean = false,
    var isDesktopMode: Boolean = false
)
