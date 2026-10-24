// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.auxo.pos.data.local.entity.ShopCategoryEntity
import com.auxo.pos.data.local.entity.ShopSubcategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM shop_categories ORDER BY sort_order, id")
    fun observeCategories(): Flow<List<ShopCategoryEntity>>

    @Query("SELECT * FROM shop_subcategories ORDER BY sort_order, name COLLATE NOCASE, id")
    fun observeSubcategories(): Flow<List<ShopSubcategoryEntity>>

    @Query("SELECT * FROM shop_categories ORDER BY sort_order, id")
    suspend fun categories(): List<ShopCategoryEntity>

    @Query("SELECT * FROM shop_subcategories ORDER BY sort_order, name COLLATE NOCASE, id")
    suspend fun subcategories(): List<ShopSubcategoryEntity>

    @Query("SELECT * FROM shop_categories WHERE id = :id")
    suspend fun category(id: Long): ShopCategoryEntity?

    @Query("SELECT COUNT(*) FROM shop_categories")
    suspend fun categoryCount(): Int

    /** The built-in category of a kind, used when a product is saved without choosing one. */
    @Query("SELECT id FROM shop_categories WHERE profile = :profile AND built_in = 1 ORDER BY sort_order LIMIT 1")
    suspend fun builtInFor(profile: String): Long?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM shop_categories")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM products WHERE category_id = :id")
    suspend fun productCount(id: Long): Int

    @Insert
    suspend fun insertCategory(category: ShopCategoryEntity): Long

    @Query("UPDATE shop_categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE shop_categories SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM shop_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Insert
    suspend fun insertSubcategory(subcategory: ShopSubcategoryEntity): Long

    @Query("SELECT * FROM shop_subcategories WHERE id = :id")
    suspend fun subcategory(id: Long): ShopSubcategoryEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM shop_subcategories WHERE category_id = :categoryId")
    suspend fun maxSubcategoryOrder(categoryId: Long): Int

    @Query("UPDATE shop_subcategories SET name = :name WHERE id = :id")
    suspend fun renameSubcategory(id: Long, name: String)

    @Query("DELETE FROM shop_subcategories WHERE id = :id")
    suspend fun deleteSubcategory(id: Long)

    /** Products that name this subcategory as text keep it; renaming the list entry renames them too. */
    @Query("UPDATE products SET subcategory = :newName WHERE category_id = :categoryId AND subcategory = :oldName")
    suspend fun renameSubcategoryOnProducts(categoryId: Long, oldName: String, newName: String)
}
