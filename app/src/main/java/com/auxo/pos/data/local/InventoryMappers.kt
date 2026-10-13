package com.auxo.pos.data.local

import com.auxo.pos.data.local.entity.BatchRow
import com.auxo.pos.data.local.entity.MovementRow
import com.auxo.pos.data.local.entity.ProductEntity
import com.auxo.pos.data.local.entity.ProductVariantEntity
import com.auxo.pos.data.local.entity.ProductWithVariants
import com.auxo.pos.data.local.entity.StockBatchEntity
import com.auxo.pos.data.local.entity.StockMovementEntity
import com.auxo.pos.data.local.entity.VariantWithBatches
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.NetQuantity
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.StockBatch
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.model.StockMovementRecord
import com.auxo.pos.domain.model.StockUnit
import java.time.LocalDate

fun ProductWithVariants.toDomain(): Product = Product(
    id = product.id,
    name = product.name,
    brand = product.brand,
    category = enumValueOrNull<ProductCategory>(product.category) ?: ProductCategory.GARMENT,
    description = product.description,
    material = product.material,
    dietType = product.dietType?.let { enumValueOrNull<DietType>(it) },
    hsnCode = product.hsnCode,
    gstRate = gstRateOf(product.gstRule, product.gstRateBasisPoints),
    variants = variants.sortedBy { it.variant.id }.map { it.toDomain() },
    createdAtMillis = product.createdAt,
    updatedAtMillis = product.updatedAt,
    stockUnit = stockUnitOf(product.stockUnit),
    preferredSupplierId = product.preferredSupplierId,
    photoFile = product.photoFile,
    subcategory = product.subcategory,
    categoryId = product.categoryId,
    available = product.available,
    tracksStock = product.tracksStock,
)

fun VariantWithBatches.toDomain(): ProductVariant = variant.toDomain().copy(
    batches = batches
        .filter { it.quantity > 0 }
        .sortedWith(compareBy<StockBatchEntity>({ it.expiryEpochDay == null }, { it.expiryEpochDay }, { it.receivedAt }, { it.id }))
        .map { it.toDomain() },
)

fun StockBatchEntity.toDomain(): StockBatch = StockBatch(
    id = id,
    variantId = variantId,
    batchNumber = batchNumber,
    expiryDate = expiryEpochDay?.let(LocalDate::ofEpochDay),
    quantity = quantity,
    receivedAtMillis = receivedAt,
    unitCost = unitCostMinor?.let { Money(it) },
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
        reorderQuantity = reorderQuantity,
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
    hsnCode = hsnCode,
    gstRule = when (gstRate) {
        null -> null
        GstRate.Apparel -> GST_RULE_APPAREL
        is GstRate.Fixed -> GST_RULE_FIXED
    },
    gstRateBasisPoints = (gstRate as? GstRate.Fixed)?.basisPoints,
    createdAt = if (createdAtMillis > 0) createdAtMillis else nowMillis,
    updatedAt = nowMillis,
    stockUnit = stockUnit.takeIf { it != StockUnit.UNIT }?.name,
    preferredSupplierId = preferredSupplierId,
    photoFile = photoFile,
    subcategory = subcategory,
    categoryId = categoryId,
    available = available,
    tracksStock = tracksStock,
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
    reorderQuantity = reorderQuantity,
)

fun StockMovementEntity.toDomain(): StockMovement = StockMovement(
    id = id,
    variantId = variantId,
    quantityChange = quantityChange,
    reason = enumValueOrNull<StockChangeReason>(reason) ?: StockChangeReason.CORRECTION,
    note = note,
    createdAtMillis = createdAt,
    batchId = batchId,
    invoiceId = invoiceId,
    receiptId = receiptId,
)

fun StockMovement.toEntity(): StockMovementEntity = StockMovementEntity(
    id = id,
    variantId = variantId,
    quantityChange = quantityChange,
    reason = reason.name,
    note = note,
    createdAt = createdAtMillis,
    invoiceId = invoiceId,
    batchId = batchId,
    receiptId = receiptId,
)

fun MovementRow.toDomain(): StockMovementRecord = StockMovementRecord(
    movement = movement.toDomain(),
    productId = productId,
    productName = productName,
    variantLabel = variantLabel(size, colour, netQuantityMilli, netQuantityUnit),
    category = enumValueOrNull<ProductCategory>(category) ?: ProductCategory.GARMENT,
    categoryId = categoryId,
    stockUnit = stockUnitOf(stockUnit),
    batchNumber = batchNumberRef,
    reference = invoiceNumber ?: receiptNumber,
    unitCost = costMinor?.let { Money(it) },
)

fun BatchRow.toDomain(): BatchWithProduct = BatchWithProduct(
    batch = batch.toDomain(),
    productId = productId,
    productName = productName,
    variantLabel = variantLabel(size, colour, netQuantityMilli, netQuantityUnit),
    category = enumValueOrNull<ProductCategory>(category) ?: ProductCategory.GARMENT,
    stockUnit = stockUnitOf(stockUnit),
    unitCost = costMinor?.let { Money(it) },
)

internal fun stockUnitOf(name: String?): StockUnit = name?.let { enumValueOrNull<StockUnit>(it) } ?: StockUnit.UNIT

/** Same text as [ProductVariant.attributesLabel], from stored columns. */
internal fun variantLabel(size: String?, colour: String?, netQuantityMilli: Long?, netQuantityUnit: String?): String {
    val unit = netQuantityUnit?.let { enumValueOrNull<QuantityUnit>(it) }
    val net = if (unit != null && netQuantityMilli != null && netQuantityMilli > 0) NetQuantity(netQuantityMilli, unit).format() else null
    return listOfNotNull(size, colour, net).joinToString(" · ")
}

private const val GST_RULE_FIXED = "FIXED"
private const val GST_RULE_APPAREL = "APPAREL"

internal fun gstRateOf(rule: String?, basisPoints: Int?): GstRate? = when (rule) {
    GST_RULE_APPAREL -> GstRate.Apparel
    GST_RULE_FIXED -> basisPoints?.takeIf { it in 0..GstRate.MAX_BASIS_POINTS }?.let { GstRate.Fixed(it) }
    else -> null
}

internal inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
