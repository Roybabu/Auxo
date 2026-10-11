package com.invision.pos.testutil

import com.invision.pos.viewmodel.BillingViewModel

/**
 * Types the whole bill amount into the only payment on the payment page. New payments start at
 * 0.00, so tests that just want to pay in full call this after `proceedToPayment()`.
 */
fun BillingViewModel.payInFull() {
    val state = uiState.value
    onPaymentAmountChange(state.payments.single().key, state.totals.grandTotal.toDecimalString())
}
