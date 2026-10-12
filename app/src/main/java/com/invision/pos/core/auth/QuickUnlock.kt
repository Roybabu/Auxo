package com.invision.pos.core.auth

import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PinError {
    /** Not 4 to 6 characters. */
    WRONG_LENGTH,
    NOT_DIGITS,

    /** Same digit repeated, a run such as 1234 or 8765, or a short pattern repeated such as 1212. */
    TOO_SIMPLE,
}

/** What makes an acceptable quick-unlock PIN. It is only a shortcut on a device that is already signed in. */
object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    fun validate(pin: String): PinError? {
        if (pin.length !in MIN_LENGTH..MAX_LENGTH) return PinError.WRONG_LENGTH
        if (pin.any { it !in '0'..'9' }) return PinError.NOT_DIGITS
        if (pin.all { it == pin[0] }) return PinError.TOO_SIMPLE
        val steps = pin.zipWithNext { a, b -> b - a }
        if (steps.all { it == 1 } || steps.all { it == -1 }) return PinError.TOO_SIMPLE
        if (pin.length % 2 == 0 && pin.take(pin.length / 2) == pin.drop(pin.length / 2)) return PinError.TOO_SIMPLE
        if (pin.length == 6 && pin.take(3) == pin.drop(3)) return PinError.TOO_SIMPLE
        return null
    }
}

/** The saved proof of one person's PIN: a salted hash, never the PIN. */
class PinRecord(val salt: ByteArray, val hash: ByteArray, val iterations: Int) {
    fun matches(pin: String, hasher: PasswordHasher): Boolean =
        PinPolicy.validate(pin).let { it == null || it == PinError.TOO_SIMPLE } &&
            MessageDigest.isEqual(hasher.hash(pin, salt, iterations), hash)

    companion object {
        fun create(pin: String, hasher: PasswordHasher): PinRecord {
            val salt = hasher.newSalt()
            return PinRecord(salt, hasher.hash(pin, salt), hasher.iterations)
        }
    }
}

/** How long the app may sit untouched before it locks. Chosen by the Super Admin; off until then. */
enum class IdleTimeout(val minutes: Int) {
    OFF(0),
    MINUTES_1(1),
    MINUTES_2(2),
    MINUTES_5(5),
    MINUTES_10(10),
    MINUTES_30(30),
    ;

    val millis: Long get() = minutes * 60_000L

    /** For the activity log. */
    fun describe(): String = when (minutes) {
        0 -> "off"
        1 -> "1 minute"
        else -> "$minutes minutes"
    }
}

/** Where the idle time is kept. It belongs to the device, not to any one person. */
interface LockSettingsStore {
    val idleTimeout: StateFlow<IdleTimeout>

    fun setIdleTimeout(value: IdleTimeout)
}

class InMemoryLockSettings(initial: IdleTimeout = IdleTimeout.OFF) : LockSettingsStore {
    private val _idleTimeout = MutableStateFlow(initial)
    override val idleTimeout: StateFlow<IdleTimeout> = _idleTimeout.asStateFlow()

    override fun setIdleTimeout(value: IdleTimeout) {
        _idleTimeout.value = value
    }
}

/** Kept in the app's private preferences, outside the database and backups, like the other device settings. */
class SharedPrefsLockSettings(context: Context) : LockSettingsStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _idleTimeout = MutableStateFlow(
        prefs.getString(KEY, null)?.let { name -> IdleTimeout.entries.firstOrNull { it.name == name } } ?: IdleTimeout.OFF,
    )
    override val idleTimeout: StateFlow<IdleTimeout> = _idleTimeout.asStateFlow()

    @Synchronized
    override fun setIdleTimeout(value: IdleTimeout) {
        prefs.edit().putString(KEY, value.name).apply()
        _idleTimeout.value = value
    }

    private companion object {
        const val PREFS = "lock_settings"
        const val KEY = "idle_timeout"
    }
}

/** Whether the app is locked, and what the lock screen may offer the signed-in person. */
data class LockInfo(
    val locked: Boolean = false,
    val userName: String = "",
    val hasPin: Boolean = false,
    val fingerprint: Boolean = false,
    /** Too many wrong PINs: the PIN and fingerprint are off until the password is used. */
    val quickUnlockOff: Boolean = false,
) {
    val pinAvailable: Boolean get() = hasPin && !quickUnlockOff
    val fingerprintAvailable: Boolean get() = fingerprint && !quickUnlockOff
}

/** What the person has set up for themselves. */
data class QuickUnlockStatus(val hasPin: Boolean, val fingerprint: Boolean)

sealed interface QuickUnlockChange {
    data object Done : QuickUnlockChange
    data class InvalidPin(val error: PinError) : QuickUnlockChange
    data object WrongPassword : QuickUnlockChange
    data class LockedOut(val seconds: Int) : QuickUnlockChange

    /** Nobody is signed in, or the account is no longer active. */
    data object Denied : QuickUnlockChange
    data object Failed : QuickUnlockChange
}

sealed interface UnlockResult {
    data object Unlocked : UnlockResult

    /** [triesLeft] more wrong PINs and quick unlock switches off. */
    data class WrongPin(val triesLeft: Int) : UnlockResult

    /** That was the last allowed wrong PIN. The PIN and fingerprint are off until the password is used. */
    data object PinLockedOut : UnlockResult
    data object WrongPassword : UnlockResult
    data class LockedOut(val seconds: Int) : UnlockResult

    /** Not locked, no PIN set, quick unlock switched off, or the account is no longer active. */
    data object Unavailable : UnlockResult
}
