package com.amiraq.nabd.reader

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession

/**
 * Extracts article content from the active tab.
 *
 * Flow:
 * 1. Kotlin loads a javascript: URI that dispatches a custom DOM event.
 * 2. The content script listens for this event and extracts article content.
 * 3. The content script sends the result via sendNativeMessage("browser", ...).
 * 4. The native message delegate in MainActivity receives type "readerArticle"
 *    and calls [onArticleReceived].
 */
object ReaderExtractor {

    private const val TAG = "ReaderExtractor"
    private const val MAX_CONTENT_LENGTH = 100_000
    private const val MIN_CONTENT_LENGTH = 50
    private const val TIMEOUT_MS = 5000L

    @Volatile
    var pendingCallback: ((ReaderResult) -> Unit)? = null

    /**
     * Called from the native message delegate when a "readerArticle" message arrives.
     */
    fun onArticleReceived(json: JSONObject) {
        val callback = pendingCallback
        pendingCallback = null

        if (callback == null) {
            Log.w(TAG, "Received reader article but no pending callback")
            return
        }

        try {
            val ok = json.optBoolean("ok", false)
            if (!ok) {
                val error = json.optString("error", "Extraction failed")
                callback(ReaderResult.Error(error))
                return
            }

            val title = json.optString("title", "").trim()
            val byline = json.optString("byline", "").trim().ifBlank { null }
            var content = json.optString("content", "").trim()

            if (content.length < MIN_CONTENT_LENGTH) {
                callback(ReaderResult.Error("Page does not have enough readable content."))
                return
            }

            if (content.length > MAX_CONTENT_LENGTH) {
                content = content.take(MAX_CONTENT_LENGTH)
            }

            content = content
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()

            val article = ReaderArticle(
                title = title.ifBlank { "Untitled" },
                url = "",
                byline = byline,
                content = content
            )
            callback(ReaderResult.Success(article))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse reader article", e)
            callback(ReaderResult.Error("Failed to parse article content."))
        }
    }

    /**
     * Triggers article extraction by dispatching a custom DOM event
     * that the content script listens for.
     */
    fun requestExtraction(session: GeckoSession, url: String, callback: (ReaderResult) -> Unit) {
        pendingCallback = { result ->
            // Attach the URL to successful results
            when (result) {
                is ReaderResult.Success -> callback(ReaderResult.Success(result.article.copy(url = url)))
                is ReaderResult.Error -> callback(result)
            }
        }

        try {
            // Dispatch custom event that content script listens for
            session.loadUri("javascript:void(document.dispatchEvent(new CustomEvent('nabd-extract-article')))")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger extraction", e)
            pendingCallback = null
            callback(ReaderResult.Error("Could not trigger article extraction."))
            return
        }

        // Timeout fallback
        Handler(Looper.getMainLooper()).postDelayed({
            val cb = pendingCallback
            if (cb != null) {
                pendingCallback = null
                cb(ReaderResult.Error("Article extraction timed out. Try again."))
            }
        }, TIMEOUT_MS)
    }
}
