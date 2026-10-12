package com.auxo.pos.viewmodel

import com.auxo.pos.core.printer.DeviceList
import com.auxo.pos.core.printer.PairedDevice
import com.auxo.pos.core.printer.PrintResult
import com.auxo.pos.core.printer.SavedPrinter
import com.auxo.pos.domain.model.Language
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.testutil.FakeBluetoothDevices
import com.auxo.pos.testutil.FakePrinterPreferences
import com.auxo.pos.testutil.FakeReceiptPrinting
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import com.auxo.pos.testutil.keepActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rpp = PairedDevice("00:11:22:33:44:55", "RPP02N", true)
    private val buds = PairedDevice("AA:BB:CC:DD:EE:FF", "Buds", false)
    private val preferences = FakePrinterPreferences()
    private val devices = FakeBluetoothDevices(DeviceList.Ready(listOf(rpp, buds)))
    private val printing = FakeReceiptPrinting()
    private val settings = FakeSettingsRepository()

    private fun viewModel() = PrinterSettingsViewModel(preferences, devices, printing, settings)

    @Test
    fun startsWithNoPrinter_andThePairedDevices() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        assertNull(vm.uiState.value.printer)
        assertEquals(DeviceList.Ready(listOf(rpp, buds)), vm.uiState.value.devices)
    }

    @Test
    fun choosingADevice_savesItWithTheNarrowRollAndNoCutter() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.choose(rpp)
        assertEquals(SavedPrinter(rpp.address, "RPP02N", ReceiptPaper.THERMAL_58, false), vm.uiState.value.printer)
    }

    @Test
    fun swappingPrinters_keepsThePaperAndCutterChoices() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.choose(rpp)
        vm.setPaper(ReceiptPaper.THERMAL_80)
        vm.setCut(true)
        vm.choose(buds)
        assertEquals(SavedPrinter(buds.address, "Buds", ReceiptPaper.THERMAL_80, true), vm.uiState.value.printer)
    }

    @Test
    fun aSheetCannotBeChosenAsThePrintersPaper() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.choose(rpp)
        vm.setPaper(ReceiptPaper.A4)
        assertEquals(ReceiptPaper.THERMAL_58, vm.uiState.value.printer?.paper)
        assertEquals(listOf(ReceiptPaper.THERMAL_58, ReceiptPaper.THERMAL_76, ReceiptPaper.THERMAL_80), vm.uiState.value.papers)
    }

    @Test
    fun paperAndCutterChanges_doNothingWithoutAPrinter() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.setPaper(ReceiptPaper.THERMAL_80)
        vm.setCut(true)
        assertNull(vm.uiState.value.printer)
    }

    @Test
    fun forgetting_removesThePrinter() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.choose(rpp)
        vm.forget()
        assertNull(vm.uiState.value.printer)
    }

    @Test
    fun refresh_picksUpBluetoothBeingSwitchedOnOrAPermissionBeingGiven() = runTest {
        devices.list = DeviceList.Off
        val vm = viewModel()
        keepActive(vm.uiState)
        assertEquals(DeviceList.Off, vm.uiState.value.devices)
        devices.list = DeviceList.NeedsPermission
        vm.refresh()
        assertEquals(DeviceList.NeedsPermission, vm.uiState.value.devices)
        devices.list = DeviceList.Ready(listOf(rpp))
        vm.refresh()
        assertEquals(DeviceList.Ready(listOf(rpp)), vm.uiState.value.devices)
    }

    @Test
    fun testPrint_usesTheShopNameAndTheBillLanguage_andShowsTheResult() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.choose(rpp)
        vm.testPrint()
        assertEquals(listOf(TestData.settings.storeName to TestData.settings.receipt.language), printing.tests)
        assertEquals(PrintResult.PRINTED, vm.uiState.value.testResult)
        assertFalse(vm.uiState.value.testing)
        vm.resultShown()
        assertNull(vm.uiState.value.testResult)
    }

    @Test
    fun aFailedTestPrint_reportsWhy() = runTest {
        printing.result = PrintResult.UNREACHABLE
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.choose(rpp)
        vm.testPrint()
        assertEquals(PrintResult.UNREACHABLE, vm.uiState.value.testResult)
    }

    @Test
    fun testPrint_withoutAPrinter_doesNothing() = runTest {
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.testPrint()
        assertTrue(printing.tests.isEmpty())
        assertNull(vm.uiState.value.testResult)
    }

    @Test
    fun choosingAnotherPrinter_clearsAnOldTestResult() = runTest {
        printing.result = PrintResult.UNREACHABLE
        val vm = viewModel()
        keepActive(vm.uiState)
        vm.choose(rpp)
        vm.testPrint()
        vm.choose(buds)
        assertNull(vm.uiState.value.testResult)
    }
}
