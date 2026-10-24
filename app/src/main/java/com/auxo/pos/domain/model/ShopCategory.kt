// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.model

/** A subcategory the admin has listed under a category. */
data class ShopSubcategory(val id: Long, val name: String)

/**
 * A category the shop sells in. The [profile] says how its products behave (size and colour, net quantity, expiry,
 * stock, diet marking, tax default); the admin chooses it when adding a category, so a custom category such as
 * "Sweets" can act like packaged food. Built-in categories start with a null [name] and show their translated name
 * until the shop renames them.
 */
data class ShopCategory(
    val id: Long,
    val name: String?,
    val profile: ProductCategory,
    val enabled: Boolean,
    val sortOrder: Int,
    val builtIn: Boolean,
    val subcategories: List<ShopSubcategory> = emptyList(),
)
