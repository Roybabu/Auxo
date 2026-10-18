package com.auxo.pos.domain.validation

import com.auxo.pos.domain.barcode.BarcodeCheck
import com.auxo.pos.domain.barcode.BarcodeError
import com.auxo.pos.domain.barcode.BarcodeRules
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.parseRatePercent
import com.auxo.pos.domain.model.NetQuantity
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StockUnit

enum class FieldError {
    REQUIRED,
    TOO_SHORT,
    TOO_LONG,
    INVALID_PRICE,
    MRP_BELOW_PRICE,
    INVALID_QUANTITY,
    QUANTITY_TOO_LARGE,
    INVALID_NET_QUANTITY,
    UNIT_REQUIRED,
    INVALID_BARCODE_CHECK_DIGIT,
    INVALID_BARCODE_CHARACTERS,
    BARCODE_TOO_LONG,
    DUPLICATE_BARCODE,
    BARCODE_IN_USE,
    INVALID_SKU,
    DUPLICATE_SKU,
    SKU_IN_USE,
    DUPLICATE_VARIANT,
    REMOVED_VARIANT_HAS_STOCK,
    INVALID_GST_RATE,
    INVALID_HSN,
    APPAREL_RULE_GARMENTS_ONLY,

    /** Weight or volume quantity that isn't a number with up to 3 decimals. */
    INVALID_AMOUNT,
}

enum class FormField { NAME, BRAND, DESCRIPTION, MATERIAL, SUBCATEGORY, SIZES, COLOURS, VARIANTS, GST_RATE, HSN }

enum class VariantField {
    SIZE, COLOUR, NET_QUANTITY, UNIT, SELLING_PRICE, MRP, COST_PRICE,
    OPENING_STOCK, LOW_STOCK, REORDER, BARCODE, SKU, BATCH,
}

data class ProductFormErrors(
    val fields: Map<FormField, FieldError> = emptyMap(),
    /** Errors per variant row, keyed by [VariantForm.key]. */
    val variants: Map<String, Map<VariantField, FieldError>> = emptyMap(),
) {
    val isEmpty: Boolean get() = fields.isEmpty() && variants.values.all { it.isEmpty() }

    fun forVariant(key: String): Map<VariantField, FieldError> = variants[key].orEmpty()

    val errorCount: Int get() = fields.size + variants.values.sumOf { it.size }
}

sealed interface ProductValidation {
    /**
     * The form is valid. Variants with a blank barcode carry an empty string and
     * get an in-store barcode when saved.
     */
    data class Valid(val product: Product) : ProductValidation
    data class Invalid(val errors: ProductFormErrors) : ProductValidation
}

object ProductFormValidator {

    const val NAME_MIN = 2
    const val NAME_MAX = 80
    const val BRAND_MAX = 60
    const val DESCRIPTION_MAX = 500
    const val MATERIAL_MAX = 60
    const val SUBCATEGORY_MAX = 40
    const val SIZE_MAX = 12
    const val COLOUR_MAX = 30
    const val BATCH_MAX = 32
    const val SKU_MAX = 32
    const val MAX_STOCK = 1_000_000

    private val SKU_PATTERN = Regex("""[A-Za-z0-9\-_]+""")

    /** HSN codes are 4, 6 or 8 digits. */
    private val HSN_PATTERN = Regex("""\d{4}|\d{6}|\d{8}""")

