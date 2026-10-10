package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Money
import kotlin.math.abs

/**
 * Amount formatting with Indian digit grouping (1,23,45,678.90), independent of
 * device locale data so printed receipts look the same everywhere.
 */
object IndianNumberFormat {

    /** "1,23,456.78"; negative amounts get a leading minus. */
    fun amount(money: Money): String {
        val minor = money.minorUnits
        val absolute = abs(minor)
        val rupees = absolute / 100
        val paise = absolute % 100
        val sign = if (minor < 0) "-" else ""
        return "$sign${group(rupees)}.${paise.toString().padStart(2, '0')}"
    }

    /** "₹1,23,456.78". */
    fun rupees(money: Money): String {
        val text = amount(money)
        return if (text.startsWith("-")) "-₹${text.drop(1)}" else "₹$text"
    }

    /** Groups the last three digits, then pairs: 12345678 -> "1,23,45,678". */
    fun group(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$pairs,$lastThree"
    }
}

/** Writes an amount in words the Indian way, e.g. "Rupees One Lakh Five Hundred and Fifty Paise Only". */
object AmountInWords {

    private val ones = listOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
    )
    private val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    fun rupees(money: Money): String {
        val absolute = abs(money.minorUnits)
        val rupees = absolute / 100
        val paise = (absolute % 100).toInt()
        val rupeeWords = if (rupees == 0L) "Zero" else words(rupees)
        val paiseWords = if (paise > 0) " and ${belowHundred(paise)} Paise" else ""
        return "Rupees $rupeeWords$paiseWords Only"
    }

    /** Words for a whole number using crore, lakh, thousand and hundred. */
    fun words(number: Long): String {
        require(number >= 0)
        if (number == 0L) return "Zero"
        val parts = mutableListOf<String>()
        var n = number
        val crore = n / 1_00_00_000
        n %= 1_00_00_000
        if (crore > 0) parts += "${words(crore)} Crore"
        val lakh = (n / 1_00_000).toInt()
        n %= 1_00_000
        if (lakh > 0) parts += "${belowHundred(lakh)} Lakh"
        val thousand = (n / 1_000).toInt()
        n %= 1_000
        if (thousand > 0) parts += "${belowHundred(thousand)} Thousand"
        val hundred = (n / 100).toInt()
        n %= 100
        if (hundred > 0) parts += "${ones[hundred]} Hundred"
        if (n > 0) parts += belowHundred(n.toInt())
        return parts.joinToString(" ")
    }

    private fun belowHundred(n: Int): String = when {
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> "${tens[n / 10]} ${ones[n % 10]}"
    }
}
