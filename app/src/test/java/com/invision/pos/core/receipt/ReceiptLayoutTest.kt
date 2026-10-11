package com.invision.pos.core.receipt

import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.IndianNumberFormat
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.screens.billing.CashSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ThermalReceiptTest {

    private val sale = SampleSales.shirtsAndBiscuits()

    private fun text(receipt: ThermalReceipt): List<String> = receipt.elements.mapNotNull { (it as? ThermalElement.Line)?.text }

    @Test
    fun linesNeverExceedThePaperWidth() {
        for (width in listOf(32, 42, 48)) {
            val receipt = ThermalReceiptBuilder.build(sale, ReceiptOptions(), width, ZoneOffset.UTC)
            receipt.elements.filterIsInstance<ThermalElement.Line>().forEach { line ->
                val limit = if (line.large) width / 2 else width
                assertTrue("'${line.text}' is longer than $limit", line.text.length <= limit)
            }
        }
    }

    @Test
    fun containsInvoiceEssentials() {
        val lines = text(ThermalReceiptBuilder.build(sale, ReceiptOptions(), 48, ZoneOffset.UTC))
        assertTrue(lines.contains("TAX INVOICE"))
        assertTrue(lines.any { it.contains("GSTIN: 19AABCU9603R1ZK") })
        assertTrue(lines.any { it.contains("Inv No: INV/26-27/00007") })
        assertTrue(lines.any { it.contains("Date: 10-10-2026 10:00 AM") })
        assertTrue(lines.any { it.contains("Customer: Walk-in Customer") })
        assertFalse(lines.any { it.contains("Place of supply") })
        assertEquals("Please share your feedback with us.", lines.last { it.isNotBlank() })
        assertTrue(lines.any { it.startsWith("TOTAL") && it.endsWith("1,557.00") })
        assertTrue(lines.any { it.startsWith("CGST") })
        assertTrue(lines.contains("  HSN 6109  GST 5%"))
        assertTrue(lines.any { it.startsWith("Paid by Cash") })
    }

    @Test
    fun optionsHideSections() {
        val options = ReceiptOptions(showCustomer = false, showHsn = false, showTaxBreakup = false, showInvoiceBarcode = false, footer = "")
        val receipt = ThermalReceiptBuilder.build(sale, options, 32, ZoneOffset.UTC)
        val lines = text(receipt)
        assertFalse(lines.any { it.contains("Customer:") })
        assertFalse(lines.any { it.contains("HSN") })
        assertFalse(lines.contains("GST summary"))
        assertTrue(receipt.elements.none { it is ThermalElement.Barcode })
    }

    @Test
    fun unregisteredStore_printsBillOfSupplyWithoutTax() {
        val bill = sale.copy(gstRegistered = false, cgstTotal = Money.ZERO, sgstTotal = Money.ZERO)
        val lines = text(ThermalReceiptBuilder.build(bill, ReceiptOptions(), 48, ZoneOffset.UTC))
        assertTrue(lines.contains("BILL OF SUPPLY"))
        assertFalse(lines.any { it.startsWith("CGST") })
    }

    @Test
    fun creditNote_showsRefundAndOriginalInvoice() {
        val note = (ReturnBuilder.build(sale, mapOf(sale.lines[0].id to 1), PaymentMethod.UPI, "Wrong size", 0) as ReturnBuild.Ready)
            .creditNote.copy(number = "CN/26-27/00001")
        val lines = text(ThermalReceiptBuilder.build(note, ReceiptOptions(), 48, ZoneOffset.UTC))
        assertTrue(lines.contains("CREDIT NOTE"))
        assertTrue(lines.any { it.contains("Against: INV/26-27/00007") })
        assertTrue(lines.any { it.startsWith("REFUND") })
        assertTrue(lines.any { it.startsWith("Refund (UPI)") })
        assertTrue(lines.any { it.contains("Reason: Wrong size") })
    }

    @Test
    fun namedCustomer() {
        val named = SampleSales.shirtsAndBiscuits(CustomerDetails(name = "Sen Traders", phone = "9831000000", gstin = "19AABCU9603R1ZK"))
        val lines = text(ThermalReceiptBuilder.build(named, ReceiptOptions(), 32, ZoneOffset.UTC))
        assertTrue(lines.contains("Customer: Sen Traders"))
        assertTrue(lines.contains("GSTIN: 19AABCU9603R1ZK"))
        assertTrue(lines.contains("Phone: 9831XXXXXX"))
        assertFalse(lines.any { it.contains("9831000000") })
    }

    @Test
    fun maskPhone_hidesTheLastSixDigits() {
        assertEquals("9831XXXXXX", ReceiptText.maskPhone("9831000000"))
        assertEquals("+91 9831X XXXXX", ReceiptText.maskPhone("+91 98310 00000"))
        assertEquals("XXXXX", ReceiptText.maskPhone("12345"))
        assertEquals("", ReceiptText.maskPhone(""))
    }

    @Test
    fun mrpAboveSellingPrice_isShownAsDiscount() {
        val line = sale.lines[0]
        val withMrp = sale.copy(lines = listOf(line.copy(mrp = line.unitPrice + Money(5_000), quantity = 2)) + sale.lines.drop(1))
        val expected = IndianNumberFormat.amount(Money(10_000))
        val thermal = text(ThermalReceiptBuilder.build(withMrp, ReceiptOptions(), 48, ZoneOffset.UTC))
        assertTrue(thermal.any { it.contains("MRP") && it.contains("Discount") && it.endsWith("-$expected") })
        val sheet = SheetInvoiceLayout.build(withMrp, ReceiptOptions(), ReceiptPaper.A5, ZoneOffset.UTC)
        assertTrue(sheet.rowNotes[0]!!.contains("Discount ${IndianNumberFormat.amount(Money(10_000))}"))
    }

    @Test
    fun mrpEqualToSellingPrice_showsNoMrpDiscount() {
        val line = sale.lines[0]
        val same = sale.copy(lines = listOf(line.copy(mrp = line.unitPrice, discount = Money.ZERO)) + sale.lines.drop(1))
        assertEquals(null, ReceiptText.mrpDiscount(same.lines[0]))
        assertEquals(null, ReceiptText.mrpDiscount(line.copy(mrp = null)))
    }

    @Test
    fun wrap_breaksLongWords() {
        assertEquals(listOf("abcde", "fgh", "ij"), ReceiptText.wrap("abcdefgh ij", 5))
        assertTrue(ReceiptText.wrap("a".repeat(70), 32).all { it.length <= 32 })
    }
}

