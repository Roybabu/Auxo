package com.invision.pos.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.receipt.ReceiptOutput
import com.invision.pos.core.receipt.ReceiptRenderer
import com.invision.pos.core.receipt.isThermal
import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.inventory.InventorySummary
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.navigation.TopLevelDestination
import com.invision.pos.ui.screens.billing.BillingSettingsContent
import com.invision.pos.ui.screens.home.HomeContent
import com.invision.pos.ui.screens.more.ShopSettingsContent
import com.invision.pos.ui.screens.more.StoreLogoSection
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.BillingSettingsUiState
import com.invision.pos.viewmodel.HomeUiState
import com.invision.pos.viewmodel.ShopUiState
import com.invision.pos.viewmodel.StoreLogoUiState
import androidx.compose.ui.unit.dp
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class StoreDetailsAndLogoTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val logo: Bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    // Store details navigation

    @Test
    fun shopSettings_hasStoreDetails_thatOpensTheScreen() {
        var opened = 0
        composeRule.setContent {
            InvisionTheme {
                ShopSettingsContent(
                    state = ShopUiState(shopName = "Das Sweets"),
                    onBack = {},
                    onRestaurantMenuChange = {},
                    onOpenStoreDetails = { opened++ },
                )
            }
        }
        composeRule.onNodeWithText("Store details").assertIsDisplayed()
        composeRule.onNodeWithTag("shop_store_details").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun storeDetails_holdsTheBillingSettings_andTheLogoSection() {
        composeRule.setContent {
            InvisionTheme {
                BillingSettingsContent(
                    state = BillingSettingsUiState(isLoading = false, settings = TestData.settings),
                    onChange = {},
                    onGstinChange = {},
                    onSave = {},
                    topSection = { StoreLogoSection(StoreLogoUiState(), onChoose = {}, onRemove = {}) },
                )
            }
        }
        composeRule.onNodeWithTag("store_logo_section").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_store_name"))
        composeRule.onNodeWithTag("settings_store_name").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_gst_registered"))
        composeRule.onNodeWithTag("settings_gst_registered").assertIsDisplayed()
    }

    // Logo section

    @Test
    fun logoSection_showsAPlaceholder_untilALogoIsAdded() {
        var chose = 0
        composeRule.setContent {
            InvisionTheme { StoreLogoSection(StoreLogoUiState(), onChoose = { chose++ }, onRemove = {}) }
        }
        composeRule.onNodeWithTag("store_logo_placeholder").assertIsDisplayed()
        composeRule.onNodeWithTag("store_logo_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("store_logo_remove").assertDoesNotExist()
        composeRule.onNodeWithText("Choose logo").performClick()
        assertEquals(1, chose)
    }

    @Test
    fun logoSection_previewsTheLogo_andOffersReplaceAndRemove() {
        var chose = 0
        var removed = 0
        composeRule.setContent {
            InvisionTheme { StoreLogoSection(StoreLogoUiState(logo = logo), onChoose = { chose++ }, onRemove = { removed++ }) }
        }
        composeRule.onNodeWithTag("store_logo_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("store_logo_placeholder").assertDoesNotExist()
        composeRule.onNodeWithText("Replace logo").performClick()
        composeRule.onNodeWithTag("store_logo_remove").performClick()
        assertEquals(1, chose)
        assertEquals(1, removed)
    }

    @Test
    fun logoSection_explainsAnUnreadablePicture() {
        composeRule.setContent {
            InvisionTheme { StoreLogoSection(StoreLogoUiState(unreadable = true), onChoose = {}, onRemove = {}) }
        }
        composeRule.onNodeWithTag("store_logo_error").assertIsDisplayed()
    }

    // Home

    private fun home(name: String = "Roy Mart", logo: Bitmap?) {
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(LocalStoreLogo provides logo) {
                    HomeContent(
                        uiState = HomeUiState.Ready(
                            storeName = name,
                            salesToday = "₹0.00",
                            billsToday = 0,
                            inventory = InventorySummary.EMPTY,
                            stockValueAtCost = "₹0.00",
                            stockValueAtPrice = "₹0.00",
                        ),
                        onNavigate = {},
                        onAddProduct = {},
                        onOpenProduct = {},
                        onOpenInventory = {},
                        onRetry = {},
                    )
                }
            }
        }
    }

    @Test
    fun home_showsTheLogoBesideTheShopName() {
        home(logo = logo)
        composeRule.onNodeWithTag("home_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("home_icon").assertDoesNotExist()
        composeRule.onNodeWithText("Roy Mart").assertIsDisplayed()
    }

    @Test
    fun home_fallsBackToTheHomeIcon_withoutALogo() {
        home(logo = null)
        composeRule.onNodeWithTag("home_icon").assertIsDisplayed()
        composeRule.onNodeWithTag("home_logo").assertDoesNotExist()
    }

    @Test
    fun home_keepsTheLogoAndNameVisible_whenTheNameIsLong() {
        home(name = "The Extraordinarily Long Named General Merchants And Sweet Makers Of Old Town", logo = logo)
        composeRule.onNodeWithTag("home_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("home_logo").assertWidthIsEqualTo(52.dp)
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    // Receipts

    private val sale = SampleSales.shirtsAndBiscuits()

    /** Bounds of the pure-red pixels in [bitmap], or null when there are none. */
    private fun redBounds(bitmap: Bitmap): IntArray? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 200 && Color.green(pixel) < 60 && Color.blue(pixel) < 60) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else intArrayOf(minX, minY, maxX, maxY)
    }

    private fun render(paper: ReceiptPaper, logo: Bitmap?, width: Int): Bitmap {
        val page = ReceiptRenderer.pages(sale, ReceiptOptions(paper = paper), ZoneOffset.UTC, logo).first()
        return ReceiptOutput.previewBitmap(page, width)
    }

    @Test
    fun thermalReceipts_printTheLogo_inItsOwnRatio_aboveTheText() {
        for (paper in listOf(ReceiptPaper.THERMAL_58, ReceiptPaper.THERMAL_76, ReceiptPaper.THERMAL_80)) {
            val bounds = redBounds(render(paper, logo, 576))
            assertTrue("$paper should show the logo", bounds != null)
            val width = bounds!![2] - bounds[0] + 1
            val height = bounds[3] - bounds[1] + 1
            assertEquals("$paper logo ratio", 2f, width.toFloat() / height, 0.15f)
            // Centred on the roll.
            assertEquals("$paper logo is centred", 288f, (bounds[0] + bounds[2]) / 2f, 6f)
            // It sits at the very top, above the shop name.
            assertTrue("$paper logo near the top", bounds[1] < 80)
        }
    }

    @Test
    fun sheetReceipts_printTheLogo_inItsOwnRatio_atTheTopLeft() {
        for (paper in listOf(ReceiptPaper.A5, ReceiptPaper.A4)) {
            val bounds = redBounds(render(paper, logo, 1240))
            assertTrue("$paper should show the logo", bounds != null)
            val width = bounds!![2] - bounds[0] + 1
            val height = bounds[3] - bounds[1] + 1
            assertEquals("$paper logo ratio", 2f, width.toFloat() / height, 0.15f)
            assertTrue("$paper logo is on the left", bounds[0] < 1240 / 4)
            assertTrue("$paper logo near the top", bounds[1] < 250)
        }
    }

    @Test
    fun receipts_withoutALogo_haveNoLogoAndNoSpareRoom() {
        for (paper in listOf(ReceiptPaper.THERMAL_80, ReceiptPaper.A4)) {
            val withoutLogo = ReceiptRenderer.pages(sale, ReceiptOptions(paper = paper), ZoneOffset.UTC).first()
            val withLogo = ReceiptRenderer.pages(sale, ReceiptOptions(paper = paper), ZoneOffset.UTC, logo).first()
            val width = if (paper.isThermal) 576 else 1240
            assertEquals("$paper has no red without a logo", null, redBounds(ReceiptOutput.previewBitmap(withoutLogo, width)))
            if (paper.isThermal) {
                assertTrue("a thermal roll grows only when there is a logo", withLogo.heightPt > withoutLogo.heightPt)
            }
            assertEquals(withoutLogo.widthPt, withLogo.widthPt, 0.01f)
        }
    }

    // Home layout: quick actions and the circular logo

    @Test
    fun home_quickActions_haveInventoryWhereAddProductWas_andAddProductWhereReceiveStockWas() {
        var added = 0
        var destination: TopLevelDestination? = null
        composeRule.setContent {
            InvisionTheme {
                HomeContent(
                    uiState = HomeUiState.Ready("Roy Mart", "₹0.00", 0, InventorySummary.EMPTY, "₹0.00", "₹0.00"),
                    onNavigate = { destination = it },
                    onAddProduct = { added++ },
                    onOpenProduct = {},
                    onOpenInventory = {},
                    onRetry = {},
                )
            }
        }
        val order = listOf("home_action_new_bill", "home_action_add_product", "home_action_inventory", "home_action_more")
        val lefts = order.map { composeRule.onNodeWithTag(it).getUnclippedBoundsInRoot().left.value }
        assertEquals("quick actions run left to right in this order", lefts.sorted(), lefts)
        assertEquals(4, lefts.toSet().size)
        composeRule.onNodeWithText("Receive stock").assertDoesNotExist()

        composeRule.onNodeWithTag("home_action_add_product").performClick()
        assertEquals(1, added)
        composeRule.onNodeWithTag("home_action_inventory").performClick()
        assertEquals(TopLevelDestination.INVENTORY, destination)
    }

    @Test
    fun home_logo_isACircle_withNoBorder() {
        home(logo = logo)
        val bounds = composeRule.onNodeWithTag("home_logo").getUnclippedBoundsInRoot()
        assertEquals(bounds.right.value - bounds.left.value, bounds.bottom.value - bounds.top.value, 0.5f)
        // Pixels at the four corners of the tile are outside the circle, so they show the header behind it.
        val image = composeRule.onNodeWithTag("home_logo").captureToImage().asAndroidBitmap()
        val inset = 2
        listOf(inset to inset, image.width - 1 - inset to inset, inset to image.height - 1 - inset, image.width - 1 - inset to image.height - 1 - inset)
            .forEach { (x, y) ->
                val pixel = image.getPixel(x, y)
                assertTrue("corner ($x,$y) is outside the circle, so it must not show the logo", !(Color.red(pixel) > 200 && Color.green(pixel) < 60))
            }
        // The centre is the logo itself (red), with no ring drawn over it.
        val centre = image.getPixel(image.width / 2, image.height / 2)
        assertTrue(Color.red(centre) > 200 && Color.green(centre) < 60)
        // Just inside the edge is still the logo, not a stroke of another colour.
        val nearEdge = image.getPixel(image.width / 2, 3)
        assertTrue("no ring or outline at the edge", Color.red(nearEdge) > 200 && Color.green(nearEdge) < 60)
    }
}
