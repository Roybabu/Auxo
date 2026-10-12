package com.invision.pos.viewmodel

import com.invision.pos.domain.billing.Discount
import com.invision.pos.domain.billing.LineIssue
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.testutil.FakeBillingRepository
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.FakeSettingsRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(listOf(TestData.tShirt(), TestData.biscuits()))
    private val billing = FakeBillingRepository(inventory)
    private val settings = FakeSettingsRepository(TestData.settings.copy(employeeDiscountLimitBp = 1_000))

    private fun viewModel(canExceed: Boolean) =
        BillingViewModel(inventory, settings, CheckoutUseCase(billing, settings, TestData.clock), canExceedDiscountLimit = { canExceed })

    @Test
    fun aLineDiscount_needsAReasonBeforeCheckout() {
        val vm = viewModel(canExceed = true)
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.onLineDiscountChange(11, "10", isPercent = true)
        assertEquals(LineIssue.DiscountReasonMissing, vm.uiState.value.check.lineIssues[11L])
        assertFalse(vm.uiState.value.check.canCheckout)

        vm.onLineDiscountReasonChange(11, "Festival offer")
        assertTrue(vm.uiState.value.check.lineIssues.isEmpty())
        assertTrue(vm.uiState.value.check.canCheckout)
        assertEquals("Festival offer", vm.uiState.value.cart.lines.single().discountReason)
    }

    @Test
    fun aBillDiscount_needsAReason_andTheReasonIsCapped() {
        val vm = viewModel(canExceed = true)
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.onBillDiscountChange("20", isPercent = false)
        assertTrue(vm.uiState.value.check.billDiscountReasonMissing)
        vm.onBillDiscountReasonChange("x".repeat(200))
        assertFalse(vm.uiState.value.check.billDiscountReasonMissing)
        assertEquals(60, vm.uiState.value.cart.billDiscountReason.length)
    }

    @Test
    fun anEmployee_isHeldToTheLimit_theSuperAdminIsNot() {
        val employee = viewModel(canExceed = false)
        employee.onBarcodeScanned(TestData.EAN[0])
        employee.onLineDiscountChange(11, "15", isPercent = true)
        employee.onLineDiscountReasonChange(11, "Regular")
        assertEquals(1_000, employee.uiState.value.discountLimitBp)
        assertEquals(LineIssue.DiscountAboveLimit, employee.uiState.value.check.lineIssues[11L])
        assertFalse(employee.uiState.value.check.canCheckout)
        employee.onLineDiscountChange(11, "10", isPercent = true)
        assertTrue(employee.uiState.value.check.canCheckout)

        val admin = viewModel(canExceed = true)
        admin.onBarcodeScanned(TestData.EAN[0])
        admin.onLineDiscountChange(11, "15", isPercent = true)
        admin.onLineDiscountReasonChange(11, "Regular")
        assertNull(admin.uiState.value.discountLimitBp)
        assertTrue(admin.uiState.value.check.canCheckout)
    }

    @Test
    fun withTheDefaultLimitOfNone_anEmployeeCannotDiscountAtAll() {
        settings.settings.value = TestData.settings // limit 0
        val vm = viewModel(canExceed = false)
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.onBillDiscountChange("1", isPercent = false)
        vm.onBillDiscountReasonChange("Rounding")
        assertTrue(vm.uiState.value.check.billDiscountAboveLimit)
        assertFalse(vm.uiState.value.check.canCheckout)
        vm.onBillDiscountChange("", isPercent = false)
        assertTrue(vm.uiState.value.check.canCheckout)
    }

    @Test
    fun theSavedSale_carriesTheReasons() = runTest {
        val vm = viewModel(canExceed = true)
        vm.onBarcodeScanned(TestData.EAN[0])
        vm.onLineDiscountChange(11, "10", isPercent = true)
        vm.onLineDiscountReasonChange(11, "Festival offer")
        vm.proceedToPayment()
        val key = vm.uiState.value.payments.single().key
        vm.onTenderedChange(key, "1000")
        vm.completeSale()
        vm.events.first()
        val saved = billing.invoices.value.single()
        assertEquals("Festival offer", saved.lines.single().discountReason)
        assertEquals(Money(4_990), saved.discountTotal)
    }
}
