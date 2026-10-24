// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.usecase

import com.auxo.pos.data.repository.AccountPaymentResult
import com.auxo.pos.data.repository.CustomerRepository
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.data.repository.ReceiveResult
import com.auxo.pos.data.repository.StockChangeResult
import com.auxo.pos.domain.customers.AccountPaymentCheck
import com.auxo.pos.domain.customers.AccountPaymentValidator
import com.auxo.pos.domain.inventory.ReorderPlanner
import com.auxo.pos.domain.inventory.ReorderSuggestion
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.purchasing.PurchaseLineForm
import com.auxo.pos.domain.purchasing.PurchaseValidator
import com.auxo.pos.domain.purchasing.ReceiptValidation
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

sealed interface ReceiveGoodsResult {
    data class Invalid(val validation: ReceiptValidation.Invalid) : ReceiveGoodsResult
    data class Saved(val result: ReceiveResult) : ReceiveGoodsResult
}

/** Checks a goods receipt form and saves it in one transaction. */
class ReceiveGoodsUseCase(
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        supplierId: Long?,
        orderId: Long?,
        supplierInvoiceNumber: String,
        lines: List<PurchaseLineForm>,
        note: String,
        requestId: String,
    ): ReceiveGoodsResult {
        val validation = PurchaseValidator.validateReceipt(supplierId, orderId, supplierInvoiceNumber, lines, note, LocalDate.now(clock))
        if (validation is ReceiptValidation.Invalid) return ReceiveGoodsResult.Invalid(validation)
        val receipt = (validation as ReceiptValidation.Valid).receipt.copy(receivedAtMillis = clock.millis())
        return ReceiveGoodsResult.Saved(purchasing.receive(receipt, requestId))
    }
}

enum class WastageError { QUANTITY_REQUIRED, QUANTITY_INVALID, MORE_THAN_IN_BATCH, NOT_WASTAGE, NOTE_TOO_LONG, STOCK_CHANGED }

sealed interface WastageResult {
    data object Done : WastageResult
    data class Rejected(val error: WastageError) : WastageResult
}

/** Writes off part or all of a batch as wastage (expired, damaged or spoiled). */
class RecordWastageUseCase(private val inventory: InventoryRepository) {

    suspend operator fun invoke(
        batch: BatchWithProduct,
        quantityText: String,
        reason: StockChangeReason,
        note: String,
        requestId: String,
    ): WastageResult {
        if (!reason.isWastage) return WastageResult.Rejected(WastageError.NOT_WASTAGE)
        if (quantityText.isBlank()) return WastageResult.Rejected(WastageError.QUANTITY_REQUIRED)
        val quantity = batch.stockUnit.parse(quantityText) ?: return WastageResult.Rejected(WastageError.QUANTITY_INVALID)
        if (quantity > batch.batch.quantity) return WastageResult.Rejected(WastageError.MORE_THAN_IN_BATCH)
        if (note.trim().length > NOTE_MAX) return WastageResult.Rejected(WastageError.NOTE_TOO_LONG)
        return when (inventory.writeOff(batch.batch.id, quantity, reason, note.trim().ifEmpty { null }, requestId)) {
            StockChangeResult.Applied -> WastageResult.Done
            StockChangeResult.WouldGoNegative, StockChangeResult.ItemMissing -> WastageResult.Rejected(WastageError.STOCK_CHANGED)
        }
    }

    companion object {
        const val NOTE_MAX = 120
    }
}

sealed interface ReceivePaymentResult {
    data class Invalid(val check: AccountPaymentCheck.Invalid) : ReceivePaymentResult
    data class Saved(val result: AccountPaymentResult) : ReceivePaymentResult
}

/** Records money received against a customer account. */
class ReceiveAccountPaymentUseCase(private val customers: CustomerRepository) {

    suspend operator fun invoke(
        customerId: Long,
        amountText: String,
        method: PaymentMethod,
        reference: String,
        note: String,
        requestId: String,
    ): ReceivePaymentResult {
        val balance = customers.observeAccount(customerId).first()?.balance ?: Money.ZERO
        return when (val check = AccountPaymentValidator.check(amountText, method, reference, note, balance)) {
            is AccountPaymentCheck.Invalid -> ReceivePaymentResult.Invalid(check)
            is AccountPaymentCheck.Valid -> ReceivePaymentResult.Saved(
                customers.receivePayment(customerId, check.amount, method, check.reference, check.note, requestId),
            )
        }
    }
}

/** Turns chosen low-stock items into draft purchase orders, one per supplier. */
class CreateReorderDraftsUseCase(private val purchasing: PurchasingRepository) {
    suspend operator fun invoke(selected: List<ReorderSuggestion>, fallbackSupplierId: Long?): List<Long> {
        val orders = ReorderPlanner.draftOrders(selected, fallbackSupplierId)
        return if (orders.isEmpty()) emptyList() else purchasing.createDraftOrders(orders)
    }
}
