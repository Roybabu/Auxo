// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category the shop sells in. [profile] is a [com.auxo.pos.domain.model.ProductCategory] name: it decides which
 * product details, tax defaults, units and stock rules the category uses. Added in version 17.
 */
@Entity(tableName = "shop_categories")
data class ShopCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The shop's own name; null on a built-in category that has not been renamed (the app shows its translated name). */
    val name: String?,
    val profile: String,
    val enabled: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "built_in") val builtIn: Boolean,
)

/** A grouping inside a category, offered when adding a product. Products keep their own copy of the text. */
@Entity(
    tableName = "shop_subcategories",
    foreignKeys = [
        ForeignKey(
            entity = ShopCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["category_id", "name"], unique = true)],
)
data class ShopSubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
