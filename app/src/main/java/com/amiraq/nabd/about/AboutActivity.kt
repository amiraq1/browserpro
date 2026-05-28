package com.amiraq.nabd.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.amiraq.nabd.BuildConfig
import com.amiraq.nabd.R
import com.amiraq.nabd.share.ShareUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<MaterialToolbar>(R.id.aboutToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)

        val versionText = findViewById<TextView>(R.id.aboutVersion)
        versionText.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        findViewById<MaterialButton>(R.id.aboutOpenRepo).setOnClickListener { openRepository() }
        findViewById<MaterialButton>(R.id.aboutCopyInfo).setOnClickListener { copyAppInfo() }
        findViewById<MaterialButton>(R.id.aboutShareInfo).setOnClickListener { shareAppInfo() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun buildAppInfoText(): String {
        return """
            |${getString(R.string.app_name)} — Nabd Browser
            |Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            |Package: ${BuildConfig.APPLICATION_ID}
            |Engine: Mozilla GeckoView $GECKOVIEW_VERSION
            |Repository: $REPO_URL
        """.trimMargin()
    }

    private fun openRepository() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(findViewById(R.id.aboutOpenRepo), R.string.about_no_browser, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun copyAppInfo() {
        ShareUtils.copyToClipboard(this, "Nabd Info", buildAppInfoText())
        Snackbar.make(findViewById(R.id.aboutCopyInfo), R.string.link_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun shareAppInfo() {
        ShareUtils.shareText(this, getString(R.string.app_name), buildAppInfoText())
    }

    companion object {
        private const val REPO_URL = "https://github.com/amiraq1/browserpro"
        private const val GECKOVIEW_VERSION = "126.0"
    }
}
