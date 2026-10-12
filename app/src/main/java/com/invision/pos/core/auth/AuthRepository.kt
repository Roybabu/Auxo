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
    data class SignedIn(
        val name: String,
        val role: Role = Role.SUPER_ADMIN,
        val userId: Long = UserAccountIds.FIRST,
        /** Everything this person may do, read from the saved account at sign-in and again on every app start. */
        val permissions: Set<Permission> = RolePermissions.of(role),
    ) : AuthState {
        val user: SessionUser get() = SessionUser(userId, name, role, permissions)
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

/** Whether the signed-in Super Admin is being asked about a recovery code. */
sealed interface RecoveryPrompt {
    data object None : RecoveryPrompt

    /** Right after first-launch setup: the person must write the code down and confirm it before going on. */
    data object Required : RecoveryPrompt

    /** An install without a code, after sign-in: the person may create one now or leave it for later. */
    data object Offered : RecoveryPrompt
}

sealed interface RecoveryStartResult {
    /** A new code, written in groups for reading. It is only saved once [AuthRepository.confirmRecoveryCode] succeeds. */
    data class Ready(val code: String) : RecoveryStartResult
    data object WrongPassword : RecoveryStartResult
    data class LockedOut(val seconds: Int) : RecoveryStartResult

    /** The signed-in person is not an active Super Admin. */
    data object Denied : RecoveryStartResult
}

sealed interface RecoveryConfirmResult {
    data object Saved : RecoveryConfirmResult

    /** What was typed is not the code that was shown. Nothing was saved. */
    data object Mismatch : RecoveryConfirmResult
    data object NothingPending : RecoveryConfirmResult
    data object Denied : RecoveryConfirmResult
    data object Failed : RecoveryConfirmResult
}

sealed interface RecoveryResetResult {
    /** The password was replaced and [newCode] now replaces the code that was used. */
    data class Reset(val newCode: String) : RecoveryResetResult
    data class Invalid(val errors: Map<SetupField, SetupError>) : RecoveryResetResult
    data object WrongCode : RecoveryResetResult
    data class LockedOut(val seconds: Int) : RecoveryResetResult

    /** The Super Admin never set up a recovery code, so there is no way back in except clearing the app's data. */
    data object NoCode : RecoveryResetResult
    data object Unavailable : RecoveryResetResult
}

/** When the saved code was made, or null if there is none. */
data class RecoveryStatus(val createdAtMillis: Long?)

interface AuthRepository {
    val state: StateFlow<AuthState>

    val recoveryPrompt: StateFlow<RecoveryPrompt>

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

    suspend fun createEmployee(name: String, identifier: String, password: String, confirm: String, granted: Set<Permission> = emptySet()): UserChangeResult

    suspend fun setEmployeeActive(userId: Long, active: Boolean): UserChangeResult

    /**
     * Replaces the extras switched on for one Employee. Options that cannot be granted are dropped and the
     * ones an option needs are added, so the saved set always works. It applies the next time that person
     * signs in or the app starts.
     */
    suspend fun setEmployeeAccess(userId: Long, granted: Set<Permission>): UserChangeResult

    /** Sets a new password chosen by the Super Admin. The employee is not told anything by the app. */
    suspend fun resetEmployeePassword(userId: Long, password: String, confirm: String): UserChangeResult

    // Recovery code (Super Admin only)

    /** Null unless the signed-in person is an active Super Admin. */
    suspend fun recoveryStatus(): RecoveryStatus?

    /**
     * Makes a new code and holds it in memory. Right after first-launch setup no password is needed; at any
     * other time the Super Admin must type their password again, so an unattended signed-in screen cannot be
     * used to plant a code. Nothing is saved, and any old code keeps working, until the code is confirmed.
     */
    suspend fun startRecoveryCode(password: String?): RecoveryStartResult

    /** Saves the code from [startRecoveryCode] if [typed] is that code. This replaces any earlier code. */
    suspend fun confirmRecoveryCode(typed: String): RecoveryConfirmResult

    /** Leaves the offer to create a code for later. Does nothing when the code is required. */
    fun dismissRecoveryOffer()

    /**
     * For a Super Admin who is signed out and has forgotten the password: replaces it, using the recovery code
     * as proof, and issues a new code in the same step (the old one stops working). Wrong codes are slowed
     * down the same way wrong passwords are.
     */
    suspend fun resetPasswordWithRecoveryCode(code: String, password: String, confirm: String): RecoveryResetResult
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

    private val _recoveryPrompt = MutableStateFlow<RecoveryPrompt>(RecoveryPrompt.None)
    override val recoveryPrompt: StateFlow<RecoveryPrompt> = _recoveryPrompt.asStateFlow()

    // A code that has been shown but not yet confirmed. Kept in memory only, so closing the app discards it.
    private var pendingCode: String? = null

    // Wrong recovery codes are counted apart from wrong passwords.
    private var codeFailures = 0
    private var codeLockedUntilMillis = 0L

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
                _state.value = AuthState.SignedIn(account.name, account.role, account.id, account.permissions)
                pendingCode = null
                _recoveryPrompt.value = RecoveryPrompt.Required
                SetupResult.Created
            }
            CreateOutcome.ALREADY_EXISTS -> alreadyInitialized()
            CreateOutcome.FAILED -> SetupResult.Failed
        }
    }

    private suspend fun newAccount(id: Long, role: Role, name: String, identifier: String, password: String, granted: Set<Permission> = emptySet()): UserAccount =
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
                granted = granted,
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
            _state.value = AuthState.SignedIn(account.name, account.role, account.id, account.permissions)
            pendingCode = null
            _recoveryPrompt.value = if (account.role == Role.SUPER_ADMIN && account.recovery == null) RecoveryPrompt.Offered else RecoveryPrompt.None
            tell(AccountEvent(AccountEventKind.SIGNED_IN, account.toSession()))
            return@withLock SignInResult.Success
        }
        failures++
        lockedUntilMillis = lockAfter(failures, lockedUntilMillis)
        SignInResult.Rejected
    }

    /** The time until which attempts are refused after [count] failures in a row; [current] if still under the free allowance. */
    private fun lockAfter(count: Int, current: Long): Long {
        if (count < FREE_ATTEMPTS) return current
        val delay = min(BASE_LOCK_MILLIS shl min(count - FREE_ATTEMPTS, 4), MAX_LOCK_MILLIS)
        return clock.millis() + delay
    }

    override fun signOut() {
        val current = _state.value as? AuthState.SignedIn ?: return
        _state.value = AuthState.SignedOut
        forgetRecoveryPrompt()
        tell(AccountEvent(AccountEventKind.SIGNED_OUT, current.user))
    }

    private fun forgetRecoveryPrompt() {
        pendingCode = null
        _recoveryPrompt.value = RecoveryPrompt.None
    }

    private fun tell(event: AccountEvent) {
        runCatching { listener?.onEvent(event) }
    }

    override fun revalidate() {
        val current = _state.value as? AuthState.SignedIn ?: return
        when (val stored = store.read()) {
            is StoredAccounts.Present -> {
                val account = stored.accounts.firstOrNull { it.id == current.userId }
                if (account == null || !account.active || account.role != current.role) {
                    _state.value = AuthState.SignedOut
                    forgetRecoveryPrompt()
                } else if (account.permissions != current.permissions) {
                    // What this person may do changed while they were signed in: apply it now, not at the next sign-in.
                    _state.value = current.copy(permissions = account.permissions)
                }
            }
            StoredAccounts.Missing, StoredAccounts.Unreadable -> {
                _state.value = AuthState.SignedOut
                forgetRecoveryPrompt()
            }
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

    override suspend fun createEmployee(name: String, identifier: String, password: String, confirm: String, granted: Set<Permission>): UserChangeResult =
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
            val employee = newAccount(id, Role.EMPLOYEE, name, identifier, password, PermissionOptions.normalize(granted))
            val outcome = store.update { accounts ->
                // Checked again inside the write, in case another change slipped in.
                if (accounts.any { it.identifier == employee.identifier || it.id == employee.id }) null else accounts + employee
            }
            if (outcome == UpdateOutcome.UPDATED) {
                tell(AccountEvent(AccountEventKind.EMPLOYEE_CREATED, admin.toSession(), employee.id, employee.name))
                if (employee.granted.isNotEmpty()) {
                    tell(AccountEvent(AccountEventKind.ACCESS_CHANGED, admin.toSession(), employee.id, employee.name, employee.granted))
                }
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

    override suspend fun setEmployeeAccess(userId: Long, granted: Set<Permission>): UserChangeResult = gate.withLock {
        val admin = activeSuperAdmin() ?: return@withLock UserChangeResult.Denied
        val wanted = PermissionOptions.normalize(granted)
        val result = changeEmployee(userId) { it.copy(granted = wanted) }
        if (result is Changed) tell(AccountEvent(AccountEventKind.ACCESS_CHANGED, admin.toSession(), userId, result.name, wanted))
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


    // Recovery code

    override suspend fun recoveryStatus(): RecoveryStatus? = gate.withLock {
        val admin = activeSuperAdmin() ?: return@withLock null
        RecoveryStatus(admin.recovery?.createdAtMillis)
    }

    override suspend fun startRecoveryCode(password: String?): RecoveryStartResult = gate.withLock {
        val admin = activeSuperAdmin() ?: return@withLock RecoveryStartResult.Denied
        // Straight after setup the person has only just typed the password; at any other time it is asked for again.
        if (_recoveryPrompt.value != RecoveryPrompt.Required) {
            val now = clock.millis()
            if (now < lockedUntilMillis) return@withLock RecoveryStartResult.LockedOut(secondsLeft(now))
            val right = password != null && withContext(worker) { hasher.matches(password, admin) }
            if (!right) {
                failures++
                lockedUntilMillis = lockAfter(failures, lockedUntilMillis)
                return@withLock RecoveryStartResult.WrongPassword
            }
            failures = 0
        }
        val code = RecoveryCodes.generate()
        pendingCode = code
        RecoveryStartResult.Ready(RecoveryCodes.format(code))
    }

    override suspend fun confirmRecoveryCode(typed: String): RecoveryConfirmResult = gate.withLock {
        val admin = activeSuperAdmin() ?: return@withLock RecoveryConfirmResult.Denied
        val code = pendingCode ?: return@withLock RecoveryConfirmResult.NothingPending
        if (!RecoveryCodes.same(code, typed)) return@withLock RecoveryConfirmResult.Mismatch
        val record = withContext(worker) { RecoveryRecord.create(code, hasher, clock.millis()) }
        val outcome = store.update { accounts ->
            accounts.map { if (it.id == admin.id && it.role == Role.SUPER_ADMIN) it.copy(recovery = record) else it }
        }
        if (outcome != UpdateOutcome.UPDATED) return@withLock RecoveryConfirmResult.Failed
        pendingCode = null
        _recoveryPrompt.value = RecoveryPrompt.None
        tell(AccountEvent(AccountEventKind.RECOVERY_CODE_SET, admin.toSession()))
        RecoveryConfirmResult.Saved
    }

    override fun dismissRecoveryOffer() {
        if (_recoveryPrompt.value == RecoveryPrompt.Offered) forgetRecoveryPrompt()
    }

    override suspend fun resetPasswordWithRecoveryCode(code: String, password: String, confirm: String): RecoveryResetResult = gate.withLock {
        if (_state.value != AuthState.SignedOut) return@withLock RecoveryResetResult.Unavailable
        val now = clock.millis()
        if (now < codeLockedUntilMillis) return@withLock RecoveryResetResult.LockedOut(secondsLeft(now, codeLockedUntilMillis))
        val admin = when (val stored = store.read()) {
            is StoredAccounts.Present -> stored.superAdmin ?: return@withLock RecoveryResetResult.Unavailable
            else -> return@withLock RecoveryResetResult.Unavailable
        }
        val proof = admin.recovery ?: return@withLock RecoveryResetResult.NoCode
        // A password that would be refused is reported first, without using up an attempt at the code.
        val errors = AdminSetupValidator.validate(admin.name, admin.identifier, password, confirm)
            .filterKeys { it == SetupField.PASSWORD || it == SetupField.CONFIRM }
        if (errors.isNotEmpty()) return@withLock RecoveryResetResult.Invalid(errors)

        if (!withContext(worker) { proof.matches(code, hasher) }) {
            codeFailures++
            codeLockedUntilMillis = lockAfter(codeFailures, codeLockedUntilMillis)
            return@withLock RecoveryResetResult.WrongCode
        }
        codeFailures = 0
        val newCode = RecoveryCodes.generate()
        val (salt, hash, record) = withContext(worker) {
            val salt = hasher.newSalt()
            Triple(salt, hasher.hash(password, salt), RecoveryRecord.create(newCode, hasher, clock.millis()))
        }
        // The new password and the new code are saved together, so the old code can never outlive the reset.
        val outcome = store.update { accounts ->
            accounts.map {
                if (it.id == admin.id && it.role == Role.SUPER_ADMIN) it.copy(salt = salt, hash = hash, iterations = hasher.iterations, recovery = record) else it
            }
        }
        if (outcome != UpdateOutcome.UPDATED) return@withLock RecoveryResetResult.Unavailable
        // The owner has proved who they are, so the wait after wrong passwords no longer applies.
        failures = 0
        lockedUntilMillis = 0L
        tell(AccountEvent(AccountEventKind.PASSWORD_RESET_WITH_CODE, admin.toSession()))
        RecoveryResetResult.Reset(RecoveryCodes.format(newCode))
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

    private fun secondsLeft(now: Long, until: Long = lockedUntilMillis): Int = (((until - now) + 999) / 1000).toInt().coerceAtLeast(1)

    private companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MILLIS = 30_000L
        const val MAX_LOCK_MILLIS = 300_000L
    }
}
