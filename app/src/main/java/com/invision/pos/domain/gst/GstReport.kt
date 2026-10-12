package com.invision.pos.domain.gst

import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.reports.ColumnAlign
import com.invision.pos.domain.reports.ReportColumn
import com.invision.pos.domain.reports.ReportPeriod
import com.invision.pos.domain.reports.ReportTable
import com.invision.pos.domain.reports.ReportValue
import com.invision.pos.domain.reports.Reports
import java.time.Instant
import java.time.ZoneId

/** Amounts that add up across invoices. [invoiceValue] is the invoice grand total, round-off included. */
data class GstTotals(
    val taxable: Money = Money.ZERO,
    val cgst: Money = Money.ZERO,
    val sgst: Money = Money.ZERO,
    val igst: Money = Money.ZERO,
    val roundOff: Money = Money.ZERO,
    val invoiceValue: Money = Money.ZERO,
) {
    val tax: Money get() = cgst + sgst + igst

    operator fun plus(other: GstTotals) = GstTotals(
        taxable + other.taxable, cgst + other.cgst, sgst + other.sgst, igst + other.igst,
        roundOff + other.roundOff, invoiceValue + other.invoiceValue,
    )

    operator fun minus(other: GstTotals) = GstTotals(
        taxable - other.taxable, cgst - other.cgst, sgst - other.sgst, igst - other.igst,
        roundOff - other.roundOff, invoiceValue - other.invoiceValue,
    )

    companion object {
        fun of(invoice: Invoice) = GstTotals(
            invoice.taxableTotal, invoice.cgstTotal, invoice.sgstTotal, invoice.igstTotal, invoice.roundOff, invoice.grandTotal,
        )
    }
}

/** Sales to businesses with a GSTIN (B2B) or to everyone else (B2C), with credit notes taken off. */
data class GstSegment(
    val sales: Int,
    val creditNotes: Int,
    val gross: GstTotals,
    val returned: GstTotals,
) {
    val net: GstTotals get() = gross - returned
}

/**
 * The GST position for a period, worked out from saved invoices and credit notes only. Nothing here changes
 * how an invoice is made or taxed: it reads the amounts that were printed on them. A credit note takes off
 * exactly the taxable value and tax it carries, so the net figures are what remains to be reported.
 */
data class GstReport(
    val period: ReportPeriod,
    val b2b: GstSegment,
    val b2c: GstSegment,
    val summary: ReportTable,
    val segments: ReportTable,
    val byRate: ReportTable,
    val hsn: ReportTable,
    val b2bDocuments: ReportTable,
) {
    val sales: Int get() = b2b.sales + b2c.sales
    val creditNotes: Int get() = b2b.creditNotes + b2c.creditNotes
    val gross: GstTotals get() = b2b.gross + b2c.gross
    val returned: GstTotals get() = b2b.returned + b2c.returned
    val net: GstTotals get() = gross - returned

    /** Every table in the order they are shown and exported. */
    val tables: List<ReportTable> get() = listOf(summary, segments, byRate, hsn, b2bDocuments)

    val isEmpty: Boolean get() = sales == 0 && creditNotes == 0
}

object GstReportBuilder {

    private val END = ColumnAlign.END

    private fun col(title: String, align: ColumnAlign = ColumnAlign.START) = ReportColumn(title, align)

    private fun amount(money: Money): ReportValue = ReportValue.Amount(money)

    private fun isB2b(invoice: Invoice): Boolean = !invoice.buyer.gstin.isNullOrBlank()

    fun build(invoices: List<Invoice>, period: ReportPeriod, zone: ZoneId): GstReport {
        val ordered = invoices.sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
        fun segment(b2b: Boolean): GstSegment {
            val group = ordered.filter { isB2b(it) == b2b }
            val sales = group.filter { it.type == InvoiceType.SALE }
            val notes = group.filter { it.type == InvoiceType.RETURN }
            return GstSegment(
                sales = sales.size,
                creditNotes = notes.size,
                gross = sales.fold(GstTotals()) { acc, inv -> acc + GstTotals.of(inv) },
                returned = notes.fold(GstTotals()) { acc, inv -> acc + GstTotals.of(inv) },
            )
        }
        val b2b = segment(true)
        val b2c = segment(false)
        return GstReport(
            period = period,
            b2b = b2b,
            b2c = b2c,
            summary = summaryTable(b2b, b2c),
            segments = segmentsTable(b2b, b2c),
            byRate = Reports.gstRates(ordered),
            hsn = Reports.hsnSummary(ordered),
            b2bDocuments = b2bDocumentsTable(ordered.filter { isB2b(it) }, zone),
        )
    }

