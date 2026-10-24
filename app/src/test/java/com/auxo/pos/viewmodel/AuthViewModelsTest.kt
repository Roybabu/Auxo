// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.AccountStore
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.FileAccountStore
import com.auxo.pos.core.auth.LocalAuthRepository
import com.auxo.pos.core.auth.PasswordHasher
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.core.auth.SetupResult
import com.auxo.pos.core.auth.StoredAccounts
import com.auxo.pos.testutil.MainDispatcherRule
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
    private lateinit var store: AccountStore
    private lateinit var auth: CountingAuth

    @Before
    fun freshDevice() {
        dir = File(context.cacheDir, "auth-vm-${UUID.randomUUID()}").apply { mkdirs() }
        store = FileAccountStore(dir)
        auth = CountingAuth(LocalAuthRepository(store, PasswordHasher(iterations = 1_000)))
    }

    private fun <T> waitUntil(block: suspend () -> T): T = runBlocking { withTimeout(15_000) { block() } }

    private fun AdminSetupViewModel.fill(
        name: String = "Sample User",
        identifier: String = "sample.user",
        pw: String = "Green-Leaf-42",
        confirm: String = pw,
    ) {
        onBusinessChange("Sample Stores")
        onNameChange(name)
        onIdentifierChange(identifier)
        onPasswordChange(pw)
        onConfirmChange(confirm)
    }

    // Setup

    @Test
    fun afterARestore_onlyTheSignInIsAsked_andTheRestoredShopDetailsAreLeftAlone() {
        var saved = 0
        val viewModel = AdminSetupViewModel(auth, restored = true, saveStore = { _, _, _ -> saved++ })
        // No shop name is typed: the backup already brought it.
        viewModel.onNameChange("Sample User")
        viewModel.onIdentifierChange("sample.user")
        viewModel.onPasswordChange("Green-Leaf-42")
        viewModel.onConfirmChange("Green-Leaf-42")
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
        assertEquals(emptyMap<SetupField, SetupError>(), viewModel.uiState.value.errors)
        assertTrue(store.read() is StoredAccounts.Present)
        assertEquals(0, saved)
    }

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
        assertEquals(setOf(SetupField.BUSINESS, SetupField.IDENTIFIER, SetupField.PASSWORD, SetupField.CONFIRM), state.errors.keys)
        assertEquals(0, auth.setups)
        assertEquals(StoredAccounts.Missing, store.read())
    }

    @Test
    fun aMismatchedConfirmation_isReported_andNothingIsSaved() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill(confirm = "Green-Leaf-24")
        viewModel.submit()
        assertEquals(SetupError.CONFIRM_MISMATCH, viewModel.uiState.value.errors[SetupField.CONFIRM])
        assertEquals(0, auth.setups)
        assertEquals(StoredAccounts.Missing, store.read())
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
        assertEquals(AuthState.SignedIn("Sample User"), auth.state.value)
        assertTrue(store.read() is StoredAccounts.Present)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.confirm)
    }

    @Test
    fun theBusinessName_isSavedOnce_afterTheAccountExists() {
        val saved = mutableListOf<String>()
        val viewModel = AdminSetupViewModel(auth) { name, _, _ -> saved += name }
        viewModel.fill()
        viewModel.onBusinessChange("  Sample   Stores ")
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
        assertEquals(listOf("Sample Stores"), saved)
    }

    @Test
    fun theBusinessName_isNotSaved_whenTheFormIsInvalid() {
        val saved = mutableListOf<String>()
        val viewModel = AdminSetupViewModel(auth) { name, _, _ -> saved += name }
        viewModel.fill(confirm = "nope")
        viewModel.submit()
        assertTrue(saved.isEmpty())
        assertEquals(StoredAccounts.Missing, store.read())
    }

    @Test
    fun onlyTheShopNameUsernameAndPassword_areRequired() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.onBusinessChange("Sample Stores")
        viewModel.onIdentifierChange("sample.user")
        viewModel.onPasswordChange("Green-Leaf-42")
        viewModel.onConfirmChange("Green-Leaf-42")
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
        assertTrue(viewModel.uiState.value.errors.isEmpty())
        assertEquals(1, auth.setups)
        // With no name typed, the username stands in for it.
        assertEquals(AuthState.SignedIn("sample.user"), auth.state.value)
    }

    @Test
    fun theStoreDetails_areSavedWithTheBusinessName_andTheLogo() {
        var savedName: String? = null
        var savedDetails: com.auxo.pos.domain.model.StoreSettings? = null
        var savedLogo: android.net.Uri? = null
        val viewModel = AdminSetupViewModel(auth) { name, details, logo ->
            savedName = name
            savedDetails = details
            savedLogo = logo
        }
        viewModel.fill()
        viewModel.onDetailsChange { it.copy(address = "  12 Park Street  ", phone = "9876543210", upiId = "sample@okbank") }
        val logo = android.net.Uri.parse("content://media/picker/1")
        viewModel.onLogoPicked(logo)
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
        assertEquals("Sample Stores", savedName)
        assertEquals("12 Park Street", savedDetails?.address)
        assertEquals("9876543210", savedDetails?.phone)
        assertEquals("sample@okbank", savedDetails?.upiId)
        assertEquals(logo, savedLogo)
    }

    @Test
    fun aWronglyFilledDetail_stopsSetup_butAnEmptyOneNever_does() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill()
        viewModel.onDetailsChange { it.copy(phone = "123") }
        viewModel.submit()
        assertTrue(com.auxo.pos.domain.billing.SettingsField.PHONE in viewModel.uiState.value.detailErrors)
        assertEquals(0, auth.setups)
        viewModel.onDetailsChange { it.copy(phone = "") }
        assertTrue(viewModel.uiState.value.detailErrors.isEmpty())
    }

    @Test
    fun aBlankBusinessName_isRefused() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill()
        viewModel.onBusinessChange("   ")
        viewModel.submit()
        assertEquals(SetupError.BUSINESS_REQUIRED, viewModel.uiState.value.errors[SetupField.BUSINESS])
        assertEquals(0, auth.setups)
    }

    @Test
    fun usernameIdeas_followTheName_untilAUsernameIsTyped_andFillTheField() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.onNameChange("Sample User")
        assertTrue("sample.user" in viewModel.uiState.value.suggestions)
        viewModel.useSuggestion("sample.user")
        assertEquals("sample.user", viewModel.uiState.value.identifier)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
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
        assertTrue(store.read() is StoredAccounts.Present)
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
        val saved = (store.read() as StoredAccounts.Present).accounts.single()
        assertEquals("sample.user", saved.identifier)
    }

    // Login

    private fun createAdmin() {
        val viewModel = AdminSetupViewModel(auth)
        viewModel.fill()
        viewModel.submit()
        waitUntil { viewModel.uiState.first { !it.isSubmitting } }
    }

    private fun restart() = CountingAuth(LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000)))

    @Test
    fun afterARestart_loginAcceptsTheAdministrator() {
        createAdmin()
        val next = restart()
        assertEquals(AuthState.SignedOut, next.state.value)
        val login = LoginViewModel(next)
        login.onIdentifierChange("sample.user")
        login.onPasswordChange("Green-Leaf-42")
        login.signIn()
        waitUntil { login.uiState.first { !it.isSigningIn } }
        assertEquals(AuthState.SignedIn("Sample User"), next.state.value)
        assertEquals("", login.uiState.value.password)
    }

    @Test
    fun aWrongPassword_showsOneGenericMessage_andStaysSignedOut() {
        createAdmin()
        val next = restart()
        val login = LoginViewModel(next)
        login.onIdentifierChange("sample.user")
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
    fun signingInWithMissingFields_explainsWhichOnes_andDoesNotCheckCredentials() {
        createAdmin()
        val next = restart()
        val login = LoginViewModel(next)
        login.signIn()
        assertTrue(login.uiState.value.identifierMissing)
        assertTrue(login.uiState.value.passwordMissing)
        assertFalse(login.uiState.value.isSigningIn)
        assertNull(login.uiState.value.message)

        login.onIdentifierChange("sample.user")
        assertFalse(login.uiState.value.identifierMissing)
        assertTrue(login.uiState.value.passwordMissing)
        login.signIn()
        assertFalse(login.uiState.value.identifierMissing)
        assertTrue(login.uiState.value.passwordMissing)
        assertEquals(AuthState.SignedOut, next.state.value)

        login.onPasswordChange("Green-Leaf-42")
        assertFalse(login.uiState.value.passwordMissing)
    }

    @Test
    fun whitespaceOnlyUsername_countsAsMissing() {
        createAdmin()
        val login = LoginViewModel(restart())
        login.onIdentifierChange("   ")
        login.onPasswordChange("Green-Leaf-42")
        login.signIn()
        assertTrue(login.uiState.value.identifierMissing)
    }
}
