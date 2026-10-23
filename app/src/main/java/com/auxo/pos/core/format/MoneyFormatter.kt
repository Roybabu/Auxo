// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.format

import com.auxo.pos.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats [Money] for display. Defaults to Indian rupees; the store's
 * currency will become a setting in a later step.
 */
class MoneyFormatter(
    private val locale: Locale = Locale.forLanguageTag("en-IN"),
    private val currency: Currency = Currency.getInstance("INR"),
) {
    fun format(money: Money): String {
        val fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
        val amount = BigDecimal.valueOf(money.minorUnits, fractionDigits)
        // NumberFormat is not thread-safe, so create one per call.
        val numberFormat = NumberFormat.getCurrencyInstance(locale).apply {
            currency = this@MoneyFormatter.currency
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
        return numberFormat.format(amount)
    }
}
