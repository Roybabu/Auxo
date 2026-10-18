package com.auxo.pos.core.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Wrong guesses are remembered when the app is closed and opened again. */
@RunWith(AndroidJUnit4::class)
class LockoutPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val clock = MutableClock()
    private val hasher = PasswordHasher(iterations = 1_000)

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "lockout-${UUID.randomUUID()}").apply { mkdirs() }
    }

    private fun launch(lockouts: LockoutStore) = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default, lockouts = lockouts)

    @Test
    fun aLock_stillHolds_afterTheAppIsRestarted() = runBlocking {
        val saved = InMemoryLockoutStore()
        launch(saved).createAdministrator("Sample User", "sample.user", "Green-Leaf-42", "Green-Leaf-42")
        val first = launch(saved)
        repeat(5) { first.signIn("sample.user", "bad-Pass-$it") }

        // The next launch is a new repository over the same saved counters.
        val second = launch(saved)
        assertEquals(SignInResult.LockedOut(30), second.signIn("sample.user", "Green-Leaf-42"))
        clock.now += 31_000
        assertEquals(SignInResult.Success, second.signIn("sample.user", "Green-Leaf-42"))
    }

    @Test
    fun theFailureCount_isKept_soTheNextWrongGuessLocksAtOnce() = runBlocking {
        val saved = InMemoryLockoutStore()
        launch(saved).createAdministrator("Sample User", "sample.user", "Green-Leaf-42", "Green-Leaf-42")
        repeat(4) { launch(saved).signIn("sample.user", "bad-Pass-$it") } // four launches, one wrong guess each
        assertEquals(SignInResult.Rejected, launch(saved).signIn("sample.user", "bad-Pass-5")) // the fifth wrong guess
        assertEquals(SignInResult.LockedOut(30), launch(saved).signIn("sample.user", "Green-Leaf-42"))
    }

    @Test
    fun aRightPassword_clearsTheSavedCount() = runBlocking {
        val saved = InMemoryLockoutStore()
        launch(saved).createAdministrator("Sample User", "sample.user", "Green-Leaf-42", "Green-Leaf-42")
        repeat(3) { launch(saved).signIn("sample.user", "bad-Pass-$it") }
        assertEquals(SignInResult.Success, launch(saved).signIn("sample.user", "Green-Leaf-42"))
        assertEquals(Lockout(), saved.read(LockoutKind.PASSWORD))
    }

    @Test
    fun aClockSetBack_cannotLockSomeoneOutForLongerThanTheLongestLock() = runBlocking {
        val saved = InMemoryLockoutStore()
        launch(saved).createAdministrator("Sample User", "sample.user", "Green-Leaf-42", "Green-Leaf-42")
        saved.write(LockoutKind.PASSWORD, Lockout(failures = 9, lockedUntilMillis = clock.now + 10L * 24 * 60 * 60 * 1000))
        val locked = launch(saved).signIn("sample.user", "Green-Leaf-42") as SignInResult.LockedOut
        assertTrue(locked.seconds <= 300)
    }

    @Test
    fun savedCounters_arePerKind_andSurviveInThePhonesPreferences() {
        SharedPrefsLockoutStore(context).write(LockoutKind.PASSWORD, Lockout(3, 1234L))
        SharedPrefsLockoutStore(context).write(LockoutKind.RECOVERY_CODE, Lockout(7, 5678L))
        // A new object over the same preferences is what the next app launch sees.
        val reopened = SharedPrefsLockoutStore(context)
        assertEquals(Lockout(3, 1234L), reopened.read(LockoutKind.PASSWORD))
        assertEquals(Lockout(7, 5678L), reopened.read(LockoutKind.RECOVERY_CODE))
        reopened.write(LockoutKind.PASSWORD, Lockout())
        assertEquals(Lockout(7, 5678L), SharedPrefsLockoutStore(context).read(LockoutKind.RECOVERY_CODE))
    }
}
