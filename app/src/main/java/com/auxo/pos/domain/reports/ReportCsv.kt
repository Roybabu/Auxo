package com.auxo.pos.domain.reports

import com.auxo.pos.domain.model.formatRatePercent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes a report as CSV (RFC 4180) for spreadsheets: amounts as plain numbers
 * with two decimals, dates as yyyy-MM-dd, and times as yyyy-MM-dd HH:mm.
 */
object ReportCsv {
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun write(table: ReportTable, zone: ZoneId): String = buildString {
        appendRow(table.columns.map { it.title })
        table.rows.forEach { row -> appendRow(row.map { cell(it, zone) }) }
        table.totals?.let { totals -> appendRow(totals.map { cell(it, zone) }) }
    }

    fun cell(value: ReportValue, zone: ZoneId): String = when (value) {
        is ReportValue.Text -> value.value
        is ReportValue.Count -> value.value.toString()
        is ReportValue.Amount -> value.value.toDecimalString()
        is ReportValue.Quantity -> value.unit.format(value.value)
        is ReportValue.Day -> value.value.toString()
        is ReportValue.Moment -> DATE_TIME.format(Instant.ofEpochMilli(value.epochMillis).atZone(zone))
        is ReportValue.Rate -> formatRatePercent(value.basisPoints) + "%"
        ReportValue.Empty -> ""
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        append(cells.joinToString(",") { escape(it) })
        append("\r\n")
    }

    /** Quotes a field when it holds a comma, quote or line break, doubling inner quotes. */
    fun escape(field: String): String {
        // Text starting with =, + or @ would be run as a formula by spreadsheets.
        // (Negative amounts start with - and must stay numbers.)
        val safe = if (field.isNotEmpty() && field[0] in "=+@") "'$field" else field
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
