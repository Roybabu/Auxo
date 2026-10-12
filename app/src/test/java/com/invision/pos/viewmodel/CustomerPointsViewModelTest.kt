package com.invision.pos.viewmodel

import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.LedgerEntry
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.Money
import com.invision.pos.testutil.FakeCustomerRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import com.invision.pos.testutil.keepActive
import com.invision.pos.testutil.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerPointsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val customers = FakeCustomerRepository(listOf(Customer(id = 1, name = "Anita Das", phone = "9830012345")))

    private fun add(type: LedgerEntryType, amount: Long = 0, points: Int = 0, at: Long) =
        customers.addEntry(LedgerEntry(customerId = 1, type = type, amount = Money(amount), points = points, createdAtMillis = at))

    @Test
    fun aCustomerWithoutPoints_showsNoPointsSection() = runTest {
        add(LedgerEntryType.SALE, 50_000, at = 1)
        val vm = CustomerDetailViewModel(1, customers, TestData.clock)
        keepActive(vm.uiState)
        val state = vm.uiState.value as CustomerDetailUiState.Ready
        assertFalse(state.hasPoints)
        assertEquals(0, state.account.points)
    }

    @Test
    fun points_areListedNewestFirst_withTheBalance_andStayOutOfTheMoneyStatement() = runTest {
        add(LedgerEntryType.SALE, 50_000, at = 1)
        add(LedgerEntryType.POINTS_EARNED, points = 5, at = 2)
        add(LedgerEntryType.POINTS_REDEEMED, points = -3, at = 3)
        add(LedgerEntryType.PAYMENT, -20_000, at = 4)
        val vm = CustomerDetailViewModel(1, customers, TestData.clock)
        keepActive(vm.uiState)
        val state = vm.uiState.value as CustomerDetailUiState.Ready
        assertTrue(state.hasPoints)
        assertEquals(2, state.account.points)
        assertEquals(listOf(LedgerEntryType.POINTS_REDEEMED, LedgerEntryType.POINTS_EARNED), state.pointsHistory.map { it.type })
        assertEquals(listOf(LedgerEntryType.PAYMENT, LedgerEntryType.SALE), state.statement.map { it.entry.type })
        assertEquals(Money(30_000), state.account.balance)
    }

    @Test
    fun theStatementExport_hasNoPointsRows() = runTest {
        add(LedgerEntryType.SALE, 50_000, at = 1)
        add(LedgerEntryType.POINTS_EARNED, points = 5, at = 2)
        val vm = CustomerDetailViewModel(1, customers, TestData.clock)
        keepActive(vm.uiState)
        val events = record(vm.events)
        vm.exportStatement()
        val file = (events.last() as CustomerDetailEvent.Export).file
        assertEquals(3, file.content.trimEnd().split("\r\n").size) // header, the sale, totals
        assertFalse(file.content.contains("Points"))
    }
}
