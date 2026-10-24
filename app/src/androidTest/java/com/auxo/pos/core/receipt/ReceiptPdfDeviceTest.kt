// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.DeviceFixtures
import com.auxo.pos.domain.model.ReceiptOptions
import com.auxo.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Receipt PDFs written with Android's PdfDocument and read back with PdfRenderer.
 * These need a real Android system, so they run on an emulator rather than Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptPdfDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sale = DeviceFixtures.sale()

    private fun points(mm: Float): Int = (mm / 25.4f * 72f).roundToInt()

    /** Opens [file] with PdfRenderer and passes it to [block]. */
    private fun <T> readPdf(file: File, block: (PdfRenderer) -> T): T {
        val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        try {
            return block(renderer)
        } finally {
            renderer.close()
        }
    }

    /** Share of dark pixels on a page rendered on white. */
    private fun ink(renderer: PdfRenderer, index: Int): Double {
        val page = renderer.openPage(index)
        try {
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val dark = pixels.count { (Color.red(it) + Color.green(it) + Color.blue(it)) / 3 < 128 }
            return dark.toDouble() / pixels.size
        } finally {
            page.close()
        }
    }

    @Test
    fun everyPaperSize_writesAPdfThatOpens() {
        for (paper in ReceiptPaper.entries) {
            val options = ReceiptOptions(paper = paper)
            val expectedPages = ReceiptRenderer.pages(sale, options, ZoneOffset.UTC).size
            val file = ReceiptOutput.writePdfFile(context, sale, options, ZoneOffset.UTC)
            assertEquals("INV-26-27-00007.pdf", file.name)
            assertEquals("%PDF-", file.inputStream().use { String(ByteArray(5).also { bytes -> it.read(bytes) }) })

            readPdf(file) { pdf ->
                assertEquals("$paper pages", expectedPages, pdf.pageCount)
                val page = pdf.openPage(0)
                try {
                    assertEquals("$paper width", points(paper.spec.paperWidthMm), page.width)
                    paper.spec.paperHeightMm?.let { assertEquals("$paper height", points(it), page.height) }
                    // Thermal receipts are as long as their content.
                    if (paper.isThermal) assertTrue("$paper is too short", page.height > page.width)
                } finally {
                    page.close()
                }
                assertTrue("$paper page is blank", ink(pdf, 0) > 0.001)
            }
        }
    }

    @Test
    fun longInvoice_continuesOnMorePages() {
        val long = DeviceFixtures.sale(lines = 60)
        for (paper in listOf(ReceiptPaper.A5, ReceiptPaper.A4)) {
            val options = ReceiptOptions(paper = paper)
            val expectedPages = ReceiptRenderer.pages(long, options, ZoneOffset.UTC).size
            assertTrue("$paper should need more than one page", expectedPages > 1)
            val file = ReceiptOutput.writePdfFile(context, long, options, ZoneOffset.UTC)
            readPdf(file) { pdf ->
                assertEquals("$paper pages", expectedPages, pdf.pageCount)
                for (index in 0 until pdf.pageCount) {
                    assertTrue("$paper page ${index + 1} is blank", ink(pdf, index) > 0.001)
                }
            }
        }
    }

    @Test
    fun shareIntent_letsTheReceivingAppReadThePdf() {
        val file = ReceiptOutput.writePdfFile(context, sale, ReceiptOptions(), ZoneOffset.UTC)
        val chooser = ReceiptOutput.shareIntent(context, file, sale, "Share receipt")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)

        val send = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)) { "no share intent" }
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(ReceiptOutput.PDF_MIME_TYPE, send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("Sample Mart INV/26-27/00007", send.getStringExtra(Intent.EXTRA_SUBJECT))

        val uri = requireNotNull(IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)) { "no PDF attached" }
        assertEquals("content", uri.scheme)
        assertEquals(ReceiptOutput.fileProviderAuthority(context), uri.authority)
        assertEquals(uri, send.clipData?.getItemAt(0)?.uri)

        // What another app reads through the content URI is the PDF file.
        val resolver = context.contentResolver
        assertEquals(ReceiptOutput.PDF_MIME_TYPE, resolver.getType(uri))
        val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
        assertEquals(file.length(), bytes.size.toLong())
        assertEquals("%PDF-", String(bytes, 0, 5))
    }

    @Test
    fun oldReceipts_areClearedFromTheCache() {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val stale = File(dir, "OLD-RECEIPT.pdf").apply {
            writeText("old")
            setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L)
        }
        val fresh = ReceiptOutput.writePdfFile(context, sale, ReceiptOptions(), ZoneOffset.UTC)
        assertFalse(stale.exists())
        assertTrue(fresh.exists())
    }
}
