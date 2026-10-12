package com.auxo.pos.core.receipt

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.auxo.pos.ui.components.QrEncoder

/** Draws a QR code on a receipt page. The code always has its own white quiet zone, so it scans on any paper. */
internal object QrDrawing {
    private val dark = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val light = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

    /** Draws [content] in a [side] by [side] square at [left], [top]; returns false if it cannot be encoded. */
    fun draw(canvas: Canvas, content: String, left: Float, top: Float, side: Float): Boolean {
        val matrix = QrEncoder.encode(content) ?: return false
        val cell = side / matrix.width
        canvas.drawRect(left, top, left + side, top + side, light)
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (matrix.get(column, row)) {
                    canvas.drawRect(left + column * cell, top + row * cell, left + (column + 1) * cell, top + (row + 1) * cell, dark)
                }
            }
        }
        return true
    }
}
