package com.amiraq.nabd.search

/**
 * Built-in search engine definitions.
 */
object SearchEngines {

    const val ID_GOOGLE = "google"
    const val ID_DUCKDUCKGO = "duckduckgo"
    const val ID_BING = "bing"
    const val ID_BRAVE = "brave"
    const val ID_YAHOO = "yahoo"
    const val ID_CUSTOM = "custom"

    val GOOGLE = SearchEngine(
        id = ID_GOOGLE,
        name = "Google",
        searchUrlTemplate = "https://www.google.com/search?q=%s"
    )

    val DUCKDUCKGO = SearchEngine(
        id = ID_DUCKDUCKGO,
        name = "DuckDuckGo",
        searchUrlTemplate = "https://duckduckgo.com/?q=%s"
    )

    val BING = SearchEngine(
        id = ID_BING,
        name = "Bing",
        searchUrlTemplate = "https://www.bing.com/search?q=%s"
    )

    val BRAVE = SearchEngine(
        id = ID_BRAVE,
        name = "Brave Search",
        searchUrlTemplate = "https://search.brave.com/search?q=%s"
    )

    val YAHOO = SearchEngine(
        id = ID_YAHOO,
        name = "Yahoo",
        searchUrlTemplate = "https://search.yahoo.com/search?p=%s"
    )

    fun getBuiltInEngines(): List<SearchEngine> = listOf(
        GOOGLE, DUCKDUCKGO, BING, BRAVE, YAHOO
    )

    fun getDefault(): SearchEngine = GOOGLE

    fun findById(id: String): SearchEngine? {
        return getBuiltInEngines().find { it.id == id }
    }
}
