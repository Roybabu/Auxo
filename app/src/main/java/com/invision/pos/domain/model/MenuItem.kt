package com.invision.pos.domain.model

/** One ingredient used to make a dish. */
data class RecipeLine(
    /** The ingredient's inventory variant. */
    val variantId: Long,
    /** Amount per serving, in the ingredient's base unit (grams, millilitres or pieces). */
    val quantity: Int,
)

/**
 * A dish or drink on the restaurant menu. The menu is separate from shop products:
 * dishes have no stock of their own, but can use up ingredients through a recipe.
 */
data class MenuItem(
    val id: Long = 0,
    val name: String,
    /** Menu section, such as "Starters" or "Beverages". */
    val section: String,
    val description: String? = null,
    val price: Money,
    val gstRateBasisPoints: Int = DEFAULT_GST_BASIS_POINTS,
    /** Service accounting code printed instead of an HSN code. */
    val sacCode: String? = DEFAULT_SAC,
    val dietType: DietType? = null,
    /** Unavailable dishes can't be added to a bill. */
    val available: Boolean = true,
    /** When on, selling the dish takes its recipe's ingredients out of stock. */
    val deductIngredients: Boolean = false,
    val sortOrder: Int = 0,
    val recipe: List<RecipeLine> = emptyList(),
) {
    companion object {
        /** Restaurant service is usually taxed at 5% GST. */
        const val DEFAULT_GST_BASIS_POINTS = 500

        /** SAC for restaurant and food serving services. */
        const val DEFAULT_SAC = "996331"
    }
}
