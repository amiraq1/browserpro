package com.amiraq.nabd.downloads

import java.util.UUID

/**
 * Represents a single download record.
 */
data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val systemDownloadId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String? = null,
    val destinationUri: String? = null,
    var status: String = STATUS_QUEUED,
    val createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long? = null
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_SUCCESSFUL = "SUCCESSFUL"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_UNKNOWN = "UNKNOWN"
    }
}
