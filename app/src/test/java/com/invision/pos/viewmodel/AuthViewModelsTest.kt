package com.invision.pos.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.auth.AdminAccountStore
import com.invision.pos.core.auth.AuthRepository
import com.invision.pos.core.auth.AuthState
import com.invision.pos.core.auth.FileAdminAccountStore
import com.invision.pos.core.auth.LocalAuthRepository
import com.invision.pos.core.auth.PasswordHasher
import com.invision.pos.core.auth.SetupError
import com.invision.pos.core.auth.SetupField
import com.invision.pos.core.auth.SetupResult
import com.invision.pos.core.auth.StoredAdmin
import com.invision.pos.testutil.MainDispatcherRule
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class CountingAuth(private val inner: AuthRepository) : AuthRepository by inner {
    var setups = 0
    override suspend fun createAdministrator(name: String, identifier: String, password: String, confirm: String): SetupResult {
        setups++
        return inner.createAdministrator(name, identifier, password, confirm)
    }
}

@RunWith(AndroidJUnit4::class)
class AuthViewModelsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var store: AdminAccountStore
    private lateinit var auth: CountingAuth

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "auth-vm-${UUID.randomUUID()}").apply { mkdirs() }
        store = FileAdminAccountStore(dir)
        auth = CountingAuth(LocalAuthRepository(store, PasswordHasher(iterations = 1_000)))
    }

    private fun <T> waitUntil(block: suspend () -> T): T = runBlocking { withTimeout(15_000) { block() } }

    private fun AdminSetupViewModel.fill(
        name: String = "Roy Babu",
        identifier: String = "roy.babu",
        pw: String = "Green-Leaf-42",
        confirm: String = pw,
    ) {
        onNameChange(name)
        onIdentifierChange(identifier)
        onPasswordChange(pw)
        onConfirmChange(confirm)
    }

    // Setup

    @Test
    fun errorsAreNotShownWhileTheFormIsUntouched_orBeingFirstTyped() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.onNameChange("R")
        assertTrue(viewModel.uiState.value.errors.isEmpty())
        assertFalse(viewModel.uiState.value.submitted)
    }

    @Test
    fun submittingAnEmptyForm_showsEveryError_andCreatesNothing() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.submit()
        val state = viewModel.uiState.value
        assertTrue(state.submitted)
        assertEquals(setOf(SetupField.NAME, SetupField.IDENTIFIER, SetupField.PASSWORD, SetupField.CONFIRM), state.errors.keys)
        assertEquals(0, auth.setups)
        assertEquals(StoredAdmin.Missing, store.read())
    }

    @Test
    fun aMismatchedConfirmation_isReported_andNothingIsSaved() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill(confirm = "Green-Leaf-24")
        viewModel.submit()
        assertEquals(SetupError.CONFIRM_MISMATCH, viewModel.uiState.value.errors[SetupField.CONFIRM])
        assertEquals(0, auth.setups)
        assertEquals(StoredAdmin.Missing, store.read())
    }

    @Test
    fun afterTheFirstAttempt_errorsFollowTheTyping() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill(confirm = "nope")
        viewModel.submit()
        assertTrue(SetupField.CONFIRM in viewModel.uiState.value.errors)
        viewModel.onConfirmChange("Green-Leaf-42")
        assertNull(viewModel.uiState.value.errors[SetupField.CONFIRM])
    }

    @Test
    fun aValidForm_createsTheAdministrator_andForgetsThePassword() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill()
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
        assertEquals(AuthState.SignedIn("Roy Babu"), auth.state.value)
        assertTrue(store.read() is StoredAdmin.Present)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.confirm)
    }

    @Test
    fun tappingCreateTwiceQuickly_savesOneAccount() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill()
        viewModel.submit()
        viewModel.submit()
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
        assertEquals(1, auth.setups)
        assertTrue(store.read() is StoredAdmin.Present)
    }

    @Test
    fun aSecondSetupScreen_cannotReplaceTheAdministrator() {
        val first = AdminSetupViewModel(auth)
        val second = AdminSetupViewModel(auth)
        first.fill()
        second.fill(name = "Other", identifier = "other.user", pw = "Other-Pass-99")
        first.submit()
        waitUntil { first.uiState.first { !it.isSubmitting } }
        second.submit()
        waitUntil { second.uiState.first { !it.isSubmitting } }
        val saved = (store.read() as StoredAdmin.Present).account
        assertEquals("roy.babu", saved.identifier)
    }

    // Login

    private fun createAdmin() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill()
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
    }

    private fun restart() = CountingAuth(LocalAuthRepository(FileAdminAccountStore(dir), PasswordHasher(iterations = 1_000)))

    @Test
    fun afterARestart_loginAcceptsTheAdministrator() {
        createAdmin()
        val next = restart()
        assertEquals(AuthState.SignedOut, next.state.value)
        val login = LoginViewModel(next)
        login.onIdentifierChange("roy.babu")
        login.onPasswordChange("Green-Leaf-42")
        login.signIn()
        waitUntil { login.uiState.first { !it.isSigningIn } }
        assertEquals(AuthState.SignedIn("Roy Babu"), next.state.value)
        assertEquals("", login.uiState.value.password)
    }

    @Test
    fun aWrongPassword_showsOneGenericMessage_andStaysSignedOut() {
        createAdmin()
        val next = restart()
        val login = LoginViewModel(next)
        login.onIdentifierChange("roy.babu")
        login.onPasswordChange("wrong-Pass-1")
        login.signIn()
        waitUntil { login.uiState.first { !it.isSigningIn } }
        assertEquals(LoginMessage.Rejected, login.uiState.value.message)
        assertEquals(AuthState.SignedOut, next.state.value)
        // Typing again clears the message.
        login.onPasswordChange("x")
        assertNull(login.uiState.value.message)
    }

    @Test
    fun loginIgnoresBlankFields() {
        createAdmin()
        val next = restart()
        val login = LoginViewModel(next)
        login.signIn()
        assertFalse(login.uiState.value.isSigningIn)
        assertNull(login.uiState.value.message)
    }
}
