package com.invision.pos.core.receipt

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.domain.model.upiLinkFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

/** A quotation prints as "QUOTATION", with its own number and validity, and never as a tax invoice or a payment request. */
@RunWith(AndroidJUnit4::class)
class ReceiptQuotationTest {

    private val quotation = SampleSales.shirtsAndBiscuits().copy(
        number = "QT/26-27/00003",
        payments = emptyList(),
        quotationValidUntil = LocalDate.of(2026, 10, 25),
    )
    private val options = ReceiptOptions(upiId = "roymart@okaxis", upiPayeeName = "Roy Mart", shopName = "Roy Mart Store")

    private fun lines(receipt: ThermalReceipt) = receipt.elements.filterIsInstance<ThermalElement.Line>().map { it.text }

    @Test
    fun thermal_isTitledQuotation_withNumberValidityAndTheNotice() {
        val lines = lines(ThermalReceiptBuilder.build(quotation, options, 48, ZoneOffset.UTC))
        assertTrue(lines.any { it.trim() == "QUOTATION" })
        assertTrue(lines.none { it.contains("TAX INVOICE") || it.contains("BILL OF SUPPLY") })
        assertTrue(lines.contains("Quote No: QT/26-27/00003"))
        assertTrue(lines.contains("Valid until: 25-10-2026"))
        assertTrue(lines.joinToString(" ").contains("not a tax invoice"))
    }

    @Test
    fun thermal_hasNoPaymentLines_noUpiQr_andNoBarcode() {
        val elements = ThermalReceiptBuilder.build(quotation, options, 48, ZoneOffset.UTC).elements
        assertTrue(elements.none { it is ThermalElement.Qr })
        assertTrue(elements.none { it is ThermalElement.Barcode })
        assertTrue(lines(ThermalReceiptBuilder.build(quotation, options, 48, ZoneOffset.UTC)).none { it.startsWith("Paid by") })
        assertNull(options.upiLinkFor(quotation))
    }

    @Test
    fun sheet_carriesTheSameFacts() {
        val doc = SheetInvoiceLayout.build(quotation, options.copy(paper = ReceiptPaper.A4), ReceiptPaper.A4, ZoneOffset.UTC)
        assertEquals("QUOTATION", doc.title)
        assertTrue(doc.detailLines.contains("Quotation no." to "QT/26-27/00003"))
        assertTrue(doc.detailLines.contains("Valid until" to "25-10-2026"))
        assertTrue(doc.notes.first().contains("not a tax invoice"))
        assertNull(doc.upiQr)
        assertNull(doc.barcode)
        assertTrue(doc.paymentLines.isEmpty())
    }

    @Test
    fun anOrdinarySale_isUnchanged() {
        val sale = SampleSales.shirtsAndBiscuits()
        assertFalse(sale.isQuotation)
        val lines = lines(ThermalReceiptBuilder.build(sale, options, 48, ZoneOffset.UTC))
        assertTrue(lines.none { it.startsWith("Valid until") || it.startsWith("Quote No") })
        assertNull(SheetInvoiceLayout.build(sale, options.copy(paper = ReceiptPaper.A4), ReceiptPaper.A4, ZoneOffset.UTC).let { doc -> doc.detailLines.firstOrNull { it.first == "Valid until" } })
    }

    @Test
    fun everyPaper_rendersAQuotation() {
        for (paper in ReceiptPaper.entries) {
            val pages = ReceiptRenderer.pages(quotation, options.copy(paper = paper), ZoneOffset.UTC)
            assertTrue(pages.isNotEmpty())
        }
    }
}
