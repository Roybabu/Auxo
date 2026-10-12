package com.auxo.pos.core.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.auxo.pos.core.label.LabelSheetLayout
import com.auxo.pos.core.printer.DotImage
import com.auxo.pos.core.printer.DotImages
import com.auxo.pos.core.printer.PrinterDots
import com.auxo.pos.domain.model.ReceiptPaper
import kotlin.math.ceil
import kotlin.math.max

/**
 * Draws a thermal [ReceiptPage] as dots for a receipt printer. The same page drawing is used for the preview, the
 * PDF and here, so what is printed looks like the preview. The roll's own side margins are dropped (the printer
 * has its own), and the page is drawn a strip at a time so a long bill never needs a huge picture in memory.
 */
object ReceiptRaster {
    /** Rows drawn per strip. */
    internal const val STRIP_ROWS = 1024

    /** Null when [paper] is not a thermal roll. */
    fun render(page: ReceiptPage, paper: ReceiptPaper): DotImage? {
        val dots = PrinterDots.widthFor(paper) ?: return null
        val margin = LabelSheetLayout.mmToPoints(paper.spec.marginMm)
        val printable = max(1f, page.widthPt - margin * 2)
        val scale = dots / printable
        val totalRows = max(1, ceil(page.heightPt * scale).toInt())

        val strips = ArrayList<DotImage>()
        var top = 0
        while (top < totalRows) {
            val rows = minOf(STRIP_ROWS, totalRows - top)
            val bitmap = Bitmap.createBitmap(dots, rows, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.translate(0f, -top.toFloat())
                canvas.scale(scale, scale)
                canvas.translate(-margin, 0f)
                page.draw(canvas)
                val pixels = IntArray(dots * rows)
                bitmap.getPixels(pixels, 0, dots, 0, 0, dots, rows)
                strips += DotImages.fromPixels(pixels, dots, rows)
            } finally {
                bitmap.recycle()
            }
            top += rows
        }
        return DotImage.stack(strips)
    }
}
