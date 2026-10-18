package com.auxo.pos.data

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.CategoryResult
import com.auxo.pos.data.repository.RoomCategoryRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.inventory.InventoryFilter
import com.auxo.pos.domain.inventory.InventoryQuery
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import com.auxo.pos.domain.model.ShopSubcategory
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.VariantDefaults
import com.auxo.pos.domain.validation.VariantForm
import com.auxo.pos.testutil.TestData
import com.auxo.pos.ui.LocalCategories
import com.auxo.pos.ui.screens.inventory.ProductEditorActions
import com.auxo.pos.ui.screens.inventory.ProductEditorContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.ProductEditorUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Products are listed under the shop's categories: Add Product offers them, lists filter on them, nothing is lost. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class CategoryProductsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: AuxoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun fresh(product: com.auxo.pos.domain.model.Product) =
        product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })

    @Test
    fun aProductSavedWithoutACategory_goesUnderTheBuiltInOneForItsKind() = runTest {
        val categories = RoomCategoryRepository(database)
        val garment = categories.getCategories().first { it.profile == ProductCategory.GARMENT }
        val inventory = RoomInventoryRepository(database, TestData.clock)
        val id = (inventory.saveProduct(fresh(TestData.tShirt())) as SaveProductResult.Saved).productId
        assertEquals(garment.id, inventory.getProduct(id)!!.categoryId)
    }

    @Test
    fun stockMovements_carryTheShopCategory_soReportsCanSeparateTwoOfTheSameKind() = runTest {
        val categories = RoomCategoryRepository(database)
        val inventory = RoomInventoryRepository(database, TestData.clock)
        val sweets = (categories.addCategory("Sweets", ProductCategory.PACKAGED_FOOD) as CategoryResult.Done).id
        val snacks = (categories.addCategory("Snacks", ProductCategory.PACKAGED_FOOD) as CategoryResult.Done).id
        inventory.saveProduct(fresh(TestData.biscuits()).copy(name = "Laddoo", categoryId = sweets))
        val chips = fresh(TestData.biscuits()).copy(name = "Chips", categoryId = snacks)
        inventory.saveProduct(chips.copy(variants = chips.variants.map { it.copy(barcode = TestData.EAN[5]) }))

        val history = inventory.observeMovementHistory(0, Long.MAX_VALUE).first()
        assertEquals(setOf(sweets, snacks), history.map { it.categoryId }.toSet())
        val onlySweets = com.auxo.pos.domain.reports.Reports.stockMovements(history, categoryId = sweets)
        assertTrue(onlySweets.rows.isNotEmpty())
        assertTrue(onlySweets.rows.all { (it[1] as com.auxo.pos.domain.reports.ReportValue.Text).value == "Laddoo" })
    }

    @Test
    fun aCustomCategory_keepsItsProducts_whenSwitchedOff_andTheyStillSell() = runTest {
        val categories = RoomCategoryRepository(database)
        val inventory = RoomInventoryRepository(database, TestData.clock)
        val sweets = (categories.addCategory("Sweets", ProductCategory.PACKAGED_FOOD) as CategoryResult.Done).id
        val id = (inventory.saveProduct(fresh(TestData.biscuits()).copy(categoryId = sweets)) as SaveProductResult.Saved).productId

        categories.setEnabled(sweets, false)
        val product = inventory.getProduct(id)!!
        assertEquals(sweets, product.categoryId)
    }

    @Test
    fun theListFilter_separatesTwoCategoriesOfTheSameKind() {
        val sweets = TestData.biscuits(id = 2).copy(categoryId = 10)
        val snacks = TestData.biscuits(id = 3).copy(name = "Chips", categoryId = 11)
        val result = InventoryFilter.apply(listOf(sweets, snacks), InventoryQuery(categoryId = 10), TestData.today)
        assertEquals(listOf(sweets.name), result.map { it.name })
        assertTrue(InventoryQuery(categoryId = 10).isFiltered)
    }

    @Test
    fun addProduct_offersTheShopsCategories_includingCustomOnes() {
        val chosen = mutableListOf<Pair<Long, ProductCategory>>()
        val rows = listOf(
            ShopCategory(1, null, ProductCategory.GARMENT, true, 0, true),
            ShopCategory(7, "Sweets", ProductCategory.PACKAGED_FOOD, true, 5, false, listOf(ShopSubcategory(1, "Laddoo"))),
            ShopCategory(2, null, ProductCategory.BEVERAGE, false, 2, true),
        )
        composeRule.setContent {
            AuxoTheme {
                CompositionLocalProvider(LocalCategories provides rows) {
                    ProductEditorContent(
                        uiState = ProductEditorUiState(form = ProductForm(categoryId = 1)),
                        actions = object : ProductEditorActionsStub() {
                            override fun onShopCategoryChange(id: Long, profile: ProductCategory) {
                                chosen += id to profile
                            }
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("category_chip_1").assertIsDisplayed()
        composeRule.onNodeWithTag("category_chip_7").assertIsDisplayed()
        // A switched-off category is not offered for a new product.
        composeRule.onNodeWithTag("category_chip_2").assertDoesNotExist()
        composeRule.onNodeWithTag("category_chip_7").performClick()
        assertEquals(listOf(7L to ProductCategory.PACKAGED_FOOD), chosen)
    }
}

/** Does nothing for every editor callback; a test overrides the ones it watches. */
private open class ProductEditorActionsStub : ProductEditorActions {
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
    override fun onSave() = Unit
}
