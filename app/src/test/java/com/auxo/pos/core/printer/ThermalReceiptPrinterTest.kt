// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.receipt.spec
import com.auxo.pos.ui.screens.more.messageRes
import com.auxo.pos.domain.billing.SampleSales
import com.auxo.pos.domain.model.Language
import com.auxo.pos.domain.model.ReceiptOptions
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.testutil.FakePrinterPreferences
import com.auxo.pos.testutil.FakePrinterTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThermalReceiptPrinterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sale = SampleSales.shirtsAndBiscuits()
    private val printer58 = SavedPrinter("00:11:22:33:44:55", "RPP02N", ReceiptPaper.THERMAL_58)
    private val transport = FakePrinterTransport()

    private fun service(printer: SavedPrinter?) = ThermalReceiptPrinter(context, FakePrinterPreferences(printer), transport)

    /** Width in bytes of the first raster command in [bytes]. */
    private fun firstRasterWidthBytes(bytes: ByteArray): Int {
        val i = (0 until bytes.size - 7).first { bytes[it] == 0x1D.toByte() && bytes[it + 1] == 0x76.toByte() && bytes[it + 2] == 0x30.toByte() }
        return (bytes[i + 4].toInt() and 0xFF) or ((bytes[i + 5].toInt() and 0xFF) shl 8)
    }

    @Test
    fun withoutAPrinter_nothingIsSent() = runTest {
        assertEquals(PrintResult.NO_PRINTER, service(null).printInvoice(sale, ReceiptOptions()))
        assertEquals(PrintResult.NO_PRINTER, service(null).printTest("Shop", Language.DEFAULT))
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun anInvoice_isSentToTheChosenPrinter_asEscPosRaster() = runTest {
        assertEquals(PrintResult.PRINTED, service(printer58).printInvoice(sale, ReceiptOptions()))
        val (address, bytes) = transport.sent.single()
        assertEquals("00:11:22:33:44:55", address)
        assertArrayEquals(EscPos.INIT, bytes.copyOfRange(0, 2))
        assertEquals(48, firstRasterWidthBytes(bytes))
    }

    @Test
    fun thePrintersOwnPaperDecidesTheWidth_notTheInvoiceScreensPaper() = runTest {
        // The invoice screen may be previewing A4; the printer is a 80 mm one.
        val printer80 = printer58.copy(paper = ReceiptPaper.THERMAL_80)
        service(printer80).printInvoice(sale, ReceiptOptions(paper = ReceiptPaper.A4))
        assertEquals(72, firstRasterWidthBytes(transport.sent.single().second))
    }

    @Test
    fun theCutCommand_isSentOnlyWhenTheShopTurnedItOn() = runTest {
        service(printer58).printInvoice(sale, ReceiptOptions())
        service(printer58.copy(cutPaper = true)).printInvoice(sale, ReceiptOptions())
        val (plain, cut) = transport.sent.map { it.second }
        assertTrue(!plain.takeLast(4).toByteArray().contentEquals(EscPos.CUT))
        assertArrayEquals(EscPos.CUT, cut.copyOfRange(cut.size - 4, cut.size))
    }

    @Test
    fun everyFailureTheTransportReports_isPassedOn() = runTest {
        for (result in PrintResult.entries) {
            transport.result = result
            assertEquals(result, service(printer58).printInvoice(sale, ReceiptOptions()))
        }
    }

    @Test
    fun aReceiptThatCannotBeDrawn_isRenderFailed_nothingIsSent_andDoesNotCrash() = runTest {
        val broken = ThermalReceiptPrinter(context, FakePrinterPreferences(printer58), transport, makeJob = { _, _ -> throw IllegalStateException("bitmap") })
        assertEquals(PrintResult.RENDER_FAILED, broken.printInvoice(sale, ReceiptOptions()))
        assertEquals(PrintResult.RENDER_FAILED, broken.printTest("Shop", Language.DEFAULT))
        val noMemory = ThermalReceiptPrinter(context, FakePrinterPreferences(printer58), transport, makeJob = { _, _ -> throw OutOfMemoryError() })
        assertEquals(PrintResult.RENDER_FAILED, noMemory.printInvoice(sale, ReceiptOptions()))
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun everyPrintResult_hasAMessageThePersonCanRead() {
        for (result in PrintResult.entries) {
            assertTrue("$result", context.getString(result.messageRes()).isNotBlank())
        }
    }

    @Test
    fun aTestPrint_goesToThePrinter_andIsShorterThanABill() = runTest {
        assertEquals(PrintResult.PRINTED, service(printer58).printTest("Sample Mart", Language.DEFAULT))
        assertEquals(PrintResult.PRINTED, service(printer58).printInvoice(sale, ReceiptOptions()))
        val (test, bill) = transport.sent.map { it.second }
        assertEquals(48, firstRasterWidthBytes(test))
        assertTrue(test.size < bill.size)
    }

    @Test
    fun testReceipt_hasARulerAsWideAsTheLine_forEachRoll() {
        for (paper in listOf(ReceiptPaper.THERMAL_58, ReceiptPaper.THERMAL_76, ReceiptPaper.THERMAL_80)) {
            val receipt = ThermalReceiptPrinter.testReceipt(context, "Sample Mart", printer58.copy(paper = paper))
            val width = paper.spec.charsPerLine
            assertEquals(width, receipt.width)
            val ruler = receipt.elements.filterIsInstance<com.auxo.pos.core.receipt.ThermalElement.Line>().map { it.text }.first { it.startsWith("1234567890") }
            assertEquals(width, ruler.length)
            assertTrue(receipt.elements.filterIsInstance<com.auxo.pos.core.receipt.ThermalElement.Line>().any { it.text == "$width characters per line" })
        }
    }

    @Test
    fun ruler_countsUpInTens() {
        assertEquals("12345678901234567890123456789012", ThermalReceiptPrinter.ruler(32))
    }
}
