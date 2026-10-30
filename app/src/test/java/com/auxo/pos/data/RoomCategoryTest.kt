// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.CategoryResult
import com.auxo.pos.data.repository.RoomCategoryRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.category.NameCheck
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The shop's category list: set up, edited, and never allowed to lose products. */
@RunWith(AndroidJUnit4::class)
class RoomCategoryTest {

    private lateinit var database: AuxoDatabase
    private lateinit var categories: RoomCategoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        categories = RoomCategoryRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aNewShop_startsWithTheBuiltInCategories_andRestaurantOff() = runTest {
        val all = categories.getCategories()
        assertEquals(
            listOf(
                ProductCategory.GARMENT, ProductCategory.FOOTWEAR, ProductCategory.ACCESSORIES, ProductCategory.PACKAGED_FOOD, ProductCategory.BEVERAGE,
                ProductCategory.GENERAL, ProductCategory.ELECTRONICS, ProductCategory.PHARMACY, ProductCategory.COSMETICS,
                ProductCategory.LOOSE, ProductCategory.JEWELLERY, ProductCategory.RESTAURANT,
            ),
            all.map { it.profile },
        )
        assertTrue(all.all { it.builtIn && it.name == null })
        // Restaurant and the six newer categories start off; the owner switches on what the shop sells.
        assertEquals(listOf(true, true, true, true, true, false, false, false, false, false, false, false), all.map { it.enabled })
    }

    @Test
    fun setup_turnsOnExactlyTheChosenCategories_andNeedsOneToSellIn() = runTest {
        val all = categories.getCategories()
        val restaurant = all.first { it.profile == ProductCategory.RESTAURANT }
        assertTrue(categories.applySetup(setOf(restaurant.id)) is CategoryResult.Done)
        assertEquals(listOf(restaurant.id), categories.getCategories().filter { it.enabled }.map { it.id })

        // At least one category has to stay on.
        assertEquals(CategoryResult.LastOne, categories.applySetup(emptySet()))
        assertEquals(listOf(restaurant.id), categories.getCategories().filter { it.enabled }.map { it.id })
    }

    @Test
    fun aCustomCategory_isListedAtOnce_withItsBehaviour() = runTest {
        val id = (categories.addCategory("  Sweets  ", ProductCategory.PACKAGED_FOOD) as CategoryResult.Done).id
        val sweets = categories.observeCategories().first().single { it.id == id }
        assertEquals("Sweets", sweets.name)
        assertEquals(ProductCategory.PACKAGED_FOOD, sweets.profile)
        assertTrue(sweets.enabled)
        assertFalse(sweets.builtIn)
    }

    @Test
    fun names_mustBeFilledShortAndUnique() = runTest {
        assertEquals(CategoryResult.BadName(NameCheck.Blank), categories.addCategory("  ", ProductCategory.GARMENT))
        assertEquals(CategoryResult.BadName(NameCheck.TooLong), categories.addCategory("x".repeat(41), ProductCategory.GARMENT))
        categories.addCategory("Sweets", ProductCategory.PACKAGED_FOOD)
        assertEquals(CategoryResult.BadName(NameCheck.Taken), categories.addCategory("sweets", ProductCategory.BEVERAGE))
    }

    @Test
    fun aNewCategory_cannotTakeABuiltInCategorysName_inAnyCase() = runTest {
        // A built-in category with no name of its own is shown under its default name, and that counts as taken.
        assertEquals(CategoryResult.BadName(NameCheck.Taken), categories.addCategory("footwear", ProductCategory.FOOTWEAR))
        assertEquals(CategoryResult.BadName(NameCheck.Taken), categories.addCategory("  CLOTHING ", ProductCategory.GARMENT))
        assertEquals(CategoryResult.BadName(NameCheck.Taken), categories.addCategory("Packaged   Food", ProductCategory.GENERAL))
    }

