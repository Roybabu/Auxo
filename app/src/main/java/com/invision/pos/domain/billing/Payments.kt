package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord

/** One payment as typed on the payment screen. */
data class PaymentDraft(
    val key: Int,
    val method: PaymentMethod,
    val amount: String = "",
    val reference: String = "",
    /** Cash only: what the customer handed over. Blank means exact change. */
    val tendered: String = "",
)

enum class PaymentError {
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    TENDERED_INVALID,
    TENDERED_TOO_LOW,
    REFERENCE_INVALID,
}

sealed interface PaymentCheck {
    data class Valid(val payments: List<PaymentRecord>, val change: Money) : PaymentCheck

    data class Invalid(
        val errors: Map<Int, PaymentError> = emptyMap(),
        /** Total due minus valid amounts entered; negative when overpaid. */
        val remaining: Money,
        val noPayments: Boolean = false,
    ) : PaymentCheck
}

object PaymentValidator {

    private val UPI_REFERENCE = Regex("""[A-Za-z0-9]{4,35}""")
    private val CARD_LAST_FOUR = Regex("""\d{4}""")

    fun check(drafts: List<PaymentDraft>, total: Money): PaymentCheck {
        val errors = mutableMapOf<Int, PaymentError>()
        val records = mutableListOf<PaymentRecord>()
        var paid = Money.ZERO
        var change = Money.ZERO

        for (draft in drafts) {
            val amount = when {
                draft.amount.isBlank() -> {
                    errors[draft.key] = PaymentError.AMOUNT_REQUIRED
                    null
                }
                else -> Money.parseOrNull(draft.amount)?.takeIf { !it.isZero }.also {
                    if (it == null) errors[draft.key] = PaymentError.AMOUNT_INVALID
                }
            } ?: continue
            paid += amount

            var tendered: Money? = null
            if (draft.method == PaymentMethod.CASH && draft.tendered.isNotBlank()) {
                tendered = Money.parseOrNull(draft.tendered)
                when {
                    tendered == null -> errors[draft.key] = PaymentError.TENDERED_INVALID
                    tendered < amount -> errors[draft.key] = PaymentError.TENDERED_TOO_LOW
                    else -> change += tendered - amount
                }
            }

            val reference = draft.reference.trim()
            val referenceOk = when {
                reference.isEmpty() -> true
                draft.method == PaymentMethod.UPI -> UPI_REFERENCE.matches(reference)
                draft.method == PaymentMethod.CARD -> CARD_LAST_FOUR.matches(reference)
                else -> true
            }
            if (!referenceOk) errors[draft.key] = PaymentError.REFERENCE_INVALID

            records += PaymentRecord(
                method = draft.method,
                kind = PaymentKind.PAYMENT,
                amount = amount,
                reference = reference.ifEmpty { null }.takeIf { draft.method != PaymentMethod.CASH },
                tendered = tendered?.takeIf { draft.method == PaymentMethod.CASH && it > amount },
                change = tendered?.let { (it - amount).takeIf { c -> c > Money.ZERO } },
            )
        }

        val remaining = total - paid
        return if (drafts.isEmpty() || errors.isNotEmpty() || !remaining.isZero) {
            PaymentCheck.Invalid(errors = errors, remaining = remaining, noPayments = drafts.isEmpty())
        } else {
            PaymentCheck.Valid(records, change)
        }
    }
}
