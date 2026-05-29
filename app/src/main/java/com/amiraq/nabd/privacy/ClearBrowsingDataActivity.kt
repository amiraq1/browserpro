package com.amiraq.nabd.privacy

import android.os.Bundle
import android.view.MenuItem
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.amiraq.nabd.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime

class ClearBrowsingDataActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var historyCheck: CheckBox
    private lateinit var downloadsCheck: CheckBox
    private lateinit var cookiesCheck: CheckBox
    private lateinit var cacheCheck: CheckBox
    private lateinit var bookmarksCheck: CheckBox
    private lateinit var quickLinksCheck: CheckBox
    private lateinit var settingsCheck: CheckBox
    private lateinit var clearButton: MaterialButton
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clear_browsing_data)

        toolbar = findViewById(R.id.clearDataToolbar)
        historyCheck = findViewById(R.id.checkHistory)
        downloadsCheck = findViewById(R.id.checkDownloads)
        cookiesCheck = findViewById(R.id.checkCookies)
        cacheCheck = findViewById(R.id.checkCache)
        bookmarksCheck = findViewById(R.id.checkBookmarks)
        quickLinksCheck = findViewById(R.id.checkQuickLinks)
        settingsCheck = findViewById(R.id.checkSettings)
        clearButton = findViewById(R.id.clearDataButton)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.clear_browsing_data)

        clearButton.setOnClickListener { confirmClear() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun confirmClear() {
        if (!anySelected()) {
            Snackbar.make(clearButton, R.string.clear_select_one, Snackbar.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_confirm_title)
            .setMessage(R.string.clear_confirm_message)
            .setPositiveButton(R.string.clear_action) { _, _ -> performClear() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun anySelected(): Boolean {
        return historyCheck.isChecked || downloadsCheck.isChecked ||
            cookiesCheck.isChecked || cacheCheck.isChecked ||
            bookmarksCheck.isChecked || quickLinksCheck.isChecked ||
            settingsCheck.isChecked
    }

    private fun performClear() {
        val manager = ClearBrowsingDataManager(this)
        clearButton.isEnabled = false

        val runtime: GeckoRuntime? = try {
            GeckoRuntime.getDefault(this)
        } catch (e: Exception) {
            null
        }

        scope.launch {
            val result = manager.clear(
                clearHistory = historyCheck.isChecked,
                clearDownloads = downloadsCheck.isChecked,
                clearCookies = cookiesCheck.isChecked,
                clearCache = cacheCheck.isChecked,
                clearBookmarks = bookmarksCheck.isChecked,
                clearQuickLinks = quickLinksCheck.isChecked,
                clearSettings = settingsCheck.isChecked,
                geckoRuntime = runtime
            )

            clearButton.isEnabled = true
            val message = if (result.failed.isEmpty()) {
                getString(R.string.clear_success)
            } else {
                getString(R.string.clear_partial, result.failed.joinToString(", "))
            }

            Snackbar.make(clearButton, message, Snackbar.LENGTH_LONG).show()
        }
    }
}
