package com.invision.pos.domain.model

/** The kinds of goods the store sells. Each category has its own product attributes. */
enum class ProductCategory {
    GARMENT,
    PACKAGED_FOOD,
    BEVERAGE,
    ;

    /** Garments are sold in size and colour variants. */
    val usesSizeAndColour: Boolean get() = this == GARMENT

    /** Food and beverages carry a net quantity (weight or volume) on each pack. */
    val usesNetQuantity: Boolean get() = this != GARMENT

    /** Food and beverages have batch numbers and expiry dates. */
    val tracksExpiry: Boolean get() = this != GARMENT

    val allowedUnits: List<QuantityUnit>
        get() = when (this) {
            GARMENT -> emptyList()
            PACKAGED_FOOD -> listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.PIECE)
            BEVERAGE -> listOf(QuantityUnit.MILLILITRE, QuantityUnit.LITRE)
        }

    val defaultUnit: QuantityUnit?
        get() = allowedUnits.firstOrNull()
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
