package com.auxo.pos.viewmodel

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.core.shop.SharedPrefsShopPreferences
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.RoomCategoryRepository
import com.auxo.pos.domain.category.CategoryRules
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.awaitUntil
import com.auxo.pos.ui.AuthGate
import com.auxo.pos.ui.screens.auth.CategorySetupContent
import com.auxo.pos.ui.theme.AuxoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** First-time setup: the admin ticks the categories the shop sells in, and restaurant service follows the choice. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class CategorySetupTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: AuxoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        context.getSharedPreferences(SharedPrefsShopPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() = database.close()

    private fun category(id: Long, profile: ProductCategory, enabled: Boolean) =
        ShopCategory(id = id, name = null, profile = profile, enabled = enabled, sortOrder = id.toInt(), builtIn = true)

    @Test
    fun restaurantService_isOnOnlyWhileARestaurantCategoryIsOn() {
        val off = listOf(category(1, ProductCategory.GARMENT, true), category(2, ProductCategory.RESTAURANT, false))
        assertFalse(CategoryRules.restaurantModeOn(off))
        assertTrue(CategoryRules.restaurantModeOn(off.map { if (it.profile == ProductCategory.RESTAURANT) it.copy(enabled = true) else it }))
        assertFalse(CategoryRules.restaurantModeOn(emptyList()))
    }

    @Test
    fun theSetupScreen_showsSellingCategories_andPassesTaps() {
        val toggled = mutableListOf<Long>()
        var confirmed = 0
        composeRule.setContent {
            AuxoTheme {
                CategorySetupContent(
                    CategorySetupUiState(
                        categories = listOf(category(1, ProductCategory.GARMENT, true), category(4, ProductCategory.INGREDIENT, true), category(2, ProductCategory.RESTAURANT, false)),
                        selected = setOf(1), loaded = true,
                    ),
                    onToggle = { toggled += it },
                    onConfirm = { confirmed++ },
                )
            }
        }
        composeRule.onNodeWithTag("setup_category_1").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_category_2").performClick()
        composeRule.onNodeWithTag("category_setup_continue").performClick()
        assertEquals(listOf(2L), toggled)
        assertEquals(1, confirmed)
        // Ingredients are kitchen stock, not something the shop sells, so they are not offered here.
        composeRule.onNodeWithTag("setup_category_4").assertDoesNotExist()
    }

    @Test
    fun confirming_turnsOnExactlyTheTickedCategories_andMarksSetupDone() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SharedPrefsShopPreferences(context)
        val repository = RoomCategoryRepository(database)
        val viewModel = CategorySetupViewModel(repository, prefs)
        awaitUntil { viewModel.uiState.value.loaded }
        assertTrue(viewModel.uiState.value.loaded)
        val restaurant = viewModel.uiState.value.categories.first { it.profile == ProductCategory.RESTAURANT }
        assertFalse(restaurant.id in viewModel.uiState.value.selected)

        viewModel.onToggle(restaurant.id)
        viewModel.onConfirm()
        awaitUntil { prefs.categorySetupDone.value }
        assertTrue(prefs.categorySetupDone.value)
        assertTrue(repository.getCategories().first { it.id == restaurant.id }.enabled)
    }

    @Test
    fun confirmingWithNothingToSell_asksForOne_andDoesNotFinish() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SharedPrefsShopPreferences(context)
        val viewModel = CategorySetupViewModel(RoomCategoryRepository(database), prefs)
        awaitUntil { viewModel.uiState.value.loaded }
        viewModel.uiState.value.selected.forEach { viewModel.onToggle(it) }
        viewModel.onConfirm()
        awaitUntil { viewModel.uiState.value.needsOne }
        assertTrue(viewModel.uiState.value.needsOne)
        assertFalse(prefs.categorySetupDone.value)
    }

    @Test
    fun theGate_asksTheAdminForCategories_beforeTheApp() {
        composeRule.setContent {
            AuxoTheme {
                AuthGate(
                    state = AuthState.SignedIn("Roy"),
                    categoryPrompt = true,
                    categorySetup = { Text("CATEGORIES", modifier = Modifier.testTag("fake_categories")) },
                    app = { Text("THE APP", modifier = Modifier.testTag("fake_app")) },
                )
            }
        }
        composeRule.onNodeWithTag("fake_categories").assertIsDisplayed()
        composeRule.onNodeWithTag("fake_app").assertDoesNotExist()
    }
}
