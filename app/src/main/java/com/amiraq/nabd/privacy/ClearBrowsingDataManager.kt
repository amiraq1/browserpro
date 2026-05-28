package com.amiraq.nabd.privacy

import android.content.Context
import android.util.Log
import com.amiraq.nabd.bookmarks.BookmarkRepository
import com.amiraq.nabd.downloads.DownloadRepository
import com.amiraq.nabd.history.HistoryRepository
import com.amiraq.nabd.home.HomePageRepository
import com.amiraq.nabd.settings.SettingsRepository
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

/**
 * Handles clearing of browsing data from local repositories and GeckoView storage.
 */
class ClearBrowsingDataManager(private val context: Context) {

    private val historyRepo = HistoryRepository(context)
    private val downloadRepo = DownloadRepository(context)
    private val bookmarkRepo = BookmarkRepository(context)
    private val homePageRepo = HomePageRepository(context)

    /**
     * Clears selected data categories.
     * GeckoView operations require a runtime instance.
     */
    fun clear(
        clearHistory: Boolean = false,
        clearDownloads: Boolean = false,
        clearCookies: Boolean = false,
        clearCache: Boolean = false,
        clearBookmarks: Boolean = false,
        clearQuickLinks: Boolean = false,
        clearSettings: Boolean = false,
        geckoRuntime: GeckoRuntime? = null
    ): ClearBrowsingDataResult {
        val cleared = mutableListOf<String>()
        val failed = mutableListOf<String>()

        if (clearHistory) {
            try {
                historyRepo.clearHistory()
                cleared.add(CAT_HISTORY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear history", e)
                failed.add(CAT_HISTORY)
            }
        }

        if (clearDownloads) {
            try {
                downloadRepo.clearDownloads()
                cleared.add(CAT_DOWNLOADS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear downloads", e)
                failed.add(CAT_DOWNLOADS)
            }
        }

        if (clearBookmarks) {
            try {
                bookmarkRepo.clearBookmarks()
                cleared.add(CAT_BOOKMARKS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear bookmarks", e)
                failed.add(CAT_BOOKMARKS)
            }
        }

        if (clearQuickLinks) {
            try {
                homePageRepo.resetDefaultQuickLinks()
                cleared.add(CAT_QUICK_LINKS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear quick links", e)
                failed.add(CAT_QUICK_LINKS)
            }
        }

        if (clearSettings) {
            try {
                val settingsRepo = SettingsRepository(context)
                settingsRepo.resetToDefaults()
                cleared.add(CAT_SETTINGS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear settings", e)
                failed.add(CAT_SETTINGS)
            }
        }

        // GeckoView storage clearing
        if (clearCookies || clearCache) {
            if (geckoRuntime != null) {
                try {
                    val storageController = geckoRuntime.storageController
                    if (clearCookies) {
                        storageController.clearData(StorageController.ClearFlags.COOKIES)
                            .accept({ cleared.add(CAT_COOKIES) }, { e ->
                                Log.e(TAG, "Failed to clear cookies", e)
                                failed.add(CAT_COOKIES)
                            })
                        storageController.clearData(StorageController.ClearFlags.SITE_DATA)
                            .accept({}, { e -> Log.w(TAG, "Site data clear issue", e) })
                    }
                    if (clearCache) {
                        storageController.clearData(StorageController.ClearFlags.NETWORK_CACHE)
                            .accept({ cleared.add(CAT_CACHE) }, { e ->
                                Log.e(TAG, "Failed to clear cache", e)
                                failed.add(CAT_CACHE)
                            })
                        storageController.clearData(StorageController.ClearFlags.IMAGE_CACHE)
                            .accept({}, { e -> Log.w(TAG, "Image cache clear issue", e) })
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GeckoView storage clearing failed", e)
                    if (clearCookies) failed.add(CAT_COOKIES)
                    if (clearCache) failed.add(CAT_CACHE)
                }
            } else {
                Log.w(TAG, "GeckoRuntime not available for storage clearing")
                if (clearCookies) failed.add(CAT_COOKIES)
                if (clearCache) failed.add(CAT_CACHE)
            }
        }

        return ClearBrowsingDataResult(cleared, failed)
    }

    companion object {
        private const val TAG = "ClearBrowsingData"
        const val CAT_HISTORY = "Browsing history"
        const val CAT_DOWNLOADS = "Download records"
        const val CAT_COOKIES = "Cookies & site data"
        const val CAT_CACHE = "Cached files"
        const val CAT_BOOKMARKS = "Bookmarks"
        const val CAT_QUICK_LINKS = "Quick links"
        const val CAT_SETTINGS = "Settings"
    }
}
