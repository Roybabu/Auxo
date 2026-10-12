package com.invision.pos.core.auth

import android.content.Context

/** Which counter of wrong guesses: the password at sign-in, or the recovery code. */
enum class LockoutKind { PASSWORD, RECOVERY_CODE }

/** Wrong guesses so far and the time until which further guesses are refused. */
data class Lockout(val failures: Int = 0, val lockedUntilMillis: Long = 0L)

/**
 * Keeps the wrong-guess counters. On a phone they are written to the app's private preferences, so closing
 * the app (or having it stopped) does not give a fresh set of guesses.
 */
interface LockoutStore {
    fun read(kind: LockoutKind): Lockout

    fun write(kind: LockoutKind, value: Lockout)
}

class InMemoryLockoutStore : LockoutStore {
    private val values = mutableMapOf<LockoutKind, Lockout>()

    @Synchronized
    override fun read(kind: LockoutKind): Lockout = values[kind] ?: Lockout()

    @Synchronized
    override fun write(kind: LockoutKind, value: Lockout) {
        values[kind] = value
    }
}

/** Kept outside the database and backups, so restoring a backup does not clear the counters. */
class SharedPrefsLockoutStore(context: Context) : LockoutStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(kind: LockoutKind): Lockout =
        Lockout(prefs.getInt("${kind.name}_failures", 0).coerceAtLeast(0), prefs.getLong("${kind.name}_until", 0L).coerceAtLeast(0L))

    @Synchronized
    override fun write(kind: LockoutKind, value: Lockout) {
        // commit(), not apply(): the count must be on disk even if the app is stopped right after a wrong guess.
        prefs.edit().putInt("${kind.name}_failures", value.failures).putLong("${kind.name}_until", value.lockedUntilMillis).commit()
    }

    private companion object {
        const val PREFS = "sign_in_lockout"
    }
}
