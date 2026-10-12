package com.invision.pos.viewmodel

import com.invision.pos.domain.customers.OwingSort
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.LedgerEntry
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.Money
import com.invision.pos.testutil.FakeCustomerRepository
import com.invision.pos.testutil.FakeSettingsRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import com.invision.pos.testutil.keepActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class OwingListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dayMillis = 24L * 60 * 60 * 1000
    private val now = TestData.clock.millis()

    private val customers = FakeCustomerRepository(
        listOf(
            Customer(id = 1, name = "Asha", phone = "9830012345"),
            Customer(id = 2, name = "Bimal", phone = "9830054321"),
            Customer(id = 3, name = "Chitra"),
            Customer(id = 4, name = "Dev"),
        ),
    ).apply {
        addEntry(LedgerEntry(customerId = 1, type = LedgerEntryType.SALE, amount = Money(500_00), createdAtMillis = now - 5 * dayMillis))
        addEntry(LedgerEntry(customerId = 2, type = LedgerEntryType.SALE, amount = Money(300_00), createdAtMillis = now - 40 * dayMillis))
        addEntry(LedgerEntry(customerId = 3, type = LedgerEntryType.SALE, amount = Money(100_00), createdAtMillis = now - 20 * dayMillis))
        addEntry(LedgerEntry(customerId = 3, type = LedgerEntryType.PAYMENT, amount = Money(-100_00), createdAtMillis = now - 10 * dayMillis))
        addEntry(LedgerEntry(customerId = 4, type = LedgerEntryType.SALE, amount = Money(50_00), createdAtMillis = now))
    }
    private val settings = FakeSettingsRepository(TestData.settings.copy(storeName = "Roy Mart", upiId = "roymart@okaxis"))

    private fun viewModel() = CustomerListViewModel(customers, settings, TestData.clock)

    @Test
    fun owingList_defaultsToTheLargestAmountFirst() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.onFilterChange(CustomerFilter.OWING)
        assertEquals(listOf("Asha", "Bimal", "Dev"), vm.uiState.value.accounts.map { it.customer.name })
        assertEquals(OwingSort.AMOUNT, vm.uiState.value.sort)
    }

    @Test
    fun sortingByAge_putsTheLongestOverdueFirst() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.onFilterChange(CustomerFilter.OWING)
        vm.onSortChange(OwingSort.AGE)
        assertEquals(listOf("Bimal", "Asha", "Dev"), vm.uiState.value.accounts.map { it.customer.name })
    }

    @Test
    fun ages_areWholeDaysSinceTheOldestUnpaidCharge_andOnlyForThoseWhoOwe() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val ages = vm.uiState.value.ageDays
        assertEquals(mapOf(1L to 5, 2L to 40, 4L to 0), ages)
        assertNull(ages[3L]) // paid up
    }

    @Test
    fun reminderShop_comesFromTheSettings_withTheUpiIdOnlyWhenValid() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        val shop = vm.uiState.value.reminderShop!!
        assertEquals("Roy Mart", shop.shopName)
        assertEquals("roymart@okaxis", shop.upiId)
        assertEquals(Language.ENGLISH, shop.language)

        settings.settings.value = settings.settings.value.copy(upiId = "oops")
        assertNull(vm.uiState.value.reminderShop!!.upiId)
    }

    @Test
    fun withoutSettings_noReminderIsOffered() = runTest {
        val vm = CustomerListViewModel(customers, null, TestData.clock)
        keepActive(vm.uiState)
        assertNull(vm.uiState.value.reminderShop)
        assertNotNull(vm.uiState.value.accounts)
    }

    @Test
    fun filteringAndSearch_stillWork_withTheNewSort() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.onFilterChange(CustomerFilter.OWING)
        vm.onSortChange(OwingSort.AGE)
        vm.onQueryChange("asha")
        assertEquals(listOf("Asha"), vm.uiState.value.accounts.map { it.customer.name })
    }

    @Suppress("unused")
    private val zone = ZoneOffset.UTC
}
