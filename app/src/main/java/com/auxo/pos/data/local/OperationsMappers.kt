package com.auxo.pos.data.local

import com.auxo.pos.data.local.entity.AccountPaymentRow
import com.auxo.pos.data.local.entity.CustomerAccountRow
import com.auxo.pos.data.local.entity.CustomerEntity
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.entity.LedgerEntryRow
import com.auxo.pos.data.local.entity.MenuItemEntity
import com.auxo.pos.data.local.entity.MenuItemWithRecipe
import com.auxo.pos.data.local.entity.PurchaseOrderEntity
import com.auxo.pos.data.local.entity.PurchaseOrderLineEntity
import com.auxo.pos.data.local.entity.PurchaseOrderWithLines
import com.auxo.pos.data.local.entity.PurchaseReceiptLineEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptWithLines
import com.auxo.pos.data.local.entity.RecipeLineEntity
import com.auxo.pos.data.local.entity.SupplierEntity
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.MenuItem
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.PurchaseOrder
import com.auxo.pos.domain.model.PurchaseOrderLine
import com.auxo.pos.domain.model.PurchaseOrderStatus
import com.auxo.pos.domain.model.PurchaseReceipt
import com.auxo.pos.domain.model.PurchaseReceiptLine
import com.auxo.pos.domain.model.RecipeLine
import com.auxo.pos.domain.model.Supplier
import java.time.LocalDate

// Suppliers and purchasing

fun SupplierEntity.toDomain(): Supplier = Supplier(
    id = id,
    name = name,
    contactPerson = contactPerson,
    phone = phone,
    email = email,
    gstin = gstin,
    stateCode = stateCode,
    address = address,
    notes = notes,
    active = active,
    createdAtMillis = createdAt,
)

fun Supplier.toEntity(nowMillis: Long): SupplierEntity = SupplierEntity(
    id = id,
    name = name,
    contactPerson = contactPerson,
    phone = phone,
    email = email,
    gstin = gstin,
    stateCode = stateCode,
    address = address,
    notes = notes,
    active = active,
    createdAt = if (createdAtMillis > 0) createdAtMillis else nowMillis,
    updatedAt = nowMillis,
)

fun PurchaseOrderWithLines.toDomain(): PurchaseOrder = PurchaseOrder(
    id = order.id,
    number = order.number,
    supplierId = order.supplierId,
    supplierName = supplier?.name.orEmpty(),
    status = enumValueOrNull<PurchaseOrderStatus>(order.status) ?: PurchaseOrderStatus.DRAFT,
    createdAtMillis = order.createdAt,
    expectedDate = order.expectedEpochDay?.let(LocalDate::ofEpochDay),
    note = order.note,
    lines = lines.sortedBy { it.id }.map { it.toDomain() },
)

fun PurchaseOrderLineEntity.toDomain(): PurchaseOrderLine = PurchaseOrderLine(
    id = id,
    variantId = variantId,
    productName = productName,
    variantLabel = variantLabel,
    stockUnit = stockUnitOf(stockUnit),
    quantityOrdered = quantityOrdered,
    quantityReceived = quantityReceived,
    unitCost = Money(unitCostMinor),
)

fun PurchaseOrder.toEntity(number: String, nowMillis: Long): PurchaseOrderEntity = PurchaseOrderEntity(
    id = id,
    number = number,
    supplierId = supplierId,
    status = status.name,
    createdAt = if (createdAtMillis > 0) createdAtMillis else nowMillis,
    updatedAt = nowMillis,
    expectedEpochDay = expectedDate?.toEpochDay(),
    note = note,
)

fun PurchaseOrderLine.toEntity(orderId: Long): PurchaseOrderLineEntity = PurchaseOrderLineEntity(
    id = 0,
    orderId = orderId,
    variantId = variantId,
    productName = productName,
    variantLabel = variantLabel,
    stockUnit = stockUnit.name,
    quantityOrdered = quantityOrdered,
    quantityReceived = quantityReceived,
    unitCostMinor = unitCost.minorUnits,
)

fun PurchaseReceiptWithLines.toDomain(): PurchaseReceipt = PurchaseReceipt(
    id = receipt.id,
    number = receipt.number,
    supplierId = receipt.supplierId,
    supplierName = supplier?.name,
    orderId = receipt.orderId,
    orderNumber = order?.number,
    supplierInvoiceNumber = receipt.supplierInvoiceNumber,
    receivedAtMillis = receipt.receivedAt,
    note = receipt.note,
    lines = lines.sortedBy { it.id }.map { it.toDomain() },
)

