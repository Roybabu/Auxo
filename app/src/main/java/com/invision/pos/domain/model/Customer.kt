package com.invision.pos.domain.model

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
)

/** A customer with what they owe now. */
data class CustomerAccount(
    val customer: Customer,
    /** Positive when the customer owes the store. */
    val balance: Money,
    val lastActivityMillis: Long? = null,
)
