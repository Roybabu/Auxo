package com.invision.pos.core.printer

import com.invision.pos.domain.model.ReceiptPaper
import java.io.ByteArrayOutputStream

/**
 * A black-and-white picture ready for a thermal printer: [data] holds one bit per dot, eight dots to a byte,
 * the leftmost dot in the highest bit, 1 = black. Every row is [bytesPerRow] bytes, padded with white.
 */
class DotImage(val widthDots: Int, val heightDots: Int, val data: ByteArray) {
    init {
        require(widthDots > 0 && heightDots >= 0) { "Bad size" }
        require(data.size == bytesPerRow * heightDots) { "Data does not match the size" }
    }

    val bytesPerRow: Int get() = (widthDots + 7) / 8

    companion object {
        /** Stacks pictures of the same width, top to bottom. */
        fun stack(parts: List<DotImage>): DotImage {
            require(parts.isNotEmpty()) { "Nothing to stack" }
            val width = parts.first().widthDots
            require(parts.all { it.widthDots == width }) { "Widths differ" }
            val out = ByteArrayOutputStream()
            parts.forEach { out.write(it.data) }
            return DotImage(width, parts.sumOf { it.heightDots }, out.toByteArray())
        }
    }
}

object DotImages {
    /** Pixels lighter than this (0-255, by brightness) print as white. */
    const val THRESHOLD = 170

    /** [argb] holds [width] x [height] pixels in Android's ARGB order. Transparent pixels count as white. */
    fun fromPixels(argb: IntArray, width: Int, height: Int): DotImage {
        require(argb.size >= width * height) { "Too few pixels" }
        val bytesPerRow = (width + 7) / 8
        val data = ByteArray(bytesPerRow * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isBlack(argb[y * width + x])) {
                    val index = y * bytesPerRow + x / 8
                    data[index] = (data[index].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        return DotImage(width, height, data)
    }

    internal fun isBlack(pixel: Int): Boolean {
        val alpha = pixel ushr 24 and 0xFF
        if (alpha < 128) return false
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        // Rec. 601 brightness, in integers.
        val brightness = (r * 299 + g * 587 + b * 114) / 1000
        return brightness < THRESHOLD
    }
}

/** How many dots wide each roll prints (203 dpi printers): 48, 64 and 72 mm printable. */
object PrinterDots {
    fun widthFor(paper: ReceiptPaper): Int? = when (paper) {
        ReceiptPaper.THERMAL_58 -> 384
        ReceiptPaper.THERMAL_76 -> 512
        ReceiptPaper.THERMAL_80 -> 576
        ReceiptPaper.A5, ReceiptPaper.A4 -> null
    }
}

/** The few ESC/POS commands that are needed to print a receipt that was drawn as a picture. */
object EscPos {
    /** Rows sent in one raster command; small enough for printers with little memory. */
    const val BAND_ROWS = 128

    val INIT = byteArrayOf(0x1B, 0x40)

    fun feed(lines: Int): ByteArray = byteArrayOf(0x1B, 0x64, lines.coerceIn(0, 255).toByte())

    /** Partial cut after feeding a little paper. */
    val CUT = byteArrayOf(0x1D, 0x56, 66, 0)

    /** `GS v 0`: print raster bit image, normal density. */
    fun rasterBand(image: DotImage, fromRow: Int, rows: Int): ByteArray {
        require(rows in 1..BAND_ROWS && fromRow >= 0 && fromRow + rows <= image.heightDots) { "Bad band" }
        val bytes = image.bytesPerRow
        val header = byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (bytes and 0xFF).toByte(), (bytes shr 8 and 0xFF).toByte(),
            (rows and 0xFF).toByte(), (rows shr 8 and 0xFF).toByte(),
        )
        return header + image.data.copyOfRange(fromRow * bytes, (fromRow + rows) * bytes)
    }

    /** The whole print job: reset, the picture in bands, some blank paper to tear off at, and optionally a cut. */
    fun job(image: DotImage, feedLines: Int = 4, cut: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT)
        var row = 0
        while (row < image.heightDots) {
            val rows = minOf(BAND_ROWS, image.heightDots - row)
            out.write(rasterBand(image, row, rows))
            row += rows
        }
        out.write(feed(feedLines))
        if (cut) out.write(CUT)
        return out.toByteArray()
    }
}
