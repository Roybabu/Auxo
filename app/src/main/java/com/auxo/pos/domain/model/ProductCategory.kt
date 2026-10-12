package com.auxo.pos.domain.model

/** The kinds of goods the store sells. Each category has its own product attributes. */
enum class ProductCategory {
    GARMENT,
    PACKAGED_FOOD,
    BEVERAGE,

    /** Raw material for the kitchen, used in menu recipes and not sold at the counter. */
    INGREDIENT,
    ;

    /** Garments are sold in size and colour variants. */
    val usesSizeAndColour: Boolean get() = this == GARMENT

    /** Packaged food and beverages carry a net quantity (weight or volume) on each pack. */
    val usesNetQuantity: Boolean get() = this == PACKAGED_FOOD || this == BEVERAGE

    /** Food, beverages and ingredients are kept in batches with expiry dates. */
    val tracksExpiry: Boolean get() = this != GARMENT

    /** Sold at the billing counter. Ingredients are only used up by recipes. */
    val isSold: Boolean get() = this != INGREDIENT

    val allowedUnits: List<QuantityUnit>
        get() = when (this) {
            GARMENT, INGREDIENT -> emptyList()
            PACKAGED_FOOD -> listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.PIECE)
            BEVERAGE -> listOf(QuantityUnit.MILLILITRE, QuantityUnit.LITRE)
        }

    val defaultUnit: QuantityUnit?
        get() = allowedUnits.firstOrNull()

    /** How stock can be counted: ingredients by weight, volume or piece; everything else by unit. */
    val allowedStockUnits: List<StockUnit>
        get() = if (this == INGREDIENT) listOf(StockUnit.GRAM, StockUnit.MILLILITRE, StockUnit.UNIT) else listOf(StockUnit.UNIT)
}

/** Veg / non-veg marking shown on packaged food in India. */
enum class DietType { VEG, NON_VEG }

enum class QuantityUnit(val symbol: String) {
    GRAM("g"),
    KILOGRAM("kg"),
    MILLILITRE("ml"),
    LITRE("L"),
    PIECE("pcs"),
}
