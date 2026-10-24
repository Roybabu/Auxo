// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.testutil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auxo.pos.AuxoApplication
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.SignInResult
import com.auxo.pos.core.auth.UserChangeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

/** Sign-in helpers for tests that run the real app (real container, real Room database). */
object SignIn {
    private const val OWNER_PW = "Green-Leaf-42"
    private const val STAFF_PW = "Counter-Desk-17"
    const val OWNER = "test.owner"
    const val STAFF = "test.staff"

    val auth: AuthRepository
        get() = (ApplicationProvider.getApplicationContext<Context>() as AuxoApplication).container.authRepository

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
