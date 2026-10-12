package com.auxo.pos.viewmodel

import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeCustomerRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Part payment on the payment screen: some now (cash, UPI), the rest on the customer's account. */
@OptIn(ExperimentalCoroutinesApi::class)
class PartPaymentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()
    private val anita = Customer(id = 1, name = "Anita Das", phone = "9830012345", creditLimit = Money(100_000))
    private val customers = FakeCustomerRepository(listOf(anita))

    private fun viewModel() = BillingViewModel(
        inventory = inventory,
        settingsRepository = settings,
        checkout = CheckoutUseCase(billing, settings, TestData.clock, customers),
        customers = customers,
        clock = TestData.clock,
    )

    /** A ₹499 bill for Anita, on the payment screen. */
    private fun BillingViewModel.startBill(linked: Boolean = true) {
        if (linked) {
            onCustomerQueryChange("anita")
            linkCustomer(uiState.value.customerResults.single())
        }
        onBarcodeScanned(TestData.EAN[0])
        proceedToPayment()
    }

    @Test
    fun partCash_restOnAccount_isOneTapAndAddsUp() = runTest {
        val vm = viewModel()
        vm.startBill()
        vm.onPaymentAmountChange(vm.uiState.value.payments.single().key, "200")
        assertEquals(Money(29_900), vm.uiState.value.paymentRemaining)

        vm.putRemainingOnAccount()
        val state = vm.uiState.value
        assertEquals(listOf(PaymentMethod.CASH, PaymentMethod.CREDIT), state.payments.map { it.method })
        assertEquals("299.00", state.payments.last().amount)
        assertTrue(state.paymentRemaining.isZero)
        assertEquals(Money(20_000), state.paidNowAmount)
        assertEquals(Money(29_900), state.onAccountAmount)
        assertEquals(Money(29_900), state.balanceAfterBill) // the customer owed nothing before

        vm.completeSale()
        vm.events.first()
        val invoice = billing.invoices.value.single()
        assertEquals(Money(49_900), invoice.payments.fold(Money.ZERO) { acc, p -> acc + p.amount })
        assertEquals(Money(29_900), invoice.payments.single { it.method == PaymentMethod.CREDIT }.amount)
    }

    @Test
    fun partCash_partUpi_restOnAccount() = runTest {
        val vm = viewModel()
        vm.startBill()
        vm.onPaymentAmountChange(vm.uiState.value.payments.single().key, "100")
        vm.addSplitPayment() // UPI for the remaining 399
        val upi = vm.uiState.value.payments.last()
        assertEquals(PaymentMethod.UPI, upi.method)
        vm.onPaymentAmountChange(upi.key, "150")
        assertEquals(Money(24_900), vm.uiState.value.paymentRemaining)

        vm.putRemainingOnAccount()
        assertEquals(listOf(PaymentMethod.CASH, PaymentMethod.UPI, PaymentMethod.CREDIT), vm.uiState.value.payments.map { it.method })
        assertTrue(vm.uiState.value.paymentRemaining.isZero)
        assertEquals(Money(25_000), vm.uiState.value.paidNowAmount)
        assertEquals(Money(24_900), vm.uiState.value.onAccountAmount)

        vm.completeSale()
        vm.events.first()
        assertEquals(3, billing.invoices.value.single().payments.size)
    }

    @Test
    fun puttingTheRestOnAccountTwice_growsTheSameRow() = runTest {
        val vm = viewModel()
        vm.startBill()
        vm.onPaymentAmountChange(vm.uiState.value.payments.single().key, "300")
        vm.putRemainingOnAccount()
        // Pay less in cash again: the account row takes the difference instead of a second row appearing.
        vm.onPaymentAmountChange(vm.uiState.value.payments.first().key, "100")
        vm.putRemainingOnAccount()
        val credit = vm.uiState.value.payments.filter { it.method == PaymentMethod.CREDIT }
        assertEquals(1, credit.size)
        assertEquals("399.00", credit.single().amount)
        assertTrue(vm.uiState.value.paymentRemaining.isZero)
    }

    @Test
    fun withoutACustomer_thereIsNoAccountToPutItOn() = runTest {
        val vm = viewModel()
        vm.startBill(linked = false)
        vm.onPaymentAmountChange(vm.uiState.value.payments.single().key, "200")
        vm.putRemainingOnAccount()
        assertEquals(1, vm.uiState.value.payments.size)
        assertEquals(Money(29_900), vm.uiState.value.paymentRemaining)
        assertEquals(null, vm.uiState.value.balanceAfterBill)
    }

    @Test
    fun nothingRemaining_changesNothing() = runTest {
        val vm = viewModel()
        vm.startBill()
        vm.putRemainingOnAccount()
        assertEquals(1, vm.uiState.value.payments.size)
    }

    @Test
    fun overTheCreditLimit_isRefusedAndNothingIsSaved() = runTest {
        // The limit is ₹1,000, so ₹1,397 on account is refused.
        val vm = viewModel()
        vm.onCustomerQueryChange("anita")
        vm.linkCustomer(vm.uiState.value.customerResults.single())
        repeat(3) { vm.onBarcodeScanned(TestData.EAN[0]) }
        vm.proceedToPayment()
        vm.onPaymentAmountChange(vm.uiState.value.payments.single().key, "100")
        vm.putRemainingOnAccount()
        vm.completeSale()
        assertTrue(vm.uiState.value.paymentShowErrors)
        assertTrue(billing.invoices.value.isEmpty())
    }
}
