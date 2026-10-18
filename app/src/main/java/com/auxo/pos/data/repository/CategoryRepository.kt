package com.auxo.pos.data.repository

import androidx.room.withTransaction
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.ShopCategoryEntity
import com.auxo.pos.data.local.entity.ShopSubcategoryEntity
import com.auxo.pos.domain.category.CategoryRules
import com.auxo.pos.domain.category.NameCheck
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory
import com.auxo.pos.domain.model.ShopSubcategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

sealed interface CategoryResult {
    data class Done(val id: Long) : CategoryResult
    data class BadName(val check: NameCheck) : CategoryResult
    data object NotFound : CategoryResult

    /** Switching it off would leave the shop with nothing to sell in. */
    data object LastOne : CategoryResult

    /** Products still use it. */
    data object InUse : CategoryResult

    /** A built-in category can be switched off and renamed, but not deleted. */
    data object BuiltIn : CategoryResult
}

/**
 * The categories the shop sells in. Switching a category off only stops it being offered for new products: its
 * products, stock, invoices and reports are never touched.
 */
interface CategoryRepository {
    fun observeCategories(): Flow<List<ShopCategory>>

    suspend fun getCategories(): List<ShopCategory>

    suspend fun addCategory(name: String, profile: ProductCategory): CategoryResult

    /** A blank name on a built-in category goes back to its translated default. */
    suspend fun renameCategory(id: Long, name: String): CategoryResult

    suspend fun setEnabled(id: Long, enabled: Boolean): CategoryResult

    /** Deletes a custom category that no product uses. */
    suspend fun deleteCategory(id: Long): CategoryResult

    suspend fun addSubcategory(categoryId: Long, name: String): CategoryResult

    /** Renames the entry and the products listed under it. */
    suspend fun renameSubcategory(id: Long, name: String): CategoryResult

    suspend fun deleteSubcategory(id: Long): CategoryResult

    /** First-time setup: exactly these categories are on. At least one selling category must be chosen. */
    suspend fun applySetup(enabledIds: Set<Long>): CategoryResult
}

class RoomCategoryRepository(private val database: AuxoDatabase) : CategoryRepository {
    private val dao = database.categoryDao()

    override fun observeCategories(): Flow<List<ShopCategory>> = flow {
        database.withTransaction { seedIfEmpty() }
        emitAll(combine(dao.observeCategories(), dao.observeSubcategories()) { categories, subs -> assemble(categories, subs) })
    }

    override suspend fun getCategories(): List<ShopCategory> = database.withTransaction {
        seedIfEmpty()
        assemble(dao.categories(), dao.subcategories())
    }

    /** A database made fresh (not upgraded) starts with the built-in categories; Restaurant is off until chosen. */
    private suspend fun seedIfEmpty() {
        if (dao.categoryCount() > 0) return
        CategoryRules.builtInOrder.forEachIndexed { index, profile ->
            dao.insertCategory(
                ShopCategoryEntity(
                    name = null, profile = profile.name, enabled = profile != ProductCategory.RESTAURANT,
                    sortOrder = index, builtIn = true,
                ),
            )
        }
    }

    override suspend fun addCategory(name: String, profile: ProductCategory): CategoryResult = database.withTransaction {
        seedIfEmpty()
        val names = dao.categories().map { it.name ?: defaultName(it.profile) }
        when (val check = CategoryRules.checkName(name, names)) {
            is NameCheck.Valid -> CategoryResult.Done(
                dao.insertCategory(
                    ShopCategoryEntity(name = check.name, profile = profile.name, enabled = true, sortOrder = dao.maxSortOrder() + 1, builtIn = false),
                ),
            )
            else -> CategoryResult.BadName(check)
        }
    }

