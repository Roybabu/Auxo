package com.invision.pos

import com.invision.pos.domain.barcode.Gs1
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StoreSettings
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/** Sample data for tests that run on a device or emulator. */
object DeviceFixtures {

    /** GST-registered store in West Bengal with prices including GST. */
    val settings = StoreSettings(
        storeName = "Roy Mart",
        address = "12 Park Street, Kolkata",
        phone = "9830012345",
        gstin = "19AABCU9603R1ZK",
        stateCode = "19",
        upiId = "roymart@okaxis",
        upiPayeeName = "Roy Mart",
    )

    /** An in-store EAN-13 (starting with 2) that is very unlikely to be in use already. */
    fun uniqueBarcode(): String {
        val payload = "2" + (1..11).joinToString("") { Random.nextInt(10).toString() }
        return payload + Gs1.checkDigit(payload)
    }

    /** A T-shirt in one size and colour at ₹499. */
    fun shirt(name: String, barcode: String, stock: Int) = Product(
        name = name,
        brand = "Roy Wear",
        category = ProductCategory.GARMENT,
        hsnCode = "6109",
        gstRate = GstRate.Apparel,
        variants = listOf(
            ProductVariant(
                barcode = barcode,
                size = "M",
                colour = "Navy",
                sellingPrice = Money(499_00),
                stockQuantity = stock,
                lowStockThreshold = 1,
            ),
        ),
    )

    /** A sale as checkout builds it, numbered as if saved; [lines] different shirts, two of each. */
    fun sale(lines: Int = 2): Invoice {
        val product = shirt("Cotton T-Shirt", "8901000000019", stock = 100).copy(id = 1)
        var cart = Cart(customer = CustomerDetails(name = "Sen Traders", phone = "9831000000"))
        for (i in 1..lines) {
            val variant = product.variants.single().copy(id = i.toLong(), productId = 1, colour = "Colour $i")
            cart = cart.add(CartLine.from(product, variant, quantity = 2))
        }
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        val payments = listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal))
        val createdAt = LocalDate.of(2026, 10, 10).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val built = SaleBuilder.build(cart, totals, settings, payments, createdAt)
        return built.copy(id = 7, number = "INV/26-27/00007", lines = built.lines.mapIndexed { i, l -> l.copy(id = 100L + i) })
    }
}
