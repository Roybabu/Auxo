package com.auxo.pos.data.security

import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.audit.AuditDraft
import com.auxo.pos.core.audit.AuditEntity
import com.auxo.pos.core.audit.AuditTrail
import com.auxo.pos.core.auth.Authorizer
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.PermissionDeniedException
import com.auxo.pos.domain.billing.DiscountPolicy
import com.auxo.pos.data.repository.AccountPaymentResult
import com.auxo.pos.data.repository.BarcodeMatch
import com.auxo.pos.data.repository.BillingRepository
import com.auxo.pos.data.repository.CustomerRepository
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.MenuRepository
import com.auxo.pos.data.repository.OrderUpdateResult
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.data.repository.ReceiveResult
import com.auxo.pos.data.repository.ReturnResult
import com.auxo.pos.data.repository.SalesTotals
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveCustomerResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.data.repository.StockChangeResult
import com.auxo.pos.domain.model.BatchDetails
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.MenuItem
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderStatus
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.model.StockMovementRecord
import com.auxo.pos.domain.model.StoreSettings
import com.auxo.pos.domain.model.Supplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Repositories wrapped so that every sensitive read and write checks [Authorizer] first. A refused call throws
 * PermissionDeniedException before any data is read or changed. Reads are checked when the flow starts being
 * collected, not when it is created, so a flow that is built early is still checked at the moment it is used.
 */
private fun <T> Authorizer.guarded(permission: Permission, source: () -> Flow<T>): Flow<T> = flow {
    require(permission)
    source().collect { emit(it) }
}

class AuthorizedBillingRepository(
    private val inner: BillingRepository,
    private val auth: Authorizer,
    private val audit: AuditTrail,
    /** Told after a sale has been saved, for "back up every N invoices". It must not throw. */
    private val onSaleRecorded: () -> Unit = {},
    /**
     * The most an Employee may discount, in basis points (read from the store settings when a sale is saved).
     * Defaults to none, so a repository built without it refuses an Employee's discount rather than allowing it.
     */
    private val employeeDiscountLimit: suspend () -> Int = { 0 },
) : BillingRepository {
    override fun observeInvoice(invoiceId: Long): Flow<Invoice?> = auth.guarded(Permission.VIEW_INVOICES) { inner.observeInvoice(invoiceId) }

    override suspend fun getInvoice(invoiceId: Long): Invoice? {
        auth.require(Permission.VIEW_INVOICES)
        return inner.getInvoice(invoiceId)
    }

    override fun observeInvoices(fromMillis: Long, toMillis: Long): Flow<List<InvoiceSummary>> =
        auth.guarded(Permission.VIEW_INVOICES) { inner.observeInvoices(fromMillis, toMillis) }

    override fun observeCreditNotesFor(invoiceId: Long): Flow<List<InvoiceSummary>> =
        auth.guarded(Permission.VIEW_INVOICES) { inner.observeCreditNotesFor(invoiceId) }

    override fun observePayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>> =
        auth.guarded(Permission.VIEW_FINANCIALS) { inner.observePayments(fromMillis, toMillis) }

    override fun observeSalesTotals(fromMillis: Long, toMillis: Long): Flow<SalesTotals> =
        auth.guarded(Permission.VIEW_FINANCIALS) { inner.observeSalesTotals(fromMillis, toMillis) }

    override suspend fun recordSale(invoice: Invoice, invoicePrefix: String, requestId: String?): SaleResult {
        auth.require(Permission.SELL)
        // The screen already stops an Employee, but this is the check that cannot be walked around.
        if (!auth.can(Permission.UNLIMITED_DISCOUNT)) {
            val counter = invoice.discountTotal - invoice.loyaltyDiscount
            if (DiscountPolicy.exceeds(counter, invoice.subtotal, employeeDiscountLimit(), roundingSlack = invoice.lines.size + 1L)) {
                throw PermissionDeniedException(Permission.UNLIMITED_DISCOUNT)
            }
        }
        val result = audit.recording({ inner.recordSale(invoice, invoicePrefix, requestId) }) { saleResult ->
            (saleResult as? SaleResult.Recorded)?.let {
                AuditDraft(AuditAction.SALE_RECORDED, AuditEntity.INVOICE, it.invoiceId, saleDetail(it.number, invoice))
            }
        }
        if (result is SaleResult.Recorded) runCatching { onSaleRecorded() }
        return result
    }

    private fun saleDetail(number: String, invoice: Invoice): String {
        val base = "Sale $number, total ${invoice.grandTotal.toDecimalString()}"
        val given = invoice.discountTotal - invoice.loyaltyDiscount
        if (given <= Money.ZERO) return base
        val reasons = DiscountPolicy.reasonsOn(invoice.lines.map { it.discountReason }, invoice.discountReason)
        return base + ", discount ${given.toDecimalString()}" + if (reasons.isEmpty()) "" else " (${reasons.joinToString("; ")})"
    }

    override suspend fun previewInvoiceNumber(invoicePrefix: String, day: java.time.LocalDate): String {
        auth.require(Permission.SELL)
        return inner.previewInvoiceNumber(invoicePrefix, day)
    }

    override suspend fun invoicesBetween(fromMillis: Long, toMillis: Long): List<Invoice> {
        auth.require(Permission.VIEW_REPORTS)
        return inner.invoicesBetween(fromMillis, toMillis)
    }

    override suspend fun recordReturn(creditNote: Invoice, creditNotePrefix: String, requestId: String?): ReturnResult {
        auth.require(Permission.PROCESS_RETURN)
        return audit.recording({ inner.recordReturn(creditNote, creditNotePrefix, requestId) }) { result ->
            (result as? ReturnResult.Recorded)?.let {
                AuditDraft(
                    AuditAction.RETURN_RECORDED,
                    AuditEntity.INVOICE,
                    it.creditNoteId,
                    "Credit note ${it.number} against ${creditNote.originalInvoiceNumber.orEmpty()}, ${creditNote.grandTotal.toDecimalString()}",
                )
            }
        }
    }
}