    override suspend fun renameCategory(id: Long, name: String): CategoryResult = database.withTransaction {
        val row = dao.category(id) ?: return@withTransaction CategoryResult.NotFound
        if (name.isBlank() && row.builtIn) {
            dao.rename(id, null)
            return@withTransaction CategoryResult.Done(id)
        }
        val others = dao.categories().filter { it.id != id }.map { it.name ?: defaultName(it.profile) }
        when (val check = CategoryRules.checkName(name, others)) {
            is NameCheck.Valid -> {
                dao.rename(id, check.name)
                CategoryResult.Done(id)
            }
            else -> CategoryResult.BadName(check)
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean): CategoryResult = database.withTransaction {
        val row = dao.category(id) ?: return@withTransaction CategoryResult.NotFound
        if (!enabled && row.enabled && sellingEnabledCount() <= 1) return@withTransaction CategoryResult.LastOne
        dao.setEnabled(id, enabled)
        CategoryResult.Done(id)
    }

    override suspend fun deleteCategory(id: Long): CategoryResult = database.withTransaction {
        val row = dao.category(id) ?: return@withTransaction CategoryResult.NotFound
        when {
            row.builtIn -> CategoryResult.BuiltIn
            dao.productCount(id) > 0 -> CategoryResult.InUse
            row.enabled && sellingEnabledCount() <= 1 -> CategoryResult.LastOne
            else -> {
                dao.deleteCategory(id)
                CategoryResult.Done(id)
            }
        }
    }

    override suspend fun addSubcategory(categoryId: Long, name: String): CategoryResult = database.withTransaction {
        dao.category(categoryId) ?: return@withTransaction CategoryResult.NotFound
        val existing = dao.subcategories().filter { it.categoryId == categoryId }.map { it.name }
        when (val check = CategoryRules.checkName(name, existing)) {
            is NameCheck.Valid -> CategoryResult.Done(
                dao.insertSubcategory(ShopSubcategoryEntity(categoryId = categoryId, name = check.name, sortOrder = dao.maxSubcategoryOrder(categoryId) + 1)),
            )
            else -> CategoryResult.BadName(check)
        }
    }

    override suspend fun renameSubcategory(id: Long, name: String): CategoryResult = database.withTransaction {
        val row = dao.subcategory(id) ?: return@withTransaction CategoryResult.NotFound
        val others = dao.subcategories().filter { it.categoryId == row.categoryId && it.id != id }.map { it.name }
        when (val check = CategoryRules.checkName(name, others)) {
            is NameCheck.Valid -> {
                dao.renameSubcategory(id, check.name)
                dao.renameSubcategoryOnProducts(row.categoryId, row.name, check.name)
                CategoryResult.Done(id)
            }
            else -> CategoryResult.BadName(check)
        }
    }

    override suspend fun deleteSubcategory(id: Long): CategoryResult = database.withTransaction {
        dao.subcategory(id) ?: return@withTransaction CategoryResult.NotFound
        // Products keep the text they were saved with, so nothing is lost.
        dao.deleteSubcategory(id)
        CategoryResult.Done(id)
    }

    override suspend fun applySetup(enabledIds: Set<Long>): CategoryResult = database.withTransaction {
        seedIfEmpty()
        val rows = dao.categories()
        val chosen = rows.filter { it.id in enabledIds }
        if (chosen.isEmpty()) return@withTransaction CategoryResult.LastOne
        rows.forEach { dao.setEnabled(it.id, it.id in enabledIds) }
        CategoryResult.Done(0)
    }

    private suspend fun sellingEnabledCount(): Int = dao.categories().count { it.enabled }

    private fun defaultName(profile: String): String = profile.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun profileOf(name: String): ProductCategory = ProductCategory.entries.firstOrNull { it.name == name } ?: ProductCategory.GARMENT

    private fun assemble(categories: List<ShopCategoryEntity>, subs: List<ShopSubcategoryEntity>): List<ShopCategory> {
        val byCategory = subs.groupBy { it.categoryId }
        return categories.map {
            ShopCategory(
                id = it.id, name = it.name, profile = profileOf(it.profile), enabled = it.enabled,
                sortOrder = it.sortOrder, builtIn = it.builtIn,
                subcategories = byCategory[it.id].orEmpty().map { s -> ShopSubcategory(s.id, s.name) },
            )
        }
    }
}
