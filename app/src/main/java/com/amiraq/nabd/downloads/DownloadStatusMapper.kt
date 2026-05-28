package com.amiraq.nabd.downloads

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Maps Android DownloadManager status codes to app-level status strings
 * and extracts download metadata.
 */
object DownloadStatusMapper {

    fun queryStatus(context: Context, systemDownloadId: Long): StatusResult? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return null

        val query = DownloadManager.Query().setFilterById(systemDownloadId)
        val cursor: Cursor = dm.query(query) ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return null

            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val uriCol = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            val status = if (statusCol >= 0) it.getInt(statusCol) else -1
            val localUri = if (uriCol >= 0) it.getString(uriCol) else null

            StatusResult(
                status = mapStatus(status),
                localUri = localUri
            )
        }
    }

    fun mapStatus(dmStatus: Int): String {
        return when (dmStatus) {
            DownloadManager.STATUS_PENDING -> DownloadItem.STATUS_QUEUED
            DownloadManager.STATUS_RUNNING -> DownloadItem.STATUS_RUNNING
            DownloadManager.STATUS_SUCCESSFUL -> DownloadItem.STATUS_SUCCESSFUL
            DownloadManager.STATUS_FAILED -> DownloadItem.STATUS_FAILED
            DownloadManager.STATUS_PAUSED -> DownloadItem.STATUS_RUNNING
            else -> DownloadItem.STATUS_UNKNOWN
        }
    }

    data class StatusResult(
        val status: String,
        val localUri: String?
    )
}
