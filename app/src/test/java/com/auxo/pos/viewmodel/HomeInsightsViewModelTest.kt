// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.data.repository.BillingRepository
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.insights.Department
import com.auxo.pos.domain.insights.InsightsPeriod
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakePurchasingRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeInsightsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val purchasing = FakePurchasingRepository(inventory)

    private fun viewModel(repository: BillingRepository = billing) =
        HomeInsightsViewModel(repository, inventory, purchasing, TestData.clock)

    private suspend fun sell(quantity: Int = 2) {
        val shirt = TestData.tShirt()
        val cart = Cart(lines = listOf(CartLine.from(shirt, shirt.variants[0], quantity = quantity)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        billing.recordSale(
            SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.UPI, amount = totals.grandTotal)), TestData.clock.millis()),
            "INV",
        )
    }

    @Test
    fun startsOnTheLastSevenDays_andBuildsTheCharts() = runTest {
        sell()
        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.failed)
        assertEquals(InsightsPeriod.LAST_7_DAYS, state.period)
        val insights = state.insights!!
        assertEquals(1, insights.billCount)
        assertEquals(7, insights.trend.size)
        assertEquals(listOf(PaymentMethod.UPI), insights.paymentMethods.map { it.method })
        assertEquals(listOf(Department.GARMENTS), insights.departments.map { it.department })
        assertEquals("Cotton T-Shirt", insights.topProducts.single().name)
    }

    @Test
    fun headerFigures_showTodaysUpi_whateverPeriodTheChartsUse() = runTest {
        sell()
        val viewModel = viewModel()
        val upi = viewModel.uiState.value.collectedToday!!.upi
        assertTrue(upi.minorUnits > 0)
        assertEquals(0L, viewModel.uiState.value.collectedToday!!.cash.minorUnits)

        viewModel.onPeriodChange(InsightsPeriod.THIS_YEAR)
        assertEquals(upi, viewModel.uiState.value.collectedToday!!.upi)
    }

    @Test
    fun headerFigures_areZeroWithNoBills() = runTest {
        val collected = viewModel().uiState.value.collectedToday!!
        assertEquals(0L, collected.cash.minorUnits)
        assertEquals(0L, collected.upi.minorUnits)
    }

    @Test
    fun withNoActivity_buildsEmptyCharts_notAnError() = runTest {
        val state = viewModel().uiState.value
        assertFalse(state.failed)
        val insights = state.insights!!
        assertFalse(insights.hasSales)
        assertFalse(insights.hasPurchases)
    }

    @Test
    fun changingThePeriod_rebuildsForThatPeriod() = runTest {
        sell()
        val viewModel = viewModel()

        viewModel.onPeriodChange(InsightsPeriod.TODAY)
        assertEquals(InsightsPeriod.TODAY, viewModel.uiState.value.period)
        assertEquals(12, viewModel.uiState.value.insights!!.trend.size)
        assertEquals(1, viewModel.uiState.value.insights!!.billCount)

        viewModel.onPeriodChange(InsightsPeriod.THIS_YEAR)
        assertEquals(7, viewModel.uiState.value.insights!!.trend.size)
        assertEquals(InsightsPeriod.THIS_YEAR, viewModel.uiState.value.insights!!.period)
    }

    @Test
    fun theChartsRefresh_whenANewBillIsSaved() = runTest {
        val viewModel = viewModel()
        assertEquals(0, viewModel.uiState.value.insights!!.billCount)
        sell()
        assertEquals(1, viewModel.uiState.value.insights!!.billCount)
        sell(quantity = 1)
        assertEquals(2, viewModel.uiState.value.insights!!.billCount)
    }

    @Test
    fun aFailureToLoad_isReported_andRetryRecovers() = runTest {
        var failing = true
        val flaky = object : BillingRepository by billing {
            override suspend fun invoicesBetween(fromMillis: Long, toMillis: Long): List<Invoice> {
                if (failing) throw IllegalStateException("database unavailable")
                return billing.invoicesBetween(fromMillis, toMillis)
            }
        }
        val viewModel = viewModel(flaky)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.failed)
        assertNull(viewModel.uiState.value.insights)

        failing = false
        viewModel.retry()
        assertFalse(viewModel.uiState.value.failed)
        assertNotNull(viewModel.uiState.value.insights)
    }
}
