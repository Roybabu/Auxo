package com.auxo.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodesTest {

    @Test
    fun aGeneratedCode_isTwentyCharactersFromTheSafeAlphabet() {
        repeat(200) {
            val code = RecoveryCodes.generate()
            assertEquals(RecoveryCodes.LENGTH, code.length)
            assertTrue(code.all { it in RecoveryCodes.ALPHABET })
            assertTrue(RecoveryCodes.isWellFormed(code))
        }
    }

    @Test
    fun theAlphabet_leavesOutTheLookAlikes() {
        assertEquals(32, RecoveryCodes.ALPHABET.length)
        assertEquals(32, RecoveryCodes.ALPHABET.toSet().size)
        assertTrue("0O1I".none { it in RecoveryCodes.ALPHABET })
    }

    @Test
    fun twoCodes_differ() {
        assertEquals(500, (1..500).map { RecoveryCodes.generate() }.toSet().size)
    }

    @Test
    fun aCode_isShownInFourGroupsOfFive() {
        assertEquals("ABCDE-FGHJK-LMNPQ-RSTUV", RecoveryCodes.format("ABCDEFGHJKLMNPQRSTUV"))
        assertEquals("ABCDE-FGHJK-LMNPQ-RSTUV", RecoveryCodes.format("abcde fghjk-lmnpq rstuv"))
    }

    @Test
    fun typingItBack_ignoresCaseSpacesAndDashes() {
        assertEquals("ABCDEFGHJKLMNPQRSTUV", RecoveryCodes.normalize(" abcde-fghjk lmnpq_rstuv \n"))
        assertTrue(RecoveryCodes.same("ABCDE-FGHJK-LMNPQ-RSTUV", "abcdefghjklmnpqrstuv"))
        assertFalse(RecoveryCodes.same("ABCDE-FGHJK-LMNPQ-RSTUV", "ABCDE-FGHJK-LMNPQ-RSTUW"))
    }

    @Test
    fun textThatCouldNotBeACode_isNotWellFormed() {
        assertFalse(RecoveryCodes.isWellFormed(""))
        assertFalse(RecoveryCodes.isWellFormed("ABCDE-FGHJK-LMNPQ-RSTU")) // too short
        assertFalse(RecoveryCodes.isWellFormed("ABCDE-FGHJK-LMNPQ-RSTUVW")) // too long
        assertFalse(RecoveryCodes.isWellFormed("ABCDE-FGHJK-LMNPQ-RSTU0")) // zero is not in the alphabet
    }

    @Test
    fun theSavedProof_matchesOnlyTheRightCode_andHoldsNoCodeText() {
        val hasher = PasswordHasher(iterations = 1_000)
        val code = RecoveryCodes.generate()
        val record = RecoveryRecord.create(code, hasher, 42L)
        assertTrue(record.matches(RecoveryCodes.format(code).lowercase(), hasher))
        assertFalse(record.matches(RecoveryCodes.generate(), hasher))
        assertFalse(record.matches("", hasher))
        assertEquals(42L, record.createdAtMillis)
        assertFalse(String(record.hash, Charsets.ISO_8859_1).contains(code))
        assertFalse(String(record.salt, Charsets.ISO_8859_1).contains(code))
    }

    @Test
    fun theSameCode_getsADifferentSaltEachTime() {
        val hasher = PasswordHasher(iterations = 1_000)
        val code = RecoveryCodes.generate()
        val a = RecoveryRecord.create(code, hasher, 1L)
        val b = RecoveryRecord.create(code, hasher, 1L)
        assertNotEquals(a.salt.toList(), b.salt.toList())
        assertNotEquals(a.hash.toList(), b.hash.toList())
    }
}
