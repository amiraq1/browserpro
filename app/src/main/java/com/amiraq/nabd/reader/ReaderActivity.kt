package com.amiraq.nabd.reader

import android.os.Bundle
import android.print.PrintManager
import android.util.TypedValue
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.amiraq.nabd.R
import com.amiraq.nabd.print.ReaderPrintDocumentAdapter
import com.amiraq.nabd.share.ShareUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class ReaderActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleView: TextView
    private lateinit var bylineView: TextView
    private lateinit var urlView: TextView
    private lateinit var contentView: TextView
    private lateinit var decreaseBtn: MaterialButton
    private lateinit var increaseBtn: MaterialButton
    private lateinit var shareBtn: MaterialButton
    private lateinit var copyLinkBtn: MaterialButton
    private lateinit var printBtn: MaterialButton
    private lateinit var readerPrefs: ReaderPreferences

    private var currentTextSize = ReaderPreferences.DEFAULT_TEXT_SIZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        readerPrefs = ReaderPreferences(this)
        currentTextSize = readerPrefs.getReaderTextSize()

        toolbar = findViewById(R.id.readerToolbar)
        titleView = findViewById(R.id.readerTitle)
        bylineView = findViewById(R.id.readerByline)
        urlView = findViewById(R.id.readerUrl)
        contentView = findViewById(R.id.readerContent)
        decreaseBtn = findViewById(R.id.readerDecreaseText)
        increaseBtn = findViewById(R.id.readerIncreaseText)
        shareBtn = findViewById(R.id.readerShareBtn)
        copyLinkBtn = findViewById(R.id.readerCopyLinkBtn)
        printBtn = findViewById(R.id.readerPrintBtn)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.reader_mode)

        decreaseBtn.setOnClickListener { changeTextSize(-ReaderPreferences.TEXT_SIZE_STEP) }
        increaseBtn.setOnClickListener { changeTextSize(ReaderPreferences.TEXT_SIZE_STEP) }
        shareBtn.setOnClickListener { shareArticle() }
        copyLinkBtn.setOnClickListener { copyArticleLink() }
        printBtn.setOnClickListener { printArticle() }

        loadArticle()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun loadArticle() {
        val article = ReaderArticleStore.currentArticle
        if (article == null) {
            titleView.text = getString(R.string.reader_no_content)
            contentView.text = ""
            return
        }

        titleView.text = article.title
        urlView.text = article.url

        if (!article.byline.isNullOrBlank()) {
            bylineView.text = article.byline
            bylineView.visibility = android.view.View.VISIBLE
        } else {
            bylineView.visibility = android.view.View.GONE
        }

        contentView.text = article.content
        applyTextSize()
    }

    private fun changeTextSize(delta: Float) {
        currentTextSize = (currentTextSize + delta).coerceIn(
            ReaderPreferences.MIN_TEXT_SIZE,
            ReaderPreferences.MAX_TEXT_SIZE
        )
        readerPrefs.setReaderTextSize(currentTextSize)
        applyTextSize()
    }

    private fun applyTextSize() {
        contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
    }

    private fun shareArticle() {
        val article = ReaderArticleStore.currentArticle ?: return
        val excerpt = article.content.take(200).let { if (article.content.length > 200) "$it…" else it }
        val text = "${article.title}\n${article.url}\n\n$excerpt"
        ShareUtils.shareText(this, article.title, text)
    }

    private fun copyArticleLink() {
        val article = ReaderArticleStore.currentArticle ?: return
        if (article.url.isBlank()) return
        ShareUtils.copyToClipboard(this, "URL", article.url)
        Snackbar.make(contentView, R.string.link_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun printArticle() {
        val article = ReaderArticleStore.currentArticle
        if (article == null || article.content.isBlank()) {
            Snackbar.make(contentView, R.string.reader_no_content, Snackbar.LENGTH_SHORT).show()
            return
        }
        val printManager = getSystemService(PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Snackbar.make(contentView, R.string.print_not_available, Snackbar.LENGTH_SHORT).show()
            return
        }
        val jobName = "Nabd - ${article.title.take(40)}"
        printManager.print(jobName, ReaderPrintDocumentAdapter(article), null)
    }
}