fun PurchaseReceiptLineEntity.toDomain(): PurchaseReceiptLine = PurchaseReceiptLine(
    id = id,
    variantId = variantId,
    orderLineId = orderLineId,
    productName = productName,
    variantLabel = variantLabel,
    stockUnit = stockUnitOf(stockUnit),
    quantity = quantity,
    unitCost = Money(unitCostMinor),
    batchNumber = batchNumber,
    expiryDate = expiryEpochDay?.let(LocalDate::ofEpochDay),
)

// Customers

fun CustomerEntity.toDomain(): Customer = Customer(
    id = id,
    name = name,
    phone = phone,
    email = email,
    gstin = gstin,
    stateCode = stateCode,
    address = address,
    creditLimit = creditLimitMinor?.let { Money(it) },
    notes = notes,
    createdAtMillis = createdAt,
)

fun Customer.toEntity(nowMillis: Long): CustomerEntity = CustomerEntity(
    id = id,
    name = name,
    phone = phone,
    email = email,
    gstin = gstin,
    stateCode = stateCode,
    address = address,
    creditLimitMinor = creditLimit?.minorUnits,
    notes = notes,
    createdAt = if (createdAtMillis > 0) createdAtMillis else nowMillis,
    updatedAt = nowMillis,
)

fun CustomerAccountRow.toDomain(): CustomerAccount = CustomerAccount(
    customer = customer.toDomain(),
    balance = Money(balanceMinor),
    lastActivityMillis = lastActivity,
    points = pointsBalance,
)

fun LedgerEntryRow.toDomain(): LedgerEntry = entry.toDomain(invoiceNumber)

fun LedgerEntryEntity.toDomain(invoiceNumber: String? = null): LedgerEntry = LedgerEntry(
    id = id,
    customerId = customerId,
    type = enumValueOrNull<LedgerEntryType>(type) ?: LedgerEntryType.SALE,
    amount = Money(amountMinor),
    invoiceId = invoiceId,
    invoiceNumber = invoiceNumber,
    method = method?.let { enumValueOrNull<PaymentMethod>(it) },
    reference = reference,
    note = note,
    createdAtMillis = createdAt,
    points = points,
)

fun LedgerEntry.toEntity(): LedgerEntryEntity = LedgerEntryEntity(
    id = id,
    customerId = customerId,
    type = type.name,
    amountMinor = amount.minorUnits,
    invoiceId = invoiceId,
    method = method?.name,
    reference = reference,
    note = note,
    createdAt = createdAtMillis,
    points = points,
)

/** A payment received against an account, shown with invoice payments. */
fun AccountPaymentRow.toPaymentRecord(): PaymentRecord = PaymentRecord(
    id = entry.id,
    invoiceId = 0,
    method = entry.method?.let { enumValueOrNull<PaymentMethod>(it) } ?: PaymentMethod.CASH,
    kind = PaymentKind.PAYMENT,
    amount = Money(-entry.amountMinor),
    reference = entry.reference,
    createdAtMillis = entry.createdAt,
    customerName = customerName,
)

// Menu

fun MenuItemWithRecipe.toDomain(): MenuItem = MenuItem(
    id = item.id,
    name = item.name,
    section = item.section,
    description = item.description,
    price = Money(item.priceMinor),
    gstRateBasisPoints = item.gstRateBasisPoints,
    sacCode = item.sacCode,
    dietType = item.dietType?.let { enumValueOrNull<DietType>(it) },
    available = item.available,
    deductIngredients = item.deductIngredients,
    sortOrder = item.sortOrder,
    recipe = recipe.sortedBy { it.id }.map { RecipeLine(variantId = it.variantId, quantity = it.quantity) },
)

fun MenuItem.toEntity(nowMillis: Long, createdAtMillis: Long? = null): MenuItemEntity = MenuItemEntity(
    id = id,
    name = name,
    section = section,
    description = description,
    priceMinor = price.minorUnits,
    gstRateBasisPoints = gstRateBasisPoints,
    sacCode = sacCode,
    dietType = dietType?.name,
    available = available,
    deductIngredients = deductIngredients,
    sortOrder = sortOrder,
    createdAt = createdAtMillis ?: nowMillis,
    updatedAt = nowMillis,
)

fun RecipeLine.toEntity(menuItemId: Long): RecipeLineEntity =
    RecipeLineEntity(menuItemId = menuItemId, variantId = variantId, quantity = quantity)
