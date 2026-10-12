package com.invision.pos.core.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.receipt.spec
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.testutil.FakePrinterPreferences
import com.invision.pos.testutil.FakePrinterTransport
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
    fun aTestPrint_goesToThePrinter_andIsShorterThanABill() = runTest {
        assertEquals(PrintResult.PRINTED, service(printer58).printTest("Roy Mart", Language.DEFAULT))
        assertEquals(PrintResult.PRINTED, service(printer58).printInvoice(sale, ReceiptOptions()))
        val (test, bill) = transport.sent.map { it.second }
        assertEquals(48, firstRasterWidthBytes(test))
        assertTrue(test.size < bill.size)
    }

    @Test
    fun testReceipt_hasARulerAsWideAsTheLine_forEachRoll() {
        for (paper in listOf(ReceiptPaper.THERMAL_58, ReceiptPaper.THERMAL_76, ReceiptPaper.THERMAL_80)) {
            val receipt = ThermalReceiptPrinter.testReceipt(context, "Roy Mart", printer58.copy(paper = paper))
            val width = paper.spec.charsPerLine
            assertEquals(width, receipt.width)
            val ruler = receipt.elements.filterIsInstance<com.invision.pos.core.receipt.ThermalElement.Line>().map { it.text }.first { it.startsWith("1234567890") }
            assertEquals(width, ruler.length)
            assertTrue(receipt.elements.filterIsInstance<com.invision.pos.core.receipt.ThermalElement.Line>().any { it.text == "$width characters per line" })
        }
    }

    @Test
    fun ruler_countsUpInTens() {
        assertEquals("12345678901234567890123456789012", ThermalReceiptPrinter.ruler(32))
    }
}
