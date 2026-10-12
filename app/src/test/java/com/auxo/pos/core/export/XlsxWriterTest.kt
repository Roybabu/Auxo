package com.auxo.pos.core.export

import com.auxo.pos.domain.gst.GstReportBuilder
import com.auxo.pos.domain.gst.GstTestData
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.reports.ColumnAlign
import com.auxo.pos.domain.reports.PeriodPreset
import com.auxo.pos.domain.reports.ReportColumn
import com.auxo.pos.domain.reports.ReportPeriod
import com.auxo.pos.domain.reports.ReportTable
import com.auxo.pos.domain.reports.ReportValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return files
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private val report = GstReportBuilder.build(
        GstTestData.all,
        ReportPeriod.of(PeriodPreset.CUSTOM, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
        GstTestData.zone,
    )

    @Test
    fun theWorkbook_hasTheRequiredPartsAndEverySheet() {
        val files = unzip(XlsxWriter.write(report.tables, GstTestData.zone))
        listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml").forEach {
            assertTrue("missing $it", it in files)
        }
        (1..5).forEach { assertTrue("missing sheet $it", "xl/worksheets/sheet$it.xml" in files) }
        assertEquals(10, files.size)
    }

    @Test
    fun everyPart_isWellFormedXml() {
        unzip(XlsxWriter.write(report.tables, GstTestData.zone)).forEach { (name, content) ->
            parse(content) // throws if it is not well formed
            assertTrue(name, content.startsWith("<?xml"))
        }
    }

    @Test
    fun sheets_areNamedAfterTheTables() {
        val workbook = parse(unzip(XlsxWriter.write(report.tables, GstTestData.zone)).getValue("xl/workbook.xml"))
        val names = (0 until workbook.getElementsByTagName("sheet").length).map { (workbook.getElementsByTagName("sheet").item(it) as Element).getAttribute("name") }
        assertEquals(listOf("Summary", "B2B and B2C", "GST by rate", "HSN summary", "B2B documents"), names)
    }

    private fun cells(sheetXml: String): Map<String, Element> {
        val doc = parse(sheetXml)
        val list = doc.getElementsByTagName("c")
        return (0 until list.length).map { list.item(it) as Element }.associateBy { it.getAttribute("r") }
    }

    @Test
    fun amounts_areRealNumbers_withTheKnownTotals() {
        val summary = cells(unzip(XlsxWriter.write(report.tables, GstTestData.zone)).getValue("xl/worksheets/sheet1.xml"))
        // Row 1 headings, rows 2-3 sales and credit notes, row 4 the net line. Columns: Details, Documents, Taxable, CGST, SGST, IGST, tax, round-off, value.
        fun number(ref: String) = summary.getValue(ref).getElementsByTagName("v").item(0).textContent
        assertEquals("3800.00", number("C2"))
        assertEquals("600.00", number("C3"))
        assertEquals("3200.00", number("C4"))
        assertEquals("42.50", number("D4"))
        assertEquals("250.00", number("F4"))
        assertEquals("335.00", number("G4"))
        assertEquals("3535.00", number("I4"))
        assertEquals("5", number("B4"))
        assertTrue(summary.getValue("C4").getAttribute("t").isEmpty()) // a number, not text
    }

    @Test
    fun text_isStoredAsText_andHeadingsAndTotalsAreBold() {
        val summary = cells(unzip(XlsxWriter.write(report.tables, GstTestData.zone)).getValue("xl/worksheets/sheet1.xml"))
        assertEquals("inlineStr", summary.getValue("A2").getAttribute("t"))
        assertEquals("Sales invoices", summary.getValue("A2").textContent)
        assertEquals("1", summary.getValue("A1").getAttribute("s")) // heading
        assertEquals("1", summary.getValue("A4").getAttribute("s")) // totals row label
        assertEquals("3", summary.getValue("C4").getAttribute("s")) // bold money
    }

    @Test
    fun textThatLooksLikeAFormula_staysText() {
        val table = ReportTable(
            "Test",
            listOf(ReportColumn("Name"), ReportColumn("Amount", ColumnAlign.END)),
            listOf(listOf(ReportValue.Text("=SUM(A1:A9)"), ReportValue.Amount(Money(1_00)))),
        )
        val sheet = cells(unzip(XlsxWriter.write(listOf(table), GstTestData.zone)).getValue("xl/worksheets/sheet1.xml"))
        assertEquals("inlineStr", sheet.getValue("A2").getAttribute("t"))
        assertEquals("=SUM(A1:A9)", sheet.getValue("A2").textContent)
        assertEquals(0, sheet.getValue("A2").getElementsByTagName("f").length)
    }

    @Test
    fun specialCharacters_areEscaped_andControlCharactersDropped() {
        val table = ReportTable(
            "T",
            listOf(ReportColumn("Buyer")),
            listOf(listOf(ReportValue.Text("Das & Sons <\"Ltd\"> \u0001ok"))),
        )
        val sheet = cells(unzip(XlsxWriter.write(listOf(table), GstTestData.zone)).getValue("xl/worksheets/sheet1.xml"))
        assertEquals("Das & Sons <\"Ltd\"> ok", sheet.getValue("A2").textContent)
    }

    @Test
    fun sheetNames_areValidAndUnique() {
        val names = XlsxWriter.sheetNames(listOf("Summary", "summary", "A/B:C?[x]*", "x".repeat(40), ""))
        assertEquals(5, names.map { it.lowercase() }.toSet().size)
        assertTrue(names.all { it.length in 1..31 })
        assertTrue(names.none { n -> n.any { it in "[]:*?/\\" } })
        assertFalse(names[2].contains('/'))
    }

    @Test
    fun columnNames_followTheSpreadsheetOrder() {
        assertEquals(listOf("A", "B", "Z", "AA", "AB", "AZ", "BA"), listOf(0, 1, 25, 26, 27, 51, 52).map { XlsxWriter.columnName(it) })
    }

    @Test
    fun anEmptyTable_stillGivesASheetWithHeadings() {
        val table = ReportTable("Empty", listOf(ReportColumn("A"), ReportColumn("B")), emptyList())
        val sheet = cells(unzip(XlsxWriter.write(listOf(table), GstTestData.zone)).getValue("xl/worksheets/sheet1.xml"))
        assertEquals(setOf("A1", "B1"), sheet.keys)
    }
}
