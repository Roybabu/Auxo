package com.auxo.pos.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.testutil.SignIn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Runs the whole app (navigation, ViewModels and the real Room database) in
 * Robolectric and drives it through the UI.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class AppFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The app opens to Login; these tests drive the app itself, so the Super Admin is already signed in.
    @Before
    fun signIn() = SignIn.asSuperAdmin()

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickTab(label: String) {
        // Navigation items are the selectable nodes with this label.
        composeRule.onAllNodes(hasText(label) and hasClickAction())
            .filterToOne(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .performClick()
    }

    /** Home has no bottom bar, so other screens are reached through its quick actions. */
    private fun homeAction(label: String) {
        composeRule.onAllNodesWithText(label).onFirst().performClick()
    }

    private fun tabBarShown(): Boolean = composeRule.onAllNodes(
        hasText("Customers") and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
    ).fetchSemanticsNodes().isNotEmpty()

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun tabsSwitch_andEmptyInventoryOpensTheEditor() {
        composeRule.setContent { AuxoTheme { AuxoApp() } }
        waitForText("Add your first product")
        screenshot("10_app_home_empty")

        homeAction("New bill")
        waitForText("No products to sell yet")

        clickTab("Inventory")
        waitForText("No products yet")
        composeRule.onNodeWithText("Add product").performClick()
        waitForTag("editor_name")
        screenshot("11_app_editor_new")
    }

    @Test
    fun addingAGarment_savesItAndShowsTheProduct() {
        composeRule.setContent { AuxoTheme { AuxoApp() } }
        waitForText("Add your first product")
        homeAction("New bill")
        clickTab("Inventory")
        waitForText("No products yet")
        composeRule.onNodeWithText("Add product").performClick()
        waitForTag("editor_name")

        composeRule.onNodeWithTag("editor_name").performTextInput("Linen Shirt")
        composeRule.onNodeWithTag("product_editor").performScrollToNode(hasText("M") and hasClickAction())
        composeRule.onNode(hasText("M") and hasClickAction()).performClick()
        composeRule.onNodeWithTag("product_editor").performScrollToNode(hasTestTag("editor_colour"))
        composeRule.onNodeWithTag("editor_colour").performTextInput("Beige")
        composeRule.onNodeWithTag("editor_add_colour").performClick()

        composeRule.onNodeWithTag("product_editor").performScrollToNode(hasTestTag("variant_price_0"))
        composeRule.onNodeWithTag("variant_price_0").performTextInput("899")
        composeRule.onNodeWithTag("product_editor").performScrollToNode(hasTestTag("editor_save"))
        composeRule.onNodeWithTag("editor_save").performClick()

        // After saving, the editor is replaced by the product page.
        waitForText("Adjust stock")
        composeRule.onNodeWithText("M · Beige").assertExists()
        screenshot("12_app_product_saved")

        clickTab("Home")
        waitForText("Units in stock")
        screenshot("13_app_home_with_product")
    }

    @Test
    fun homeHidesTheTabBar_otherScreensShowIt() {
        composeRule.setContent { AuxoTheme { AuxoApp() } }
        waitForText("Add your first product")
        assertFalse("The tab bar should be hidden on Home", tabBarShown())

        homeAction("New bill")
        waitForText("No products to sell yet")
        assertTrue("The tab bar should show away from Home", tabBarShown())

        clickTab("Home")
        waitForText("Add your first product")
        assertFalse(tabBarShown())
    }

    @Test
    fun homeTab_alwaysReturnsHome_fromAScreenOpenedOnHome() {
        composeRule.setContent { AuxoTheme { AuxoApp() } }
        waitForText("Add your first product")

        homeAction("Add product")
        waitForTag("editor_name")
        clickTab("Home")
        waitForText("Add your first product")
        assertFalse(tabBarShown())

        composeRule.onAllNodes(hasText("Inventory") and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
        assertTrue(tabBarShown())
        clickTab("Home")
        waitForText("Add your first product")
    }

    @Test
    fun moreTab_alwaysOpensItsMenu_notTheOptionYouLastUsed() {
        composeRule.setContent { AuxoTheme { AuxoApp() } }
        waitForText("Add your first product")

        homeAction("More")
        waitForTag("more_list")
        composeRule.onNodeWithText("Suppliers, purchase orders and receiving stock").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("more_list").fetchSemanticsNodes().isEmpty()
        }

        clickTab("Home")
        waitForText("Add your first product")
        homeAction("More")
        waitForTag("more_list")

        // Tapping More while already inside one of its pages also goes back to the menu.
        composeRule.onNodeWithText("Suppliers, purchase orders and receiving stock").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("more_list").fetchSemanticsNodes().isEmpty()
        }
        clickTab("More")
        waitForTag("more_list")
    }

    @Test
    fun tappingTheCurrentTab_goesBackToItsMainPage() {
        composeRule.setContent { AuxoTheme { AuxoApp() } }
        waitForText("Add your first product")
        homeAction("New bill")
        waitForText("No products to sell yet")

        // Billing: from the invoice history back to the billing page.
        composeRule.onNodeWithContentDescription("Invoices and payments").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("No products to sell yet").fetchSemanticsNodes().isEmpty()
        }
        clickTab("Billing")
        waitForText("No products to sell yet")

        // Inventory: from the product editor back to the product list.
        clickTab("Inventory")
        waitForText("No products yet")
        composeRule.onNodeWithText("Add product").performClick()
        waitForTag("editor_name")
        clickTab("Inventory")
        waitForText("No products yet")
        composeRule.onAllNodesWithTag("editor_name").assertCountEquals(0)

        // Customers: from the customer editor back to the customer list.
        clickTab("Customers")
        waitForText("No customers yet")
        composeRule.onAllNodesWithText("Add customer").onFirst().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("No customers yet").fetchSemanticsNodes().isEmpty()
        }
        clickTab("Customers")
        waitForText("No customers yet")
    }

    @Test
    fun tabsOpenedFromHome_alsoGoToTheirMainPage() {
        composeRule.setContent { AuxoTheme { AuxoApp() } }
        waitForText("Add your first product")

        homeAction("Add product")
        waitForTag("editor_name")
        clickTab("Inventory")
        waitForText("No products yet")
        composeRule.onAllNodesWithTag("editor_name").assertCountEquals(0)
    }
}