/** Billing needs to read the store's settings (tax, invoice prefix, shop name); only the Super Admin may change them. */
class AuthorizedSettingsRepository(private val inner: SettingsRepository, private val auth: Authorizer, private val audit: AuditTrail) : SettingsRepository {
    override fun observeSettings(): Flow<StoreSettings> = auth.guarded(Permission.SELL) { inner.observeSettings() }

    override suspend fun getSettings(): StoreSettings {
        auth.require(Permission.SELL)
        return inner.getSettings()
    }

    override suspend fun saveSettings(settings: StoreSettings) {
        auth.require(Permission.MANAGE_SETTINGS)
        // Whoever may edit settings, only the Super Admin sets what Employees may discount.
        val before = inner.getSettings().employeeDiscountLimitBp
        val limitChanged = before != settings.employeeDiscountLimitBp
        val daysBefore = inner.getSettings().expiryWarningDays
        if (limitChanged) auth.require(Permission.UNLIMITED_DISCOUNT)
        audit.recording({ inner.saveSettings(settings) }) {
            val limit = if (limitChanged) ", employee discount limit ${before / 100.0}% to ${settings.employeeDiscountLimitBp / 100.0}%" else ""
            val expiry = if (daysBefore != settings.expiryWarningDays) ", expiry warning $daysBefore to ${settings.expiryWarningDays} days" else ""
            AuditDraft(AuditAction.SETTINGS_SAVED, AuditEntity.SETTINGS, 1L, "Store settings saved for ${settings.storeName}$limit$expiry")
        }
    }
}

/** Reading the rules is part of billing; changing them is for the Super Admin only ([Permission.MANAGE_LOYALTY]). */
class AuthorizedLoyaltyRepository(
    private val inner: com.auxo.pos.data.repository.LoyaltyRepository,
    private val auth: Authorizer,
    private val audit: AuditTrail,
) : com.auxo.pos.data.repository.LoyaltyRepository {
    override fun observeRules(): Flow<com.auxo.pos.domain.loyalty.LoyaltyRules> = auth.guarded(Permission.SELL) { inner.observeRules() }

    override suspend fun getRules(): com.auxo.pos.domain.loyalty.LoyaltyRules {
        auth.require(Permission.SELL)
        return inner.getRules()
    }

    override suspend fun saveRules(rules: com.auxo.pos.domain.loyalty.LoyaltyRules) {
        auth.require(Permission.MANAGE_LOYALTY)
        audit.recording({ inner.saveRules(rules) }) {
            val state = if (rules.enabled) "on" else "off"
            AuditDraft(
                AuditAction.LOYALTY_CHANGED,
                AuditEntity.LOYALTY,
                1L,
                "Loyalty points $state: 1 point per Rs ${rules.spendPerPoint.toDecimalString()} spent, each point worth Rs ${rules.pointValue.toDecimalString()}",
            )
        }
    }
}

