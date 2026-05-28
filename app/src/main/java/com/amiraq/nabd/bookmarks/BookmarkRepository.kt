package com.amiraq.nabd.bookmarks

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists bookmarks in SharedPreferences as a JSON array.
 * Bookmarks are returned sorted newest first.
 */
class BookmarkRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBookmarks(): List<Bookmark> {
        return loadBookmarks().sortedByDescending { it.createdAt }
    }

    fun addBookmark(title: String, url: String) {
        if (url.isBlank()) return
        val bookmarks = loadBookmarks().toMutableList()

        // Avoid duplicates by URL
        if (bookmarks.any { it.url == url }) return

        val bookmark = Bookmark(
            title = title.ifBlank { url },
            url = url
        )
        bookmarks.add(bookmark)
        saveBookmarks(bookmarks)
    }

    fun removeBookmark(id: String) {
        val bookmarks = loadBookmarks().toMutableList()
        bookmarks.removeAll { it.id == id }
        saveBookmarks(bookmarks)
    }

    fun removeBookmarkByUrl(url: String) {
        val bookmarks = loadBookmarks().toMutableList()
        bookmarks.removeAll { it.url == url }
        saveBookmarks(bookmarks)
    }

    fun isBookmarked(url: String): Boolean {
        return loadBookmarks().any { it.url == url }
    }

    private fun loadBookmarks(): List<Bookmark> {
        val json = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<Bookmark>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Bookmark(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        url = obj.optString("url", ""),
                        createdAt = obj.optLong("createdAt", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse bookmarks JSON, resetting", e)
            prefs.edit().remove(KEY_BOOKMARKS).apply()
            emptyList()
        }
    }

    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val array = JSONArray()
        for (bookmark in bookmarks) {
            val obj = JSONObject().apply {
                put("id", bookmark.id)
                put("title", bookmark.title)
                put("url", bookmark.url)
                put("createdAt", bookmark.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    companion object {
        private const val TAG = "BookmarkRepository"
        private const val PREFS_NAME = "nabd_bookmarks"
        private const val KEY_BOOKMARKS = "bookmarks"
    }
}
