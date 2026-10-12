package com.invision.pos.testutil

import android.graphics.Bitmap
import com.invision.pos.core.printer.BluetoothDevices
import com.invision.pos.core.printer.DeviceList
import com.invision.pos.core.printer.PrintResult
import com.invision.pos.core.printer.PrinterPreferences
import com.invision.pos.core.printer.PrinterTransport
import com.invision.pos.core.printer.ReceiptPrinting
import com.invision.pos.core.printer.SavedPrinter
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.ReceiptOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePrinterPreferences(initial: SavedPrinter? = null) : PrinterPreferences {
    private val state = MutableStateFlow(initial)
    override val printer: StateFlow<SavedPrinter?> = state
    override fun save(printer: SavedPrinter) { state.value = printer }
    override fun forget() { state.value = null }
}

class FakeBluetoothDevices(var list: DeviceList = DeviceList.Ready(emptyList())) : BluetoothDevices {
    override fun list(): DeviceList = list
}

class FakePrinterTransport(var result: PrintResult = PrintResult.PRINTED) : PrinterTransport {
    val sent = mutableListOf<Pair<String, ByteArray>>()
    override suspend fun send(address: String, bytes: ByteArray): PrintResult {
        sent += address to bytes
        return result
    }
}

class FakeReceiptPrinting(initial: SavedPrinter? = null) : ReceiptPrinting {
    private val state = MutableStateFlow(initial)
    override val printer: StateFlow<SavedPrinter?> = state
    var result = PrintResult.PRINTED
    val printed = mutableListOf<Pair<Invoice, ReceiptOptions>>()
    val tests = mutableListOf<Pair<String, Language>>()

    override suspend fun printInvoice(invoice: Invoice, options: ReceiptOptions, logo: Bitmap?): PrintResult {
        printed += invoice to options
        return result
    }

    override suspend fun printTest(shopName: String, language: Language): PrintResult {
        tests += shopName to language
        return result
    }
}
