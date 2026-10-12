package com.auxo.pos.viewmodel

import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.usecase.ObserveInventorySummaryUseCase
import com.auxo.pos.domain.usecase.ObserveStoreOverviewUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val formatter = MoneyFormatter()

    private fun createViewModel(
        inventory: FakeInventoryRepository = FakeInventoryRepository(),
        billing: FakeBillingRepository = FakeBillingRepository(inventory),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = HomeViewModel(
        observeStoreOverview = ObserveStoreOverviewUseCase(settings, billing, TestData.clock),
        observeInventorySummary = ObserveInventorySummaryUseCase(inventory, TestData.clock),
        moneyFormatter = formatter,
    )

    @Test
    fun uiState_isLoading_beforeAnyoneCollects() {
        assertEquals(HomeUiState.Loading, createViewModel().uiState.value)
    }

    @Test
    fun uiState_combinesSalesAndInventory() = runTest {
        val repository = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.juice(stock = 0)))
        val viewModel = createViewModel(inventory = repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals("Roy Mart", state.storeName)
        assertEquals(formatter.format(Money.ZERO), state.salesToday)
        assertEquals(0, state.billsToday)
        assertEquals(2, state.inventory.productCount)
        assertEquals(1, state.inventory.outOfStockCount)
        assertEquals(formatter.format(Money(9 * 25_000L)), state.stockValueAtCost)
    }

    @Test
    fun uiState_updatesWhenInventoryChanges() = runTest {
        val repository = FakeInventoryRepository()
        val viewModel = createViewModel(inventory = repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertEquals(0, (viewModel.uiState.value as HomeUiState.Ready).inventory.productCount)

        repository.products.value = listOf(TestData.biscuits())
        assertEquals(1, (viewModel.uiState.value as HomeUiState.Ready).inventory.productCount)
    }

    @Test
    fun uiState_failsGracefully_andRetryRecovers() = runTest {
        val repository = FakeInventoryRepository().apply { failReads = true }
        val viewModel = createViewModel(inventory = repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertEquals(HomeUiState.Failed, viewModel.uiState.value)

        repository.failReads = false
        viewModel.retry()
        assertTrue(viewModel.uiState.value is HomeUiState.Ready)
    }
}
