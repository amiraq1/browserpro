package com.amiraq.nabd.config

/**
 * Application-level default configuration constants.
 *
 * These values are used as defaults when no user preference is stored.
 * Users can override them at runtime via the Settings screen.
 *
 * IMPORTANT: Never store AI provider API keys (OpenAI, Gemini, etc.) in this file
 * or anywhere in the Android app. Keys belong on the backend only.
 */
object AppConfig {

    /** Default: remote summarizer is disabled (mock mode). */
    const val DEFAULT_USE_REMOTE_SUMMARIZER = false

    /** Default backend endpoint URL. Replace with your own when deploying. */
    const val DEFAULT_SUMMARIZER_ENDPOINT = "https://example.com/summarize"

    /** Default summary language (ISO 639-1). */
    const val DEFAULT_SUMMARY_LANGUAGE = "ar"

    /** Default maximum summary length in characters. */
    const val DEFAULT_SUMMARY_MAX_LENGTH = 600
}
