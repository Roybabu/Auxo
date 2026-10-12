package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Rounding

/** A discount on a line or on the whole bill. */
sealed interface Discount {
    data class Amount(val value: Money) : Discount

    /** Basis points: 1050 = 10.5%. */
    data class Percent(val basisPoints: Int) : Discount

    /** The discount on [base]. Never more than [base]. */
    fun amountOn(base: Money): Money {
        val raw = when (this) {
            is Amount -> value
            is Percent -> Money(Rounding.halfUp(base.minorUnits * basisPoints, 10_000))
        }
        return if (raw > base) base else raw
    }

    companion object {
        /**
         * Parses discount input. Blank means no discount. Percentages allow up to
         * two decimals and at most 100.
         */
        fun parse(text: String, isPercent: Boolean): DiscountParse {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return DiscountParse.None
            return if (isPercent) {
                if (!Regex("""\d{1,3}(\.\d{1,2})?""").matches(trimmed)) return DiscountParse.Invalid
                val basisPoints = trimmed.toBigDecimal().movePointRight(2).toInt()
                when {
                    basisPoints > 10_000 -> DiscountParse.Invalid
                    basisPoints == 0 -> DiscountParse.None
                    else -> DiscountParse.Parsed(Percent(basisPoints))
                }
            } else {
                val amount = Money.parseOrNull(trimmed) ?: return DiscountParse.Invalid
                if (amount.isZero) DiscountParse.None else DiscountParse.Parsed(Amount(amount))
            }
        }
    }
}

sealed interface DiscountParse {
    data object None : DiscountParse
    data object Invalid : DiscountParse
    data class Parsed(val discount: Discount) : DiscountParse
}
