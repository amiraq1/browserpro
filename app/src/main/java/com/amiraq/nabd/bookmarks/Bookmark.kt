package com.amiraq.nabd.bookmarks

import java.util.UUID

/**
 * Represents a saved bookmark.
 */
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)
