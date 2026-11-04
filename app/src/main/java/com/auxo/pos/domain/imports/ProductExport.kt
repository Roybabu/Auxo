// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.imports

import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StockBatch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The whole inventory as a CSV file, one row per variant.
 *
 * The first columns are exactly the import columns, in the same order, so the file can be edited and imported
 * again. The columns after them carry the rest of what is known about each product; the import ignores them.
 */
object ProductExport {

    private val extraColumns = listOf(
        "subcategory", "stock_unit", "reorder_quantity", "warranty_months", "metal", "making_charge_percent",
        "prescription_required", "available", "tracks_stock", "stock_status", "stock_value_at_cost", "batches",
    )

    val header: String get() = (ImportColumn.entries.map { it.key } + extraColumns).joinToString(",")

    fun fileName(today: LocalDate): String = "auxo-inventory-$today.csv"

    /** Products sorted by name; a product without variants still gets one row, so it is not lost. */
    fun csv(products: List<Product>): String {
        val lines = ArrayList<String>()
        lines += header
        for (product in products.sortedBy { it.name.lowercase() }) {
            if (product.variants.isEmpty()) lines += row(product, null) else product.variants.forEach { lines += row(product, it) }
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun row(product: Product, variant: ProductVariant?): String {
        val cells = ArrayList<String>()
        cells += text(product.name)
        cells += categoryName(product.category)
        cells += text(product.brand)
        cells += text(product.description)
        cells += text(product.material)
        cells += when (product.dietType) {
            DietType.VEG -> "Veg"
            DietType.NON_VEG -> "Non-veg"
            null -> ""
        }
        cells += gst(product.gstRate)
        cells += text(product.hsnCode)
        cells += text(variant?.size)
        cells += text(variant?.colour)
        cells += variant?.netQuantity?.amountText().orEmpty()
        cells += variant?.netQuantity?.unit?.symbol.orEmpty()
        cells += variant?.sellingPrice?.toDecimalString().orEmpty()
        cells += variant?.mrp?.toDecimalString().orEmpty()
        cells += variant?.costPrice?.toDecimalString().orEmpty()
        cells += variant?.stockQuantity?.toString().orEmpty()
        cells += variant?.lowStockThreshold?.toString().orEmpty()
        cells += text(variant?.barcode)
        cells += text(variant?.sku)
        cells += text(variant?.batchNumber)
        cells += variant?.expiryDate?.toString().orEmpty()

        cells += text(product.subcategory)
        cells += product.stockUnit.name.lowercase()
        cells += variant?.reorderQuantity?.toString().orEmpty()
        cells += product.warrantyMonths?.toString().orEmpty()
        cells += product.metal?.name.orEmpty()
        cells += product.makingChargeBasisPoints?.let { BigDecimal.valueOf(it.toLong(), 2).stripTrailingZeros().toPlainString() }.orEmpty()
        cells += if (product.category.usesPrescription) yesNo(product.prescriptionRequired) else ""
        cells += yesNo(product.available)
        cells += yesNo(product.tracksStock)
        cells += if (variant != null && product.tracksStock) variant.stockStatus.name.lowercase() else ""
        cells += stockValue(variant)
        cells += batches(variant?.batches.orEmpty())
        return cells.joinToString(",") { ProductImportTemplate.csvCell(it) }
    }

    /** The names the import reads back. Loose goods, jewellery and restaurant items have no import name of their own. */
    fun categoryName(category: ProductCategory): String = when (category) {
        ProductCategory.GARMENT -> "Clothing"
        ProductCategory.FOOTWEAR -> "Footwear"
        ProductCategory.ACCESSORIES -> "Accessories"
        ProductCategory.PACKAGED_FOOD -> "Packaged food"
        ProductCategory.BEVERAGE -> "Beverage"
        ProductCategory.GENERAL -> "General"
        ProductCategory.ELECTRONICS -> "Electronics"
        ProductCategory.PHARMACY -> "Pharmacy"
        ProductCategory.COSMETICS -> "Cosmetics"
        ProductCategory.LOOSE -> "Loose goods"
        ProductCategory.JEWELLERY -> "Jewellery"
        ProductCategory.RESTAURANT -> "Restaurant"
    }

    private fun gst(rate: GstRate?): String = when (rate) {
        null -> ""
        GstRate.Apparel -> "Apparel"
        is GstRate.Fixed -> BigDecimal.valueOf(rate.basisPoints.toLong(), 2).stripTrailingZeros().toPlainString()
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun stockValue(variant: ProductVariant?): String {
        val cost = variant?.costPrice ?: return ""
        return runCatching { (cost * variant.stockQuantity).toDecimalString() }.getOrDefault("")
    }

    /** Every batch with stock, e.g. "B-101 x20 exp 2027-03-31; B-102 x5". */
    private fun batches(batches: List<StockBatch>): String = batches.joinToString("; ") { batch ->
        buildString {
            append(batch.batchNumber?.takeIf { it.isNotBlank() } ?: "no batch no.")
            append(" x").append(batch.quantity)
            batch.expiryDate?.let { append(" exp ").append(it) }
        }
    }

    /**
     * Free text. A spreadsheet runs a cell that starts with = + - or @ as a formula, so such text gets a leading
     * apostrophe, which spreadsheets show as plain text.
     */
    private fun text(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        return if (trimmed.isNotEmpty() && trimmed.first() in FORMULA_STARTS) "'$trimmed" else trimmed
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t')
}
