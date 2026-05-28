package com.amiraq.nabd.search

/**
 * Represents a search engine with a URL template.
 * The template must contain %s which is replaced with the encoded query.
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val searchUrlTemplate: String,
    val isCustom: Boolean = false
)
