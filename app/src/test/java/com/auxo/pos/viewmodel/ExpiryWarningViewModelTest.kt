package com.auxo.pos.viewmodel

import com.auxo.pos.domain.inventory.StockFilter
import com.auxo.pos.domain.model.StockBatch
import com.auxo.pos.domain.reports.ExpiryWindow
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.domain.usecase.CreateReorderDraftsUseCase
import com.auxo.pos.domain.usecase.FindByBarcodeUseCase
import com.auxo.pos.domain.usecase.ObserveInventorySummaryUseCase
import com.auxo.pos.domain.usecase.RecordWastageUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakePurchasingRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import com.auxo.pos.testutil.keepActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiryWarningViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = TestData.today
    private val settings = FakeSettingsRepository(TestData.settings.copy(expiryWarningDays = 7))

    /** Juice expires in 10 days; these biscuits have one batch 3 days out and one that expired yesterday. */
    private val biscuits = TestData.biscuits().let { product ->
        val variant = product.variants[0]
        product.copy(
            variants = listOf(
                variant.copy(
                    batches = listOf(
                        StockBatch(id = 1, variantId = variant.id, batchNumber = "OLD", expiryDate = today.minusDays(1), quantity = 4),
                        StockBatch(id = 2, variantId = variant.id, batchNumber = "NEW", expiryDate = today.plusDays(3), quantity = 30),
                    ),
                ),
            ),
        )
    }
    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.juice(stock = 5), biscuits))

    @Test
    fun theHomeSummary_usesTheSavedWindow_andFollowsChanges() = runTest {
        val summary = ObserveInventorySummaryUseCase(inventory, TestData.clock, settings)
        // Biscuit variant expiry is its earliest batch date; juice is 10 days out.
        val narrow = summary().first()
        settings.saveSettings(settings.getSettings().copy(expiryWarningDays = 15))
        val wide = summary().first()
        assertEquals(narrow.expiringSoonCount + 1, wide.expiringSoonCount)
        assertEquals(narrow.expiredCount, wide.expiredCount)
    }

    @Test
    fun withoutSettings_theSummaryUsesThirtyDays() = runTest {
        val withDefault = ObserveInventorySummaryUseCase(inventory, TestData.clock)().first()
        val thirty = ObserveInventorySummaryUseCase(inventory, TestData.clock, FakeSettingsRepository(TestData.settings.copy(expiryWarningDays = 30)))().first()
        assertEquals(thirty, withDefault)
    }

    @Test
    fun theInventoryList_usesTheWindowForItsExpiringFilter() = runTest {
        val viewModel = InventoryViewModel(inventory, FindByBarcodeUseCase(inventory), TestData.clock, StockFilter.EXPIRING_SOON, settings)
        keepActive(viewModel.uiState)
        assertEquals(7, viewModel.uiState.value.warningDays)
        val narrow = viewModel.uiState.value.products.map { it.name }
        settings.saveSettings(settings.getSettings().copy(expiryWarningDays = 15))
        val wide = viewModel.uiState.value.products.map { it.name }
        assertEquals(15, viewModel.uiState.value.warningDays)
        assertEquals(listOf("Mango Juice"), wide - narrow.toSet())
    }

    @Test
    fun theExpiryTab_opensOnTheShopsWindow_untilThePersonPicksAnother() = runTest {
        val purchasing = FakePurchasingRepository(inventory, emptyList())
        val viewModel = StockAlertsViewModel(
            initialTab = AlertsTab.EXPIRY,
            inventory = inventory,
            purchasing = purchasing,
            createReorderDrafts = CreateReorderDraftsUseCase(purchasing),
            recordWastage = RecordWastageUseCase(inventory),
            clock = TestData.clock,
            settings = settings,
        )
        keepActive(viewModel.uiState)
        assertEquals(ExpiryWindow.WITHIN_7_DAYS, viewModel.uiState.value.expiryWindow)
        settings.saveSettings(settings.getSettings().copy(expiryWarningDays = 15))
        assertEquals(ExpiryWindow.WITHIN_15_DAYS, viewModel.uiState.value.expiryWindow)
        viewModel.onExpiryWindowChange(ExpiryWindow.EXPIRED)
        settings.saveSettings(settings.getSettings().copy(expiryWarningDays = 30))
        assertEquals(ExpiryWindow.EXPIRED, viewModel.uiState.value.expiryWindow)
        assertEquals(listOf("OLD"), viewModel.uiState.value.batches.map { it.batch.batchNumber })
    }

    @Test
    fun billing_countsItemsWithExpiredStock_andNothingElse() = runTest {
        val billing = FakeBillingRepository(inventory)
        val viewModel = BillingViewModel(inventory, settings, CheckoutUseCase(billing, settings, TestData.clock), clock = TestData.clock)
        keepActive(viewModel.uiState)
        assertEquals(1, viewModel.uiState.value.expiredItemCount)

        val clean = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
        val cleanVm = BillingViewModel(clean, settings, CheckoutUseCase(FakeBillingRepository(clean), settings, TestData.clock), clock = TestData.clock)
        keepActive(cleanVm.uiState)
        assertEquals(0, cleanVm.uiState.value.expiredItemCount)
    }
}
