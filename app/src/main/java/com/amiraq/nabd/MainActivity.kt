package com.amiraq.nabd

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.amiraq.nabd.bookmarks.BookmarkRepository
import com.amiraq.nabd.downloads.DownloadItem
import com.amiraq.nabd.downloads.DownloadRepository
import com.amiraq.nabd.downloads.DownloadsActivity
import com.amiraq.nabd.history.HistoryRepository
import com.amiraq.nabd.settings.SettingsActivity
import com.amiraq.nabd.settings.SettingsRepository
import com.amiraq.nabd.summarizer.SummarizationResult
import com.amiraq.nabd.summarizer.Summarizer
import com.amiraq.nabd.summarizer.SummarizerFactory
import com.amiraq.nabd.tabs.BrowserTab
import com.amiraq.nabd.tabs.TabManager
import com.amiraq.nabd.theme.ThemeManager
import com.amiraq.nabd.privacy.PrivacyProtectionManager
import com.amiraq.nabd.privacy.ClearBrowsingDataManager
import com.amiraq.nabd.reader.ReaderArticleStore
import com.amiraq.nabd.reader.ReaderExtractor
import com.amiraq.nabd.reader.ReaderResult
import com.amiraq.nabd.search.SearchEngineManager
import com.amiraq.nabd.session.BrowserSessionState
import com.amiraq.nabd.session.SavedTabState
import com.amiraq.nabd.session.SessionRepository
import com.amiraq.nabd.share.ShareUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

