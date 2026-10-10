package com.invision.pos.ui

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
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.ui.theme.InvisionTheme
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
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class AppFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun tabsSwitch_andEmptyInventoryOpensTheEditor() {
        composeRule.setContent { InvisionTheme { InvisionApp() } }
        waitForText("Add your first product")
        screenshot("10_app_home_empty")

        clickTab("Billing")
        waitForText("Start a bill")

        clickTab("Inventory")
        waitForText("No products yet")
        composeRule.onNodeWithText("Add product").performClick()
        waitForTag("editor_name")
        screenshot("11_app_editor_new")
    }

    @Test
    fun addingAGarment_savesItAndShowsTheProduct() {
        composeRule.setContent { InvisionTheme { InvisionApp() } }
        waitForText("Add your first product")
        clickTab("Inventory")
        waitForText("No products yet")
        composeRule.onNodeWithText("Add product").performClick()
        waitForTag("editor_name")

        composeRule.onNodeWithTag("editor_name").performTextInput("Linen Shirt")
        composeRule.onNodeWithText("M").performClick()
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
}
