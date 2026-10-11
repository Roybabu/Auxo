package com.invision.pos.viewmodel

import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.testutil.FakeBillingRepository
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakeSettingsRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingBestSellersTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits(), TestData.juice()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()

    private fun viewModel() = BillingViewModel(
        inventory = inventory,
        settingsRepository = settings,
        checkout = CheckoutUseCase(billing, settings, TestData.clock),
        clock = TestData.clock,
        billing = billing,
    )

    private suspend fun sell(product: Product, quantity: Int, daysAgo: Long = 0) {
        val cart = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity = quantity)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val millis = TestData.clock.millis() - daysAgo * 24 * 60 * 60 * 1000
        billing.recordSale(
            SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), millis),
            "INV",
        )
    }

    @Test
    fun withNoSales_thereAreNoBestSellers() = runTest {
        assertTrue(viewModel().uiState.value.bestSellers.isEmpty())
    }

    @Test
    fun bestSellers_areOrderedByUnitsSoldInTheLast30Days() = runTest {
        sell(TestData.tShirt(), 1)
        sell(TestData.biscuits(), 4)
        sell(TestData.biscuits(), 2)
        val names = viewModel().uiState.value.bestSellers.map { it.product.name }
        assertEquals(listOf(TestData.biscuits().name, TestData.tShirt().name), names)
    }

    @Test
    fun salesOlderThan30Days_doNotCount() = runTest {
        sell(TestData.tShirt(), 1)
        sell(TestData.biscuits(), 50, daysAgo = 40)
        val names = viewModel().uiState.value.bestSellers.map { it.product.name }
        assertEquals(listOf(TestData.tShirt().name), names)
    }

    @Test
    fun theListUpdates_afterAnotherSale() = runTest {
        val viewModel = viewModel()
        sell(TestData.tShirt(), 1)
        assertEquals(1, viewModel.uiState.value.bestSellers.size)
        sell(TestData.biscuits(), 3)
        assertEquals(TestData.biscuits().name, viewModel.uiState.value.bestSellers.first().product.name)
    }
}
