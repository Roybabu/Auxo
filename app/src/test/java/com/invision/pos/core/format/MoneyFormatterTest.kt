package com.invision.pos.core.format

import com.invision.pos.domain.model.Money
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormatterTest {

    private val formatter = MoneyFormatter()

    @Test
    fun format_showsRupeesWithTwoDecimals() {
        val formatted = formatter.format(Money(125_050))

        assertTrue("Expected rupee symbol in '$formatted'", formatted.contains("₹"))
        assertTrue("Expected 1,250.50 in '$formatted'", formatted.contains("1,250.50"))
    }

    @Test
    fun format_zeroAmount() {
        val formatted = formatter.format(Money.ZERO)

        assertTrue("Expected 0.00 in '$formatted'", formatted.contains("0.00"))
    }
}
