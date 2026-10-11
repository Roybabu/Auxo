package com.invision.pos.domain.menu

import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.RecipeLine
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.domain.model.formatRatePercent
import com.invision.pos.domain.model.parseRatePercent

enum class MenuField { NAME, SECTION, DESCRIPTION, PRICE, GST_RATE, SAC, RECIPE }

enum class MenuError { REQUIRED, TOO_LONG, INVALID_PRICE, INVALID_GST_RATE, INVALID_SAC, RECIPE_NEEDED, INVALID_QUANTITY, DUPLICATE_INGREDIENT }

/** One recipe row being edited. Quantity is per serving, in the ingredient's entry unit (kg, L or pcs). */
data class RecipeLineForm(
    val key: String,
    val variantId: Long,
    val ingredientName: String,
    val stockUnit: StockUnit,
    val quantity: String = "",
)

data class MenuItemForm(
    val name: String = "",
    val section: String = "",
    val description: String = "",
    val price: String = "",
    val gstRate: String = formatRatePercent(MenuItem.DEFAULT_GST_BASIS_POINTS),
    val sacCode: String = MenuItem.DEFAULT_SAC,
    val dietType: DietType? = null,
    val available: Boolean = true,
    val deductIngredients: Boolean = false,
    val recipe: List<RecipeLineForm> = emptyList(),
)

sealed interface MenuValidation {
    data class Valid(val item: MenuItem) : MenuValidation
    data class Invalid(val errors: Map<MenuField, MenuError>, val recipeErrors: Map<String, MenuError>) : MenuValidation
}

object MenuValidator {
    const val NAME_MAX = 60
    const val SECTION_MAX = 30
    const val DESCRIPTION_MAX = 200
    private val SAC = Regex("""\d{4}|\d{6}|\d{8}""")

    fun validate(form: MenuItemForm, existing: MenuItem? = null): MenuValidation {
        val errors = mutableMapOf<MenuField, MenuError>()
        val recipeErrors = mutableMapOf<String, MenuError>()
        val name = form.name.trim()
        when {
            name.isEmpty() -> errors[MenuField.NAME] = MenuError.REQUIRED
            name.length > NAME_MAX -> errors[MenuField.NAME] = MenuError.TOO_LONG
        }
        val section = form.section.trim()
        when {
            section.isEmpty() -> errors[MenuField.SECTION] = MenuError.REQUIRED
            section.length > SECTION_MAX -> errors[MenuField.SECTION] = MenuError.TOO_LONG
        }
        if (form.description.trim().length > DESCRIPTION_MAX) errors[MenuField.DESCRIPTION] = MenuError.TOO_LONG
        val price = Money.parseOrNull(form.price)?.takeIf { !it.isZero }
        when {
            form.price.isBlank() -> errors[MenuField.PRICE] = MenuError.REQUIRED
            price == null -> errors[MenuField.PRICE] = MenuError.INVALID_PRICE
        }
        val gst = parseRatePercent(form.gstRate)
        if (gst == null) errors[MenuField.GST_RATE] = MenuError.INVALID_GST_RATE
        val sac = form.sacCode.trim()
        if (sac.isNotEmpty() && !SAC.matches(sac)) errors[MenuField.SAC] = MenuError.INVALID_SAC

        val seen = mutableSetOf<Long>()
        val recipe = form.recipe.mapNotNull { line ->
            val quantity = line.stockUnit.parse(line.quantity)
            when {
                !seen.add(line.variantId) -> recipeErrors[line.key] = MenuError.DUPLICATE_INGREDIENT
                quantity == null -> recipeErrors[line.key] = MenuError.INVALID_QUANTITY
            }
            if (quantity != null && line.key !in recipeErrors) RecipeLine(line.variantId, quantity) else null
        }
        if (form.deductIngredients && form.recipe.isEmpty()) errors[MenuField.RECIPE] = MenuError.RECIPE_NEEDED

        if (errors.isNotEmpty() || recipeErrors.isNotEmpty() || price == null || gst == null) {
            return MenuValidation.Invalid(errors, recipeErrors)
        }
        return MenuValidation.Valid(
            MenuItem(
                id = existing?.id ?: 0,
                name = name,
                section = section,
                description = form.description.trim().ifEmpty { null },
                price = price,
                gstRateBasisPoints = gst,
                sacCode = sac.ifEmpty { null },
                dietType = form.dietType,
                available = form.available,
                deductIngredients = form.deductIngredients,
                sortOrder = existing?.sortOrder ?: 0,
                recipe = recipe,
            ),
        )
    }
}
