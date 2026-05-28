package com.amiraq.nabd.summarizer

import com.amiraq.nabd.settings.SettingsRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Factory that provides the active [Summarizer] implementation based on runtime settings.
 *
 * Reads user preferences from [SettingsRepository]. When remote summarization is enabled,
 * returns [RemoteApiSummarizer] configured with the user's endpoint, language, and max length.
 * Otherwise returns [MockSummarizer] which works offline.
 */
object SummarizerFactory {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun create(settings: SettingsRepository): Summarizer {
        return if (settings.isRemoteSummarizerEnabled()) {
            RemoteApiSummarizer(
                endpointUrl = settings.getSummarizerEndpoint(),
                language = settings.getSummaryLanguage(),
                maxLength = settings.getSummaryMaxLength(),
                client = client
            )
        } else {
            MockSummarizer()
        }
    }
}
