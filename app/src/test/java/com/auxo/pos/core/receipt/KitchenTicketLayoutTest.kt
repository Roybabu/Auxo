package com.auxo.pos.core.receipt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.billing.KitchenTicket
import com.auxo.pos.domain.billing.KotDelta
import com.auxo.pos.domain.billing.KotLine
import com.auxo.pos.domain.billing.SampleSales
import com.auxo.pos.domain.billing.ServiceType
import com.auxo.pos.domain.model.Language
import com.auxo.pos.domain.model.ReceiptOptions
import com.auxo.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class KitchenTicketLayoutTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val placeholder = Regex("%\\d\\$[sd]")

    private fun ticket(
        service: ServiceType = ServiceType.DINE_IN,
        table: String? = "T4",
        added: List<KotLine> = listOf(KotLine("Masala Dosa", "", 2), KotLine("Tea", "Large", 1)),
        cancelled: List<KotLine> = emptyList(),
        addOn: Boolean = false,
    ) = KitchenTicket("Sample Cafe", "KOT-003", service, table, "Asha", KotDelta(added, cancelled), addOn, 1_000_000L)

    private fun texts(receipt: ThermalReceipt) = receipt.elements.filterIsInstance<ThermalElement.Line>().map { it.text }

    @Test
    fun dineIn_showsKotNumberTableAndItems() {
        val lines = texts(KitchenTicketLayout.build(ticket(), 48, ZoneOffset.UTC))
        assertTrue(lines.contains("KOT: KOT-003"))
        assertTrue(lines.contains("TABLE T4"))
        assertTrue(lines.contains("2 x Masala Dosa"))
        assertTrue(lines.contains("1 x Tea Large"))
        assertTrue(lines.contains("For: Asha"))
        assertTrue(lines.contains("Total new items: 3"))
        assertFalse(lines.contains("ADD-ON ITEMS"))
    }

    @Test
    fun kotNumberAndTable_areDrawnLarge() {
        val big = KitchenTicketLayout.build(ticket(), 48, ZoneOffset.UTC).elements.filterIsInstance<ThermalElement.Line>().filter { it.large }.map { it.text }
        assertEquals(listOf("KOT: KOT-003", "TABLE T4"), big)
    }

    @Test
    fun takeaway_hasNoTable() {
        val lines = texts(KitchenTicketLayout.build(ticket(ServiceType.TAKEAWAY, table = null), 48, ZoneOffset.UTC))
        assertTrue(lines.contains("TAKEAWAY"))
        assertTrue(lines.none { it.startsWith("TABLE") })
    }

    @Test
    fun addOnTicket_isMarked_andCancelledItemsAreListedSeparately() {
        val lines = texts(
            KitchenTicketLayout.build(ticket(added = listOf(KotLine("Vada", "", 2)), cancelled = listOf(KotLine("Tea", "", 1)), addOn = true), 48, ZoneOffset.UTC),
        )
        assertTrue(lines.contains("ADD-ON ITEMS"))
        assertTrue(lines.indexOf("NEW ITEMS") < lines.indexOf("2 x Vada"))
        assertTrue(lines.indexOf("2 x Vada") < lines.indexOf("CANCELLED - DO NOT MAKE"))
        assertTrue(lines.indexOf("CANCELLED - DO NOT MAKE") < lines.indexOf("1 x Tea"))
        assertTrue(lines.contains("Total new items: 2"))
    }

    @Test
    fun noLineIsWiderThanTheRoll_onEitherThermalWidth() {
        val long = ticket(added = listOf(KotLine("Paneer Butter Masala with Extra Cheese and Butter Naan", "Family pack", 12)))
        for (width in listOf(32, 48)) {
            val receipt = KitchenTicketLayout.build(long, width, ZoneOffset.UTC)
            for (line in receipt.elements.filterIsInstance<ThermalElement.Line>()) {
                if (line.large) assertTrue("'${line.text}'", line.text.length <= width / 2) else assertTrue("'${line.text}'", line.text.length <= width)
            }
        }
    }

    @Test
    fun wrappedItemName_hangsUnderTheName() {
        val rows = texts(KitchenTicketLayout.build(ticket(added = listOf(KotLine("Paneer Butter Masala with Extra Cheese", "", 2))), 32, ZoneOffset.UTC))
        val start = rows.indexOfFirst { it.startsWith("2 x Paneer") }
        assertTrue(start >= 0)
        assertTrue(rows[start + 1].startsWith("    "))
    }

    @Test
    fun ticketPaper_isTheReceiptRoll_or80mmWhenBillsAreOnSheets() {
        assertEquals(ReceiptPaper.THERMAL_58, KitchenTicketOutput.ticketPaper(ReceiptPaper.THERMAL_58))
        assertEquals(ReceiptPaper.THERMAL_80, KitchenTicketOutput.ticketPaper(ReceiptPaper.A4))
        assertEquals(ReceiptPaper.THERMAL_80, KitchenTicketOutput.ticketPaper(ReceiptPaper.A5))
    }

    @Test
    fun pages_areOneTallRoll_forEveryPaper() {
        for (paper in ReceiptPaper.entries) {
            val pages = KitchenTicketOutput.pages(ticket(), paper, KotLabels())
            assertEquals(1, pages.size)
            assertTrue(pages[0].heightPt > pages[0].widthPt)
        }
    }

    @Test
    fun fileName_isSafe() {
        assertEquals("KOT-003.pdf", KitchenTicketOutput.fileName(ticket()))
        assertEquals("kot.pdf", KitchenTicketOutput.fileName(ticket().copy(kotNumber = "")))
    }

    // Words

    private fun values(labels: KotLabels): Map<String, String> =
        KotLabels::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(labels) as String }

    @Test
    fun englishResources_matchTheBuiltInWording_andEveryFieldHasAResource() {
        assertEquals(KotLabels(), KotLabels.from(context, Language.ENGLISH))
        assertEquals(values(KotLabels()).size, KotLabels.resourceIds.size)
        assertEquals(KotLabels.resourceIds.size, KotLabels.resourceIds.toSet().size)
    }

    @Test
    fun everyLanguage_hasEveryKitchenWord_withTheSamePlaceholders() {
        val english = values(KotLabels())
        for (language in Language.entries) {
            for ((name, value) in values(KotLabels.from(context, language))) {
                assertTrue("$name is empty in ${language.tag}", value.isNotBlank())
                assertEquals(
                    "$name placeholders differ in ${language.tag}",
                    placeholder.findAll(english.getValue(name)).map { it.value }.sorted().toList(),
                    placeholder.findAll(value).map { it.value }.sorted().toList(),
                )
            }
        }
    }

    @Test
    fun hindiAndBengali_areTranslated() {
        val english = values(KotLabels())
        for (language in listOf(Language.HINDI, Language.BENGALI)) {
            val same = values(KotLabels.from(context, language)).filter { (n, v) -> english[n] == v }.keys
            // The KOT abbreviation and the "%1$d x %2$s" template stay as they are.
            assertEquals(setOf("number", "line"), same)
            assertNotEquals(english.getValue("title"), values(KotLabels.from(context, language)).getValue("title"))
        }
    }

    // Invoices

    private val sale = SampleSales.shirtsAndBiscuits()

    @Test
    fun invoiceService_isShownOnThermalAndSheetBills() {
        val dineIn = sale.copy(serviceType = ServiceType.DINE_IN, tableNumber = "T4")
        val thermal = texts(ThermalReceiptBuilder.build(dineIn, ReceiptOptions(), 48, ZoneOffset.UTC))
        assertTrue(thermal.contains("Dine-in · Table T4"))
        val sheet = SheetInvoiceLayout.build(dineIn, ReceiptOptions(paper = ReceiptPaper.A4), ReceiptPaper.A4, ZoneOffset.UTC)
        assertTrue(sheet.detailLines.contains("Service" to "Dine-in · Table T4"))

        val takeaway = sale.copy(serviceType = ServiceType.TAKEAWAY)
        assertTrue(texts(ThermalReceiptBuilder.build(takeaway, ReceiptOptions(), 48, ZoneOffset.UTC)).contains("Takeaway"))
    }

    @Test
    fun ordinaryBills_haveNoServiceLine() {
        val thermal = texts(ThermalReceiptBuilder.build(sale, ReceiptOptions(), 48, ZoneOffset.UTC))
        assertTrue(thermal.none { it.startsWith("Dine-in") || it == "Takeaway" })
        val sheet = SheetInvoiceLayout.build(sale, ReceiptOptions(paper = ReceiptPaper.A4), ReceiptPaper.A4, ZoneOffset.UTC)
        assertTrue(sheet.detailLines.none { it.first == "Service" })
    }
}
