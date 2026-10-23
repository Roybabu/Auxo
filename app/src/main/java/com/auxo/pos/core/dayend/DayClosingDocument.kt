// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.dayend

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.auxo.pos.core.receipt.ReceiptOutput
import com.auxo.pos.core.receipt.ReceiptPage
import com.auxo.pos.core.receipt.ReceiptPrintAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** One line of the printed summary. A heading line has no value. */
data class SummaryLine(val label: String, val value: String = "", val bold: Boolean = false, val gapBefore: Boolean = false)

/** Everything the printed day-end summary says, already worded and formatted for the reader. */
data class DaySummary(
    val title: String,
    val subtitle: String,
    val lines: List<SummaryLine>,
    val footer: String,
    /** Used for the file and print job name, e.g. "Day-end-2026-10-12". */
    val name: String,
)

/** Draws a [DaySummary] as one A4 page and hands it to print and share. */
object DayClosingDocument {

    private const val WIDTH = 595f // A4 in points
    private const val HEIGHT = 842f
    private const val MARGIN = 48f
    private const val DIR = "receipts"
    private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

    fun page(summary: DaySummary): ReceiptPage = ReceiptPage(WIDTH, HEIGHT) { canvas -> draw(canvas, summary) }

    private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        color = android.graphics.Color.BLACK
    }

    private fun draw(canvas: Canvas, summary: DaySummary) {
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = MARGIN + 16f
        canvas.drawText(summary.title, MARGIN, y, paint(20f, bold = true))
        y += 22f
        canvas.drawText(summary.subtitle, MARGIN, y, paint(11f))
        y += 14f
        val rule = paint(1f)
        canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, rule)
        y += 24f
        for (line in summary.lines) {
            if (line.gapBefore) y += 10f
            val p = paint(12f, line.bold)
            canvas.drawText(line.label, MARGIN, y, p)
            if (line.value.isNotEmpty()) canvas.drawText(line.value, WIDTH - MARGIN - p.measureText(line.value), y, p)
            y += 20f
        }
        y += 8f
        canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, rule)
        y += 20f
        // The footer can be several lines (signature, note); wrap on explicit line breaks only.
        for (row in summary.footer.split('\n')) {
            canvas.drawText(row, MARGIN, y, paint(10f))
            y += 14f
        }
    }

    /** Sends the summary to the Android print system. Needs an Activity context. Returns false if printing could not start. */
    fun print(context: Context, summary: DaySummary): Boolean {
        val printManager = context.getSystemService(PrintManager::class.java) ?: return false
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        return try {
            printManager.print(summary.name, ReceiptPrintAdapter(listOf(page(summary)), fileName(summary)), attributes)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun fileName(summary: DaySummary): String = summary.name.replace(Regex("[^A-Za-z0-9-]"), "-") + ".pdf"

    private fun writeFile(context: Context, summary: DaySummary): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MILLIS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val file = File(dir, fileName(summary))
        FileOutputStream(file).use { ReceiptOutput.writePdf(listOf(page(summary)), it) }
        return file
    }

    /** Writes the PDF off the main thread and opens the share sheet. Returns false if that failed. */
    suspend fun share(context: Context, summary: DaySummary, chooserTitle: String): Boolean {
        val file = runCatching { withContext(Dispatchers.IO) { writeFile(context, summary) } }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return false
        return try {
            val uri = FileProvider.getUriForFile(context, ReceiptOutput.fileProviderAuthority(context), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = ReceiptOutput.PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, summary.title)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