    /**
     * @param originalVariants variants saved before editing; removing one that still
     *   has stock is not allowed, so stock is never lost silently.
     */
    fun validate(form: ProductForm, originalVariants: List<ProductVariant> = emptyList()): ProductValidation {
        val fieldErrors = mutableMapOf<FormField, FieldError>()
        val variantErrors = mutableMapOf<String, MutableMap<VariantField, FieldError>>()

        val name = form.name.trim()
        when {
            name.isEmpty() -> fieldErrors[FormField.NAME] = FieldError.REQUIRED
            name.length < NAME_MIN -> fieldErrors[FormField.NAME] = FieldError.TOO_SHORT
            name.length > NAME_MAX -> fieldErrors[FormField.NAME] = FieldError.TOO_LONG
        }
        if (form.brand.trim().length > BRAND_MAX) fieldErrors[FormField.BRAND] = FieldError.TOO_LONG
        if (form.description.trim().length > DESCRIPTION_MAX) fieldErrors[FormField.DESCRIPTION] = FieldError.TOO_LONG
        if (form.material.trim().length > MATERIAL_MAX) fieldErrors[FormField.MATERIAL] = FieldError.TOO_LONG
        if (form.subcategory.trim().length > SUBCATEGORY_MAX) fieldErrors[FormField.SUBCATEGORY] = FieldError.TOO_LONG

        val unit = form.stockUnit.takeIf { it in form.category.allowedStockUnits } ?: StockUnit.UNIT
        // Only restaurant items can be sold without a stock count.
        val tracksStock = !form.category.canSkipStock || form.tracksStock

        val gstRate: GstRate? = when (val choice = form.gst) {
            GstChoice.NotSet -> null
            GstChoice.Apparel -> {
                if (form.category != ProductCategory.GARMENT) fieldErrors[FormField.GST_RATE] = FieldError.APPAREL_RULE_GARMENTS_ONLY
                GstRate.Apparel
            }
            is GstChoice.Standard -> GstRate.Fixed(choice.basisPoints)
            GstChoice.Custom -> {
                val basisPoints = parseRatePercent(form.customGstRate)
                if (basisPoints == null) fieldErrors[FormField.GST_RATE] = FieldError.INVALID_GST_RATE
                basisPoints?.let { GstRate.Fixed(it) }
            }
        }
        val hsn = form.hsnCode.trim()
        if (hsn.isNotEmpty() && !HSN_PATTERN.matches(hsn)) fieldErrors[FormField.HSN] = FieldError.INVALID_HSN

        if (form.category.usesSizeAndColour) {
            if (form.sizes.isEmpty()) fieldErrors[FormField.SIZES] = FieldError.REQUIRED
            if (form.colours.isEmpty()) fieldErrors[FormField.COLOURS] = FieldError.REQUIRED
        }
        if (form.variants.isEmpty()) {
            fieldErrors.putIfAbsent(FormField.VARIANTS, FieldError.REQUIRED)
        }

        val keptIds = form.variants.map { it.id }.toSet()
        if (originalVariants.any { it.id !in keptIds && it.stockQuantity > 0 }) {
            fieldErrors[FormField.VARIANTS] = FieldError.REMOVED_VARIANT_HAS_STOCK
        }

        val validVariants = form.variants.mapNotNull { row ->
            val errors = mutableMapOf<VariantField, FieldError>()
            val variant = validateVariant(form.category, unit, tracksStock, row, errors)
            if (errors.isNotEmpty()) variantErrors[row.key] = errors
            variant
        }

        markDuplicates(form, variantErrors)

        if (fieldErrors.isNotEmpty() || variantErrors.values.any { it.isNotEmpty() }) {
            return ProductValidation.Invalid(ProductFormErrors(fieldErrors, variantErrors))
        }

        val product = Product(
            id = form.productId,
            name = name,
            brand = form.brand.trim().ifEmpty { null },
            category = form.category,
            description = form.description.trim().ifEmpty { null },
            material = if (form.category == ProductCategory.GARMENT) form.material.trim().ifEmpty { null } else null,
            dietType = if (form.category.usesDietType) form.dietType else null,
            hsnCode = hsn.ifEmpty { null },
            gstRate = gstRate,
            variants = validVariants,
            subcategory = form.subcategory.trim().ifEmpty { null },
            categoryId = form.categoryId,
            available = form.available || form.category != ProductCategory.RESTAURANT,
            tracksStock = tracksStock,
            createdAtMillis = form.createdAtMillis,
            stockUnit = unit,
            preferredSupplierId = form.preferredSupplierId,
            photoFile = form.photoFile,
        )
        return ProductValidation.Valid(product)
    }

