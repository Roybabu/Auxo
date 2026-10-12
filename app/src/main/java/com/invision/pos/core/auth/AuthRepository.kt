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
    /** First launch: there is no Super Admin yet. */
    data object NeedsSetup : AuthState

    /** Accounts exist and nobody is signed in. */
    data object SignedOut : AuthState

    /** [userId] and [role] come from the saved account at the moment of sign-in; the first account is always id 1. */
    data class SignedIn(val name: String, val role: Role = Role.SUPER_ADMIN, val userId: Long = UserAccountIds.FIRST) : AuthState {
        val user: SessionUser get() = SessionUser(userId, name, role)
    }

    /** The saved accounts could not be read. Setup is never offered over them. */
    data object Unreadable : AuthState
}

object UserAccountIds {
    const val FIRST = 1L
}

sealed interface SetupResult {
    data object Created : SetupResult
    data class Invalid(val errors: Map<SetupField, SetupError>) : SetupResult

    /** A Super Admin already exists (for example, another attempt finished first). Nothing was changed. */
    data object AlreadyInitialized : SetupResult
    data object Failed : SetupResult
}

sealed interface SignInResult {
    data object Success : SignInResult
    data object Rejected : SignInResult

    /** The password was right but the account has been switched off by the Super Admin. */
    data object Deactivated : SignInResult
    data class LockedOut(val seconds: Int) : SignInResult
    data object Unavailable : SignInResult
}

/** The outcome of a User Management action. */
sealed interface UserChangeResult {
    data object Done : UserChangeResult
    data class Invalid(val errors: Map<SetupField, SetupError>) : UserChangeResult

    /** The signed-in person is not an active Super Admin. Nothing was changed. */
    data object Denied : UserChangeResult

    /** No such account, or it is the Super Admin, which this screen can never change. */
    data object NotAnEmployee : UserChangeResult
    data object Failed : UserChangeResult
}

interface AuthRepository {
    val state: StateFlow<AuthState>

    /** Creates the one Super Admin and signs them in. Does nothing if one already exists. */
    suspend fun createAdministrator(name: String, identifier: String, password: String, confirm: String): SetupResult

    suspend fun signIn(identifier: String, password: String): SignInResult

    fun signOut()

    /**
     * Reads the saved accounts again and signs the person out if their account is gone, switched off or no
     * longer has the role they signed in with. Called when the app starts and returns to the screen.
     */
    fun revalidate()

    // User Management. Each of these checks, against the saved accounts, that the caller is an active Super Admin.

    suspend fun listUsers(): List<ManagedUser>?

    suspend fun createEmployee(name: String, identifier: String, password: String, confirm: String): UserChangeResult

    suspend fun setEmployeeActive(userId: Long, active: Boolean): UserChangeResult

    /** Sets a new password chosen by the Super Admin. The employee is not told anything by the app. */
    suspend fun resetEmployeePassword(userId: Long, password: String, confirm: String): UserChangeResult
}

