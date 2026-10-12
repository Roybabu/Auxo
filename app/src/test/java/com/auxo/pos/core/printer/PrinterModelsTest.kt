package com.auxo.pos.core.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrinterModelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs() = SharedPrefsPrinterPreferences(context.getSharedPreferences("printer-test", Context.MODE_PRIVATE)).also { it.forget() }

    @Test
    fun noPrinterIsChosenAtFirst() {
        assertNull(prefs().printer.value)
    }

    @Test
    fun theChosenPrinter_isKeptAcrossRestarts() {
        val first = prefs()
        first.save(SavedPrinter("00:11:22:33:44:55", "RPP02N", ReceiptPaper.THERMAL_80, cutPaper = true))
        val second = SharedPrefsPrinterPreferences(context.getSharedPreferences("printer-test", Context.MODE_PRIVATE))
        assertEquals(SavedPrinter("00:11:22:33:44:55", "RPP02N", ReceiptPaper.THERMAL_80, true), second.printer.value)
    }

    @Test
    fun forgetting_clearsIt() {
        val p = prefs()
        p.save(SavedPrinter("00:11:22:33:44:55", "RPP02N"))
        p.forget()
        assertNull(p.printer.value)
        assertNull(SharedPrefsPrinterPreferences(context.getSharedPreferences("printer-test", Context.MODE_PRIVATE)).printer.value)
    }

    @Test
    fun aSavedSheetPaper_fallsBackToTheNarrowRoll() {
        context.getSharedPreferences("printer-test", Context.MODE_PRIVATE).edit()
            .putString(SharedPrefsPrinterPreferences.KEY_ADDRESS, "AA:BB:CC:DD:EE:FF")
            .putString(SharedPrefsPrinterPreferences.KEY_PAPER, "A4")
            .apply()
        val loaded = SharedPrefsPrinterPreferences(context.getSharedPreferences("printer-test", Context.MODE_PRIVATE)).printer.value
        assertEquals(ReceiptPaper.THERMAL_58, loaded?.paper)
        // No name saved: the address stands in for it.
        assertEquals("AA:BB:CC:DD:EE:FF", loaded?.name)
    }

    @Test
    fun printersAreListedBeforeHeadphones() {
        val sorted = listOf(
            PairedDevice("1", "Zebra Buds", looksLikePrinter("Zebra Buds")),
            PairedDevice("2", "RPP02N", looksLikePrinter("RPP02N")),
            PairedDevice("3", "Alpha Watch", looksLikePrinter("Alpha Watch")),
            PairedDevice("4", "BT-Printer 58", looksLikePrinter("BT-Printer 58")),
        ).printersFirst()
        assertEquals(listOf("BT-Printer 58", "RPP02N", "Alpha Watch", "Zebra Buds"), sorted.map { it.name })
    }

    @Test
    fun looksLikePrinter_matchesCommonNames_andToleratesNull() {
        assertTrue(looksLikePrinter("MTP-II"))
        assertTrue(looksLikePrinter("Thermal Printer"))
        assertFalse(looksLikePrinter("Galaxy Buds"))
        assertFalse(looksLikePrinter(null))
    }
}
