package com.auxo.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun plus_addsMinorUnits() {
        assertEquals(Money(1_550), Money(1_000) + Money(550))
    }

    @Test
    fun minus_subtractsMinorUnits() {
        assertEquals(Money(450), Money(1_000) - Money(550))
    }

    @Test
    fun times_multipliesByQuantity() {
        assertEquals(Money(3_750), Money(1_250) * 3)
    }

    @Test
    fun zero_isZero() {
        assertTrue(Money.ZERO.isZero)
        assertFalse(Money(1).isZero)
    }

    @Test
    fun compareTo_ordersByAmount() {
        assertTrue(Money(100) < Money(200))
        assertTrue(Money(200) > Money(100))
    }

    @Test(expected = ArithmeticException::class)
    fun plus_overflow_throwsInsteadOfWrapping() {
        Money(Long.MAX_VALUE) + Money(1)
    }

    @Test(expected = ArithmeticException::class)
    fun times_overflow_throwsInsteadOfWrapping() {
        Money(Long.MAX_VALUE) * 2
    }

    @Test
    fun parseOrNull_acceptsWholeAndDecimalAmounts() {
        assertEquals(Money(19_900), Money.parseOrNull("199"))
        assertEquals(Money(19_950), Money.parseOrNull("199.5"))
        assertEquals(Money(19_950), Money.parseOrNull(" 199.50 "))
        assertEquals(Money(5), Money.parseOrNull("0.05"))
    }

    @Test
    fun parseOrNull_rejectsBadInput() {
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("abc"))
        assertNull(Money.parseOrNull("-5"))
        assertNull(Money.parseOrNull("1.234"))
        assertNull(Money.parseOrNull("1,000"))
        assertNull(Money.parseOrNull("."))
        assertNull(Money.parseOrNull("12345678901"))
    }

    @Test
    fun toDecimalString_roundTrips() {
        assertEquals("199.50", Money(19_950).toDecimalString())
        assertEquals(Money(19_950), Money.parseOrNull(Money(19_950).toDecimalString()))
    }
}
