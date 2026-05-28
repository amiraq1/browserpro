package com.amiraq.nabd

import android.content.Context
import android.content.SharedPreferences
import android.app.Dialog
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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.net.URLEncoder
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError

class MainActivity : AppCompatActivity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession
    private lateinit var geckoView: GeckoView
    private lateinit var urlEditText: TextInputEditText
    private lateinit var goButton: MaterialButton
    private lateinit var summarizeButton: MaterialButton
    private lateinit var pageProgress: LinearProgressIndicator
    private lateinit var preferences: SharedPreferences

    private var canGoBack = false
    private var summarizerAction: WebExtension.Action? = null
    private var popupDialog: Dialog? = null
    private var popupSession: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        bindViews()
        setupBrowserControls()
        setupGecko()
        setupBackNavigation()
        installSummarizerExtension()

        val startUrl = preferences.getString(PREF_LAST_URL, DEFAULT_HOME).orEmpty()
            .ifBlank { DEFAULT_HOME }
        loadUrl(startUrl)
    }

    override fun onStart() {
        super.onStart()
        if (::session.isInitialized) {
            session.setActive(true)
        }
    }

    override fun onStop() {
        if (::session.isInitialized) {
            session.setActive(false)
        }
        super.onStop()
    }

    override fun onDestroy() {
        dismissExtensionPopup()
        if (::session.isInitialized) {
            session.close()
        }
        super.onDestroy()
    }

    private fun bindViews() {
        geckoView = findViewById(R.id.geckoView)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)
        summarizeButton = findViewById(R.id.summarizeButton)
        pageProgress = findViewById(R.id.pageProgress)
    }

    private fun setupBrowserControls() {
        goButton.setOnClickListener {
            loadFromInput()
        }

        summarizeButton.setOnClickListener {
            val action = summarizerAction
            if (action == null) {
                Toast.makeText(this, R.string.summarize_page, Toast.LENGTH_SHORT).show()
            } else {
                action.click()
            }
        }

        urlEditText.setOnEditorActionListener { _, actionId, event ->
            val isEnterUp = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP
            val isGoAction = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH

            if (isEnterUp || isGoAction) {
                loadFromInput()
                true
            } else {
                false
            }
        }
    }

    private fun setupGecko() {
        // GeckoRuntime owns the browser engine process. Keep one runtime for
        // the app, then attach GeckoSession instances to it.
        runtime = GeckoRuntime.create(this)
        session = GeckoSession()
        session.setProgressDelegate(createProgressDelegate())
        session.setNavigationDelegate(createNavigationDelegate())
        session.open(runtime)
        geckoView.setSession(session)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) {
                    session.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun createProgressDelegate() = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            pageProgress.progress = 0
            pageProgress.visibility = View.VISIBLE
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            pageProgress.progress = progress.coerceIn(0, 100)
            if (progress < 100) {
                pageProgress.visibility = View.VISIBLE
            }
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            pageProgress.progress = 100
            pageProgress.visibility = View.GONE
            if (!success) {
                Log.e(TAG, "Page stopped with an error")
            }
        }
    }

    private fun createNavigationDelegate() = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean
        ) {
            if (!url.isNullOrBlank()) {
                updateUrlField(url)
                saveLastUrl(url)
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            this@MainActivity.canGoBack = canGoBack
        }

        override fun onLoadError(
            session: GeckoSession,
            uri: String?,
            error: WebRequestError
        ): GeckoResult<String>? {
            Log.e(TAG, "Failed to load $uri: $error")
            return null
        }
    }

    private fun loadFromInput() {
        val rawInput = urlEditText.text?.toString().orEmpty()
        val url = normalizeInput(rawInput)
        if (url.isBlank()) {
            return
        }

        hideKeyboard()
        loadUrl(url)
    }

    private fun loadUrl(url: String) {
        try {
            updateUrlField(url)
            saveLastUrl(url)
            session.loadUri(url)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to load URL: $url", exception)
        }
    }

    private fun normalizeInput(rawInput: String): String {
        val input = rawInput.trim()
        if (input.isBlank()) {
            return ""
        }

        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
        ) {
            return input
        }

        if (looksLikeDomain(input)) {
            return "https://$input"
        }

        val encodedQuery = URLEncoder.encode(input, Charsets.UTF_8.name())
        return "https://www.google.com/search?q=$encodedQuery"
    }

    private fun looksLikeDomain(input: String): Boolean {
        if (input.any { it.isWhitespace() }) {
            return false
        }

        val domainPattern = Regex(
            pattern = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+(?:\\:\\d{1,5})?(?:/.*)?$"
        )
        return domainPattern.matches(input)
    }

    private fun updateUrlField(url: String) {
        if (urlEditText.text?.toString() == url) {
            return
        }

        urlEditText.setText(url)
        urlEditText.setSelection(urlEditText.text?.length ?: 0)
    }

    private fun saveLastUrl(url: String) {
        preferences.edit().putString(PREF_LAST_URL, url).apply()
    }

    private fun hideKeyboard() {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputManager?.hideSoftInputFromWindow(urlEditText.windowToken, 0)
        urlEditText.clearFocus()
    }

    private fun installSummarizerExtension() {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept({ extension ->
                val ensuredExtension = extension ?: run {
                    Log.e(TAG, "Summarizer extension was not returned by GeckoView")
                    return@accept
                }

                // popup.js calls browser.runtime.sendNativeMessage("browser", ...).
                // GeckoView routes those messages to this delegate because the
                // native app id here matches the JavaScript native app id.
                ensuredExtension.setMessageDelegate(createSummarizerMessageDelegate(), NATIVE_APP_ID)
                val actionDelegate = createSummarizerActionDelegate()
                ensuredExtension.setActionDelegate(actionDelegate)
                session.webExtensionController.setActionDelegate(ensuredExtension, actionDelegate)
                Log.d(TAG, "Web Page Summarizer extension is ready")
            }, { throwable ->
                Log.e(TAG, "Failed to install summarizer extension", throwable)
            })
    }

    private fun createSummarizerMessageDelegate() = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            return try {
                if (nativeApp != NATIVE_APP_ID) {
                    errorResponse("Unsupported native app: $nativeApp")
                } else {
                    handleSummarizeMessage(message)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Native message handling failed", exception)
                errorResponse("Native message handling failed")
            }
        }
    }

    private fun createSummarizerActionDelegate() = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action
        ) {
            summarizerAction = action
            summarizeButton.isEnabled = true
        }

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? {
            return GeckoResult.fromValue(showExtensionPopup())
        }

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? {
            return if (popupDialog?.isShowing == true) {
                dismissExtensionPopup()
                null
            } else {
                GeckoResult.fromValue(showExtensionPopup())
            }
        }
    }

    private fun showExtensionPopup(): GeckoSession {
        dismissExtensionPopup()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val popupView = GeckoView(this)
        popupView.layoutParams = ViewGroup.LayoutParams(dp(340), dp(440))

        val newPopupSession = GeckoSession()
        newPopupSession.open(runtime)
        popupView.setSession(newPopupSession)

        dialog.setContentView(popupView)
        dialog.setOnDismissListener {
            if (popupSession === newPopupSession) {
                popupSession = null
                popupDialog = null
            }
            newPopupSession.close()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(dp(340), dp(440))

        popupDialog = dialog
        popupSession = newPopupSession

        return newPopupSession
    }

    private fun dismissExtensionPopup() {
        popupDialog?.setOnDismissListener(null)
        popupDialog?.dismiss()
        popupDialog = null
        popupSession?.close()
        popupSession = null
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun handleSummarizeMessage(message: Any): GeckoResult<Any> {
        val payload = message as? JSONObject
            ?: return errorResponse("Expected a JSON object")

        if (payload.optString("type") != "summarize") {
            return errorResponse("Unsupported message type")
        }

        val pageText = payload.optString("text").trim()
        if (pageText.isBlank()) {
            return errorResponse("Page text is empty")
        }

        val summary = pageText.take(100) + "..."
        return successResponse(summary)
    }

    private fun successResponse(summary: String): GeckoResult<Any> {
        val response = JSONObject()
            .put("ok", true)
            .put("summary", summary)
        return GeckoResult.fromValue(response)
    }

    private fun errorResponse(error: String): GeckoResult<Any> {
        val response = JSONObject()
            .put("ok", false)
            .put("error", error)
        return GeckoResult.fromValue(response)
    }

    companion object {
        private const val TAG = "NabdBrowser"
        private const val PREFS_NAME = "nabd_preferences"
        private const val PREF_LAST_URL = "last_url"

        const val DEFAULT_HOME = "https://www.google.com"
        const val EXTENSION_LOCATION = "resource://android/assets/summarizer-extension/"
        const val EXTENSION_ID = "summarizer@example.com"
        const val NATIVE_APP_ID = "browser"
    }
}
