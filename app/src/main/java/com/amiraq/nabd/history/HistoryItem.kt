package com.amiraq.nabd.history

import java.util.UUID

/**
 * Represents a single browsing history entry.
 */
data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)
