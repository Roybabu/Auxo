package com.invision.pos.core.receipt

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.domain.model.upiLinkFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** The UPI QR on bills: what it carries, when it is left out, and that it takes room on every paper. */
@RunWith(AndroidJUnit4::class)
class ReceiptUpiQrTest {

    private val sale = SampleSales.shirtsAndBiscuits()
    private val withUpi = ReceiptOptions(upiId = "roymart@okaxis", upiPayeeName = "Roy Mart", shopName = "Roy Mart Store")

    @Test
    fun link_carriesTheExactGrandTotalAndTheRealInvoiceNumber() {
        val link = withUpi.upiLinkFor(sale)!!
        assertTrue(link.startsWith("upi://pay?pa=roymart%40okaxis"))
        assertTrue(link, link.contains("am=1557.00"))
        assertTrue(link, link.contains("tn=Invoice%20INV%2F26-27%2F00007"))
        assertTrue(link, link.contains("tr=INV262700007"))
    }

    @Test
    fun noLink_whenOffInvalidMissingOrNotASale() {
        assertNull(withUpi.copy(showUpiQr = false).upiLinkFor(sale))
        assertNull(withUpi.copy(upiId = "").upiLinkFor(sale))
        assertNull(withUpi.copy(upiId = "not a upi id").upiLinkFor(sale))
        assertNull(ReceiptOptions().upiLinkFor(sale))
        assertNull(withUpi.upiLinkFor(sale.copy(number = "")))
        val note = (ReturnBuilder.build(sale, mapOf(sale.lines[0].id to 1), PaymentMethod.UPI, "Wrong size", 0) as ReturnBuild.Ready)
            .creditNote.copy(number = "CN/26-27/00001")
        assertNull(withUpi.upiLinkFor(note))
    }

    @Test
    fun thermalReceipt_hasOneQrWithTheAmountCaption_andNoneWithoutIt() {
        val qr = ThermalReceiptBuilder.build(sale, withUpi, 48, ZoneOffset.UTC).elements.filterIsInstance<ThermalElement.Qr>().single()
        assertEquals(withUpi.upiLinkFor(sale), qr.content)
        assertEquals("Scan to pay 1,557.00", qr.caption)
        val none = ThermalReceiptBuilder.build(sale, ReceiptOptions(), 48, ZoneOffset.UTC).elements.filterIsInstance<ThermalElement.Qr>()
        assertTrue(none.isEmpty())
    }

    @Test
    fun sheetInvoice_carriesTheQrLink() {
        val doc = SheetInvoiceLayout.build(sale, withUpi.copy(paper = ReceiptPaper.A4), ReceiptPaper.A4, ZoneOffset.UTC)
        assertEquals(withUpi.upiLinkFor(sale), doc.upiQr)
        assertNotNull(doc.upiCaption)
        assertNull(SheetInvoiceLayout.build(sale, ReceiptOptions(paper = ReceiptPaper.A4), ReceiptPaper.A4, ZoneOffset.UTC).upiQr)
    }

    @Test
    fun everyPaper_drawsTheQr_andNeedsMoreRoomForIt() {
        for (paper in ReceiptPaper.entries) {
            val plain = ReceiptRenderer.pages(sale, ReceiptOptions(paper = paper), ZoneOffset.UTC)
            val qr = ReceiptRenderer.pages(sale, withUpi.copy(paper = paper), ZoneOffset.UTC)
            assertTrue("$paper has pages", qr.isNotEmpty())
            if (paper == ReceiptPaper.A4 || paper == ReceiptPaper.A5) {
                assertTrue("$paper still fits the same pages or one more", qr.size in plain.size..plain.size + 1)
            } else {
                assertTrue("$paper roll is taller with the QR", qr.first().heightPt > plain.first().heightPt)
            }
        }
    }
}
