package com.invision.pos.domain.model

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

    companion object {
        val ZERO = Money(0L)
    }
}
