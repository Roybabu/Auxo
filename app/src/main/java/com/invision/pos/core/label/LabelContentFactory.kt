package com.invision.pos.core.label

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant

object LabelContentFactory {

    fun forVariant(
        product: Product,
        variant: ProductVariant,
        showMrp: Boolean,
        formatMoney: (Money) -> String,
    ): LabelContent = LabelContent(
        title = product.name,
        subtitle = listOfNotNull(product.brand, variant.attributesLabel.ifEmpty { null })
            .joinToString(" · ")
            .ifEmpty { null },
        price = formatMoney(variant.sellingPrice),
        mrp = if (showMrp) variant.mrp?.let(formatMoney) else null,
        barcode = variant.barcode,
    )

    /** One label per copy, in variant order. Variants with no copies are skipped. */
    fun build(
        product: Product,
        copies: Map<Long, Int>,
        showMrp: Boolean,
        formatMoney: (Money) -> String,
    ): List<LabelContent> = product.variants.flatMap { variant ->
        val count = copies[variant.id] ?: 0
        if (count <= 0) {
            emptyList()
        } else {
            val content = forVariant(product, variant, showMrp, formatMoney)
            List(count) { content }
        }
    }
}