class AuthorizedInventoryRepository(private val inner: InventoryRepository, private val auth: Authorizer, private val audit: AuditTrail) : InventoryRepository {
    // Looking products up is part of billing.
    override fun observeProducts(): Flow<List<Product>> = auth.guarded(Permission.SELL) { inner.observeProducts() }

    override suspend fun getProduct(productId: Long): Product? {
        auth.require(Permission.SELL)
        return inner.getProduct(productId)
    }

    override suspend fun findByBarcode(barcode: String): BarcodeMatch? {
        auth.require(Permission.SELL)
        return inner.findByBarcode(barcode)
    }

    override fun observeProduct(productId: Long): Flow<Product?> = auth.guarded(Permission.MANAGE_PRODUCTS) { inner.observeProduct(productId) }

    override fun observeStockMovements(productId: Long, limit: Int): Flow<List<StockMovement>> =
        auth.guarded(Permission.VIEW_STOCK_RECORDS) { inner.observeStockMovements(productId, limit) }

    override suspend fun isBarcodeInUse(barcode: String): Boolean {
        auth.require(Permission.MANAGE_PRODUCTS)
        return inner.isBarcodeInUse(barcode)
    }

    override suspend fun saveProduct(product: Product): SaveProductResult {
        auth.require(Permission.MANAGE_PRODUCTS)
        return audit.recording({ inner.saveProduct(product) }) { result ->
            (result as? SaveProductResult.Saved)?.let {
                val verb = if (product.id == 0L) "Added" else "Changed"
                AuditDraft(AuditAction.PRODUCT_SAVED, AuditEntity.PRODUCT, it.productId, "$verb product ${product.name}")
            }
        }
    }

    override suspend fun deleteProduct(productId: Long) {
        auth.require(Permission.MANAGE_PRODUCTS)
        val name = inner.getProduct(productId)?.name
        audit.recording({ inner.deleteProduct(productId) }) {
            AuditDraft(AuditAction.PRODUCT_DELETED, AuditEntity.PRODUCT, productId, "Deleted product ${name ?: "#$productId"}")
        }
    }

    override suspend fun recordStockChange(movement: StockMovement, batch: BatchDetails, requestId: String?): StockChangeResult {
        auth.require(Permission.ADJUST_STOCK)
        return audit.recording({ inner.recordStockChange(movement, batch, requestId) }) { result ->
            (result as? StockChangeResult.Applied)?.let {
                AuditDraft(
                    AuditAction.STOCK_CHANGED,
                    AuditEntity.VARIANT,
                    movement.variantId,
                    "Stock ${if (movement.quantityChange >= 0) "+" else ""}${movement.quantityChange} (${movement.reason.name.lowercase()})",
                )
            }
        }
    }

    override fun observeBatches(): Flow<List<BatchWithProduct>> = auth.guarded(Permission.VIEW_STOCK_RECORDS) { inner.observeBatches() }

    override suspend fun writeOff(batchId: Long, quantity: Int, reason: StockChangeReason, note: String?, requestId: String): StockChangeResult {
        auth.require(Permission.ADJUST_STOCK)
        return audit.recording({ inner.writeOff(batchId, quantity, reason, note, requestId) }) { result ->
            (result as? StockChangeResult.Applied)?.let {
                AuditDraft(AuditAction.STOCK_WRITTEN_OFF, AuditEntity.BATCH, batchId, "Wrote off $quantity (${reason.name.lowercase()})")
            }
        }
    }

    override fun observeMovementHistory(fromMillis: Long, toMillis: Long): Flow<List<StockMovementRecord>> =
        auth.guarded(Permission.VIEW_STOCK_RECORDS) { inner.observeMovementHistory(fromMillis, toMillis) }
}

class AuthorizedCustomerRepository(private val inner: CustomerRepository, private val auth: Authorizer, private val audit: AuditTrail) : CustomerRepository {
    // The bill's customer box searches and links saved customers, and can save a new one.
    override fun observeAccounts(): Flow<List<CustomerAccount>> = auth.guarded(Permission.SELL) { inner.observeAccounts() }

