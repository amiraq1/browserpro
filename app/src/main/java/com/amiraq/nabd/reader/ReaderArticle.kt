package com.amiraq.nabd.reader

/**
 * Holds extracted article content for Reader Mode.
 */
data class ReaderArticle(
    val title: String,
    val url: String,
    val byline: String? = null,
    val content: String,
    val extractedAt: Long = System.currentTimeMillis()
)
