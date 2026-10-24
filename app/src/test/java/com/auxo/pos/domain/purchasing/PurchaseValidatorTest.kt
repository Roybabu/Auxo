// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.purchasing

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderLine
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class PurchaseValidatorTest {

    private val today = TestData.today
    private val shirt = TestData.tShirt()
    private val flour = TestData.flour()

    private fun line(key: String, variantId: Long?, quantity: String, cost: String, unit: StockUnit = StockUnit.UNIT) =
        PurchaseLineForm(key = key, variantId = variantId, productName = "Item $key", variantLabel = "", stockUnit = unit, quantity = quantity, unitCost = cost)

    @Test
    fun validOrder_parsesQuantitiesInBaseUnits() {
        val result = PurchaseValidator.validateOrder(
            supplierId = 1,
            lines = listOf(line("a", 11, "10", "250"), line("b", 41, "2.5", "45.50", StockUnit.GRAM)),
            expectedDate = today.plusDays(2),
            note = "  Deliver before noon ",
            today = today,
        ) as OrderValidation.Valid
        val order = result.order
        assertEquals(10, order.lines[0].quantityOrdered)
        assertEquals(2_500, order.lines[1].quantityOrdered)
        assertEquals(Money(4_550), order.lines[1].unitCost)
        assertEquals("Deliver before noon", order.note)
        // 10 x ₹250 + 2.5 kg x ₹45.50
        assertEquals(Money(250_000 + 11_375), order.total)
    }

    @Test
    fun invalidOrder_reportsFormAndLineErrors() {
        val result = PurchaseValidator.validateOrder(
            supplierId = null,
            lines = listOf(line("a", 11, "", "250"), line("b", 11, "1", "abc"), line("c", null, "1", "1")),
            expectedDate = today.minusDays(1),
            note = "",
            today = today,
        ) as OrderValidation.Invalid
        assertEquals(setOf(PurchaseFormError.SUPPLIER_REQUIRED, PurchaseFormError.EXPECTED_DATE_IN_PAST), result.formErrors)
        assertEquals(setOf(PurchaseLineError.QUANTITY_REQUIRED), result.lineErrors["a"])
        assertEquals(setOf(PurchaseLineError.DUPLICATE_ITEM, PurchaseLineError.COST_INVALID), result.lineErrors["b"])
        assertEquals(setOf(PurchaseLineError.ITEM_DELETED), result.lineErrors["c"])
    }

    @Test
    fun emptyOrder_needsLines() {
        val result = PurchaseValidator.validateOrder(1, emptyList(), null, "", today) as OrderValidation.Invalid
        assertEquals(setOf(PurchaseFormError.NO_LINES), result.formErrors)
    }

    @Test
    fun receiptFromOrder_skipsZeroLines_andRefusesMoreThanDue() {
        val due = line("a", 11, "5", "250").copy(orderLineId = 100, remaining = 4)
        val skipped = line("b", 12, "0", "").copy(orderLineId = 101, remaining = 3)
        val invalid = PurchaseValidator.validateReceipt(null, 9, "", listOf(due, skipped), "", today) as ReceiptValidation.Invalid
        assertEquals(setOf(PurchaseLineError.MORE_THAN_ORDERED), invalid.lineErrors["a"])
        assertTrue("b" !in invalid.lineErrors)

        val valid = PurchaseValidator.validateReceipt(null, 9, "", listOf(due.copy(quantity = "4"), skipped), "", today) as ReceiptValidation.Valid
        assertEquals(1, valid.receipt.lines.size)
        assertEquals(100L, valid.receipt.lines.single().orderLineId)
    }

    @Test
    fun receiptWithNothingArriving_isRejected() {
        val skipped = line("a", 11, "0", "").copy(orderLineId = 100, remaining = 4)
        val result = PurchaseValidator.validateReceipt(null, 9, "", listOf(skipped), "", today) as ReceiptValidation.Invalid
        assertEquals(setOf(PurchaseFormError.NOTHING_TO_RECEIVE), result.formErrors)
    }

    @Test
    fun receipt_checksBillNumberAndBatchDetails() {
        val food = line("a", 21, "10", "22", StockUnit.UNIT).copy(tracksExpiry = true, batchNumber = " b-7 ", expiryDate = today.minusDays(1))
        val shirtLine = line("b", 11, "3", "250").copy(batchNumber = "ignored", expiryDate = today.plusDays(5))
        val invalid = PurchaseValidator.validateReceipt(1, null, "INV 12", listOf(food, shirtLine), "", today) as ReceiptValidation.Invalid
        assertEquals(setOf(PurchaseFormError.SUPPLIER_INVOICE_INVALID), invalid.formErrors)
        assertEquals(setOf(PurchaseLineError.ALREADY_EXPIRED), invalid.lineErrors["a"])

        val valid = PurchaseValidator.validateReceipt(
            1, null, "inv/12", listOf(food.copy(expiryDate = today.plusDays(30)), shirtLine), "", today,
        ) as ReceiptValidation.Valid
        assertEquals("INV/12", valid.receipt.supplierInvoiceNumber)
        assertEquals("b-7", valid.receipt.lines[0].batchNumber)
        // Batch details only apply to items kept in batches.
        assertEquals(null, valid.receipt.lines[1].batchNumber)
        assertEquals(null, valid.receipt.lines[1].expiryDate)
    }

    @Test
    fun directReceipt_needsASupplier_andNoDuplicateItems() {
        val result = PurchaseValidator.validateReceipt(null, null, "", listOf(line("a", 11, "1", "1"), line("b", 11, "2", "1")), "", today)
            as ReceiptValidation.Invalid
        assertEquals(setOf(PurchaseFormError.SUPPLIER_REQUIRED), result.formErrors)
        assertEquals(setOf(PurchaseLineError.DUPLICATE_ITEM), result.lineErrors["b"])
    }

    @Test
    fun lineFormsStartFromTheItemOrTheOrder() {
        val fromVariant = PurchaseLineForm.forVariant("k", flour, flour.variants.single(), quantity = 2_500)
        assertEquals("2.5", fromVariant.quantity)
        assertEquals("45.00", fromVariant.unitCost)
        assertTrue(fromVariant.tracksExpiry)

        val orderLine = PurchaseOrderLine(variantId = 11, productName = shirt.name, variantLabel = "M", quantityOrdered = 6, unitCost = Money(25_000))
        val fromOrder = PurchaseLineForm.fromOrderLine("k", orderLine)
        assertEquals("6", fromOrder.quantity)
        assertEquals("250.00", fromOrder.unitCost)
    }
}

