package com.auxo.pos.core.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

class MutableClock(var now: Long = 1_700_000_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis(): Long = now
}

@RunWith(AndroidJUnit4::class)
class AuthRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val clock = MutableClock()

    // A low work factor keeps the tests quick; production uses PasswordHasher.DEFAULT_ITERATIONS.
    private val hasher = PasswordHasher(iterations = 1_000)

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "auth-${UUID.randomUUID()}").apply { mkdirs() }
    }

    /** A new repository over the same folder is what the next app launch sees. */
    private fun launch() = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default)

    private val accountFile get() = File(dir, "accounts.json")

    private suspend fun LocalAuthRepository.setUp(
        name: String = "Roy Babu",
        identifier: String = "roy.babu",
        pw: String = "Green-Leaf-42",
        confirm: String = pw,
    ) = createAdministrator(name, identifier, pw, confirm)

    // First launch

    @Test
    fun firstLaunch_needsSetup_andWritesNothing() {
        assertEquals(AuthState.NeedsSetup, launch().state.value)
        assertFalse(accountFile.exists())
    }

    @Test
    fun aLeftoverTemporaryFile_isNotAnAccount() {
        File(dir, "accounts.json.tmp").writeText("{half written")
        assertEquals(AuthState.NeedsSetup, launch().state.value)
    }

    // Setup

    @Test
    fun successfulSetup_createsTheAdministrator_andSignsThemIn() = runBlocking {
        val auth = launch()
        assertEquals(SetupResult.Created, auth.setUp(name = "  Roy   Babu "))
        assertEquals(AuthState.SignedIn("Roy Babu"), auth.state.value)
        assertTrue(accountFile.isFile)
    }

    @Test
    fun thePasswordIsNeverStoredInPlainText() = runBlocking {
        launch().setUp(pw = "Green-Leaf-42")
        val saved = accountFile.readText()
        assertFalse(saved.contains("Green-Leaf-42"))
        assertFalse(saved.lowercase().contains("green-leaf"))
        assertTrue(saved.contains("\"salt\""))
        assertTrue(saved.contains("\"hash\""))
        assertTrue(saved.contains("\"iterations\":1000"))
    }

    @Test
    fun twoAccountsWithTheSamePassword_getDifferentSaltsAndHashes() = runBlocking {
        launch().setUp()
        val first = FileAccountStore(dir).read() as StoredAccounts.Present
        val otherDir = File(context.cacheDir, "auth-${UUID.randomUUID()}").apply { mkdirs() }
        LocalAuthRepository(FileAccountStore(otherDir), hasher, clock).setUp()
        val second = FileAccountStore(otherDir).read() as StoredAccounts.Present
        assertFalse(first.accounts.single().salt.contentEquals(second.accounts.single().salt))
        assertFalse(first.accounts.single().hash.contentEquals(second.accounts.single().hash))
    }

    @Test
    fun setupLeavesNoTemporaryFileBehind() = runBlocking {
        launch().setUp()
        assertFalse(File(dir, "accounts.json.tmp").exists())
    }

    @Test
    fun invalidInput_createsNothing() = runBlocking {
        val auth = launch()
        val result = auth.setUp(name = "", identifier = "no", pw = "short")
        assertTrue(result is SetupResult.Invalid)
        val errors = (result as SetupResult.Invalid).errors
        assertEquals(SetupError.NAME_REQUIRED, errors[SetupField.NAME])
        assertEquals(SetupError.IDENTIFIER_INVALID, errors[SetupField.IDENTIFIER])
        assertEquals(SetupError.PASSWORD_TOO_SHORT, errors[SetupField.PASSWORD])
        assertEquals(AuthState.NeedsSetup, auth.state.value)
        assertFalse(accountFile.exists())
    }

    @Test
    fun aPasswordMismatch_createsNothing() = runBlocking {
        val auth = launch()
        val result = auth.setUp(pw = "Green-Leaf-42", confirm = "Green-Leaf-24")
        assertEquals(SetupError.CONFIRM_MISMATCH, (result as SetupResult.Invalid).errors[SetupField.CONFIRM])
        assertFalse(accountFile.exists())
        // The same person can correct it and finish.
        assertEquals(SetupResult.Created, auth.setUp())
    }

    // Restart and existing administrator

    @Test
    fun afterARestart_theAppShowsLogin_notSetup() = runBlocking {
        launch().setUp()
        assertEquals(AuthState.SignedOut, launch().state.value)
        assertEquals(AuthState.SignedOut, launch().state.value)
    }

    @Test
    fun anExistingAdministrator_isDetected_andSetupIsRefused_withoutChangingTheAccount() = runBlocking {
        launch().setUp(name = "Roy Babu", identifier = "roy.babu", pw = "Green-Leaf-42")
        val before = accountFile.readText()

        val next = launch()
        val attempt = next.setUp(name = "Intruder", identifier = "intruder", pw = "Other-Pass-99")
        assertEquals(SetupResult.AlreadyInitialized, attempt)
        assertEquals(before, accountFile.readText())
        assertEquals(AuthState.SignedOut, next.state.value)

        // Only the original credentials work.
        assertEquals(SignInResult.Rejected, next.signIn("intruder", "Other-Pass-99"))
        assertEquals(SignInResult.Success, next.signIn("roy.babu", "Green-Leaf-42"))
    }

    @Test
    fun aStaleSetupScreen_cannotOverwriteAnAccountCreatedMeanwhile() = runBlocking {
        val stale = launch() // thinks this is a first launch
        launch().setUp(pw = "Green-Leaf-42")
        val result = stale.setUp(name = "Late", identifier = "late.user", pw = "Late-Pass-77")
        assertEquals(SetupResult.AlreadyInitialized, result)
        assertEquals(AuthState.SignedOut, stale.state.value)
        assertEquals(SignInResult.Success, launch().signIn("roy.babu", "Green-Leaf-42"))
    }

    // Duplicates and concurrency

    @Test
    fun concurrentSetupAttempts_createExactlyOneAdministrator() = runBlocking {
        val repositories = List(3) { launch() }
        val results = (0 until 12).map { i ->
            async(Dispatchers.Default) {
                repositories[i % repositories.size].setUp(name = "Admin $i", identifier = "admin$i", pw = "Strong-Pass-$i")
            }
        }.awaitAll()
        assertEquals(1, results.count { it == SetupResult.Created })
        assertEquals(11, results.count { it == SetupResult.AlreadyInitialized })
        val saved = (FileAccountStore(dir).read() as StoredAccounts.Present).accounts.single()
        // Whoever won, the account is that one attempt's and still works.
        val index = saved.identifier.removePrefix("admin").toInt()
        assertEquals(SignInResult.Success, launch().signIn("admin$index", "Strong-Pass-$index"))
    }

    @Test
    fun theSameRepositoryRefusesASecondSetup() = runBlocking {
        val auth = launch()
        assertEquals(SetupResult.Created, auth.setUp())
        assertEquals(SetupResult.AlreadyInitialized, auth.setUp(name = "Second", identifier = "second.admin", pw = "Second-Pass-1"))
        assertEquals(AuthState.SignedIn("Roy Babu"), auth.state.value)
    }

    // A damaged account must never become "no account"

    @Test
    fun anUnreadableAccount_isNotTreatedAsFirstLaunch() = runBlocking {
        accountFile.writeText("this is not json")
        val auth = launch()
        assertEquals(AuthState.Unreadable, auth.state.value)
        assertEquals(SetupResult.Failed, auth.setUp())
        assertEquals("this is not json", accountFile.readText())
        assertEquals(SignInResult.Unavailable, auth.signIn("roy.babu", "Green-Leaf-42"))
    }

    // Sign-in

    @Test
    fun signIn_checksTheUsernameAndPassword() = runBlocking {
        launch().setUp(identifier = "Roy@Example.com", pw = "Green-Leaf-42")
        val auth = launch()
        assertEquals(SignInResult.Rejected, auth.signIn("roy@example.com", "wrong-Pass-1"))
        assertEquals(SignInResult.Rejected, auth.signIn("someone.else", "Green-Leaf-42"))
        assertEquals(AuthState.SignedOut, auth.state.value)
        // Case of the username does not matter; the password's case does.
        assertEquals(SignInResult.Rejected, auth.signIn("roy@example.com", "green-leaf-42"))
        assertEquals(SignInResult.Success, auth.signIn("  ROY@example.COM ", "Green-Leaf-42"))
        assertEquals(AuthState.SignedIn("Roy Babu"), auth.state.value)
    }

    @Test
    fun repeatedFailures_lockSigningInForAWhile() = runBlocking {
        launch().setUp(pw = "Green-Leaf-42")
        val auth = launch()
        repeat(5) { assertEquals(SignInResult.Rejected, auth.signIn("roy.babu", "bad-Pass-$it")) }
        // Even the right password waits while locked.
        assertEquals(SignInResult.LockedOut(30), auth.signIn("roy.babu", "Green-Leaf-42"))
        clock.now += 10_000
        assertEquals(SignInResult.LockedOut(20), auth.signIn("roy.babu", "Green-Leaf-42"))
        clock.now += 21_000
        assertEquals(SignInResult.Success, auth.signIn("roy.babu", "Green-Leaf-42"))
    }

    @Test
    fun theLockGrowsWithMoreFailures_butIsCapped() = runBlocking {
        launch().setUp()
        val auth = launch()
        repeat(5) { auth.signIn("roy.babu", "bad-Pass-1") }
        clock.now += 31_000
        auth.signIn("roy.babu", "bad-Pass-1") // 6th failure: 60 s
        val locked = auth.signIn("roy.babu", "Green-Leaf-42") as SignInResult.LockedOut
        assertEquals(60, locked.seconds)
        repeat(8) {
            clock.now += 400_000
            auth.signIn("roy.babu", "bad-Pass-1")
        }
        val capped = auth.signIn("roy.babu", "Green-Leaf-42") as SignInResult.LockedOut
        assertTrue(capped.seconds <= 300)
    }

    @Test
    fun signingIn_withNoAdministrator_doesNothing() = runBlocking {
        val auth = launch()
        assertEquals(SignInResult.Unavailable, auth.signIn("roy.babu", "Green-Leaf-42"))
        assertEquals(AuthState.NeedsSetup, auth.state.value)
    }

    @Test
    fun theHasherIsSaltedAndOnlyMatchesTheRightPassword() {
        val salt = hasher.newSalt()
        val a = hasher.hash("Green-Leaf-42", salt)
        assertTrue(a.contentEquals(hasher.hash("Green-Leaf-42", salt)))
        assertFalse(a.contentEquals(hasher.hash("Green-Leaf-43", salt)))
        assertFalse(a.contentEquals(hasher.hash("Green-Leaf-42", hasher.newSalt())))
        assertEquals(32, a.size)
        assertNotEquals(salt.toList(), hasher.newSalt().toList())
        val account = UserAccount(1L, "N", "n.n", Role.SUPER_ADMIN, true, salt, a, 1_000, 0L)
        assertTrue(hasher.matches("Green-Leaf-42", account))
        assertFalse(hasher.matches("green-leaf-42", account))
    }
}
