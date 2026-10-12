package com.auxo.pos.core.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import com.auxo.pos.core.barcode.BarcodeEncoder

/**
 * Draws a label on an Android [Canvas]. The same code renders the on-screen
 * preview (bitmap) and the printed PDF, so what you see is what prints.
 */
object LabelRenderer {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val barPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    /** Draws [content] inside the rectangle at ([left], [top]) of size [width] x [height] canvas units. */
    fun draw(canvas: Canvas, content: LabelContent, left: Float, top: Float, width: Float, height: Float) {
        val padding = minOf(width, height) * 0.06f
        val innerLeft = left + padding
        val innerWidth = width - padding * 2
        var y = top + padding

        // Title
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = height * 0.12f
        y += textPaint.textSize
        canvas.drawText(fit(content.title, innerWidth), innerLeft, y, textPaint)

        // Variant line
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = height * 0.095f
        content.subtitle?.takeIf { it.isNotBlank() }?.let {
            y += textPaint.textSize * 1.15f
            canvas.drawText(fit(it, innerWidth), innerLeft, y, textPaint)
        }

        // Price, with MRP to the right
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = height * 0.13f
        y += textPaint.textSize * 1.15f
        canvas.drawText(content.price, innerLeft, y, textPaint)
        content.mrp?.let { mrp ->
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = height * 0.085f
            val mrpText = "MRP $mrp"
            val mrpWidth = textPaint.measureText(mrpText)
            canvas.drawText(mrpText, innerLeft + innerWidth - mrpWidth, y, textPaint)
        }

        // Barcode fills the remaining height, leaving room for the digits underneath.
        val digitsSize = height * 0.08f
        val barTop = y + padding * 0.8f
        val barBottom = top + height - padding - digitsSize * 1.2f
        drawBarcode(canvas, content.barcode, innerLeft, barTop, innerWidth, barBottom - barTop)

        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = digitsSize
        val digitsWidth = textPaint.measureText(content.barcode)
        canvas.drawText(content.barcode, innerLeft + (innerWidth - digitsWidth) / 2f, top + height - padding, textPaint)
    }

    /** Draws only the bars, centred in the given area. Does nothing for invalid values. */
    fun drawBarcode(canvas: Canvas, value: String, left: Float, top: Float, width: Float, height: Float) {
        if (height <= 0f || width <= 0f) return
        val pattern = BarcodeEncoder.encode(value) ?: return
        val totalModules = pattern.modules.size + pattern.quietZoneModules * 2
        val moduleWidth = width / totalModules
        var x = left + pattern.quietZoneModules * moduleWidth
        var index = 0
        while (index < pattern.modules.size) {
            if (pattern.modules[index]) {
                var run = 1
                while (index + run < pattern.modules.size && pattern.modules[index + run]) run++
                canvas.drawRect(x, top, x + run * moduleWidth, top + height, barPaint)
                x += run * moduleWidth
                index += run
            } else {
                x += moduleWidth
                index++
            }
        }
    }

    /** Renders one label to a bitmap for previews. */
    fun renderBitmap(content: LabelContent, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        draw(canvas, content, 0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        return bitmap
    }

    private fun fit(text: String, maxWidth: Float): String =
        TextUtils.ellipsize(text, textPaint, maxWidth, TextUtils.TruncateAt.END).toString()
}