    override fun observeAccount(customerId: Long): Flow<CustomerAccount?> = auth.guarded(Permission.SELL) { inner.observeAccount(customerId) }

    override suspend fun getCustomer(customerId: Long): Customer? {
        auth.require(Permission.SELL)
        return inner.getCustomer(customerId)
    }

    override suspend fun findByPhone(phone: String): Customer? {
        auth.require(Permission.SELL)
        return inner.findByPhone(phone)
    }

    override suspend fun saveCustomer(customer: Customer, openingBalance: Money): SaveCustomerResult {
        // Adding a brand-new customer from the bill is billing data entry. Editing one, or starting a balance, is management.
        val addingFromBill = customer.id == 0L && openingBalance.isZero
        auth.require(if (addingFromBill) Permission.SELL else Permission.MANAGE_CUSTOMERS)
        return audit.recording({ inner.saveCustomer(customer, openingBalance) }) { result ->
            (result as? SaveCustomerResult.Saved)?.let {
                val verb = if (customer.id == 0L) "Added" else "Changed"
                AuditDraft(AuditAction.CUSTOMER_SAVED, AuditEntity.CUSTOMER, it.customerId, "$verb customer ${customer.name}")
            }
        }
    }

    override fun observeLedger(customerId: Long): Flow<List<LedgerEntry>> = auth.guarded(Permission.MANAGE_CUSTOMERS) { inner.observeLedger(customerId) }

    override fun observeInvoices(customerId: Long): Flow<List<InvoiceSummary>> =
        auth.guarded(Permission.MANAGE_CUSTOMERS) { inner.observeInvoices(customerId) }

    override suspend fun receivePayment(
        customerId: Long,
        amount: Money,
        method: PaymentMethod,
        reference: String?,
        note: String?,
        requestId: String,
    ): AccountPaymentResult {
        auth.require(Permission.RECEIVE_ACCOUNT_PAYMENT)
        return audit.recording({ inner.receivePayment(customerId, amount, method, reference, note, requestId) }) { result ->
            (result as? AccountPaymentResult.Recorded)?.let {
                AuditDraft(
                    AuditAction.ACCOUNT_PAYMENT_RECEIVED,
                    AuditEntity.CUSTOMER,
                    customerId,
                    "Received ${amount.toDecimalString()} by ${method.name.lowercase()} on account",
                )
            }
        }
    }

    override fun observeAccountPayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>> =
        auth.guarded(Permission.VIEW_FINANCIALS) { inner.observeAccountPayments(fromMillis, toMillis) }
}

class AuthorizedPurchasingRepository(private val inner: PurchasingRepository, private val auth: Authorizer, private val audit: AuditTrail) : PurchasingRepository {
    override fun observeSuppliers(): Flow<List<Supplier>> = auth.guarded(Permission.MANAGE_SUPPLIERS) { inner.observeSuppliers() }

    override fun observeSupplier(supplierId: Long): Flow<Supplier?> = auth.guarded(Permission.MANAGE_SUPPLIERS) { inner.observeSupplier(supplierId) }

    override suspend fun getSupplier(supplierId: Long): Supplier? {
        auth.require(Permission.MANAGE_SUPPLIERS)
        return inner.getSupplier(supplierId)
    }

    override suspend fun saveSupplier(supplier: Supplier): Long {
        auth.require(Permission.MANAGE_SUPPLIERS)
        return audit.recording({ inner.saveSupplier(supplier) }) {
            AuditDraft(AuditAction.SUPPLIER_SAVED, AuditEntity.SUPPLIER, it, "${if (supplier.id == 0L) "Added" else "Changed"} supplier ${supplier.name}")
        }
    }

    override fun observeOrders(): Flow<List<PurchaseOrder>> = auth.guarded(Permission.MANAGE_PURCHASES) { inner.observeOrders() }

