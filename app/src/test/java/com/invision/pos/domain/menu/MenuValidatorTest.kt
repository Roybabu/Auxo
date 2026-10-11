package com.invision.pos.domain.menu

import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.RecipeLine
import com.invision.pos.domain.model.StockUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuValidatorTest {

    private val valid = MenuItemForm(
        name = " Masala Dosa ",
        section = "Breakfast",
        price = "120",
        dietType = DietType.VEG,
        deductIngredients = true,
        recipe = listOf(
            RecipeLineForm("a", variantId = 41, ingredientName = "Rice batter", stockUnit = StockUnit.GRAM, quantity = "0.15"),
            RecipeLineForm("b", variantId = 51, ingredientName = "Milk", stockUnit = StockUnit.MILLILITRE, quantity = "0.05"),
        ),
    )

    @Test
    fun validDish_convertsRecipeToBaseUnits_withDefaultsForRestaurantGst() {
        val item = (MenuValidator.validate(valid) as MenuValidation.Valid).item
        assertEquals("Masala Dosa", item.name)
        assertEquals(Money(12_000), item.price)
        assertEquals(MenuItem.DEFAULT_GST_BASIS_POINTS, item.gstRateBasisPoints)
        assertEquals(MenuItem.DEFAULT_SAC, item.sacCode)
        assertEquals(listOf(RecipeLine(41, 150), RecipeLine(51, 50)), item.recipe)
    }

    @Test
    fun invalidDish_reportsFieldAndRecipeErrors() {
        val form = valid.copy(
            name = "",
            section = "x".repeat(31),
            price = "0",
            gstRate = "abc",
            sacCode = "12345",
            recipe = valid.recipe + valid.recipe[0].copy(key = "c") + RecipeLineForm("d", 61, "Salt", StockUnit.GRAM, "0.0001"),
        )
        val result = MenuValidator.validate(form) as MenuValidation.Invalid
        assertEquals(MenuError.REQUIRED, result.errors[MenuField.NAME])
        assertEquals(MenuError.TOO_LONG, result.errors[MenuField.SECTION])
        assertEquals(MenuError.INVALID_PRICE, result.errors[MenuField.PRICE])
        assertEquals(MenuError.INVALID_GST_RATE, result.errors[MenuField.GST_RATE])
        assertEquals(MenuError.INVALID_SAC, result.errors[MenuField.SAC])
        assertEquals(MenuError.DUPLICATE_INGREDIENT, result.recipeErrors["c"])
        assertEquals(MenuError.INVALID_QUANTITY, result.recipeErrors["d"])
    }

    @Test
    fun deductingIngredients_needsARecipe() {
        val result = MenuValidator.validate(valid.copy(recipe = emptyList())) as MenuValidation.Invalid
        assertEquals(MenuError.RECIPE_NEEDED, result.errors[MenuField.RECIPE])
        // Without deduction a dish needs no recipe.
        val plain = MenuValidator.validate(valid.copy(recipe = emptyList(), deductIngredients = false, sacCode = "")) as MenuValidation.Valid
        assertNull(plain.item.sacCode)
    }
}
