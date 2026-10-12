package com.auxo.pos.domain.model

/** How GST is charged on a product. Rates are in basis points: 500 = 5%. */
sealed interface GstRate {

    /** One rate whatever the price, e.g. 5% or 18%. */
    data class Fixed(val basisPoints: Int) : GstRate {
        init {
            require(basisPoints in 0..MAX_BASIS_POINTS) { "GST rate out of range" }
        }
    }

    /**
     * Apparel and footwear under GST 2.0: 5% when the sale value of a piece is
     * up to ₹2,500, 18% above that. The sale value is the pre-GST transaction
     * value after discounts.
     */
    data object Apparel : GstRate

    companion object {
        const val MAX_BASIS_POINTS = 10_000

        /** Slabs offered as quick choices in the product editor. */
        val STANDARD_BASIS_POINTS = listOf(0, 500, 1_800, 4_000)

        val APPAREL_THRESHOLD = Money(2_500_00)
        const val APPAREL_LOW_BASIS_POINTS = 500
        const val APPAREL_HIGH_BASIS_POINTS = 1_800
    }
}

/**
 * Returns the rate in basis points for one piece selling at [netUnitValue].
 * [pricesIncludeTax] says whether [netUnitValue] already contains GST.
 */
fun GstRate.basisPointsFor(netUnitValue: Money, pricesIncludeTax: Boolean): Int = when (this) {
    is GstRate.Fixed -> basisPoints
    GstRate.Apparel -> {
        val low = GstRate.APPAREL_LOW_BASIS_POINTS
        // At the lower rate, is the pre-GST value of the piece within the threshold?
        val taxableAtLow = if (pricesIncludeTax) {
            Rounding.halfUp(netUnitValue.minorUnits * 10_000, 10_000L + low)
        } else {
            netUnitValue.minorUnits
        }
        if (taxableAtLow <= GstRate.APPAREL_THRESHOLD.minorUnits) low else GstRate.APPAREL_HIGH_BASIS_POINTS
    }
}

/** Formats basis points as a percentage, e.g. 500 -> "5", 25 -> "0.25", 1250 -> "12.5". */
fun formatRatePercent(basisPoints: Int): String =
    java.math.BigDecimal.valueOf(basisPoints.toLong(), 2).stripTrailingZeros().toPlainString()

/** Parses a percentage such as "5", "0.25" or "12.5" into basis points. */
fun parseRatePercent(text: String): Int? {
    val trimmed = text.trim()
    if (!Regex("""\d{1,3}(\.\d{1,2})?""").matches(trimmed)) return null
    val basisPoints = trimmed.toBigDecimal().movePointRight(2).toInt()
    return basisPoints.takeIf { it in 0..GstRate.MAX_BASIS_POINTS }
}

object Rounding {
    /** [numerator] / [denominator] rounded half up. Both must be non-negative. */
    fun halfUp(numerator: Long, denominator: Long): Long {
        require(numerator >= 0 && denominator > 0)
        return (numerator / denominator) + if ((numerator % denominator) * 2 >= denominator) 1 else 0
    }
}
