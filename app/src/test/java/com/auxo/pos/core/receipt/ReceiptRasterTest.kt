// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.receipt

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.label.LabelSheetLayout
import com.auxo.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Real pixels are needed to check what was drawn.
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ReceiptRasterTest {

    private fun bit(image: com.auxo.pos.core.printer.DotImage, x: Int, y: Int): Boolean =
        (image.data[y * image.bytesPerRow + x / 8].toInt() and (0x80 shr (x % 8))) != 0

    /** A page as wide as the roll, [heightPt] tall, with a black box over the whole printable width. */
    private fun page(paper: ReceiptPaper, heightPt: Float, draw: (android.graphics.Canvas, Float, Float) -> Unit): ReceiptPage {
        val width = LabelSheetLayout.mmToPoints(paper.spec.paperWidthMm)
        val margin = LabelSheetLayout.mmToPoints(paper.spec.marginMm)
        return ReceiptPage(width, heightPt) { canvas -> draw(canvas, margin, width) }
    }

    @Test
    fun theImageIsAsWideAsThePrinter_forEachRoll() {
        for ((paper, dots) in listOf(ReceiptPaper.THERMAL_58 to 384, ReceiptPaper.THERMAL_76 to 512, ReceiptPaper.THERMAL_80 to 576)) {
            val image = ReceiptRaster.render(page(paper, 40f) { _, _, _ -> }, paper)
            assertNotNull(image)
            assertEquals(dots, image!!.widthDots)
        }
    }

    @Test
    fun aSheetIsNotRastered() {
        assertNull(ReceiptRaster.render(ReceiptPage(595f, 842f) {}, ReceiptPaper.A4))
    }

    @Test
    fun aBlankPage_printsNothing() {
        val image = ReceiptRaster.render(page(ReceiptPaper.THERMAL_58, 40f) { _, _, _ -> }, ReceiptPaper.THERMAL_58)!!
        assertTrue(image.data.all { it.toInt() == 0 })
    }

    @Test
    fun whatIsDrawnInTheTextArea_fillsThePrinterWidth_andTheSideMarginsAreDropped() {
        val paper = ReceiptPaper.THERMAL_58
        val paint = Paint().apply { color = android.graphics.Color.BLACK }
        // Black from the left edge of the printable area to its right edge, 10 pt tall at the top.
        val image = ReceiptRaster.render(
            page(paper, 40f) { canvas, margin, width -> canvas.drawRect(margin, 0f, width - margin, 10f, paint) },
            paper,
        )!!
        assertTrue(bit(image, 2, 2))
        assertTrue(bit(image, image.widthDots / 2, 2))
        assertTrue(bit(image, image.widthDots - 3, 2))
        // Below the box nothing is printed.
        assertTrue(!bit(image, image.widthDots / 2, image.heightDots - 2))
    }

    @Test
    fun aVeryLongBill_isDrawnInStrips_withoutLosingRows() {
        val paper = ReceiptPaper.THERMAL_58
        val paint = Paint().apply { color = android.graphics.Color.BLACK }
        // Tall enough for several strips; a black bar near the bottom must survive the stitching.
        val heightPt = 1500f
        val image = ReceiptRaster.render(
            page(paper, heightPt) { canvas, margin, width -> canvas.drawRect(margin, heightPt - 20f, width - margin, heightPt - 10f, paint) },
            paper,
        )!!
        assertTrue(image.heightDots > ReceiptRaster.STRIP_ROWS * 2)
        val scale = 384f / (LabelSheetLayout.mmToPoints(58f) - LabelSheetLayout.mmToPoints(5f) * 2)
        val barRow = ((heightPt - 15f) * scale).toInt()
        assertTrue(bit(image, 100, barRow))
        assertTrue(!bit(image, 100, 10))
    }

    @Test
    fun aRealReceipt_hasSomeBlackAndSomeWhite() {
        val sale = com.auxo.pos.domain.billing.SampleSales.shirtsAndBiscuits()
        val options = com.auxo.pos.domain.model.ReceiptOptions(paper = ReceiptPaper.THERMAL_58)
        val page = ReceiptRenderer.pages(sale, options, java.time.ZoneOffset.UTC).single()
        val image = ReceiptRaster.render(page, ReceiptPaper.THERMAL_58)!!
        val black = image.data.count { it.toInt() != 0 }
        assertTrue(black > 100)
        assertTrue(black < image.data.size)
    }
}