class PurchaseNumbersAndTextTest {

    @Test
    fun numbers_followTheFinancialYear_withTheirOwnCounters() {
        val march = TestData.today.withMonth(3).withDayOfMonth(31).plusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(2026, PurchaseNumbers.financialYear(TestData.clock.millis(), ZoneOffset.UTC))
        assertEquals(2026, PurchaseNumbers.financialYear(march, ZoneOffset.UTC))
        assertEquals("PO/26-27/00003", PurchaseNumbers.order(2026, 3))
        assertEquals("GRN/26-27/00001", PurchaseNumbers.receipt(2026, 1))
        assertTrue(PurchaseNumbers.orderSequence(2026) != "PO|2026")
    }

    @Test
    fun orderText_listsLinesWithUnitsAndTotal() {
        val order = PurchaseOrder(
            number = "PO/26-27/00001",
            supplierId = 1,
            supplierName = "Fresh Mills",
            expectedDate = TestData.today.plusDays(2),
            note = "Call before delivery",
            lines = listOf(
                PurchaseOrderLine(variantId = 41, productName = "Wheat Flour", variantLabel = "", stockUnit = StockUnit.GRAM, quantityOrdered = 25_000, unitCost = Money(4_500)),
                PurchaseOrderLine(variantId = 11, productName = "Cotton T-Shirt", variantLabel = "M · Navy", quantityOrdered = 10, unitCost = Money(25_000)),
            ),
        )
        val text = PurchaseOrderText.format(order, "Sample Mart", "9830012345")
        assertTrue(text.startsWith("Purchase order PO/26-27/00001"))
        assertTrue(text.contains("From: Sample Mart (9830012345)"))
        assertTrue(text.contains("To: Fresh Mills"))
        assertTrue(text.contains("Needed by: 12 Oct 2026"))
        assertTrue(text.contains("1. Wheat Flour - 25 kg @ Rs. 45.00/kg"))
        assertTrue(text.contains("2. Cotton T-Shirt M · Navy - 10 pcs @ Rs. 250.00/pcs"))
        assertTrue(text.contains("Estimated total: Rs. 3,625.00"))
        assertTrue(text.endsWith("Note: Call before delivery"))
    }
}
