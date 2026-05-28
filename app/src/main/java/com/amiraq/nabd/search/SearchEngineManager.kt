package com.amiraq.nabd.search

import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import java.net.URLEncoder

/**
 * Manages the active search engine and builds search URLs from queries.
 */
class SearchEngineManager(private val settings: SettingsRepository) {

    /**
     * Returns the currently selected search engine based on user settings.
     */
    fun getCurrentSearchEngine(): SearchEngine {
        val id = settings.getSearchEngineId()

        if (id == SearchEngines.ID_CUSTOM) {
            val name = settings.getCustomSearchEngineName().ifBlank { "Custom" }
            val template = settings.getCustomSearchEngineTemplate()
            if (isValidCustomTemplate(template)) {
                return SearchEngine(
                    id = SearchEngines.ID_CUSTOM,
                    name = name,
                    searchUrlTemplate = template,
                    isCustom = true
                )
            }
            Log.w(TAG, "Invalid custom template, falling back to default")
        }

        return SearchEngines.findById(id) ?: SearchEngines.getDefault()
    }

    /**
     * Builds a full search URL by encoding the query and inserting it into the template.
     */
    fun buildSearchUrl(query: String): String {
        val engine = getCurrentSearchEngine()
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return engine.searchUrlTemplate.replace("%s", encoded)
    }

    /**
     * Validates a custom search URL template.
     * Must start with https:// (or http:// for localhost), and contain %s.
     */
    fun isValidCustomTemplate(template: String): Boolean {
        if (template.isBlank()) return false
        if (!template.contains("%s")) return false

        val isHttps = template.startsWith("https://")
        val isLocalHttp = template.startsWith("http://localhost") ||
            template.startsWith("http://127.0.0.1") ||
            template.startsWith("http://10.0.2.2")

        if (!isHttps && !isLocalHttp) return false

        // Check it forms a valid URL when %s is replaced
        return try {
            val testUrl = template.replace("%s", "test")
            android.net.Uri.parse(testUrl).scheme != null
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "SearchEngineManager"
    }
}
