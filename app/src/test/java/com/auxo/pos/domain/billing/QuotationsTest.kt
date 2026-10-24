// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuotationsTest {

    private val shirt = TestData.tShirt()
    private val variant = shirt.variants[0]

    private fun summary(status: QuotationStatus = QuotationStatus.OPEN, until: LocalDate = LocalDate.of(2026, 10, 20)) = QuotationSummary(
        1, "QT/26-27/00001", 0, until, "Asha", "", 1, Money(100_00), status, null,
    )

    @Test
    fun validity_isCountedInDaysFromTheDayItIsMade() {
        assertEquals(LocalDate.of(2026, 10, 25), Quotations.validUntil(LocalDate.of(2026, 10, 10), 15))
        assertEquals(LocalDate.of(2026, 10, 10), Quotations.validUntil(LocalDate.of(2026, 10, 10), -3))
        assertTrue(Quotations.DEFAULT_VALIDITY_DAYS in Quotations.VALIDITY_DAYS)
    }

    @Test
    fun numbers_haveTheirOwnPrefixAndFinancialYear() {
        assertEquals("QT/26-27/00001", Quotations.number(2026, 1))
        assertEquals("QT|2026", Quotations.sequenceKey(2026))
    }

    @Test
    fun aQuotation_isExpiredOnlyAfterItsLastDay_andOnlyWhileOpen() {
        val until = LocalDate.of(2026, 10, 20)
        assertFalse(summary().isExpired(until))
        assertTrue(summary().isExpired(until.plusDays(1)))
        assertFalse(summary(QuotationStatus.CONVERTED).isExpired(until.plusDays(1)))
    }

    @Test
    fun reprice_followsTodaysPrice_keepingQuantityAndDiscount() {
        val old = CartLine.from(shirt, variant, 3).copy(unitPrice = Money(399_00), discount = Discount.Percent(1_000), discountReason = "Festival")
        val cart = Cart(lines = listOf(old))
        val repriced = QuotationRepricing.reprice(cart, listOf(shirt))
        val line = repriced.cart.lines.single()
        assertEquals(variant.sellingPrice, line.unitPrice)
        assertEquals(3, line.quantity)
        assertEquals(Discount.Percent(1_000), line.discount)
        assertEquals("Festival", line.discountReason)
        assertEquals(listOf(shirt.name), repriced.changed)
    }

    @Test
    fun reprice_reportsNothingWhenPricesHaveNotMoved() {
        val cart = Cart(lines = listOf(CartLine.from(shirt, variant, 1)))
        assertTrue(QuotationRepricing.reprice(cart, listOf(shirt)).changed.isEmpty())
    }

    @Test
    fun reprice_leavesCustomItemsAndMissingProductsAlone_andUpdatesDishes() {
        val custom = CartLine.custom(CartLine.CUSTOM_KEY_BASE, CustomItemDraft("Alteration", Money(150_00), 1, 1_800))
        val gone = CartLine.from(shirt, variant, 1).copy(variantId = 999)
        val dish = TestData.dish(id = 40, name = "Masala Dosa", price = Money(100_00))
        val oldDish = CartLine.from(dish, dish.variants.single(), 2).copy(unitPrice = Money(90_00))
        val repriced = QuotationRepricing.reprice(Cart(lines = listOf(custom, gone, oldDish)), listOf(shirt, dish))
        assertEquals(custom, repriced.cart.lines[0])
        assertEquals(gone, repriced.cart.lines[1])
        assertEquals(Money(100_00), repriced.cart.lines[2].unitPrice)
        assertEquals(2, repriced.cart.lines[2].quantity)
        assertEquals(listOf("Masala Dosa"), repriced.changed)
    }

    @Test
    fun reprice_reportsATaxRateChange() {
        val old = CartLine.from(shirt, variant, 1).copy(gstRate = GstRate.Fixed(1_200))
        val repriced = QuotationRepricing.reprice(Cart(lines = listOf(old)), listOf(shirt))
        assertEquals(shirt.gstRate, repriced.cart.lines.single().gstRate)
        assertEquals(listOf(shirt.name), repriced.changed)
    }
}
