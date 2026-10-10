package com.invision.pos.ui.screens.inventory

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.invision.pos.R
import com.invision.pos.domain.inventory.StockAdjustmentError
import com.invision.pos.domain.inventory.StockAlertType
import com.invision.pos.domain.inventory.StockFilter
import com.invision.pos.domain.model.DietType
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockStatus
import com.invision.pos.domain.validation.FieldError
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.ToneColors

@StringRes
fun ProductCategory.labelRes(): Int = when (this) {
    ProductCategory.GARMENT -> R.string.category_garment
    ProductCategory.PACKAGED_FOOD -> R.string.category_packaged_food
    ProductCategory.BEVERAGE -> R.string.category_beverage
}

@DrawableRes
fun ProductCategory.iconRes(): Int = when (this) {
    ProductCategory.GARMENT -> R.drawable.ic_garment
    ProductCategory.PACKAGED_FOOD -> R.drawable.ic_food
    ProductCategory.BEVERAGE -> R.drawable.ic_beverage
}

@StringRes
fun StockStatus.labelRes(): Int = when (this) {
    StockStatus.IN_STOCK -> R.string.stock_status_in
    StockStatus.LOW_STOCK -> R.string.stock_status_low
    StockStatus.OUT_OF_STOCK -> R.string.stock_status_out
}

@Composable
@ReadOnlyComposable
fun StockStatus.tone(): ToneColors = when (this) {
    StockStatus.IN_STOCK -> InvisionThemeExtras.statusColors.success
    StockStatus.LOW_STOCK -> InvisionThemeExtras.statusColors.warning
    StockStatus.OUT_OF_STOCK -> InvisionThemeExtras.statusColors.danger
}

@Composable
@ReadOnlyComposable
fun StockAlertType.tone(): ToneColors = when (this) {
    StockAlertType.EXPIRED, StockAlertType.OUT_OF_STOCK -> InvisionThemeExtras.statusColors.danger
    StockAlertType.EXPIRING_SOON, StockAlertType.LOW_STOCK -> InvisionThemeExtras.statusColors.warning
}

@StringRes
fun StockFilter.labelRes(): Int = when (this) {
    StockFilter.ALL -> R.string.filter_all
    StockFilter.LOW_STOCK -> R.string.stock_filter_low
    StockFilter.OUT_OF_STOCK -> R.string.stock_filter_out
    StockFilter.EXPIRING_SOON -> R.string.stock_filter_expiring
}

@StringRes
fun StockChangeReason.labelRes(): Int = when (this) {
    StockChangeReason.INITIAL -> R.string.reason_initial
    StockChangeReason.RECEIVED -> R.string.reason_received
    StockChangeReason.RETURNED -> R.string.reason_returned
    StockChangeReason.SOLD -> R.string.reason_sold
    StockChangeReason.DAMAGED -> R.string.reason_damaged
    StockChangeReason.CORRECTION -> R.string.reason_correction
}

@StringRes
fun DietType?.labelRes(): Int = when (this) {
    DietType.VEG -> R.string.diet_veg
    DietType.NON_VEG -> R.string.diet_non_veg
    null -> R.string.diet_unspecified
}

@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.error_required
    FieldError.TOO_SHORT -> R.string.error_too_short
    FieldError.TOO_LONG -> R.string.error_too_long
    FieldError.INVALID_PRICE -> R.string.error_invalid_price
    FieldError.MRP_BELOW_PRICE -> R.string.error_mrp_below_price
    FieldError.INVALID_QUANTITY -> R.string.error_invalid_quantity
    FieldError.QUANTITY_TOO_LARGE -> R.string.error_quantity_too_large
    FieldError.INVALID_NET_QUANTITY -> R.string.error_invalid_net_quantity
    FieldError.UNIT_REQUIRED -> R.string.error_unit_required
    FieldError.INVALID_BARCODE_CHECK_DIGIT -> R.string.error_barcode_check_digit
    FieldError.INVALID_BARCODE_CHARACTERS -> R.string.error_barcode_characters
    FieldError.BARCODE_TOO_LONG -> R.string.error_barcode_too_long
    FieldError.DUPLICATE_BARCODE -> R.string.error_duplicate_barcode
    FieldError.BARCODE_IN_USE -> R.string.error_barcode_in_use
    FieldError.INVALID_SKU -> R.string.error_invalid_sku
    FieldError.DUPLICATE_SKU -> R.string.error_duplicate_sku
    FieldError.SKU_IN_USE -> R.string.error_sku_in_use
    FieldError.DUPLICATE_VARIANT -> R.string.error_duplicate_variant
    FieldError.REMOVED_VARIANT_HAS_STOCK -> R.string.error_removed_variant_has_stock
}

@StringRes
fun StockAdjustmentError.messageRes(): Int = when (this) {
    StockAdjustmentError.QUANTITY_REQUIRED -> R.string.adjust_error_required
    StockAdjustmentError.QUANTITY_INVALID -> R.string.adjust_error_invalid
    StockAdjustmentError.QUANTITY_TOO_LARGE -> R.string.adjust_error_too_large
    StockAdjustmentError.WOULD_GO_NEGATIVE -> R.string.adjust_error_negative
    StockAdjustmentError.NO_CHANGE -> R.string.adjust_error_no_change
}
