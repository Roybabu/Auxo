package com.auxo.pos.domain.inventory

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.StockStatus
import com.auxo.pos.domain.model.StockUnit
import java.time.LocalDate

/** Most urgent first. */
enum class StockAlertType { EXPIRED, OUT_OF_STOCK, EXPIRING_SOON, LOW_STOCK }

data class StockAlert(
    val productId: Long,
    val productName: String,
    val variantId: Long,
    val variantLabel: String,
    val type: StockAlertType,
    val stockQuantity: Int,
    val expiryDate: LocalDate?,
    val stockUnit: StockUnit = StockUnit.UNIT,
)

data class InventorySummary(
    val productCount: Int,
    val variantCount: Int,
    val unitsInStock: Int,
    val stockValueAtCost: Money,
    val stockValueAtPrice: Money,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val expiringSoonCount: Int,
    val expiredCount: Int,
    val alerts: List<StockAlert>,
) {
    val hasProducts: Boolean get() = productCount > 0

    companion object {
        val EMPTY = InventorySummary(0, 0, 0, Money.ZERO, Money.ZERO, 0, 0, 0, 0, emptyList())
    }
}

object InventorySummaryCalculator {

    const val DEFAULT_MAX_ALERTS = 6

    /**
     * Counts are per variant. Stock value uses cost price where known; variants
     * without a cost price are left out of the cost total. Units in stock and
     * value at selling price count shop items only; ingredients are weighed or
     * measured and not sold, so they add to the cost total alone.
     */
    fun calculate(
        products: List<Product>,
        today: LocalDate,
        maxAlerts: Int = DEFAULT_MAX_ALERTS,
        warningDays: Int = InventoryFilter.EXPIRY_WARNING_DAYS.toInt(),
    ): InventorySummary {
        var units = 0
        var valueAtCost = Money.ZERO
        var valueAtPrice = Money.ZERO
        var low = 0
        var out = 0
        var expiring = 0
        var expired = 0
        val alerts = mutableListOf<StockAlert>()

        for (product in products) {
            for (variant in product.variants) {
                val inStock = variant.stockQuantity.coerceAtLeast(0)
                variant.costPrice?.let { valueAtCost += product.stockUnit.costOf(inStock, it) }
                if (product.category.isSold) {
                    units += inStock
                    valueAtPrice += variant.sellingPrice * inStock
                }

                val alertType = when {
                    variant.isExpired(today) -> StockAlertType.EXPIRED
                    variant.stockStatus == StockStatus.OUT_OF_STOCK -> StockAlertType.OUT_OF_STOCK
                    variant.expiresWithin(today, warningDays.toLong()) -> StockAlertType.EXPIRING_SOON
                    variant.stockStatus == StockStatus.LOW_STOCK -> StockAlertType.LOW_STOCK
                    else -> null
                }

                when (variant.stockStatus) {
                    StockStatus.LOW_STOCK -> low++
                    StockStatus.OUT_OF_STOCK -> out++
                    StockStatus.IN_STOCK -> Unit
                }
                if (variant.isExpired(today)) expired++
                if (variant.expiresWithin(today, warningDays.toLong())) expiring++

                if (alertType != null) {
                    alerts += StockAlert(
                        productId = product.id,
                        productName = product.name,
                        variantId = variant.id,
                        variantLabel = variant.attributesLabel,
                        type = alertType,
                        stockQuantity = variant.stockQuantity,
                        expiryDate = variant.expiryDate,
                        stockUnit = product.stockUnit,
                    )
                }
            }
        }

        return InventorySummary(
            productCount = products.size,
            variantCount = products.sumOf { it.variants.size },
            unitsInStock = units,
            stockValueAtCost = valueAtCost,
            stockValueAtPrice = valueAtPrice,
            lowStockCount = low,
            outOfStockCount = out,
            expiringSoonCount = expiring,
            expiredCount = expired,
            alerts = alerts
                .sortedWith(compareBy<StockAlert>({ it.type.ordinal }, { it.productName.lowercase() }))
                .take(maxAlerts),
        )
    }
}
