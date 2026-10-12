package com.auxo.pos.viewmodel

import com.auxo.pos.core.auth.FixedAuthorizer
import com.auxo.pos.core.auth.Role
import com.auxo.pos.data.security.GstReportService
import com.auxo.pos.domain.gst.GstTestData
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class GstReportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val billing = FakeBillingRepository(FakeInventoryRepository(emptyList())).apply { invoices.value = GstTestData.all }

    /** Today is 5 November 2026, so the report opens on October. */
    private val clock = Clock.fixed(LocalDate.of(2026, 11, 5).atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private fun viewModel(role: Role? = Role.SUPER_ADMIN) = GstReportViewModel(GstReportService(billing, FixedAuthorizer(role)), clock)

    @Test
    fun opensOnLastMonth_withItsReport() {
        val state = viewModel().uiState.value
        assertEquals(YearMonth.of(2026, 10), state.month)
        assertEquals(GstPeriodMode.MONTH, state.mode)
        assertFalse(state.isLoading)
        assertEquals(Money(3_535_00), state.report?.net?.invoiceValue)
        assertEquals(LocalDate.of(2026, 10, 1), state.period.from)
        assertEquals(LocalDate.of(2026, 10, 31), state.period.to)
    }

    @Test
    fun previousMonth_loadsThatMonth() {
        val vm = viewModel()
        vm.previousMonth()
        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
        assertTrue(vm.uiState.value.report!!.isEmpty)
        vm.nextMonth()
        assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)
        assertFalse(vm.uiState.value.report!!.isEmpty)
    }

    @Test
    fun nextMonth_stopsAtTheCurrentMonth() {
        val vm = viewModel()
        assertTrue(vm.uiState.value.canGoNext)
        vm.nextMonth() // November: this month
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.month)
        assertFalse(vm.uiState.value.canGoNext)
        vm.nextMonth() // December: not offered
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.month)
    }

    @Test
    fun aDateRange_isUsedAsChosen_andSwappedIfBackwards() {
        val vm = viewModel()
        vm.onRangeChange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 3))
        val state = vm.uiState.value
        assertEquals(GstPeriodMode.RANGE, state.mode)
        assertEquals(LocalDate.of(2026, 10, 3), state.from)
        assertEquals(LocalDate.of(2026, 10, 12), state.to)
        // A, B, C and D fall in these days; E (14th) does not.
        assertEquals(3, state.report?.sales)
        assertEquals(1, state.report?.creditNotes)
    }

    @Test
    fun thisFinancialYear_runsFromTheFirstOfAprilToToday() {
        val vm = viewModel()
        vm.thisFinancialYear()
        assertEquals(LocalDate.of(2026, 4, 1), vm.uiState.value.from)
        assertEquals(LocalDate.of(2026, 11, 5), vm.uiState.value.to)
        assertEquals(5, vm.uiState.value.report!!.sales + vm.uiState.value.report!!.creditNotes)
    }

    @Test
    fun switchingBackToMonth_usesTheMonthAgain() {
        val vm = viewModel()
        vm.onRangeChange(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5))
        vm.onModeChange(GstPeriodMode.MONTH)
        assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)
        assertEquals(LocalDate.of(2026, 10, 31), vm.uiState.value.period.to)
        assertNotNull(vm.uiState.value.report)
    }

    @Test
    fun nobodyAllowed_showsAFailure_thatRetryDoesNotBypass() {
        val vm = viewModel(role = Role.EMPLOYEE)
        assertTrue(vm.uiState.value.loadFailed)
        assertEquals(null, vm.uiState.value.report)
        vm.load()
        assertTrue(vm.uiState.value.loadFailed)
    }
}
