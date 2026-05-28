package com.amiraq.nabd.home

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class HomePageRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getQuickLinks(): List<HomeQuickLink> {
        val json = prefs.getString(KEY_QUICK_LINKS, null)
        if (json == null) {
            val defaults = defaultQuickLinks()
            saveQuickLinks(defaults)
            return defaults
        }
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<HomeQuickLink>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(HomeQuickLink(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", ""),
                    url = obj.optString("url", ""),
                    createdAt = obj.optLong("createdAt", 0L)
                ))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse quick links, resetting", e)
            val defaults = defaultQuickLinks()
            saveQuickLinks(defaults)
            defaults
        }
    }

    fun addQuickLink(title: String, url: String) {
        if (url.isBlank()) return
        val links = getQuickLinks().toMutableList()
        if (links.any { it.url == url }) return
        links.add(HomeQuickLink(title = title.ifBlank { url }, url = url))
        saveQuickLinks(links)
    }

    fun removeQuickLink(id: String) {
        val links = getQuickLinks().toMutableList()
        links.removeAll { it.id == id }
        saveQuickLinks(links)
    }

    fun updateQuickLink(id: String, title: String, url: String) {
        val links = getQuickLinks().toMutableList()
        val index = links.indexOfFirst { it.id == id }
        if (index >= 0) {
            links[index] = links[index].copy(title = title, url = url)
            saveQuickLinks(links)
        }
    }

    fun resetDefaultQuickLinks() {
        saveQuickLinks(defaultQuickLinks())
    }

    fun clearQuickLinks() {
        prefs.edit().remove(KEY_QUICK_LINKS).apply()
    }

    private fun saveQuickLinks(links: List<HomeQuickLink>) {
        val array = JSONArray()
        for (link in links) {
            array.put(JSONObject().apply {
                put("id", link.id)
                put("title", link.title)
                put("url", link.url)
                put("createdAt", link.createdAt)
            })
        }
        prefs.edit().putString(KEY_QUICK_LINKS, array.toString()).apply()
    }

    private fun defaultQuickLinks(): List<HomeQuickLink> = listOf(
        HomeQuickLink(title = "Google", url = "https://www.google.com"),
        HomeQuickLink(title = "Wikipedia", url = "https://www.wikipedia.org"),
        HomeQuickLink(title = "YouTube", url = "https://www.youtube.com"),
        HomeQuickLink(title = "GitHub", url = "https://github.com")
    )

    companion object {
        private const val TAG = "HomePageRepository"
        private const val PREFS_NAME = "nabd_homepage"
        private const val KEY_QUICK_LINKS = "quick_links"
    }
}
