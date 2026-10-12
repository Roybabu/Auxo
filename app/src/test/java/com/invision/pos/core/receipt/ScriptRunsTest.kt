package com.invision.pos.core.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptRunsTest {

    private fun runs(text: String) = ScriptRuns.split(text).map { it.script to it.text }

    @Test
    fun plainEnglish_isOneLatinRun() {
        assertEquals(listOf(Script.LATIN to "Paid by Cash 1,557.00"), runs("Paid by Cash 1,557.00"))
    }

    @Test
    fun emptyText_hasNoRuns() {
        assertEquals(emptyList<ScriptRun>(), ScriptRuns.split(""))
    }

    @Test
    fun aHindiLabelWithItsAmount_staysInOneDevanagariRun() {
        assertEquals(listOf(Script.DEVANAGARI to "कुल  1,557.00"), runs("कुल  1,557.00"))
    }

    @Test
    fun aBengaliWordInsideEnglish_getsItsOwnRun_andSpacesFollowTheWordBefore() {
        assertEquals(
            listOf(Script.LATIN to "Item ", Script.BENGALI to "পণ্য ", Script.LATIN to "x"),
            runs("Item পণ্য x"),
        )
    }

    @Test
    fun aProductNameInAnotherScript_isNotSplitMidWord() {
        val result = runs("शर्ट Blue")
        assertEquals(listOf(Script.DEVANAGARI to "शर्ट ", Script.LATIN to "Blue"), result)
    }

    @Test
    fun aConjunctWithAJoiner_isNeverCut() {
        val word = "क्‍ष" // ksha with a zero-width joiner
        assertEquals(listOf(Script.DEVANAGARI to word), runs(word))
        assertEquals(1, runs("आप $word").size)
    }

    @Test
    fun theDanda_andRupeeSign_belongToNoScript() {
        assertEquals(listOf(Script.BENGALI to "মাত্র।"), runs("মাত্র।"))
        assertEquals(listOf(Script.DEVANAGARI to "₹ 5 बचाए"), runs("₹ 5 बचाए"))
        assertEquals(listOf(Script.LATIN to "₹ 5"), runs("₹ 5"))
    }

    @Test
    fun leadingSpacesAndDigits_joinTheFirstLettersAfterThem() {
        assertEquals(listOf(Script.LATIN to "  2 x ", Script.DEVANAGARI to "छूट"), runs("  2 x छूट"))
    }

    @Test
    fun runsPutTogether_giveBackTheOriginalText() {
        val text = "Shirt शर्ट  পণ্য ₹1,200.00 (Ref 12) मात्र।"
        assertEquals(text, ScriptRuns.split(text).joinToString("") { it.text })
    }
}
