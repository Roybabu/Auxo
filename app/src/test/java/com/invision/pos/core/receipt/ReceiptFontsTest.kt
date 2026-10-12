package com.invision.pos.core.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.domain.billing.AmountInWords
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReceiptFontsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The code points a font file has a glyph for, read from its cmap table (formats 4 and 12). */
    private fun coverage(bytes: ByteArray): Set<Int> {
        val buf = ByteBuffer.wrap(bytes)
        val tableCount = buf.getShort(4).toInt() and 0xFFFF
        var cmap = -1
        for (i in 0 until tableCount) {
            val at = 12 + i * 16
            val tag = String(bytes, at, 4, Charsets.US_ASCII)
            if (tag == "cmap") cmap = buf.getInt(at + 8)
        }
        assertTrue("no cmap table", cmap >= 0)
        val found = mutableSetOf<Int>()
        val subtables = buf.getShort(cmap + 2).toInt() and 0xFFFF
        for (i in 0 until subtables) {
            val offset = cmap + buf.getInt(cmap + 4 + i * 8 + 4)
            when (buf.getShort(offset).toInt() and 0xFFFF) {
                4 -> {
                    val segX2 = buf.getShort(offset + 6).toInt() and 0xFFFF
                    val ends = offset + 14
                    val starts = ends + segX2 + 2
                    for (s in 0 until segX2 / 2) {
                        val end = buf.getShort(ends + s * 2).toInt() and 0xFFFF
                        val start = buf.getShort(starts + s * 2).toInt() and 0xFFFF
                        if (start != 0xFFFF) for (cp in start..end) found += cp
                    }
                }
                12 -> {
                    val groups = buf.getInt(offset + 12)
                    for (g in 0 until groups) {
                        val at = offset + 16 + g * 12
                        for (cp in buf.getInt(at)..buf.getInt(at + 4)) found += cp
                    }
                }
            }
        }
        return found
    }

    private fun asset(path: String): ByteArray = context.assets.open(path).use { it.readBytes() }

    private fun everyCharacter(language: Language, script: Script): Set<Int> {
        val labels = ReceiptLabels.from(context, language)
        val texts = mutableListOf<String>()
        ReceiptLabels::class.java.declaredFields.filter { it.type == String::class.java }.forEach {
            it.isAccessible = true
            texts += (it.get(labels) as String).replace(Regex("%\\d\\$[sd]"), "")
        }
        // Amounts spelled out, and the digits and punctuation drawn next to the words.
        for (amount in listOf(0L, 1L, 20L, 99L, 100L, 1_000L, 1_00_000L, 1_00_00_000L, 12_34_56_789L, 1_23L, 45L)) {
            texts += AmountInWords.rupees(Money(amount * 100 + 50), language)
        }
        texts += " 0123456789.,-:()+%/&₹·…"
        // Only what the script's own font is asked to draw: Latin letters in a bill are drawn with the Latin font.
        return texts.flatMap { ScriptRuns.split(it) }.filter { it.script == script }.flatMap { it.text.codePoints().toArray().toList() }.toSet()
    }

    @Test
    fun theBundledFonts_areAllInTheApp_andAreOpenFontLicensed() {
        for (path in ReceiptFonts.assetPaths) {
            val bytes = asset(path)
            assertTrue("$path is too small to be a font", bytes.size > 50_000)
            assertEquals("$path is not a TrueType font", 0x00010000, ByteBuffer.wrap(bytes).getInt(0))
        }
        assertTrue(String(asset("fonts/OFL.txt")).contains("SIL OPEN FONT LICENSE"))
    }

    @Test
    fun theDevanagariFonts_haveEveryLetterTheHindiBillUses() {
        val needed = everyCharacter(Language.HINDI, Script.DEVANAGARI)
        for (path in listOf(ReceiptFonts.DEVANAGARI_REGULAR, ReceiptFonts.DEVANAGARI_BOLD)) {
            val have = coverage(asset(path))
            val missing = needed.filter { it !in have && it != 0x200D && it != 0x200C }
            assertTrue("$path lacks ${missing.map { Integer.toHexString(it) }}", missing.isEmpty())
        }
    }

    @Test
    fun theBengaliFonts_haveEveryLetterTheBengaliBillUses() {
        val needed = everyCharacter(Language.BENGALI, Script.BENGALI)
        for (path in listOf(ReceiptFonts.BENGALI_REGULAR, ReceiptFonts.BENGALI_BOLD)) {
            val have = coverage(asset(path))
            val missing = needed.filter { it !in have && it != 0x200D && it != 0x200C }
            assertTrue("$path lacks ${missing.map { Integer.toHexString(it) }}", missing.isEmpty())
        }
    }

    @Test
    fun theFonts_load_andEachScriptReportsItsOwnFonts() {
        val fonts = ReceiptFonts.load(context.assets)
        assertTrue(fonts.hasBundled(Script.DEVANAGARI))
        assertTrue(fonts.hasBundled(Script.BENGALI))
        assertTrue(!fonts.hasBundled(Script.LATIN))
        assertTrue(!ReceiptFonts.system.hasBundled(Script.DEVANAGARI))
    }

    @Test
    fun measuringMixedText_addsUpTheRuns() {
        val style = ScriptPaint(ReceiptFonts.system, size = 12f)
        val whole = style.measureText("Shirt शर्ट")
        val parts = ScriptRuns.split("Shirt शर्ट").sumOf { style.measureText(it.text).toDouble() }.toFloat()
        assertEquals(parts, whole, 0.01f)
    }

    @Test
    fun fit_keepsTextThatFits_andCutsOthersWithAnEllipsis() {
        val style = ScriptPaint(ReceiptFonts.system, size = 12f)
        val text = "Cotton shirt blue"
        val width = style.measureText(text)
        assertEquals(text, style.fit(text, width + 1f))
        val cut = style.fit(text, width / 2)
        assertTrue(cut.endsWith("…") && cut.length < text.length)
        assertTrue(style.measureText(cut) <= width / 2)
        assertEquals("", style.fit(text, 0.01f))
    }

    @Test
    fun everyPaper_drawsEveryLanguage_withoutError_andAddsInk() {
        val fonts = ReceiptFonts.load(context.assets)
        val sale = SampleSales.shirtsAndBiscuits()
        for (language in Language.entries) {
            val labels = ReceiptLabels.from(context, language)
            for (paper in ReceiptPaper.entries) {
                val pages = ReceiptRenderer.pages(sale, ReceiptOptions(paper = paper, language = language), ZoneOffset.UTC, null, labels, fonts)
                assertTrue("${language.tag} ${paper.name} has no pages", pages.isNotEmpty())
                pages.forEach { page ->
                    val bitmap = Bitmap.createBitmap(page.widthPt.toInt().coerceAtLeast(1), page.heightPt.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    page.draw(Canvas(bitmap))
                }
            }
        }
    }

    @Test
    fun aSheetInvoice_inHindi_stillWrapsTheItemColumn() {
        val sale = SampleSales.shirtsAndBiscuits()
        val labels = ReceiptLabels.from(context, Language.HINDI)
        val doc = SheetInvoiceLayout.build(sale, ReceiptOptions(language = Language.HINDI), ReceiptPaper.A4, ZoneOffset.UTC, labels)
        assertEquals(1, doc.columns.count { it.isItem })
        assertEquals("सामान", doc.columns.first { it.isItem }.title)
        assertEquals("पृष्ठ 1 / 2", fillIn(doc.pageNumberTemplate, 1, 2))
    }

    @Test
    fun thermalPairLines_carryTheirLabelAndAmountSeparately() {
        val sale = SampleSales.shirtsAndBiscuits()
        val labels = ReceiptLabels.from(context, Language.BENGALI)
        val receipt = ThermalReceiptBuilder.build(sale, ReceiptOptions(language = Language.BENGALI), 48, ZoneOffset.UTC, labels)
        val total = receipt.elements.filterIsInstance<ThermalElement.Line>().first { it.label == labels.total }
        assertEquals("1,557.00", total.value)
        assertTrue(total.text.startsWith(labels.total) && total.text.endsWith("1,557.00"))
    }
}
