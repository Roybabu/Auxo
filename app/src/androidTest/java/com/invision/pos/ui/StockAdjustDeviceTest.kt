package com.invision.pos.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.DeviceFixtures
import com.invision.pos.InvisionApplication
import com.invision.pos.MainActivity
import com.invision.pos.data.repository.SaveProductResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The stock adjustment dialog, whose text fields can't be driven under
 * Robolectric, used through the real app on a device.
 */
@RunWith(AndroidJUnit4::class)
class StockAdjustDeviceTest {

    @get:Rule(order = 0)
    val signedIn = com.invision.pos.SignedInRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: InvisionApplication = ApplicationProvider.getApplicationContext()

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(timeoutMillis = 15_000) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }


    /**
     * Opens a bottom-bar tab. Home has no bar, so from there the "More" quick action
     * is used first: it leaves Home and brings the bar up.
     */
    private fun clickTab(label: String) {
        val tab = hasText(label) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        // The app may still be drawing its first screen: wait for the bar or for Home's quick actions.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(tab).fetchSemanticsNodes().isEmpty()) {
            composeRule.onAllNodesWithText("More").onFirst().performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) { composeRule.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty() }
        }
        composeRule.onAllNodes(tab).onFirst().performClick()
    }

    @Test
    fun receivingStock_throughTheDialog() {
        val barcode = DeviceFixtures.uniqueBarcode()
        val name = "Dialog Shirt ${barcode.takeLast(6)}"
        val productId = runBlocking {
            (app.container.inventoryRepository.saveProduct(DeviceFixtures.shirt(name, barcode, stock = 5)) as SaveProductResult.Saved).productId
        }

        clickTab("Inventory")
        waitFor(hasTestTag("inventory_search"))
        composeRule.onNodeWithTag("inventory_search").performTextInput(name)
        // The product row, not the search box that also holds the name.
        val row = hasText(name) and hasClickAction() and !hasSetTextAction()
        waitFor(row)
        composeRule.onNode(row).performClick()

        waitFor(hasText("Adjust stock") and hasClickAction())
        composeRule.onNode(hasText("Adjust stock") and hasClickAction()).performClick()
        waitFor(hasText("Current stock: 5", substring = true))
        composeRule.onNode(hasText("Quantity") and hasSetTextAction()).performTextInput("3")
        waitFor(hasText("New stock: 8"))
        composeRule.onNodeWithText("Save").performClick()

        waitFor(hasText("8 in stock"))
        val stock = runBlocking { app.container.inventoryRepository.getProduct(productId)!!.variants.single().stockQuantity }
        assertEquals(8, stock)
    }
}
