package com.auxo.pos.domain.usecase

import com.auxo.pos.data.repository.BillingRepository
import com.auxo.pos.data.repository.CustomerRepository
import com.auxo.pos.data.repository.ReturnResult
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartCheck
import com.auxo.pos.domain.billing.CartValidator
import com.auxo.pos.domain.billing.CreditAccount
import com.auxo.pos.domain.billing.PaymentCheck
import com.auxo.pos.domain.billing.PaymentDraft
import com.auxo.pos.domain.billing.PaymentValidator
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.ReturnError
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.businessDays
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.StoreOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

sealed interface CheckoutResult {
    data class Completed(val invoiceId: Long, val number: String, val change: Money) : CheckoutResult

    /** The cart can't be billed as it is; fix the listed problems first. */
    data class CartInvalid(val check: CartCheck) : CheckoutResult

    data class PaymentsInvalid(val check: PaymentCheck.Invalid) : CheckoutResult

    /** Stock changed since the cart was built (or only expired stock is left); nothing was saved. */
    data class StockChanged(val variantIds: Set<Long>) : CheckoutResult

    /** The amount on account is more than the customer's credit limit allows; nothing was saved. */
    data object CreditLimitExceeded : CheckoutResult

    /** The customer account on the bill no longer exists; nothing was saved. */
    data object CustomerMissing : CheckoutResult

    /** The loyalty points on the bill can no longer be used (rules changed, or the balance is lower); nothing was saved. */
    data object LoyaltyUnavailable : CheckoutResult

    /** Billing settings are incomplete (for example GSTIN missing for a GST-registered store). */
    data object SetupRequired : CheckoutResult
}

/**
 * Bills a cart: re-reads stock, recalculates totals, checks payments (including
 * any amount on the customer's account) and saves the invoice, payments, stock and
 * account entry together.
 */
class CheckoutUseCase(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val customers: CustomerRepository? = null,
) {
    suspend operator fun invoke(cart: Cart, drafts: List<PaymentDraft>, requestId: String? = null): CheckoutResult {
        val store = settings.getSettings()
        if (store.needsGstinSetup || (store.gstRegistered && store.stateCode.isEmpty())) return CheckoutResult.SetupRequired

        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        val cartCheck = CartValidator.check(cart, totals, store.gstRegistered)
        if (!cartCheck.canCheckout) return CheckoutResult.CartInvalid(cartCheck)

        val account = cart.customer.customerId?.let { id ->
            val repository = customers ?: return CheckoutResult.CustomerMissing
            val customer = repository.getCustomer(id) ?: return CheckoutResult.CustomerMissing
            CreditAccount(repository.observeAccount(id).first()?.balance ?: Money.ZERO, customer.creditLimit)
        }
        val payments = when (val check = PaymentValidator.check(drafts, totals.grandTotal, account)) {
            is PaymentCheck.Invalid -> return CheckoutResult.PaymentsInvalid(check)
            is PaymentCheck.Valid -> check
        }

        val invoice = SaleBuilder.build(cart, totals, store, payments.payments, clock.millis())
        return when (val result = billing.recordSale(invoice, store.invoicePrefix, requestId)) {
            is SaleResult.Recorded -> CheckoutResult.Completed(result.invoiceId, result.number, payments.change)
            is SaleResult.StockShortage -> CheckoutResult.StockChanged(result.variantIds)
            SaleResult.CreditLimitExceeded -> CheckoutResult.CreditLimitExceeded
            SaleResult.CustomerMissing -> CheckoutResult.CustomerMissing
            SaleResult.LoyaltyUnavailable -> CheckoutResult.LoyaltyUnavailable
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
        requestId: String? = null,
    ): ProcessReturnResult {
        val original = billing.getInvoice(invoiceId) ?: return ProcessReturnResult.InvoiceNotFound
        val store = settings.getSettings()
        val build = ReturnBuilder.build(original, quantities, refundMethod, reason, clock.millis())
        val creditNote = when (build) {
            is ReturnBuild.Rejected -> return ProcessReturnResult.Rejected(build.error)
            is ReturnBuild.Ready -> build.creditNote
        }
        return when (val result = billing.recordReturn(creditNote, store.creditNotePrefix, requestId)) {
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
    // "Today" moves on at midnight, so a till left open overnight does not keep showing yesterday's sales.
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<StoreOverview> = clock.businessDays().flatMapLatest { today ->
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        combine(settings.observeSettings(), billing.observeSalesTotals(start, end)) { store, totals ->
            StoreOverview(storeName = store.storeName, salesToday = totals.net, billsToday = totals.billCount)
        }
    }
}
