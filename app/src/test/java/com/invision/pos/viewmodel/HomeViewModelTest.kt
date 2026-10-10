package com.invision.pos.viewmodel

import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.data.repository.InMemoryStoreRepository
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.StoreOverview
import com.invision.pos.domain.usecase.ObserveStoreOverviewUseCase
import com.invision.pos.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val formatter = MoneyFormatter()

    private fun createViewModel(overview: StoreOverview) = HomeViewModel(
        observeStoreOverview = ObserveStoreOverviewUseCase(InMemoryStoreRepository(overview)),
        moneyFormatter = formatter,
    )

    @Test
    fun uiState_isLoading_beforeAnyoneCollects() {
        val viewModel = createViewModel(InMemoryStoreRepository.DEFAULT_OVERVIEW)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_isReady_withFormattedOverview() = runTest {
        val overview = StoreOverview(
            storeName = "Roy Mart",
            salesToday = Money(125_050),
            billsToday = 12,
        )
        val viewModel = createViewModel(overview)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertEquals(
            HomeUiState.Ready(
                storeName = "Roy Mart",
                salesToday = formatter.format(Money(125_050)),
                billsToday = 12,
            ),
            viewModel.uiState.value,
        )
    }
}