    private fun validateVariant(
        category: ProductCategory,
        unit: StockUnit,
        tracksStock: Boolean,
        row: VariantForm,
        errors: MutableMap<VariantField, FieldError>,
    ): ProductVariant? {
        val size = row.size.trim()
        val colour = row.colour.trim()
        var netQuantity: NetQuantity? = null

        if (category.usesSizeAndColour) {
            requireText(size, SIZE_MAX, VariantField.SIZE, errors)
            requireText(colour, COLOUR_MAX, VariantField.COLOUR, errors)
        }
        if (category.usesNetQuantity) {
            val unit = row.unit
            if (unit == null || unit !in category.allowedUnits) {
                errors[VariantField.UNIT] = FieldError.UNIT_REQUIRED
            }
            when {
                row.netQuantity.isBlank() -> errors[VariantField.NET_QUANTITY] = FieldError.REQUIRED
                unit != null -> {
                    netQuantity = NetQuantity.parseOrNull(row.netQuantity, unit)
                    if (netQuantity == null) errors[VariantField.NET_QUANTITY] = FieldError.INVALID_NET_QUANTITY
                }
            }
        }

        val price = when {
            row.sellingPrice.isBlank() -> {
                errors[VariantField.SELLING_PRICE] = FieldError.REQUIRED
                null
            }
            else -> Money.parseOrNull(row.sellingPrice)?.takeIf { !it.isZero }.also {
                if (it == null) errors[VariantField.SELLING_PRICE] = FieldError.INVALID_PRICE
            }
        }
        val mrp = optionalMoney(row.mrp, VariantField.MRP, errors)
        if (price != null && mrp != null && mrp < price) errors[VariantField.MRP] = FieldError.MRP_BELOW_PRICE
        val cost = optionalMoney(row.costPrice, VariantField.COST_PRICE, errors)

        // Without a stock count there is nothing to open with, warn about or reorder.
        val openingStock = if (row.isSaved || !tracksStock) 0 else optionalQuantity(row.openingStock, unit, VariantField.OPENING_STOCK, errors)
        val lowStock = if (tracksStock) optionalQuantity(row.lowStockThreshold, unit, VariantField.LOW_STOCK, errors) else 0
        val reorder = if (tracksStock) optionalQuantity(row.reorderQuantity, unit, VariantField.REORDER, errors) else 0

        val barcode = row.barcode.trim()
        if (barcode.isNotEmpty()) {
            val check = BarcodeRules.check(barcode)
            if (check is BarcodeCheck.Invalid) {
                errors[VariantField.BARCODE] = when (check.error) {
                    BarcodeError.INVALID_CHECK_DIGIT -> FieldError.INVALID_BARCODE_CHECK_DIGIT
                    BarcodeError.TOO_LONG -> FieldError.BARCODE_TOO_LONG
                    BarcodeError.EMPTY, BarcodeError.INVALID_CHARACTERS -> FieldError.INVALID_BARCODE_CHARACTERS
                }
            }
        }

        val sku = row.sku.trim()
        if (sku.isNotEmpty() && (sku.length > SKU_MAX || !SKU_PATTERN.matches(sku))) {
            errors[VariantField.SKU] = FieldError.INVALID_SKU
        }

        val batch = row.batchNumber.trim()
        if (batch.length > BATCH_MAX) errors[VariantField.BATCH] = FieldError.TOO_LONG

        if (errors.isNotEmpty() || price == null) return null

        return ProductVariant(
            id = row.id,
            sku = sku.ifEmpty { null },
            barcode = barcode,
            size = size.takeIf { category.usesSizeAndColour },
            colour = colour.takeIf { category.usesSizeAndColour },
            netQuantity = netQuantity,
            sellingPrice = price,
            mrp = mrp,
            costPrice = cost,
            stockQuantity = if (row.isSaved) row.currentStock ?: 0 else openingStock,
            lowStockThreshold = lowStock,
            reorderQuantity = reorder,
            batchNumber = batch.ifEmpty { null }.takeIf { category.tracksExpiry },
            expiryDate = row.expiryDate.takeIf { category.tracksExpiry },
        )
    }

