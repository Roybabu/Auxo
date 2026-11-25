// SPDX-License-Identifier: GPL-3.0-or-later
package com.auxo.pos.domain.insights

import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfitBuilderTest {
    private val product = TestData.tShirt().let { p ->
        p.copy(variants = p.variants.map { it.copy(costPrice = Money(10000)) })
    }
    private val millis = TestData.today.atTime(10, 0).atZone(TestData.clock.zone).toInstant().toEpochMilli()
    private fun sale() = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity = 2))).let { cart ->
        SaleBuilder.build(cart, BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart)), TestData.settings, emptyList(), millis)
    }

    @Test fun profitUsesTaxableRevenueAndCostAndReturnsReverseBoth() {
        val invoice = sale()
        val result = ProfitBuilder.build(listOf(invoice), listOf(product), TestData.clock.zone)
        assertEquals(invoice.lines.single().taxable, result.sales)
        assertEquals(Money(20000), result.costs)
        assertEquals(result.sales - result.costs, result.profit)
        assertEquals(result.profit, result.products.single().amount)
        assertEquals(result.profit, result.categories.single().amount)
        assertEquals(result.profit, result.days.single().amount)
        val returned = ProfitBuilder.build(listOf(invoice, invoice.copy(type = InvoiceType.RETURN)), listOf(product), TestData.clock.zone)
        assertEquals(Money.ZERO, returned.profit)
        assertEquals(Money.ZERO, returned.sales)
        assertEquals(Money.ZERO, returned.costs)
    }

    @Test fun missingCostsAreExcludedAndReported() {
        val invoice = sale()
        val missing = product.copy(variants = product.variants.map { it.copy(costPrice = null) })
        val result = ProfitBuilder.build(listOf(invoice), listOf(missing), TestData.clock.zone)
        assertEquals(1, result.missingLines)
        assertEquals(invoice.lines.single().taxable, result.missingSales)
        assertEquals(0, result.knownLines)
        assertEquals(Money.ZERO, result.profit)
    }
}
