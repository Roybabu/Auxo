package com.invision.pos.domain.reports

import com.invision.pos.domain.billing.InvoiceNumbers
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.StockUnit
import java.time.LocalDate
import java.time.ZoneId

enum class ReportType(
    /** Whether the report covers a period; the rest show the current position. */
    val usesPeriod: Boolean,
) {
    SALES_SUMMARY(true),
    ITEM_SALES(true),
    COLLECTIONS(true),
    GST_RATES(true),
    HSN_SUMMARY(true),
    PURCHASES(true),
    STOCK_MOVEMENTS(true),
    WASTAGE(true),
    STOCK_VALUE(false),
    LOW_STOCK(false),
    EXPIRY(false),
    CUSTOMER_BALANCES(false),
}

enum class PeriodPreset { TODAY, YESTERDAY, LAST_7_DAYS, THIS_MONTH, LAST_MONTH, THIS_FINANCIAL_YEAR, ALL, CUSTOM }

/** A range of whole days, both ends included. Null ends mean no limit. */
data class ReportPeriod(val preset: PeriodPreset, val from: LocalDate?, val to: LocalDate?) {

    /** Start (inclusive) and end (exclusive) instants in [zone]. */
    fun bounds(zone: ZoneId): Pair<Long, Long> {
        val start = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
        val end = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        return start to end
    }

    /** Short text for file names, such as "2026-10-01_2026-10-11" or "all". */
    val slug: String
        get() = when {
            from == null && to == null -> "all"
            from == to -> from.toString()
            else -> "${from ?: "start"}_${to ?: "now"}"
        }

    companion object {
        /**
         * The period for [preset] as of [today]. CUSTOM uses [customFrom] and
         * [customTo], swapping them if they are the wrong way round.
         */
        fun of(preset: PeriodPreset, today: LocalDate, customFrom: LocalDate? = null, customTo: LocalDate? = null): ReportPeriod =
            when (preset) {
                PeriodPreset.TODAY -> ReportPeriod(preset, today, today)
                PeriodPreset.YESTERDAY -> today.minusDays(1).let { ReportPeriod(preset, it, it) }
                PeriodPreset.LAST_7_DAYS -> ReportPeriod(preset, today.minusDays(6), today)
                PeriodPreset.THIS_MONTH -> ReportPeriod(preset, today.withDayOfMonth(1), today)
                PeriodPreset.LAST_MONTH -> {
                    val start = today.withDayOfMonth(1).minusMonths(1)
                    ReportPeriod(preset, start, start.plusMonths(1).minusDays(1))
                }
                PeriodPreset.THIS_FINANCIAL_YEAR ->
                    ReportPeriod(preset, LocalDate.of(InvoiceNumbers.financialYearStart(today), 4, 1), today)
                PeriodPreset.ALL -> ReportPeriod(preset, null, null)
                PeriodPreset.CUSTOM -> {
                    val start = customFrom ?: today
                    val end = customTo ?: start
                    if (end.isBefore(start)) ReportPeriod(preset, end, start) else ReportPeriod(preset, start, end)
                }
            }
    }
}

/** One cell of a report, kept typed so the screen and the CSV file can each format it. */
sealed interface ReportValue {
    data class Text(val value: String) : ReportValue
    data class Count(val value: Long) : ReportValue
    data class Amount(val value: Money) : ReportValue
    data class Quantity(val value: Int, val unit: StockUnit = StockUnit.UNIT) : ReportValue
    data class Day(val value: LocalDate) : ReportValue
    data class Moment(val epochMillis: Long) : ReportValue
    data class Rate(val basisPoints: Int) : ReportValue
    data object Empty : ReportValue
}

enum class ColumnAlign { START, END }

data class ReportColumn(val title: String, val align: ColumnAlign = ColumnAlign.START)

/**
 * A finished report: columns, rows and an optional totals row. Column titles are
 * in English, like receipts, so exported files read the same everywhere.
 */
data class ReportTable(
    val title: String,
    val columns: List<ReportColumn>,
    val rows: List<List<ReportValue>>,
    val totals: List<ReportValue>? = null,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}
