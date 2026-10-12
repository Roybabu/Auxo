package com.invision.pos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.auth.Role
import com.invision.pos.domain.inventory.StockFilter
import com.invision.pos.testutil.SignIn
import com.invision.pos.ui.navigation.BillingRoutes
import com.invision.pos.ui.navigation.CustomerRoutes
import com.invision.pos.ui.navigation.InventoryRoutes
import com.invision.pos.ui.navigation.InvisionNavHost
import com.invision.pos.ui.navigation.MoreRoutes
import com.invision.pos.ui.navigation.StockRoutes
import com.invision.pos.ui.navigation.TopLevelDestination
import com.invision.pos.ui.theme.InvisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole app with the real database, signed in as each role. Pages a role may not use are not just hidden:
 * navigating straight to them is refused and sends the person back Home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class RoleAccessFlowTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun showApp(role: Role, name: String) {
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(LocalCapabilities provides Capabilities(role, name)) {
                    InvisionApp()
                }
            }
        }
    }

    private fun showNavHost(role: Role, name: String) {
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(LocalCapabilities provides Capabilities(role, name)) {
                    nav = rememberNavController()
                    InvisionNavHost(nav)
                }
            }
        }
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun goTo(route: String) {
        composeRule.runOnUiThread { nav.navigate(route) }
        composeRule.waitForIdle()
    }

    private val restricted = mapOf(
        "inventory" to InventoryRoutes.list(StockFilter.ALL),
        "new product" to InventoryRoutes.newProduct(),
        "customers" to CustomerRoutes.LIST,
        "new customer" to CustomerRoutes.newCustomer(),
        "suppliers" to StockRoutes.suppliers(),
        "purchases" to StockRoutes.purchases(),
        "new purchase order" to StockRoutes.newOrder(),
        "receive stock" to StockRoutes.receive(),
        "stock movements" to StockRoutes.movements(),
        "stock alerts" to StockRoutes.alerts(),
        "menu" to MoreRoutes.MENU,
        "new dish" to MoreRoutes.newMenuItem(),
        "reports" to MoreRoutes.REPORTS,
        "backup" to MoreRoutes.BACKUP,
        "shop settings" to MoreRoutes.SHOP_SETTINGS,
        "store details" to MoreRoutes.STORE_DETAILS,
        "user management" to MoreRoutes.USER_MANAGEMENT,
        "activity log" to MoreRoutes.ACTIVITY_LOG,
        "return for an invoice" to BillingRoutes.returnFor(1),
    )

    // Employee

    @Test
    fun anEmployee_isSentHome_fromEveryRestrictedPage() {
        SignIn.asEmployee()
        showNavHost(Role.EMPLOYEE, "Test Staff")
        waitForTag("employee_home")
        for ((label, route) in restricted) {
            goTo(route)
            composeRule.waitUntil(5_000) { nav.currentDestination?.route == TopLevelDestination.HOME.route }
            assertEquals("$label should send an employee Home", TopLevelDestination.HOME.route, nav.currentDestination?.route)
            composeRule.onNodeWithTag("employee_home").assertIsDisplayed()
        }
    }

    @Test
    fun anEmployee_canOpenTheBillingPage_andInvoiceHistory() {
        SignIn.asEmployee()
        showNavHost(Role.EMPLOYEE, "Test Staff")
        waitForTag("employee_home")
        goTo(BillingRoutes.POS)
        composeRule.waitUntil(5_000) { nav.currentDestination?.route == BillingRoutes.POS }
        goTo(BillingRoutes.HISTORY)
        composeRule.waitUntil(5_000) { nav.currentDestination?.route == BillingRoutes.HISTORY }
        // The employee's history is the invoice list only: no Payments tab, no sales totals.
        composeRule.onAllNodesWithText("Payments").assertCountEquals(0)
    }

    @Test
    fun anEmployeesHome_hasNoFigures_andTheBarHasOnlyBillingAndMore() {
        SignIn.asEmployee()
        showApp(Role.EMPLOYEE, "Test Staff")
        waitForTag("employee_home")
        composeRule.onAllNodesWithTag("home_action_add_product").assertCountEquals(0)
        composeRule.onNodeWithTag("home_action_new_bill").performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Billing").fetchSemanticsNodes().isNotEmpty() }
        val tabs = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        composeRule.onAllNodes(hasText("Inventory") and hasClickAction() and tabs).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Customers") and hasClickAction() and tabs).assertCountEquals(0)
        composeRule.onAllNodes(hasText("More") and hasClickAction() and tabs).assertCountEquals(1)
    }

    // Super Admin

    @Test
    fun theSuperAdmin_canOpenEveryPage() {
        SignIn.asSuperAdmin()
        showNavHost(Role.SUPER_ADMIN, "Test Owner")
        composeRule.waitUntil(10_000) { nav.currentDestination?.route == TopLevelDestination.HOME.route }
        for ((label, route) in restricted.filterKeys { "return" !in it }) {
            goTo(route)
            val here = nav.currentDestination?.route
            assertNotEquals("$label was refused for the Super Admin", TopLevelDestination.HOME.route, here)
            composeRule.runOnUiThread { nav.popBackStack() }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun theSuperAdmin_reachesUserManagement_fromMore() {
        SignIn.asSuperAdmin()
        showApp(Role.SUPER_ADMIN, "Test Owner")
        composeRule.onAllNodesWithText("More").onFirst().performClick()
        waitForTag("more_user_management")
        composeRule.onNodeWithTag("more_user_management").performClick()
        waitForTag("users_list")
        composeRule.onNodeWithText("Add employee").assertIsDisplayed()
    }

    @Test
    fun anEmployee_doesNotSeeUserManagement_inMore() {
        SignIn.asEmployee()
        showApp(Role.EMPLOYEE, "Test Staff")
        waitForTag("employee_home")
        composeRule.onNodeWithTag("home_action_more").performClick()
        waitForTag("more_list")
        composeRule.onAllNodesWithTag("more_user_management").assertCountEquals(0)
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasText("Sign out"))
    }
}
