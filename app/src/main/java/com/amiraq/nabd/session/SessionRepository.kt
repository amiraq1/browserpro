package com.amiraq.nabd.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists and restores browser session state (open tabs) using SharedPreferences.
 * Only normal (non-private) tabs are saved.
 */
class SessionRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(state: BrowserSessionState) {
        try {
            val tabsArray = JSONArray()
            for (tab in state.tabs) {
                tabsArray.put(JSONObject().apply {
                    put("id", tab.id)
                    put("title", tab.title)
                    put("url", tab.url)
                    put("isHomePage", tab.isHomePage)
                    put("isDesktopMode", tab.isDesktopMode)
                    put("savedAt", tab.savedAt)
                })
            }
            val json = JSONObject().apply {
                put("tabs", tabsArray)
                put("activeTabId", state.activeTabId ?: "")
                put("savedAt", state.savedAt)
            }
            prefs.edit().putString(KEY_SESSION, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    fun loadSession(): BrowserSessionState? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val tabsArray = json.optJSONArray("tabs") ?: return null
            val tabs = mutableListOf<SavedTabState>()
            for (i in 0 until tabsArray.length()) {
                val obj = tabsArray.getJSONObject(i)
                tabs.add(SavedTabState(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", ""),
                    url = obj.optString("url", ""),
                    isHomePage = obj.optBoolean("isHomePage", false),
                    isDesktopMode = obj.optBoolean("isDesktopMode", false),
                    savedAt = obj.optLong("savedAt", 0L)
                ))
            }
            if (tabs.isEmpty()) return null
            BrowserSessionState(
                tabs = tabs,
                activeTabId = json.optString("activeTabId", "").ifBlank { null },
                savedAt = json.optLong("savedAt", 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session, ignoring", e)
            null
        }
    }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun hasSavedSession(): Boolean {
        return prefs.getString(KEY_SESSION, null) != null
    }

    companion object {
        private const val TAG = "SessionRepository"
        private const val PREFS_NAME = "nabd_session"
        private const val KEY_SESSION = "browser_session"
    }
}