    @Test
    fun renaming_toAnotherCategorysName_isRefused_butKeepingYourOwnNameInAnyCaseIsFine() = runTest {
        val sweets = (categories.addCategory("Sweets", ProductCategory.PACKAGED_FOOD) as CategoryResult.Done).id
        val garment = categories.getCategories().first { it.profile == ProductCategory.GARMENT }

        assertEquals(CategoryResult.BadName(NameCheck.Taken), categories.renameCategory(garment.id, "sweets"))
        assertEquals(CategoryResult.BadName(NameCheck.Taken), categories.renameCategory(sweets, "Footwear"))
        // The category's own name is not "another" category's name, so changing its case is allowed.
        assertEquals(CategoryResult.Done(sweets), categories.renameCategory(sweets, "SWEETS"))
        assertEquals(1, categories.getCategories().count { it.name.equals("sweets", ignoreCase = true) })
    }

    @Test
    fun aBuiltInCategory_canBeRenamedAndPutBack_butNotDeleted() = runTest {
        val garment = categories.getCategories().first { it.profile == ProductCategory.GARMENT }
        categories.renameCategory(garment.id, "Fashion")
        assertEquals("Fashion", categories.getCategories().first { it.id == garment.id }.name)
        categories.renameCategory(garment.id, "")
        assertEquals(null, categories.getCategories().first { it.id == garment.id }.name)
        assertEquals(CategoryResult.BuiltIn, categories.deleteCategory(garment.id))
    }

    @Test
    fun switchingACategoryOff_keepsItsProducts_andTheLastOneCannotBeSwitchedOff() = runTest {
        val inventory = RoomInventoryRepository(database, TestData.clock)
        val garment = categories.getCategories().first { it.profile == ProductCategory.GARMENT }
        val shirt = TestData.tShirt().let { it.copy(id = 0, categoryId = garment.id, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val saved = (inventory.saveProduct(shirt) as SaveProductResult.Saved).productId

        assertTrue(categories.setEnabled(garment.id, false) is CategoryResult.Done)
        assertEquals(garment.id, inventory.getProduct(saved)!!.categoryId)

        val sellable = categories.getCategories().filter { it.enabled }
        sellable.dropLast(1).forEach { assertTrue(categories.setEnabled(it.id, false) is CategoryResult.Done) }
        assertEquals(CategoryResult.LastOne, categories.setEnabled(sellable.last().id, false))
    }

    @Test
    fun aCustomCategoryWithProducts_cannotBeDeleted() = runTest {
        val inventory = RoomInventoryRepository(database, TestData.clock)
        val id = (categories.addCategory("Gifts", ProductCategory.GARMENT) as CategoryResult.Done).id
        val shirt = TestData.tShirt().let { it.copy(id = 0, categoryId = id, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        inventory.saveProduct(shirt)
        assertEquals(CategoryResult.InUse, categories.deleteCategory(id))
        val empty = (categories.addCategory("Empty", ProductCategory.GARMENT) as CategoryResult.Done).id
        assertTrue(categories.deleteCategory(empty) is CategoryResult.Done)
    }

    @Test
    fun subcategories_areAddedRenamedWithTheirProducts_andRemovedWithoutLosingThem() = runTest {
        val inventory = RoomInventoryRepository(database, TestData.clock)
        val restaurant = categories.getCategories().first { it.profile == ProductCategory.RESTAURANT }
        val sub = (categories.addSubcategory(restaurant.id, "Starters") as CategoryResult.Done).id
        assertEquals(CategoryResult.BadName(NameCheck.Taken), categories.addSubcategory(restaurant.id, "starters"))
        val dish = TestData.dish(subcategory = "Starters").let { it.copy(id = 0, categoryId = restaurant.id, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val productId = (inventory.saveProduct(dish) as SaveProductResult.Saved).productId

        categories.renameSubcategory(sub, "Small plates")
        assertEquals("Small plates", inventory.getProduct(productId)!!.subcategory)
        assertEquals(listOf("Small plates"), categories.getCategories().first { it.id == restaurant.id }.subcategories.map { it.name })

        categories.deleteSubcategory(sub)
        assertEquals("Small plates", inventory.getProduct(productId)!!.subcategory)
        assertTrue(categories.getCategories().first { it.id == restaurant.id }.subcategories.isEmpty())
    }
}
