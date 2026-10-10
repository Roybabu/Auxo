package com.invision.pos.domain.billing

import com.invision.pos.domain.model.StoreSettings

enum class SettingsField {
    STORE_NAME, ADDRESS, PHONE, GSTIN, STATE, INVOICE_PREFIX, CREDIT_NOTE_PREFIX, UPI_ID, UPI_PAYEE, FOOTER,
}

enum class SettingsError {
    REQUIRED,
    TOO_LONG,
    INVALID_PHONE,
    INVALID_GSTIN,
    GSTIN_STATE_MISMATCH,
    INVALID_PREFIX,
    SAME_PREFIX,
    INVALID_UPI_ID,
}

object SettingsValidator {
    const val STORE_NAME_MAX = 60
    const val ADDRESS_MAX = 200
    const val FOOTER_MAX = 120
    const val UPI_PAYEE_MAX = 50

    fun check(settings: StoreSettings): Map<SettingsField, SettingsError> = buildMap {
        val name = settings.storeName.trim()
        when {
            name.isEmpty() -> put(SettingsField.STORE_NAME, SettingsError.REQUIRED)
            name.length > STORE_NAME_MAX -> put(SettingsField.STORE_NAME, SettingsError.TOO_LONG)
        }
        if (settings.address.trim().length > ADDRESS_MAX) put(SettingsField.ADDRESS, SettingsError.TOO_LONG)
        if (settings.phone.isNotBlank() && !IndianPhone.isValidContactNumber(settings.phone)) {
            put(SettingsField.PHONE, SettingsError.INVALID_PHONE)
        }
        if (settings.gstin.isNotBlank()) {
            when {
                !Gstin.isValid(settings.gstin) -> put(SettingsField.GSTIN, SettingsError.INVALID_GSTIN)
                settings.stateCode.isNotEmpty() && Gstin.stateCode(settings.gstin) != settings.stateCode ->
                    put(SettingsField.GSTIN, SettingsError.GSTIN_STATE_MISMATCH)
            }
        }
        if (settings.gstRegistered && settings.stateCode.isEmpty()) put(SettingsField.STATE, SettingsError.REQUIRED)
        if (!InvoiceNumbers.isValidPrefix(settings.invoicePrefix)) put(SettingsField.INVOICE_PREFIX, SettingsError.INVALID_PREFIX)
        when {
            !InvoiceNumbers.isValidPrefix(settings.creditNotePrefix) ->
                put(SettingsField.CREDIT_NOTE_PREFIX, SettingsError.INVALID_PREFIX)
            settings.creditNotePrefix == settings.invoicePrefix ->
                put(SettingsField.CREDIT_NOTE_PREFIX, SettingsError.SAME_PREFIX)
        }
        if (settings.upiId.isNotBlank() && !Upi.isValidVpa(settings.upiId)) put(SettingsField.UPI_ID, SettingsError.INVALID_UPI_ID)
        if (settings.upiPayeeName.trim().length > UPI_PAYEE_MAX) put(SettingsField.UPI_PAYEE, SettingsError.TOO_LONG)
        if (settings.receipt.footer.trim().length > FOOTER_MAX) put(SettingsField.FOOTER, SettingsError.TOO_LONG)
    }
}
