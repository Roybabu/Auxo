package com.invision.pos.domain.loyalty

import com.invision.pos.domain.model.LedgerEntry
import com.invision.pos.domain.model.Money
import java.math.BigInteger

/**
 * The shop's loyalty rules, set by the Super Admin. Off by default. [spendPerPoint] is how much a customer
 * spends to earn one point; [pointValue] is what one point takes off a bill when redeemed.
 */
data class LoyaltyRules(
    val enabled: Boolean = false,
    val spendPerPoint: Money = DEFAULT_SPEND_PER_POINT,
    val pointValue: Money = DEFAULT_POINT_VALUE,
) {
    companion object {
        /** One point for every Rs 100 spent. */
        val DEFAULT_SPEND_PER_POINT = Money(10_000)

        /** One point is worth Re 1 (a 1% return at the defaults). */
        val DEFAULT_POINT_VALUE = Money(100)

        val OFF = LoyaltyRules()
    }
}

/** Points a customer is using on the bill being made, and what they take off. */
data class LoyaltyRedemption(val points: Int, val amount: Money) {
    init {
        require(points > 0 && amount > Money.ZERO) { "A redemption needs points and a value" }
    }
}

enum class LoyaltyRuleError { SPEND_REQUIRED, SPEND_INVALID, SPEND_OUT_OF_RANGE, VALUE_REQUIRED, VALUE_INVALID, VALUE_OUT_OF_RANGE, VALUE_ABOVE_SPEND }

sealed interface LoyaltyRuleCheck {
    data class Valid(val rules: LoyaltyRules) : LoyaltyRuleCheck
    data class Invalid(val errors: Set<LoyaltyRuleError>) : LoyaltyRuleCheck
}

/** Checks what the Super Admin typed on the loyalty settings page. */
object LoyaltyRuleValidator {
    /** Between Re 1 and Rs 1,00,000 of spending per point. */
    val SPEND_RANGE: ClosedRange<Money> = Money(100)..Money(10_000_000)

    /** Between 1 paisa and Rs 1,000 per point. */
    val VALUE_RANGE: ClosedRange<Money> = Money(1)..Money(100_000)

    fun check(enabled: Boolean, spendText: String, valueText: String): LoyaltyRuleCheck {
        val errors = mutableSetOf<LoyaltyRuleError>()
        val spend = amount(spendText, errors, LoyaltyRuleError.SPEND_REQUIRED, LoyaltyRuleError.SPEND_INVALID)
        val value = amount(valueText, errors, LoyaltyRuleError.VALUE_REQUIRED, LoyaltyRuleError.VALUE_INVALID)
        if (spend != null && spend !in SPEND_RANGE) errors += LoyaltyRuleError.SPEND_OUT_OF_RANGE
        if (value != null && value !in VALUE_RANGE) errors += LoyaltyRuleError.VALUE_OUT_OF_RANGE
        // A point must never be worth more than the spending that earns it, or shopping would pay for itself.
        if (spend != null && value != null && value > spend) errors += LoyaltyRuleError.VALUE_ABOVE_SPEND
        return if (errors.isEmpty() && spend != null && value != null) {
            LoyaltyRuleCheck.Valid(LoyaltyRules(enabled, spend, value))
        } else {
            LoyaltyRuleCheck.Invalid(errors)
        }
    }

    private fun amount(text: String, errors: MutableSet<LoyaltyRuleError>, required: LoyaltyRuleError, invalid: LoyaltyRuleError): Money? {
        if (text.isBlank()) {
            errors += required
            return null
        }
        val parsed = Money.parseOrNull(text)?.takeIf { it > Money.ZERO }
        if (parsed == null) errors += invalid
        return parsed
    }
}

/** The arithmetic of earning, redeeming and taking back points. Whole points only; nothing is rounded up. */
object LoyaltyMath {

    /** Re 1 is always left to pay, so points never make a bill free. */
    val MIN_LEFT_TO_PAY = Money(100)

    /** Points a bill earns: one for every full [LoyaltyRules.spendPerPoint] of its total. Zero while loyalty is off. */
    fun earned(rules: LoyaltyRules, billTotal: Money): Int {
        if (!rules.enabled || billTotal <= Money.ZERO) return 0
        return (billTotal.minorUnits / rules.spendPerPoint.minorUnits).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** What [points] take off a bill. */
    fun valueOf(rules: LoyaltyRules, points: Int): Money =
        Money(Math.multiplyExact(rules.pointValue.minorUnits, points.coerceAtLeast(0).toLong()))

    /**
     * The most points that can be used on a bill: no more than the customer has, and never enough to take
     * the bill (before tax) below [MIN_LEFT_TO_PAY].
     */
    fun maxRedeemable(rules: LoyaltyRules, balance: Int, billBeforePoints: Money): Int {
        if (!rules.enabled || balance <= 0) return 0
        val room = billBeforePoints.minorUnits - MIN_LEFT_TO_PAY.minorUnits
        if (room <= 0) return 0
        return minOf(balance.toLong(), room / rules.pointValue.minorUnits).toInt()
    }

    /**
     * Points to take back when goods are returned, so returning a bill does not keep its points. Proportional to
     * the share of the sale given back: [creditedAfter] is the total of all credit notes for the sale including
     * this one. Whole points, rounded down, so the total taken back never exceeds what was earned. The result is
     * never more than the [available] points the customer still has.
     */
    fun reversal(earned: Int, saleTotal: Money, creditedAfter: Money, alreadyReversed: Int, available: Int): Int {
        if (earned <= 0 || saleTotal <= Money.ZERO || available <= 0) return 0
        val credited = creditedAfter.minorUnits.coerceIn(0, saleTotal.minorUnits)
        val target = (BigInteger.valueOf(earned.toLong()) * BigInteger.valueOf(credited) / BigInteger.valueOf(saleTotal.minorUnits)).toInt()
        return (target - alreadyReversed).coerceAtLeast(0).coerceAtMost(available)
    }
}

/** Points held by a customer: the sum of the points on their ledger entries. */
object LoyaltyPoints {
    fun balance(entries: List<LedgerEntry>): Int = entries.sumOf { it.points }

    fun earnedTotal(entries: List<LedgerEntry>): Int = entries.filter { it.points > 0 }.sumOf { it.points }

    fun usedTotal(entries: List<LedgerEntry>): Int = entries.filter { it.points < 0 }.sumOf { -it.points }
}

/** What a point gives back as a share of the spending that earned it, e.g. "1" or "0.5" (percent). */
fun LoyaltyRules.returnPercentText(): String =
    java.math.BigDecimal(pointValue.minorUnits).multiply(java.math.BigDecimal(100))
        .divide(java.math.BigDecimal(spendPerPoint.minorUnits), 2, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
