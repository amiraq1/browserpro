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
import com.amiraq.nabd.share.ShareUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
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
    private lateinit var summarizer: Summarizer
    private lateinit var preferences: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var summarizerExtension: WebExtension? = null
    private var summarizerAction: WebExtension.Action? = null
    private var popupDialog: Dialog? = null
    private var popupSession: GeckoSession? = null
    private lateinit var homePageContainer: ScrollView
    private lateinit var homeSearchInput: TextInputEditText
    private lateinit var homeQuickLinksContainer: android.widget.GridLayout
    private lateinit var homeRecentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
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
        summarizer = SummarizerFactory.create(settingsRepository)
        bindViews()
        setupGeckoRuntime()
        setupTabManager()
        setupBrowserControls()
        setupBackNavigation()
        installSummarizerExtension()
        val startUrl = preferences.getString(PREF_LAST_URL, DEFAULT_HOME).orEmpty().ifBlank { DEFAULT_HOME }
        val firstTab = tabManager.createTab(startUrl)
        setupTabDelegates(firstTab)
        if (settingsRepository.isCustomHomepageEnabled() && startUrl == DEFAULT_HOME) {
            firstTab.isHomePage = true
            showHomePage()
        } else {
            firstTab.session.loadUri(startUrl)
        }
        updateTabCountButton()
    }
    override fun onStart() { super.onStart(); tabManager.getActiveSession()?.setActive(true) }
    override fun onResume() { super.onResume(); if (::settingsRepository.isInitialized) { summarizer = SummarizerFactory.create(settingsRepository); searchEngineManager = SearchEngineManager(settingsRepository); if (::geckoRuntime.isInitialized) PrivacyProtectionManager.applyToRuntime(geckoRuntime.settings, settingsRepository) }; if (!isWebFullscreen) setSystemBarsVisible(true) }
    override fun onStop() { tabManager.getActiveSession()?.setActive(false); super.onStop() }
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
    }
    private fun setupGeckoRuntime() { try { geckoRuntime = GeckoRuntime.create(this); PrivacyProtectionManager.applyToRuntime(geckoRuntime.settings, settingsRepository) } catch (e: Exception) { Log.e(TAG, "Failed to create GeckoRuntime", e) } }
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
        val items = arrayOf(bookmarkLabel, getString(R.string.menu_bookmarks), getString(R.string.menu_history), getString(R.string.menu_downloads), getString(R.string.find_in_page), getString(R.string.reader_mode), getString(R.string.menu_share_page), getString(R.string.menu_copy_link), getString(R.string.menu_new_private_tab), desktopLabel, immersiveLabel, getString(R.string.privacy_report_title), getString(R.string.clear_browsing_data), getString(R.string.settings_title))
        MaterialAlertDialogBuilder(this).setItems(items) { _, which -> when (which) { 0 -> toggleBookmark(); 1 -> showBookmarksDialog(); 2 -> showHistoryDialog(); 3 -> startActivity(Intent(this, DownloadsActivity::class.java)); 4 -> showFindBar(); 5 -> openReaderMode(); 6 -> sharePage(); 7 -> copyPageLink(); 8 -> openNewPrivateTab(); 9 -> toggleDesktopMode(); 10 -> toggleToolbarVisibility(); 11 -> showPrivacyReport(); 12 -> startActivity(Intent(this, com.amiraq.nabd.privacy.ClearBrowsingDataActivity::class.java)); 13 -> startActivity(Intent(this, SettingsActivity::class.java)) } }.show()
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
        refreshHomePage()
    }

    private fun hideHomePage() {
        homePageContainer.visibility = View.GONE
        geckoView.visibility = View.VISIBLE
    }

    private fun refreshHomePage() {
        renderQuickLinks()
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
            if (isEnterUp || isGoAction) { loadFromHomeInput(homeSearchInput.text?.toString().orEmpty()); true } else false
        }
    }

    private fun loadFromHomeInput(rawInput: String) {
        val url = normalizeInput(rawInput)
        if (url.isBlank()) return
        hideKeyboard()
        tabManager.getActiveTab()?.isHomePage = false
        hideHomePage()
        loadUrl(url)
    }

    private fun renderQuickLinks() {
        homeQuickLinksContainer.removeAllViews()
        val links = homePageRepository.getQuickLinks()
        val columnCount = 4
        homeQuickLinksContainer.columnCount = columnCount

        for (link in links) {
            val card = createQuickLinkCard(link.title, link.url) {
                tabManager.getActiveTab()?.isHomePage = false
                hideHomePage()
                loadUrl(link.url)
            }
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

        // Add button
        val addCard = createQuickLinkCard("+", getString(R.string.home_add_quick_link)) {
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

    private fun createQuickLinkCard(title: String, @Suppress("UNUSED_PARAMETER") subtitle: String, onClick: () -> Unit): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(3).toFloat()
            strokeWidth = 0
            setContentPadding(dp(6), dp(14), dp(6), dp(10))
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        // Circular initial with theme-aware background
        val initial = TextView(this).apply {
            val letter = if (title == "+") "+" else title.take(1).uppercase()
            text = letter
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            val size = dp(44)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(getThemeColor(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            setPadding(0, dp(10), 0, 0)
        }
        inner.addView(initial)
        val label = TextView(this).apply {
            text = title.take(8)
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(6), 0, 0)
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
        }
        inner.addView(label)
        card.addView(inner)
        return card
    }

    private fun showQuickLinkOptionsDialog(link: com.amiraq.nabd.home.HomeQuickLink) {
        val options = arrayOf(getString(R.string.home_open), getString(R.string.home_edit), getString(R.string.home_remove))
        MaterialAlertDialogBuilder(this).setTitle(link.title).setItems(options) { _, which ->
            when (which) {
                0 -> { tabManager.getActiveTab()?.isHomePage = false; hideHomePage(); loadUrl(link.url) }
                1 -> showEditFavoriteDialog(link)
                2 -> { homePageRepository.removeQuickLink(link.id); renderQuickLinks() }
            }
        }.show()
    }

    private fun showAddFavoriteDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)) }
        val nameInput = com.google.android.material.textfield.TextInputEditText(this).apply { hint = getString(R.string.home_favorite_name_hint) }
        val urlInput = com.google.android.material.textfield.TextInputEditText(this).apply { hint = getString(R.string.home_favorite_url_hint) }
        layout.addView(nameInput); layout.addView(urlInput)
        MaterialAlertDialogBuilder(this).setTitle(R.string.home_add_quick_link).setView(layout)
            .setPositiveButton(R.string.setting_save) { _, _ ->
                val name = nameInput.text?.toString().orEmpty().trim()
                val url = urlInput.text?.toString().orEmpty().trim()
                if (name.isNotBlank() && url.isNotBlank()) {
                    val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                    homePageRepository.addQuickLink(name, normalizedUrl)
                    renderQuickLinks()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showEditFavoriteDialog(link: com.amiraq.nabd.home.HomeQuickLink) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)) }
        val nameInput = com.google.android.material.textfield.TextInputEditText(this).apply { hint = getString(R.string.home_favorite_name_hint); setText(link.title) }
        val urlInput = com.google.android.material.textfield.TextInputEditText(this).apply { hint = getString(R.string.home_favorite_url_hint); setText(link.url) }
        layout.addView(nameInput); layout.addView(urlInput)
        MaterialAlertDialogBuilder(this).setTitle(R.string.home_edit_favorite).setView(layout)
            .setPositiveButton(R.string.setting_save) { _, _ ->
                val name = nameInput.text?.toString().orEmpty().trim()
                val url = urlInput.text?.toString().orEmpty().trim()
                if (name.isNotBlank() && url.isNotBlank()) {
                    homePageRepository.removeQuickLink(link.id)
                    val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                    homePageRepository.addQuickLink(name, normalizedUrl)
                    renderQuickLinks()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun renderRecentSites() {
        homeRecentContainer.removeAllViews()
        val recent = historyRepository.getHistory().take(5)
        if (recent.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.home_no_recent)
                textSize = 14f
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            homeRecentContainer.addView(empty)
            return
        }
        for (item in recent) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setOnClickListener {
                    tabManager.getActiveTab()?.isHomePage = false
                    hideHomePage()
                    loadUrl(item.url)
                }
                isClickable = true
                isFocusable = true
            }
            // Domain initial circle
            val domain = try { android.net.Uri.parse(item.url).host ?: "" } catch (e: Exception) { "" }
            val circle = TextView(this).apply {
                text = domain.removePrefix("www.").take(1).uppercase()
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
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
            textContainer.addView(TextView(this).apply {
                text = item.title.take(40)
                textSize = 14f
                maxLines = 1
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
            })
            textContainer.addView(TextView(this).apply {
                text = domain.removePrefix("www.")
                textSize = 12f
                maxLines = 1
                setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            row.addView(textContainer)
            homeRecentContainer.addView(row)
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
    private fun updateTabCountButton() {
        val count = tabManager.getTabCount().toString()
        val isPrivate = tabManager.getActiveTab()?.isPrivate == true
        tabCountButton.text = if (isPrivate) "🕶$count" else count
    }
    private fun updateProgressForTab(tab: BrowserTab) { if (tab.isLoading) { pageProgress.progress = tab.progress.coerceIn(0, 100); pageProgress.visibility = View.VISIBLE } else pageProgress.visibility = View.GONE }
    private fun recordHistory(title: String, url: String) {
        // Do not record history for private tabs
        val activeTab = tabManager.getActiveTab()
        if (activeTab?.isPrivate == true) return
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
        override fun onPageStop(session: GeckoSession, success: Boolean) { tab.isLoading = false; tab.progress = 100; if (tab.id == tabManager.getActiveTab()?.id) { pageProgress.progress = 100; pageProgress.visibility = View.GONE }; if (success && tab.url.isNotBlank()) recordHistory(tab.title.ifBlank { tab.url }, tab.url); if (!success) Log.e(TAG, "Page failed in tab ${tab.id}") }
    }
    private fun createNavigationDelegate(tab: BrowserTab) = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) { if (!url.isNullOrBlank()) { tab.url = url; tab.isHomePage = false; if (tab.id == tabManager.getActiveTab()?.id) { hideHomePage(); updateUrlField(url); saveLastUrl(url) } } }
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
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return input
        if (looksLikeDomain(input)) return "https://$input"
        return searchEngineManager.buildSearchUrl(input)
    }
    private fun looksLikeDomain(input: String): Boolean { if (input.any { it.isWhitespace() }) return false; return Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+(?::\\d{1,5})?(?:/.*)?$").matches(input) }
    private fun updateUrlField(url: String) { if (urlEditText.text?.toString() == url) return; urlEditText.setText(url); urlEditText.setSelection(urlEditText.text?.length ?: 0) }
    private fun saveLastUrl(url: String) {
        if (tabManager.getActiveTab()?.isPrivate == true) return
        preferences.edit().putString(PREF_LAST_URL, url).apply()
    }
    private fun hideKeyboard() { val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager; imm?.hideSoftInputFromWindow(urlEditText.windowToken, 0); urlEditText.clearFocus() }
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