    /** Flags repeated barcodes, SKUs and size/colour or pack-size combinations within the form. */
    private fun markDuplicates(
        form: ProductForm,
        variantErrors: MutableMap<String, MutableMap<VariantField, FieldError>>,
    ) {
        fun flag(key: String, field: VariantField, error: FieldError) {
            variantErrors.getOrPut(key) { mutableMapOf() }.putIfAbsent(field, error)
        }

        form.variants
            .filter { it.barcode.isNotBlank() }
            .groupBy { it.barcode.trim() }
            .values.filter { it.size > 1 }
            .flatten()
            .forEach { flag(it.key, VariantField.BARCODE, FieldError.DUPLICATE_BARCODE) }

        form.variants
            .filter { it.sku.isNotBlank() }
            .groupBy { it.sku.trim().lowercase() }
            .values.filter { it.size > 1 }
            .flatten()
            .forEach { flag(it.key, VariantField.SKU, FieldError.DUPLICATE_SKU) }

        val comboField = if (form.category.usesSizeAndColour) VariantField.SIZE else VariantField.NET_QUANTITY
        form.variants
            .groupBy { variantIdentity(form.category, it) }
            .filterKeys { it.isNotBlank() }
            .values.filter { it.size > 1 }
            .flatten()
            .forEach { flag(it.key, comboField, FieldError.DUPLICATE_VARIANT) }
    }

    private fun variantIdentity(category: ProductCategory, row: VariantForm): String =
        if (category.usesSizeAndColour) {
            "${row.size.trim().lowercase()}|${row.colour.trim().lowercase()}"
        } else {
            val quantity = row.unit?.let { NetQuantity.parseOrNull(row.netQuantity, it) }
            quantity?.let { "${it.amountMilli}|${it.unit}" }.orEmpty()
        }

    private fun requireText(value: String, max: Int, field: VariantField, errors: MutableMap<VariantField, FieldError>) {
        when {
            value.isEmpty() -> errors[field] = FieldError.REQUIRED
            value.length > max -> errors[field] = FieldError.TOO_LONG
        }
    }

    private fun optionalMoney(text: String, field: VariantField, errors: MutableMap<VariantField, FieldError>): Money? {
        if (text.isBlank()) return null
        return Money.parseOrNull(text).also { if (it == null) errors[field] = FieldError.INVALID_PRICE }
    }

    /** Whole pieces, or kg / L with up to 3 decimals stored as grams / millilitres. */
    private fun optionalQuantity(text: String, unit: StockUnit, field: VariantField, errors: MutableMap<VariantField, FieldError>): Int {
        if (unit == StockUnit.UNIT) return optionalCount(text, field, errors)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return unit.parse(trimmed, allowZero = true) ?: run {
            errors[field] = if (DECIMAL.matches(trimmed)) FieldError.QUANTITY_TOO_LARGE else FieldError.INVALID_AMOUNT
            0
        }
    }

    private val DECIMAL = Regex("""\d+(\.\d{1,3})?""")

    private fun optionalCount(text: String, field: VariantField, errors: MutableMap<VariantField, FieldError>): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        if (!trimmed.all { it in '0'..'9' }) {
            errors[field] = FieldError.INVALID_QUANTITY
            return 0
        }
        val value = trimmed.toIntOrNull()
        if (value == null || value > MAX_STOCK) {
            errors[field] = FieldError.QUANTITY_TOO_LARGE
            return 0
        }
        return value
    }
}
