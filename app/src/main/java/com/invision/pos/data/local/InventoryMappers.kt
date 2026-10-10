package com.invision.pos.data.local

import com.invision.pos.data.local.entity.ProductEntity
import com.invision.pos.data.local.entity.ProductVariantEntity
import com.invision.pos.data.local.entity.ProductWithVariants
import com.invision.pos.data.local.entity.StockMovementEntity
import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.NetQuantity
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.QuantityUnit
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import java.time.LocalDate

fun ProductWithVariants.toDomain(): Product = Product(
    id = product.id,
    name = product.name,
    brand = product.brand,
    category = enumValueOrNull<ProductCategory>(product.category) ?: ProductCategory.GARMENT,
    description = product.description,
    material = product.material,
    dietType = product.dietType?.let { enumValueOrNull<DietType>(it) },
    variants = variants.sortedBy { it.id }.map { it.toDomain() },
    createdAtMillis = product.createdAt,
    updatedAtMillis = product.updatedAt,
)

fun ProductVariantEntity.toDomain(): ProductVariant {
    val unit = netQuantityUnit?.let { enumValueOrNull<QuantityUnit>(it) }
    val milli = netQuantityMilli
    return ProductVariant(
        id = id,
        productId = productId,
        sku = sku,
        barcode = barcode,
        size = size,
        colour = colour,
        netQuantity = if (unit != null && milli != null && milli > 0) NetQuantity(milli, unit) else null,
        sellingPrice = Money(priceMinor),
        mrp = mrpMinor?.let { Money(it) },
        costPrice = costMinor?.let { Money(it) },
        stockQuantity = stockQuantity,
        lowStockThreshold = lowStockThreshold,
        batchNumber = batchNumber,
        expiryDate = expiryEpochDay?.let(LocalDate::ofEpochDay),
    )
}

fun Product.toEntity(nowMillis: Long): ProductEntity = ProductEntity(
    id = id,
    name = name,
    brand = brand,
    category = category.name,
    description = description,
    material = material,
    dietType = dietType?.name,
    createdAt = if (createdAtMillis > 0) createdAtMillis else nowMillis,
    updatedAt = nowMillis,
)

fun ProductVariant.toEntity(productId: Long): ProductVariantEntity = ProductVariantEntity(
    id = id,
    productId = productId,
    sku = sku,
    barcode = barcode,
    size = size,
    colour = colour,
    netQuantityMilli = netQuantity?.amountMilli,
    netQuantityUnit = netQuantity?.unit?.name,
    priceMinor = sellingPrice.minorUnits,
    mrpMinor = mrp?.minorUnits,
    costMinor = costPrice?.minorUnits,
    stockQuantity = stockQuantity,
    lowStockThreshold = lowStockThreshold,
    batchNumber = batchNumber,
    expiryEpochDay = expiryDate?.toEpochDay(),
)

fun StockMovementEntity.toDomain(): StockMovement = StockMovement(
    id = id,
    variantId = variantId,
    quantityChange = quantityChange,
    reason = enumValueOrNull<StockChangeReason>(reason) ?: StockChangeReason.CORRECTION,
    note = note,
    createdAtMillis = createdAt,
)

fun StockMovement.toEntity(): StockMovementEntity = StockMovementEntity(
    id = id,
    variantId = variantId,
    quantityChange = quantityChange,
    reason = reason.name,
    note = note,
    createdAt = createdAtMillis,
)

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
