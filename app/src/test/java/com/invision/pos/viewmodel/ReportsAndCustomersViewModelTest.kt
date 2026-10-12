package com.invision.pos.viewmodel

import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.contacts.ContactError
import com.invision.pos.domain.contacts.ContactField
import com.invision.pos.domain.customers.AccountPaymentError
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.LedgerEntry
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.reports.PeriodPreset
import com.invision.pos.domain.reports.ReportType
import com.invision.pos.domain.reports.ReportValue
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.domain.usecase.ReceiveAccountPaymentUseCase
import com.invision.pos.testutil.FakeBillingRepository
import com.invision.pos.testutil.FakeCustomerRepository
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakeMenuRepository
import com.invision.pos.testutil.FakePurchasingRepository
import com.invision.pos.testutil.FakeSettingsRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import com.invision.pos.testutil.payInFull
import com.invision.pos.testutil.keepActive
import com.invision.pos.testutil.record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val purchasing = FakePurchasingRepository(inventory)
    private val customers = FakeCustomerRepository(listOf(Customer(id = 1, name = "Anita"), Customer(id = 2, name = "Ravi")))

    private fun viewModel() = ReportsViewModel(billing, inventory, purchasing, customers, TestData.clock)

    private suspend fun sell() {
        val shirt = TestData.tShirt()
        val cart = Cart(lines = listOf(CartLine.from(shirt, shirt.variants[0], quantity = 2)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        billing.recordSale(SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis()), "INV")
    }

    @Test
    fun buildsTheChosenReport_forThePeriod() = runTest {
        sell()
        val viewModel = viewModel()
        val events = record(viewModel.events)
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ReportType.SALES_SUMMARY, state.filters.type)
        assertEquals(LocalDate.of(2026, 10, 1), state.period!!.from)
        assertEquals(ReportValue.Count(1), state.table!!.rows.single()[1])

        viewModel.export()
        val file = (events.last() as ReportsEvent.Export).file
        assertEquals("sales-summary_2026-10-01_2026-10-10.csv", file.fileName)
        assertTrue(file.content.startsWith("Date,Bills,Returns"))

        viewModel.onPresetChange(PeriodPreset.LAST_MONTH)
        assertTrue(viewModel.uiState.value.table!!.isEmpty)

        viewModel.onCustomRangeChange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 9))
        assertEquals(LocalDate.of(2026, 10, 9), viewModel.uiState.value.period!!.from)
        assertEquals(1, viewModel.uiState.value.table!!.rows.size)
    }

    @Test
    fun report_refreshesWhenDataChanges() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.table!!.isEmpty)

        sell()
        assertEquals(ReportValue.Count(1), viewModel.uiState.value.table!!.rows.single()[1])
        sell()
        assertEquals(ReportValue.Count(2), viewModel.uiState.value.table!!.rows.single()[1])
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun stockReport_refreshesWhenStockChanges() = runTest {
        val viewModel = viewModel()
        viewModel.onTypeChange(ReportType.STOCK_VALUE)
        assertEquals(3, viewModel.uiState.value.table!!.rows.size)

        val juice = TestData.juice(stock = 5)
        inventory.saveProduct(juice.copy(id = 0, variants = juice.variants.map { it.copy(id = 0, productId = 0) }))
        assertEquals(4, viewModel.uiState.value.table!!.rows.size)
    }

    @Test
    fun currentPositionReports_ignoreThePeriod() = runTest {
        sell()
        customers.addEntry(LedgerEntry(customerId = 1, type = LedgerEntryType.SALE, amount = Money(5_000), createdAtMillis = 1))
        val viewModel = viewModel()
        viewModel.onTypeChange(ReportType.STOCK_VALUE)
        assertEquals(3, viewModel.uiState.value.table!!.rows.size)
        assertEquals("stock-value_2026-10-10.csv", viewModel.fileName(ReportType.STOCK_VALUE, viewModel.uiState.value.period))

        viewModel.onTypeChange(ReportType.CUSTOMER_BALANCES)
        assertEquals(1, viewModel.uiState.value.table!!.rows.size)
        viewModel.onOnlyOutstandingChange(false)
        assertEquals(2, viewModel.uiState.value.table!!.rows.size)

        viewModel.onTypeChange(ReportType.ITEM_SALES)
        assertEquals(ReportValue.Count(2), viewModel.uiState.value.table!!.rows.single()[3])
        viewModel.onTypeChange(ReportType.STOCK_MOVEMENTS)
        assertTrue(viewModel.uiState.value.table!!.rows.any { (it[3] as ReportValue.Text).value == "Sold" })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anita = Customer(id = 1, name = "Anita Das", phone = "9830012345", creditLimit = Money(100_000))
    private val ravi = Customer(id = 2, name = "Ravi Roy", phone = "9830054321")
    private val customers = FakeCustomerRepository(listOf(anita, ravi)).apply {
        addEntry(LedgerEntry(customerId = 1, type = LedgerEntryType.SALE, amount = Money(80_000), invoiceNumber = "INV/26-27/00001", createdAtMillis = 10))
        addEntry(LedgerEntry(customerId = 1, type = LedgerEntryType.PAYMENT, amount = Money(-30_000), method = PaymentMethod.UPI, createdAtMillis = 20))
    }

    @Test
    fun list_filtersAccountsThatOwe() = runTest {
        val viewModel = CustomerListViewModel(customers)
        keepActive(viewModel.uiState)
        assertEquals(2, viewModel.uiState.value.customerCount)
        assertEquals(Money(50_000), viewModel.uiState.value.totalDue)
        viewModel.onFilterChange(CustomerFilter.OWING)
        assertEquals(listOf("Anita Das"), viewModel.uiState.value.accounts.map { it.customer.name })
        viewModel.onFilterChange(CustomerFilter.ALL)
        viewModel.onQueryChange("54321")
        assertEquals(listOf("Ravi Roy"), viewModel.uiState.value.accounts.map { it.customer.name })
    }

    @Test
    fun detail_showsTheStatementNewestFirst_andExportsIt() = runTest {
        val viewModel = CustomerDetailViewModel(1, customers, TestData.clock)
        keepActive(viewModel.uiState)
        val events = record(viewModel.events)
        val state = viewModel.uiState.value as CustomerDetailUiState.Ready
        assertEquals(Money(50_000), state.account.balance)
        assertEquals(listOf(50_000L, 80_000L), state.statement.map { it.balanceAfter.minorUnits })
        assertEquals(Money(80_000), state.charged)
        assertEquals(Money(30_000), state.paid)

        viewModel.exportStatement()
        val file = (events.last() as CustomerDetailEvent.Export).file
        assertEquals("statement-Anita Das-2026-10-10.csv", file.fileName)
        val rows = file.content.trimEnd().split("\r\n")
        assertEquals(4, rows.size) // header, two entries, totals
        assertTrue(rows[1].contains("Sale on account"))

        val missing = CustomerDetailViewModel(99, customers, TestData.clock)
        keepActive(missing.uiState)
        assertEquals(CustomerDetailUiState.NotFound, missing.uiState.value)
    }

    @Test
    fun editor_refusesAMobileNumberInUse_andSavesAnOpeningBalance() = runTest {
        val viewModel = CustomerEditorViewModel(0, customers)
        val events = record(viewModel.events)
        viewModel.onFormChange(viewModel.uiState.value.form.copy(name = "Meera", phone = "98300 54321"))
        viewModel.save()
        assertEquals(ContactError.PHONE_IN_USE, viewModel.uiState.value.visibleErrors[ContactField.PHONE])

        viewModel.onFormChange(viewModel.uiState.value.form.copy(phone = "9831100000", openingBalance = "250"))
        viewModel.save()
        val id = (events.last() as CustomerEditorEvent.Saved).customerId
        assertEquals("9831100000", customers.customers.value.first { it.id == id }.phone)
        assertEquals(Money(25_000), customers.ledger.value.last().amount)
    }

    @Test
    fun payment_isPrefilledWithTheBalance_andRecordedOnce() = runTest {
        val viewModel = AccountPaymentViewModel(1, customers, ReceiveAccountPaymentUseCase(customers))
        val events = record(viewModel.events)
        assertEquals("500.00", viewModel.uiState.value.amount)

        viewModel.onAmountChange("600")
        viewModel.save()
        assertEquals(setOf(AccountPaymentError.MORE_THAN_DUE), viewModel.uiState.value.errors)

        viewModel.onAmountChange("200")
        assertEquals(Money(30_000), viewModel.uiState.value.balanceAfter)
        viewModel.onMethodChange(PaymentMethod.CREDIT) // not a way to pay
        assertEquals(PaymentMethod.CASH, viewModel.uiState.value.method)
        viewModel.onMethodChange(PaymentMethod.CARD)
        viewModel.onReferenceChange("4242")
        viewModel.save()
        assertEquals(AccountPaymentEvent.Saved(Money(20_000)), events.last())
        viewModel.save() // a second tap on the same screen
        val payments = customers.ledger.value.filter { it.type == LedgerEntryType.PAYMENT }
        assertEquals(2, payments.size)
        assertEquals("4242", payments.last().reference)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillingAccountsAndMenuTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.flour()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()
    private val anita = Customer(id = 1, name = "Anita Das", phone = "9830012345", creditLimit = Money(100_000))
    private val customers = FakeCustomerRepository(listOf(anita))
    private val dosa = MenuItem(id = 5, name = "Masala Dosa", section = "Breakfast", price = Money(12_000))
    private val menu = FakeMenuRepository(listOf(dosa, MenuItem(id = 6, name = "Idli", section = "Breakfast", price = Money(6_000), available = false)))

    private fun viewModel() = BillingViewModel(
        inventory = inventory,
        settingsRepository = settings,
        checkout = CheckoutUseCase(billing, settings, TestData.clock, customers),
        customers = customers,
        menuRepository = menu,
        clock = TestData.clock,
    )

    @Test
    fun dishes_canBeBilled_butIngredientsCant() = runTest {
        val viewModel = viewModel()
        val events = record(viewModel.events)
        assertEquals(listOf("Masala Dosa"), viewModel.uiState.value.menu.map { it.name })
        assertEquals(1, viewModel.uiState.value.productCount) // flour is an ingredient

        viewModel.onSearchTextChange("flour")
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        viewModel.onSearchTextChange("dosa")
        viewModel.onSearchSubmitted()
        viewModel.addMenuItem(dosa)
        val line = viewModel.uiState.value.cart.lines.single()
        assertEquals(2, line.quantity)
        assertEquals(dosa.id, line.menuItemId)
        assertEquals(Money(24_000), viewModel.uiState.value.totals.grandTotal)

        viewModel.addMenuItem(menu.items.value.first { it.name == "Idli" })
        assertEquals(BillingEvent.OutOfStock("Idli"), events.last())
    }

    @Test
    fun aSavedCustomer_canBuyOnAccount() = runTest {
        val viewModel = viewModel()
        val events = record(viewModel.events)
        assertEquals(PaymentMethod.MONEY, viewModel.uiState.value.paymentMethods)

        viewModel.onCustomerQueryChange("anita")
        viewModel.linkCustomer(viewModel.uiState.value.customerResults.single())
        assertEquals(PaymentMethod.entries, viewModel.uiState.value.paymentMethods)
        assertEquals(1L, viewModel.uiState.value.cart.customer.customerId)

        viewModel.onSearchTextChange(TestData.EAN[0])
        viewModel.onSearchSubmitted()
        viewModel.proceedToPayment()
        val draft = viewModel.uiState.value.payments.single()
        viewModel.onPaymentMethodChange(draft.key, PaymentMethod.CREDIT)
        viewModel.payInFull()
        viewModel.completeSale()
        assertTrue(events.last() is BillingEvent.Completed)
        val invoice = billing.invoices.value.single()
        assertEquals(1L, invoice.customerId)
        assertEquals(PaymentMethod.CREDIT, invoice.payments.single().method)

        // Typing new buyer details unlinks the account and drops payment on account.
        viewModel.onCustomerQueryChange("9830012345")
        viewModel.linkCustomer(viewModel.uiState.value.customerResults.single())
        assertEquals(PaymentMethod.entries, viewModel.uiState.value.paymentMethods)
        viewModel.onCustomerChange(CustomerDetails(name = "Someone"))
        assertEquals(null, viewModel.uiState.value.account)
        assertEquals(PaymentMethod.MONEY, viewModel.uiState.value.paymentMethods)
    }

    @Test
    fun typedBuyer_canBeSavedAsACustomer() = runTest {
        val viewModel = viewModel()
        val events = record(viewModel.events)
        viewModel.onCustomerChange(CustomerDetails(name = "Meera Sen", phone = "9831100000"))
        viewModel.saveTypedCustomer()
        assertEquals(BillingEvent.CustomerSaved("Meera Sen"), events.last())
        assertEquals(2, customers.customers.value.size)
        assertTrue(viewModel.uiState.value.account != null)

        viewModel.onCustomerChange(CustomerDetails(name = "Copy", phone = "9830012345"))
        viewModel.saveTypedCustomer()
        assertEquals(CustomerSaveError.PHONE_IN_USE, viewModel.uiState.value.customerSaveError)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val asha = Customer(id = 1, name = "Asha Rao", phone = "9830011111")
    private val customers = FakeCustomerRepository(listOf(asha))

    private fun bill(id: Long, type: com.invision.pos.domain.model.InvoiceType, minor: Long, millis: Long) =
        com.invision.pos.domain.model.InvoiceSummary(id, "N-$id", type, millis, "Asha Rao", null, Money(minor), 1)

    @Test
    fun history_followsTheCustomersInvoices() = runTest {
        val viewModel = CustomerHistoryViewModel(1, customers, java.time.ZoneOffset.UTC)
        keepActive(viewModel.uiState)
        assertEquals(com.invision.pos.domain.customers.CustomerHistory.EMPTY, (viewModel.uiState.value as CustomerHistoryUiState.Ready).history)

        customers.invoices.value = listOf(
            bill(1, com.invision.pos.domain.model.InvoiceType.SALE, 100_000, 1_000),
            bill(2, com.invision.pos.domain.model.InvoiceType.RETURN, 25_000, 2_000),
        )
        val ready = viewModel.uiState.value as CustomerHistoryUiState.Ready
        assertEquals("Asha Rao", ready.customer.name)
        assertEquals(Money(75_000), ready.history.totalSpend)
        assertEquals(1, ready.history.visitCount)
    }

    @Test
    fun anUnknownCustomer_isNotFound() = runTest {
        val viewModel = CustomerHistoryViewModel(99, customers, java.time.ZoneOffset.UTC)
        keepActive(viewModel.uiState)
        assertEquals(CustomerHistoryUiState.NotFound, viewModel.uiState.value)
    }

}
