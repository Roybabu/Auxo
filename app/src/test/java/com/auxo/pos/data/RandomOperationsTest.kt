package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomCustomerRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.RoomPurchasingRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveCustomerResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.PurchaseReceiptLine
import com.auxo.pos.domain.model.Supplier
import com.auxo.pos.testutil.DataIntegrity
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Hundreds of random sales, returns, receipts and account payments in a row. Some are refused
 * (short stock, credit limit, too much to pay), and after every few steps the books must still
 * reconcile. The random numbers are seeded, so a failure repeats.
 */
@RunWith(AndroidJUnit4::class)
class RandomOperationsTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var customers: RoomCustomerRepository
    private lateinit var purchasing: RoomPurchasingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        customers = RoomCustomerRepository(database, TestData.clock)
        purchasing = RoomPurchasingRepository(database, TestData.clock) { ZoneOffset.UTC }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        return inventory.getProduct((inventory.saveProduct(fresh) as SaveProductResult.Saved).productId)!!
    }

    @Test
    fun manyRandomOperations_keepTheBooksReconciled() = runTest {
        val random = Random(20261011)
        val shirt = saveNew(TestData.tShirt())
        val biscuits = saveNew(TestData.biscuits())
        val supplierId = purchasing.saveSupplier(Supplier(name = "Fresh Mills"))
        val limited = (customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345", creditLimit = Money(300_000))) as SaveCustomerResult.Saved).customerId
        val customer = customers.getCustomer(limited)!!
        val sellable = listOf(shirt to shirt.variants[0], shirt to shirt.variants[1], biscuits to biscuits.variants[0])
        val saleIds = mutableListOf<Long>()
        val counts = mutableMapOf<String, Int>()
        fun count(what: String) = counts.merge(what, 1, Int::plus)

        repeat(200) { step ->
            when (random.nextInt(6)) {
                0, 1, 2 -> {
                    val onAccount = random.nextInt(3) == 0
                    val picked = sellable.shuffled(random).take(random.nextInt(1, 3))
                    val cart = Cart(
                        lines = picked.map { (p, v) -> CartLine.from(p, v, random.nextInt(1, 5)) },
                        customer = if (onAccount) CustomerDetails(name = customer.name, phone = customer.phone.orEmpty(), customerId = customer.id) else CustomerDetails(),
                    )
                    val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
                    val credit = if (onAccount) Money(totals.grandTotal.minorUnits / 2) else Money.ZERO
                    val payments = listOfNotNull(
                        (totals.grandTotal - credit).takeIf { !it.isZero }?.let { PaymentRecord(method = PaymentMethod.UPI, amount = it) },
                        credit.takeIf { !it.isZero }?.let { PaymentRecord(method = PaymentMethod.CREDIT, amount = it) },
                    )
                    val result = billing.recordSale(SaleBuilder.build(cart, totals, TestData.settings, payments, TestData.clock.millis()), "INV", "sale-$step")
                    if (result is SaleResult.Recorded) saleIds += result.invoiceId
                    count("sale:" + result::class.simpleName)
                }
                3 -> if (saleIds.isNotEmpty()) {
                    val original = billing.getInvoice(saleIds.random(random))!!
                    val open = original.lines.filter { it.returnableQuantity > 0 }
                    if (open.isNotEmpty()) {
                        val line = open.random(random)
                        val method = if (original.customerId != null && random.nextBoolean()) PaymentMethod.CREDIT else PaymentMethod.CASH
                        val build = ReturnBuilder.build(original, mapOf(line.id to random.nextInt(1, line.returnableQuantity + 1)), method, "x", TestData.clock.millis())
                        if (build is ReturnBuild.Ready) count("return:" + billing.recordReturn(build.creditNote, "CN", "ret-$step")::class.simpleName)
                    }
                }
                4 -> {
                    val variant = biscuits.variants[0]
                    val line = PurchaseReceiptLine(
                        variantId = variant.id, productName = biscuits.name, variantLabel = variant.attributesLabel, stockUnit = biscuits.stockUnit,
                        quantity = random.nextInt(1, 11), unitCost = Money(2_000), batchNumber = "B${random.nextInt(1, 4)}",
                        expiryDate = TestData.today.plusDays(random.nextLong(5, 200)),
                    )
                    count("receive:" + purchasing.receive(PurchaseReceipt(supplierId = supplierId, lines = listOf(line)), "recv-$step")::class.simpleName)
                }
                else -> {
                    val balance = customers.observeAccount(customer.id).first()!!.balance
                    if (balance > Money.ZERO) {
                        val amount = Money(random.nextLong(1, balance.minorUnits + 1))
                        count("payment:" + customers.receivePayment(customer.id, amount, PaymentMethod.CASH, null, null, "pay-$step")::class.simpleName)
                    }
                }
            }
            if (step % 10 == 9) DataIntegrity.assertConsistent(database)
        }
        DataIntegrity.assertConsistent(database)
        // The run really did exercise sales, returns, receipts and payments.
        assertTrue("$counts", counts.keys.any { it == "sale:Recorded" } && counts.keys.any { it == "return:Recorded" })
        assertTrue("$counts", counts.keys.any { it.startsWith("receive:Recorded") } && counts.keys.any { it.startsWith("payment:Recorded") })
    }
}