class LocalAuthRepository(
    private val store: AccountStore,
    private val hasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC(),
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val listener: AccountEventListener? = null,
) : AuthRepository {

    private val gate = Mutex()
    private val _state = MutableStateFlow(initialState())
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    // Failed sign-ins are counted in memory only; they slow down guessing, they are not a security boundary.
    private var failures = 0
    private var lockedUntilMillis = 0L

    private fun initialState(): AuthState = when (store.read()) {
        StoredAccounts.Missing -> AuthState.NeedsSetup
        is StoredAccounts.Present -> AuthState.SignedOut
        StoredAccounts.Unreadable -> AuthState.Unreadable
    }

    override suspend fun createAdministrator(
        name: String,
        identifier: String,
        password: String,
        confirm: String,
    ): SetupResult = gate.withLock {
        // Look at the saved accounts again, not at the screen state: this is the check that stops a second setup.
        when (store.read()) {
            is StoredAccounts.Present -> return@withLock alreadyInitialized()
            StoredAccounts.Unreadable -> {
                _state.value = AuthState.Unreadable
                return@withLock SetupResult.Failed
            }
            StoredAccounts.Missing -> Unit
        }
        val errors = AdminSetupValidator.validate(name, identifier, password, confirm)
        if (errors.isNotEmpty()) return@withLock SetupResult.Invalid(errors)

        val account = newAccount(UserAccountIds.FIRST, Role.SUPER_ADMIN, name, identifier, password)
        when (store.createIfAbsent(account)) {
            CreateOutcome.CREATED -> {
                _state.value = AuthState.SignedIn(account.name, account.role, account.id)
                SetupResult.Created
            }
            CreateOutcome.ALREADY_EXISTS -> alreadyInitialized()
            CreateOutcome.FAILED -> SetupResult.Failed
        }
    }

    private suspend fun newAccount(id: Long, role: Role, name: String, identifier: String, password: String): UserAccount =
        withContext(worker) {
            val salt = hasher.newSalt()
            UserAccount(
                id = id,
                name = AdminSetupValidator.normalizeName(name),
                identifier = AdminSetupValidator.normalizeIdentifier(identifier),
                role = role,
                active = true,
                salt = salt,
                hash = hasher.hash(password, salt),
                iterations = hasher.iterations,
                createdAtMillis = clock.millis(),
            )
        }

    private fun alreadyInitialized(): SetupResult {
        if (_state.value == AuthState.NeedsSetup) _state.value = AuthState.SignedOut
        return SetupResult.AlreadyInitialized
    }

    override suspend fun signIn(identifier: String, password: String): SignInResult = gate.withLock {
        val now = clock.millis()
        if (now < lockedUntilMillis) return@withLock SignInResult.LockedOut(secondsLeft(now))

        val accounts = when (val stored = store.read()) {
            is StoredAccounts.Present -> stored.accounts
            StoredAccounts.Missing -> {
                _state.value = AuthState.NeedsSetup
                return@withLock SignInResult.Unavailable
            }
            StoredAccounts.Unreadable -> {
                _state.value = AuthState.Unreadable
                return@withLock SignInResult.Unavailable
            }
        }
        val wanted = AdminSetupValidator.normalizeIdentifier(identifier)
        val account = accounts.firstOrNull { it.identifier == wanted }
        // The password is always hashed, even for an unknown username, so the two cases take the same time.
        val passwordMatches = withContext(worker) { hasher.matches(password, account ?: accounts.first()) }
        if (account != null && passwordMatches) {
            failures = 0
            if (!account.active) return@withLock SignInResult.Deactivated
            _state.value = AuthState.SignedIn(account.name, account.role, account.id)
            tell(AccountEvent(AccountEventKind.SIGNED_IN, account.toSession()))
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

    override fun signOut() {
        val current = _state.value as? AuthState.SignedIn ?: return
        _state.value = AuthState.SignedOut
        tell(AccountEvent(AccountEventKind.SIGNED_OUT, current.user))
    }

    private fun tell(event: AccountEvent) {
        runCatching { listener?.onEvent(event) }
    }

    override fun revalidate() {
        val current = _state.value as? AuthState.SignedIn ?: return
        when (val stored = store.read()) {
            is StoredAccounts.Present -> {
                val account = stored.accounts.firstOrNull { it.id == current.userId }
                if (account == null || !account.active || account.role != current.role) _state.value = AuthState.SignedOut
            }
            StoredAccounts.Missing, StoredAccounts.Unreadable -> _state.value = AuthState.SignedOut
        }
    }

    // User Management

    /** The signed-in person, but only if the saved accounts still say they are an active Super Admin. */
    private fun activeSuperAdmin(): UserAccount? {
        val current = _state.value as? AuthState.SignedIn ?: return null
        if (current.role != Role.SUPER_ADMIN) return null
        val stored = store.read() as? StoredAccounts.Present ?: return null
        return stored.accounts.firstOrNull { it.id == current.userId && it.role == Role.SUPER_ADMIN && it.active }
    }

    override suspend fun listUsers(): List<ManagedUser>? = gate.withLock {
        activeSuperAdmin() ?: return@withLock null
        (store.read() as? StoredAccounts.Present)?.accounts?.map { it.toManaged() }
    }

    override suspend fun createEmployee(name: String, identifier: String, password: String, confirm: String): UserChangeResult =
        gate.withLock {
            val admin = activeSuperAdmin() ?: return@withLock UserChangeResult.Denied
            val errors = AdminSetupValidator.validate(name, identifier, password, confirm).toMutableMap()
            val wanted = AdminSetupValidator.normalizeIdentifier(identifier)
            val existing = (store.read() as? StoredAccounts.Present)?.accounts ?: return@withLock UserChangeResult.Failed
            if (SetupField.IDENTIFIER !in errors && existing.any { it.identifier == wanted }) {
                errors[SetupField.IDENTIFIER] = SetupError.IDENTIFIER_TAKEN
            }
            if (errors.isNotEmpty()) return@withLock UserChangeResult.Invalid(errors)

            val id = (existing.maxOf { it.id }) + 1
            val employee = newAccount(id, Role.EMPLOYEE, name, identifier, password)
            val outcome = store.update { accounts ->
                // Checked again inside the write, in case another change slipped in.
                if (accounts.any { it.identifier == employee.identifier || it.id == employee.id }) null else accounts + employee
            }
            if (outcome == UpdateOutcome.UPDATED) {
                tell(AccountEvent(AccountEventKind.EMPLOYEE_CREATED, admin.toSession(), employee.id, employee.name))
                UserChangeResult.Done
            } else {
                UserChangeResult.Failed
            }
        }

    override suspend fun setEmployeeActive(userId: Long, active: Boolean): UserChangeResult = gate.withLock {
        val admin = activeSuperAdmin() ?: return@withLock UserChangeResult.Denied
        val result = changeEmployee(userId) { it.copy(active = active) }
        if (result is Changed) {
            tell(AccountEvent(if (active) AccountEventKind.EMPLOYEE_TURNED_ON else AccountEventKind.EMPLOYEE_TURNED_OFF, admin.toSession(), userId, result.name))
        }
        result.outcome
    }

    override suspend fun resetEmployeePassword(userId: Long, password: String, confirm: String): UserChangeResult = gate.withLock {
        val admin = activeSuperAdmin() ?: return@withLock UserChangeResult.Denied
        val target = (store.read() as? StoredAccounts.Present)?.accounts?.firstOrNull { it.id == userId && it.role == Role.EMPLOYEE }
            ?: return@withLock UserChangeResult.NotAnEmployee
        val errors = AdminSetupValidator.validate(target.name, target.identifier, password, confirm)
            .filterKeys { it == SetupField.PASSWORD || it == SetupField.CONFIRM }
        if (errors.isNotEmpty()) return@withLock UserChangeResult.Invalid(errors)
        val (salt, hash) = withContext(worker) {
            val salt = hasher.newSalt()
            salt to hasher.hash(password, salt)
        }
        val result = changeEmployee(userId) { it.copy(salt = salt, hash = hash, iterations = hasher.iterations) }
        if (result is Changed) tell(AccountEvent(AccountEventKind.PASSWORD_RESET, admin.toSession(), userId, result.name))
        result.outcome
    }

    /** The result of changing one account: what to report, and (when it worked) whose account it was. */
    private open class Change(val outcome: UserChangeResult)

    private class Changed(val name: String) : Change(UserChangeResult.Done)

    /** Only ever touches an Employee account; the Super Admin cannot be changed through here. */
    private fun changeEmployee(userId: Long, change: (UserAccount) -> UserAccount): Change {
        var found: UserAccount? = null
        val outcome = store.update { accounts ->
            accounts.map { account ->
                if (account.id == userId && account.role == Role.EMPLOYEE) {
                    found = account
                    change(account)
                } else {
                    account
                }
            }.takeIf { found != null }
        }
        return when {
            outcome == UpdateOutcome.UPDATED -> Changed(found?.name.orEmpty())
            outcome == UpdateOutcome.REJECTED && found == null -> Change(UserChangeResult.NotAnEmployee)
            else -> Change(UserChangeResult.Failed)
        }
    }

    private fun secondsLeft(now: Long): Int = (((lockedUntilMillis - now) + 999) / 1000).toInt().coerceAtLeast(1)

    private companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MILLIS = 30_000L
        const val MAX_LOCK_MILLIS = 300_000L
    }
}
