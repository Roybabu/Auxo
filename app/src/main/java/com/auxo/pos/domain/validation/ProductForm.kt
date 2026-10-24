// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.validation

import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.formatRatePercent
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.StockUnit
import java.time.LocalDate

/** Editable values for one variant row, kept as text until validated. */
data class VariantForm(
    /** Stable key for UI lists and error lookup. */
    val key: String,
    /** 0 for a variant that has not been saved yet. */
    val id: Long = 0,
    val size: String = "",
    val colour: String = "",
    val netQuantity: String = "",
    val unit: QuantityUnit? = null,
    val sellingPrice: String = "",
    val mrp: String = "",
    val costPrice: String = "",
    /** Opening stock. Only editable for new variants; saved ones change via stock adjustments. */
    val openingStock: String = "",
    val lowStockThreshold: String = "",
    val barcode: String = "",
    val sku: String = "",
    val batchNumber: String = "",
    val expiryDate: LocalDate? = null,
    /** Stock on hand for a saved variant, null for new ones. */
    val currentStock: Int? = null,
    /** Quantity to order when restocking; blank lets reorder suggestions work it out. */
    val reorderQuantity: String = "",
) {
    val isSaved: Boolean get() = id != 0L

    companion object {
        /** Quantities are shown in the entry unit of [unit]: kg or L for ingredients. */
        fun fromVariant(variant: ProductVariant, unit: StockUnit = StockUnit.UNIT): VariantForm = VariantForm(
            key = "v${variant.id}",
            id = variant.id,
            size = variant.size.orEmpty(),
            colour = variant.colour.orEmpty(),
            netQuantity = variant.netQuantity?.amountText().orEmpty(),
            unit = variant.netQuantity?.unit,
            sellingPrice = variant.sellingPrice.toDecimalString(),
            mrp = variant.mrp?.toDecimalString().orEmpty(),
            costPrice = variant.costPrice?.toDecimalString().orEmpty(),
            openingStock = "",
            lowStockThreshold = unit.toEntryText(variant.lowStockThreshold),
            barcode = variant.barcode,
            sku = variant.sku.orEmpty(),
            batchNumber = variant.batchNumber.orEmpty(),
            expiryDate = variant.expiryDate,
            currentStock = variant.stockQuantity,
            reorderQuantity = if (variant.reorderQuantity > 0) unit.toEntryText(variant.reorderQuantity) else "",
        )
    }
}

/** Values applied to new garment variants when sizes or colours are added. */
data class VariantDefaults(
    val sellingPrice: String = "",
    val mrp: String = "",
    val costPrice: String = "",
    val openingStock: String = "",
    val lowStockThreshold: String = "",
)

data class ProductForm(
    val productId: Long = 0,
    val category: ProductCategory = ProductCategory.GARMENT,
    val name: String = "",
    val brand: String = "",
    val description: String = "",
    val material: String = "",
    val dietType: DietType? = null,
    /** Warranty period in months, as typed; electronics only. */
    val warrantyMonths: String = "",
    /** What the piece is made of; jewellery only. */
    val metal: com.auxo.pos.domain.jewellery.JewelleryMetal? = null,
    /** Making charge in percent of the metal value, as typed; jewellery only. */
    val makingChargePercent: String = "",
    /** Sold only against a prescription; pharmacy only. */
    val prescriptionRequired: Boolean = false,
    /** Grouping inside the category, such as a menu section; restaurant items only. */
    val subcategory: String = "",
    /** The shop category row this product is listed under; null until one is chosen. */
    val categoryId: Long? = null,
    /** Whether the item can be added to a bill; restaurant items only. */
    val available: Boolean = true,
    /** False sells the item without a stock count; only restaurant items can turn it off. */
    val tracksStock: Boolean = true,
    val gst: GstChoice = GstChoice.NotSet,
    /** Custom GST rate in percent, used when [gst] is [GstChoice.Custom]. */
    val customGstRate: String = "",
    val hsnCode: String = "",
    val sizes: List<String> = emptyList(),
    val colours: List<String> = emptyList(),
    val defaults: VariantDefaults = VariantDefaults(),
    val variants: List<VariantForm> = emptyList(),
    val createdAtMillis: Long = 0,
    /** How stock is counted; only ingredients can use weight or volume. Fixed once saved. */
    val stockUnit: StockUnit = StockUnit.UNIT,
    val preferredSupplierId: Long? = null,
    /** The product photo's file name (see [Product.photoFile]); null for none. */
    val photoFile: String? = null,
) {
    val isNew: Boolean get() = productId == 0L

    companion object {
        fun fromProduct(product: Product): ProductForm = ProductForm(
            productId = product.id,
            category = product.category,
            name = product.name,
            brand = product.brand.orEmpty(),
            description = product.description.orEmpty(),
            material = product.material.orEmpty(),
            dietType = product.dietType,
            warrantyMonths = product.warrantyMonths?.toString().orEmpty(),
            prescriptionRequired = product.prescriptionRequired,
            metal = product.metal,
            makingChargePercent = product.makingChargeBasisPoints?.let { formatRatePercent(it) }.orEmpty(),
            subcategory = product.subcategory.orEmpty(),
            categoryId = product.categoryId,
            available = product.available,
            tracksStock = product.tracksStock,
            gst = GstChoice.from(product.gstRate),
            customGstRate = (product.gstRate as? GstRate.Fixed)
                ?.takeIf { it.basisPoints !in GstRate.STANDARD_BASIS_POINTS }
                ?.let { formatRatePercent(it.basisPoints) }
                .orEmpty(),
            hsnCode = product.hsnCode.orEmpty(),
            sizes = product.variants.mapNotNull { it.size }.distinct(),
            colours = product.variants.mapNotNull { it.colour }.distinct(),
            defaults = product.variants.firstOrNull()?.let {
                VariantDefaults(
                    sellingPrice = it.sellingPrice.toDecimalString(),
                    mrp = it.mrp?.toDecimalString().orEmpty(),
                    costPrice = it.costPrice?.toDecimalString().orEmpty(),
                    lowStockThreshold = it.lowStockThreshold.toString(),
                )
            } ?: VariantDefaults(),
            variants = product.variants.map { VariantForm.fromVariant(it, product.stockUnit) },
            createdAtMillis = product.createdAtMillis,
            stockUnit = product.stockUnit,
            preferredSupplierId = product.preferredSupplierId,
            photoFile = product.photoFile,
        )
    }
}

