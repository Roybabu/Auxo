package com.auxo.pos.domain.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun normalize_addsTwoDecimals() {
        assertEquals("500.00", AmountInput.normalize("500"))
        assertEquals("1250.00", AmountInput.normalize("1250"))
        assertEquals("99.50", AmountInput.normalize("99.5"))
        assertEquals("99.99", AmountInput.normalize("99.99"))
        assertEquals("5.00", AmountInput.normalize("5."))
        assertEquals("0.50", AmountInput.normalize(".5"))
    }

    @Test
    fun normalize_leavesNothingForBlankOrInvalid() {
        assertEquals("", AmountInput.normalize(""))
        assertEquals("", AmountInput.normalize("."))
        assertEquals("", AmountInput.normalize("abc"))
        assertEquals("", AmountInput.normalize("12345678901")) // too many digits for an amount
    }

    @Test
    fun sanitize_keepsOneDecimalPointAndTwoDecimals_withoutAddingZeros() {
        assertEquals("5", AmountInput.sanitize("5"))
        assertEquals("5.", AmountInput.sanitize("5."))
        assertEquals("1.50", AmountInput.sanitize("1.5.0")) // the extra point is dropped
        assertEquals("1.55", AmountInput.sanitize("1.5599"))
        assertEquals("12.5", AmountInput.sanitize("1a2.5x"))
        assertEquals("", AmountInput.sanitize("-"))
    }
}
