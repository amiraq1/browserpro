package com.amiraq.nabd.session

data class BrowserSessionState(
    val tabs: List<SavedTabState>,
    val activeTabId: String?,
    val savedAt: Long = System.currentTimeMillis()
)
