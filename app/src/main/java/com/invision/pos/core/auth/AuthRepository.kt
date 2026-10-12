package com.invision.pos.core.auth

import java.time.Clock
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the app shows first. */
sealed interface AuthState {
    /** First launch: there is no administrator yet. */
    data object NeedsSetup : AuthState

    /** An administrator exists and nobody is signed in. */
    data object SignedOut : AuthState

    data class SignedIn(val name: String) : AuthState

    /** The saved account could not be read. Setup is never offered over it. */
    data object Unreadable : AuthState
}

sealed interface SetupResult {
    data object Created : SetupResult
    data class Invalid(val errors: Map<SetupField, SetupError>) : SetupResult

    /** An administrator already exists (for example, another attempt finished first). Nothing was changed. */
    data object AlreadyInitialized : SetupResult
    data object Failed : SetupResult
}

sealed interface SignInResult {
    data object Success : SignInResult
    data object Rejected : SignInResult
    data class LockedOut(val seconds: Int) : SignInResult
    data object Unavailable : SignInResult
}

interface AuthRepository {
    val state: StateFlow<AuthState>

    /** Creates the one administrator and signs them in. Does nothing if one already exists. */
    suspend fun createAdministrator(name: String, identifier: String, password: String, confirm: String): SetupResult

    suspend fun signIn(identifier: String, password: String): SignInResult
}

class LocalAuthRepository(
    private val store: AdminAccountStore,
    private val hasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC(),
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) : AuthRepository {

    private val gate = Mutex()
    private val _state = MutableStateFlow(initialState())
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    // Failed sign-ins are counted in memory only; they slow down guessing, they are not a security boundary.
    private var failures = 0
    private var lockedUntilMillis = 0L

    private fun initialState(): AuthState = when (store.read()) {
        StoredAdmin.Missing -> AuthState.NeedsSetup
        is StoredAdmin.Present -> AuthState.SignedOut
        StoredAdmin.Unreadable -> AuthState.Unreadable
    }

    override suspend fun createAdministrator(
        name: String,
        identifier: String,
        password: String,
        confirm: String,
    ): SetupResult = gate.withLock {
        // Look at the saved account again, not at the screen state: this is the check that stops a second setup.
        when (store.read()) {
            is StoredAdmin.Present -> return@withLock alreadyInitialized()
            StoredAdmin.Unreadable -> {
                _state.value = AuthState.Unreadable
                return@withLock SetupResult.Failed
            }
            StoredAdmin.Missing -> Unit
        }
        val errors = AdminSetupValidator.validate(name, identifier, password, confirm)
        if (errors.isNotEmpty()) return@withLock SetupResult.Invalid(errors)

        val cleanName = AdminSetupValidator.normalizeName(name)
        val cleanIdentifier = AdminSetupValidator.normalizeIdentifier(identifier)
        val account = withContext(worker) {
            val salt = hasher.newSalt()
            AdminAccount(cleanName, cleanIdentifier, salt, hasher.hash(password, salt), hasher.iterations, clock.millis())
        }
        when (store.createIfAbsent(account)) {
            CreateOutcome.CREATED -> {
                _state.value = AuthState.SignedIn(cleanName)
                SetupResult.Created
            }
            CreateOutcome.ALREADY_EXISTS -> alreadyInitialized()
            CreateOutcome.FAILED -> SetupResult.Failed
        }
    }

    private fun alreadyInitialized(): SetupResult {
        if (_state.value == AuthState.NeedsSetup) _state.value = AuthState.SignedOut
        return SetupResult.AlreadyInitialized
    }

    override suspend fun signIn(identifier: String, password: String): SignInResult = gate.withLock {
        val now = clock.millis()
        if (now < lockedUntilMillis) return@withLock SignInResult.LockedOut(secondsLeft(now))

        val account = when (val stored = store.read()) {
            is StoredAdmin.Present -> stored.account
            StoredAdmin.Missing -> {
                _state.value = AuthState.NeedsSetup
                return@withLock SignInResult.Unavailable
            }
            StoredAdmin.Unreadable -> {
                _state.value = AuthState.Unreadable
                return@withLock SignInResult.Unavailable
            }
        }
        // The password is always hashed, even for a wrong username, so the two cases take the same time.
        val passwordMatches = withContext(worker) { hasher.matches(password, account) }
        val identifierMatches = AdminSetupValidator.normalizeIdentifier(identifier) == account.identifier
        if (passwordMatches && identifierMatches) {
            failures = 0
            _state.value = AuthState.SignedIn(account.name)
            return@withLock SignInResult.Success
        }
        failures++
        if (failures >= FREE_ATTEMPTS) {
            val steps = failures - FREE_ATTEMPTS
            val delay = min(BASE_LOCK_MILLIS shl min(steps, 4), MAX_LOCK_MILLIS)
            lockedUntilMillis = clock.millis() + delay
        }
        SignInResult.Rejected
    }

    private fun secondsLeft(now: Long): Int = (((lockedUntilMillis - now) + 999) / 1000).toInt().coerceAtLeast(1)

    private companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MILLIS = 30_000L
        const val MAX_LOCK_MILLIS = 300_000L
    }
}
