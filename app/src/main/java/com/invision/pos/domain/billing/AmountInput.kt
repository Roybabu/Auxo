package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Money

/** Typing rules for money fields: what to keep while the person types, and the tidy form afterwards. */
object AmountInput {

    /** Longest amount accepted, such as 9999999999.99. */
    const val MAX_LENGTH = 13

    /**
     * Keeps digits and a single decimal point, with at most two digits after it. Nothing is added,
     * so typing "5" stays "5" until the field is left.
     */
    fun sanitize(text: String): String {
        val out = StringBuilder()
        var seenDot = false
        var decimals = 0
        for (c in text) {
            when {
                c.isDigit() -> {
                    if (seenDot) {
                        if (decimals < 2) {
                            out.append(c)
                            decimals++
                        }
                    } else {
                        out.append(c)
                    }
                }
                c == '.' && !seenDot -> {
                    seenDot = true
                    out.append(c)
                }
            }
        }
        return out.toString().take(MAX_LENGTH)
    }

    /**
     * The amount with two decimals: "500" -> "500.00", "99.5" -> "99.50", "5." -> "5.00", ".5" -> "0.50".
     * Blank or invalid text gives "". Used when the field is left or the payment is submitted.
     */
    fun normalize(text: String): String {
        val trimmed = sanitize(text).trimEnd('.').let { if (it.startsWith(".")) "0$it" else it }
        return Money.parseOrNull(trimmed)?.toDecimalString() ?: ""
    }
}