    override fun observeOrder(orderId: Long): Flow<PurchaseOrder?> = auth.guarded(Permission.MANAGE_PURCHASES) { inner.observeOrder(orderId) }

    override suspend fun getOrder(orderId: Long): PurchaseOrder? {
        auth.require(Permission.MANAGE_PURCHASES)
        return inner.getOrder(orderId)
    }

    override suspend fun saveDraftOrder(order: PurchaseOrder): OrderUpdateResult {
        auth.require(Permission.MANAGE_PURCHASES)
        return audit.recording({ inner.saveDraftOrder(order) }) { result ->
            (result as? OrderUpdateResult.Saved)?.let {
                AuditDraft(AuditAction.ORDER_SAVED, AuditEntity.PURCHASE_ORDER, it.orderId, "Saved draft purchase order for ${order.supplierName}")
            }
        }
    }

    override suspend fun createDraftOrders(orders: List<PurchaseOrder>): List<Long> {
        auth.require(Permission.MANAGE_PURCHASES)
        return audit.recording({ inner.createDraftOrders(orders) }) { ids ->
            ids.firstOrNull()?.let { AuditDraft(AuditAction.ORDER_SAVED, AuditEntity.PURCHASE_ORDER, it, "Created ${ids.size} draft purchase order(s)") }
        }
    }

    override suspend fun setOrderStatus(orderId: Long, status: PurchaseOrderStatus): Boolean {
        auth.require(Permission.MANAGE_PURCHASES)
        return audit.recording({ inner.setOrderStatus(orderId, status) }) { changed ->
            if (changed) AuditDraft(AuditAction.ORDER_STATUS_CHANGED, AuditEntity.PURCHASE_ORDER, orderId, "Purchase order marked ${status.name.lowercase()}") else null
        }
    }

    override fun observeReceipts(fromMillis: Long, toMillis: Long): Flow<List<PurchaseReceipt>> =
        auth.guarded(Permission.MANAGE_PURCHASES) { inner.observeReceipts(fromMillis, toMillis) }

    override fun observeReceipt(receiptId: Long): Flow<PurchaseReceipt?> = auth.guarded(Permission.MANAGE_PURCHASES) { inner.observeReceipt(receiptId) }

    override fun observeReceiptsForOrder(orderId: Long): Flow<List<PurchaseReceipt>> =
        auth.guarded(Permission.MANAGE_PURCHASES) { inner.observeReceiptsForOrder(orderId) }

    override suspend fun receive(receipt: PurchaseReceipt, requestId: String, updateCostPrices: Boolean): ReceiveResult {
        auth.require(Permission.RECEIVE_GOODS)
        return audit.recording({ inner.receive(receipt, requestId, updateCostPrices) }) { result ->
            (result as? ReceiveResult.Recorded)?.let {
                AuditDraft(AuditAction.GOODS_RECEIVED, AuditEntity.PURCHASE_RECEIPT, it.receiptId, "Received goods ${it.number}")
            }
        }
    }
}

class AuthorizedMenuRepository(private val inner: MenuRepository, private val auth: Authorizer, private val audit: AuditTrail) : MenuRepository {
    // Dishes are sold from the bill, so reading the menu is part of billing.
    override fun observeMenu(): Flow<List<MenuItem>> = auth.guarded(Permission.SELL) { inner.observeMenu() }

    override suspend fun getItem(menuItemId: Long): MenuItem? {
        auth.require(Permission.SELL)
        return inner.getItem(menuItemId)
    }

    override suspend fun saveItem(item: MenuItem): Long {
        auth.require(Permission.MANAGE_MENU)
        return audit.recording({ inner.saveItem(item) }) {
            AuditDraft(AuditAction.MENU_ITEM_SAVED, AuditEntity.MENU_ITEM, it, "${if (item.id == 0L) "Added" else "Changed"} dish ${item.name}")
        }
    }

    override suspend fun setAvailable(menuItemId: Long, available: Boolean) {
        auth.require(Permission.MANAGE_MENU)
        audit.recording({ inner.setAvailable(menuItemId, available) }) {
            AuditDraft(AuditAction.MENU_ITEM_AVAILABILITY_CHANGED, AuditEntity.MENU_ITEM, menuItemId, "Dish marked ${if (available) "available" else "unavailable"}")
        }
    }

