// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.data.repository.CategoryRepository
import com.auxo.pos.data.repository.CategoryResult
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import com.auxo.pos.domain.model.StoreSettings
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.keepActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FssaiHintViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeCategories(initial: List<ShopCategory>) : CategoryRepository {
        val categories = MutableStateFlow(initial)
        override fun observeCategories(): Flow<List<ShopCategory>> = categories
        override suspend fun getCategories() = categories.value
        override suspend fun addCategory(name: String, profile: ProductCategory): CategoryResult = error("unused")
        override suspend fun renameCategory(id: Long, name: String): CategoryResult = error("unused")
        override suspend fun setEnabled(id: Long, enabled: Boolean): CategoryResult = error("unused")
        override suspend fun deleteCategory(id: Long): CategoryResult = error("unused")
        override suspend fun addSubcategory(categoryId: Long, name: String): CategoryResult = error("unused")
        override suspend fun renameSubcategory(id: Long, name: String): CategoryResult = error("unused")
        override suspend fun deleteSubcategory(id: Long): CategoryResult = error("unused")
        override suspend fun applySetup(enabledIds: Set<Long>): CategoryResult = error("unused")
    }

    private class FakeStore(initial: Boolean = false) : FssaiHintStore {
        val flag = MutableStateFlow(initial)
        override val dismissed: Flow<Boolean> get() = flag
        override fun dismiss() {
            flag.value = true
        }
    }

    private fun category(profile: ProductCategory, enabled: Boolean = true) =
        ShopCategory(id = profile.ordinal + 1L, name = null, profile = profile, enabled = enabled, sortOrder = 0, builtIn = true)

    @Test
    fun showsForFoodShopsWithoutANumber_andHidesOnceSetOrDismissed() = runTest {
        val settings = FakeSettingsRepository(StoreSettings())
        val store = FakeStore()
        val viewModel = FssaiHintViewModel(settings, FakeCategories(listOf(category(ProductCategory.PACKAGED_FOOD))), store)
        keepActive(viewModel.visible)
        assertTrue(viewModel.visible.value)

        settings.settings.value = StoreSettings(fssai = "12345678901234")
        assertFalse(viewModel.visible.value)

        settings.settings.value = StoreSettings()
        assertTrue(viewModel.visible.value)
        viewModel.dismiss()
        assertFalse(viewModel.visible.value)
    }

    @Test
    fun staysHidden_forAClothingOnlyShop_orWhenFoodCategoriesAreOff() = runTest {
        val categories = FakeCategories(listOf(category(ProductCategory.GARMENT), category(ProductCategory.BEVERAGE, enabled = false)))
        val viewModel = FssaiHintViewModel(FakeSettingsRepository(StoreSettings()), categories, FakeStore())
        keepActive(viewModel.visible)
        assertFalse(viewModel.visible.value)

        categories.categories.value = listOf(category(ProductCategory.RESTAURANT))
        assertTrue(viewModel.visible.value)
    }
}
