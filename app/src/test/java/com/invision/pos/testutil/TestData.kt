package com.invision.pos.testutil

import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.NetQuantity
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.QuantityUnit
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
        brand = "Roy Wear",
        category = ProductCategory.GARMENT,
        material = "100% cotton",
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

    fun juice(id: Long = 3, stock: Int = 0) = Product(
        id = id,
        name = "Mango Juice",
        category = ProductCategory.BEVERAGE,
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
