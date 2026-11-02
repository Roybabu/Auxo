// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

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
        appendRow(table.columns.map { it.title }.map { escape(it) })
        table.rows.forEach { row -> appendRow(row.map { field(it, zone) }) }
        table.totals?.let { totals -> appendRow(totals.map { field(it, zone) }) }
    }

    /** A cell ready for the file. Only text is guarded against formulas; numbers (also negative ones) stay numbers. */
    private fun field(value: ReportValue, zone: ZoneId): String =
        escape(cell(value, zone), isText = value is ReportValue.Text)

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
        append(cells.joinToString(","))
        append("\r\n")
    }

    /**
     * Quotes a field when it holds a comma, quote or line break, doubling inner quotes. With [isText], text that a
     * spreadsheet would run as a formula (starting with =, +, @, a dash followed by more, a tab or a carriage return)
     * gets a leading apostrophe.
     */
    fun escape(field: String, isText: Boolean = true): String {
        val safe = if (isText && startsLikeFormula(field)) "'$field" else field
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private fun startsLikeFormula(field: String): Boolean {
        if (field.isEmpty()) return false
        val first = field[0]
        return first in "=+@\t\r" || (first == '-' && field.length > 1)
    }
}
