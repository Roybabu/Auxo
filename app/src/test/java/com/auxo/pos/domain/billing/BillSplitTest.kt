// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.loyalty.LoyaltyRedemption
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSplitTest {

    private fun line(id: Long, quantity: Int, price: Long = 10_000, discount: Discount? = null, unit: StockUnit = StockUnit.UNIT) = CartLine(
        variantId = id, productId = id, productName = "Item $id", variantLabel = "", barcode = "", hsnCode = null,
        unitPrice = Money(price), mrp = null, gstRate = null, quantity = quantity, discount = discount,
        discountReason = if (discount != null) "test" else "", availableStock = null, stockUnit = unit,
    )

    private fun ok(cart: Cart, quantities: Map<Long, Int>) = BillSplit.splitOff(cart, quantities) as SplitOutcome.Ok

    private fun error(cart: Cart, quantities: Map<Long, Int>) = (BillSplit.splitOff(cart, quantities) as SplitOutcome.Failed).error

    @Test
    fun equalShares_alwaysAddUpToTheTotal_withTheExtraPaisaGoingToTheFirstPeople() {
        val shares = BillSplit.equalShares(Money(10_001), 3)
        assertEquals(listOf(Money(3_334), Money(3_334), Money(3_333)), shares)
        assertEquals(Money(10_001), shares.fold(Money.ZERO) { a, b -> a + b })
        assertEquals(List(4) { Money(2_500) }, BillSplit.equalShares(Money(10_000), 4))
    }

    @Test
    fun equalShares_keepThePeopleCountWithinLimits() {
        assertEquals(BillSplit.MIN_PEOPLE, BillSplit.equalShares(Money(100), 1).size)
        assertEquals(BillSplit.MAX_PEOPLE, BillSplit.equalShares(Money(100), 500).size)
    }

    @Test
    fun wholeLines_moveAcross() {
        val cart = Cart(lines = listOf(line(1, 2), line(2, 1), line(3, 4)))
        val result = ok(cart, mapOf(2L to 1, 3L to 4))
        assertEquals(listOf(1L), result.remaining.lines.map { it.variantId })
        assertEquals(listOf(2L, 3L), result.moved.lines.map { it.variantId })
    }

    @Test
    fun aPartOfALine_staysAndMoves_withTheUnitsAddingUp() {
        val cart = Cart(lines = listOf(line(1, 5)))
        val result = ok(cart, mapOf(1L to 2))
        assertEquals(3, result.remaining.lines.single().quantity)
        assertEquals(2, result.moved.lines.single().quantity)
    }

    @Test
    fun aFixedLineDiscount_isSharedByQuantity_andNothingIsLost() {
        val cart = Cart(lines = listOf(line(1, 3, discount = Discount.Amount(Money(1_000)))))
        val result = ok(cart, mapOf(1L to 1))
        val rest = (result.remaining.lines.single().discount as Discount.Amount).value
        val moved = (result.moved.lines.single().discount as Discount.Amount).value
        assertEquals(Money(333), moved)
        assertEquals(Money(1_000), rest + moved)
    }

    @Test
    fun aPercentageLineDiscount_goesWithTheUnits() {
        val cart = Cart(lines = listOf(line(1, 4, discount = Discount.Percent(1_000))))
        val result = ok(cart, mapOf(1L to 1))
        assertEquals(Discount.Percent(1_000), result.remaining.lines.single().discount)
        assertEquals(Discount.Percent(1_000), result.moved.lines.single().discount)
    }

    @Test
    fun theTwoBills_addUpToTheOriginal() {
        val cart = Cart(lines = listOf(line(1, 5, price = 3_333), line(2, 2, price = 999)))
        val result = ok(cart, mapOf(1L to 2, 2L to 2))
        val mode = TestData.settings.taxModeFor(cart)
        val whole = BillCalculator.calculate(cart, mode).subtotal
        val parts = BillCalculator.calculate(result.remaining, mode).subtotal + BillCalculator.calculate(result.moved, mode).subtotal
        assertEquals(whole, parts)
    }

    @Test
    fun aLineSoldByWeight_movesWholeOrNotAtAll() {
        val cart = Cart(lines = listOf(line(1, 1_500, unit = StockUnit.GRAM), line(2, 1)))
        assertEquals(SplitError.INVALID, error(cart, mapOf(1L to 500)))
        assertEquals(listOf(1L), ok(cart, mapOf(1L to 1_500)).moved.lines.map { it.variantId })
    }

    @Test
    fun badChoices_areRefused() {
        val cart = Cart(lines = listOf(line(1, 2), line(2, 1)))
        assertEquals(SplitError.NOTHING_CHOSEN, error(cart, emptyMap()))
        assertEquals(SplitError.NOTHING_CHOSEN, error(cart, mapOf(1L to 0)))
        assertEquals(SplitError.NOTHING_LEFT, error(cart, mapOf(1L to 2, 2L to 1)))
        assertEquals(SplitError.INVALID, error(cart, mapOf(1L to 3)))
        assertEquals(SplitError.INVALID, error(cart, mapOf(99L to 1)))
    }

    @Test
    fun aBillWithABillDiscountOrPoints_cannotBeSplit() {
        val discounted = Cart(lines = listOf(line(1, 2), line(2, 1)), billDiscount = Discount.Percent(500), billDiscountReason = "regular")
        assertEquals(SplitError.BILL_DISCOUNT, error(discounted, mapOf(1L to 1)))
        val points = Cart(lines = listOf(line(1, 2)), loyalty = LoyaltyRedemption(10, Money(1_000)))
        assertEquals(SplitError.POINTS, error(points, mapOf(1L to 1)))
    }

    @Test
    fun theMovedBill_keepsTheCustomer() {
        val customer = CustomerDetails(name = "Anita", phone = "9830012345")
        val result = ok(Cart(lines = listOf(line(1, 2)), customer = customer), mapOf(1L to 1))
        assertEquals(customer, result.moved.customer)
        assertNull(result.moved.billDiscount)
        assertTrue(result.remaining.customer == customer)
    }

    @Test
    fun splitting_isOfferedOnARestaurantBill_butNotOnAShopBill() {
        val dish = line(1, 1).copy(isRestaurantItem = true)
        assertTrue(BillSplit.isOffered(Cart(lines = listOf(dish))))
        // A restaurant dish anywhere on the bill is enough.
        assertTrue(BillSplit.isOffered(Cart(lines = listOf(line(2, 1), dish))))
        assertFalse(BillSplit.isOffered(Cart(lines = listOf(line(2, 1)))))
        assertFalse(BillSplit.isOffered(Cart()))
    }
}
