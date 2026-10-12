package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BestSellersTest {

    private val shirt = TestData.tShirt()
    private val biscuits = TestData.biscuits()
    private val juice = TestData.juice()

    private fun sale(product: Product, quantity: Int): Invoice {
        val cart = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity = quantity)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        return SaleBuilder.build(
            cart, totals, TestData.settings,
            listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)),
            TestData.clock.millis(),
        )
    }

    private fun id(product: Product) = product.variants[0].id

    @Test
    fun noSales_givesNoBestSellers() {
        assertTrue(BestSellers.rank(emptyList()).isEmpty())
    }

    @Test
    fun mostUnitsSoldComesFirst_addingUpAcrossBills() {
        val bills = listOf(sale(shirt, 3), sale(biscuits, 2), sale(biscuits, 2), sale(juice, 1))
        assertEquals(listOf(id(biscuits), id(shirt), id(juice)), BestSellers.rank(bills))
    }

    @Test
    fun returns_takeUnitsOff_andFullyReturnedItemsDropOut() {
        val soldShirts = sale(shirt, 3)
        val returned = soldShirts.copy(type = InvoiceType.RETURN)
        val bills = listOf(soldShirts, returned, sale(biscuits, 1))
        assertEquals(listOf(id(biscuits)), BestSellers.rank(bills))
    }

    @Test
    fun equalSales_areOrderedByIdSoTheListIsStable() {
        val bills = listOf(sale(biscuits, 2), sale(shirt, 2))
        assertEquals(listOf(id(shirt), id(biscuits)).sorted(), BestSellers.rank(bills))
    }
}
