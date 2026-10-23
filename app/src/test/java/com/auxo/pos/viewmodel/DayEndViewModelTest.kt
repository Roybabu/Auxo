// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.domain.dayend.CashStatus
import com.auxo.pos.domain.dayend.CountError
import com.auxo.pos.domain.dayend.DayClosing
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.FakeDayEndRepository
import com.auxo.pos.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayEndViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 12)
    private val repository = FakeDayEndRepository(today)

    private fun viewModel() = DayEndViewModel(repository)

    @Test
    fun opens_withTodaysFigures_andNothingCounted() {
        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertEquals(today, state.date)
        assertEquals(Money(1_250_00), state.figures?.expectedCash)
        assertNull(state.closedToday)
        assertNull(state.comparison)
    }

    @Test
    fun typingTheCount_showsTheDifferenceAtOnce() {
        val vm = viewModel()
        vm.onCountedChange("1200")
        assertEquals(Money(-50_00), vm.uiState.value.comparison?.difference)
        assertEquals(CashStatus.SHORT, vm.uiState.value.comparison?.status)
        vm.onCountedChange("1250")
        assertEquals(CashStatus.MATCHES, vm.uiState.value.comparison?.status)
        vm.onCountedChange("1251.5")
        assertEquals(CashStatus.OVER, vm.uiState.value.comparison?.status)
    }

    @Test
    fun theCount_acceptsOnlyAmounts() {
        val vm = viewModel()
        vm.onCountedChange("12a5,0.999")
        assertEquals("1250.99", vm.uiState.value.counted)
    }

    @Test
    fun closingWithoutACount_isRefused_andAsksForNothing() {
        val vm = viewModel()
        vm.requestClose()
        assertEquals(CountError.REQUIRED, vm.uiState.value.countError)
        assertFalse(vm.uiState.value.confirming)
        assertEquals(0, repository.closeCalls)
    }

    @Test
    fun closing_asksForConfirmation_thenSavesTheCountAndNote() = runTest {
        val vm = viewModel()
        vm.onCountedChange("1200")
        vm.onNoteChange("Ten-rupee notes short")
        vm.requestClose()
        assertTrue(vm.uiState.value.confirming)
        assertEquals(0, repository.closeCalls) // nothing is saved until confirmed

        vm.confirmClose()
        val event = vm.events.first()
        assertTrue(event is DayEndEvent.Closed)
        assertEquals(1, repository.closeCalls)
        assertEquals("Ten-rupee notes short", repository.lastNote)
        val closed: DayClosing = vm.uiState.value.closedToday!!
        assertEquals(Money(1_200_00), closed.counted)
        assertEquals(Money(-50_00), closed.difference)
        assertNull(vm.uiState.value.figures) // the open-day form is gone
    }

    @Test
    fun cancellingTheConfirmation_savesNothing() {
        val vm = viewModel()
        vm.onCountedChange("1250")
        vm.requestClose()
        vm.cancelClose()
        assertFalse(vm.uiState.value.confirming)
        assertEquals(0, repository.closeCalls)
        assertNull(vm.uiState.value.closedToday)
    }

    @Test
    fun confirmationUsesTheLatestFigures() {
        val vm = viewModel()
        vm.onCountedChange("1250")
        // A bill is made after the screen was opened.
        repository.figures = repository.figures.copy(cashReceived = Money(1_100_00))
        vm.requestClose()
        assertEquals(Money(1_350_00), vm.uiState.value.figures?.expectedCash)
        assertEquals(CashStatus.SHORT, vm.uiState.value.comparison?.status)
    }

    @Test
    fun aDayThatIsAlreadyClosed_showsTheClosing_andCannotBeClosedAgain() = runTest {
        val first = viewModel()
        first.onCountedChange("1250")
        first.requestClose()
        first.confirmClose()
        first.events.first()

        val second = viewModel()
        assertEquals(Money(1_250_00), second.uiState.value.closedToday?.counted)
        second.onCountedChange("1")
        second.requestClose()
        second.confirmClose()
        assertEquals(1, repository.closeCalls)
    }

    @Test
    fun earlierDays_areListedLatestFirst_andNotMixedWithToday() {
        val older = DayClosing(1, FakeDayEndRepository.sampleFigures(today.minusDays(2)), Money(1_250_00), null, 5L, "Test Admin")
        val newer = DayClosing(2, FakeDayEndRepository.sampleFigures(today.minusDays(1)), Money(1_240_00), null, 6L, "Test Admin")
        val repo = FakeDayEndRepository(today, initial = listOf(newer, older))
        val state = DayEndViewModel(repo).uiState.value
        assertEquals(listOf(2L, 1L), state.history.map { it.id })
        assertNull(state.closedToday)
        assertTrue(state.figures != null)
    }

    @Test
    fun aFailingRepository_showsAnErrorThatCanBeRetried() {
        repository.failWith = IllegalStateException("db")
        val vm = viewModel()
        assertTrue(vm.uiState.value.loadFailed)
        repository.failWith = null
        vm.load()
        assertFalse(vm.uiState.value.loadFailed)
        assertEquals(today, vm.uiState.value.date)
    }

    @Test
    fun aFailureWhileClosing_isReported_andNothingIsClosed() = runTest {
        val vm = viewModel()
        vm.onCountedChange("1250")
        vm.requestClose()
        repository.failWith = IllegalStateException("db")
        vm.confirmClose()
        assertEquals(DayEndEvent.Failed, vm.events.first())
        assertNull(vm.uiState.value.closedToday)
        assertFalse(vm.uiState.value.isSaving)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayClosingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 12)
    private val closing = DayClosing(7, FakeDayEndRepository.sampleFigures(today), Money(1_240_00), "Note", 9L, "Test Admin")

    @Test
    fun loadsTheClosing() {
        val vm = DayClosingViewModel(7, FakeDayEndRepository(today, initial = listOf(closing)))
        assertEquals(closing, vm.uiState.value.closing)
        assertFalse(vm.uiState.value.notFound)
    }

    @Test
    fun anUnknownClosing_isNotFound() {
        val vm = DayClosingViewModel(99, FakeDayEndRepository(today))
        assertTrue(vm.uiState.value.notFound)
        assertNull(vm.uiState.value.closing)
    }

    @Test
    fun aFailure_canBeRetried() {
        val repo = FakeDayEndRepository(today, initial = listOf(closing)).apply { failWith = IllegalStateException("db") }
        val vm = DayClosingViewModel(7, repo)
        assertTrue(vm.uiState.value.loadFailed)
        repo.failWith = null
        vm.load()
        assertEquals(closing, vm.uiState.value.closing)
    }
}
