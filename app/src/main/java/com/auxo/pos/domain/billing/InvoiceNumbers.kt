package com.auxo.pos.domain.billing

import java.time.LocalDate

/**
 * Invoice numbers restart each Indian financial year (April to March) and look
 * like "INV/26-27/00001": at most 16 characters as GST rules require.
 */
object InvoiceNumbers {

    const val PREFIX_MAX = 4
    private val PREFIX = Regex("""[A-Z0-9]{1,$PREFIX_MAX}""")

    /** The calendar year in which the financial year containing [date] starts. */
    fun financialYearStart(date: LocalDate): Int = if (date.monthValue >= 4) date.year else date.year - 1

    /** "26-27" for the year starting April 2026. */
    fun financialYearLabel(startYear: Int): String {
        val first = (startYear % 100).toString().padStart(2, '0')
        val second = ((startYear + 1) % 100).toString().padStart(2, '0')
        return "$first-$second"
    }

    fun format(prefix: String, financialYearStart: Int, sequence: Long): String =
        "$prefix/${financialYearLabel(financialYearStart)}/${sequence.toString().padStart(5, '0')}"

    /** Key used to keep one counter per prefix and financial year. */
    fun sequenceKey(prefix: String, financialYearStart: Int): String = "$prefix|$financialYearStart"

    fun isValidPrefix(prefix: String): Boolean = PREFIX.matches(prefix)
}