class SheetInvoiceLayoutTest {

    private val sale = SampleSales.shirtsAndBiscuits()

    @Test
    fun a4_hasDiscountAndTaxableColumns_a5_doesNot() {
        val a4 = SheetInvoiceLayout.build(sale, ReceiptOptions(), ReceiptPaper.A4, ZoneOffset.UTC)
        val a5 = SheetInvoiceLayout.build(sale, ReceiptOptions(), ReceiptPaper.A5, ZoneOffset.UTC)
        assertEquals(listOf("#", "Item", "HSN", "Qty", "Rate", "Disc.", "Taxable", "GST", "Amount"), a4.columns.map { it.title })
        assertEquals(listOf("#", "Item", "HSN", "Qty", "Rate", "GST", "Amount"), a5.columns.map { it.title })
        assertEquals(sale.lines.size, a4.rows.size)
        assertEquals("Cotton T-Shirt · M · Navy", a4.rows[0][1])
    }

    @Test
    fun totalsWordsAndSummary() {
        val doc = SheetInvoiceLayout.build(sale, ReceiptOptions(), ReceiptPaper.A4, ZoneOffset.UTC)
        assertEquals("TAX INVOICE", doc.title)
        assertEquals("Grand total" to "₹1,557.00", doc.grandTotal)
        assertEquals("Rupees One Thousand Five Hundred Fifty Seven Only", doc.amountInWords)
        assertEquals(listOf("GST rate", "Taxable value", "CGST", "SGST", "Total tax"), doc.taxSummaryHeader)
        assertEquals(listOf("Walk-in Customer"), doc.buyerLines)
        assertTrue(doc.detailLines.none { it.first.contains("Place of supply") })
        assertEquals("Please share your feedback with us.", doc.feedbackNote)
        assertEquals("For Roy Mart", doc.signatureLabel)
    }

    @Test
    fun hsnColumnFollowsTheOption() {
        val doc = SheetInvoiceLayout.build(sale, ReceiptOptions(showHsn = false, showTaxBreakup = false), ReceiptPaper.A4, ZoneOffset.UTC)
        assertFalse(doc.columns.any { it.title == "HSN" })
        assertTrue(doc.taxSummaryHeader.isEmpty())
    }
}

class CashSuggestionsTest {

    @Test
    fun nextLikelyNotes() {
        assertEquals(listOf(Money(350_00), Money(400_00), Money(500_00)), CashSuggestions.forAmount(Money(347_00)))
        assertEquals(listOf(Money(1_000_00), Money(2_000_00)), CashSuggestions.forAmount(Money(990_00)))
    }
}
