// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.auxo.pos.R
import com.auxo.pos.core.backup.shared.DetailKind
import com.auxo.pos.core.backup.shared.RestoreDetail

@StringRes
internal fun restoreDetailRes(kind: DetailKind): Int = when (kind) {
        DetailKind.FIELD_MISSING -> R.string.restore_detail_field_missing
        DetailKind.FIELD_INVALID -> R.string.restore_detail_field_invalid
        DetailKind.FIELD_NOT_UUID -> R.string.restore_detail_field_not_uuid
        DetailKind.LINK_TARGET_MISSING -> R.string.restore_detail_link_target_missing
        DetailKind.UNKNOWN_METAL -> R.string.restore_detail_unknown_metal
        DetailKind.UNKNOWN_LOYALTY_TYPE -> R.string.restore_detail_unknown_loyalty_type
        DetailKind.UNKNOWN_DISCOUNT -> R.string.restore_detail_unknown_discount
        DetailKind.UNKNOWN_STOCK_UNIT -> R.string.restore_detail_unknown_stock_unit
        DetailKind.LOGO_PHOTO_MISSING -> R.string.restore_detail_logo_photo_missing
        DetailKind.PRODUCT_PHOTO_MISSING -> R.string.restore_detail_product_photo_missing
        DetailKind.PHOTO_NOT_UUID -> R.string.restore_detail_photo_not_uuid
        DetailKind.PHOTO_DUPLICATE -> R.string.restore_detail_photo_duplicate
        DetailKind.DUPLICATE_ID -> R.string.restore_detail_duplicate_id
        DetailKind.DUPLICATE_BARCODE -> R.string.restore_detail_duplicate_barcode
        DetailKind.DUPLICATE_SKU -> R.string.restore_detail_duplicate_sku
        DetailKind.REFERENCE_LOOP -> R.string.restore_detail_reference_loop
        DetailKind.ORPHAN_HELD_BILL_LINE -> R.string.restore_detail_orphan_held_bill_line
        DetailKind.ORPHAN_QUOTATION_LINE -> R.string.restore_detail_orphan_quotation_line
        DetailKind.ORPHAN_COUNT_LINE -> R.string.restore_detail_orphan_count_line
        DetailKind.SETTINGS_COUNT -> R.string.restore_detail_settings_count
        DetailKind.LOYALTY_SETTINGS_COUNT -> R.string.restore_detail_loyalty_settings_count
        DetailKind.NOT_A_BACKUP -> R.string.restore_detail_not_a_backup
        DetailKind.UNSUPPORTED_VERSION -> R.string.restore_detail_unsupported_version
        DetailKind.MISSING_TABLE -> R.string.restore_detail_missing_table
        DetailKind.FILE_DAMAGED -> R.string.restore_detail_file_damaged
}

/** The reason a shared backup was refused, in the person's language. */
@Composable
internal fun restoreDetailText(detail: RestoreDetail): String =
    stringResource(restoreDetailRes(detail.kind), *detail.args.toTypedArray())
