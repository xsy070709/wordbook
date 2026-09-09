package com.wordbook.app.printing

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import com.wordbook.app.data.SavedWord
import java.io.FileOutputStream

enum class PrintContent { WORDS_ONLY, WORDS_AND_TRANSLATIONS }

class WordPrintAdapter(
    private val context: Context,
    private val title: String,
    private val words: List<SavedWord>,
    private val content: PrintContent,
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("$title.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build(),
            oldAttributes != newAttributes,
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val printAttributes = attributes ?: run {
            callback.onWriteFailed("缺少打印页面设置")
            return
        }
        val document = PrintedPdfDocument(context, printAttributes)
        try {
            var wordIndex = 0
            var pageNumber = 0
            while (wordIndex < words.size && !cancellationSignal.isCanceled) {
                val firstWordOnPage = wordIndex
                val page = document.startPage(pageNumber)
                val canvas = page.canvas
                val rect = page.info.contentRect
                val left = rect.left.toFloat()
                val right = rect.right.toFloat()
                var y = rect.top + 32f

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = if (content == PrintContent.WORDS_ONLY) 15f else 13f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val translationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 11f
                }
                val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.LTGRAY
                    strokeWidth = 1f
                }

                canvas.drawText(title, left, y, titlePaint)
                y += 28f
                canvas.drawLine(left, y, right, y, rulePaint)
                y += 22f

                if (content == PrintContent.WORDS_ONLY) {
                    val columnGap = 24f
                    val columnWidth = (right - left - columnGap) / 2f
                    val rowHeight = 28f
                    while (wordIndex < words.size && y + rowHeight <= rect.bottom) {
                        val first = words[wordIndex++]
                        canvas.drawText(ellipsize(first.word, wordPaint, columnWidth), left, y, wordPaint)
                        if (wordIndex < words.size) {
                            val second = words[wordIndex++]
                            canvas.drawText(
                                ellipsize(second.word, wordPaint, columnWidth),
                                left + columnWidth + columnGap,
                                y,
                                wordPaint,
                            )
                        }
                        y += rowHeight
                    }
                } else {
                    while (wordIndex < words.size) {
                        val item = words[wordIndex]
                        val translationLines = wrap(item.translation.replace('\n', ' '), translationPaint, right - left - 16f, 2)
                        val rowHeight = 25f + translationLines.size * 16f + 10f
                        if (y + rowHeight > rect.bottom) break
                        canvas.drawText(ellipsize(item.word, wordPaint, right - left), left, y, wordPaint)
                        y += 19f
                        translationLines.forEach { line ->
                            canvas.drawText(line, left + 16f, y, translationPaint)
                            y += 16f
                        }
                        y += 5f
                        canvas.drawLine(left, y, right, y, rulePaint)
                        y += 10f
                        wordIndex++
                    }
                }
                if (wordIndex == firstWordOnPage) {
                    val item = words[wordIndex++]
                    canvas.drawText(ellipsize(item.word, wordPaint, right - left), left, y, wordPaint)
                }
                document.finishPage(page)
                pageNumber++
            }
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
            } else {
                FileOutputStream(destination.fileDescriptor).use(document::writeTo)
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        } catch (error: Exception) {
            callback.onWriteFailed(error.message ?: "打印文档生成失败")
        } finally {
            document.close()
        }
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        val suffix = "…"
        val count = paint.breakText(text, true, width - paint.measureText(suffix), null)
        return text.take(count) + suffix
    }

    private fun wrap(text: String, paint: Paint, width: Float, maxLines: Int): List<String> {
        var remaining = text.trim()
        val lines = mutableListOf<String>()
        while (remaining.isNotEmpty() && lines.size < maxLines) {
            val count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
            val lastLine = lines.size == maxLines - 1 && count < remaining.length
            lines += if (lastLine) ellipsize(remaining, paint, width) else remaining.take(count).trimEnd()
            remaining = remaining.drop(count).trimStart()
        }
        return lines.ifEmpty { listOf("") }
    }
}
