package com.invision.pos.domain.usecase

import com.invision.pos.data.repository.BillingRepository
import com.invision.pos.data.repository.ReturnResult
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.data.repository.SettingsRepository
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartCheck
import com.invision.pos.domain.billing.CartValidator
import com.invision.pos.domain.billing.PaymentCheck
import com.invision.pos.domain.billing.PaymentDraft
import com.invision.pos.domain.billing.PaymentValidator
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.ReturnError
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.StoreOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate

sealed interface CheckoutResult {
    data class Completed(val invoiceId: Long, val number: String, val change: Money) : CheckoutResult

    /** The cart can't be billed as it is; fix the listed problems first. */
    data class CartInvalid(val check: CartCheck) : CheckoutResult

    data class PaymentsInvalid(val check: PaymentCheck.Invalid) : CheckoutResult

    /** Stock changed since the cart was built; nothing was saved. */
    data class StockChanged(val variantIds: Set<Long>) : CheckoutResult

    /** Billing settings are incomplete (for example GSTIN missing for a GST-registered store). */
    data object SetupRequired : CheckoutResult
}

/**
 * Bills a cart: re-reads stock, recalculates totals, checks payments and saves
 * the invoice, payments and stock deduction together.
 */
class CheckoutUseCase(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(cart: Cart, drafts: List<PaymentDraft>): CheckoutResult {
        val store = settings.getSettings()
        if (store.needsGstinSetup || (store.gstRegistered && store.stateCode.isEmpty())) return CheckoutResult.SetupRequired

        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        val cartCheck = CartValidator.check(cart, totals, store.gstRegistered)
        if (!cartCheck.canCheckout) return CheckoutResult.CartInvalid(cartCheck)

        val payments = when (val check = PaymentValidator.check(drafts, totals.grandTotal)) {
            is PaymentCheck.Invalid -> return CheckoutResult.PaymentsInvalid(check)
            is PaymentCheck.Valid -> check
        }

        val invoice = SaleBuilder.build(cart, totals, store, payments.payments, clock.millis())
        return when (val result = billing.recordSale(invoice, store.invoicePrefix)) {
            is SaleResult.Recorded -> CheckoutResult.Completed(result.invoiceId, result.number, payments.change)
            is SaleResult.StockShortage -> CheckoutResult.StockChanged(result.variantIds)
        }
    }
}

sealed interface ProcessReturnResult {
    data class Completed(val creditNoteId: Long, val number: String, val refund: Money) : ProcessReturnResult
    data class Rejected(val error: ReturnError) : ProcessReturnResult

    /** Units were returned elsewhere in the meantime; nothing was saved. */
    data object AlreadyReturned : ProcessReturnResult
    data object InvoiceNotFound : ProcessReturnResult
}

class ProcessReturnUseCase(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        quantities: Map<Long, Int>,
        refundMethod: PaymentMethod,
        reason: String,
    ): ProcessReturnResult {
        val original = billing.getInvoice(invoiceId) ?: return ProcessReturnResult.InvoiceNotFound
        val store = settings.getSettings()
        val build = ReturnBuilder.build(original, quantities, refundMethod, reason, store.roundOffTotal, clock.millis())
        val creditNote = when (build) {
            is ReturnBuild.Rejected -> return ProcessReturnResult.Rejected(build.error)
            is ReturnBuild.Ready -> build.creditNote
        }
        return when (val result = billing.recordReturn(creditNote, store.creditNotePrefix)) {
            is ReturnResult.Recorded -> ProcessReturnResult.Completed(result.creditNoteId, result.number, creditNote.grandTotal)
            ReturnResult.QuantityExceedsSold -> ProcessReturnResult.AlreadyReturned
        }
    }
}

/** Store name plus today's net sales (sales minus refunds) and bill count, for Home. */
class ObserveStoreOverviewUseCase(
    private val settings: SettingsRepository,
    private val billing: BillingRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<StoreOverview> {
        val today = LocalDate.now(clock)
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return combine(settings.observeSettings(), billing.observeSalesTotals(start, end)) { store, totals ->
            StoreOverview(storeName = store.storeName, salesToday = totals.net, billsToday = totals.billCount)
        }
    }
}
