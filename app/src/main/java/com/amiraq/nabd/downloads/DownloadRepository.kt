package com.amiraq.nabd.downloads

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists download records in SharedPreferences as a JSON array.
 */
class DownloadRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDownloads(): List<DownloadItem> {
        return loadDownloads().sortedByDescending { it.createdAt }
    }

    fun addDownload(item: DownloadItem) {
        val downloads = loadDownloads().toMutableList()
        downloads.add(item)
        saveDownloads(downloads)
    }

    fun updateStatus(systemDownloadId: Long, status: String, completedAt: Long?) {
        val downloads = loadDownloads().toMutableList()
        val index = downloads.indexOfFirst { it.systemDownloadId == systemDownloadId }
        if (index >= 0) {
            downloads[index] = downloads[index].copy(
                status = status,
                completedAt = completedAt
            )
            saveDownloads(downloads)
        }
    }

    fun updateDestinationUri(systemDownloadId: Long, uri: String) {
        val downloads = loadDownloads().toMutableList()
        val index = downloads.indexOfFirst { it.systemDownloadId == systemDownloadId }
        if (index >= 0) {
            downloads[index] = downloads[index].copy(destinationUri = uri)
            saveDownloads(downloads)
        }
    }

    fun removeDownload(id: String) {
        val downloads = loadDownloads().toMutableList()
        downloads.removeAll { it.id == id }
        saveDownloads(downloads)
    }

    fun findBySystemDownloadId(systemDownloadId: Long): DownloadItem? {
        return loadDownloads().find { it.systemDownloadId == systemDownloadId }
    }

    fun clearDownloads() {
        prefs.edit().remove(KEY_DOWNLOADS).apply()
    }

    private fun loadDownloads(): List<DownloadItem> {
        val json = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<DownloadItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DownloadItem(
                        id = obj.optString("id", ""),
                        systemDownloadId = obj.optLong("systemDownloadId", -1L),
                        url = obj.optString("url", ""),
                        fileName = obj.optString("fileName", ""),
                        mimeType = obj.optString("mimeType", "").ifBlank { null },
                        destinationUri = obj.optString("destinationUri", "").ifBlank { null },
                        status = obj.optString("status", DownloadItem.STATUS_UNKNOWN),
                        createdAt = obj.optLong("createdAt", 0L),
                        completedAt = if (obj.has("completedAt")) obj.optLong("completedAt") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse downloads JSON, resetting", e)
            prefs.edit().remove(KEY_DOWNLOADS).apply()
            emptyList()
        }
    }

    private fun saveDownloads(downloads: List<DownloadItem>) {
        val array = JSONArray()
        for (item in downloads) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("systemDownloadId", item.systemDownloadId)
                put("url", item.url)
                put("fileName", item.fileName)
                put("mimeType", item.mimeType ?: "")
                put("destinationUri", item.destinationUri ?: "")
                put("status", item.status)
                put("createdAt", item.createdAt)
                if (item.completedAt != null) put("completedAt", item.completedAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_DOWNLOADS, array.toString()).apply()
    }

    companion object {
        private const val TAG = "DownloadRepository"
        private const val PREFS_NAME = "nabd_downloads"
        private const val KEY_DOWNLOADS = "downloads"
    }
}
