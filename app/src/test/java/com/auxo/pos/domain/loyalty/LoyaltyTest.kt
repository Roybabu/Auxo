package com.auxo.pos.domain.loyalty

import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoyaltyTest {

    private val rules = LoyaltyRules(enabled = true, spendPerPoint = Money(10_000), pointValue = Money(100)) // 1 point per Rs 100, worth Re 1

    @Test
    fun loyalty_isOffByDefault() {
        assertFalse(LoyaltyRules().enabled)
        assertFalse(LoyaltyRules.OFF.enabled)
        assertEquals(0, LoyaltyMath.earned(LoyaltyRules(), Money(1_000_000)))
    }

    @Test
    fun pointsEarned_areWholePoints_roundedDown() {
        assertEquals(0, LoyaltyMath.earned(rules, Money(9_999)))
        assertEquals(1, LoyaltyMath.earned(rules, Money(10_000)))
        assertEquals(12, LoyaltyMath.earned(rules, Money(129_999)))
        assertEquals(0, LoyaltyMath.earned(rules, Money.ZERO))
        assertEquals(0, LoyaltyMath.earned(rules, Money(-5_000)))
    }

    @Test
    fun aBigBill_cannotOverflowThePoints() {
        val tiny = LoyaltyRules(true, Money(100), Money(1))
        assertEquals(Int.MAX_VALUE, LoyaltyMath.earned(tiny, Money(Long.MAX_VALUE / 2)))
    }

    @Test
    fun pointsAreWorthTheSetValue() {
        val rules = rules.copy(pointValue = Money(50))
        assertEquals(Money(1_500), LoyaltyMath.valueOf(rules, 30))
        assertEquals(Money.ZERO, LoyaltyMath.valueOf(rules, 0))
        assertEquals(Money.ZERO, LoyaltyMath.valueOf(rules, -4))
    }

    @Test
    fun redeemable_isLimitedByBalance_andLeavesReOneToPay() {
        // Bill Rs 500: room for 499 points; the customer has 50.
        assertEquals(50, LoyaltyMath.maxRedeemable(rules, balance = 50, billBeforePoints = Money(50_000)))
        // Customer has 900 points but the bill is Rs 500: Rs 499 off at most.
        assertEquals(499, LoyaltyMath.maxRedeemable(rules, balance = 900, billBeforePoints = Money(50_000)))
        // Rs 1 bill leaves nothing to redeem.
        assertEquals(0, LoyaltyMath.maxRedeemable(rules, balance = 900, billBeforePoints = Money(100)))
        assertEquals(0, LoyaltyMath.maxRedeemable(rules, balance = 0, billBeforePoints = Money(50_000)))
        assertEquals(0, LoyaltyMath.maxRedeemable(LoyaltyRules.OFF, balance = 900, billBeforePoints = Money(50_000)))
    }

    @Test
    fun redeemable_withAFractionalPointValue_roundsDown() {
        val rules = LoyaltyRules(true, Money(10_000), Money(30)) // 30 paise a point
        // Room is 10,000 - 100 = 9,900 paise: 330 points exactly; 1,000 paise room -> 33.
        assertEquals(330, LoyaltyMath.maxRedeemable(rules, 1_000, Money(10_000)))
        assertEquals(36, LoyaltyMath.maxRedeemable(rules, 1_000, Money(1_200))) // (1200-100)/30 = 36.67
    }

    @Test
    fun reversal_isProportionalToWhatWasReturned() {
        // Sale Rs 1,000 earned 10 points. Half returned -> 5 points taken back.
        assertEquals(5, LoyaltyMath.reversal(earned = 10, saleTotal = Money(100_000), creditedAfter = Money(50_000), alreadyReversed = 0, available = 99))
        // Then the other half: the total taken back is 10 in all.
        assertEquals(5, LoyaltyMath.reversal(10, Money(100_000), Money(100_000), alreadyReversed = 5, available = 99))
    }

    @Test
    fun reversal_neverTakesBackMoreThanWasEarned_orMoreThanTheCustomerHas() {
        // Three thirds of a Rs 1,000 sale (10 points): 3, 3, 4 add up to exactly 10.
        val first = LoyaltyMath.reversal(10, Money(100_000), Money(33_333), 0, 99)
        val second = LoyaltyMath.reversal(10, Money(100_000), Money(66_666), first, 99)
        val third = LoyaltyMath.reversal(10, Money(100_000), Money(100_000), first + second, 99)
        assertEquals(10, first + second + third)
        // The customer already spent the points: only what is left is taken, never a negative balance.
        assertEquals(2, LoyaltyMath.reversal(10, Money(100_000), Money(100_000), 0, available = 2))
        assertEquals(0, LoyaltyMath.reversal(10, Money(100_000), Money(100_000), 0, available = 0))
    }

    @Test
    fun reversal_ofASaleWithNoPoints_isZero() {
        assertEquals(0, LoyaltyMath.reversal(0, Money(100_000), Money(100_000), 0, 50))
        assertEquals(0, LoyaltyMath.reversal(5, Money.ZERO, Money(100), 0, 50))
    }

    @Test
    fun reversal_withHugeAmounts_doesNotOverflow() {
        assertEquals(1_000_000_000, LoyaltyMath.reversal(2_000_000_000, Money(1_000_000_000_000), Money(500_000_000_000), 0, Int.MAX_VALUE))
    }

    // Rules typed by the Super Admin

    @Test
    fun validRules_areAccepted() {
        val check = LoyaltyRuleValidator.check(true, "100", "1") as LoyaltyRuleCheck.Valid
        assertEquals(LoyaltyRules(true, Money(10_000), Money(100)), check.rules)
        val off = LoyaltyRuleValidator.check(false, "50.50", "0.25") as LoyaltyRuleCheck.Valid
        assertEquals(LoyaltyRules(false, Money(5_050), Money(25)), off.rules)
    }

    @Test
    fun badRules_areRefusedWithReasons() {
        fun errors(spend: String, value: String) = (LoyaltyRuleValidator.check(true, spend, value) as LoyaltyRuleCheck.Invalid).errors
        assertEquals(setOf(LoyaltyRuleError.SPEND_REQUIRED, LoyaltyRuleError.VALUE_REQUIRED), errors("", " "))
        assertEquals(setOf(LoyaltyRuleError.SPEND_INVALID), errors("abc", "1"))
        assertEquals(setOf(LoyaltyRuleError.SPEND_INVALID), errors("0", "1"))
        assertEquals(setOf(LoyaltyRuleError.VALUE_INVALID), errors("100", "-1"))
        assertEquals(setOf(LoyaltyRuleError.SPEND_OUT_OF_RANGE), errors("0.50", "0.10")) // below Re 1
        assertEquals(setOf(LoyaltyRuleError.SPEND_OUT_OF_RANGE), errors("200000", "1"))
        assertEquals(setOf(LoyaltyRuleError.VALUE_OUT_OF_RANGE), errors("100000", "2000"))
    }

    @Test
    fun aPointWorthMoreThanItsSpend_isRefused() {
        val errors = (LoyaltyRuleValidator.check(true, "10", "11") as LoyaltyRuleCheck.Invalid).errors
        assertEquals(setOf(LoyaltyRuleError.VALUE_ABOVE_SPEND), errors)
        assertTrue(LoyaltyRuleValidator.check(true, "10", "10") is LoyaltyRuleCheck.Valid)
    }

    // Points on the ledger

    private fun entry(type: LedgerEntryType, amount: Long, points: Int, at: Long, id: Long = at) =
        LedgerEntry(id = id, customerId = 1, type = type, amount = Money(amount), points = points, createdAtMillis = at)

    @Test
    fun pointsBalance_isTheSumOfThePointsOnTheLedger() {
        val entries = listOf(
            entry(LedgerEntryType.SALE, 50_000, 0, 1),
            entry(LedgerEntryType.POINTS_EARNED, 0, 20, 2),
            entry(LedgerEntryType.POINTS_REDEEMED, 0, -8, 3),
            entry(LedgerEntryType.POINTS_REVERSED, 0, -2, 4),
        )
        assertEquals(10, LoyaltyPoints.balance(entries))
        assertEquals(20, LoyaltyPoints.earnedTotal(entries))
        assertEquals(10, LoyaltyPoints.usedTotal(entries))
    }
}
