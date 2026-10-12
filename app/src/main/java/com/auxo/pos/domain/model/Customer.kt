package com.auxo.pos.domain.model

/** A customer with an account. Sales can be put on the account and paid off later. */
data class Customer(
    val id: Long = 0,
    val name: String,
    /** Ten-digit mobile number; unique among customers. */
    val phone: String? = null,
    val email: String? = null,
    val gstin: String? = null,
    val stateCode: String? = null,
    val address: String? = null,
    /** Most the customer may owe; null means no limit. */
    val creditLimit: Money? = null,
    val notes: String? = null,
    val createdAtMillis: Long = 0,
)

enum class LedgerEntryType {
    /** Amount owed (or held in advance) when the account was opened. */
    OPENING_BALANCE,

    /** Part of a sale put on the account. */
    SALE,

    /** Refund for returned goods credited to the account. */
    RETURN,

    /** Money received against the account. */
    PAYMENT,

    /** Loyalty points earned on a sale. Carries points only; the money amount is zero. */
    POINTS_EARNED,

    /** Loyalty points used as a discount on a sale. */
    POINTS_REDEEMED,

    /** Points taken back because goods from the sale were returned. */
    POINTS_REVERSED,
    ;

    /** True for entries that move loyalty points and never change what the customer owes. */
    val isPoints: Boolean get() = this == POINTS_EARNED || this == POINTS_REDEEMED || this == POINTS_REVERSED
}

/**
 * One line of a customer's account. [amount] is positive when the customer owes
 * more (a sale on account) and negative when they owe less (a payment or return).
 */
data class LedgerEntry(
    val id: Long = 0,
    val customerId: Long,
    val type: LedgerEntryType,
    val amount: Money,
    val invoiceId: Long? = null,
    val invoiceNumber: String? = null,
    /** How a payment was made. */
    val method: PaymentMethod? = null,
    /** UPI reference or last four card digits for payments. */
    val reference: String? = null,
    val note: String? = null,
    val createdAtMillis: Long,
    /** Loyalty points added (positive) or taken off (negative). Zero for ordinary money entries. */
    val points: Int = 0,
)

/** A customer with what they owe now. */
data class CustomerAccount(
    val customer: Customer,
    /** Positive when the customer owes the store. */
    val balance: Money,
    val lastActivityMillis: Long? = null,
    /** Loyalty points the customer holds now. */
    val points: Int = 0,
    /**
     * When the oldest part of the money still owed was charged (see Ledger.owingSince), or null when nothing
     * is owed. Payments are counted against the oldest charges first.
     */
    val owingSinceMillis: Long? = null,
)
