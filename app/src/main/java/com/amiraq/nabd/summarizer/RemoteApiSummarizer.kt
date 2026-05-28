package com.amiraq.nabd.summarizer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Summarizer implementation that sends page text to a secure remote backend.
 *
 * The backend is responsible for calling the actual AI provider (OpenAI, Gemini, etc.).
 * No AI API keys are stored in the Android app.
 *
 * @param endpointUrl The backend URL to POST summarization requests to.
 * @param language The preferred summary language (ISO 639-1 code).
 * @param maxLength The maximum desired summary length in characters.
 * @param client OkHttpClient instance with configured timeouts.
 *
 * Expected backend response:
 * - Success: { "ok": true, "summary": "..." }
 * - Error:   { "ok": false, "error": "..." }
 */
class RemoteApiSummarizer(
    private val endpointUrl: String,
    private val language: String,
    private val maxLength: Int,
    private val client: OkHttpClient
) : Summarizer {

    override suspend fun summarize(text: String): SummarizationResult {
        val trimmed = text.trim()

        if (trimmed.isBlank()) {
            return SummarizationResult.Error("Page text is empty.")
        }

        // Limit input to avoid sending excessively large payloads
        val truncated = if (trimmed.length > MAX_INPUT_LENGTH) {
            trimmed.take(MAX_INPUT_LENGTH)
        } else {
            trimmed
        }

        return withContext(Dispatchers.IO) {
            try {
                performRequest(truncated)
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network error during summarization", e)
                SummarizationResult.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during summarization", e)
                SummarizationResult.Error("Summarization failed: ${e.message}")
            }
        }
    }

    private fun performRequest(text: String): SummarizationResult {
        val requestJson = JSONObject().apply {
            put("text", text)
            put("language", language)
            put("maxLength", maxLength)
        }

        val body = requestJson.toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(endpointUrl)
            .post(body)
            .header("Content-Type", "application/json; charset=utf-8")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            return SummarizationResult.Error("Server returned HTTP $code")
        }

        val responseBody = response.body?.string()
        response.close()

        if (responseBody.isNullOrBlank()) {
            return SummarizationResult.Error("Empty response from server")
        }

        return parseResponse(responseBody)
    }

    private fun parseResponse(responseBody: String): SummarizationResult {
        return try {
            val json = JSONObject(responseBody)
            val ok = json.optBoolean("ok", false)

            if (ok) {
                val summary = json.optString("summary", "").trim()
                if (summary.isBlank()) {
                    SummarizationResult.Error("Server returned empty summary")
                } else {
                    SummarizationResult.Success(summary)
                }
            } else {
                val error = json.optString("error", "Unknown server error")
                SummarizationResult.Error(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse server response", e)
            SummarizationResult.Error("Invalid response format from server")
        }
    }

    companion object {
        private const val TAG = "RemoteApiSummarizer"
        private const val MAX_INPUT_LENGTH = 12_000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
