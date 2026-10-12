package com.auxo.pos.domain.gst

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.reports.PeriodPreset
import com.auxo.pos.domain.reports.ReportPeriod
import com.auxo.pos.domain.reports.ReportValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GstReportTest {

    private val period = ReportPeriod.of(PeriodPreset.CUSTOM, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val report = GstReportBuilder.build(GstTestData.all, period, GstTestData.zone)

    private fun m(rupees: String) = Money.parseOrNull(rupees)!!

    private fun amountsOf(row: List<ReportValue>) = row.filterIsInstance<ReportValue.Amount>().map { it.value }

    @Test
    fun netTotals_takeCreditNotesOffTheSales() {
        assertEquals(3, report.sales)
        assertEquals(2, report.creditNotes)
        assertEquals(GstTotals(m("3800"), m("55"), m("55"), m("255"), Money.ZERO, m("4165")), report.gross)
        assertEquals(GstTotals(m("600"), m("12.50"), m("12.50"), m("5"), Money.ZERO, m("630")), report.returned)
        assertEquals(GstTotals(m("3200"), m("42.50"), m("42.50"), m("250"), Money.ZERO, m("3535")), report.net)
        assertEquals(m("335"), report.net.tax)
        // Value is taxable value plus tax: the report adds up on its own.
        assertEquals(report.net.taxable + report.net.tax + report.net.roundOff, report.net.invoiceValue)
    }

    @Test
    fun b2bAndB2c_areSplitByTheBuyersGstin() {
        assertEquals(1, report.b2b.sales)
        assertEquals(1, report.b2b.creditNotes)
        assertEquals(GstTotals(m("2200"), Money.ZERO, Money.ZERO, m("250"), Money.ZERO, m("2450")), report.b2b.net)

        assertEquals(2, report.b2c.sales)
        assertEquals(1, report.b2c.creditNotes)
        assertEquals(GstTotals(m("1000"), m("42.50"), m("42.50"), Money.ZERO, Money.ZERO, m("1085")), report.b2c.net)

        assertEquals(report.net, report.b2b.net + report.b2c.net)
    }

    @Test
    fun taxByRate_isNetOfCreditNotes() {
        val table = report.byRate
        assertEquals(2, table.rows.size)
        // 5%: A 1000 + B 300 - D 500 - E 100
        assertEquals(ReportValue.Rate(500), table.rows[0][0])
        assertEquals(listOf(m("700"), m("12.50"), m("12.50"), m("10"), m("35"), m("735")), amountsOf(table.rows[0]))
        // 12%: B 2000 + C 500
        assertEquals(ReportValue.Rate(1200), table.rows[1][0])
        assertEquals(listOf(m("2500"), m("30"), m("30"), m("240"), m("300"), m("2800")), amountsOf(table.rows[1]))
        assertEquals(listOf(m("3200"), m("42.50"), m("42.50"), m("250"), m("335"), m("3535")), amountsOf(table.totals!!))
    }

    @Test
    fun hsnSummary_groupsByCodeAndRate_withReturnedQuantityTakenOff() {
        val rows = report.hsn.rows
        assertEquals(listOf("1905", "6109", "6109"), rows.map { (it[0] as ReportValue.Text).value })
        assertEquals(listOf(500, 500, 1200), rows.map { (it[2] as ReportValue.Rate).basisPoints })
        assertEquals(listOf(2L, 1L, 2L), rows.map { (it[3] as ReportValue.Count).value })
        assertEquals(listOf(m("200"), m("500"), m("2500")), rows.map { (it[4] as ReportValue.Amount).value })
        assertEquals(m("3200"), rows.sumOf { (it[4] as ReportValue.Amount).value.minorUnits }.let { Money(it) })
    }

    @Test
    fun summaryTable_showsSalesLessCreditNotesEqualsNet() {
        val table = report.summary
        assertEquals(listOf("Sales invoices", "Less: credit notes"), table.rows.map { (it[0] as ReportValue.Text).value })
        assertEquals(ReportValue.Count(3), table.rows[0][1])
        assertEquals(ReportValue.Count(2), table.rows[1][1])
        assertEquals(m("3800"), amountsOf(table.rows[0]).first())
        assertEquals(m("600"), amountsOf(table.rows[1]).first())
        assertEquals(m("3200"), amountsOf(table.totals!!).first())
        assertEquals(m("3535"), amountsOf(table.totals).last())
    }

    @Test
    fun segmentsTable_hasB2bB2cAndTotal() {
        val table = report.segments
        assertEquals(3, listOf(table.rows[0], table.rows[1], table.totals!!).size)
        assertEquals(listOf(m("2200"), Money.ZERO, Money.ZERO, m("250"), m("250"), Money.ZERO, m("2450")), amountsOf(table.rows[0]))
        assertEquals(listOf(m("1000"), m("42.50"), m("42.50"), Money.ZERO, m("85"), Money.ZERO, m("1085")), amountsOf(table.rows[1]))
        assertEquals(m("3535"), amountsOf(table.totals).last())
    }

    @Test
    fun b2bDocuments_listsInvoicesAndCreditNotes_withCreditNotesNegative() {
        val table = report.b2bDocuments
        assertEquals(listOf("INV/26-27/00002", "CN/26-27/00002"), table.rows.map { (it[1] as ReportValue.Text).value })
        assertEquals(listOf("Invoice", "Credit note"), table.rows.map { (it[2] as ReportValue.Text).value })
        assertEquals(ReportValue.Text("INV/26-27/00002"), table.rows[1][3]) // the credit note names its invoice
        assertEquals(ReportValue.Text(GstTestData.GSTIN), table.rows[0][4])
        assertEquals(ReportValue.Day(LocalDate.of(2026, 10, 5)), table.rows[0][0])
        assertEquals(m("2555"), (table.rows[0][11] as ReportValue.Amount).value)
        assertEquals(Money(-m("105").minorUnits), (table.rows[1][11] as ReportValue.Amount).value)
        assertEquals(m("2450"), (table.totals!![11] as ReportValue.Amount).value)
    }

    @Test
    fun anEmptyPeriod_isEmpty_andAllZero() {
        val empty = GstReportBuilder.build(emptyList(), period, GstTestData.zone)
        assertTrue(empty.isEmpty)
        assertEquals(GstTotals(), empty.net)
        assertTrue(empty.byRate.rows.isEmpty())
        assertTrue(empty.hsn.rows.isEmpty())
        assertTrue(empty.b2bDocuments.rows.isEmpty())
    }

    @Test
    fun theOrderOfInvoices_doesNotChangeTheReport() {
        val shuffled = GstReportBuilder.build(GstTestData.all.reversed(), period, GstTestData.zone)
        assertEquals(report, shuffled)
    }

    @Test
    fun allTablesAreExportedInOrder() {
        assertEquals(listOf("Summary", "B2B and B2C", "GST by rate", "HSN summary", "B2B documents"), report.tables.map { it.title })
    }

    @Test
    fun csv_holdsEveryTable_withPlainNumbers() {
        val csv = GstExport.csv(report, GstTestData.zone)
        assertTrue(csv.startsWith("Summary\r\n"))
        listOf("B2B and B2C", "GST by rate", "HSN summary", "B2B documents").forEach { assertTrue("missing $it", csv.contains("$it\r\n")) }
        assertTrue(csv.contains("Net,5,3200.00,42.50,42.50,250.00,335.00,0.00,3535.00"))
        assertTrue(csv.contains(GstTestData.GSTIN))
        assertEquals("GST-report-2026-10-01_2026-10-31", GstExport.baseName(report))
    }
}
