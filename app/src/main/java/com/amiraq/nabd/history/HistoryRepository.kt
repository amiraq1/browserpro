package com.amiraq.nabd.history

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists browsing history in SharedPreferences as a JSON array.
 * History is returned sorted newest first. Capped at [MAX_ITEMS].
 */
class HistoryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(): List<HistoryItem> {
        return loadHistory().sortedByDescending { it.visitedAt }
    }

    /**
     * Records a page visit. If the most recent item has the same URL,
     * updates its timestamp and title instead of adding a duplicate.
     */
    fun addVisit(title: String, url: String) {
        if (!isRecordableUrl(url)) return

        val history = loadHistory().toMutableList()
        val displayTitle = title.ifBlank { url }

        // Check if latest entry is the same URL — update instead of duplicate
        val sorted = history.sortedByDescending { it.visitedAt }
        if (sorted.isNotEmpty() && sorted[0].url == url) {
            val existing = sorted[0]
            val index = history.indexOf(existing)
            if (index >= 0) {
                history[index] = existing.copy(
                    title = displayTitle,
                    visitedAt = System.currentTimeMillis()
                )
                saveHistory(history)
                return
            }
        }

        val item = HistoryItem(
            title = displayTitle,
            url = url
        )
        history.add(item)

        // Cap at MAX_ITEMS — remove oldest
        val capped = if (history.size > MAX_ITEMS) {
            history.sortedByDescending { it.visitedAt }.take(MAX_ITEMS)
        } else {
            history
        }

        saveHistory(capped)
    }

    fun removeHistoryItem(id: String) {
        val history = loadHistory().toMutableList()
        history.removeAll { it.id == id }
        saveHistory(history)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun isRecordableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun loadHistory(): List<HistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<HistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HistoryItem(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        url = obj.optString("url", ""),
                        visitedAt = obj.optLong("visitedAt", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse history JSON, resetting", e)
            prefs.edit().remove(KEY_HISTORY).apply()
            emptyList()
        }
    }

    private fun saveHistory(history: List<HistoryItem>) {
        val array = JSONArray()
        for (item in history) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("visitedAt", item.visitedAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val TAG = "HistoryRepository"
        private const val PREFS_NAME = "nabd_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_ITEMS = 500
    }
}
