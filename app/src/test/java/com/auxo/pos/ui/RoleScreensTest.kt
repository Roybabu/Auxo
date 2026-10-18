package com.auxo.pos.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.audit.AuditRecord
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.auth.FileAccountStore
import com.auxo.pos.core.auth.LocalAuthRepository
import com.auxo.pos.core.auth.PasswordHasher
import com.auxo.pos.core.auth.Role
import com.auxo.pos.core.auth.SignInResult
import com.auxo.pos.ui.screens.home.EmployeeHomeContent
import com.auxo.pos.ui.screens.more.ActivityLogContent
import com.auxo.pos.ui.screens.more.MoreActions
import com.auxo.pos.ui.screens.more.MoreScreen
import com.auxo.pos.ui.screens.more.LocalInlineForms
import com.auxo.pos.ui.screens.more.UserManagementScreen
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.ActivityLogUiState
import com.auxo.pos.viewmodel.ShopUiState
import com.auxo.pos.viewmodel.UserManagementViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OWNER_PW = "Green-Leaf-42"
private const val STAFF_PW = "Counter-Desk-17"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class RoleScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun more(role: Role, actions: MoreActions = object : MoreActions {}, shop: ShopUiState = ShopUiState(restaurantMenu = true)) {
        composeRule.setContent {
            AuxoTheme {
                CompositionLocalProvider(LocalCapabilities provides Capabilities(role, "Asha K")) {
                    MoreScreen(actions = actions, shop = shop)
                }
            }
        }
    }

    // More

    @Test
    fun more_forTheSuperAdmin_offersUserManagementAndEverythingElse() {
        more(Role.SUPER_ADMIN)
        composeRule.onNodeWithText("Business settings").assertIsDisplayed()
        composeRule.onNodeWithTag("more_user_management").assertIsDisplayed()
        composeRule.onNodeWithTag("more_recovery_code").assertIsDisplayed()
        composeRule.onNodeWithTag("more_loyalty").assertIsDisplayed()
        composeRule.onNodeWithTag("more_activity_log").assertIsDisplayed()
        composeRule.onNodeWithTag("more_shop_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("more_signed_in").assertIsDisplayed()
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasText("Reports"))
        composeRule.onNodeWithText("Reports").assertIsDisplayed()
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasText("Backup and restore"))
        composeRule.onNodeWithText("Backup and restore").assertIsDisplayed()
    }

    @Test
    fun more_forAnEmployee_hidesEverythingButInvoicesAppearanceAndSignOut() {
        more(Role.EMPLOYEE)
        composeRule.onAllNodesWithTag("more_user_management").assertCountEquals(0)
        composeRule.onAllNodesWithTag("more_recovery_code").assertCountEquals(0)
        composeRule.onAllNodesWithTag("more_loyalty").assertCountEquals(0)
        composeRule.onAllNodesWithTag("more_activity_log").assertCountEquals(0)
        composeRule.onAllNodesWithTag("more_shop_settings").assertCountEquals(0)
        composeRule.onAllNodesWithText("Business settings").assertCountEquals(0)
        for (hidden in listOf("Suppliers", "Stock alerts", "Stock movements", "Reports", "Backup and restore", "Menu", "Invoices and payments")) {
            composeRule.onAllNodesWithText(hidden).assertCountEquals(0)
        }
        composeRule.onNodeWithTag("more_invoices").assertIsDisplayed()
        composeRule.onNodeWithTag("more_appearance").assertIsDisplayed()
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasText("Quick unlock"))
        composeRule.onNodeWithTag("more_quick_unlock").assertIsDisplayed()
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasText("Sign out"))
        composeRule.onNodeWithTag("more_sign_out").assertIsDisplayed()
        composeRule.onNodeWithText("Signed in as Asha K (Employee)").assertIsDisplayed()
    }

    @Test
    fun signOut_asksFirst_thenSignsOut() {
        var signedOut = 0
        more(Role.EMPLOYEE, actions = object : MoreActions {
            override fun onSignOut() {
                signedOut++
            }
        })
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasText("Sign out"))
        composeRule.onNodeWithTag("more_sign_out").performClick()
        assertEquals(0, signedOut)
        composeRule.onNodeWithText("Any bill you have not finished will be lost.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sign out")[composeRule.onAllNodesWithText("Sign out").fetchSemanticsNodes().size - 1].performClick()
        assertEquals(1, signedOut)
    }

    // Employee Home

    @Test
    fun employeeHome_showsNoBusinessFigures_onlyTheThreeActions() {
        var newBill = 0
        composeRule.setContent {
            AuxoTheme {
                CompositionLocalProvider(LocalCapabilities provides Capabilities(Role.EMPLOYEE, "Asha K")) {
                    EmployeeHomeContent(shopName = "Sample Mart", onNewBill = { newBill++ }, onOpenInvoices = {}, onMore = {})
                }
            }
        }
        composeRule.onNodeWithText("Sample Mart").assertIsDisplayed()
        composeRule.onNodeWithTag("employee_home_user").assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_new_bill").assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_invoices").assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_more").assertIsDisplayed()
        for (hidden in listOf("home_action_add_product", "home_action_inventory")) composeRule.onAllNodesWithTag(hidden).assertCountEquals(0)
        for (text in listOf("Cash today", "UPI today", "Stock value", "Sales today")) composeRule.onAllNodesWithText(text).assertCountEquals(0)
        composeRule.onNodeWithTag("home_action_new_bill").performClick()
        assertEquals(1, newBill)
    }

    // Activity log

    @Test
    fun activityLog_listsWhoDidWhat() {
        val entries = listOf(
            AuditRecord(2, 1_700_000_100_000, 1, "Sample User", Role.SUPER_ADMIN, "PRODUCT_DELETED", "product", 7, "Deleted product Linen Shirt"),
            AuditRecord(1, 1_700_000_000_000, 2, "Asha K", Role.EMPLOYEE, "SALE_RECORDED", "invoice", 3, "Sale INV/26-27/00001, total 899.00"),
        )
        composeRule.setContent { AuxoTheme { ActivityLogContent(ActivityLogUiState(isLoading = false, entries = entries), onRetry = {}) } }
        composeRule.onNodeWithText("Deleted product Linen Shirt").assertIsDisplayed()
        composeRule.onNodeWithText("Sale INV/26-27/00001, total 899.00").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_list").assertIsDisplayed()
    }

    // User Management (real accounts on real files)

    private val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "users-ui-${UUID.randomUUID()}").apply { mkdirs() }
    private val auth = LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000))

    private fun userManagement(withEmployee: Boolean = false) {
        runBlocking {
            auth.createAdministrator("Sample User", "sample.user", OWNER_PW, OWNER_PW)
            if (withEmployee) auth.createEmployee("Asha K", "asha.k", STAFF_PW, STAFF_PW)
        }
        val viewModel = UserManagementViewModel(auth)
        composeRule.setContent {
            AuxoTheme {
                CompositionLocalProvider(LocalInlineForms provides true) {
                    UserManagementScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("users_list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForNode(tag: String) = composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun userManagement_showsTheProtectedSuperAdmin_andNoEmployeesYet() {
        userManagement()
        composeRule.onNodeWithTag("user_sample.user").assertIsDisplayed()
        composeRule.onNodeWithText("The Super Admin's username and password cannot be changed here.").assertIsDisplayed()
        composeRule.onAllNodesWithTag("user_active_sample.user").assertCountEquals(0)
        composeRule.onAllNodesWithTag("user_reset_sample.user").assertCountEquals(0)
        composeRule.onNodeWithTag("users_empty").assertIsDisplayed()
    }

    @Test
    fun userManagement_createsAnEmployee_whoCanThenSignIn() {
        userManagement()
        composeRule.onNodeWithTag("users_add").performClick()
        waitForNode("employee_name")
        composeRule.onNodeWithTag("employee_name").performTextInput("Asha K")
        composeRule.onNodeWithTag("employee_identifier").performTextInput("asha.k")
        composeRule.onNodeWithTag("employee_password").performTextInput(STAFF_PW)
        composeRule.onNodeWithTag("employee_confirm").performTextInput(STAFF_PW)
        composeRule.onNodeWithTag("employee_create").performClick()
        waitForNode("user_asha.k")
        composeRule.onNodeWithTag("user_active_asha.k").assertIsDisplayed()

        auth.signOut()
        assertEquals(SignInResult.Success, runBlocking { auth.signIn("asha.k", STAFF_PW) })
        assertEquals(Role.EMPLOYEE, (auth.state.value as AuthState.SignedIn).role)
    }

    @Test
    fun userManagement_showsFieldErrors_andCreatesNothing() {
        userManagement()
        composeRule.onNodeWithTag("users_add").performClick()
        waitForNode("employee_create")
        composeRule.onNodeWithTag("employee_create").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Enter your name.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, runBlocking { auth.listUsers() }!!.size)
    }

    @Test
    fun userManagement_turnsAnEmployeeOff_onlyAfterConfirming_andBackOnWithoutAsking() {
        userManagement(withEmployee = true)
        waitForNode("user_active_asha.k")

        composeRule.onNodeWithTag("user_active_asha.k").performClick()
        composeRule.onNodeWithText("Turn off this account?").assertIsDisplayed()
        // Cancelling changes nothing.
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(runBlocking { auth.listUsers() }!!.single { it.identifier == "asha.k" }.active)

        composeRule.onNodeWithTag("user_active_asha.k").performClick()
        composeRule.onNodeWithText("Turn off").performClick()
        composeRule.waitUntil(5_000) { !runBlocking { auth.listUsers() }!!.single { it.identifier == "asha.k" }.active }
        auth.signOut()
        assertEquals(SignInResult.Deactivated, runBlocking { auth.signIn("asha.k", STAFF_PW) })

        runBlocking { auth.signIn("sample.user", OWNER_PW) }
    }

    @Test
    fun userManagement_resetsAnEmployeesPassword() {
        userManagement(withEmployee = true)
        waitForNode("user_reset_asha.k")
        composeRule.onNodeWithTag("user_reset_asha.k").performClick()
        waitForNode("reset_password")
        composeRule.onNodeWithTag("reset_password").performTextInput("Fresh-Start-77")
        composeRule.onNodeWithTag("reset_confirm").performTextInput("Fresh-Start-77")
        composeRule.onNodeWithTag("reset_submit").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("reset_submit").fetchSemanticsNodes().isEmpty() }

        auth.signOut()
        assertEquals(SignInResult.Rejected, runBlocking { auth.signIn("asha.k", STAFF_PW) })
        assertEquals(SignInResult.Success, runBlocking { auth.signIn("asha.k", "Fresh-Start-77") })
    }

    @Test
    fun theUserManagementScreenIsEmptyForSomeoneWhoIsNotTheSuperAdmin() {
        runBlocking {
            auth.createAdministrator("Sample User", "sample.user", OWNER_PW, OWNER_PW)
            auth.createEmployee("Asha K", "asha.k", STAFF_PW, STAFF_PW)
            auth.signOut()
            auth.signIn("asha.k", STAFF_PW)
        }
        val viewModel = UserManagementViewModel(auth)
        composeRule.setContent { AuxoTheme {
                CompositionLocalProvider(LocalInlineForms provides true) {
                    UserManagementScreen(onBack = {}, viewModel = viewModel)
                }
            } }
        // The data layer refused to list anyone, so the screen shows an error and no accounts.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Something went wrong. Please try again.").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithTag("user_sample.user").assertCountEquals(0)
        assertTrue(runBlocking { auth.listUsers() } == null)
    }
}