    override suspend fun deleteItem(menuItemId: Long) {
        auth.require(Permission.MANAGE_MENU)
        val name = inner.getItem(menuItemId)?.name
        audit.recording({ inner.deleteItem(menuItemId) }) {
            AuditDraft(AuditAction.MENU_ITEM_DELETED, AuditEntity.MENU_ITEM, menuItemId, "Deleted dish ${name ?: "#$menuItemId"}")
        }
    }
}

/**
 * Day-end closing for whoever holds [Permission.CLOSE_DAY] (the Super Admin always does). Closing a day writes
 * its activity-log line in the same transaction, naming who closed it and how the cash compared.
 */
class AuthorizedDayEndRepository(
    private val inner: com.auxo.pos.data.repository.DayEndRepository,
    private val auth: Authorizer,
    private val audit: AuditTrail,
) : com.auxo.pos.data.repository.DayEndRepository {

    override fun today() = inner.today()

    override suspend fun figuresFor(date: java.time.LocalDate): com.auxo.pos.domain.dayend.DayFigures {
        auth.require(Permission.CLOSE_DAY)
        return inner.figuresFor(date)
    }

    override suspend fun closingFor(date: java.time.LocalDate): com.auxo.pos.domain.dayend.DayClosing? {
        auth.require(Permission.CLOSE_DAY)
        return inner.closingFor(date)
    }

    override suspend fun getClosing(id: Long): com.auxo.pos.domain.dayend.DayClosing? {
        auth.require(Permission.CLOSE_DAY)
        return inner.getClosing(id)
    }

    override fun observeClosings(): Flow<List<com.auxo.pos.domain.dayend.DayClosing>> =
        auth.guarded(Permission.CLOSE_DAY) { inner.observeClosings() }

    override suspend fun closeDay(date: java.time.LocalDate, counted: Money, note: String?, closedBy: String): com.auxo.pos.data.repository.CloseDayResult {
        auth.require(Permission.CLOSE_DAY)
        // The person recorded is the one signed in, whatever name the caller passed.
        val who = auth.user?.name ?: closedBy
        return audit.recording({ inner.closeDay(date, counted, note, who) }) { result ->
            (result as? com.auxo.pos.data.repository.CloseDayResult.Closed)?.let {
                val c = it.closing
                AuditDraft(
                    AuditAction.DAY_CLOSED,
                    AuditEntity.DAY_CLOSING,
                    c.id,
                    "Closed ${c.date}: expected cash ${c.figures.expectedCash.toDecimalString()}, counted ${c.counted.toDecimalString()}, " +
                        "difference ${c.difference.toDecimalString()}",
                )
            }
        }
    }
}

/**
 * Builds the GST report for the Super Admin. It reads saved invoices and credit notes through [billing] and
 * refuses anyone who does not hold [Permission.GST_REPORT], which no employee can be given.
 */
class GstReportService(
    private val billing: BillingRepository,
    private val auth: Authorizer,
) {
    suspend fun report(period: com.auxo.pos.domain.reports.ReportPeriod, zone: java.time.ZoneId): com.auxo.pos.domain.gst.GstReport {
        auth.require(Permission.GST_REPORT)
        val (from, to) = period.bounds(zone)
        return com.auxo.pos.domain.gst.GstReportBuilder.build(billing.invoicesBetween(from, to), period, zone)
    }
}

/** Parking and resuming bills is part of billing, so it needs the same permission as selling. */
class AuthorizedHeldBillRepository(
    private val inner: com.auxo.pos.data.repository.HeldBillRepository,
    private val auth: Authorizer,
) : com.auxo.pos.data.repository.HeldBillRepository {
    override fun observeHeld(): Flow<List<com.auxo.pos.domain.billing.HeldBillSummary>> =
        auth.guarded(Permission.SELL) { inner.observeHeld() }

    override suspend fun hold(cart: com.auxo.pos.domain.billing.Cart, total: Money, label: String, replacingId: Long?): Long {
        auth.require(Permission.SELL)
        return inner.hold(cart, total, label, replacingId)
    }

    override suspend fun get(id: Long): com.auxo.pos.domain.billing.HeldBill? {
        auth.require(Permission.SELL)
        return inner.get(id)
    }

    override suspend fun discard(id: Long) {
        auth.require(Permission.SELL)
        inner.discard(id)
    }

    override suspend fun placeOrder(
        cart: com.auxo.pos.domain.billing.Cart,
        total: Money,
        label: String,
        replacingId: Long?,
    ): com.auxo.pos.domain.billing.PlaceOrderResult {
        auth.require(Permission.SELL)
        return inner.placeOrder(cart, total, label, replacingId)
    }
}

/** Making and converting quotations is part of billing, so it needs the same permission as selling. */
class AuthorizedQuotationRepository(
    private val inner: com.auxo.pos.data.repository.QuotationRepository,
    private val auth: Authorizer,
) : com.auxo.pos.data.repository.QuotationRepository {
    override fun observeAll(): Flow<List<com.auxo.pos.domain.billing.QuotationSummary>> =
        auth.guarded(Permission.SELL) { inner.observeAll() }

    override suspend fun save(cart: com.auxo.pos.domain.billing.Cart, total: Money, validUntil: java.time.LocalDate): com.auxo.pos.domain.billing.QuotationSummary {
        auth.require(Permission.SELL)
        return inner.save(cart, total, validUntil)
    }

    override suspend fun get(id: Long): com.auxo.pos.domain.billing.Quotation? {
        auth.require(Permission.SELL)
        return inner.get(id)
    }

    override suspend fun delete(id: Long) {
        auth.require(Permission.SELL)
        inner.delete(id)
    }

    override suspend fun markConverted(id: Long, invoiceId: Long): Boolean {
        auth.require(Permission.SELL)
        return inner.markConverted(id, invoiceId)
    }
}

/**
 * Counting stock and posting the differences is stock adjustment, so it needs [Permission.ADJUST_STOCK].
 * Starting, cancelling and posting a count are written to the activity log; the adjustments themselves are
 * logged one by one by the stock repository.
 */
class AuthorizedStockCountRepository(
    private val inner: com.auxo.pos.data.repository.StockCountRepository,
    private val auth: Authorizer,
    private val audit: AuditTrail,
) : com.auxo.pos.data.repository.StockCountRepository {
    override fun observeOpen(): Flow<com.auxo.pos.domain.inventory.StockCount?> =
        auth.guarded(Permission.ADJUST_STOCK) { inner.observeOpen() }

    override suspend fun start(): com.auxo.pos.domain.inventory.StockCount {
        auth.require(Permission.ADJUST_STOCK)
        val alreadyOpen = inner.observeOpen().first() != null
        return audit.recording({ inner.start() }) { count ->
            if (alreadyOpen) null else AuditDraft(AuditAction.STOCK_COUNT_STARTED, AuditEntity.STOCK_COUNT, count.id, "Started stock count #${count.id}")
        }
    }

    override suspend fun setLine(countId: Long, line: com.auxo.pos.domain.inventory.CountLine): Boolean {
        auth.require(Permission.ADJUST_STOCK)
        return inner.setLine(countId, line)
    }

    override suspend fun removeLine(countId: Long, variantId: Long): Boolean {
        auth.require(Permission.ADJUST_STOCK)
        return inner.removeLine(countId, variantId)
    }

    override suspend fun cancel(countId: Long) {
        auth.require(Permission.ADJUST_STOCK)
        audit.recording({ inner.cancel(countId) }) {
            AuditDraft(AuditAction.STOCK_COUNT_CANCELLED, AuditEntity.STOCK_COUNT, countId, "Cancelled stock count #$countId without changing stock")
        }
    }

    override suspend fun markPosted(countId: Long, reason: com.auxo.pos.domain.inventory.CountReason, note: String, adjusted: Int): Boolean {
        auth.require(Permission.ADJUST_STOCK)
        return audit.recording({ inner.markPosted(countId, reason, note, adjusted) }) { closed ->
            if (closed) {
                AuditDraft(
                    AuditAction.STOCK_COUNT_POSTED,
                    AuditEntity.STOCK_COUNT,
                    countId,
                    "Posted stock count #$countId: $adjusted item(s) adjusted (${reason.label.lowercase()})",
                )
            } else {
                null
            }
        }
    }
}
