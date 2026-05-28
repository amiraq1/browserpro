package com.amiraq.nabd.session

data class SavedTabState(
    val id: String,
    val title: String,
    val url: String,
    val isHomePage: Boolean,
    val isDesktopMode: Boolean,
    val savedAt: Long = System.currentTimeMillis()
)
