package com.auxo.pos.domain.inventory

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderLine
import com.auxo.pos.domain.model.Supplier

/** A variant at or below its low-stock level, with how much to order. */
data class ReorderSuggestion(
    val product: Product,
    val variant: ProductVariant,
    /** In the product's base unit. */
    val suggestedQuantity: Int,
) {
    val isOutOfStock: Boolean get() = variant.stockQuantity <= 0
    val supplierId: Long? get() = product.preferredSupplierId
}

/** Reorder items that go to the same supplier ([supplierId] null: no preferred supplier). */
data class ReorderGroup(val supplierId: Long?, val items: List<ReorderSuggestion>)

object ReorderPlanner {

    /**
     * [suggestions] grouped by preferred supplier: suppliers by name, then the items with no supplier last.
     * Items keep their order (out of stock first) inside a group. A supplier that is not in [suppliers]
     * (hidden, say) is treated as no supplier.
     */
    fun groups(suggestions: List<ReorderSuggestion>, suppliers: List<Supplier>): List<ReorderGroup> {
        val known = suppliers.associateBy { it.id }
        val grouped = suggestions.groupBy { s -> s.supplierId?.takeIf { it in known } }
        val named = grouped.filterKeys { it != null }.entries
            .sortedBy { known.getValue(it.key!!).name.lowercase() }
            .map { ReorderGroup(it.key, it.value) }
        return named + listOfNotNull(grouped[null]?.let { ReorderGroup(null, it) })
    }

    /**
     * Applies quantities the person typed (in the entry unit: pcs, kg or L), keyed by variant id. A blank or
     * invalid text keeps the suggestion; use [invalidQuantities] to find those.
     */
    fun withQuantities(suggestions: List<ReorderSuggestion>, texts: Map<Long, String>): List<ReorderSuggestion> =
        suggestions.map { s ->
            val typed = texts[s.variant.id]?.let { s.product.stockUnit.parse(it) }
            if (typed != null) s.copy(suggestedQuantity = typed) else s
        }

    /** Variants whose typed quantity can't be used (not a number, zero, too precise or too large). */
    fun invalidQuantities(suggestions: List<ReorderSuggestion>, texts: Map<Long, String>): Set<Long> =
        suggestions.filter { s -> texts[s.variant.id]?.let { s.product.stockUnit.parse(it) == null } == true }
            .map { it.variant.id }.toSet()

    /**
     * Variants that are out of stock or low (1 unit or fewer, or at or below the level set on the item).
     * Out-of-stock items come first, then by name.
     */
    fun suggestions(products: List<Product>): List<ReorderSuggestion> = products.flatMap { product ->
        product.variants
            .filter { it.needsRestock }
            .map { ReorderSuggestion(product, it, suggestedQuantity(it)) }
    }.sortedWith(compareBy<ReorderSuggestion>({ !it.isOutOfStock }, { it.product.name.lowercase() }, { it.variant.id }))

    /**
     * The reorder quantity when one is set; otherwise enough to reach twice the
     * low-stock level (at least one).
     */
    fun suggestedQuantity(variant: ProductVariant): Int {
        if (variant.reorderQuantity > 0) return variant.reorderQuantity
        val target = (variant.lowStockThreshold.toLong() * 2).coerceAtLeast(1L)
        return (target - variant.stockQuantity.coerceAtLeast(0)).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * One draft order per supplier for [selected] items, at their cost price.
     * Items without a preferred supplier go to [fallbackSupplierId], or are left
     * out if it is null.
     */
    fun draftOrders(selected: List<ReorderSuggestion>, fallbackSupplierId: Long?): List<PurchaseOrder> =
        selected
            .mapNotNull { suggestion -> (suggestion.supplierId ?: fallbackSupplierId)?.let { it to suggestion } }
            .groupBy({ it.first }, { it.second })
            .map { (supplierId, items) ->
                PurchaseOrder(
                    supplierId = supplierId,
                    lines = items.map { item ->
                        PurchaseOrderLine(
                            variantId = item.variant.id,
                            productName = item.product.name,
                            variantLabel = item.variant.attributesLabel,
                            stockUnit = item.product.stockUnit,
                            quantityOrdered = item.suggestedQuantity,
                            unitCost = item.variant.costPrice ?: Money.ZERO,
                        )
                    },
                )
            }
}
