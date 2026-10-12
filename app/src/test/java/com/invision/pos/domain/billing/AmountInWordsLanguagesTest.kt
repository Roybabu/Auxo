package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInWordsLanguagesTest {

    private fun words(minor: Long, language: Language) = AmountInWords.rupees(Money(minor), language)

    @Test
    fun english_isUnchanged() {
        assertEquals("Rupees One Hundred Eighteen Only", words(118_00, Language.ENGLISH))
        assertEquals(AmountInWords.rupees(Money(1_00_500_50)), words(1_00_500_50, Language.ENGLISH))
    }

    @Test
    fun hindi_spellsAmountsWithLakhAndCrore() {
        assertEquals("शून्य रुपये मात्र", words(0, Language.HINDI))
        assertEquals("एक सौ अठारह रुपये मात्र", words(118_00, Language.HINDI))
        assertEquals("एक हज़ार पाँच सौ सत्तावन रुपये मात्र", words(1_557_00, Language.HINDI))
        assertEquals("एक लाख पाँच सौ रुपये और पचास पैसे मात्र", words(1_00_500_50, Language.HINDI))
        assertEquals(
            "बारह करोड़ चौंतीस लाख छप्पन हज़ार सात सौ नवासी रुपये मात्र",
            words(12_34_56_789_00, Language.HINDI),
        )
    }

    @Test
    fun bengali_spellsAmountsWithLakhAndCrore() {
        assertEquals("শূন্য টাকা মাত্র", words(0, Language.BENGALI))
        assertEquals("এক শত আঠারো টাকা মাত্র", words(118_00, Language.BENGALI))
        assertEquals("এক হাজার পাঁচ শত সাতান্ন টাকা মাত্র", words(1_557_00, Language.BENGALI))
        assertEquals("এক লক্ষ পাঁচ শত টাকা এবং পঞ্চাশ পয়সা মাত্র", words(1_00_500_50, Language.BENGALI))
        assertEquals(
            "বারো কোটি চৌত্রিশ লক্ষ ছাপ্পান্ন হাজার সাত শত ঊননব্বই টাকা মাত্র",
            words(12_34_56_789_00, Language.BENGALI),
        )
    }

    @Test
    fun theWordsForOneToNinetyNine_areAllFilledInAndDistinct() {
        for (table in listOf(NumberWords.HINDI, NumberWords.BENGALI)) {
            val spelled = (1..99).map { table.belowHundred(it) }
            assertTrue(spelled.none { it.isBlank() })
            assertEquals(99, spelled.toSet().size)
        }
    }

    @Test
    fun aNegativeAmount_isSpelledWithoutASign() {
        assertEquals(words(118_00, Language.HINDI), words(-118_00, Language.HINDI))
    }
}
