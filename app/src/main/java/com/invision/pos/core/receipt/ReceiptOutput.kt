package com.invision.pos.core.receipt

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes receipts as PDF, renders previews, and hands them to print and share. */
object ReceiptOutput {

    private const val RECEIPT_DIR = "receipts"
    private const val STALE_AFTER_MILLIS = 24 * 60 * 60 * 1000L
    const val PDF_MIME_TYPE = "application/pdf"

    fun writePdf(pages: List<ReceiptPage>, output: OutputStream) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(page.widthPt.roundToInt(), page.heightPt.roundToInt(), index + 1).create()
                val pdfPage = document.startPage(info)
                page.draw(pdfPage.canvas)
                document.finishPage(pdfPage)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    /** Renders [page] to a bitmap [widthPx] wide, for on-screen preview. */
    fun previewBitmap(page: ReceiptPage, widthPx: Int): Bitmap {
        val scale = widthPx / page.widthPt
        val bitmap = createBitmap(widthPx, (page.heightPt * scale).roundToInt().coerceAtLeast(1))
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        page.draw(canvas)
        return bitmap
    }

    /** File name such as "INV-26-27-00001.pdf". */
    fun fileName(invoice: Invoice): String =
        invoice.number.ifEmpty { "receipt" }.replace(Regex("[^A-Za-z0-9-]"), "-") + ".pdf"

    /**
     * Writes the receipt PDF into the app's cache and returns the file. Receipts
     * written more than a day ago are cleared out first.
     */
    fun writePdfFile(context: Context, invoice: Invoice, options: ReceiptOptions, zone: ZoneId = ZoneId.systemDefault()): File {
        val dir = File(context.cacheDir, RECEIPT_DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MILLIS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val file = File(dir, fileName(invoice))
        FileOutputStream(file).use { writePdf(ReceiptRenderer.pages(invoice, options, zone), it) }
        return file
    }

    /** Authority of the FileProvider declared in the manifest. */
    fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

    /** Share sheet for a receipt PDF written by [writePdfFile], with read access for the receiving app. */
    fun shareIntent(context: Context, file: File, invoice: Invoice, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${invoice.seller.name} ${invoice.number}")
            clipData = ClipData.newRawUri(invoice.number, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle)
    }

    /**
     * Writes the PDF off the main thread and opens the share sheet. Returns false
     * if the file couldn't be written or no app can receive it.
     */
    suspend fun share(context: Context, invoice: Invoice, options: ReceiptOptions, chooserTitle: String): Boolean {
        val file = try {
            withContext(Dispatchers.IO) { writePdfFile(context, invoice, options) }
        } catch (e: IOException) {
            return false
        }
        return try {
            context.startActivity(shareIntent(context, file, invoice, chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            // The file is outside the paths the FileProvider shares.
            false
        }
    }

    /**
     * Sends the receipt to the Android print system. Must be called with an
     * Activity context. Returns false if printing couldn't be started.
     */
    fun print(context: Context, invoice: Invoice, options: ReceiptOptions): Boolean {
        val printManager = context.getSystemService(PrintManager::class.java) ?: return false
        val pages = ReceiptRenderer.pages(invoice, options, ZoneId.systemDefault())
        val first = pages.first()
        val mediaSize = when (options.paper) {
            ReceiptPaper.A4 -> PrintAttributes.MediaSize.ISO_A4
            ReceiptPaper.A5 -> PrintAttributes.MediaSize.ISO_A5
            else -> PrintAttributes.MediaSize(
                "invision_${options.paper.name.lowercase()}",
                "${options.paper.spec.paperWidthMm.roundToInt()} mm roll",
                pointsToMils(first.widthPt),
                pointsToMils(first.heightPt),
            )
        }
        val attributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        return try {
            printManager.print(invoice.number.ifEmpty { "Receipt" }, ReceiptPrintAdapter(pages, fileName(invoice)), attributes)
            true
        } catch (e: IllegalStateException) {
            // For example when the screen is closing.
            false
        }
    }

    private fun pointsToMils(points: Float): Int = (points / 72f * 1000f).roundToInt()
}

private class ReceiptPrintAdapter(
    private val pages: List<ReceiptPage>,
    private val fileName: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(fileName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages.size)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        ranges: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            FileOutputStream(destination.fileDescriptor).use { ReceiptOutput.writePdf(pages, it) }
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
            } else {
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        }
    }
}
