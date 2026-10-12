package com.auxo.pos.domain.inventory

import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductVariant

/** One variant offered in an item picker, such as when adding lines to a purchase order. */
data class PickableItem(val product: Product, val variant: ProductVariant) {
    /** Product name with size, colour or pack size, e.g. "Cotton T-Shirt · M · Navy". */
    val label: String
        get() = listOf(product.name, variant.attributesLabel).filter { it.isNotBlank() }.joinToString(" · ")
}

object ItemSearch {
    const val DEFAULT_LIMIT = 30

    /**
     * Variants of [products] matching [query] by name, brand, SKU, barcode, size or
     * colour (all of them when blank), limited to products [include] accepts.
     * A barcode match returns just that variant.
     */
    fun search(
        products: List<Product>,
        query: String,
        include: (Product) -> Boolean = { true },
        limit: Int = DEFAULT_LIMIT,
    ): List<PickableItem> {
        val text = query.trim()
        val candidates = products.filter(include)
        if (text.isNotEmpty()) {
            val exact = candidates.firstNotNullOfOrNull { product ->
                product.variants.firstOrNull { it.barcode == text }?.let { PickableItem(product, it) }
            }
            if (exact != null) return listOf(exact)
        }
        return candidates
            .asSequence()
            .filter { text.isEmpty() || InventoryFilter.matchesText(it, text) }
            .sortedBy { it.name.lowercase() }
            .flatMap { product -> product.variants.asSequence().map { PickableItem(product, it) } }
            .take(limit)
            .toList()
    }
}
