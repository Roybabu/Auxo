// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.loyalty.LoyaltyRedemption
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountPolicyTest {

    private val mode = TaxMode(pricesIncludeTax = false, roundOff = false)

    private fun line(id: Long, price: Long, discount: Discount? = null, reason: String = "") = CartLine(
        variantId = id, productId = id, productName = "Item $id", variantLabel = "", barcode = "", hsnCode = null,
        unitPrice = Money(price), mrp = null, gstRate = GstRate.Fixed(500), quantity = 1, discount = discount,
        discountReason = reason, availableStock = 10,
    )

    private fun check(cart: Cart, limit: Int? = null): CartCheck {
        val totals = BillCalculator.calculate(cart, mode)
        return CartValidator.check(cart, totals, gstRegistered = true, discountLimitBp = limit)
    }

    @Test
    fun exceeds_usesWholeNumbers_soTheLimitIsExact() {
        assertFalse(DiscountPolicy.exceeds(Money(1_000), Money(10_000), 1_000)) // exactly 10%
        assertTrue(DiscountPolicy.exceeds(Money(1_001), Money(10_000), 1_000))
        assertFalse(DiscountPolicy.exceeds(Money.ZERO, Money(10_000), 0))
        assertTrue(DiscountPolicy.exceeds(Money(1), Money(10_000), 0))
        assertTrue(DiscountPolicy.exceeds(Money(1), Money.ZERO, 10_000))
    }

    @Test
    fun roundingToAPaisa_doesNotPushAPercentageDiscountOverItsOwnLimit() {
        // 10% of Rs 4.99 rounds to 50 paise, which is 10.02% of 499 paise.
        val rounded = Discount.Percent(1_000).amountOn(Money(499))
        assertEquals(Money(50), rounded)
        assertFalse(DiscountPolicy.exceeds(rounded, Money(499), 1_000, roundingSlack = 1))
        assertTrue(DiscountPolicy.exceeds(rounded, Money(499), 1_000))
        // The slack never lets a discount through when Employees may not discount at all.
        assertTrue(DiscountPolicy.exceeds(Money(1), Money(499), 0, roundingSlack = 5))
        // And it is only a paisa, not a way to give more.
        assertTrue(DiscountPolicy.exceeds(Money(60), Money(499), 1_000, roundingSlack = 1))
    }

    @Test
    fun reasons_areTidied() {
        assertEquals("Damaged box", DiscountPolicy.cleanReason("  Damaged \n  box "))
        assertNull(DiscountPolicy.cleanReason("   "))
        assertEquals(DiscountPolicy.REASON_MAX, DiscountPolicy.cleanReason("x".repeat(200))!!.length)
        assertEquals(listOf("A", "B"), DiscountPolicy.reasonsOn(listOf("A", null, "A"), "B"))
    }

    @Test
    fun limitLabels() {
        assertEquals("5%", DiscountLimits.label(500))
        assertEquals("12.5%", DiscountLimits.label(1_250))
        assertEquals("100%", DiscountLimits.label(10_000))
        assertTrue(DiscountPolicy.LIMIT_CHOICES.first() == 0)
    }

    @Test
    fun aDiscountNeedsAReason_onTheLineAndOnTheBill() {
        val noReason = check(Cart(lines = listOf(line(1, 10_000, Discount.Percent(500)))))
        assertEquals(LineIssue.DiscountReasonMissing, noReason.lineIssues[1L])
        assertFalse(noReason.canCheckout)

        val withReason = check(Cart(lines = listOf(line(1, 10_000, Discount.Percent(500), "Festival"))))
        assertTrue(withReason.lineIssues.isEmpty())
        assertTrue(withReason.canCheckout)

        val bill = check(Cart(lines = listOf(line(1, 10_000)), billDiscount = Discount.Amount(Money(100))))
        assertTrue(bill.billDiscountReasonMissing)
        assertFalse(bill.canCheckout)
        assertTrue(check(Cart(lines = listOf(line(1, 10_000)), billDiscount = Discount.Amount(Money(100)), billDiscountReason = "Regular")).canCheckout)
    }

    @Test
    fun noLimit_meansAnyDiscountPasses_andLimitZeroMeansNone() {
        val cart = Cart(lines = listOf(line(1, 10_000, Discount.Percent(5_000), "Clearance")))
        assertTrue(check(cart, limit = null).canCheckout)
        val none = check(cart, limit = 0)
        assertEquals(LineIssue.DiscountAboveLimit, none.lineIssues[1L])
        assertFalse(none.canCheckout)
        assertTrue(check(Cart(lines = listOf(line(1, 10_000))), limit = 0).canCheckout) // no discount, nothing to stop
    }

    @Test
    fun lineLimit_isPerLine() {
        val cart = Cart(lines = listOf(line(1, 10_000, Discount.Percent(1_000), "A"), line(2, 10_000, Discount.Percent(1_500), "B")))
        val result = check(cart, limit = 1_000)
        assertNull(result.lineIssues[1L])
        assertEquals(LineIssue.DiscountAboveLimit, result.lineIssues[2L])
    }

    @Test
    fun billLimit_looksAtTheWholeBill_andLineAndBillDiscountsAdd() {
        val cart = Cart(
            lines = listOf(line(1, 10_000, Discount.Percent(800), "A"), line(2, 10_000)),
            billDiscount = Discount.Percent(500),
            billDiscountReason = "B",
        )
        // 8% of Rs 100 is Rs 8, then 5% of the Rs 192 left is Rs 9.60: Rs 17.60 of Rs 200 is 8.8% of the bill.
        assertFalse(check(cart, limit = 1_000).billDiscountAboveLimit)
        assertTrue(check(cart, limit = 800).billDiscountAboveLimit)
    }

    @Test
    fun loyaltyPoints_areNotCountedAgainstTheEmployeeLimit() {
        val cart = Cart(
            lines = listOf(line(1, 10_000)),
            loyalty = LoyaltyRedemption(points = 100, amount = Money(5_000)),
            customer = CustomerDetails(customerId = 1),
        )
        val totals = BillCalculator.calculate(cart, mode)
        assertEquals(Money.ZERO, DiscountPolicy.counterDiscount(totals))
        assertFalse(DiscountPolicy.billExceeds(totals, 0))
    }
}
