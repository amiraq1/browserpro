package com.amiraq.nabd.print

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import com.amiraq.nabd.reader.ReaderArticle
import java.io.FileOutputStream

/**
 * PrintDocumentAdapter that renders a ReaderArticle as a multi-page PDF
 * using Android's PdfDocument and Canvas text drawing.
 */
class ReaderPrintDocumentAdapter(
    private val article: ReaderArticle
) : PrintDocumentAdapter() {

    private var pageWidth = 595  // A4 in points at 72dpi
    private var pageHeight = 842

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val mediaSize = newAttributes.mediaSize
        if (mediaSize != null) {
            pageWidth = (mediaSize.widthMils * 72 / 1000)
            pageHeight = (mediaSize.heightMils * 72 / 1000)
        }

        val info = PrintDocumentInfo.Builder("nabd_reader_article.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }

        try {
            val document = PdfDocument()
            val marginH = 50f
            val marginV = 60f
            val usableWidth = pageWidth - (marginH * 2)

            val titlePaint = Paint().apply { textSize = 18f; isAntiAlias = true; isFakeBoldText = true }
            val metaPaint = Paint().apply { textSize = 11f; isAntiAlias = true; color = 0xFF666666.toInt() }
            val bodyPaint = Paint().apply { textSize = 12f; isAntiAlias = true }
            val lineSpacing = 18f
            val titleSpacing = 24f

            // Prepare text lines
            val allLines = mutableListOf<Pair<String, Paint>>()
            wrapText(article.title, titlePaint, usableWidth).forEach { allLines.add(it to titlePaint) }
            allLines.add("" to metaPaint) // spacer

            if (!article.byline.isNullOrBlank()) {
                allLines.add(article.byline to metaPaint)
            }
            allLines.add(article.url to metaPaint)
            allLines.add("" to bodyPaint) // spacer
            allLines.add("" to bodyPaint) // spacer

            val contentLines = article.content.split("\n")
            for (paragraph in contentLines) {
                if (paragraph.isBlank()) {
                    allLines.add("" to bodyPaint)
                } else {
                    wrapText(paragraph, bodyPaint, usableWidth).forEach { allLines.add(it to bodyPaint) }
                }
            }

            // Render pages
            var lineIndex = 0
            var pageNum = 1

            while (lineIndex < allLines.size) {
                if (cancellationSignal?.isCanceled == true) {
                    document.close()
                    callback.onWriteCancelled()
                    return
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val page = document.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                var y = marginV + lineSpacing

                while (lineIndex < allLines.size && y + lineSpacing < pageHeight - marginV) {
                    val (text, paint) = allLines[lineIndex]
                    if (text.isNotEmpty()) {
                        canvas.drawText(text, marginH, y, paint)
                    }
                    y += if (paint === titlePaint) titleSpacing else lineSpacing
                    lineIndex++
                }

                document.finishPage(page)
                pageNum++
            }

            // Write to output
            val fos = FileOutputStream(destination.fileDescriptor)
            document.writeTo(fos)
            document.close()
            fos.close()

            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            Log.e("ReaderPrint", "Print write failed", e)
            callback.onWriteFailed(e.message)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) {
                current = StringBuilder(test)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        if (lines.isEmpty()) lines.add("")
        return lines
    }
}
