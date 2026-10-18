package com.auxo.pos.testutil

import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.NetQuantity
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.StockUnit
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Sample products and a fixed clock shared by tests. */
object TestData {
    val today: LocalDate = LocalDate.of(2026, 10, 10)

    val clock: Clock = Clock.fixed(today.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    /** Valid EAN-13 codes (correct check digits). */
    val EAN = listOf(
        "8901000000019", "8901000000026", "8901000000033", "8901000000040",
        "8901000000057", "8901000000064", "8901000000071", "8901000000088",
    )

    /** GST-registered store in West Bengal with prices including GST. */
    val settings = com.auxo.pos.domain.model.StoreSettings(
        storeName = "Sample Mart",
        address = "12 Park Street, Kolkata",
        phone = "9830012345",
        gstin = "19AABCU9603R1ZK",
        stateCode = "19",
        upiId = "samplemart@okaxis",
        upiPayeeName = "Sample Mart",
    )

    fun variant(
        id: Long = 0,
        barcode: String = EAN[0],
        price: Long = 49_900,
        stock: Int = 10,
        lowStock: Int = 2,
        size: String? = null,
        colour: String? = null,
        netQuantity: NetQuantity? = null,
        cost: Long? = null,
        mrp: Long? = null,
        expiry: LocalDate? = null,
        sku: String? = null,
    ) = ProductVariant(
        id = id,
        sku = sku,
        barcode = barcode,
        size = size,
        colour = colour,
        netQuantity = netQuantity,
        sellingPrice = Money(price),
        mrp = mrp?.let { Money(it) },
        costPrice = cost?.let { Money(it) },
        stockQuantity = stock,
        lowStockThreshold = lowStock,
        expiryDate = expiry,
    )

    fun tShirt(id: Long = 1) = Product(
        id = id,
        name = "Cotton T-Shirt",
        brand = "Sample Wear",
        category = ProductCategory.GARMENT,
        material = "100% cotton",
        hsnCode = "6109",
        gstRate = com.auxo.pos.domain.model.GstRate.Apparel,
        variants = listOf(
            variant(id = id * 10 + 1, barcode = EAN[0], size = "M", colour = "Navy", stock = 8, cost = 25_000),
            variant(id = id * 10 + 2, barcode = EAN[1], size = "L", colour = "Navy", stock = 1, cost = 25_000),
        ),
    )

    fun biscuits(id: Long = 2, expiry: LocalDate? = today.plusDays(90)) = Product(
        id = id,
        name = "Butter Biscuits",
        brand = "Good Bake",
        category = ProductCategory.PACKAGED_FOOD,
        dietType = DietType.VEG,
        hsnCode = "1905",
        gstRate = com.auxo.pos.domain.model.GstRate.Fixed(500),
        variants = listOf(
            variant(
                id = id * 10 + 1,
                barcode = EAN[2],
                price = 3_000,
                stock = 40,
                lowStock = 10,
                netQuantity = NetQuantity(200_000, QuantityUnit.GRAM),
                cost = 2_200,
                mrp = 3_500,
                expiry = expiry,
            ),
        ),
    )

    /** Flour counted in grams; cost is per kilogram. */
    fun flour(id: Long = 4, grams: Int = 5_000, expiry: LocalDate? = today.plusDays(60)) = Product(
        id = id,
        name = "Wheat Flour",
        category = ProductCategory.PACKAGED_FOOD,
        stockUnit = StockUnit.GRAM,
        variants = listOf(
            variant(id = id * 10 + 1, barcode = EAN[4], price = 0, stock = grams, lowStock = 1_000, cost = 4_500, expiry = expiry),
        ),
    )

    /** Milk counted in millilitres; cost is per litre. */
    fun milk(id: Long = 5, millilitres: Int = 2_000, expiry: LocalDate? = today.plusDays(3)) = Product(
        id = id,
        name = "Milk",
        category = ProductCategory.PACKAGED_FOOD,
        stockUnit = StockUnit.MILLILITRE,
        variants = listOf(
            variant(id = id * 10 + 1, barcode = EAN[5], price = 0, stock = millilitres, lowStock = 500, cost = 6_000, expiry = expiry),
        ),
    )

    /** A restaurant dish: a product with one variant, no stock count, 5% GST. */
    fun dish(
        id: Long = 40,
        name: String = "Masala Dosa",
        price: Money = Money(12_000),
        subcategory: String? = "Breakfast",
        available: Boolean = true,
        tracksStock: Boolean = false,
        stock: Int = 0,
    ) = Product(
        id = id,
        name = name,
        category = ProductCategory.RESTAURANT,
        hsnCode = ProductCategory.RESTAURANT_SAC,
        gstRate = com.auxo.pos.domain.model.GstRate.Fixed(ProductCategory.RESTAURANT_GST_BASIS_POINTS),
        subcategory = subcategory,
        available = available,
        tracksStock = tracksStock,
        variants = listOf(
            variant(id = id * 10 + 1, barcode = "DISH-$id", price = price.minorUnits, stock = stock, lowStock = 0),
        ),
    )

    fun juice(id: Long = 3, stock: Int = 0) = Product(
        id = id,
        name = "Mango Juice",
        category = ProductCategory.BEVERAGE,
        hsnCode = "2202",
        gstRate = com.auxo.pos.domain.model.GstRate.Fixed(500),
        variants = listOf(
            variant(
                id = id * 10 + 1,
                barcode = EAN[3],
                price = 9_900,
                stock = stock,
                lowStock = 5,
                netQuantity = NetQuantity(1_000_000, QuantityUnit.LITRE),
                expiry = today.plusDays(10),
            ),
        ),
    )
}
