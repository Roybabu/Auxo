// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.loyalty.LoyaltyRedemption
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Loyalty points as a bill-level discount: taken off after the other discounts, before tax. */
class LoyaltyCalculationTest {

    private val inclusive = TaxMode(pricesIncludeTax = true, roundOff = false)
    private val exclusive = TaxMode(pricesIncludeTax = false, roundOff = false)

    private fun line(id: Long, price: Long, qty: Int = 1, rate: GstRate? = GstRate.Fixed(500), discount: Discount? = null) = CartLine(
        variantId = id, productId = id, productName = "Item $id", variantLabel = "", barcode = "", hsnCode = null,
        unitPrice = Money(price), mrp = null, gstRate = rate, quantity = qty, discount = discount, availableStock = 100,
    )

    private fun cart(vararg lines: CartLine, bill: Discount? = null, points: Int = 0, value: Long = points * 100L, customer: Long? = 1L) = Cart(
        lines = lines.toList(),
        billDiscount = bill,
        loyalty = if (points > 0) LoyaltyRedemption(points, Money(value)) else null,
        customer = CustomerDetails(name = "Anita", customerId = customer),
    )

    @Test
    fun points_takeTheirValueOffTheBill() {
        val totals = BillCalculator.calculate(cart(line(1, 100_000), points = 50), inclusive)
        assertEquals(Money(5_000), totals.loyaltyDiscount)
        assertEquals(Money(5_000), totals.billDiscountTotal)
        assertEquals(Money(95_000), totals.grandTotal)
    }

    @Test
    fun noPoints_changeNothing() {
        val totals = BillCalculator.calculate(cart(line(1, 100_000)), inclusive)
        assertEquals(Money.ZERO, totals.loyaltyDiscount)
        assertEquals(Money(100_000), totals.grandTotal)
    }

    @Test
    fun tax_isWorkedOutOnTheDiscountedValue() {
        // Rs 1,000 + 18% on top, less Rs 100 of points: tax on Rs 900.
        val totals = BillCalculator.calculate(cart(line(1, 100_000, rate = GstRate.Fixed(1_800)), points = 100), exclusive)
        assertEquals(Money(90_000), totals.taxableTotal)
        assertEquals(Money(16_200), totals.taxTotal)
        assertEquals(Money(106_200), totals.grandTotal)
    }

    @Test
    fun points_comeOffAfterTheBillDiscount() {
        // 10% of Rs 1,000 = Rs 100, then 50 points = Rs 50.
        val totals = BillCalculator.calculate(cart(line(1, 100_000), bill = Discount.Percent(1_000), points = 50), inclusive)
        assertEquals(Money(10_000), totals.billDiscountTotal - totals.loyaltyDiscount)
        assertEquals(Money(5_000), totals.loyaltyDiscount)
        assertEquals(Money(85_000), totals.grandTotal)
    }

    @Test
    fun points_areSharedAcrossLines_withNothingLost() {
        val totals = BillCalculator.calculate(cart(line(1, 33_333), line(2, 66_667, rate = GstRate.Fixed(1_200)), points = 77), inclusive)
        assertEquals(Money(7_700), totals.lines.sumOf { it.billDiscountShare.minorUnits }.let { Money(it) })
        assertEquals(Money(92_300), totals.grandTotal)
    }

    @Test
    fun points_neverTakeMoreThanIsLeft() {
        // Rs 100 bill with a Rs 40 discount: only Rs 60 is left for points to take, even if 100 are claimed.
        val totals = BillCalculator.calculate(cart(line(1, 10_000), bill = Discount.Amount(Money(4_000)), points = 100), inclusive)
        assertEquals(Money(6_000), totals.loyaltyDiscount)
        assertEquals(Money.ZERO, totals.grandTotal)
    }

    @Test
    fun lineDiscounts_areAppliedFirst() {
        val totals = BillCalculator.calculate(cart(line(1, 100_000, discount = Discount.Percent(2_000)), points = 50), inclusive)
        assertEquals(Money(20_000), totals.lineDiscountTotal)
        assertEquals(Money(75_000), totals.grandTotal) // 1000 - 200 - 50
    }

    // The validator

    private fun check(cart: Cart): CartCheck {
        val totals = BillCalculator.calculate(cart, inclusive)
        return CartValidator.check(cart, totals, gstRegistered = true)
    }

    @Test
    fun aValidRedemption_passes() {
        assertFalse(check(cart(line(1, 100_000), points = 50)).loyaltyInvalid)
        assertTrue(check(cart(line(1, 100_000), points = 50)).canCheckout)
    }

    @Test
    fun points_withoutASavedCustomer_blockCheckout() {
        val result = check(cart(line(1, 100_000), points = 50, customer = null))
        assertTrue(result.loyaltyInvalid)
        assertFalse(result.canCheckout)
    }

    @Test
    fun points_worthMoreThanIsLeft_blockCheckout() {
        val result = check(cart(line(1, 10_000), bill = Discount.Amount(Money(4_000)), points = 100))
        assertTrue(result.loyaltyInvalid)
        assertFalse(result.canCheckout)
    }

    @Test
    fun aBillBuiltWithPoints_remembersHowManyWereUsed() {
        val cart = cart(line(1, 100_000), points = 25)
        val totals = BillCalculator.calculate(cart, inclusive)
        val settings = com.auxo.pos.testutil.TestData.settings
        val invoice = SaleBuilder.build(cart, totals, settings, listOf(com.auxo.pos.domain.model.PaymentRecord(method = com.auxo.pos.domain.model.PaymentMethod.CASH, amount = totals.grandTotal)), 1L)
        assertEquals(25, invoice.loyaltyPointsRedeemed)
        assertEquals(Money(2_500), invoice.discountTotal)
        assertEquals(0, SaleBuilder.build(cart.copy(loyalty = null), BillCalculator.calculate(cart.copy(loyalty = null), inclusive), settings, listOf(com.auxo.pos.domain.model.PaymentRecord(method = com.auxo.pos.domain.model.PaymentMethod.CASH, amount = Money(100_000))), 1L).loyaltyPointsRedeemed)
    }
}
