package com.invision.pos.ui.screens.purchasing

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.invision.pos.R
import com.invision.pos.domain.model.PurchaseOrderStatus
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.domain.purchasing.PurchaseFormError
import com.invision.pos.domain.purchasing.PurchaseLineError
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.ToneColors
import com.invision.pos.viewmodel.OrderFilter

/** "12 pcs", "750 g" or "1.25 kg". */
fun StockUnit.withSymbol(base: Int): String = if (this == StockUnit.UNIT) "$base $entrySymbol" else format(base)

@StringRes
fun PurchaseOrderStatus.labelRes(): Int = when (this) {
    PurchaseOrderStatus.DRAFT -> R.string.po_status_draft
    PurchaseOrderStatus.ORDERED -> R.string.po_status_ordered
    PurchaseOrderStatus.PARTLY_RECEIVED -> R.string.po_status_partly
    PurchaseOrderStatus.RECEIVED -> R.string.po_status_received
    PurchaseOrderStatus.CANCELLED -> R.string.po_status_cancelled
}

@Composable
@ReadOnlyComposable
fun PurchaseOrderStatus.tone(): ToneColors {
    val status = InvisionThemeExtras.statusColors
    return when (this) {
        PurchaseOrderStatus.DRAFT -> status.neutral
        PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTLY_RECEIVED -> status.warning
        PurchaseOrderStatus.RECEIVED -> status.success
        PurchaseOrderStatus.CANCELLED -> status.danger
    }
}

@StringRes
fun OrderFilter.labelRes(): Int = when (this) {
    OrderFilter.OPEN -> R.string.order_filter_open
    OrderFilter.CLOSED -> R.string.order_filter_closed
    OrderFilter.ALL -> R.string.filter_all
}

@StringRes
fun PurchaseFormError.messageRes(): Int = when (this) {
    PurchaseFormError.SUPPLIER_REQUIRED -> R.string.purchase_error_supplier
    PurchaseFormError.NO_LINES -> R.string.purchase_error_no_lines
    PurchaseFormError.NOTHING_TO_RECEIVE -> R.string.purchase_error_nothing
    PurchaseFormError.SUPPLIER_INVOICE_INVALID -> R.string.purchase_error_bill_number
    PurchaseFormError.NOTE_TOO_LONG -> R.string.error_too_long
    PurchaseFormError.EXPECTED_DATE_IN_PAST -> R.string.purchase_error_date_past
}

@StringRes
fun PurchaseLineError.messageRes(): Int = when (this) {
    PurchaseLineError.QUANTITY_REQUIRED -> R.string.purchase_line_quantity_required
    PurchaseLineError.QUANTITY_INVALID -> R.string.adjust_error_invalid
    PurchaseLineError.MORE_THAN_ORDERED -> R.string.purchase_line_more_than_due
    PurchaseLineError.COST_REQUIRED -> R.string.purchase_line_cost_required
    PurchaseLineError.COST_INVALID -> R.string.error_invalid_amount
    PurchaseLineError.BATCH_TOO_LONG -> R.string.purchase_line_batch_too_long
    PurchaseLineError.ALREADY_EXPIRED -> R.string.purchase_line_expired
    PurchaseLineError.DUPLICATE_ITEM -> R.string.purchase_line_duplicate
    PurchaseLineError.ITEM_DELETED -> R.string.purchase_line_deleted
}
