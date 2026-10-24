// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.model

import java.math.BigDecimal

/**
 * Net contents of one pack, e.g. 500 g or 1.25 L.
 * Stored in thousandths of the unit so decimals stay exact.
 */
data class NetQuantity(
    val amountMilli: Long,
    val unit: QuantityUnit,
) {
    init {
        require(amountMilli > 0) { "Net quantity must be positive" }
    }

    /** Amount without the unit, e.g. "1.25". */
    fun amountText(): String =
        BigDecimal.valueOf(amountMilli, 3).stripTrailingZeros().toPlainString()

    fun format(): String = "${amountText()} ${unit.symbol}"

    companion object {
        private val PATTERN = Regex("""\d{1,7}(\.\d{1,3})?""")

        fun parseOrNull(text: String, unit: QuantityUnit): NetQuantity? {
            val trimmed = text.trim()
            if (!PATTERN.matches(trimmed)) return null
            val milli = trimmed.toBigDecimalOrNull()?.movePointRight(3)?.toLong() ?: return null
            return if (milli > 0) NetQuantity(milli, unit) else null
        }
    }
}
