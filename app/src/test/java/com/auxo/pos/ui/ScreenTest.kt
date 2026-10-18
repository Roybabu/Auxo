package com.auxo.pos.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.domain.inventory.InventoryQuery
import com.auxo.pos.domain.inventory.InventorySummaryCalculator
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.ProductFormValidator
import com.auxo.pos.domain.validation.ProductValidation
import com.auxo.pos.domain.validation.VariantDefaults
import com.auxo.pos.domain.validation.VariantForm
import com.auxo.pos.domain.validation.GarmentVariants
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.testutil.TestData
import com.auxo.pos.ui.screens.home.HomeContent
import com.auxo.pos.ui.screens.inventory.AdjustStockForm
import com.auxo.pos.ui.components.ConfirmDialog
import com.auxo.pos.ui.screens.inventory.InventoryListContent
import com.auxo.pos.ui.screens.inventory.LabelPrintContent
import com.auxo.pos.ui.screens.inventory.ProductDetailContent
import com.auxo.pos.ui.screens.inventory.ProductEditorActions
import com.auxo.pos.ui.screens.inventory.ProductEditorContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.AdjustStockDialogState
import com.auxo.pos.viewmodel.HomeUiState
import com.auxo.pos.viewmodel.InventoryUiState
import com.auxo.pos.viewmodel.LabelPrintUiState
import com.auxo.pos.viewmodel.ProductDetailUiState
import com.auxo.pos.viewmodel.ProductEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders each screen with sample data on a phone-sized window, checks key
 * content, and saves a screenshot to app/build/outputs/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class ScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formatter = MoneyFormatter()
    private val products = listOf(TestData.tShirt(), TestData.biscuits(), TestData.juice(stock = 0))

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { AuxoTheme { content() } }
        composeRule.waitForIdle()
    }

    private fun screenshot(name: String) {
        // The last root is the top-most window, e.g. a dialog.
        val bitmap = composeRule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun home_withInventory() {
        val summary = InventorySummaryCalculator.calculate(products, TestData.today)
        show {
            HomeContent(
                uiState = HomeUiState.Ready(
                    storeName = "Sample Mart",
                    salesToday = formatter.format(Money.ZERO),
                    billsToday = 0,
                    inventory = summary,
                    stockValueAtCost = formatter.format(summary.stockValueAtCost),
                    stockValueAtPrice = formatter.format(summary.stockValueAtPrice),
                ),
                onNavigate = {},
                onAddProduct = {},
                onOpenProduct = {},
                onOpenInventory = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("Sample Mart").assertIsDisplayed()
        // The inventory figures sit in the white panel, below the header.
        composeRule.onNodeWithText("Units in stock").performScrollTo().assertIsDisplayed()
        screenshot("01_home")
    }

    @Test
    fun home_failed_offersRetry() {
        var retried = false
        show {
            HomeContent(
                uiState = HomeUiState.Failed,
                onNavigate = {},
                onAddProduct = {},
                onOpenProduct = {},
                onOpenInventory = {},
                onRetry = { retried = true },
            )
        }
        composeRule.onNodeWithText("Try again").performClick()
        assertTrue(retried)
        screenshot("02_home_error")
    }

    @Test
    fun inventory_empty_offersToAddFirstProduct() {
        var added = false
        show {
            InventoryListContent(
                uiState = InventoryUiState(isLoading = false, totalProductCount = 0),
                onSearchTextChange = {},
                onSearchSubmitted = {},
                onCategorySelected = {},
                onStockFilterSelected = {},
                onClearFilters = {},
                onRetry = {},
                onOpenProduct = {},
                onAddProduct = { added = true },
            )
        }
        composeRule.onNodeWithText("No products yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add product").performClick()
        assertTrue(added)
        screenshot("03_inventory_empty")
    }

    @Test
    fun inventory_list_showsProductsAndSearch() {
        val typed = mutableListOf<String>()
        var opened = -1L
        show {
            InventoryListContent(
                uiState = InventoryUiState(
                    isLoading = false,
                    products = products.sortedBy { it.name },
                    totalProductCount = products.size,
                    query = InventoryQuery(),
                    today = TestData.today,
                ),
                onSearchTextChange = { typed += it },
                onSearchSubmitted = {},
                onCategorySelected = {},
                onStockFilterSelected = {},
                onClearFilters = {},
                onRetry = {},
                onOpenProduct = { opened = it },
                onAddProduct = {},
            )
        }
        composeRule.onNodeWithText("Cotton T-Shirt").assertIsDisplayed()
        composeRule.onNodeWithText("Mango Juice").assertIsDisplayed()
        screenshot("04_inventory_list")

        composeRule.onNodeWithTag("inventory_search").performTextInput("juice")
        assertEquals("juice", typed.last())
        composeRule.onNodeWithText("Butter Biscuits").performClick()
        assertEquals(2L, opened)
    }

    @Test
    fun productDetail_showsVariantsAndHistory() {
        val product = TestData.tShirt()
        val movements = listOf(
            StockMovement(1, product.variants[0].id, 8, StockChangeReason.INITIAL, null, TestData.clock.millis()),
            StockMovement(2, product.variants[1].id, -2, StockChangeReason.SOLD, "Walk-in", TestData.clock.millis()),
        )
        show {
            ProductDetailContent(
                state = ProductDetailUiState.Ready(product, movements, TestData.today),
                onAdjustStock = {},
            )
        }
        composeRule.onNodeWithText("M · Navy").assertIsDisplayed()
        screenshot("05_product_detail")
    }

    @Test
    fun adjustStockForm_showsNewStock() {
        show {
            AdjustStockForm(
                state = AdjustStockDialogState(
                    variant = TestData.tShirt().variants.first(),
                    reason = StockChangeReason.RECEIVED,
                    quantity = "12",
                ),
                onReasonChange = {},
                onQuantityChange = {},
                onNoteChange = {},
                onDone = {},
            )
        }
        composeRule.onNodeWithText("New stock: 20").assertIsDisplayed()
        screenshot("06_adjust_stock")
    }

    @Test
    fun confirmDialog_rendersInItsOwnWindow() {
        show {
            ConfirmDialog(
                title = "Delete product?",
                message = "Cotton T-Shirt and its stock history will be deleted.",
                confirmLabel = "Delete",
                onConfirm = {},
                onDismiss = {},
                destructive = true,
            )
        }
        // Robolectric captures dialog windows as blank images, so dialogs are checked without
        // screenshots. A text field inside any dialog window never reports idle under
        // Robolectric, so the stock dialog's fields are tested through AdjustStockForm.
        composeRule.onNodeWithText("Delete product?").assertIsDisplayed()
    }

    @Test
    fun editor_garment_showsErrorsAfterSave() {
        var saved = false
        val sizes = listOf("S", "M")
        val colours = listOf("Navy")
        val defaults = VariantDefaults(sellingPrice = "499")
        val form = ProductForm(
            category = ProductCategory.GARMENT,
            name = "",
            sizes = sizes,
            colours = colours,
            defaults = defaults,
            variants = GarmentVariants.sync(sizes, colours, emptyList(), defaults) { "k${keyCounter++}" },
        )
        val errors = (ProductFormValidator.validate(form) as ProductValidation.Invalid).errors
        show {
            ProductEditorContent(
                uiState = ProductEditorUiState(form = form, errors = errors, showErrors = true),
                actions = RecordingActions(onSave = { saved = true }),
            )
        }
        composeRule.onNodeWithTag("editor_name").assertIsDisplayed()
        composeRule.onAllNodesWithText("Required")[0].assertIsDisplayed()
        screenshot("07_editor_garment")

        composeRule.onNodeWithTag("product_editor").performScrollToNode(hasTestTag("editor_save"))
        composeRule.onNodeWithTag("editor_save").performClick()
        assertTrue(saved)
    }

    @Test
    fun editor_food() {
        val form = ProductForm(
            category = ProductCategory.PACKAGED_FOOD,
            name = "Butter Biscuits",
            brand = "Good Bake",
            dietType = DietType.VEG,
            variants = listOf(
                VariantForm(
                    key = "a",
                    netQuantity = "200",
                    unit = QuantityUnit.GRAM,
                    sellingPrice = "30",
                    mrp = "35",
                    barcode = TestData.EAN[2],
                    batchNumber = "B-0925",
                    expiryDate = TestData.today.plusDays(90),
                ),
            ),
        )
        show { ProductEditorContent(uiState = ProductEditorUiState(form = form), actions = RecordingActions()) }
        composeRule.onNodeWithText("Packaged food").assertIsDisplayed()
        screenshot("08_editor_food")
    }

    @Test
    fun labelPrint() {
        val product = TestData.tShirt()
        show {
            LabelPrintContent(
                uiState = LabelPrintUiState(
                    isLoading = false,
                    product = product,
                    copies = product.variants.associate { it.id to 2 },
                ),
                onTemplateChange = {},
                onShowMrpChange = {},
                onCopiesChange = { _, _ -> },
                onMatchStock = {},
            )
        }
        composeRule.onNodeWithText("Preview").assertIsDisplayed()
        screenshot("09_labels")
    }

    @Test
    fun labelPrint_previewsEveryVariantThatWillPrint() {
        val product = TestData.tShirt().let { shirt ->
            shirt.copy(variants = shirt.variants + shirt.variants[0].copy(id = 19, barcode = TestData.EAN[2], size = "XL"))
        }
        val third = product.variants[2]
        show {
            LabelPrintContent(
                uiState = LabelPrintUiState(
                    isLoading = false,
                    product = product,
                    // M and XL print; L is set to zero.
                    copies = mapOf(product.variants[0].id to 2, product.variants[1].id to 0, third.id to 1),
                ),
                onTemplateChange = {},
                onShowMrpChange = {},
                onCopiesChange = { _, _ -> },
                onMatchStock = {},
            )
        }
        composeRule.onNodeWithTag("label_preview_${product.variants[0].id}").assertExists()
        composeRule.onNodeWithTag("label_preview_${third.id}").assertExists()
        composeRule.onAllNodesWithTag("label_preview_${product.variants[1].id}").assertCountEquals(0)
        composeRule.onNodeWithText("Previewing 2 of 3 variants", substring = true).assertExists()
        composeRule.onNodeWithText("Not printing (0 copies)", substring = true).assertExists()
    }

    private var keyCounter = 0

    /** Test double for editor callbacks. */
    private class RecordingActions(private val onSave: () -> Unit = {}) : ProductEditorActions {
        override fun onCategoryChange(category: ProductCategory) = Unit
        override fun onNameChange(value: String) = Unit
        override fun onBrandChange(value: String) = Unit
        override fun onDescriptionChange(value: String) = Unit
        override fun onMaterialChange(value: String) = Unit
        override fun onDietTypeChange(dietType: DietType?) = Unit
        override fun onToggleSize(size: String) = Unit
        override fun onAddColour(colour: String) = Unit
        override fun onRemoveColour(colour: String) = Unit
        override fun onDefaultsChange(defaults: VariantDefaults) = Unit
        override fun onApplyDefaults() = Unit
        override fun onAddPackSize() = Unit
        override fun onRemoveVariant(key: String) = Unit
        override fun onUpdateVariant(key: String, transform: (VariantForm) -> VariantForm) = Unit
        override fun onGenerateBarcode(key: String) = Unit
        override fun onScanBarcode(key: String) = Unit
        override fun onPickExpiry(key: String) = Unit
        override fun onSave() = onSave.invoke()
    }
}