    private val TOTAL_COLUMNS = listOf(
        col("Taxable value", END), col("CGST", END), col("SGST", END), col("IGST", END),
        col("Total tax", END), col("Round-off", END), col("Invoice value", END),
    )

    private fun totalsCells(t: GstTotals): List<ReportValue> =
        listOf(amount(t.taxable), amount(t.cgst), amount(t.sgst), amount(t.igst), amount(t.tax), amount(t.roundOff), amount(t.invoiceValue))

    private fun summaryTable(b2b: GstSegment, b2c: GstSegment): ReportTable {
        val gross = b2b.gross + b2c.gross
        val returned = b2b.returned + b2c.returned
        val net = gross - returned
        val sales = b2b.sales + b2c.sales
        val notes = b2b.creditNotes + b2c.creditNotes
        fun row(label: String, documents: Int, totals: GstTotals) =
            listOf<ReportValue>(ReportValue.Text(label), ReportValue.Count(documents.toLong())) + totalsCells(totals)
        return ReportTable(
            title = "Summary",
            columns = listOf(col("Details"), col("Documents", END)) + TOTAL_COLUMNS,
            rows = listOf(
                row("Sales invoices", sales, gross),
                row("Less: credit notes", notes, returned),
            ),
            totals = row("Net", sales + notes, net),
        )
    }

    private fun segmentsTable(b2b: GstSegment, b2c: GstSegment): ReportTable {
        fun row(label: String, s: GstSegment) = listOf<ReportValue>(
            ReportValue.Text(label), ReportValue.Count(s.sales.toLong()), ReportValue.Count(s.creditNotes.toLong()),
        ) + totalsCells(s.net)
        val both = GstSegment(b2b.sales + b2c.sales, b2b.creditNotes + b2c.creditNotes, b2b.gross + b2c.gross, b2b.returned + b2c.returned)
        return ReportTable(
            title = "B2B and B2C",
            columns = listOf(col("Type"), col("Invoices", END), col("Credit notes", END)) + TOTAL_COLUMNS,
            rows = listOf(row("B2B (buyer has a GSTIN)", b2b), row("B2C (no GSTIN)", b2c)),
            totals = row("Total (net of credit notes)", both),
        )
    }

    private fun b2bDocumentsTable(documents: List<Invoice>, zone: ZoneId): ReportTable {
        fun signed(invoice: Invoice, money: Money) = if (invoice.type == InvoiceType.RETURN) Money(-money.minorUnits) else money
        val rows = documents.map { inv ->
            listOf<ReportValue>(
                ReportValue.Day(Instant.ofEpochMilli(inv.createdAtMillis).atZone(zone).toLocalDate()),
                ReportValue.Text(inv.number),
                ReportValue.Text(if (inv.type == InvoiceType.RETURN) "Credit note" else "Invoice"),
                if (inv.originalInvoiceNumber.isNullOrBlank()) ReportValue.Empty else ReportValue.Text(inv.originalInvoiceNumber),
                ReportValue.Text(inv.buyer.gstin.orEmpty()),
                if (inv.buyer.name.isNullOrBlank()) ReportValue.Empty else ReportValue.Text(inv.buyer.name),
                if (inv.placeOfSupplyStateCode.isBlank()) ReportValue.Empty else ReportValue.Text(inv.placeOfSupplyStateCode),
                amount(signed(inv, inv.taxableTotal)),
                amount(signed(inv, inv.cgstTotal)),
                amount(signed(inv, inv.sgstTotal)),
                amount(signed(inv, inv.igstTotal)),
                amount(signed(inv, inv.grandTotal)),
            )
        }
        val net = documents.fold(GstTotals()) { acc, inv ->
            val t = GstTotals.of(inv)
            if (inv.type == InvoiceType.RETURN) acc - t else acc + t
        }
        return ReportTable(
            title = "B2B documents",
            columns = listOf(
                col("Date"), col("Number"), col("Type"), col("Against invoice"), col("Buyer GSTIN"), col("Buyer"), col("Place of supply"),
                col("Taxable value", END), col("CGST", END), col("SGST", END), col("IGST", END), col("Invoice value", END),
            ),
            rows = rows,
            totals = if (rows.isEmpty()) {
                null
            } else {
                listOf(
                    ReportValue.Text("Total"), ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty, ReportValue.Empty,
                    amount(net.taxable), amount(net.cgst), amount(net.sgst), amount(net.igst), amount(net.invoiceValue),
                )
            },
        )
    }
}
