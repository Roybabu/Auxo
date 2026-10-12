package com.invision.pos.testutil

import com.invision.pos.data.repository.AccountPaymentResult
import com.invision.pos.data.repository.CustomerRepository
import com.invision.pos.data.repository.MenuRepository
import com.invision.pos.data.repository.OrderUpdateResult
import com.invision.pos.data.repository.PurchasingRepository
import com.invision.pos.data.repository.ReceiveResult
import com.invision.pos.data.repository.SaveCustomerResult
import com.invision.pos.domain.model.BatchDetails
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.LedgerEntry
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.PurchaseOrderStatus
import com.invision.pos.domain.model.PurchaseReceipt
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.model.Supplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * In-memory purchasing store. Receiving adds stock to [inventory] and follows the
 * Room rules: no over-receipt, one receipt per supplier bill, and a repeated
 * request id returns the first receipt.
 */
class FakePurchasingRepository(
    private val inventory: FakeInventoryRepository,
    suppliers: List<Supplier> = emptyList(),
) : PurchasingRepository {

    val suppliers = MutableStateFlow(suppliers)
    val orders = MutableStateFlow<List<PurchaseOrder>>(emptyList())
    val receipts = MutableStateFlow<List<PurchaseReceipt>>(emptyList())

    var failSaves = false
    var receiveCalls = 0
        private set

    private var nextSupplierId = (suppliers.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextOrderId = 1L
    private var nextLineId = 1L
    private var nextReceiptId = 1L
    private val requests = mutableMapOf<String, ReceiveResult.Recorded>()

    override fun observeSuppliers(): Flow<List<Supplier>> = suppliers

    override fun observeSupplier(supplierId: Long): Flow<Supplier?> = suppliers.map { list -> list.firstOrNull { it.id == supplierId } }

    override suspend fun getSupplier(supplierId: Long): Supplier? = suppliers.value.firstOrNull { it.id == supplierId }

    override suspend fun saveSupplier(supplier: Supplier): Long {
        if (failSaves) throw IOException("Simulated write failure")
        val id = if (supplier.id == 0L) nextSupplierId++ else supplier.id
        suppliers.value = suppliers.value.filterNot { it.id == id } + supplier.copy(id = id)
        return id
    }

    override fun observeOrders(): Flow<List<PurchaseOrder>> = combine(orders, suppliers) { list, s ->
        list.map { order -> order.copy(supplierName = s.firstOrNull { it.id == order.supplierId }?.name.orEmpty()) }
            .sortedByDescending { it.id }
    }

    override fun observeOrder(orderId: Long): Flow<PurchaseOrder?> = observeOrders().map { list -> list.firstOrNull { it.id == orderId } }

    override suspend fun getOrder(orderId: Long): PurchaseOrder? = orders.value.firstOrNull { it.id == orderId }
        ?.let { order -> order.copy(supplierName = suppliers.value.firstOrNull { it.id == order.supplierId }?.name.orEmpty()) }

    override suspend fun saveDraftOrder(order: PurchaseOrder): OrderUpdateResult {
        if (failSaves) throw IOException("Simulated write failure")
        if (order.id == 0L) {
            val id = insertDraft(order)
            return OrderUpdateResult.Saved(id, orders.value.first { it.id == id }.number)
        }
        val saved = orders.value.firstOrNull { it.id == order.id }
        if (saved == null || saved.status != PurchaseOrderStatus.DRAFT) return OrderUpdateResult.NotEditable
        val updated = order.copy(number = saved.number, status = PurchaseOrderStatus.DRAFT, lines = order.lines.map { it.copy(id = nextLineId++, quantityReceived = 0) })
        orders.value = orders.value.map { if (it.id == order.id) updated else it }
        return OrderUpdateResult.Saved(order.id, saved.number)
    }

    override suspend fun createDraftOrders(orders: List<PurchaseOrder>): List<Long> {
        if (failSaves) throw IOException("Simulated write failure")
        return orders.map { insertDraft(it) }
    }

    private fun insertDraft(order: PurchaseOrder): Long {
        val id = nextOrderId++
        val number = "PO/26-27/%05d".format(id)
        orders.value += order.copy(id = id, number = number, status = PurchaseOrderStatus.DRAFT, lines = order.lines.map { it.copy(id = nextLineId++, quantityReceived = 0) })
        return id
    }

    override suspend fun setOrderStatus(orderId: Long, status: PurchaseOrderStatus): Boolean {
        val current = orders.value.firstOrNull { it.id == orderId }?.status
        val allowed = when (status) {
            PurchaseOrderStatus.ORDERED -> current == PurchaseOrderStatus.DRAFT
            PurchaseOrderStatus.CANCELLED -> current?.isOpen == true
            else -> false
        }
        if (allowed) orders.value = orders.value.map { if (it.id == orderId) it.copy(status = status) else it }
        return allowed
    }

    override fun observeReceipts(fromMillis: Long, toMillis: Long): Flow<List<PurchaseReceipt>> =
        receipts.map { list -> list.filter { it.receivedAtMillis in fromMillis until toMillis }.sortedByDescending { it.id } }

    override fun observeReceipt(receiptId: Long): Flow<PurchaseReceipt?> = receipts.map { list -> list.firstOrNull { it.id == receiptId } }

    override fun observeReceiptsForOrder(orderId: Long): Flow<List<PurchaseReceipt>> = receipts.map { list -> list.filter { it.orderId == orderId } }

    override suspend fun receive(receipt: PurchaseReceipt, requestId: String, updateCostPrices: Boolean): ReceiveResult {
        receiveCalls++
        requests[requestId]?.let { return it }
        val order = receipt.orderId?.let { id -> orders.value.firstOrNull { it.id == id } }
        if (receipt.orderId != null && order?.status?.isOpen != true) return ReceiveResult.OrderClosed
        val supplierId = order?.supplierId ?: receipt.supplierId
        val bill = receipt.supplierInvoiceNumber
        if (bill != null && receipts.value.any { it.supplierId == supplierId && it.supplierInvoiceNumber.equals(bill, ignoreCase = true) }) {
            return ReceiveResult.DuplicateSupplierInvoice
        }
        if (order != null) {
            val over = receipt.lines.filter { line ->
                val orderLine = order.lines.firstOrNull { it.id == line.orderLineId } ?: return@filter false
                line.quantity > orderLine.remaining
            }.mapNotNull { it.orderLineId }.toSet()
            if (over.isNotEmpty()) return ReceiveResult.OverReceived(over)
        }
        val missing = receipt.lines.mapNotNull { it.variantId }.filter { id -> inventory.products.value.none { p -> p.variants.any { it.id == id } } }.toSet()
        if (missing.isNotEmpty()) return ReceiveResult.MissingItems(missing)

        val id = nextReceiptId++
        val number = "GRN/26-27/%05d".format(id)
        receipt.lines.forEach { line ->
            inventory.recordStockChange(
                StockMovement(variantId = line.variantId!!, quantityChange = line.quantity, reason = StockChangeReason.RECEIVED, createdAtMillis = receipt.receivedAtMillis),
                BatchDetails(line.batchNumber, line.expiryDate, line.unitCost),
            )
        }
        if (order != null) {
            val updatedLines = order.lines.map { orderLine ->
                val got = receipt.lines.filter { it.orderLineId == orderLine.id }.sumOf { it.quantity }
                orderLine.copy(quantityReceived = orderLine.quantityReceived + got)
            }
            val status = if (updatedLines.all { it.quantityReceived >= it.quantityOrdered }) PurchaseOrderStatus.RECEIVED else PurchaseOrderStatus.PARTLY_RECEIVED
            orders.value = orders.value.map { if (it.id == order.id) it.copy(lines = updatedLines, status = status) else it }
        }
        receipts.value += receipt.copy(
            id = id,
            number = number,
            supplierId = supplierId,
            supplierName = suppliers.value.firstOrNull { it.id == supplierId }?.name,
            orderNumber = order?.number,
        )
        return ReceiveResult.Recorded(id, number).also { requests[requestId] = it }
    }
}

/** In-memory customer accounts that follow the Room rules for payments. */
class FakeCustomerRepository(customers: List<Customer> = emptyList()) : CustomerRepository {

    val customers = MutableStateFlow(customers)
    val ledger = MutableStateFlow<List<LedgerEntry>>(emptyList())
    var failReads = false
    var failSaves = false

    private var nextCustomerId = (customers.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextEntryId = 1L
    private val requests = mutableSetOf<String>()

    fun addEntry(entry: LedgerEntry) {
        ledger.value += entry.copy(id = nextEntryId++)
    }

    override fun observeAccounts(): Flow<List<CustomerAccount>> = combine(customers, ledger) { list, entries ->
        if (failReads) throw IOException("Simulated read failure")
        list.map { customer -> account(customer, entries) }.sortedBy { it.customer.name.lowercase() }
    }

    private fun account(customer: Customer, entries: List<LedgerEntry>): CustomerAccount {
        val own = entries.filter { it.customerId == customer.id }
        return CustomerAccount(customer, own.fold(Money.ZERO) { acc, e -> acc + e.amount }, own.maxOfOrNull { it.createdAtMillis })
    }

    override fun observeAccount(customerId: Long): Flow<CustomerAccount?> = combine(customers, ledger) { list, entries ->
        list.firstOrNull { it.id == customerId }?.let { account(it, entries) }
    }

    override suspend fun getCustomer(customerId: Long): Customer? = customers.value.firstOrNull { it.id == customerId }

    override suspend fun findByPhone(phone: String): Customer? = customers.value.firstOrNull { it.phone == phone }

    override suspend fun saveCustomer(customer: Customer, openingBalance: Money): SaveCustomerResult {
        if (failSaves) throw IOException("Simulated write failure")
        val phone = customer.phone
        if (phone != null && customers.value.any { it.phone == phone && it.id != customer.id }) return SaveCustomerResult.PhoneInUse
        val id = if (customer.id == 0L) nextCustomerId++ else customer.id
        customers.value = customers.value.filterNot { it.id == id } + customer.copy(id = id)
        if (customer.id == 0L && !openingBalance.isZero) {
            addEntry(LedgerEntry(customerId = id, type = LedgerEntryType.OPENING_BALANCE, amount = openingBalance, createdAtMillis = 0))
        }
        return SaveCustomerResult.Saved(id)
    }

    override fun observeLedger(customerId: Long): Flow<List<LedgerEntry>> = ledger.map { list -> list.filter { it.customerId == customerId } }

    val invoices = MutableStateFlow<List<InvoiceSummary>>(emptyList())

    override fun observeInvoices(customerId: Long): Flow<List<InvoiceSummary>> = invoices

    override suspend fun receivePayment(
        customerId: Long,
        amount: Money,
        method: PaymentMethod,
        reference: String?,
        note: String?,
        requestId: String,
    ): AccountPaymentResult {
        if (!requests.add(requestId)) return AccountPaymentResult.Recorded(0)
        customers.value.firstOrNull { it.id == customerId } ?: return AccountPaymentResult.CustomerMissing.also { requests.remove(requestId) }
        val balance = ledger.value.filter { it.customerId == customerId }.fold(Money.ZERO) { acc, e -> acc + e.amount }
        if (amount > balance) {
            requests.remove(requestId)
            return AccountPaymentResult.MoreThanDue(balance)
        }
        addEntry(LedgerEntry(customerId = customerId, type = LedgerEntryType.PAYMENT, amount = Money(-amount.minorUnits), method = method, reference = reference, note = note, createdAtMillis = 0))
        return AccountPaymentResult.Recorded(ledger.value.last().id)
    }

    override fun observeAccountPayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>> = combine(customers, ledger) { list, entries ->
        entries.filter { it.type == LedgerEntryType.PAYMENT && it.createdAtMillis in fromMillis until toMillis }.map { entry ->
            PaymentRecord(
                method = entry.method ?: PaymentMethod.CASH,
                amount = Money(-entry.amount.minorUnits),
                createdAtMillis = entry.createdAtMillis,
                customerName = list.firstOrNull { it.id == entry.customerId }?.name,
            )
        }
    }
}

/** In-memory restaurant menu. */
class FakeMenuRepository(items: List<MenuItem> = emptyList()) : MenuRepository {

    val items = MutableStateFlow(items)
    var failSaves = false
    private var nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeMenu(): Flow<List<MenuItem>> =
        items.map { list -> list.sortedWith(compareBy({ it.section.lowercase() }, { it.sortOrder }, { it.name.lowercase() })) }

    override suspend fun getItem(menuItemId: Long): MenuItem? = items.value.firstOrNull { it.id == menuItemId }

    override suspend fun saveItem(item: MenuItem): Long {
        if (failSaves) throw IOException("Simulated write failure")
        val id = if (item.id == 0L) nextId++ else item.id
        items.value = items.value.filterNot { it.id == id } + item.copy(id = id)
        return id
    }

    override suspend fun setAvailable(menuItemId: Long, available: Boolean) {
        items.value = items.value.map { if (it.id == menuItemId) it.copy(available = available) else it }
    }

    override suspend fun deleteItem(menuItemId: Long) {
        items.value = items.value.filterNot { it.id == menuItemId }
    }
}
