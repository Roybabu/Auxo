package com.auxo.pos.testutil

import com.auxo.pos.viewmodel.BillingViewModel

/**
 * Types the whole bill amount into the only payment on the payment page. The page already starts
 * with the whole bill, so this only matters after a test has changed the amount.
 */
fun BillingViewModel.payInFull() {
    val state = uiState.value
    onPaymentAmountChange(state.payments.single().key, state.totals.grandTotal.toDecimalString())
}
