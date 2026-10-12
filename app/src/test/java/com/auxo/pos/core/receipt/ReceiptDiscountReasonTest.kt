package com.auxo.pos.core.receipt

import com.auxo.pos.domain.billing.SampleSales
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.ReceiptOptions
import com.auxo.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** A discount and why it was given are printed on the bill. */
class ReceiptDiscountReasonTest {

    private val base = SampleSales.shirtsAndBiscuits()
    private val sale = base.copy(
        discountTotal = Money(5_00),
        discountReason = "Regular customer",
        lines = base.lines.mapIndexed { i, l ->
            if (i == 0) l.copy(discount = Money(5_00), discountReason = "Damaged box") else l
        },
    )

    @Test
    fun thermal_showsTheLineReason_andTheBillReason() {
        val lines = ThermalReceiptBuilder.build(sale, ReceiptOptions(), 48, ZoneOffset.UTC)
            .elements.filterIsInstance<ThermalElement.Line>().map { it.text }
        assertTrue(lines.toString(), lines.any { it.contains("Discount (Damaged box)") })
        assertTrue(lines.toString(), lines.any { it == "Discount reason: Regular customer" })
    }

    @Test
    fun thermal_withNoReasons_printsTheOldLines() {
        val plain = base.copy(discountTotal = Money(5_00), lines = base.lines.mapIndexed { i, l -> if (i == 0) l.copy(discount = Money(5_00)) else l })
        val lines = ThermalReceiptBuilder.build(plain, ReceiptOptions(), 48, ZoneOffset.UTC)
            .elements.filterIsInstance<ThermalElement.Line>().map { it.text }
        assertFalse(lines.any { it.contains("reason", ignoreCase = true) })
        assertTrue(lines.any { it.trimStart().startsWith("Discount") })
    }

    @Test
    fun sheet_showsBothReasons() {
        for (paper in listOf(ReceiptPaper.A5, ReceiptPaper.A4)) {
            val doc = SheetInvoiceLayout.build(sale, ReceiptOptions(paper = paper), paper, ZoneOffset.UTC)
            val text = doc.notes + doc.rowNotes.filterNotNull()
            assertTrue("$paper: $text", text.any { it.contains("Damaged box") })
            assertTrue("$paper: $text", text.any { it.contains("Regular customer") })
        }
    }
}
