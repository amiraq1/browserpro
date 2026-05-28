package com.amiraq.nabd.reader

/**
 * Temporary in-memory holder for the article to display in ReaderActivity.
 * Avoids Intent Binder size limits for large article text.
 */
object ReaderArticleStore {
    var currentArticle: ReaderArticle? = null
}
