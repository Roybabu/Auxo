// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillCalculatorTest {

    private val inclusive = TaxMode(pricesIncludeTax = true, roundOff = false)
    private val exclusive = TaxMode(pricesIncludeTax = false, roundOff = false)

    private fun line(
        id: Long,
        price: Long,
        qty: Int = 1,
        rate: GstRate? = GstRate.Fixed(500),
        mrp: Long? = null,
        discount: Discount? = null,
    ) = CartLine(
        variantId = id, productId = id, productName = "Item $id", variantLabel = "", barcode = "", hsnCode = null,
        unitPrice = Money(price), mrp = mrp?.let { Money(it) }, gstRate = rate, quantity = qty, discount = discount, availableStock = 100,
    )

    private fun calc(vararg lines: CartLine, mode: TaxMode = inclusive, billDiscount: Discount? = null) =
        BillCalculator.calculate(Cart(lines = lines.toList(), billDiscount = billDiscount), mode)

    @Test
    fun inclusivePrices_extractGstFromThePrice() {
        val totals = calc(line(1, 3_000, qty = 2))
        val l = totals.lines.single()
        assertEquals(Money(5_714), l.taxable)
        assertEquals(Money(143), l.cgst)
        assertEquals(Money(143), l.sgst)
        assertEquals(Money(6_000), totals.grandTotal)
        assertEquals(Money(286), totals.taxTotal)
    }

    @Test
    fun exclusivePrices_addGstOnTop() {
        val totals = calc(line(1, 100_000, rate = GstRate.Fixed(1_800)), mode = exclusive)
        assertEquals(Money(100_000), totals.taxableTotal)
        assertEquals(Money(9_000), totals.cgstTotal)
        assertEquals(Money(9_000), totals.sgstTotal)
        assertEquals(Money(118_000), totals.grandTotal)
    }

    @Test
    fun oddTax_putsTheExtraPaisaOnCgst() {
        val (cgst, sgst, igst) = BillCalculator.splitTax(Money(7_129), interState = false)
        assertEquals(Money(3_565), cgst)
        assertEquals(Money(3_564), sgst)
        assertEquals(Money.ZERO, igst)
    }

    @Test
    fun interState_chargesIgstOnly() {
        val totals = calc(line(1, 100_000, rate = GstRate.Fixed(1_800)), mode = exclusive.copy(interState = true))
        assertEquals(Money(18_000), totals.igstTotal)
        assertEquals(Money.ZERO, totals.cgstTotal + totals.sgstTotal)
    }

    @Test
    fun apparelRule_usesPreGstValuePerPiece() {
        fun rateFor(price: Long, mode: TaxMode = inclusive, qty: Int = 1, discount: Discount? = null) =
            calc(line(1, price, qty = qty, rate = GstRate.Apparel, discount = discount), mode = mode).lines.single().rateBasisPoints

        assertEquals(500, rateFor(2_600_00)) // ₹2,476.19 before GST
        assertEquals(500, rateFor(2_625_00)) // exactly ₹2,500.00 before GST
        assertEquals(1_800, rateFor(2_625_01)) // just above
        assertEquals(1_800, rateFor(2_700_00))
        assertEquals(500, rateFor(2_500_00, exclusive))
        assertEquals(1_800, rateFor(2_500_01, exclusive))
        // Per piece: two shirts at ₹2,600 each stay at 5%.
        assertEquals(500, rateFor(2_600_00, qty = 2))
        // A discount that brings the piece under the threshold lowers the rate.
        assertEquals(500, rateFor(3_000_00, discount = Discount.Amount(Money(500_00))))
    }

    @Test
    fun apparelAt18Percent_inclusive() {
        val l = calc(line(1, 2_700_00, rate = GstRate.Apparel)).lines.single()
        assertEquals(Money(228_814), l.taxable)
        assertEquals(Money(41_186), l.tax)
    }

    @Test
    fun lineDiscounts_amountAndPercent() {
        val totals = calc(
            line(1, 10_000, discount = Discount.Percent(1_000)),
            line(2, 5_000, discount = Discount.Amount(Money(1_000))),
        )
        assertEquals(Money(1_000), totals.lines[0].lineDiscount)
        assertEquals(Money(1_000), totals.lines[1].lineDiscount)
        assertEquals(Money(15_000), totals.subtotal)
        assertEquals(Money(13_000), totals.grandTotal)
    }

    @Test
    fun discountNeverExceedsTheValue() {
        assertEquals(Money(500), Discount.Amount(Money(900)).amountOn(Money(500)))
        assertEquals(Money(500), Discount.Percent(10_000).amountOn(Money(500)))
    }

    @Test
    fun billDiscount_isSharedInProportion() {
        val totals = calc(line(1, 60_000), line(2, 40_000), billDiscount = Discount.Amount(Money(10_000)))
        assertEquals(Money(6_000), totals.lines[0].billDiscountShare)
        assertEquals(Money(4_000), totals.lines[1].billDiscountShare)
        assertEquals(Money(90_000), totals.grandTotal)
        assertEquals(Money(10_000), totals.discountTotal)
    }

    @Test
    fun allocate_addsUpExactly_andBreaksTiesInOrder() {
        val parts = BillCalculator.allocate(Money(100), listOf(Money(1), Money(1), Money(1)))
        assertEquals(listOf(Money(34), Money(33), Money(33)), parts)
        val uneven = BillCalculator.allocate(Money(1_001), listOf(Money(333), Money(667), Money(1)))
        assertEquals(Money(1_001), uneven.fold(Money.ZERO) { a, b -> a + b })
    }

    @Test
    fun roundOff_toNearestRupee() {
        val up = calc(line(1, 9_950), mode = inclusive.copy(roundOff = true))
        assertEquals(Money(50), up.roundOff)
        assertEquals(Money(10_000), up.grandTotal)
        val down = calc(line(1, 9_949), mode = inclusive.copy(roundOff = true))
        assertEquals(Money(-49), down.roundOff)
        assertEquals(Money(9_900), down.grandTotal)
    }

    @Test
    fun unregisteredStore_chargesNoTax() {
        val totals = calc(line(1, 10_000, rate = GstRate.Fixed(1_800)), mode = TaxMode(gstRegistered = false, roundOff = false))
        assertEquals(0, totals.lines.single().rateBasisPoints)
        assertEquals(Money.ZERO, totals.taxTotal)
        assertEquals(Money(10_000), totals.taxableTotal)
        assertEquals(Money(10_000), totals.grandTotal)
    }

    @Test
    fun savings_comparesMrpWithAmountPaid() {
        val totals = calc(line(1, 3_000, qty = 2, mrp = 3_500))
        assertEquals(Money(1_000), totals.savings)
    }

    @Test
    fun taxSummary_groupsByRate() {
        val totals = calc(line(1, 10_000), line(2, 20_000, rate = GstRate.Fixed(1_800)), line(3, 5_000))
        assertEquals(listOf(500, 1_800), totals.taxSummary.map { it.rateBasisPoints })
        assertEquals(totals.taxableTotal, totals.taxSummary.fold(Money.ZERO) { a, r -> a + r.taxable })
    }

    @Test
    fun totalsAlwaysAddUp() {
        val totals = calc(
            line(1, 2_999, qty = 3, discount = Discount.Percent(733)),
            line(2, 2_700_00, rate = GstRate.Apparel),
            line(3, 1_01, qty = 7, rate = GstRate.Fixed(4_000)),
            billDiscount = Discount.Percent(1_250),
            mode = inclusive.copy(roundOff = true),
        )
        val lineTotals = totals.lines.fold(Money.ZERO) { a, l -> a + l.total }
        assertEquals(totals.totalBeforeRounding, lineTotals)
        assertEquals(totals.taxableTotal + totals.taxTotal, totals.totalBeforeRounding)
        assertEquals(totals.subtotal - totals.discountTotal, totals.totalBeforeRounding)
        assertEquals(0L, totals.grandTotal.minorUnits % 100)
    }

    @Test
    fun emptyCart() {
        assertEquals(BillTotals.EMPTY, calc())
    }

    @Test
    fun cartMergesTheSameVariant() {
        val product = TestData.biscuits()
        val variant = product.variants.single()
        val cart = Cart().add(CartLine.from(product, variant)).add(CartLine.from(product, variant))
        assertEquals(1, cart.lines.size)
        assertEquals(2, cart.itemCount)
        assertTrue(cart.setQuantity(variant.id, 0).isEmpty)
    }
}
