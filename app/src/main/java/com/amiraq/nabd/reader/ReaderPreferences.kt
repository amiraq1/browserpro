package com.amiraq.nabd.reader

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists reader-specific preferences (text size).
 */
class ReaderPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getReaderTextSize(): Float = prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE)

    fun setReaderTextSize(size: Float) {
        val clamped = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        prefs.edit().putFloat(KEY_TEXT_SIZE, clamped).apply()
    }

    companion object {
        private const val PREFS_NAME = "nabd_reader_prefs"
        private const val KEY_TEXT_SIZE = "reader_text_size"
        const val DEFAULT_TEXT_SIZE = 18f
        const val MIN_TEXT_SIZE = 14f
        const val MAX_TEXT_SIZE = 26f
        const val TEXT_SIZE_STEP = 2f
    }
}
