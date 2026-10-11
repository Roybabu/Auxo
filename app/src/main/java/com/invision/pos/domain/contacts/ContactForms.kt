package com.invision.pos.domain.contacts

import com.invision.pos.domain.billing.Gstin
import com.invision.pos.domain.billing.IndianPhone
import com.invision.pos.domain.billing.IndianStates
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.Supplier

enum class ContactField { NAME, CONTACT_PERSON, PHONE, EMAIL, GSTIN, STATE, ADDRESS, NOTES, CREDIT_LIMIT, OPENING_BALANCE }

enum class ContactError { REQUIRED, TOO_LONG, INVALID_PHONE, INVALID_MOBILE, INVALID_EMAIL, INVALID_GSTIN, GSTIN_STATE_MISMATCH, INVALID_AMOUNT }

/** Rules shared by supplier and customer forms. */
internal object ContactRules {
    const val NAME_MAX = 80
    const val ADDRESS_MAX = 200
    const val NOTES_MAX = 300
    const val EMAIL_MAX = 100
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    fun name(text: String, errors: MutableMap<ContactField, ContactError>, field: ContactField = ContactField.NAME, required: Boolean = true) {
        val value = text.trim()
        when {
            value.isEmpty() && required -> errors[field] = ContactError.REQUIRED
            value.length > NAME_MAX -> errors[field] = ContactError.TOO_LONG
        }
    }

    fun email(text: String, errors: MutableMap<ContactField, ContactError>) {
        val value = text.trim()
        when {
            value.isEmpty() -> Unit
            value.length > EMAIL_MAX -> errors[ContactField.EMAIL] = ContactError.TOO_LONG
            !EMAIL.matches(value) -> errors[ContactField.EMAIL] = ContactError.INVALID_EMAIL
        }
    }

    fun gstin(text: String, stateCode: String, errors: MutableMap<ContactField, ContactError>) {
        if (text.isBlank()) return
        when {
            !Gstin.isValid(text) -> errors[ContactField.GSTIN] = ContactError.INVALID_GSTIN
            stateCode.isNotEmpty() && Gstin.stateCode(text) != stateCode -> errors[ContactField.GSTIN] = ContactError.GSTIN_STATE_MISMATCH
        }
    }

    fun limit(text: String, max: Int, field: ContactField, errors: MutableMap<ContactField, ContactError>) {
        if (text.trim().length > max) errors[field] = ContactError.TOO_LONG
    }

    /** The state to save: the one picked, or the one in the GSTIN. */
    fun state(stateCode: String, gstin: String): String? = when {
        stateCode.isNotEmpty() -> stateCode
        Gstin.isValid(gstin) -> Gstin.stateCode(gstin).takeIf { IndianStates.byCode(it) != null }
        else -> null
    }
}

/** Supplier details as typed. */
data class SupplierForm(
    val name: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val stateCode: String = "",
    val address: String = "",
    val notes: String = "",
) {
    companion object {
        fun from(supplier: Supplier) = SupplierForm(
            name = supplier.name,
            contactPerson = supplier.contactPerson.orEmpty(),
            phone = supplier.phone.orEmpty(),
            email = supplier.email.orEmpty(),
            gstin = supplier.gstin.orEmpty(),
            stateCode = supplier.stateCode.orEmpty(),
            address = supplier.address.orEmpty(),
            notes = supplier.notes.orEmpty(),
        )
    }
}

sealed interface SupplierValidation {
    data class Valid(val supplier: Supplier) : SupplierValidation
    data class Invalid(val errors: Map<ContactField, ContactError>) : SupplierValidation
}

