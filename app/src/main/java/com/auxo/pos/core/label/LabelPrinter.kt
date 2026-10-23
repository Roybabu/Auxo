package com.auxo.pos.core.label

import com.auxo.pos.core.debuglog.DebugLog
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/** Sends labels to the Android print system as a PDF laid out for [LabelTemplate]. */
object LabelPrinter {

    /** Must be called with an Activity context. */
    fun print(context: Context, template: LabelTemplate, labels: List<LabelContent>, jobName: String) {
        val printManager = context.getSystemService(PrintManager::class.java) ?: return
        val attributes = PrintAttributes.Builder()
            .setMediaSize(mediaSizeFor(template))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        DebugLog.event("Printer", "Label print started: ${labels.size} labels")
        printManager.print(jobName, LabelPrintAdapter(template, labels), attributes)
    }

    private fun mediaSizeFor(template: LabelTemplate): PrintAttributes.MediaSize =
        if (template == LabelTemplate.A4_24) {
            PrintAttributes.MediaSize.ISO_A4
        } else {
            PrintAttributes.MediaSize(
                "auxo_${template.id}",
                "${template.pageWidthMm.roundToInt()} x ${template.pageHeightMm.roundToInt()} mm",
                mmToMils(template.pageWidthMm),
                mmToMils(template.pageHeightMm),
            )
        }

    private fun mmToMils(mm: Float): Int = (mm / 25.4f * 1000f).roundToInt()
}

private class LabelPrintAdapter(
    private val template: LabelTemplate,
    private val labels: List<LabelContent>,
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
        val info = PrintDocumentInfo.Builder("auxo-labels.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(LabelSheetLayout.pageCount(template, labels.size))
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val document = PdfDocument()
        try {
            val pageWidth = LabelSheetLayout.mmToPoints(template.pageWidthMm).roundToInt()
            val pageHeight = LabelSheetLayout.mmToPoints(template.pageHeightMm).roundToInt()
            val slotsByPage = LabelSheetLayout.slots(template, labels.size)
                .withIndex()
                .groupBy { it.value.pageIndex }
            val labelWidth = LabelSheetLayout.mmToPoints(template.labelWidthMm)
            val labelHeight = LabelSheetLayout.mmToPoints(template.labelHeightMm)

            for (pageIndex in 0 until LabelSheetLayout.pageCount(template, labels.size)) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                slotsByPage[pageIndex].orEmpty().forEach { (labelIndex, slot) ->
                    LabelRenderer.draw(
                        canvas = page.canvas,
                        content = labels[labelIndex],
                        left = LabelSheetLayout.mmToPoints(slot.leftMm),
                        top = LabelSheetLayout.mmToPoints(slot.topMm),
                        width = labelWidth,
                        height = labelHeight,
                    )
                }
                document.finishPage(page)
            }

            FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        } finally {
            document.close()
        }
    }
}
