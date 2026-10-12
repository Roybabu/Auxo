package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.printer.BluetoothDevices
import com.auxo.pos.core.printer.DeviceList
import com.auxo.pos.core.printer.PairedDevice
import com.auxo.pos.core.printer.PrintResult
import com.auxo.pos.core.printer.PrinterDots
import com.auxo.pos.core.printer.PrinterPreferences
import com.auxo.pos.core.printer.ReceiptPrinting
import com.auxo.pos.core.printer.SavedPrinter
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.domain.model.StoreSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrinterSettingsUiState(
    val printer: SavedPrinter? = null,
    val devices: DeviceList = DeviceList.Unavailable,
    val testing: Boolean = false,
    /** How the last test print ended; null before one. */
    val testResult: PrintResult? = null,
) {
    /** The rolls a receipt can be printed on from the phone. */
    val papers: List<ReceiptPaper> get() = PRINTER_PAPERS
}

val PRINTER_PAPERS: List<ReceiptPaper> = listOf(ReceiptPaper.THERMAL_58, ReceiptPaper.THERMAL_76, ReceiptPaper.THERMAL_80)

/** Choose the Bluetooth receipt printer, set its paper, and test it. */
class PrinterSettingsViewModel(
    private val preferences: PrinterPreferences,
    private val bluetooth: BluetoothDevices,
    private val printing: ReceiptPrinting,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val devices = MutableStateFlow(bluetooth.list())
    private val local = MutableStateFlow(Local())

    private data class Local(val testing: Boolean = false, val testResult: PrintResult? = null)

    val uiState: StateFlow<PrinterSettingsUiState> = combine(preferences.printer, devices, local) { printer, list, state ->
        PrinterSettingsUiState(printer, list, state.testing, state.testResult)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PrinterSettingsUiState())

    /** Looks at the paired devices again: after Bluetooth was switched on, the permission was given, or a device was paired. */
    fun refresh() {
        devices.value = bluetooth.list()
    }

    fun choose(device: PairedDevice) {
        val current = preferences.printer.value
        // Keep the paper and cutter choices when the person swaps one printer for another.
        preferences.save(SavedPrinter(device.address, device.name, current?.paper ?: ReceiptPaper.THERMAL_58, current?.cutPaper ?: false))
        local.update { it.copy(testResult = null) }
    }

    fun setPaper(paper: ReceiptPaper) {
        if (PrinterDots.widthFor(paper) == null) return
        val current = preferences.printer.value ?: return
        preferences.save(current.copy(paper = paper))
    }

    fun setCut(enabled: Boolean) {
        val current = preferences.printer.value ?: return
        preferences.save(current.copy(cutPaper = enabled))
    }

    fun forget() {
        preferences.forget()
        local.value = Local()
    }

    fun testPrint() {
        if (local.value.testing || preferences.printer.value == null) return
        local.update { Local(testing = true, testResult = null) }
        viewModelScope.launch {
            val result = try {
                val shop = runCatching { settings.observeSettings().first() }.getOrDefault(StoreSettings())
                printing.printTest(shop.storeName, shop.receipt.language)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                PrintResult.WRITE_FAILED
            }
            local.value = Local(testing = false, testResult = result)
        }
    }

    fun resultShown() = local.update { it.copy(testResult = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                PrinterSettingsViewModel(container.printerPreferences, container.bluetoothDevices, container.receiptPrinting, container.settingsRepository)
            }
        }
    }
}
