package com.auxo.pos.domain.model

import java.math.BigDecimal
import kotlin.math.abs

/**
 * How a product's stock is counted. Shop products count units (pieces or packs).
 * Kitchen ingredients count grams or millilitres, so part of a pack can be used.
 *
 * Quantities are always whole numbers of the base unit. People type quantities and
 * see prices in the entry unit: kilograms for grams, litres for millilitres.
 */
enum class StockUnit(
    /** Base units in one entry unit. */
    val scale: Int,
) {
    UNIT(1),
    GRAM(1000),
    MILLILITRE(1000),
    ;

    /** Unit people type and see prices in: "pcs", "kg" or "L". */
    val entrySymbol: String
        get() = when (this) {
            UNIT -> "pcs"
            GRAM -> "kg"
            MILLILITRE -> "L"
        }

    /** Unit stock is counted in: "pcs", "g" or "ml". */
    val baseSymbol: String
        get() = when (this) {
            UNIT -> "pcs"
            GRAM -> "g"
            MILLILITRE -> "ml"
        }

    private val decimals: Int get() = if (scale == 1) 0 else 3

    /** Largest quantity accepted, in base units: a million pieces or 100 tonnes / kilolitres. */
    val maxBase: Int get() = if (scale == 1) 1_000_000 else 100_000_000

    /**
     * Parses a quantity typed in the entry unit ("1.25" kg) into base units (1250 g).
     * Returns null when blank, malformed, too precise, too large, or zero unless [allowZero].
     */
    fun parse(text: String, allowZero: Boolean = false): Int? {
        val trimmed = text.trim()
        val pattern = if (scale == 1) WHOLE else DECIMAL
        if (!pattern.matches(trimmed)) return null
        val base = trimmed.toBigDecimal().movePointRight(decimals).toLong()
        val min = if (allowZero) 0L else 1L
        return base.takeIf { it in min..maxBase.toLong() }?.toInt()
    }

    /** Base quantity in the entry unit, for editing: 1250 g -> "1.25". */
    fun toEntryText(base: Int): String =
        BigDecimal.valueOf(base.toLong(), decimals).stripTrailingZeros().toPlainString()

    /**
     * Readable quantity. Units are shown as a plain number ("12"); grams and
     * millilitres switch to kg and L from 1,000 ("750 g", "1.25 kg").
     */
    fun format(base: Int): String = when {
        this == UNIT -> base.toString()
        abs(base) >= scale -> "${toEntryText(base)} $entrySymbol"
        else -> "$base $baseSymbol"
    }

    /** Cost of [base] units when [costPerEntryUnit] is the price of one piece, kilogram or litre. */
    fun costOf(base: Int, costPerEntryUnit: Money): Money {
        val value = Rounding.halfUp(costPerEntryUnit.minorUnits * abs(base.toLong()), scale.toLong())
        return Money(if (base < 0) -value else value)
    }

    private companion object {
        val WHOLE = Regex("""\d{1,9}""")
        val DECIMAL = Regex("""\d{1,6}(\.\d{1,3})?""")
    }
}
