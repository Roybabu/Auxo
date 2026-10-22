package com.auxo.pos.viewmodel

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.RoomCategoryRepository
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import com.auxo.pos.domain.model.ShopSubcategory
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.awaitUntil
import com.auxo.pos.testutil.keepActive
import com.auxo.pos.ui.screens.more.CategoriesContent
import com.auxo.pos.ui.screens.more.CategoryActions
import com.auxo.pos.ui.theme.AuxoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shop Settings → Product Categories: the screen shows the list, and the view model reports why a change was refused. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class CategoriesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private val sweets = ShopCategory(
        id = 7, name = "Sweets", profile = ProductCategory.PACKAGED_FOOD, enabled = true, sortOrder = 5, builtIn = false,
        subcategories = listOf(ShopSubcategory(1, "Laddoo")),
    )
    private val garment = ShopCategory(id = 1, name = null, profile = ProductCategory.GARMENT, enabled = false, sortOrder = 0, builtIn = true)

    @Test
    fun theScreen_listsCategories_withTheirSwitchAndSubcategories() {
        val changed = mutableListOf<Pair<Long, Boolean>>()
        composeRule.setContent {
            AuxoTheme {
                CategoriesContent(
                    CategoriesUiState(categories = listOf(garment, sweets), loaded = true),
                    onBack = {},
                    actions = object : CategoryActions {
                        override fun onEnabledChange(id: Long, enabled: Boolean) {
                            changed += id to enabled
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("category_1").assertIsDisplayed()
        composeRule.onNodeWithText("Sweets").assertIsDisplayed()
        composeRule.onNodeWithText("Laddoo").assertIsDisplayed()
        composeRule.onNodeWithTag("category_switch_1").assertIsOff()
        composeRule.onNodeWithTag("category_switch_7").assertIsOn()
        composeRule.onNodeWithTag("category_switch_1").performClick()
        assertEquals(listOf(1L to true), changed)
        // Only a custom category can be deleted.
        composeRule.onNodeWithTag("category_delete_7").assertIsDisplayed()
        composeRule.onNodeWithTag("category_delete_1").let { assertTrue(runCatching { it.assertIsDisplayed() }.isFailure) }
    }

    @Test
    fun theScreen_offersAddCategory() {
        composeRule.setContent {
            AuxoTheme {
                CategoriesContent(CategoriesUiState(categories = listOf(garment), loaded = true), onBack = {}, actions = object : CategoryActions {})
            }
        }
        composeRule.onNodeWithTag("category_add").assertIsDisplayed()
    }

    @Test
    fun theViewModel_listsAddedCategories_andExplainsRefusals() = runTest {
        val viewModel = CategoriesViewModel(RoomCategoryRepository(database))
        keepActive(viewModel.uiState)
        awaitUntil { viewModel.uiState.value.categories.size == 10 }
        assertEquals(10, viewModel.uiState.value.categories.size)
        // A new shop starts with the four newer categories off, and Restaurant off.
        assertEquals(
            setOf(ProductCategory.GENERAL, ProductCategory.ELECTRONICS, ProductCategory.PHARMACY, ProductCategory.COSMETICS, ProductCategory.RESTAURANT),
            viewModel.uiState.value.categories.filter { !it.enabled }.map { it.profile }.toSet(),
        )

        viewModel.onAdd("Sweets", ProductCategory.PACKAGED_FOOD)
        awaitUntil { viewModel.uiState.value.categories.any { it.name == "Sweets" } }
        assertTrue(viewModel.uiState.value.categories.any { it.name == "Sweets" })
        assertNull(viewModel.uiState.value.problem)

        viewModel.onAdd("sweets", ProductCategory.BEVERAGE)
        awaitUntil { viewModel.uiState.value.problem != null }
        assertEquals(CategoryProblem.TAKEN, viewModel.uiState.value.problem)
        viewModel.dismissProblem()
        assertNull(viewModel.uiState.value.problem)

        viewModel.onAdd(" ", ProductCategory.BEVERAGE)
        awaitUntil { viewModel.uiState.value.problem != null }
        assertEquals(CategoryProblem.BLANK, viewModel.uiState.value.problem)
        viewModel.dismissProblem()

        val builtIn = viewModel.uiState.value.categories.first { it.builtIn }
        viewModel.onDelete(builtIn.id)
        awaitUntil { viewModel.uiState.value.problem != null }
        assertEquals(CategoryProblem.BUILT_IN, viewModel.uiState.value.problem)
    }
}
