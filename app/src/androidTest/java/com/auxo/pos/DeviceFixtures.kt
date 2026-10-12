package com.auxo.pos

import com.auxo.pos.domain.barcode.Gs1
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StoreSettings
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

/**
 * The app opens to Admin Setup or Login, so a device test that drives the real MainActivity signs in first:
 * it creates the administrator when the device has none, or signs in when one already exists.
 * Declare it with a lower order than the compose rule so it runs before the activity launches.
 */
class SignedInRule : org.junit.rules.ExternalResource() {
    override fun before() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<AuxoApplication>()
        val auth = app.container.authRepository
        kotlinx.coroutines.runBlocking {
            when (auth.state.value) {
                com.auxo.pos.core.auth.AuthState.NeedsSetup ->
                    auth.createAdministrator("Device Admin", USERNAME, DEVICE_PW, DEVICE_PW)
                com.auxo.pos.core.auth.AuthState.SignedOut -> auth.signIn(USERNAME, DEVICE_PW)
                else -> Unit
            }
            // Setup asks for a recovery code before the app opens; make one so the test reaches the app.
            if (auth.recoveryPrompt.value == com.auxo.pos.core.auth.RecoveryPrompt.Required) {
                val shown = auth.startRecoveryCode(null) as com.auxo.pos.core.auth.RecoveryStartResult.Ready
                auth.confirmRecoveryCode(shown.code)
            }
            auth.dismissRecoveryOffer()
        }
        check(auth.state.value is com.auxo.pos.core.auth.AuthState.SignedIn) { "Could not sign in for the device test" }
    }

    companion object {
        const val USERNAME = "deviceadmin"
        const val DEVICE_PW = "DevicePass1!"
    }
}
