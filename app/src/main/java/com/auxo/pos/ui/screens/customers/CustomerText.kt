// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.customers

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.auxo.pos.R
import com.auxo.pos.domain.contacts.ContactError
import com.auxo.pos.domain.customers.AccountPaymentError
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.ToneColors

/** Messages for supplier and customer form errors. */
@StringRes
fun ContactError.messageRes(): Int = when (this) {
    ContactError.REQUIRED -> R.string.error_required
    ContactError.TOO_LONG -> R.string.error_too_long
    ContactError.INVALID_PHONE -> R.string.settings_error_phone
    ContactError.INVALID_MOBILE -> R.string.customer_issue_phone
    ContactError.INVALID_EMAIL -> R.string.contact_error_email
    ContactError.INVALID_GSTIN -> R.string.customer_issue_gstin
    ContactError.GSTIN_STATE_MISMATCH -> R.string.settings_error_gstin_state
    ContactError.INVALID_AMOUNT -> R.string.error_invalid_amount
    ContactError.PHONE_IN_USE -> R.string.customer_error_phone_in_use
}

@StringRes
fun LedgerEntryType.labelRes(): Int = when (this) {
    LedgerEntryType.OPENING_BALANCE -> R.string.ledger_opening_balance
    LedgerEntryType.SALE -> R.string.ledger_sale
    LedgerEntryType.RETURN -> R.string.ledger_return
    LedgerEntryType.PAYMENT -> R.string.ledger_payment
    LedgerEntryType.POINTS_EARNED -> R.string.ledger_points_earned
    LedgerEntryType.POINTS_REDEEMED -> R.string.ledger_points_redeemed
    LedgerEntryType.POINTS_REVERSED -> R.string.ledger_points_reversed
}

@StringRes
fun AccountPaymentError.messageRes(): Int = when (this) {
    AccountPaymentError.AMOUNT_REQUIRED -> R.string.payment_error_required
    AccountPaymentError.AMOUNT_INVALID -> R.string.payment_error_invalid
    AccountPaymentError.MORE_THAN_DUE -> R.string.account_payment_more_than_due
    AccountPaymentError.NOTHING_DUE -> R.string.account_payment_nothing_due
    AccountPaymentError.REFERENCE_INVALID -> R.string.payment_error_reference
    AccountPaymentError.NOTE_TOO_LONG -> R.string.error_too_long
}

/** "Owes ₹500.00", "Advance ₹200.00" or "Settled". */
@Composable
@ReadOnlyComposable
fun balanceText(balance: Money): String {
    val formatter = LocalMoneyFormatter.current
    return when {
        balance > Money.ZERO -> stringResource(R.string.customer_balance_due, formatter.format(balance))
        balance < Money.ZERO -> stringResource(R.string.customer_balance_advance, formatter.format(Money(-balance.minorUnits)))
        else -> stringResource(R.string.customer_balance_settled)
    }
}

/** Warning while money is owed, success for an advance, neutral when settled. */
@Composable
@ReadOnlyComposable
fun balanceTone(balance: Money): ToneColors {
    val status = AuxoThemeExtras.statusColors
    return when {
        balance > Money.ZERO -> status.warning
        balance < Money.ZERO -> status.success
        else -> status.neutral
    }
}