class MainActivity : AppCompatActivity() {
    private lateinit var geckoRuntime: GeckoRuntime
    private lateinit var geckoView: GeckoView
    private lateinit var urlEditText: TextInputEditText
    private lateinit var goButton: MaterialButton
    private lateinit var summarizeButton: MaterialButton
    private lateinit var menuButton: MaterialButton
    private lateinit var newTabButton: MaterialButton
    private lateinit var tabCountButton: MaterialButton
    private lateinit var pageProgress: LinearProgressIndicator
    private lateinit var findBar: LinearLayout
    private lateinit var findEditText: TextInputEditText
    private lateinit var findCount: TextView
    private lateinit var findPrevButton: MaterialButton
    private lateinit var findNextButton: MaterialButton
    private lateinit var findCloseButton: MaterialButton
    private lateinit var browserChrome: LinearLayout
    private var isWebFullscreen = false
    private lateinit var tabManager: TabManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var homePageRepository: com.amiraq.nabd.home.HomePageRepository
    private lateinit var searchEngineManager: SearchEngineManager
    private lateinit var sessionRepository: SessionRepository
    private lateinit var summarizer: Summarizer
    private lateinit var preferences: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var summarizerExtension: WebExtension? = null
    private var summarizerAction: WebExtension.Action? = null
    private var popupDialog: Dialog? = null
    private var popupSession: GeckoSession? = null
    private lateinit var homePageContainer: androidx.core.widget.NestedScrollView
    private lateinit var homeSearchInput: TextInputEditText
    private lateinit var homeQuickLinksContainer: android.widget.GridLayout
    private lateinit var homeRecentContainer: LinearLayout
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var browserContentContainer: android.widget.FrameLayout
    private lateinit var gestureController: com.amiraq.nabd.gestures.BrowserGestureController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        settingsRepository = SettingsRepository(this)
        bookmarkRepository = BookmarkRepository(this)
        historyRepository = HistoryRepository(this)
        downloadRepository = DownloadRepository(this)
        homePageRepository = com.amiraq.nabd.home.HomePageRepository(this)
        searchEngineManager = SearchEngineManager(settingsRepository)
        sessionRepository = SessionRepository(this)
        summarizer = SummarizerFactory.create(settingsRepository)
        bindViews()
        applySafeAreaInsets()
        setupGeckoRuntime()
        setupTabManager()
        setupBrowserControls()
        setupBackNavigation()
        installSummarizerExtension()
        // Restore session or create first tab
        if (!restoreSavedSession()) {
            val startUrl = preferences.getString(PREF_LAST_URL, DEFAULT_HOME).orEmpty().ifBlank { DEFAULT_HOME }
            val firstTab = tabManager.createTab(startUrl)
            setupTabDelegates(firstTab)
            if (settingsRepository.isCustomHomepageEnabled() && startUrl == DEFAULT_HOME) {
                firstTab.isHomePage = true
                showHomePage()
            } else {
                firstTab.session.loadUri(startUrl)
            }
        }
        updateTabCountButton()
    }
    override fun onStart() { super.onStart(); tabManager.getActiveSession()?.setActive(true) }
    override fun onResume() { super.onResume(); if (::settingsRepository.isInitialized) { summarizer = SummarizerFactory.create(settingsRepository); searchEngineManager = SearchEngineManager(settingsRepository); if (::geckoRuntime.isInitialized) PrivacyProtectionManager.applyToRuntime(geckoRuntime.settings, settingsRepository); updateGestureSettings() }; if (!isWebFullscreen) setSystemBarsVisible(true) }
    override fun onStop() { tabManager.getActiveSession()?.setActive(false); saveCurrentSession(); super.onStop() }
    override fun onDestroy() {
        dismissExtensionPopup()
        // Close private tabs (they should never persist)
        for (tab in tabManager.getPrivateTabs()) {
            tabManager.closeTab(tab.id, DEFAULT_HOME)
        }
        // Clear on exit if enabled
        if (isFinishing && ::settingsRepository.isInitialized && settingsRepository.isClearOnExitEnabled()) {
            try {
                val manager = ClearBrowsingDataManager(this)
                manager.clear(
                    clearHistory = true,
                    clearCookies = true,
                    clearCache = true,
                    geckoRuntime = if (::geckoRuntime.isInitialized) geckoRuntime else null
                )
            } catch (e: Exception) { Log.e(TAG, "Clear on exit failed", e) }
        }
        scope.cancel()
        tabManager.destroy()
        super.onDestroy()
    }

    private fun bindViews() {
        geckoView = findViewById(R.id.geckoView)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)
        summarizeButton = findViewById(R.id.summarizeButton)
        menuButton = findViewById(R.id.menuButton)
        newTabButton = findViewById(R.id.newTabButton)
        tabCountButton = findViewById(R.id.tabCountButton)
        pageProgress = findViewById(R.id.pageProgress)
        findBar = findViewById(R.id.findBar)
        findEditText = findViewById(R.id.findEditText)
        findCount = findViewById(R.id.findCount)
        findPrevButton = findViewById(R.id.findPrevButton)
        findNextButton = findViewById(R.id.findNextButton)
        findCloseButton = findViewById(R.id.findCloseButton)
        browserChrome = findViewById(R.id.browserChrome)
        homePageContainer = findViewById(R.id.homePageContainer)
        homeSearchInput = findViewById(R.id.homeSearchInput)
        homeQuickLinksContainer = findViewById(R.id.homeQuickLinksContainer)
        homeRecentContainer = findViewById(R.id.homeRecentContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        browserContentContainer = findViewById(R.id.browserContentContainer)
    }
 feature/phases-2-to-16
    private fun setupGeckoRuntime() {
        try {
            val settings = org.mozilla.geckoview.GeckoRuntimeSettings.Builder()
                .consoleOutput(false)
                .contentBlocking(PrivacyProtectionManager.buildContentBlockingSettings(settingsRepository))
                .build()
            geckoRuntime = GeckoRuntime.create(this, settings)
            PrivacyProtectionManager.applyToRuntime(geckoRuntime.settings, settingsRepository)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create GeckoRuntime", e)
        }
    }


    private fun applySafeAreaInsets() {
        val start = browserChrome.paddingStart
        val top = browserChrome.paddingTop
        val end = browserChrome.paddingEnd
        val bottom = browserChrome.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(browserChrome) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPaddingRelative(start, top + bars.top, end, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(browserChrome)
    }

    private fun setupGeckoRuntime() { try { geckoRuntime = GeckoRuntime.create(this); PrivacyProtectionManager.applyToRuntime(geckoRuntime.settings, settingsRepository) } catch (e: Exception) { Log.e(TAG, "Failed to create GeckoRuntime", e) } }
  main
    private fun setupTabManager() { tabManager = TabManager(geckoRuntime, geckoView) { tab -> if (isWebFullscreen) exitWebFullscreen(); hideFindBarSilently(); if (tab.isHomePage) showHomePage() else hideHomePage(); updateUrlField(tab.url); updateProgressForTab(tab); updateTabCountButton(); reRegisterExtensionDelegateForActiveTab() } }
    private fun setupTabDelegates(tab: BrowserTab) { tab.session.setProgressDelegate(createProgressDelegate(tab)); tab.session.setNavigationDelegate(createNavigationDelegate(tab)); tab.session.setContentDelegate(createContentDelegate()) }
    private fun setupBrowserControls() {
        goButton.setOnClickListener { loadFromInput() }
        summarizeButton.setOnClickListener { summarizerAction?.click() }
        newTabButton.setOnClickListener { openNewTab() }
        tabCountButton.setOnClickListener { showTabSwitcher() }
        menuButton.setOnClickListener { showMenuDialog() }
        urlEditText.setOnEditorActionListener { _, actionId, event ->
            val isEnterUp = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            val isGoAction = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
            if (isEnterUp || isGoAction) { loadFromInput(); true } else false
        }
        // Find bar controls
        findCloseButton.setOnClickListener { hideFindBar() }
        findPrevButton.setOnClickListener { findPrevious() }
        findNextButton.setOnClickListener { findNext() }
        findEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performFind(findEditText.text?.toString().orEmpty()); true } else false
        }
        findEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { performFind(s?.toString().orEmpty()) }
        })
        setupHomeSearchInput()
        setupGestures()
    }
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isWebFullscreen) { exitWebFullscreen(); return }
                val activeTab = tabManager.getActiveTab()
                if (activeTab != null && activeTab.canGoBack) activeTab.session.goBack()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    private fun showMenuDialog() {
        val activeUrl = tabManager.getActiveTab()?.url.orEmpty()
        val isBookmarked = bookmarkRepository.isBookmarked(activeUrl)
        val bookmarkLabel = if (isBookmarked) getString(R.string.menu_remove_bookmark) else getString(R.string.menu_add_bookmark)
        val immersiveLabel = if (browserChrome.visibility == View.VISIBLE) getString(R.string.menu_hide_toolbar) else getString(R.string.menu_show_toolbar)
        val desktopLabel = if (tabManager.getActiveTab()?.isDesktopMode == true) getString(R.string.menu_desktop_on) else getString(R.string.menu_desktop_off)
        val items = arrayOf(bookmarkLabel, getString(R.string.menu_bookmarks), getString(R.string.menu_history), getString(R.string.menu_downloads), getString(R.string.find_in_page), getString(R.string.reader_mode), getString(R.string.menu_share_page), getString(R.string.menu_copy_link), getString(R.string.menu_new_private_tab), desktopLabel, immersiveLabel, getString(R.string.privacy_report_title), getString(R.string.clear_browsing_data), getString(R.string.settings_title), getString(R.string.about_title))
        MaterialAlertDialogBuilder(this).setItems(items) { _, which -> when (which) { 0 -> toggleBookmark(); 1 -> showBookmarksDialog(); 2 -> showHistoryDialog(); 3 -> startActivity(Intent(this, DownloadsActivity::class.java)); 4 -> showFindBar(); 5 -> openReaderMode(); 6 -> sharePage(); 7 -> copyPageLink(); 8 -> openNewPrivateTab(); 9 -> toggleDesktopMode(); 10 -> toggleToolbarVisibility(); 11 -> showPrivacyReport(); 12 -> startActivity(Intent(this, com.amiraq.nabd.privacy.ClearBrowsingDataActivity::class.java)); 13 -> startActivity(Intent(this, SettingsActivity::class.java)); 14 -> startActivity(Intent(this, com.amiraq.nabd.about.AboutActivity::class.java)) } }.show()
    }
    private fun toggleBookmark() {
        val tab = tabManager.getActiveTab() ?: return
        val url = tab.url; if (url.isBlank()) return
        if (bookmarkRepository.isBookmarked(url)) bookmarkRepository.removeBookmarkByUrl(url) else bookmarkRepository.addBookmark(tab.title.ifBlank { url }, url)
    }
    private fun openNewTab() {
        if (settingsRepository.isCustomHomepageEnabled()) {
            val tab = tabManager.createTab("")
            tab.isHomePage = true
            setupTabDelegates(tab)
            updateTabCountButton()
            showHomePage()
        } else {
            val tab = tabManager.createTab(DEFAULT_HOME)
            setupTabDelegates(tab)
            tab.session.loadUri(DEFAULT_HOME)
            updateTabCountButton()
        }
    }

    private fun openNewPrivateTab() {
        val tab = tabManager.createTab("", isPrivate = true)
        tab.isHomePage = true
        setupTabDelegates(tab)
        updateTabCountButton()
        showHomePage()
    }

    private fun toggleDesktopMode() {
        val tab = tabManager.getActiveTab() ?: return
        tabManager.toggleDesktopMode(tab.id)
        val label = if (tab.isDesktopMode) getString(R.string.menu_desktop_on) else getString(R.string.menu_desktop_off)
        com.google.android.material.snackbar.Snackbar.make(geckoView, label, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        // Reload current page if it's a real URL
        if (tab.url.startsWith("http://") || tab.url.startsWith("https://")) {
            tab.session.reload()
        }
    }
    private fun toggleToolbarVisibility() {
        if (browserChrome.visibility == View.VISIBLE) {
            browserChrome.visibility = View.GONE
        } else {
            browserChrome.visibility = View.VISIBLE
        }
    }

    private fun sharePage() {
        val tab = tabManager.getActiveTab() ?: return
        val url = tab.url
        if (!ShareUtils.isShareableUrl(url)) {
            com.google.android.material.snackbar.Snackbar.make(geckoView, R.string.share_not_available, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            return
        }
        val title = tab.title.ifBlank { url }
        ShareUtils.shareText(this, title, "$title\n$url")
    }

    private fun copyPageLink() {
        val tab = tabManager.getActiveTab() ?: return
        val url = tab.url
        if (!ShareUtils.isShareableUrl(url)) {
            com.google.android.material.snackbar.Snackbar.make(geckoView, R.string.share_not_available, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            return
        }
        ShareUtils.copyToClipboard(this, "URL", url)
        com.google.android.material.snackbar.Snackbar.make(geckoView, R.string.link_copied, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    // ─── Homepage ────────────────────────────────────────────────────────────────

    private fun showHomePage() {
        homePageContainer.visibility = View.VISIBLE
        geckoView.visibility = View.GONE
        urlEditText.setText("")
        homeSearchInput.setText("")
        renderHomePage()
    }

    private fun hideHomePage() {
        homePageContainer.visibility = View.GONE
        geckoView.visibility = View.VISIBLE
    }

    private fun refreshHomePage() = renderHomePage()

    private fun renderHomePage() {
        renderFavoriteSites()
        val activeTab = tabManager.getActiveTab()
        if (activeTab?.isPrivate == true) {
            // Hide recent sites in private mode
            homeRecentContainer.removeAllViews()
            val note = TextView(this).apply {
                text = getString(R.string.private_browsing_note)
                textSize = 13f
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            homeRecentContainer.addView(note)
        } else {
            renderRecentSites()
        }
    }

    private fun setupHomeSearchInput() {
        homeSearchInput.setOnEditorActionListener { _, actionId, event ->
            val isEnterUp = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            val isGoAction = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
            if (isEnterUp || isGoAction) { handleHomeSearch(homeSearchInput.text?.toString().orEmpty()); true } else false
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        // Edge swipe gestures
        gestureController = com.amiraq.nabd.gestures.BrowserGestureController(
            this,
            onSwipeBack = { navigateBackGesture() },
            onSwipeForward = { navigateForwardGesture() }
        )
        gestureController.isEnabled = settingsRepository.isSwipeNavigationEnabled()
        browserContentContainer.setOnTouchListener(gestureController)

        // Pull to refresh
        swipeRefreshLayout.setOnRefreshListener { refreshFromGesture() }
        swipeRefreshLayout.isEnabled = settingsRepository.isPullToRefreshEnabled()
        swipeRefreshLayout.setColorSchemeColors(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
    }

    private fun navigateBackGesture() {
        if (isWebFullscreen || findBar.visibility == View.VISIBLE) return
        val tab = tabManager.getActiveTab() ?: return
        if (tab.isHomePage) return
        if (tab.canGoBack) tab.session.goBack()
    }

    private fun navigateForwardGesture() {
        if (isWebFullscreen || findBar.visibility == View.VISIBLE) return
        val tab = tabManager.getActiveTab() ?: return
        if (tab.isHomePage) return
        if (tab.canGoForward) tab.session.goForward()
    }

    private fun refreshFromGesture() {
        val tab = tabManager.getActiveTab()
        if (tab == null) { swipeRefreshLayout.isRefreshing = false; return }
        if (tab.isHomePage) {
            refreshHomePage()
            swipeRefreshLayout.isRefreshing = false
        } else {
            tab.session.reload()
            // Animation stops when onPageStop fires
        }
    }

    private fun updateGestureSettings() {
        if (::gestureController.isInitialized) {
            gestureController.isEnabled = settingsRepository.isSwipeNavigationEnabled()
        }
        if (::swipeRefreshLayout.isInitialized) {
            swipeRefreshLayout.isEnabled = settingsRepository.isPullToRefreshEnabled()
        }
    }

    // ─── Session Save/Restore ────────────────────────────────────────────────────

    private fun saveCurrentSession() {
        if (!::sessionRepository.isInitialized) return
        try {
            val normalTabs = tabManager.getNormalTabs()
            val savedTabs = normalTabs.mapNotNull { tab ->
                val url = tab.url
                // Only save tabs with valid URLs or homepage tabs
                if (tab.isHomePage || isRestorableUrl(url)) {
                    SavedTabState(
                        id = tab.id,
                        title = tab.title,
                        url = if (tab.isHomePage) "" else url,
                        isHomePage = tab.isHomePage,
                        isDesktopMode = tab.isDesktopMode
                    )
                } else null
            }
            if (savedTabs.isEmpty()) { sessionRepository.clearSession(); return }
            // Find active normal tab
            val activeTab = tabManager.getActiveTab()
            val activeId = if (activeTab != null && !activeTab.isPrivate) activeTab.id else savedTabs.firstOrNull()?.id
            sessionRepository.saveSession(BrowserSessionState(tabs = savedTabs, activeTabId = activeId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    private fun restoreSavedSession(): Boolean {
        if (!settingsRepository.isSessionRestoreEnabled()) return false
        val state = sessionRepository.loadSession() ?: return false
        if (state.tabs.isEmpty()) return false

        var activeTabId: String? = null
        for (saved in state.tabs) {
            val tab = tabManager.createTab(saved.url, isPrivate = false)
            setupTabDelegates(tab)
            if (saved.isDesktopMode) {
                tab.isDesktopMode = true
                tabManager.toggleDesktopMode(tab.id) // applies UA
                tab.isDesktopMode = true // ensure state after toggle
            }
            if (saved.isHomePage) {
                tab.isHomePage = true
            } else if (isRestorableUrl(saved.url)) {
                tab.session.loadUri(saved.url)
            } else {
                tab.isHomePage = true
            }
            tab.title = saved.title
            if (saved.id == state.activeTabId) activeTabId = tab.id
        }

        // Switch to active tab
        val targetId = activeTabId ?: tabManager.getTabs().firstOrNull()?.id
        if (targetId != null) {
            tabManager.switchToTab(targetId)
            val active = tabManager.getActiveTab()
            if (active?.isHomePage == true) showHomePage()
        }
        return true
    }

    private fun isRestorableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun loadFromHomeInput(rawInput: String) = handleHomeSearch(rawInput)

    private fun handleHomeSearch(input: String) {
        val url = normalizeInput(input)
        if (url.isBlank()) return
        hideKeyboardFrom(homeSearchInput)
        openHomeUrl(url)
    }

    private fun openHomeUrl(url: String) {
        if (url.isBlank() || isInternalHomeUrl(url)) return
        tabManager.getActiveTab()?.isHomePage = false
        hideHomePage()
        loadUrl(url)
    }

    private fun renderFavoriteSites() {
        homeQuickLinksContainer.removeAllViews()
        val links = homePageRepository.getQuickLinks()
        val columnCount = 2
        homeQuickLinksContainer.columnCount = columnCount

        for (link in links) {
            val card = createFavoriteSiteCard(link.title, link.url, isAddCard = false) { openHomeUrl(link.url) }
            card.setOnLongClickListener {
                showQuickLinkOptionsDialog(link)
                true
            }
            val params = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            homeQuickLinksContainer.addView(card, params)
        }

        val addCard = createFavoriteSiteCard(getString(R.string.home_add_site), "", isAddCard = true) {
            showAddFavoriteDialog()
        }
        val addParams = android.widget.GridLayout.LayoutParams().apply {
            width = 0
            height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        homeQuickLinksContainer.addView(addCard, addParams)
    }

    private fun createFavoriteSiteCard(title: String, url: String, isAddCard: Boolean, onClick: () -> Unit): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)
            strokeColor = getThemeColor(com.google.android.material.R.attr.colorOutline)
            setCardBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
            setContentPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
            contentDescription = if (isAddCard) getString(R.string.home_add_quick_link) else "$title, ${formatHost(url)}"
            isClickable = true
            isFocusable = true
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val initial = TextView(this).apply {
            text = if (isAddCard) "+" else title.take(1).uppercase()
            textSize = if (isAddCard) 22f else 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimary))
            val size = dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            }
        }
        row.addView(initial)
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(com.google.android.material.textview.MaterialTextView(this).apply {
            text = title
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
        })
        textColumn.addView(com.google.android.material.textview.MaterialTextView(this).apply {
            text = if (isAddCard) getString(R.string.home_favorite_url_hint) else formatHost(url)
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        row.addView(textColumn)
        card.addView(row)
        return card
    }

    private fun showQuickLinkOptionsDialog(link: com.amiraq.nabd.home.HomeQuickLink) {
        val options = arrayOf(getString(R.string.home_open), getString(R.string.home_edit), getString(R.string.home_remove))
        MaterialAlertDialogBuilder(this).setTitle(link.title).setItems(options) { _, which ->
            when (which) {
                0 -> openHomeUrl(link.url)
                1 -> showEditFavoriteDialog(link)
                2 -> confirmRemoveFavorite(link)
            }
        }.show()
    }

    private fun showAddFavoriteDialog() {
        showFavoriteEditorDialog(
            titleRes = R.string.home_add_dialog_title,
            initialName = "",
            initialUrl = "",
            existingId = null
        )
    }

    private fun showEditFavoriteDialog(link: com.amiraq.nabd.home.HomeQuickLink) {
        showFavoriteEditorDialog(
            titleRes = R.string.home_edit_favorite,
            initialName = link.title,
            initialUrl = link.url,
            existingId = link.id
        )
    }

    private fun showFavoriteEditorDialog(titleRes: Int, initialName: String, initialUrl: String, existingId: String?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
        }
        val nameField = createFavoriteDialogField(getString(R.string.home_favorite_name_hint), initialName, android.text.InputType.TYPE_CLASS_TEXT)
        val urlField = createFavoriteDialogField(getString(R.string.home_favorite_url_hint), initialUrl, android.text.InputType.TYPE_TEXT_VARIATION_URI)
        layout.addView(nameField.first)
        layout.addView(urlField.first)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(layout)
            .setPositiveButton(R.string.setting_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                nameField.first.error = null
                urlField.first.error = null
                val name = nameField.second.text?.toString().orEmpty().trim()
                val normalizedUrl = normalizeFavoriteUrl(urlField.second.text?.toString().orEmpty())
                var valid = true
                if (name.isBlank()) {
                    nameField.first.error = getString(R.string.home_error_name_required)
                    valid = false
                }
                if (normalizedUrl == null) {
                    urlField.first.error = getString(R.string.home_error_url_invalid)
                    valid = false
                } else if (isDuplicateFavoriteUrl(normalizedUrl, existingId)) {
                    urlField.first.error = getString(R.string.home_error_duplicate_url)
                    valid = false
                }
                if (!valid || normalizedUrl == null) return@setOnClickListener

                if (existingId == null) {
                    homePageRepository.addQuickLink(name, normalizedUrl)
                } else {
                    homePageRepository.updateQuickLink(existingId, name, normalizedUrl)
                }
                renderFavoriteSites()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun createFavoriteDialogField(hint: String, value: String, inputType: Int): Pair<TextInputLayout, TextInputEditText> {
        val input = TextInputEditText(this).apply {
            setText(value)
            this.inputType = inputType
            maxLines = 1
            isSingleLine = true
        }
        val layout = TextInputLayout(this).apply {
            this.hint = hint
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            isErrorEnabled = true
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            addView(input)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, 0) }
        }
        return layout to input
    }

    private fun confirmRemoveFavorite(link: com.amiraq.nabd.home.HomeQuickLink) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_remove_favorite_title)
            .setMessage(getString(R.string.home_remove_favorite_message, link.title))
            .setPositiveButton(R.string.home_remove) { _, _ ->
                homePageRepository.removeQuickLink(link.id)
                renderFavoriteSites()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderRecentSites() {
        homeRecentContainer.removeAllViews()
        val recent = historyRepository.getHistory().take(5)
        if (recent.isEmpty()) {
            val empty = com.google.android.material.textview.MaterialTextView(this).apply {
                text = getString(R.string.home_no_recent)
                textSize = 14f
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            homeRecentContainer.addView(empty)
            return
        }
        for (item in recent) {
            if (!isWebUrl(item.url)) continue
            val card = MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                cardElevation = dp(0).toFloat()
                strokeWidth = dp(1)
                strokeColor = getThemeColor(com.google.android.material.R.attr.colorOutline)
                setCardBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
                contentDescription = "${item.title.ifBlank { formatHost(item.url) }}, ${formatHost(item.url)}"
                setContentPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener { openHomeUrl(item.url) }
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(8)) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val domain = formatHost(item.url)
            val circle = TextView(this).apply {
                text = domain.take(1).uppercase()
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTextColor(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
                val size = dp(36)
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(getThemeColor(com.google.android.material.R.attr.colorSurfaceVariant))
                }
                setPadding(0, dp(7), 0, 0)
            }
            row.addView(circle)
            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textContainer.addView(com.google.android.material.textview.MaterialTextView(this).apply {
                text = item.title.ifBlank { domain }
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
            })
            textContainer.addView(com.google.android.material.textview.MaterialTextView(this).apply {
                text = domain
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            row.addView(textContainer)
            card.addView(row)
            homeRecentContainer.addView(card)
        }
    }

    private fun openReaderMode() {
        val tab = tabManager.getActiveTab() ?: return
        val url = tab.url
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("resource:") || url.startsWith("data:") || url.startsWith("file:")) {
            com.google.android.material.snackbar.Snackbar.make(geckoView, R.string.reader_not_available, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            return
        }
        val session = tabManager.getActiveSession() ?: return
        com.google.android.material.snackbar.Snackbar.make(geckoView, R.string.reader_extracting, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        ReaderExtractor.requestExtraction(session, url) { result ->
            runOnUiThread {
                when (result) {
                    is ReaderResult.Success -> {
                        ReaderArticleStore.currentArticle = result.article
                        startActivity(Intent(this, com.amiraq.nabd.reader.ReaderActivity::class.java))
                    }
                    is ReaderResult.Error -> {
                        com.google.android.material.snackbar.Snackbar.make(geckoView, result.message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    private fun getErrorColor(): Int { val tv = android.util.TypedValue(); theme.resolveAttribute(android.R.attr.colorError, tv, true); return if (tv.data != 0) tv.data else android.graphics.Color.parseColor("#BA1A1A") }
    private fun getThemeColor(attr: Int): Int { val tv = android.util.TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }
    private fun normalizeFavoriteUrl(rawUrl: String): String? {
        val input = rawUrl.trim()
        if (input.isBlank() || isInternalHomeUrl(input)) return null
        val candidate = if (input.startsWith("http://", true) || input.startsWith("https://", true)) {
            input
        } else if (looksLikeDomain(input)) {
            "https://$input"
        } else {
            return null
        }
        return if (isWebUrl(candidate)) candidate else null
    }

    private fun isDuplicateFavoriteUrl(url: String, existingId: String?): Boolean {
        val key = favoriteUrlKey(url)
        return homePageRepository.getQuickLinks().any { link ->
            link.id != existingId && favoriteUrlKey(link.url) == key
        }
    }

    private fun favoriteUrlKey(url: String): String = url.trim().trimEnd('/').lowercase(Locale.US)

    private fun formatHost(url: String): String = try {
        android.net.Uri.parse(url).host.orEmpty().removePrefix("www.").ifBlank { url }
    } catch (e: Exception) {
        url
    }

    private fun isWebUrl(url: String): Boolean {
        val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { return false }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    private fun isInternalHomeUrl(url: String): Boolean = url.startsWith("nabd://", ignoreCase = true)

    private fun updateTabCountButton() {
        val count = tabManager.getTabCount().toString()
        val isPrivate = tabManager.getActiveTab()?.isPrivate == true
        tabCountButton.text = if (isPrivate) "P$count" else count
    }
    private fun updateProgressForTab(tab: BrowserTab) { if (tab.isLoading) { pageProgress.progress = tab.progress.coerceIn(0, 100); pageProgress.visibility = View.VISIBLE } else pageProgress.visibility = View.GONE }
    private fun recordHistory(title: String, url: String) {
        // Do not record history for private tabs
        val activeTab = tabManager.getActiveTab()
        if (activeTab?.isPrivate == true) return
        if (activeTab?.isHomePage == true || !isWebUrl(url)) return
        historyRepository.addVisit(title, url)
    }

    private fun showPrivacyReport() {
        val url = tabManager.getActiveTab()?.url.orEmpty().ifBlank { "—" }
        val adBlock = if (settingsRepository.isAdBlockEnabled()) getString(R.string.privacy_status_on) else getString(R.string.privacy_status_off)
        val tracker = if (settingsRepository.isTrackerProtectionEnabled()) getString(R.string.privacy_status_on) else getString(R.string.privacy_status_off)
        val crypto = if (settingsRepository.isCryptominerBlockingEnabled()) getString(R.string.privacy_status_on) else getString(R.string.privacy_status_off)
        val finger = if (settingsRepository.isFingerprinterBlockingEnabled()) getString(R.string.privacy_status_on) else getString(R.string.privacy_status_off)
        val message = "Site: $url\n\nAd blocking: $adBlock\nTracker protection: $tracker\nCryptominer blocking: $crypto\nFingerprinter blocking: $finger\n\nProtection is applied at the engine level via GeckoView Enhanced Tracking Protection."
        MaterialAlertDialogBuilder(this).setTitle(R.string.privacy_report_title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    }

    // ─── Find in Page ────────────────────────────────────────────────────────────

    private fun showFindBar() {
        findBar.visibility = View.VISIBLE
        findEditText.setText("")
        findCount.text = "0/0"
        findEditText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(findEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideFindBarSilently() {
        findBar.visibility = View.GONE
        findEditText.setText("")
        findCount.text = "0/0"
    }

    private fun hideFindBar() {
        findBar.visibility = View.GONE
        findEditText.setText("")
        findCount.text = "0/0"
        hideKeyboard()
        clearFind()
    }

    private fun performFind(query: String) {
        val session = tabManager.getActiveSession() ?: return
        if (query.isBlank()) {
            clearFind()
            findCount.text = "0/0"
            return
        }
        session.finder.find(query, 0).accept { result ->
            runOnUiThread {
                if (result != null) {
                    val current = result.current + 1
                    val total = result.total
                    findCount.text = if (total > 0) "$current/$total" else "0/0"
                } else {
                    findCount.text = "0/0"
                }
            }
        }
    }

    private fun findNext() {
        val session = tabManager.getActiveSession() ?: return
        val query = findEditText.text?.toString().orEmpty()
        if (query.isBlank()) return
        session.finder.find(query, 0).accept { result ->
            runOnUiThread {
                if (result != null) {
                    val current = result.current + 1
                    val total = result.total
                    findCount.text = if (total > 0) "$current/$total" else "0/0"
                }
            }
        }
    }

    private fun findPrevious() {
        val session = tabManager.getActiveSession() ?: return
        val query = findEditText.text?.toString().orEmpty()
        if (query.isBlank()) return
        session.finder.find(query, GeckoSession.FINDER_FIND_BACKWARDS).accept { result ->
            runOnUiThread {
                if (result != null) {
                    val current = result.current + 1
                    val total = result.total
                    findCount.text = if (total > 0) "$current/$total" else "0/0"
                }
            }
        }
    }

    private fun clearFind() {
        val session = tabManager.getActiveSession() ?: return
        session.finder.clear()
    }

    // ─── Fullscreen ──────────────────────────────────────────────────────────────

    private fun enterWebFullscreen() {
        isWebFullscreen = true
        hideFindBarSilently()
        browserChrome.visibility = View.GONE
        setSystemBarsVisible(false)
    }

    private fun exitWebFullscreen() {
        isWebFullscreen = false
        browserChrome.visibility = View.VISIBLE
        setSystemBarsVisible(true)
        tabManager.getActiveSession()?.exitFullScreen()
    }

    @Suppress("DEPRECATION")
    private fun setSystemBarsVisible(visible: Boolean) {
        if (visible) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun showBookmarksDialog() {
        val bookmarks = bookmarkRepository.getBookmarks()
        if (bookmarks.isEmpty()) { MaterialAlertDialogBuilder(this).setTitle(R.string.menu_bookmarks).setMessage(R.string.no_bookmarks).setPositiveButton(android.R.string.ok, null).show(); return }
        val dialog = Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val scrollView = ScrollView(this); val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val title = TextView(this).apply { text = getString(R.string.menu_bookmarks); textSize = 18f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface)); setPadding(0, 0, 0, dp(12)) }
        container.addView(title)
        for (bookmark in bookmarks) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(10), dp(4), dp(10)) }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            info.addView(TextView(this).apply { text = bookmark.title.take(50); textSize = 14f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface)) })
            info.addView(TextView(this).apply { text = bookmark.url.take(60); textSize = 11f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)) })
            row.addView(info)
            row.addView(TextView(this).apply { text = "✕"; textSize = 16f; setTextColor(getErrorColor()); setPadding(dp(12), dp(4), dp(4), dp(4)); setOnClickListener { bookmarkRepository.removeBookmark(bookmark.id); dialog.dismiss(); showBookmarksDialog() } })
            row.setOnClickListener { loadUrl(bookmark.url); dialog.dismiss() }
            container.addView(row)
        }
        scrollView.addView(container); dialog.setContentView(scrollView); dialog.show()
        dialog.window?.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(getThemeColor(com.google.android.material.R.attr.colorSurface)))
    }

    private fun showHistoryDialog() {
        val history = historyRepository.getHistory()
        if (history.isEmpty()) { MaterialAlertDialogBuilder(this).setTitle(R.string.menu_history).setMessage(R.string.no_history).setPositiveButton(android.R.string.ok, null).show(); return }
        val dialog = Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val scrollView = ScrollView(this); val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        header.addView(TextView(this).apply { text = getString(R.string.menu_history); textSize = 18f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface)); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        header.addView(MaterialButton(this).apply { text = getString(R.string.clear_history); textSize = 12f; setOnClickListener { historyRepository.clearHistory(); dialog.dismiss() } })
        container.addView(header)
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        for (item in history.take(50)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(10), dp(4), dp(10)) }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            info.addView(TextView(this).apply { text = item.title.take(50); textSize = 14f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface)) })
            info.addView(TextView(this).apply { text = "${dateFormat.format(Date(item.visitedAt))} — ${item.url.take(45)}"; textSize = 11f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)) })
            row.addView(info)
            row.addView(TextView(this).apply { text = "✕"; textSize = 16f; setTextColor(getErrorColor()); setPadding(dp(12), dp(4), dp(4), dp(4)); setOnClickListener { historyRepository.removeHistoryItem(item.id); dialog.dismiss(); showHistoryDialog() } })
            row.setOnClickListener { loadUrl(item.url); dialog.dismiss() }
            container.addView(row)
        }
        scrollView.addView(container); dialog.setContentView(scrollView); dialog.show()
        dialog.window?.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(getThemeColor(com.google.android.material.R.attr.colorSurface)))
    }

    private fun showTabSwitcher() {
        val tabs = tabManager.getTabs(); val activeTab = tabManager.getActiveTab()
        val dialog = Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val scrollView = ScrollView(this); val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        container.addView(TextView(this).apply { text = getString(R.string.tabs_title, tabs.size); textSize = 18f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface)); setPadding(0, 0, 0, dp(12)) })
        for (tab in tabs) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(12), dp(8), dp(12)); if (tab.id == activeTab?.id) setBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurfaceVariant)) }
            val displayText = tab.title.ifBlank { tab.url.ifBlank { if (tab.isPrivate) "Private Tab" else "New Tab" } }
            val prefix = if (tab.isPrivate) "🕶 " else ""
            row.addView(TextView(this).apply { text = prefix + (if (displayText.length > 38) displayText.take(38) + "…" else displayText); textSize = 14f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface)); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply { text = "✕"; textSize = 18f; setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)); setPadding(dp(12), dp(4), dp(4), dp(4)); setOnClickListener { tabManager.closeTab(tab.id, DEFAULT_HOME)?.let { n -> if (tabManager.getTabs().any { it.id == n.id && it.url == DEFAULT_HOME && it.title.isBlank() }) { setupTabDelegates(n); n.session.loadUri(DEFAULT_HOME) } }; updateTabCountButton(); dialog.dismiss() } })
            row.setOnClickListener { tabManager.switchToTab(tab.id); updateTabCountButton(); dialog.dismiss() }
            container.addView(row)
        }
        container.addView(MaterialButton(this).apply { text = getString(R.string.new_tab); setOnClickListener { openNewTab(); dialog.dismiss() } })
        scrollView.addView(container); dialog.setContentView(scrollView); dialog.show()
        dialog.window?.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(getThemeColor(com.google.android.material.R.attr.colorSurface)))
    }

    private fun installSummarizerExtension() {
        geckoRuntime.webExtensionController.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept({ extension ->
            if (extension == null) { Log.e(TAG, "Summarizer extension returned null"); return@accept }
            summarizerExtension = extension
            extension.setMessageDelegate(createNativeMessageDelegate(), NATIVE_APP_ID)
            val actionDelegate = createActionDelegate()
            extension.setActionDelegate(actionDelegate)
            tabManager.getActiveSession()?.webExtensionController?.setActionDelegate(extension, actionDelegate)
            Log.d(TAG, "Summarizer extension installed")
        }, { throwable -> Log.e(TAG, "Failed to install summarizer extension", throwable) })
    }
    private fun reRegisterExtensionDelegateForActiveTab() { val ext = summarizerExtension ?: return; val session = tabManager.getActiveSession() ?: return; try { session.webExtensionController.setActionDelegate(ext, createActionDelegate()) } catch (e: Exception) { Log.e(TAG, "Error re-registering extension delegate", e) } }
    private fun createNativeMessageDelegate() = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP_ID) return errorResult("Unsupported native app: $nativeApp")
            val json = try { message as? JSONObject } catch (e: Exception) { null }
            if (json == null) return errorResult("Expected JSON object")
            val type = json.optString("type", "")
            // Handle reader article extraction result
            if (type == "readerArticle") {
                runOnUiThread { ReaderExtractor.onArticleReceived(json) }
                return null
            }
            if (type != "summarize") return errorResult("Unsupported message type: $type")
            val text = json.optString("text", ""); val geckoResult = GeckoResult<Any>(); val currentSummarizer = summarizer
            scope.launch { try { val result = currentSummarizer.summarize(text); val response = when (result) { is SummarizationResult.Success -> JSONObject().put("ok", true).put("summary", result.summary); is SummarizationResult.Error -> JSONObject().put("ok", false).put("error", result.message) }; geckoResult.complete(response) } catch (e: Exception) { Log.e(TAG, "Summarization failed", e); geckoResult.complete(JSONObject().put("ok", false).put("error", "Failed: ${e.message}")) } }
            return geckoResult
        }
    }
    private fun errorResult(message: String): GeckoResult<Any> = GeckoResult.fromValue(JSONObject().put("ok", false).put("error", message))

    private fun createActionDelegate() = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) { summarizerAction = action; summarizeButton.isEnabled = true }
        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = GeckoResult.fromValue(showExtensionPopup())
        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = if (popupDialog?.isShowing == true) { dismissExtensionPopup(); null } else GeckoResult.fromValue(showExtensionPopup())
    }
    private fun showExtensionPopup(): GeckoSession {
        dismissExtensionPopup(); val dialog = Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val popupView = GeckoView(this); popupView.layoutParams = ViewGroup.LayoutParams(dp(340), dp(440))
        val newSession = GeckoSession(); newSession.open(geckoRuntime); popupView.setSession(newSession)
        dialog.setContentView(popupView); dialog.setOnDismissListener { if (popupSession === newSession) { popupSession = null; popupDialog = null }; newSession.close() }
        dialog.show(); dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); dialog.window?.setLayout(dp(340), dp(440))
        popupDialog = dialog; popupSession = newSession; return newSession
    }
    private fun dismissExtensionPopup() { popupDialog?.setOnDismissListener(null); popupDialog?.dismiss(); popupDialog = null; popupSession?.close(); popupSession = null }

    private fun createProgressDelegate(tab: BrowserTab) = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) { tab.isLoading = true; tab.progress = 0; if (tab.id == tabManager.getActiveTab()?.id) { pageProgress.progress = 0; pageProgress.visibility = View.VISIBLE } }
        override fun onProgressChange(session: GeckoSession, progress: Int) { tab.progress = progress.coerceIn(0, 100); if (tab.id == tabManager.getActiveTab()?.id) { pageProgress.progress = tab.progress; if (tab.progress < 100) pageProgress.visibility = View.VISIBLE } }
        override fun onPageStop(session: GeckoSession, success: Boolean) { tab.isLoading = false; tab.progress = 100; if (tab.id == tabManager.getActiveTab()?.id) { pageProgress.progress = 100; pageProgress.visibility = View.GONE; swipeRefreshLayout.isRefreshing = false }; if (success && tab.url.isNotBlank()) recordHistory(tab.title.ifBlank { tab.url }, tab.url); if (!success) Log.e(TAG, "Page failed in tab ${tab.id}") }
    }
    private fun createNavigationDelegate(tab: BrowserTab) = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
            if (url.isNullOrBlank()) return
            if (url == "about:blank") {
                if (tab.isHomePage && tab.id == tabManager.getActiveTab()?.id) showHomePage()
                return
            }
            tab.url = url
            tab.isHomePage = false
            if (tab.id == tabManager.getActiveTab()?.id) {
                hideHomePage()
                updateUrlField(url)
                saveLastUrl(url)
            }
        }
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canGoForward = canGoForward }
        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? { Log.e(TAG, "Load error for $uri: code=${error.code}"); return null }
    }
    private fun createContentDelegate() = object : GeckoSession.ContentDelegate {
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) { runOnUiThread { showDownloadConfirmation(response) } }
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { runOnUiThread { if (fullScreen) enterWebFullscreen() else exitWebFullscreen() } }
    }

    private fun showDownloadConfirmation(response: WebResponse) {
        val url = response.uri; val headers = response.headers
        val contentDisposition = headers["Content-Disposition"] ?: headers["content-disposition"]
        val contentType = headers["Content-Type"] ?: headers["content-type"]
        val suggestedName = extractFileNameFromDisposition(contentDisposition)
        val mimeType = contentType?.split(";")?.firstOrNull()?.trim()
        val fileName = sanitizeFileName(suggestedName ?: deriveFileNameFromUrl(url))
        MaterialAlertDialogBuilder(this).setTitle(R.string.download_confirm_title).setMessage(getString(R.string.download_confirm_message, fileName, url)).setPositiveButton(R.string.download_btn) { _, _ -> startDownload(url, fileName, mimeType) }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun startDownload(url: String, fileName: String, mimeType: String?) {
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply { setTitle(fileName); setDescription(url); setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName); if (!mimeType.isNullOrBlank()) setMimeType(mimeType) }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val systemId = dm.enqueue(request)
            downloadRepository.addDownload(DownloadItem(systemDownloadId = systemId, url = url, fileName = fileName, mimeType = mimeType, status = DownloadItem.STATUS_QUEUED))
            com.google.android.material.snackbar.Snackbar.make(geckoView, R.string.download_started, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) { Log.e(TAG, "Failed to start download", e) }
    }
    private fun deriveFileNameFromUrl(url: String): String = try { val path = android.net.Uri.parse(url).lastPathSegment; if (!path.isNullOrBlank()) URLDecoder.decode(path, Charsets.UTF_8.name()) else "download_${System.currentTimeMillis()}" } catch (e: Exception) { "download_${System.currentTimeMillis()}" }
    private fun sanitizeFileName(name: String): String { val s = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim(); return s.ifBlank { "download_${System.currentTimeMillis()}" } }
    private fun extractFileNameFromDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null
        val patterns = listOf(Regex("filename\\*=(?:UTF-8''|utf-8'')(.+)", RegexOption.IGNORE_CASE), Regex("filename=\"(.+?)\"", RegexOption.IGNORE_CASE), Regex("filename=([^;\\s]+)", RegexOption.IGNORE_CASE))
        for (p in patterns) { val m = p.find(disposition); if (m != null) return try { URLDecoder.decode(m.groupValues[1], Charsets.UTF_8.name()) } catch (e: Exception) { m.groupValues[1] } }
        return null
    }

    private fun loadFromInput() { val rawInput = urlEditText.text?.toString().orEmpty(); val url = normalizeInput(rawInput); if (url.isBlank()) return; hideKeyboard(); loadUrl(url) }
    private fun loadUrl(url: String) { try { updateUrlField(url); saveLastUrl(url); tabManager.getActiveSession()?.loadUri(url) } catch (e: Exception) { Log.e(TAG, "Unable to load URL: $url", e) } }
    private fun normalizeInput(rawInput: String): String {
        val input = rawInput.trim(); if (input.isBlank()) return ""
        if (isInternalHomeUrl(input)) return ""
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return input
        if (looksLikeDomain(input)) return "https://$input"
        return searchEngineManager.buildSearchUrl(input)
    }
    private fun looksLikeDomain(input: String): Boolean { if (input.any { it.isWhitespace() }) return false; return Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+(?::\\d{1,5})?(?:/.*)?$").matches(input) }
    private fun updateUrlField(url: String) { if (urlEditText.text?.toString() == url) return; urlEditText.setText(url); urlEditText.setSelection(0) }
    private fun saveLastUrl(url: String) {
        if (tabManager.getActiveTab()?.isPrivate == true) return
        if (!isWebUrl(url)) return
        preferences.edit().putString(PREF_LAST_URL, url).apply()
    }
    private fun hideKeyboard() { val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager; imm?.hideSoftInputFromWindow(urlEditText.windowToken, 0); urlEditText.clearFocus() }
    private fun hideKeyboardFrom(view: View) { val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager; imm?.hideSoftInputFromWindow(view.windowToken, 0); view.clearFocus() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "NabdBrowser"
        private const val PREFS_NAME = "nabd_preferences"
        private const val PREF_LAST_URL = "last_url"
        private const val DEFAULT_HOME = "https://www.google.com"
        private const val EXTENSION_LOCATION = "resource://android/assets/summarizer-extension/"
        private const val EXTENSION_ID = "summarizer@example.com"
        private const val NATIVE_APP_ID = "browser"
    }
}
