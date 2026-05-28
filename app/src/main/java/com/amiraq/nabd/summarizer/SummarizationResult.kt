package com.amiraq.nabd.summarizer

/**
 * Represents the outcome of a summarization operation.
 */
sealed class SummarizationResult {
    data class Success(val summary: String) : SummarizationResult()
    data class Error(val message: String) : SummarizationResult()
}
