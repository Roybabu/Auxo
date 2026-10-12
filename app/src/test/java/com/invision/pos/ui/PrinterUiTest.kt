package com.invision.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.printer.DeviceList
import com.invision.pos.core.printer.PairedDevice
import com.invision.pos.core.printer.PrintResult
import com.invision.pos.core.printer.SavedPrinter
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.screens.billing.InvoiceDetailContent
import com.invision.pos.ui.screens.more.PrinterSettingsContent
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.InvoiceDetailUiState
import com.invision.pos.viewmodel.PrinterSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class PrinterUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rpp = PairedDevice("00:11:22:33:44:55", "RPP02N", true)
    private val buds = PairedDevice("AA:BB:CC:DD:EE:FF", "Buds", false)
    private val chosen = SavedPrinter(rpp.address, rpp.name, ReceiptPaper.THERMAL_58)

    private class Calls {
        val chosen = mutableListOf<PairedDevice>()
        val papers = mutableListOf<ReceiptPaper>()
        val cuts = mutableListOf<Boolean>()
        var forget = 0
        var test = 0
        var allow = 0
        var openSettings = 0
    }

    private fun show(state: PrinterSettingsUiState, calls: Calls = Calls()): Calls {
        composeRule.setContent {
            InvisionTheme {
                PrinterSettingsContent(
                    state = state,
                    onBack = {},
                    onChoose = { calls.chosen += it },
                    onPaper = { calls.papers += it },
                    onCut = { calls.cuts += it },
                    onForget = { calls.forget++ },
                    onTest = { calls.test++ },
                    onAllowBluetooth = { calls.allow++ },
                    onOpenBluetoothSettings = { calls.openSettings++ },
                )
            }
        }
        composeRule.waitForIdle()
        return calls
    }

    @Test
    fun withNoPrinter_theScreenSaysSo_andListsPairedDevices() {
        val calls = show(PrinterSettingsUiState(devices = DeviceList.Ready(listOf(rpp, buds))))
        composeRule.onNodeWithTag("printer_none").assertIsDisplayed()
        composeRule.onAllNodesWithTag("printer_test").assertCountEquals(0)
        composeRule.onNodeWithTag("printer_choose_${rpp.address}").performClick()
        assertEquals(listOf(rpp), calls.chosen)
        composeRule.onNodeWithTag("printer_open_settings").performClick()
        assertEquals(1, calls.openSettings)
    }

    @Test
    fun theChosenPrinter_isMarked_andItsSettingsCanBeChanged() {
        val calls = show(PrinterSettingsUiState(printer = chosen, devices = DeviceList.Ready(listOf(rpp, buds))))
        composeRule.onNodeWithTag("printer_current").assertIsDisplayed()
        composeRule.onNodeWithText("Chosen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("printer_choose_${rpp.address}").assertCountEquals(0)
        composeRule.onNodeWithTag("printer_paper_THERMAL_80").performClick()
        composeRule.onNodeWithTag("printer_cut").performClick()
        composeRule.onNodeWithTag("printer_test").performClick()
        composeRule.onNodeWithTag("printer_forget").performClick()
        assertEquals(listOf(ReceiptPaper.THERMAL_80), calls.papers)
        assertEquals(listOf(true), calls.cuts)
        assertEquals(1, calls.test)
        assertEquals(1, calls.forget)
    }

    @Test
    fun onlyRollsCanBeChosen() {
        show(PrinterSettingsUiState(printer = chosen))
        for (paper in listOf("THERMAL_58", "THERMAL_76", "THERMAL_80")) composeRule.onNodeWithTag("printer_paper_$paper").assertIsDisplayed()
        composeRule.onAllNodesWithTag("printer_paper_A4").assertCountEquals(0)
    }

    @Test
    fun whileTesting_theTestButtonWaits() {
        show(PrinterSettingsUiState(printer = chosen, testing = true))
        composeRule.onNodeWithTag("printer_test").assertIsNotEnabled()
    }

    @Test
    fun aTestThatWorked_andOneThatDidNot_areSaidClearly() {
        show(PrinterSettingsUiState(printer = chosen, testResult = PrintResult.PRINTED))
        composeRule.onNodeWithText("Test receipt sent to the printer.").assertIsDisplayed()
    }

    @Test
    fun aPrinterThatCannotBeReached_saysToCheckPowerAndRange() {
        show(PrinterSettingsUiState(printer = chosen, testResult = PrintResult.UNREACHABLE))
        composeRule.onNodeWithText("switched on", substring = true).assertIsDisplayed()
    }

    @Test
    fun bluetoothOff_unavailable_andNoPermission_eachSayWhatToDo() {
        show(PrinterSettingsUiState(devices = DeviceList.Off))
        composeRule.onNodeWithTag("printer_devices_off").assertIsDisplayed()
    }

    @Test
    fun withoutPermission_theAllowButtonAsksForIt() {
        val calls = show(PrinterSettingsUiState(devices = DeviceList.NeedsPermission))
        composeRule.onNodeWithTag("printer_devices_permission").assertIsDisplayed()
        composeRule.onNodeWithTag("printer_allow").performClick()
        assertEquals(1, calls.allow)
    }

    @Test
    fun noPairedDevices_pointsToBluetoothSettings() {
        show(PrinterSettingsUiState(devices = DeviceList.Ready(emptyList())))
        composeRule.onNodeWithTag("printer_devices_empty").assertIsDisplayed()
    }

    @Test
    fun noBluetoothAtAll_isSaid() {
        show(PrinterSettingsUiState(devices = DeviceList.Unavailable))
        composeRule.onNodeWithTag("printer_devices_unavailable").assertIsDisplayed()
    }

    private fun invoiceState() = InvoiceDetailUiState.Ready(SampleSales.shirtsAndBiscuits(), emptyList(), TestData.settings, ReceiptPaper.THERMAL_58)

    @Test
    fun theInvoiceScreen_showsTheBluetoothButton_onlyWhenAPrinterIsChosen() {
        var printed = 0
        var enabled by androidx.compose.runtime.mutableStateOf(false)
        composeRule.setContent {
            InvisionTheme {
                InvoiceDetailContent(
                    state = invoiceState(),
                    onPaperChange = {},
                    onPrint = {},
                    onShare = {},
                    onReturn = {},
                    onOpenInvoice = {},
                    onNewBill = {},
                    zone = ZoneOffset.UTC,
                    onBluetoothPrint = if (enabled) ({ printed++ }) else null,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("invoice_bluetooth_print").assertCountEquals(0)
        enabled = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("invoice_bluetooth_print").performClick()
        assertEquals(1, printed)
        // Share and print through the phone's print service stay as they were.
        composeRule.onNodeWithText("Share PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Print").assertIsDisplayed()
    }
}
