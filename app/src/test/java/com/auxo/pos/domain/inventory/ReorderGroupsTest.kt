package com.auxo.pos.domain.inventory

import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderGroupsTest {

    private val suppliers = listOf(Supplier(id = 1, name = "Sample Textiles"), Supplier(id = 2, name = "drinks Co"))

    // T-shirt L is low (supplier 1); juice is out of stock (supplier 2); flour is low (no supplier).
    private val catalogue = listOf(
        TestData.tShirt().copy(preferredSupplierId = 1),
        TestData.juice(stock = 0).copy(preferredSupplierId = 2),
        TestData.flour(grams = 500),
    )
    private val suggestions = ReorderPlanner.suggestions(catalogue)

    @Test
    fun groups_areBySupplierName_andItemsWithoutOneComeLast() {
        val groups = ReorderPlanner.groups(suggestions, suppliers)
        assertEquals(listOf<Long?>(2L, 1L, null), groups.map { it.supplierId })
        assertEquals(listOf("Mango Juice"), groups[0].items.map { it.product.name })
        assertEquals(listOf("Cotton T-Shirt"), groups[1].items.map { it.product.name })
        assertEquals(listOf("Wheat Flour"), groups[2].items.map { it.product.name })
    }

    @Test
    fun aSupplierThatIsNotListed_isTreatedAsNoSupplier() {
        val groups = ReorderPlanner.groups(suggestions, suppliers.filter { it.id == 1L })
        assertEquals(listOf<Long?>(1L, null), groups.map { it.supplierId })
        assertEquals(setOf("Mango Juice", "Wheat Flour"), groups.last().items.map { it.product.name }.toSet())
    }

    @Test
    fun nothingToOrder_meansNoGroups() {
        assertTrue(ReorderPlanner.groups(emptyList(), suppliers).isEmpty())
    }

    @Test
    fun typedQuantities_replaceTheSuggestion_inTheEntryUnit() {
        val flour = suggestions.first { it.product.name == "Wheat Flour" }
        val juice = suggestions.first { it.product.name == "Mango Juice" }
        assertEquals(1_500, flour.suggestedQuantity) // up to twice the 1 kg level
        val typed = ReorderPlanner.withQuantities(suggestions, mapOf(flour.variant.id to "2.5", juice.variant.id to "24"))
        assertEquals(2_500, typed.first { it.variant.id == flour.variant.id }.suggestedQuantity)
        assertEquals(24, typed.first { it.variant.id == juice.variant.id }.suggestedQuantity)
        // Everything else is untouched.
        assertEquals(suggestions.filter { it.variant.id !in setOf(flour.variant.id, juice.variant.id) }, typed.filter { it.variant.id !in setOf(flour.variant.id, juice.variant.id) })
    }

    @Test
    fun unusableQuantities_keepTheSuggestion_andAreReported() {
        val flour = suggestions.first { it.product.name == "Wheat Flour" }
        val juice = suggestions.first { it.product.name == "Mango Juice" }
        val texts = mapOf(flour.variant.id to "abc", juice.variant.id to "0")
        assertEquals(suggestions, ReorderPlanner.withQuantities(suggestions, texts))
        assertEquals(setOf(flour.variant.id, juice.variant.id), ReorderPlanner.invalidQuantities(suggestions, texts))
        // Pieces are whole numbers; weights take up to three decimals.
        assertEquals(setOf(juice.variant.id), ReorderPlanner.invalidQuantities(suggestions, mapOf(juice.variant.id to "1.5", flour.variant.id to "1.250")))
    }

    @Test
    fun theDraftOrdersCarryTheTypedQuantities_oneOrderPerSupplier() {
        val juice = suggestions.first { it.product.name == "Mango Juice" }
        val typed = ReorderPlanner.withQuantities(suggestions, mapOf(juice.variant.id to "36"))
        val orders = ReorderPlanner.draftOrders(typed, fallbackSupplierId = 1)
        assertEquals(setOf(1L, 2L), orders.map { it.supplierId }.toSet())
        assertEquals(36, orders.first { it.supplierId == 2L }.lines.single().quantityOrdered)
        // The flour has no supplier, so it joins the fallback supplier's order.
        assertEquals(setOf("Cotton T-Shirt", "Wheat Flour"), orders.first { it.supplierId == 1L }.lines.map { it.productName }.toSet())
    }
}