object SupplierValidator {
    fun validate(form: SupplierForm, existing: Supplier? = null): SupplierValidation {
        val errors = mutableMapOf<ContactField, ContactError>()
        ContactRules.name(form.name, errors)
        ContactRules.name(form.contactPerson, errors, ContactField.CONTACT_PERSON, required = false)
        if (form.phone.isNotBlank() && !IndianPhone.isValidContactNumber(form.phone)) errors[ContactField.PHONE] = ContactError.INVALID_PHONE
        ContactRules.email(form.email, errors)
        ContactRules.gstin(form.gstin, form.stateCode, errors)
        ContactRules.limit(form.address, ContactRules.ADDRESS_MAX, ContactField.ADDRESS, errors)
        ContactRules.limit(form.notes, ContactRules.NOTES_MAX, ContactField.NOTES, errors)
        if (errors.isNotEmpty()) return SupplierValidation.Invalid(errors)

        val gstin = Gstin.normalize(form.gstin)
        return SupplierValidation.Valid(
            Supplier(
                id = existing?.id ?: 0,
                name = form.name.trim(),
                contactPerson = form.contactPerson.trim().ifEmpty { null },
                phone = form.phone.trim().ifEmpty { null },
                email = form.email.trim().ifEmpty { null },
                gstin = gstin.ifEmpty { null },
                stateCode = ContactRules.state(form.stateCode, gstin),
                address = form.address.trim().ifEmpty { null },
                notes = form.notes.trim().ifEmpty { null },
                active = existing?.active ?: true,
                createdAtMillis = existing?.createdAtMillis ?: 0,
            ),
        )
    }
}

/** Customer details as typed. */
data class CustomerForm(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val stateCode: String = "",
    val address: String = "",
    val creditLimit: String = "",
    val notes: String = "",
    /** New customers only: what they already owe. */
    val openingBalance: String = "",
) {
    companion object {
        fun from(customer: Customer) = CustomerForm(
            name = customer.name,
            phone = customer.phone.orEmpty(),
            email = customer.email.orEmpty(),
            gstin = customer.gstin.orEmpty(),
            stateCode = customer.stateCode.orEmpty(),
            address = customer.address.orEmpty(),
            creditLimit = customer.creditLimit?.toDecimalString().orEmpty(),
            notes = customer.notes.orEmpty(),
        )
    }
}

sealed interface CustomerValidation {
    data class Valid(val customer: Customer, val openingBalance: Money) : CustomerValidation
    data class Invalid(val errors: Map<ContactField, ContactError>) : CustomerValidation
}

object CustomerValidator {
    fun validate(form: CustomerForm, existing: Customer? = null): CustomerValidation {
        val errors = mutableMapOf<ContactField, ContactError>()
        ContactRules.name(form.name, errors)
        val mobile = if (form.phone.isBlank()) null else IndianPhone.normalizeMobile(form.phone)
        if (form.phone.isNotBlank() && mobile == null) errors[ContactField.PHONE] = ContactError.INVALID_MOBILE
        ContactRules.email(form.email, errors)
        ContactRules.gstin(form.gstin, form.stateCode, errors)
        ContactRules.limit(form.address, ContactRules.ADDRESS_MAX, ContactField.ADDRESS, errors)
        ContactRules.limit(form.notes, ContactRules.NOTES_MAX, ContactField.NOTES, errors)
        val creditLimit = if (form.creditLimit.isBlank()) null else Money.parseOrNull(form.creditLimit)
        if (form.creditLimit.isNotBlank() && creditLimit == null) errors[ContactField.CREDIT_LIMIT] = ContactError.INVALID_AMOUNT
        val opening = if (existing != null || form.openingBalance.isBlank()) Money.ZERO else Money.parseOrNull(form.openingBalance)
        if (opening == null) errors[ContactField.OPENING_BALANCE] = ContactError.INVALID_AMOUNT
        if (errors.isNotEmpty() || opening == null) return CustomerValidation.Invalid(errors)

        val gstin = Gstin.normalize(form.gstin)
        return CustomerValidation.Valid(
            customer = Customer(
                id = existing?.id ?: 0,
                name = form.name.trim(),
                phone = mobile,
                email = form.email.trim().ifEmpty { null },
                gstin = gstin.ifEmpty { null },
                stateCode = ContactRules.state(form.stateCode, gstin),
                address = form.address.trim().ifEmpty { null },
                creditLimit = creditLimit,
                notes = form.notes.trim().ifEmpty { null },
                createdAtMillis = existing?.createdAtMillis ?: 0,
            ),
            openingBalance = opening,
        )
    }
}
