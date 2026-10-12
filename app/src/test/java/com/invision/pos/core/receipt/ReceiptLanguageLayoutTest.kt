package com.invision.pos.core.receipt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReceiptLanguageLayoutTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val sale = SampleSales.shirtsAndBiscuits()

    private fun thermal(language: Language): List<String> {
        val labels = ReceiptLabels.from(context, language)
        return ThermalReceiptBuilder.build(sale, ReceiptOptions(language = language), 48, ZoneOffset.UTC, labels)
            .elements.mapNotNull { (it as? ThermalElement.Line)?.text }
    }

    @Test
    fun aHindiThermalBill_usesHindiWording_butKeepsNamesAsEntered() {
        val lines = thermal(Language.HINDI)
        assertTrue(lines.contains("कर चालान"))
        assertTrue(lines.any { it.startsWith("कुल") && it.endsWith("1,557.00") })
        assertFalse(lines.contains("TAX INVOICE"))
        sale.lines.forEach { line -> assertTrue(lines.any { it.contains(line.productName) }) }
    }

    @Test
    fun aBengaliThermalBill_usesBengaliWording() {
        val lines = thermal(Language.BENGALI)
        assertTrue(lines.contains("কর চালান"))
        assertTrue(lines.any { it.startsWith("মোট") && it.endsWith("1,557.00") })
    }

    @Test
    fun anEnglishBill_isIdenticalWhetherOrNotLabelsComeFromResources() {
        val builtIn = ThermalReceiptBuilder.build(sale, ReceiptOptions(), 48, ZoneOffset.UTC)
        val fromResources = ThermalReceiptBuilder.build(sale, ReceiptOptions(), 48, ZoneOffset.UTC, ReceiptLabels.from(context, Language.ENGLISH))
        // Compared line by line so a failure names the one line that differs.
        assertEquals(builtIn.elements.size, fromResources.elements.size)
        builtIn.elements.zip(fromResources.elements).forEachIndexed { index, (expected, actual) -> assertEquals("line $index", expected, actual) }
        assertEquals(builtIn, fromResources)
    }

    @Test
    fun aSheetInvoice_spellsTheTotalInTheBillLanguage() {
        fun sheet(language: Language) = SheetInvoiceLayout.build(
            sale, ReceiptOptions(language = language), ReceiptPaper.A4, ZoneOffset.UTC, ReceiptLabels.from(context, language),
        )
        assertEquals("Rupees One Thousand Five Hundred Fifty Seven Only", sheet(Language.ENGLISH).amountInWords)
        assertEquals("एक हज़ार पाँच सौ सत्तावन रुपये मात्र", sheet(Language.HINDI).amountInWords)
        assertEquals("এক হাজার পাঁচ শত সাতান্ন টাকা মাত্র", sheet(Language.BENGALI).amountInWords)
        assertEquals("कर चालान", sheet(Language.HINDI).title)
        assertEquals("সর্বমোট", sheet(Language.BENGALI).grandTotal.first)
        assertEquals(sale.lines.map { it.productName }, sheet(Language.HINDI).rows.map { it[1].substringBefore(" · ") })
    }
}
