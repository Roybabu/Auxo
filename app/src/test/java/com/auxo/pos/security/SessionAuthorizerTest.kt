package com.auxo.pos.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.FileAccountStore
import com.auxo.pos.core.auth.LocalAuthRepository
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.PasswordHasher
import com.auxo.pos.core.auth.PermissionDeniedException
import com.auxo.pos.core.auth.SessionAuthorizer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** The authorizer follows who is signed in right now: after login, sign-out, a restart and a deactivation. */
@RunWith(AndroidJUnit4::class)
class SessionAuthorizerTest {

    private val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "authz-${UUID.randomUUID()}").apply { mkdirs() }
    private val hasher = PasswordHasher(iterations = 1_000)

    private fun launch() = LocalAuthRepository(FileAccountStore(dir), hasher, com.auxo.pos.core.auth.MutableClock(), Dispatchers.Default)

    private val ownerPw = "Green-Leaf-42"
    private val staffPw = "Counter-Desk-17"

    private fun seed() = runBlocking {
        val owner = launch()
        owner.createAdministrator("Roy Babu", "roy.babu", ownerPw, ownerPw)
        owner.createEmployee("Asha K", "asha.k", staffPw, staffPw)
    }

    @Test
    fun nobodySignedIn_nothingIsAllowed() {
        seed()
        val auth = launch()
        val authorizer = SessionAuthorizer(auth)
        assertNull(authorizer.user)
        assertTrue(Permission.entries.none { authorizer.can(it) })
        try {
            authorizer.require(Permission.SELL)
            fail("Expected a refusal")
        } catch (expected: PermissionDeniedException) {
            assertEquals(Permission.SELL, expected.permission)
        }
    }

    @Test
    fun theSuperAdmin_isAllowedEverything_afterLogin_andAfterARestart() = runBlocking {
        seed()
        repeat(2) {
            val auth = launch()
            val authorizer = SessionAuthorizer(auth)
            assertTrue(Permission.entries.none { authorizer.can(it) }) // a restart signs everyone out
            auth.signIn("roy.babu", ownerPw)
            assertTrue(Permission.entries.all { authorizer.can(it) })
            assertEquals("Roy Babu", authorizer.user?.name)
        }
    }

    @Test
    fun anEmployee_isAllowedOnlyBillingAndInvoiceReading() = runBlocking {
        seed()
        val auth = launch()
        val authorizer = SessionAuthorizer(auth)
        auth.signIn("asha.k", staffPw)
        assertEquals(setOf(Permission.SELL, Permission.VIEW_INVOICES), Permission.entries.filter { authorizer.can(it) }.toSet())
        assertFalse(authorizer.can(Permission.MANAGE_USERS))
    }

    @Test
    fun signingOut_takesEveryPermissionAway() = runBlocking {
        seed()
        val auth = launch()
        val authorizer = SessionAuthorizer(auth)
        auth.signIn("roy.babu", ownerPw)
        assertTrue(authorizer.can(Permission.MANAGE_USERS))
        auth.signOut()
        assertFalse(authorizer.can(Permission.SELL))
    }

    @Test
    fun aDeactivatedEmployee_loses_everything_onTheNextCheck() = runBlocking {
        seed()
        val employee = launch()
        val authorizer = SessionAuthorizer(employee)
        employee.signIn("asha.k", staffPw)
        assertTrue(authorizer.can(Permission.SELL))

        val owner = launch()
        owner.signIn("roy.babu", ownerPw)
        owner.setEmployeeActive(2L, false)

        employee.revalidate() // run at app start and every time the app returns to the screen
        assertFalse(authorizer.can(Permission.SELL))
    }
}
