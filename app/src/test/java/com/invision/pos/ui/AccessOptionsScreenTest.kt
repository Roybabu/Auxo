package com.invision.pos.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.auth.FileAccountStore
import com.invision.pos.core.auth.LocalAuthRepository
import com.invision.pos.core.auth.PasswordHasher
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.PermissionGroup
import com.invision.pos.core.auth.PermissionOptions
import com.invision.pos.core.auth.Role
import com.invision.pos.core.auth.RolePermissions
import com.invision.pos.core.auth.SignInResult
import com.invision.pos.ui.screens.more.LocalInlineForms
import com.invision.pos.ui.screens.more.MoreActions
import com.invision.pos.ui.screens.more.MoreScreen
import com.invision.pos.ui.screens.more.UserManagementScreen
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.ShopUiState
import com.invision.pos.viewmodel.UserManagementViewModel
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

/** Choosing what one employee may do, and what the app then shows them. The tall screen fits the whole checklist. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class AccessOptionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "access-ui-${UUID.randomUUID()}").apply { mkdirs() }
    private val auth = LocalAuthRepository(FileAccountStore(dir), PasswordHasher(iterations = 1_000))

    private fun userManagement(withEmployee: Boolean = false) {
        runBlocking {
            auth.createAdministrator("Roy Babu", "roy.babu", OWNER_PW, OWNER_PW)
            if (withEmployee) auth.createEmployee("Asha K", "asha.k", STAFF_PW, STAFF_PW)
        }
        val viewModel = UserManagementViewModel(auth)
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(LocalInlineForms provides true) {
                    UserManagementScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        waitFor("users_list")
    }

    private fun waitFor(tag: String) = composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun grantedToAsha(): Set<Permission> = runBlocking { auth.listUsers()!!.single { it.identifier == "asha.k" }.granted }

    @Test
    fun theChecklist_offersEveryOption_inGroups_andNothingElse() {
        userManagement()
        composeRule.onNodeWithTag("users_add").performClick()
        waitFor("permission_checklist")
        for (permission in PermissionOptions.all) composeRule.onNodeWithTag("perm_${permission.name}").assertIsDisplayed()
        for (never in listOf(Permission.MANAGE_USERS, Permission.BACKUP_AND_RESTORE, Permission.SELL, Permission.VIEW_INVOICES)) {
            composeRule.onAllNodesWithTag("perm_${never.name}").assertCountEquals(0)
        }
        assertEquals(6, PermissionGroup.entries.size)
    }

    @Test
    fun switchingOnAnOption_alsoSwitchesOnWhatItNeeds_andOffAgainTogether() {
        userManagement()
        composeRule.onNodeWithTag("users_add").performClick()
        waitFor("permission_checklist")
        composeRule.onNodeWithTag("perm_RECEIVE_GOODS").performClick()
        composeRule.onNodeWithTag("perm_RECEIVE_GOODS").assertIsOn()
        composeRule.onNodeWithTag("perm_MANAGE_PURCHASES").assertIsOn()
        composeRule.onNodeWithTag("perm_MANAGE_SUPPLIERS").assertIsOn()
        composeRule.onNodeWithTag("perm_MANAGE_MENU").assertIsOff()

        composeRule.onNodeWithTag("perm_MANAGE_SUPPLIERS").performClick()
        composeRule.onNodeWithTag("perm_MANAGE_SUPPLIERS").assertIsOff()
        composeRule.onNodeWithTag("perm_MANAGE_PURCHASES").assertIsOff()
        composeRule.onNodeWithTag("perm_RECEIVE_GOODS").assertIsOff()
    }

    @Test
    fun anEmployee_isCreatedWithTheChosenOptions_andTheListSaysSo() {
        userManagement()
        composeRule.onNodeWithTag("users_add").performClick()
        waitFor("employee_name")
        composeRule.onNodeWithTag("employee_name").performTextInput("Asha K")
        composeRule.onNodeWithTag("employee_identifier").performTextInput("asha.k")
        composeRule.onNodeWithTag("employee_password").performTextInput(STAFF_PW)
        composeRule.onNodeWithTag("employee_confirm").performTextInput(STAFF_PW)
        composeRule.onNodeWithTag("perm_MANAGE_MENU").performClick()
        composeRule.onNodeWithTag("perm_PROCESS_RETURN").performClick()
        composeRule.onNodeWithTag("employee_create").performClick()
        waitFor("user_asha.k")

        assertEquals(setOf(Permission.MANAGE_MENU, Permission.PROCESS_RETURN), grantedToAsha())
        composeRule.onNodeWithTag("user_access_asha.k").assertIsDisplayed()
        auth.signOut()
        assertEquals(SignInResult.Success, runBlocking { auth.signIn("asha.k", STAFF_PW) })
    }

    @Test
    fun anEmployee_createdWithNoOptions_hasOnlyTheBasics() {
        userManagement(withEmployee = true)
        waitFor("user_asha.k")
        assertEquals(emptySet<Permission>(), grantedToAsha())
    }

    @Test
    fun editingAccess_showsWhatTheyHave_andSavesTheChange() {
        userManagement(withEmployee = true)
        waitFor("user_edit_access_asha.k")
        composeRule.onNodeWithTag("user_edit_access_asha.k").performClick()
        waitFor("users_access_form")
        composeRule.onNodeWithTag("perm_MANAGE_CUSTOMERS").assertIsOff()
        composeRule.onNodeWithTag("perm_RECEIVE_ACCOUNT_PAYMENT").performClick()
        composeRule.onNodeWithTag("perm_MANAGE_CUSTOMERS").assertIsOn()
        composeRule.onNodeWithTag("access_save").performClick()
        composeRule.waitUntil(10_000) { grantedToAsha().isNotEmpty() }
        assertEquals(setOf(Permission.RECEIVE_ACCOUNT_PAYMENT, Permission.MANAGE_CUSTOMERS), grantedToAsha())

        composeRule.onNodeWithTag("user_edit_access_asha.k").performClick()
        waitFor("users_access_form")
        composeRule.onNodeWithTag("perm_MANAGE_CUSTOMERS").assertIsOn()
    }

    @Test
    fun theSuperAdmin_hasNoAccessEditor() {
        userManagement(withEmployee = true)
        waitFor("user_asha.k")
        composeRule.onAllNodesWithTag("user_edit_access_roy.babu").assertCountEquals(0)
    }

    // What the employee then sees

    private fun moreFor(vararg extras: Permission) {
        val held = RolePermissions.of(Role.EMPLOYEE, extras.toSet())
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(LocalCapabilities provides Capabilities(Role.EMPLOYEE, "Asha K", held)) {
                    MoreScreen(actions = object : MoreActions {}, shop = ShopUiState(restaurantMenu = true))
                }
            }
        }
    }

    @Test
    fun more_forAnEmployeeWithTheMenu_showsTheMenuAndNothingElseRestricted() {
        moreFor(Permission.MANAGE_MENU)
        composeRule.onNodeWithTag("more_list").assertIsDisplayed()
        composeRule.onAllNodesWithTag("more_user_management").assertCountEquals(0)
        composeRule.onAllNodesWithTag("more_shop_settings").assertCountEquals(0)
        composeRule.onAllNodesWithTag("more_activity_log").assertCountEquals(0)
        composeRule.onNodeWithTag("more_invoices").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Restaurant menu").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun more_forAnEmployeeWithSettingsAndTheLog_showsThemButNeverUserManagement() {
        moreFor(Permission.MANAGE_SETTINGS, Permission.VIEW_ACTIVITY_LOG)
        composeRule.onNodeWithTag("more_shop_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("more_activity_log").assertIsDisplayed()
        composeRule.onAllNodesWithTag("more_user_management").assertCountEquals(0)
    }
}
