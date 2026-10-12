package com.invision.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminSetupValidatorTest {

    private fun errors(
        name: String = "Roy Babu",
        identifier: String = "roy.babu",
        password: String = "Green-Leaf-42",
        confirm: String = password,
    ) = AdminSetupValidator.validate(name, identifier, password, confirm)

    @Test
    fun aCompleteValidForm_hasNoErrors() {
        assertTrue(errors().isEmpty())
        assertTrue(errors(identifier = "roy@example.com").isEmpty())
        assertTrue(errors(identifier = "  ROY_Babu-7  ").isEmpty())
    }

    @Test
    fun everyFieldIsRequired() {
        val all = AdminSetupValidator.validate("", "", "", "")
        assertEquals(SetupError.NAME_REQUIRED, all[SetupField.NAME])
        assertEquals(SetupError.IDENTIFIER_REQUIRED, all[SetupField.IDENTIFIER])
        assertEquals(SetupError.PASSWORD_REQUIRED, all[SetupField.PASSWORD])
        assertEquals(SetupError.CONFIRM_REQUIRED, all[SetupField.CONFIRM])
        assertEquals(SetupError.NAME_REQUIRED, errors(name = "   ")[SetupField.NAME])
    }

    @Test
    fun nameLengthIsLimited() {
        assertEquals(SetupError.NAME_TOO_LONG, errors(name = "A".repeat(61))[SetupField.NAME])
        assertTrue(errors(name = "A".repeat(60)).isEmpty())
    }

    @Test
    fun identifier_acceptsUsernamesAndEmailsOnly() {
        for (bad in listOf("ab", "has space", "bad!char", "a".repeat(33), "roy@", "@example.com", "roy@example", "roy@@example.com", "roy@exa mple.com")) {
            assertEquals("'$bad' should be rejected", SetupError.IDENTIFIER_INVALID, errors(identifier = bad)[SetupField.IDENTIFIER])
        }
        for (good in listOf("abc", "a.b-c_d", "x".repeat(32), "roy.babu@shop.co.in", "a+b@example.com")) {
            assertTrue("'$good' should be accepted", errors(identifier = good).isEmpty())
        }
    }

    @Test
    fun password_mustBeLongEnough_andNotTooLong() {
        assertEquals(SetupError.PASSWORD_TOO_SHORT, errors(password = "Ab1defg")[SetupField.PASSWORD])
        assertTrue(errors(password = "Ab1defgh").isEmpty())
        assertEquals(SetupError.PASSWORD_TOO_LONG, errors(password = "Ab1" + "x".repeat(126))[SetupField.PASSWORD])
    }

    @Test
    fun password_needsALetterAndADigit() {
        assertEquals(SetupError.PASSWORD_NEEDS_LETTER_AND_DIGIT, errors(password = "onlyletters")[SetupField.PASSWORD])
        assertEquals(SetupError.PASSWORD_NEEDS_LETTER_AND_DIGIT, errors(password = "1234567890")[SetupField.PASSWORD])
    }

    @Test
    fun password_cannotBeAVeryCommonOne_orContainTheUsername() {
        assertEquals(SetupError.PASSWORD_TOO_COMMON, errors(password = "Password1")[SetupField.PASSWORD])
        assertEquals(SetupError.PASSWORD_TOO_COMMON, errors(password = "QWERTY123")[SetupField.PASSWORD])
        assertEquals(SetupError.PASSWORD_CONTAINS_IDENTIFIER, errors(identifier = "royshop", password = "RoyShop2026")[SetupField.PASSWORD])
        assertEquals(SetupError.PASSWORD_CONTAINS_IDENTIFIER, errors(identifier = "royshop@example.com", password = "xroyshop9z")[SetupField.PASSWORD])
    }

    @Test
    fun confirmation_mustMatchExactly() {
        assertEquals(SetupError.CONFIRM_MISMATCH, errors(password = "Green-Leaf-42", confirm = "Green-Leaf-43")[SetupField.CONFIRM])
        assertEquals(SetupError.CONFIRM_MISMATCH, errors(password = "Green-Leaf-42", confirm = "green-leaf-42")[SetupField.CONFIRM])
        assertEquals(SetupError.CONFIRM_MISMATCH, errors(password = "Green-Leaf-42", confirm = "Green-Leaf-42 ")[SetupField.CONFIRM])
    }

    @Test
    fun normalising_trimsAndLowerCases() {
        assertEquals("roy babu", AdminSetupValidator.normalizeName("  roy   babu ").lowercase())
        assertEquals("roy@example.com", AdminSetupValidator.normalizeIdentifier("  Roy@Example.COM "))
    }
}
