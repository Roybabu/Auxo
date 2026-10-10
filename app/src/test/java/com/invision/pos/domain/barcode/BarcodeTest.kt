package com.invision.pos.domain.barcode

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Gs1Test {

    @Test
    fun checkDigit_matchesKnownCodes() {
        assertEquals(1, Gs1.checkDigit("400638133393")) // EAN-13 4006381333931
        assertEquals(7, Gs1.checkDigit("590123412345")) // EAN-13 5901234123457
        assertEquals(2, Gs1.checkDigit("03600029145")) // UPC-A 036000291452
        assertEquals(4, Gs1.checkDigit("9638507")) // EAN-8 96385074
    }

    @Test
    fun isValid_acceptsCorrectCodes() {
        assertTrue(Gs1.isValid("4006381333931"))
        assertTrue(Gs1.isValid("036000291452"))
        assertTrue(Gs1.isValid("96385074"))
    }

    @Test
    fun isValid_rejectsWrongCheckDigitLengthOrCharacters() {
        assertFalse(Gs1.isValid("4006381333932"))
        assertFalse(Gs1.isValid("400638133393"))
        assertFalse(Gs1.isValid("40063813339A1"))
        assertFalse(Gs1.isValid(""))
    }
}

class BarcodeRulesTest {

    @Test
    fun digitCodes_mapToGs1Symbologies() {
        assertEquals(BarcodeCheck.Valid(BarcodeSymbology.EAN_13), BarcodeRules.check("4006381333931"))
        assertEquals(BarcodeCheck.Valid(BarcodeSymbology.UPC_A), BarcodeRules.check("036000291452"))
        assertEquals(BarcodeCheck.Valid(BarcodeSymbology.EAN_8), BarcodeRules.check("96385074"))
    }

    @Test
    fun gs1LengthWithBadCheckDigit_isRejected() {
        assertEquals(BarcodeCheck.Invalid(BarcodeError.INVALID_CHECK_DIGIT), BarcodeRules.check("4006381333930"))
    }

    @Test
    fun otherValues_useCode128() {
        assertEquals(BarcodeCheck.Valid(BarcodeSymbology.CODE_128), BarcodeRules.check("TSHIRT-M-NAVY"))
        assertEquals(BarcodeCheck.Valid(BarcodeSymbology.CODE_128), BarcodeRules.check("12345"))
    }

    @Test
    fun invalidValues_areRejected() {
        assertEquals(BarcodeCheck.Invalid(BarcodeError.EMPTY), BarcodeRules.check(""))
        assertEquals(BarcodeCheck.Invalid(BarcodeError.INVALID_CHARACTERS), BarcodeRules.check("ABC 123"))
        assertEquals(BarcodeCheck.Invalid(BarcodeError.TOO_LONG), BarcodeRules.check("A".repeat(33)))
    }
}

class InternalBarcodeGeneratorTest {

    @Test
    fun next_returnsValidInStoreEan13() {
        val generator = InternalBarcodeGenerator(Random(42))
        repeat(50) {
            val code = generator.next()
            assertEquals(13, code.length)
            assertTrue(code.startsWith("2"))
            assertTrue("$code should be valid", Gs1.isValid(code))
            assertTrue(InternalBarcodeGenerator.isInStoreCode(code))
        }
    }

    @Test
    fun nextUnique_skipsCodesAlreadyTakenOrUsed() = runTest {
        val first = InternalBarcodeGenerator(Random(7)).next()
        val second = InternalBarcodeGenerator(Random(7)).apply { next() }.next()

        // Same seed: the first candidate is taken locally, so the second one is returned.
        val code = InternalBarcodeGenerator(Random(7)).nextUnique(taken = setOf(first)) { false }
        assertEquals(second, code)

        // The first candidate is already saved in the database.
        val fromDb = InternalBarcodeGenerator(Random(7)).nextUnique(taken = emptySet()) { it == first }
        assertNotEquals(first, fromDb)
    }

    @Test
    fun manufacturerCodes_areNotInStoreCodes() {
        assertFalse(InternalBarcodeGenerator.isInStoreCode("8901000000019"))
    }
}
