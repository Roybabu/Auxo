// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tax is worked out on the price after the discount, never on the price before it. */
class DiscountTaxMathTest {

    private val exclusive = TaxMode(pricesIncludeTax = false, roundOff = false)
    private val inclusive = TaxMode(pricesIncludeTax = true, roundOff = false)

    private fun line(id: Long, price: Long, rate: Int, qty: Int = 1, discount: Discount? = null) = CartLine(
        variantId = id, productId = id, productName = "Item $id", variantLabel = "", barcode = "", hsnCode = null,
        unitPrice = Money(price), mrp = null, gstRate = GstRate.Fixed(rate), quantity = qty, discount = discount,
        discountReason = if (discount != null) "Festival" else "", availableStock = 100,
    )

    @Test
    fun percentDiscount_exclusivePrices_taxesTheDiscountedPrice() {
        val totals = BillCalculator.calculate(Cart(lines = listOf(line(1, 10_000, 500, discount = Discount.Percent(1_000)))), exclusive)
        val l = totals.lines.single()
        assertEquals(Money(1_000), l.lineDiscount)
        assertEquals(Money(9_000), l.taxable)
        assertEquals(Money(225), l.cgst)
        assertEquals(Money(225), l.sgst)
        assertEquals(Money(9_450), totals.grandTotal)
    }

    @Test
    fun percentDiscount_inclusivePrices_takesTheTaxOutOfTheDiscountedPrice() {
        val totals = BillCalculator.calculate(Cart(lines = listOf(line(1, 10_500, 500, discount = Discount.Percent(1_000)))), inclusive)
        val l = totals.lines.single()
        assertEquals(Money(9_000), l.taxable)
        assertEquals(Money(450), l.tax)
        assertEquals(Money(9_450), totals.grandTotal)
    }

    @Test
    fun fixedDiscount_isTakenOffBeforeTax() {
        val totals = BillCalculator.calculate(Cart(lines = listOf(line(1, 20_000, 1_800, discount = Discount.Amount(Money(5_000))))), exclusive)
        assertEquals(Money(15_000), totals.taxableTotal)
        assertEquals(Money(2_700), totals.taxTotal)
        assertEquals(Money(17_700), totals.grandTotal)
    }

    @Test
    fun billDiscount_isSharedByValue_andEachShareIsTaxedAtItsOwnRate() {
        val cart = Cart(
            lines = listOf(line(1, 30_000, 500), line(2, 20_000, 1_800)),
            billDiscount = Discount.Amount(Money(5_000)),
            billDiscountReason = "Regular customer",
        )
        val totals = BillCalculator.calculate(cart, exclusive)
        assertEquals(listOf(Money(3_000), Money(2_000)), totals.lines.map { it.billDiscountShare })
        assertEquals(listOf(Money(27_000), Money(18_000)), totals.lines.map { it.taxable })
        assertEquals(Money(1_350), totals.lines[0].tax)
        assertEquals(Money(3_240), totals.lines[1].tax)
        assertEquals(Money(49_590), totals.grandTotal)
    }

    @Test
    fun lineAndBillDiscountTogether_stillSumToTheGrandTotal() {
        for (mode in listOf(exclusive, inclusive)) {
            val cart = Cart(
                lines = listOf(
                    line(1, 33_333, 500, qty = 3, discount = Discount.Percent(750)),
                    line(2, 9_999, 1_200, qty = 2),
                    line(3, 1_250, 0, qty = 7, discount = Discount.Amount(Money(333))),
                ),
                billDiscount = Discount.Percent(1_250),
            )
            val totals = BillCalculator.calculate(cart, mode)
            assertEquals(totals.subtotal - totals.discountTotal, totals.lines.fold(Money.ZERO) { acc, l -> acc + l.gross - l.discount })
            if (!mode.pricesIncludeTax) {
                assertEquals(totals.taxableTotal + totals.taxTotal, totals.grandTotal)
            }
            // What is charged is never more than what the discounted prices come to.
            assertEquals(totals.subtotal - totals.discountTotal + if (mode.pricesIncludeTax) Money.ZERO else totals.taxTotal, totals.grandTotal)
        }
    }

    @Test
    fun invoiceKeepsTheDiscountedAmountsAndTheReasons() {
        val cart = Cart(
            lines = listOf(line(1, 10_000, 500, discount = Discount.Percent(1_000)).copy(discountReason = "  Damaged   box ")),
            billDiscount = Discount.Amount(Money(100)),
            billDiscountReason = "Loyal customer",
        )
        val totals = BillCalculator.calculate(cart, exclusive)
        val settings = com.auxo.pos.testutil.TestData.settings
        val invoice = SaleBuilder.build(cart, totals, settings.copy(pricesIncludeTax = false, roundOffTotal = false), emptyList(), 0)
        assertEquals(totals.discountTotal, invoice.discountTotal)
        assertEquals("Damaged box", invoice.lines.single().discountReason)
        assertEquals("Loyal customer", invoice.discountReason)
        assertEquals(totals.taxableTotal, invoice.taxableTotal)
    }

    @Test
    fun noDiscount_meansNoReasonIsKept() {
        val cart = Cart(lines = listOf(line(1, 10_000, 500).copy(discountReason = "left over")), billDiscountReason = "left over")
        val totals = BillCalculator.calculate(cart, exclusive)
        val invoice = SaleBuilder.build(cart, totals, com.auxo.pos.testutil.TestData.settings, emptyList(), 0)
        assertEquals(null, invoice.lines.single().discountReason)
        assertEquals(null, invoice.discountReason)
    }
}
