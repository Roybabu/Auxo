package com.auxo.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickUnlockTest {

    @Test
    fun aPin_isFourToSixDigits() {
        for (ok in listOf("4829", "13579", "902714", "7302")) assertNull(ok, PinPolicy.validate(ok))
        assertEquals(PinError.WRONG_LENGTH, PinPolicy.validate("482"))
        assertEquals(PinError.WRONG_LENGTH, PinPolicy.validate("4829017"))
        assertEquals(PinError.WRONG_LENGTH, PinPolicy.validate(""))
        assertEquals(PinError.NOT_DIGITS, PinPolicy.validate("48a9"))
        assertEquals(PinError.NOT_DIGITS, PinPolicy.validate("48 9"))
        assertEquals(PinError.NOT_DIGITS, PinPolicy.validate("४८२९")) // digits from another script are not accepted
    }

    @Test
    fun obviousPins_areRefused() {
        for (simple in listOf("0000", "1111", "999999", "1234", "4321", "123456", "654321", "1212", "123123", "9090")) {
            assertEquals(simple, PinError.TOO_SIMPLE, PinPolicy.validate(simple))
        }
        assertNull(PinPolicy.validate("1235"))
        assertNull(PinPolicy.validate("1223"))
    }

    @Test
    fun theSavedPin_matchesOnlyTheRightOne_andHoldsNoDigits() {
        val hasher = PasswordHasher(iterations = 1_000)
        val record = PinRecord.create("4829", hasher)
        assertTrue(record.matches("4829", hasher))
        assertFalse(record.matches("4830", hasher))
        assertFalse(record.matches("", hasher))
        assertFalse(record.matches("48290", hasher))
        assertFalse(String(record.hash, Charsets.ISO_8859_1).contains("4829"))
    }

    @Test
    fun theSamePin_getsADifferentSaltEachTime() {
        val hasher = PasswordHasher(iterations = 1_000)
        val a = PinRecord.create("4829", hasher)
        val b = PinRecord.create("4829", hasher)
        assertNotEquals(a.salt.toList(), b.salt.toList())
        assertNotEquals(a.hash.toList(), b.hash.toList())
    }

    @Test
    fun idleTimes_areOffOrAFewMinutes() {
        assertEquals(0L, IdleTimeout.OFF.millis)
        assertEquals(60_000L, IdleTimeout.MINUTES_1.millis)
        assertEquals(1_800_000L, IdleTimeout.MINUTES_30.millis)
        assertEquals("off", IdleTimeout.OFF.describe())
        assertEquals("1 minute", IdleTimeout.MINUTES_1.describe())
        assertEquals("5 minutes", IdleTimeout.MINUTES_5.describe())
        assertEquals(listOf(0, 1, 2, 5, 10, 30), IdleTimeout.entries.map { it.minutes })
    }

    @Test
    fun theLockScreen_offersQuickUnlock_onlyWhileItIsOn() {
        val info = LockInfo(locked = true, hasPin = true, fingerprint = true)
        assertTrue(info.pinAvailable && info.fingerprintAvailable)
        val off = info.copy(quickUnlockOff = true)
        assertFalse(off.pinAvailable || off.fingerprintAvailable)
        assertFalse(LockInfo(locked = true).pinAvailable)
    }

    @Test
    fun inMemoryLockSettings_startOff_andHoldTheChoice() {
        val settings = InMemoryLockSettings()
        assertEquals(IdleTimeout.OFF, settings.idleTimeout.value)
        settings.setIdleTimeout(IdleTimeout.MINUTES_5)
        assertEquals(IdleTimeout.MINUTES_5, settings.idleTimeout.value)
    }
}
