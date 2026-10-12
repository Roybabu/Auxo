package com.invision.pos.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.inventory.CountReason
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.usecase.PostStockCountUseCase
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakeStockCountRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import com.invision.pos.testutil.keepActive
import com.invision.pos.testutil.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StockCountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits(), TestData.flour(grams = 5_000)))
    private val counts = FakeStockCountRepository()

    private val m = TestData.tShirt().variants[0].id // 8 in stock
    private val l = TestData.tShirt().variants[1].id // 1 in stock
    private val flour = TestData.flour().variants[0].id // 5 kg

    private fun viewModel() = StockCountViewModel(inventory, counts, PostStockCountUseCase(inventory, counts, TestData.clock))

    private fun stock(variantId: Long) = inventory.products.value.flatMap { it.variants }.first { it.id == variantId }.stockQuantity

    private fun candidate(vm: StockCountViewModel, variantId: Long) =
        vm.uiState.value.searchResults.first { it.variant.id == variantId }

    private fun count(vm: StockCountViewModel, variantId: Long, text: String, search: String = "shirt") {
        vm.onSearchTextChange(search)
        vm.openEditor(candidate(vm, variantId))
        vm.onCountTextChange(text)
        vm.saveCount()
    }

    @Test
    fun beforeACountStarts_thereIsNothingToReview_andStartingOpensOne() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        assertNull(vm.uiState.value.count)
        vm.start()
        assertNotNull(vm.uiState.value.count)
        assertEquals(CountStage.COUNTING, vm.uiState.value.stage)
    }

    @Test
    fun searchingFindsItemsToCount() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        vm.onSearchTextChange("shirt")
        assertEquals(setOf(m, l), vm.uiState.value.searchResults.map { it.variant.id }.toSet())
        vm.onSearchTextChange("")
        assertTrue(vm.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun aScannedBarcode_opensThatItemToCount() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val events = record(vm.events)
        vm.start()
        vm.onBarcodeScanned(TestData.EAN[0])
        assertEquals(m, vm.uiState.value.editor!!.variant.id)
        vm.dismissEditor()
        vm.onBarcodeScanned("0000000000000")
        assertEquals(StockCountEvent.BarcodeNotFound("0000000000000"), events.last())
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun typingACount_savesTheExpectedStockWithIt_andLeavesStockAlone() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        count(vm, m, "5")

        val row = vm.uiState.value.rows.single()
        assertEquals(8, row.line.expected)
        assertEquals(5, row.line.counted)
        assertEquals(-3, row.difference)
        assertEquals(8, stock(m)) // nothing has changed yet
        assertNull(vm.uiState.value.editor)
        assertEquals("", vm.uiState.value.searchText)
    }

    @Test
    fun weighedItems_areTypedInKilograms() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        count(vm, flour, "4.5", search = "flour")
        val line = vm.uiState.value.rows.single().line
        assertEquals(5_000, line.expected)
        assertEquals(4_500, line.counted)
    }

    @Test
    fun anUnusableQuantity_keepsTheBoxOpenWithAnError() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        for (bad in listOf("", "abc", "-1", "1.5")) {
            vm.onSearchTextChange("shirt")
            vm.openEditor(candidate(vm, m))
            vm.onCountTextChange(bad)
            vm.saveCount()
            assertTrue("'$bad' should be refused", vm.uiState.value.editor!!.error)
            assertTrue(vm.uiState.value.rows.isEmpty())
        }
        vm.onCountTextChange("0") // zero is a real count
        vm.saveCount()
        assertEquals(0, vm.uiState.value.rows.single().line.counted)
    }

    @Test
    fun countingAnItemAgain_replacesTheEarlierCount_andOpensWithIt() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        count(vm, m, "5")
        vm.openEditor(vm.uiState.value.rows.single())
        assertEquals("5", vm.uiState.value.editor!!.text)
        vm.onCountTextChange("7")
        vm.saveCount()
        assertEquals(listOf(7), vm.uiState.value.rows.map { it.line.counted })
    }

    @Test
    fun anItemCanBeTakenBackOutOfTheCount() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        count(vm, m, "5")
        count(vm, l, "1")
        assertEquals(2, vm.uiState.value.rows.size)
        vm.removeLine(m)
        assertEquals(listOf(l), vm.uiState.value.rows.map { it.variant.id })
    }

    @Test
    fun reviewShowsOnlyTheDifferences_andTheirValue() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        count(vm, m, "5") // 3 short at 250.00
        count(vm, l, "1") // matches
        vm.goToReview()

        val state = vm.uiState.value
        assertEquals(CountStage.REVIEW, state.stage)
        assertEquals(2, state.rows.size)
        assertEquals(listOf(m), state.differences.map { it.variant.id })
        assertEquals(1, state.summary.short)
        assertEquals(com.invision.pos.domain.model.Money(750_00), state.summary.shortageValue)
        assertEquals(com.invision.pos.domain.model.Money(-750_00), state.netValue)
        vm.backToCounting()
        assertEquals(CountStage.COUNTING, vm.uiState.value.stage)
    }

    @Test
    fun nothingChangesUntilTheConfirmationIsAccepted() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val events = record(vm.events)
        vm.start()
        count(vm, m, "5")
        vm.goToReview()
        vm.onReasonChange(CountReason.SPOT_CHECK)
        vm.onNoteChange("Front shelf")

        vm.requestPost()
        assertTrue(vm.uiState.value.confirmOpen)
        assertEquals(8, stock(m)) // asked, not done

        vm.dismissConfirm()
        assertFalse(vm.uiState.value.confirmOpen)
        assertEquals(8, stock(m))
        assertTrue(events.isEmpty())

        vm.requestPost()
        vm.confirmPost()
        assertEquals(5, stock(m))
        assertEquals(StockCountEvent.Posted(adjusted = 1, matched = 0), events.last())
        assertNull(vm.uiState.value.count)
        assertEquals(CountStage.COUNTING, vm.uiState.value.stage)
        val movement = inventory.movements.value.single { it.reason == StockChangeReason.CORRECTION }
        assertEquals(-3, movement.quantityChange)
        assertEquals("Stock count · Spot check · Front shelf", movement.note)
        assertEquals(Triple(CountReason.SPOT_CHECK, "Front shelf", 1), counts.posted.values.single())
    }

    @Test
    fun aSecondTapOnConfirm_doesNotPostTwice() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        record(vm.events)
        vm.start()
        count(vm, m, "5")
        vm.goToReview()
        vm.requestPost()
        vm.confirmPost()
        vm.confirmPost()
        assertEquals(5, stock(m))
        assertEquals(1, inventory.movements.value.count { it.reason == StockChangeReason.CORRECTION })
    }

    @Test
    fun aCountWithNoDifferences_canJustBeFinished() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val events = record(vm.events)
        vm.start()
        count(vm, l, "1")
        vm.goToReview()
        assertTrue(vm.uiState.value.differences.isEmpty())
        vm.requestPost()
        vm.confirmPost()
        assertEquals(StockCountEvent.Posted(adjusted = 0, matched = 1), events.last())
        assertTrue(inventory.movements.value.none { it.reason == StockChangeReason.CORRECTION })
    }

    @Test
    fun cancellingAsksFirst_thenThrowsTheCountAway() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val events = record(vm.events)
        vm.start()
        count(vm, m, "1")
        vm.requestCancel()
        assertTrue(vm.uiState.value.cancelConfirmOpen)
        vm.dismissCancel()
        assertNotNull(vm.uiState.value.count)

        vm.requestCancel()
        vm.confirmCancel()
        assertNull(vm.uiState.value.count)
        assertEquals(StockCountEvent.Cancelled, events.last())
        assertEquals(8, stock(m))
        assertEquals(1, counts.cancelled.size)
    }

    @Test
    fun theNoteIsKeptShort() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.start()
        vm.onNoteChange("x".repeat(500))
        assertEquals(80, vm.uiState.value.note.length)
    }

    @Test
    fun aFailedSave_isReported_andNothingIsCounted() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val events = record(vm.events)
        vm.start()
        counts.failWrites = true
        count(vm, m, "5")
        assertEquals(StockCountEvent.Failed, events.last())
        assertTrue(vm.uiState.value.rows.isEmpty())
    }
}
