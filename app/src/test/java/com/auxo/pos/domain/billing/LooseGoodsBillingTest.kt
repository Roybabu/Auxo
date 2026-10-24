// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goods weighed at the counter: the price is for a kilogram, the quantity is in grams. */
class LooseGoodsBillingTest {

    private val mode = TaxMode(pricesIncludeTax = true, roundOff = false)

    private fun rice(pricePerKg: Long = 80_00, stockGrams: Int = 25_000, unit: StockUnit = StockUnit.GRAM) = Product(
        id = 7,
        name = "Basmati rice",
        category = ProductCategory.LOOSE,
        stockUnit = unit,
        gstRate = GstRate.Fixed(0),
        variants = listOf(TestData.variant(id = 71, barcode = TestData.EAN[1], price = pricePerKg, stock = stockGrams)),
    )

    private fun line(grams: Int, pricePerKg: Long = 80_00, discount: Discount? = null): CartLine {
        val product = rice(pricePerKg)
        return CartLine.from(product, product.variants.single(), quantity = grams).copy(discount = discount, discountReason = if (discount != null) "Regular" else "")
    }

    private fun totals(vararg lines: CartLine) = BillCalculator.calculate(Cart(lines = lines.toList()), mode)

    @Test
    fun aLooseItem_startsAtOneKilogram_andStepsByAHundredGrams() {
        val product = rice()
        val line = CartLine.from(product, product.variants.single())
        assertEquals(1_000, line.quantity)
        assertEquals(100, line.step)
        assertEquals("1 kg", line.quantityText)
    }

    @Test
    fun pieces_stillStartAtOne_andStepByOne() {
        val product = TestData.tShirt()
        val line = CartLine.from(product, product.variants.first())
        assertEquals(1, line.quantity)
        assertEquals(1, line.step)
    }

    @Test
    fun theLineTotal_isThePricePerKilogramTimesTheWeight() {
        // 1.250 kg at 80 a kilogram.
        assertEquals(Money(100_00), totals(line(1_250)).grandTotal)
        // 0.350 kg at 90 a kilogram is 31.50.
        assertEquals(Money(31_50), totals(line(350, pricePerKg = 90_00)).grandTotal)
    }

    @Test
    fun theAmount_isRoundedToThePaisa() {
        // 0.333 kg at 100.10 a kilogram is 33.33333 -> 33.33.
        assertEquals(Money(33_33), totals(line(333, pricePerKg = 100_10)).grandTotal)
    }

    @Test
    fun aDiscount_appliesToTheWholeWeighedAmount() {
        val t = totals(line(1_250, discount = Discount.Percent(1_000)))
        assertEquals(Money(10_00), t.lines.single().lineDiscount)
        assertEquals(Money(90_00), t.grandTotal)
    }

    @Test
    fun aWeighedLine_countsAsOneItem() {
        val cart = Cart(lines = listOf(line(1_250), CartLine.from(TestData.tShirt(), TestData.tShirt().variants.first(), quantity = 3)))
        assertEquals(4, cart.itemCount)
    }

    @Test
    fun aVolumeItem_isPricedPerLitre() {
        val milk = rice(pricePerKg = 60_00, unit = StockUnit.MILLILITRE)
        val line = CartLine.from(milk, milk.variants.single(), quantity = 1_500)
        assertEquals(Money(90_00), totals(line).grandTotal)
        assertEquals("1.5 L", line.quantityText)
    }

    @Test
    fun theInvoiceLine_keepsTheUnit_andReadsAsWeight() {
        val cart = Cart(lines = listOf(line(1_250)))
        val t = BillCalculator.calculate(cart, mode)
        val invoice = SaleBuilder.build(cart, t, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = t.grandTotal)), TestData.clock.millis())
        val saved = invoice.lines.single()
        assertEquals(StockUnit.GRAM, saved.stockUnit)
        assertEquals(1_250, saved.quantity)
        assertEquals("1.25 kg", saved.quantityText)
        assertEquals(Money(100_00), saved.gross)
        assertEquals(1, invoice.itemCount)
    }

    @Test
    fun returningPartOfTheWeight_refundsThatShareOfTheMoney() {
        val cart = Cart(lines = listOf(line(1_250)))
        val t = BillCalculator.calculate(cart, mode)
        val sale = SaleBuilder.build(cart, t, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = t.grandTotal)), TestData.clock.millis())
        val original = sale.copy(lines = sale.lines.map { it.copy(id = 1) })

        val ready = ReturnBuilder.build(original, mapOf(1L to 500), PaymentMethod.CASH, "Too much", TestData.clock.millis()) as ReturnBuild.Ready
        assertEquals(InvoiceType.RETURN, ready.creditNote.type)
        assertEquals(Money(40_00), ready.creditNote.grandTotal)
        assertEquals(StockUnit.GRAM, ready.creditNote.lines.single().stockUnit)
    }

    @Test
    fun youCannotReturnMoreThanWasSold() {
        val cart = Cart(lines = listOf(line(1_250)))
        val t = BillCalculator.calculate(cart, mode)
        val sale = SaleBuilder.build(cart, t, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = t.grandTotal)), TestData.clock.millis())
        val original = sale.copy(lines = sale.lines.map { it.copy(id = 1) })
        assertTrue(ReturnBuilder.build(original, mapOf(1L to 1_251), PaymentMethod.CASH, "x", TestData.clock.millis()) is ReturnBuild.Rejected)
    }

    @Test
    fun theLimit_isTheStockInGrams() {
        val line = line(24_900)
        assertEquals(25_000, line.availableStock)
        assertTrue(line.canIncrease)
        assertFalse(line.copy(quantity = 25_000).canIncrease)
        assertEquals(25_000, line.withinStock(30_000))
    }
}
