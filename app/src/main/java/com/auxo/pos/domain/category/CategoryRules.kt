package com.auxo.pos.domain.category

import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ShopCategory

/** What the admin typed for a category or subcategory name, checked against the names already in use. */
sealed interface NameCheck {
    data class Valid(val name: String) : NameCheck
    data object Blank : NameCheck
    data object TooLong : NameCheck
    data object Taken : NameCheck
}

object CategoryRules {
    const val NAME_MAX = 40

    /** Categories a new shop is offered, in the order they are listed. Restaurant starts off. */
    val builtInOrder: List<ProductCategory> = listOf(
        ProductCategory.GARMENT,
        ProductCategory.PACKAGED_FOOD,
        ProductCategory.BEVERAGE,
        ProductCategory.RESTAURANT,
    )

    /** Names are trimmed, single-spaced and compared without regard to case. [others] are the names already in use. */
    fun checkName(raw: String, others: Collection<String>): NameCheck {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        return when {
            name.isEmpty() -> NameCheck.Blank
            name.length > NAME_MAX -> NameCheck.TooLong
            others.any { it.equals(name, ignoreCase = true) } -> NameCheck.Taken
            else -> NameCheck.Valid(name)
        }
    }

    /** The restaurant menu is offered while any Restaurant category is on. */
    fun restaurantModeOn(all: List<ShopCategory>): Boolean = all.any { it.enabled && it.profile == ProductCategory.RESTAURANT }
}
