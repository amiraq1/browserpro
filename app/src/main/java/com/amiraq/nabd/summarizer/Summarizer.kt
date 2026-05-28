package com.amiraq.nabd.summarizer

/**
 * Abstraction for text summarization.
 *
 * Implementations may use local heuristics, remote AI APIs, or on-device models.
 * The suspend modifier allows implementations to perform async work (network calls,
 * model inference) without blocking the caller.
 */
interface Summarizer {
    suspend fun summarize(text: String): SummarizationResult
}
