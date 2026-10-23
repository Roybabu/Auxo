// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.model

import java.math.BigDecimal

/**
 * An amount of money stored in the currency's minor unit (for INR, paise).
 *
 * Using whole minor units avoids floating-point rounding errors in totals.
 * Arithmetic throws [ArithmeticException] on overflow instead of wrapping.
 */
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {

    val isZero: Boolean get() = minorUnits == 0L

    operator fun plus(other: Money): Money = Money(Math.addExact(minorUnits, other.minorUnits))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(minorUnits, other.minorUnits))

    operator fun times(quantity: Int): Money = Money(Math.multiplyExact(minorUnits, quantity.toLong()))

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    /** Plain decimal form for editing, e.g. 1250 -> "12.50". */
    fun toDecimalString(): String = BigDecimal.valueOf(minorUnits, FRACTION_DIGITS).toPlainString()

    companion object {
        val ZERO = Money(0L)

        private const val FRACTION_DIGITS = 2
        private val DECIMAL_PATTERN = Regex("""\d{1,10}(\.\d{1,2})?""")

        /**
         * Parses user input such as "12", "12.5" or "12.50".
         * Returns null for blank, negative, malformed or over-precise values.
         */
        fun parseOrNull(text: String): Money? {
            val trimmed = text.trim()
            if (!DECIMAL_PATTERN.matches(trimmed)) return null
            val decimal = trimmed.toBigDecimalOrNull() ?: return null
            return try {
                Money(decimal.movePointRight(FRACTION_DIGITS).longValueExact())
            } catch (e: ArithmeticException) {
                null
            }
        }
    }
}
