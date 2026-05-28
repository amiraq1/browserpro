package com.amiraq.nabd.reader

sealed class ReaderResult {
    data class Success(val article: ReaderArticle) : ReaderResult()
    data class Error(val message: String) : ReaderResult()
}