/** Helpers for building the garment size x colour matrix. */
object GarmentVariants {
    /**
     * Rebuilds the variant list from [sizes] and [colours]: keeps rows whose size
     * and colour still exist, adds rows for new combinations using [defaults],
     * and drops the rest. Rows are grouped by colour, then ordered by size.
     */
    fun sync(
        sizes: List<String>,
        colours: List<String>,
        existing: List<VariantForm>,
        defaults: VariantDefaults,
        newKey: () -> String,
    ): List<VariantForm> = colours.flatMap { colour ->
        sizes.map { size ->
            existing.firstOrNull {
                it.size.equals(size, ignoreCase = true) && it.colour.equals(colour, ignoreCase = true)
            } ?: VariantForm(
                key = newKey(),
                size = size,
                colour = colour,
                sellingPrice = defaults.sellingPrice,
                mrp = defaults.mrp,
                costPrice = defaults.costPrice,
                openingStock = defaults.openingStock,
                lowStockThreshold = defaults.lowStockThreshold,
            )
        }
    }

    /** Copies prices and thresholds to every row, and opening stock to unsaved rows. */
    fun applyDefaults(variants: List<VariantForm>, defaults: VariantDefaults): List<VariantForm> =
        variants.map {
            it.copy(
                sellingPrice = defaults.sellingPrice,
                mrp = defaults.mrp,
                costPrice = defaults.costPrice,
                lowStockThreshold = defaults.lowStockThreshold,
                openingStock = if (it.isSaved) it.openingStock else defaults.openingStock,
            )
        }
}

/** GST choice in the product editor. */
sealed interface GstChoice {
    data object NotSet : GstChoice
    data object Apparel : GstChoice
    data class Standard(val basisPoints: Int) : GstChoice
    data object Custom : GstChoice

    companion object {
        fun from(rate: GstRate?): GstChoice = when (rate) {
            null -> NotSet
            GstRate.Apparel -> Apparel
            is GstRate.Fixed -> if (rate.basisPoints in GstRate.STANDARD_BASIS_POINTS) Standard(rate.basisPoints) else Custom
        }

        /** Default for new products: the apparel rule for garments and footwear, otherwise chosen by the user. */
        fun defaultFor(category: com.auxo.pos.domain.model.ProductCategory): GstChoice = when (category) {
            com.auxo.pos.domain.model.ProductCategory.GARMENT, com.auxo.pos.domain.model.ProductCategory.FOOTWEAR -> Apparel
            com.auxo.pos.domain.model.ProductCategory.RESTAURANT ->
                Standard(com.auxo.pos.domain.model.ProductCategory.RESTAURANT_GST_BASIS_POINTS)
            else -> NotSet
        }
    }
}
