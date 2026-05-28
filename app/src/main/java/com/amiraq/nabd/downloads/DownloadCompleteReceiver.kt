package com.amiraq.nabd.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives broadcast when Android DownloadManager completes a download.
 * Updates the local DownloadRepository with the final status.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        try {
            val repository = DownloadRepository(context)
            // Early return if this download wasn't initiated by our app
            repository.findBySystemDownloadId(downloadId) ?: return

            val result = DownloadStatusMapper.queryStatus(context, downloadId)
            if (result != null) {
                val completedAt = if (result.status == DownloadItem.STATUS_SUCCESSFUL) {
                    System.currentTimeMillis()
                } else {
                    null
                }
                repository.updateStatus(downloadId, result.status, completedAt)

                if (!result.localUri.isNullOrBlank()) {
                    repository.updateDestinationUri(downloadId, result.localUri)
                }
            } else {
                repository.updateStatus(downloadId, DownloadItem.STATUS_UNKNOWN, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling download complete for id=$downloadId", e)
        }
    }

    companion object {
        private const val TAG = "DownloadCompleteRcvr"
    }
}
