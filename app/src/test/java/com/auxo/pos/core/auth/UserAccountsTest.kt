// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Roles, employee accounts and User Management, against real files on a throw-away folder. */
@RunWith(AndroidJUnit4::class)
class UserAccountsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val clock = MutableClock()
    private val hasher = PasswordHasher(iterations = 1_000)

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "users-${UUID.randomUUID()}").apply { mkdirs() }
    }

    /** A new repository over the same folder is what the next app launch sees. */
    private fun launch() = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default)

    private suspend fun LocalAuthRepository.setUpOwner() =
        createAdministrator("Sample User", "sample.user", OWNER_PW, OWNER_PW)

    private suspend fun LocalAuthRepository.addEmployee(identifier: String = "asha.k", pw: String = EMPLOYEE_PW) =
        createEmployee("Asha K", identifier, pw, pw)

    private suspend fun ownerWithEmployee(): LocalAuthRepository {
        val auth = launch()
        auth.setUpOwner()
        assertEquals(UserChangeResult.Done, auth.addEmployee())
        return auth
    }

    // Super Admin

    @Test
    fun theFirstAccount_isTheSuperAdmin_withId1() = runBlocking {
        val auth = launch()
        auth.setUpOwner()
        assertEquals(AuthState.SignedIn("Sample User", Role.SUPER_ADMIN, 1L), auth.state.value)
        val saved = (FileAccountStore(dir).read() as StoredAccounts.Present).accounts.single()
        assertEquals(Role.SUPER_ADMIN, saved.role)
        assertTrue(saved.active)
    }

    @Test
    fun setupWorksOnlyOnce_evenWhenEmployeesExist() = runBlocking {
        ownerWithEmployee()
        val again = launch().createAdministrator("Other", "other.user", "Other-Pass-99", "Other-Pass-99")
        assertEquals(SetupResult.AlreadyInitialized, again)
        assertEquals(2, (FileAccountStore(dir).read() as StoredAccounts.Present).accounts.size)
    }

    // Upgrading an install that only ever had one administrator

    @Test
    fun anOlderSingleAdministratorFile_becomesTheSuperAdmin_andStillSignsIn() = runBlocking {
        val salt = hasher.newSalt()
        File(dir, "admin_account.json").writeText(
            JSONObject()
                .put("version", 1)
                .put("name", "Sample User")
                .put("identifier", "sample.user")
                .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("hash", Base64.encodeToString(hasher.hash(OWNER_PW, salt), Base64.NO_WRAP))
                .put("iterations", hasher.iterations)
                .put("createdAt", 1_600_000_000_000L)
                .toString(),
        )
        val auth = launch()
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertEquals(SignInResult.Success, auth.signIn("sample.user", OWNER_PW))
        assertEquals(AuthState.SignedIn("Sample User", Role.SUPER_ADMIN, 1L), auth.state.value)
        assertTrue(File(dir, "accounts.json").isFile)
        assertFalse(File(dir, "admin_account.json").exists())
        // And setup is still refused.
        assertEquals(SetupResult.AlreadyInitialized, launch().createAdministrator("X", "x.user", OWNER_PW, OWNER_PW))
    }

    // Employees

    @Test
    fun theSuperAdmin_createsAnEmployee_whoCanSignIn_withTheEmployeeRole() = runBlocking {
        ownerWithEmployee()
        val auth = launch()
        assertEquals(SignInResult.Success, auth.signIn("Asha.K", EMPLOYEE_PW))
        assertEquals(AuthState.SignedIn("Asha K", Role.EMPLOYEE, 2L), auth.state.value)
    }

    @Test
    fun employeePasswordsAreSaltedHashes_neverPlainText() = runBlocking {
        ownerWithEmployee()
        val text = File(dir, "accounts.json").readText()
        assertFalse(text.contains(EMPLOYEE_PW))
        assertFalse(text.contains(OWNER_PW))
        val accounts = (FileAccountStore(dir).read() as StoredAccounts.Present).accounts
        assertFalse(accounts[0].salt.contentEquals(accounts[1].salt))
    }

    @Test
    fun newEmployeeDetails_areValidated_andUsernamesAreUnique() = runBlocking {
        val auth = launch()
        auth.setUpOwner()
        val weak = auth.createEmployee("", "ab", "short", "different") as UserChangeResult.Invalid
        assertEquals(SetupError.NAME_REQUIRED, weak.errors[SetupField.NAME])
        assertEquals(SetupError.IDENTIFIER_INVALID, weak.errors[SetupField.IDENTIFIER])
        assertEquals(SetupError.PASSWORD_TOO_SHORT, weak.errors[SetupField.PASSWORD])
        assertEquals(1, auth.listUsers()!!.size)

        assertEquals(UserChangeResult.Done, auth.addEmployee("asha.k"))
        val clash = auth.addEmployee("ASHA.K") as UserChangeResult.Invalid
        assertEquals(SetupError.IDENTIFIER_TAKEN, clash.errors[SetupField.IDENTIFIER])
        val clashWithOwner = auth.addEmployee("Sample.User") as UserChangeResult.Invalid
        assertEquals(SetupError.IDENTIFIER_TAKEN, clashWithOwner.errors[SetupField.IDENTIFIER])
        assertEquals(2, auth.listUsers()!!.size)
    }

    @Test
    fun employees_cannotUseUserManagement_atAll() = runBlocking {
        ownerWithEmployee()
        val auth = launch()
        auth.signIn("asha.k", EMPLOYEE_PW)

        assertNull(auth.listUsers())
        assertEquals(UserChangeResult.Denied, auth.addEmployee("new.hire"))
        assertEquals(UserChangeResult.Denied, auth.setEmployeeActive(2L, false))
        assertEquals(UserChangeResult.Denied, auth.setEmployeeActive(1L, false))
        assertEquals(UserChangeResult.Denied, auth.resetEmployeePassword(1L, "Hacked-Pass-1", "Hacked-Pass-1"))
        assertEquals(UserChangeResult.Denied, auth.resetEmployeePassword(2L, "Hacked-Pass-1", "Hacked-Pass-1"))

        // Nothing on disk changed: still two accounts, owner untouched, employee still active with the old password.
        val fresh = launch()
        assertEquals(SignInResult.Success, fresh.signIn("sample.user", OWNER_PW))
        assertEquals(2, fresh.listUsers()!!.size)
        fresh.signOut()
        assertEquals(SignInResult.Success, fresh.signIn("asha.k", EMPLOYEE_PW))
    }

    @Test
    fun nobodyIsSignedIn_userManagementIsDenied() = runBlocking {
        ownerWithEmployee()
        val auth = launch()
        assertNull(auth.listUsers())
        assertEquals(UserChangeResult.Denied, auth.addEmployee("new.hire"))
    }

    // Deactivation

    @Test
    fun aDeactivatedEmployee_cannotSignIn_untilTurnedBackOn() = runBlocking {
        val owner = ownerWithEmployee()
        assertEquals(UserChangeResult.Done, owner.setEmployeeActive(2L, false))

        val next = launch()
        assertEquals(SignInResult.Deactivated, next.signIn("asha.k", EMPLOYEE_PW))
        assertEquals(AuthState.SignedOut, next.state.value)
        // A wrong password still looks like any wrong password, so a stranger learns nothing about the account.
        assertEquals(SignInResult.Rejected, next.signIn("asha.k", "Wrong-Pass-1"))

        assertEquals(SignInResult.Success, next.signIn("sample.user", OWNER_PW))
        assertEquals(UserChangeResult.Done, next.setEmployeeActive(2L, true))
        next.signOut()
        assertEquals(SignInResult.Success, next.signIn("asha.k", EMPLOYEE_PW))
    }

    @Test
    fun roleAndStatus_persistAcrossRestarts() = runBlocking {
        val owner = ownerWithEmployee()
        owner.setEmployeeActive(2L, false)
        repeat(2) {
            val restarted = FileAccountStore(dir).read() as StoredAccounts.Present
            val employee = restarted.accounts.single { it.id == 2L }
            assertEquals(Role.EMPLOYEE, employee.role)
            assertFalse(employee.active)
            assertEquals(Role.SUPER_ADMIN, restarted.superAdmin!!.role)
        }
        assertEquals(AuthState.SignedOut, launch().state.value)
    }

    @Test
    fun revalidate_signsOutAnEmployeeWhoseAccountWasSwitchedOff() = runBlocking {
        ownerWithEmployee()
        val employeeSession = launch()
        employeeSession.signIn("asha.k", EMPLOYEE_PW)
        // The Super Admin switches the account off from another session of the same files.
        val owner = launch()
        owner.signIn("sample.user", OWNER_PW)
        owner.setEmployeeActive(2L, false)

        employeeSession.revalidate()
        assertEquals(AuthState.SignedOut, employeeSession.state.value)
    }

    @Test
    fun revalidate_keepsAnActiveSessionSignedIn() = runBlocking {
        val owner = ownerWithEmployee()
        owner.revalidate()
        assertTrue(owner.state.value is AuthState.SignedIn)
    }

    // The Super Admin account is out of reach

    @Test
    fun theSuperAdmin_cannotBeDeactivatedOrReset_throughUserManagement() = runBlocking {
        val owner = ownerWithEmployee()
        assertEquals(UserChangeResult.NotAnEmployee, owner.setEmployeeActive(1L, false))
        assertEquals(UserChangeResult.NotAnEmployee, owner.resetEmployeePassword(1L, "Hacked-Pass-1", "Hacked-Pass-1"))
        assertEquals(UserChangeResult.NotAnEmployee, owner.setEmployeeActive(99L, false))
        val fresh = launch()
        assertEquals(SignInResult.Success, fresh.signIn("sample.user", OWNER_PW))
        assertEquals(SignInResult.Rejected, fresh.signIn("sample.user", "Hacked-Pass-1"))
    }

    @Test
    fun noEmployeeCanBeGivenTheSuperAdminRole_throughTheApi() = runBlocking {
        ownerWithEmployee()
        // There is no function that changes a role; the only way to get a second Super Admin is to edit the file,
        // and a file with two of them is refused outright rather than trusted.
        val accounts = File(dir, "accounts.json")
        accounts.writeText(accounts.readText().replaceFirst("\"EMPLOYEE\"", "\"SUPER_ADMIN\""))
        assertEquals(StoredAccounts.Unreadable, FileAccountStore(dir).read())
        assertEquals(AuthState.Unreadable, launch().state.value)
        assertEquals(SetupResult.Failed, launch().createAdministrator("X", "x.user", OWNER_PW, OWNER_PW))
    }

    // Credential reset

    @Test
    fun resettingACredential_replacesThePassword_andKeepsTheRoleAndStatus() = runBlocking {
        val owner = ownerWithEmployee()
        assertEquals(UserChangeResult.Done, owner.resetEmployeePassword(2L, "Fresh-Start-77", "Fresh-Start-77"))

        val next = launch()
        assertEquals(SignInResult.Rejected, next.signIn("asha.k", EMPLOYEE_PW))
        assertEquals(SignInResult.Success, next.signIn("asha.k", "Fresh-Start-77"))
        assertEquals(Role.EMPLOYEE, (next.state.value as AuthState.SignedIn).role)
    }

    @Test
    fun aResetIsValidated_likeAnyNewPassword() = runBlocking {
        val owner = ownerWithEmployee()
        val result = owner.resetEmployeePassword(2L, "short", "short") as UserChangeResult.Invalid
        assertEquals(SetupError.PASSWORD_TOO_SHORT, result.errors[SetupField.PASSWORD])
        val mismatch = owner.resetEmployeePassword(2L, "Fresh-Start-77", "Fresh-Start-78") as UserChangeResult.Invalid
        assertEquals(SetupError.CONFIRM_MISMATCH, mismatch.errors[SetupField.CONFIRM])
        assertEquals(SignInResult.Success, launch().signIn("asha.k", EMPLOYEE_PW))
    }

    @Test
    fun signingOut_returnsToLogin() = runBlocking {
        val auth = launch()
        auth.setUpOwner()
        auth.signOut()
        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    // Account events (they feed the activity log)

    @Test
    fun accountChanges_areReported_withTheActor_andNeverCarryPasswords() = runBlocking {
        val events = mutableListOf<AccountEvent>()
        val auth = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default, AccountEventListener { events += it })
        auth.setUpOwner()
        auth.addEmployee()
        auth.setEmployeeActive(2L, false)
        auth.setEmployeeActive(2L, true)
        auth.resetEmployeePassword(2L, "Fresh-Start-77", "Fresh-Start-77")
        auth.signOut()
        auth.signIn("asha.k", "Fresh-Start-77")

        assertEquals(
            listOf(
                AccountEventKind.EMPLOYEE_CREATED,
                AccountEventKind.EMPLOYEE_TURNED_OFF,
                AccountEventKind.EMPLOYEE_TURNED_ON,
                AccountEventKind.PASSWORD_RESET,
                AccountEventKind.SIGNED_OUT,
                AccountEventKind.SIGNED_IN,
            ),
            events.map { it.kind },
        )
        assertTrue(events.take(5).all { it.actor.id == 1L && it.actor.role == Role.SUPER_ADMIN })
        assertEquals(2L, events.last().actor.id)
        assertEquals(listOf(2L, 2L, 2L, 2L), events.take(4).map { it.targetId })
        val text = events.joinToString { it.toString() }
        assertFalse(text.contains(EMPLOYEE_PW) || text.contains("Fresh-Start-77") || text.contains(OWNER_PW))
    }

    @Test
    fun aFailingListener_doesNotBreakSigningIn() = runBlocking {
        ownerWithEmployee()
        val auth = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default, AccountEventListener { error("log is broken") })
        assertEquals(SignInResult.Success, auth.signIn("sample.user", OWNER_PW))
        auth.signOut()
        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun deniedAndInvalidChanges_reportNothing() = runBlocking {
        ownerWithEmployee()
        val events = mutableListOf<AccountEvent>()
        val auth = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default, AccountEventListener { events += it })
        auth.setEmployeeActive(2L, false) // nobody signed in
        auth.signIn("asha.k", EMPLOYEE_PW)
        events.clear()
        auth.setEmployeeActive(2L, false) // an employee
        auth.addEmployee("another.one")
        assertTrue(events.isEmpty())
    }


    // Per-employee access

    private val menuAndReturns = setOf(Permission.VIEW_ACTIVITY_LOG, Permission.PROCESS_RETURN)

    @Test
    fun anEmployee_createdWithExtras_getsThem_andKeepsThemAfterARestart() = runBlocking {
        val owner = launch()
        owner.setUpOwner()
        assertEquals(UserChangeResult.Done, owner.createEmployee("Asha K", "asha.k", EMPLOYEE_PW, EMPLOYEE_PW, menuAndReturns))

        val restarted = launch()
        assertEquals(SignInResult.Success, restarted.signIn("asha.k", EMPLOYEE_PW))
        val signedIn = restarted.state.value as AuthState.SignedIn
        assertEquals(RolePermissions.BASE + menuAndReturns, signedIn.permissions)
        val listed = (launch().also { it.signIn("sample.user", OWNER_PW) }.listUsers())!!.single { it.id == 2L }
        assertEquals(menuAndReturns, listed.granted)
    }

    @Test
    fun anEmployee_createdWithoutExtras_hasOnlyTheBasics() = runBlocking {
        val owner = ownerWithEmployee()
        assertEquals(emptySet<Permission>(), owner.listUsers()!!.single { it.id == 2L }.granted)
        assertEquals(SignInResult.Success, launch().signIn("asha.k", EMPLOYEE_PW))
    }

    @Test
    fun theSuperAdmin_canChangeAnEmployeesAccess_andItAppliesAtTheNextSignIn() = runBlocking {
        val owner = ownerWithEmployee()
        assertEquals(UserChangeResult.Done, owner.setEmployeeAccess(2L, setOf(Permission.MANAGE_PRODUCTS)))
        val employee = launch()
        employee.signIn("asha.k", EMPLOYEE_PW)
        // Products come with the stock history they show.
        assertEquals(
            RolePermissions.BASE + setOf(Permission.MANAGE_PRODUCTS, Permission.VIEW_STOCK_RECORDS),
            (employee.state.value as AuthState.SignedIn).permissions,
        )

        assertEquals(UserChangeResult.Done, owner.setEmployeeAccess(2L, emptySet()))
        val again = launch()
        again.signIn("asha.k", EMPLOYEE_PW)
        assertEquals(RolePermissions.BASE, (again.state.value as AuthState.SignedIn).permissions)
    }

    @Test
    fun changedAccess_reachesAnEmployeeWhoIsAlreadySignedIn_whenTheAppStartsAgain() = runBlocking {
        val owner = ownerWithEmployee()
        val employee = launch()
        employee.signIn("asha.k", EMPLOYEE_PW)
        owner.setEmployeeAccess(2L, setOf(Permission.VIEW_ACTIVITY_LOG))
        employee.revalidate()
        assertTrue(Permission.VIEW_ACTIVITY_LOG in (employee.state.value as AuthState.SignedIn).permissions)

        owner.setEmployeeAccess(2L, emptySet())
        employee.revalidate()
        assertFalse(Permission.VIEW_ACTIVITY_LOG in (employee.state.value as AuthState.SignedIn).permissions)
        assertTrue(employee.state.value is AuthState.SignedIn)
    }

    @Test
    fun nobodyCanBeGivenUserManagementOrBackup() = runBlocking {
        val owner = ownerWithEmployee()
        owner.setEmployeeAccess(2L, setOf(Permission.MANAGE_USERS, Permission.BACKUP_AND_RESTORE, Permission.VIEW_REPORTS))
        val saved = owner.listUsers()!!.single { it.id == 2L }.granted
        assertEquals(setOf(Permission.VIEW_REPORTS, Permission.VIEW_FINANCIALS), saved)
        val employee = launch()
        employee.signIn("asha.k", EMPLOYEE_PW)
        assertEquals(UserChangeResult.Denied, employee.createEmployee("X", "x.y", EMPLOYEE_PW, EMPLOYEE_PW))
        assertEquals(UserChangeResult.Denied, employee.setEmployeeAccess(2L, PermissionOptions.all.toSet()))
    }

    @Test
    fun anEmployee_cannotChangeAnyonesAccess_notEvenTheirOwn() = runBlocking {
        ownerWithEmployee()
        val employee = launch()
        employee.signIn("asha.k", EMPLOYEE_PW)
        assertEquals(UserChangeResult.Denied, employee.setEmployeeAccess(2L, setOf(Permission.VIEW_ACTIVITY_LOG)))
        assertEquals(RolePermissions.BASE, (employee.state.value as AuthState.SignedIn).permissions)
    }

    @Test
    fun theSuperAdminsAccess_cannotBeChanged() = runBlocking {
        val owner = ownerWithEmployee()
        assertEquals(UserChangeResult.NotAnEmployee, owner.setEmployeeAccess(1L, emptySet()))
        assertEquals(Permission.entries.toSet(), (owner.state.value as AuthState.SignedIn).permissions)
        assertEquals(UserChangeResult.NotAnEmployee, owner.setEmployeeAccess(99L, emptySet()))
    }

    @Test
    fun aRegistryEditedToGrantMore_isCutBackToWhatTheAppOffers() = runBlocking {
        ownerWithEmployee()
        val file = File(dir, FileAccountStore.FILE_NAME)
        val json = JSONObject(file.readText())
        val employee = json.getJSONArray("accounts").getJSONObject(1)
        employee.put("granted", org.json.JSONArray(listOf("MANAGE_USERS", "BACKUP_AND_RESTORE", "NOT_A_PERMISSION", "RECEIVE_GOODS")))
        // Even the Super Admin's entry cannot widen anything: it already holds everything.
        file.writeText(json.toString())

        val saved = (FileAccountStore(dir).read() as StoredAccounts.Present).accounts.single { it.id == 2L }
        assertEquals(setOf(Permission.RECEIVE_GOODS, Permission.MANAGE_PURCHASES, Permission.MANAGE_SUPPLIERS), saved.granted)
        assertFalse(Permission.MANAGE_USERS in saved.permissions)
        assertFalse(Permission.BACKUP_AND_RESTORE in saved.permissions)
    }

    @Test
    fun aRegistryWrittenBeforeExtrasExisted_givesEmployeesOnlyTheBasics() = runBlocking {
        ownerWithEmployee()
        val file = File(dir, FileAccountStore.FILE_NAME)
        val json = JSONObject(file.readText())
        val accounts = json.getJSONArray("accounts")
        for (i in 0 until accounts.length()) accounts.getJSONObject(i).remove("granted")
        file.writeText(json.toString())

        val employee = launch()
        assertEquals(SignInResult.Success, employee.signIn("asha.k", EMPLOYEE_PW))
        assertEquals(RolePermissions.BASE, (employee.state.value as AuthState.SignedIn).permissions)
        assertEquals(SignInResult.Success, launch().signIn("sample.user", OWNER_PW))
    }

    @Test
    fun accessChanges_areReported_withWhatWasGranted() = runBlocking {
        val events = mutableListOf<AccountEvent>()
        val auth = LocalAuthRepository(FileAccountStore(dir), hasher, clock, Dispatchers.Default, AccountEventListener { events += it })
        auth.setUpOwner()
        auth.createEmployee("Asha K", "asha.k", EMPLOYEE_PW, EMPLOYEE_PW, setOf(Permission.VIEW_ACTIVITY_LOG))
        auth.setEmployeeAccess(2L, emptySet())
        assertEquals(
            listOf(AccountEventKind.EMPLOYEE_CREATED, AccountEventKind.ACCESS_CHANGED, AccountEventKind.ACCESS_CHANGED),
            events.map { it.kind },
        )
        assertEquals(setOf(Permission.VIEW_ACTIVITY_LOG), events[1].granted)
        assertEquals(emptySet<Permission>(), events[2].granted)
        assertEquals("Asha K", events[2].targetName)
    }

    private companion object {
        const val OWNER_PW = "Green-Leaf-42"
        const val EMPLOYEE_PW = "Counter-Desk-17"
    }
}
