package com.amiraq.nabd.summarizer

/**
 * A local mock summarizer that returns the first 100 characters of the input text.
 *
 * This serves as a placeholder until a real AI summarization service is integrated.
 */
class MockSummarizer : Summarizer {

    override suspend fun summarize(text: String): SummarizationResult {
        val trimmed = text.trim()

        if (trimmed.isBlank()) {
            return SummarizationResult.Error("Page text is empty.")
        }

        val summary = if (trimmed.length > MAX_PREVIEW_LENGTH) {
            trimmed.take(MAX_PREVIEW_LENGTH) + "..."
        } else {
            trimmed
        }

        return SummarizationResult.Success(summary)
    }

    companion object {
        private const val MAX_PREVIEW_LENGTH = 100
    }
}
