package com.amiraq.nabd.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.amiraq.nabd.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadsActivity : AppCompatActivity() {

    private lateinit var repository: DownloadRepository
    private lateinit var toolbar: MaterialToolbar
    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var clearButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        repository = DownloadRepository(this)
        toolbar = findViewById(R.id.downloadsToolbar)
        container = findViewById(R.id.downloadsContainer)
        emptyView = findViewById(R.id.emptyDownloads)
        clearButton = findViewById(R.id.clearDownloadsButton)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.menu_downloads)

        clearButton.setOnClickListener {
            repository.clearDownloads()
            refreshList()
        }

        refreshList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshList() {
        container.removeAllViews()

        // Refresh statuses from DownloadManager
        val downloads = repository.getDownloads().map { item ->
            if (item.status == DownloadItem.STATUS_QUEUED || item.status == DownloadItem.STATUS_RUNNING) {
                val result = DownloadStatusMapper.queryStatus(this, item.systemDownloadId)
                if (result != null && result.status != item.status) {
                    val completedAt = if (result.status == DownloadItem.STATUS_SUCCESSFUL) {
                        System.currentTimeMillis()
                    } else null
                    repository.updateStatus(item.systemDownloadId, result.status, completedAt)
                    if (!result.localUri.isNullOrBlank()) {
                        repository.updateDestinationUri(item.systemDownloadId, result.localUri)
                    }
                    item.copy(status = result.status, completedAt = completedAt,
                        destinationUri = result.localUri ?: item.destinationUri)
                } else item
            } else item
        }

        if (downloads.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            clearButton.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        clearButton.visibility = View.VISIBLE

        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val density = resources.displayMetrics.density

        for (item in downloads) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * density).toInt(), (12 * density).toInt(),
                    (12 * density).toInt(), (12 * density).toInt())
            }

            val nameView = TextView(this).apply {
                text = item.fileName.take(50)
                textSize = 14f
            }
            row.addView(nameView)

            val detailView = TextView(this).apply {
                val dateStr = dateFormat.format(Date(item.createdAt))
                text = "$dateStr • ${item.status}"
                textSize = 12f
            }
            row.addView(detailView)

            val urlView = TextView(this).apply {
                text = item.url.take(60)
                textSize = 11f
            }
            row.addView(urlView)

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (8 * density).toInt(), 0, 0)
            }

            if (item.status == DownloadItem.STATUS_SUCCESSFUL) {
                val openBtn = MaterialButton(this).apply {
                    text = getString(R.string.download_open)
                    textSize = 12f
                    setOnClickListener { openFile(item) }
                }
                btnRow.addView(openBtn)
            }

            val removeBtn = MaterialButton(this).apply {
                text = getString(R.string.download_remove)
                textSize = 12f
                setOnClickListener {
                    repository.removeDownload(item.id)
                    refreshList()
                }
            }
            btnRow.addView(removeBtn)
            row.addView(btnRow)

            container.addView(row)
        }
    }

    private fun openFile(item: DownloadItem) {
        val uriStr = item.destinationUri
        if (uriStr.isNullOrBlank()) {
            Snackbar.make(container, R.string.download_file_not_found, Snackbar.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(uriStr)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(container, R.string.download_no_app, Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(container, R.string.download_open_error, Snackbar.LENGTH_SHORT).show()
        }
    }
}
