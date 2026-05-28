package com.amiraq.nabd.privacy

/**
 * Result of a clear browsing data operation.
 */
data class ClearBrowsingDataResult(
    val cleared: List<String>,
    val failed: List<String>
)
