package com.auxo.pos.viewmodel

import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.loyalty.LoyaltyRules
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeCustomerRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeLoyaltyRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Using loyalty points on the bill screen. A shirt costs Rs 499; one point is worth Re 1. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoyaltyBillingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository()
    private val anita = Customer(id = 1, name = "Anita Das", phone = "9830012345")
    private val customers = FakeCustomerRepository(listOf(anita))
    private val loyalty = FakeLoyaltyRepository(LoyaltyRules(true, Money(10_000), Money(100)))

    private fun viewModel() = BillingViewModel(
        inventory = inventory,
        settingsRepository = settings,
        checkout = CheckoutUseCase(billing, settings, TestData.clock, customers),
        customers = customers,
        clock = TestData.clock,
        loyalty = loyalty,
    )

    private fun givePoints(points: Int) = customers.addEntry(
        LedgerEntry(customerId = 1, type = LedgerEntryType.POINTS_EARNED, amount = Money.ZERO, points = points, createdAtMillis = 1),
    )

    private fun BillingViewModel.billForAnita(linked: Boolean = true) {
        if (linked) {
            onCustomerQueryChange("anita")
            linkCustomer(uiState.value.customerResults.single())
        }
        onBarcodeScanned(TestData.EAN[0])
    }

    @Test
    fun loyaltyOff_showsNothing_evenForACustomerWithPoints() {
        givePoints(30)
        loyalty.rules.value = LoyaltyRules.OFF
        val vm = viewModel()
        vm.billForAnita()
        assertFalse(vm.uiState.value.loyaltyAvailable)
        vm.onLoyaltyPointsChange("10")
        assertEquals(0, vm.uiState.value.loyaltyPoints)
        assertEquals(Money(49_900), vm.uiState.value.totals.grandTotal)
    }

    @Test
    fun aWalkInBill_cannotUsePoints() {
        val vm = viewModel()
        vm.billForAnita(linked = false)
        assertFalse(vm.uiState.value.loyaltyAvailable)
        vm.onLoyaltyPointsChange("10")
        assertEquals(0, vm.uiState.value.loyaltyPoints)
        assertNull(vm.uiState.value.cart.loyalty)
    }

    @Test
    fun aLinkedCustomer_seesTheirBalance_andTheMostThatCanBeUsed() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        val state = vm.uiState.value
        assertTrue(state.loyaltyAvailable)
        assertEquals(30, state.account?.points)
        assertEquals(30, state.loyaltyMax)
    }

    @Test
    fun typingPoints_takesThemOffTheTotal() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("20")
        val state = vm.uiState.value
        assertEquals(20, state.loyaltyPoints)
        assertEquals(Money(2_000), state.loyaltyDiscount)
        assertEquals(Money(47_900), state.totals.grandTotal)
        assertEquals(20, state.cart.loyalty?.points)
        assertTrue(state.check.canCheckout)
    }

    @Test
    fun morePointsThanTheCustomerHas_areCutBackToTheBalance() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("500")
        assertEquals(30, vm.uiState.value.loyaltyPoints)
        assertEquals(Money(46_900), vm.uiState.value.totals.grandTotal)
    }

    @Test
    fun nonDigits_areIgnored() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("1a2.5")
        assertEquals(30, vm.uiState.value.loyaltyPoints) // "125", kept to the balance
        vm.onLoyaltyPointsChange("abc")
        assertEquals(0, vm.uiState.value.loyaltyPoints)
    }

    @Test
    fun useAll_stopsShortOfAFreeBill() {
        givePoints(900)
        val vm = viewModel()
        vm.billForAnita()
        vm.useAllLoyaltyPoints()
        val state = vm.uiState.value
        assertEquals(498, state.loyaltyPoints) // Re 1 is always left to pay
        assertEquals(Money(100), state.totals.grandTotal)
        assertTrue(state.check.canCheckout)
    }

    @Test
    fun clearing_putsTheBillBack() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("20")
        vm.clearLoyaltyPoints()
        assertEquals(0, vm.uiState.value.loyaltyPoints)
        assertEquals(Money(49_900), vm.uiState.value.totals.grandTotal)
        assertNull(vm.uiState.value.cart.loyalty)
    }

    @Test
    fun changingTheCustomer_dropsThePoints() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("20")
        vm.unlinkCustomer()
        assertEquals(0, vm.uiState.value.loyaltyPoints)
        assertEquals(Money(49_900), vm.uiState.value.totals.grandTotal)
    }

    @Test
    fun aBiggerBillDiscount_cutsThePointsBack() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("30")
        vm.onBillDiscountChange("480", isPercent = false) // Rs 19 left: at most 18 points
        assertEquals(18, vm.uiState.value.loyaltyPoints)
        assertEquals(Money(100), vm.uiState.value.totals.grandTotal)
    }

    @Test
    fun switchingLoyaltyOff_dropsThePointsOnTheOpenBill() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("20")
        loyalty.rules.value = LoyaltyRules.OFF
        val state = vm.uiState.value
        assertEquals(0, state.loyaltyPoints)
        assertFalse(state.loyaltyAvailable)
        assertEquals(Money(49_900), state.totals.grandTotal)
    }

    @Test
    fun theSale_carriesThePointsUsed_andTheBillStartsFreshAfterwards() = runTest {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("20")
        vm.proceedToPayment()
        vm.completeSale()
        vm.events.first()
        val invoice = billing.invoices.value.single()
        assertEquals(20, invoice.loyaltyPointsRedeemed)
        assertEquals(Money(47_900), invoice.grandTotal)
        assertEquals(Money(2_000), invoice.discountTotal)
        // The next bill keeps the shop's rules but none of the points.
        assertEquals(0, vm.uiState.value.loyaltyPoints)
        assertEquals(true, vm.uiState.value.loyaltyRules.enabled)
    }

    @Test
    fun ifThePointsCannotBeUsedAnyMore_theSaleIsNotSaved_andTheCashierIsTold() = runTest {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onLoyaltyPointsChange("20")
        vm.proceedToPayment()
        billing.loyaltyUnavailable = true // the balance changed on another device, say
        vm.completeSale()
        assertEquals(BillingEvent.LoyaltyUnavailable, vm.events.first())
        assertTrue(billing.invoices.value.isEmpty())
        val state = vm.uiState.value
        assertEquals(0, state.loyaltyPoints)
        assertEquals(BillingStage.CART, state.stage)
        assertEquals(Money(49_900), state.totals.grandTotal)
        assertFalse(state.isSaving)
    }

    @Test
    fun aBillDiscount_andPoints_workTogether() {
        givePoints(30)
        val vm = viewModel()
        vm.billForAnita()
        vm.onBillDiscountChange("10", isPercent = true) // Rs 49.90 off
        vm.onLoyaltyPointsChange("20")
        val totals = vm.uiState.value.totals
        assertEquals(Money(4_990 + 2_000), totals.billDiscountTotal)
        assertEquals(Money(2_000), totals.loyaltyDiscount)
        assertEquals(Discount.Percent(1_000), vm.uiState.value.cart.billDiscount)
    }
}
