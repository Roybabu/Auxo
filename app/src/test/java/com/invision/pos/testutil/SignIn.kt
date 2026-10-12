package com.invision.pos.testutil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.invision.pos.InvisionApplication
import com.invision.pos.core.auth.AuthRepository
import com.invision.pos.core.auth.SignInResult
import com.invision.pos.core.auth.UserChangeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

/** Sign-in helpers for tests that run the real app (real container, real Room database). */
object SignIn {
    private const val OWNER_PW = "Green-Leaf-42"
    private const val STAFF_PW = "Counter-Desk-17"
    const val OWNER = "test.owner"
    const val STAFF = "test.staff"

    val auth: AuthRepository
        get() = (ApplicationProvider.getApplicationContext<Context>() as InvisionApplication).container.authRepository

    /** First launch: creates the Super Admin, who is then signed in. */
    fun asSuperAdmin() = runBlocking {
        auth.createAdministrator("Test Owner", OWNER, OWNER_PW, OWNER_PW)
        Unit
    }

    /** Creates an Employee account (as the Super Admin) and leaves that employee signed in. */
    fun asEmployee() = runBlocking {
        asSuperAdmin()
        assertEquals(UserChangeResult.Done, auth.createEmployee("Test Staff", STAFF, STAFF_PW, STAFF_PW))
        auth.signOut()
        assertEquals(SignInResult.Success, auth.signIn(STAFF, STAFF_PW))
    }

    /** Signs the Super Admin back in after an employee used the app. */
    fun backToSuperAdmin() = runBlocking {
        auth.signOut()
        assertEquals(SignInResult.Success, auth.signIn(OWNER, OWNER_PW))
    }
}
