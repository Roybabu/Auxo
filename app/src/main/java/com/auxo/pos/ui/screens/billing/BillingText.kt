// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.billing

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.auxo.pos.R
import com.auxo.pos.domain.billing.CustomerIssue
import com.auxo.pos.domain.billing.LineIssue
import com.auxo.pos.domain.billing.PaymentError
import com.auxo.pos.domain.billing.ReturnError
import com.auxo.pos.domain.billing.SettingsError
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.viewmodel.HistoryRange

@StringRes
fun PaymentMethod.labelRes(): Int = when (this) {
    PaymentMethod.CASH -> R.string.payment_method_cash
    PaymentMethod.UPI -> R.string.payment_method_upi
    PaymentMethod.CARD -> R.string.payment_method_card
    PaymentMethod.CREDIT -> R.string.payment_method_credit
}

@StringRes
fun ReceiptPaper.shortLabelRes(): Int = when (this) {
    ReceiptPaper.THERMAL_58 -> R.string.paper_58
    ReceiptPaper.THERMAL_76 -> R.string.paper_76
    ReceiptPaper.THERMAL_80 -> R.string.paper_80
    ReceiptPaper.A5 -> R.string.paper_a5
    ReceiptPaper.A4 -> R.string.paper_a4
}

@StringRes
fun ReceiptPaper.longLabelRes(): Int = when (this) {
    ReceiptPaper.THERMAL_58 -> R.string.settings_paper_58
    ReceiptPaper.THERMAL_76 -> R.string.settings_paper_76
    ReceiptPaper.THERMAL_80 -> R.string.settings_paper_80
    ReceiptPaper.A5 -> R.string.settings_paper_a5
    ReceiptPaper.A4 -> R.string.settings_paper_a4
}

@StringRes
fun HistoryRange.labelRes(): Int = when (this) {
    HistoryRange.TODAY -> R.string.history_range_today
    HistoryRange.LAST_7_DAYS -> R.string.history_range_7_days
    HistoryRange.THIS_MONTH -> R.string.history_range_month
    HistoryRange.ALL -> R.string.history_range_all
}

@StringRes
fun PaymentError.messageRes(): Int = when (this) {
    PaymentError.AMOUNT_REQUIRED -> R.string.payment_error_required
    PaymentError.AMOUNT_INVALID -> R.string.payment_error_invalid
    PaymentError.TENDERED_INVALID -> R.string.payment_error_tendered_invalid
    PaymentError.TENDERED_TOO_LOW -> R.string.payment_error_tendered_low
    PaymentError.REFERENCE_INVALID -> R.string.payment_error_reference
    PaymentError.CREDIT_NEEDS_CUSTOMER -> R.string.payment_error_credit_customer
    PaymentError.CREDIT_OVER_LIMIT -> R.string.payment_error_credit_limit
}

@StringRes
fun CustomerIssue.messageRes(): Int = when (this) {
    CustomerIssue.NAME_REQUIRED_WITH_GSTIN -> R.string.customer_issue_name_required
    CustomerIssue.NAME_TOO_LONG -> R.string.customer_issue_name_too_long
    CustomerIssue.INVALID_PHONE -> R.string.customer_issue_phone
    CustomerIssue.INVALID_GSTIN -> R.string.customer_issue_gstin
}

@StringRes
fun ReturnError.messageRes(): Int = when (this) {
    ReturnError.NOTHING_SELECTED -> R.string.return_none_selected
    ReturnError.QUANTITY_TOO_LARGE -> R.string.return_error_too_many
    ReturnError.NOT_A_SALE -> R.string.return_error_not_sale
    ReturnError.NO_ACCOUNT -> R.string.return_error_no_account
}

@StringRes
fun SettingsError.messageRes(): Int = when (this) {
    SettingsError.REQUIRED -> R.string.settings_error_required
    SettingsError.TOO_LONG -> R.string.settings_error_too_long
    SettingsError.INVALID_PHONE -> R.string.settings_error_phone
    SettingsError.INVALID_GSTIN -> R.string.settings_error_gstin
    SettingsError.INVALID_FSSAI -> R.string.settings_error_fssai
    SettingsError.GSTIN_STATE_MISMATCH -> R.string.settings_error_gstin_state
    SettingsError.INVALID_PREFIX -> R.string.settings_error_prefix
    SettingsError.SAME_PREFIX -> R.string.settings_error_same_prefix
    SettingsError.INVALID_UPI_ID -> R.string.settings_error_upi
}

@Composable
@ReadOnlyComposable
fun lineIssueText(issue: LineIssue): String = when (issue) {
    LineIssue.Unavailable -> stringResource(R.string.issue_unavailable)
    is LineIssue.NotEnoughStock -> stringResource(R.string.issue_not_enough_stock, issue.available)
    LineIssue.MissingGstRate -> stringResource(R.string.issue_missing_gst)
    LineIssue.DiscountTooLarge -> stringResource(R.string.issue_discount_too_large)
    LineIssue.DiscountReasonMissing -> stringResource(R.string.issue_discount_reason_missing)
    LineIssue.DiscountAboveLimit -> stringResource(R.string.issue_discount_above_limit)
}
