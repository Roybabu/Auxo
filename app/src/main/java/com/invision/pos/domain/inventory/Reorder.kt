package com.invision.pos.domain.inventory

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.PurchaseOrderLine

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

object ReorderPlanner {

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
