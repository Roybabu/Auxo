package com.auxo.pos.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the shop uses suppliers (Suppliers, Purchase orders, Receive stock, supplier fields and
 * purchase figures). Screens leave all of it out when this is false. On unless the shop turns it off
 * in Shop settings, and on in tests that do not set it.
 */
val LocalSuppliersEnabled = staticCompositionLocalOf { true }

/**
 * Whether the shop sells restaurant items (the Restaurant switch in Shop settings). The Restaurant category is only
 * offered when it is on; products already in it are kept either way. On in tests that do not set it.
 */
val LocalRestaurantEnabled = staticCompositionLocalOf { true }

/** The categories to offer in pickers and filters: Restaurant only while the shop has it switched on. */
@androidx.compose.runtime.Composable
fun visibleCategories(): List<com.auxo.pos.domain.model.ProductCategory> {
    val restaurant = LocalRestaurantEnabled.current
    return com.auxo.pos.domain.model.ProductCategory.entries.filter { restaurant || it != com.auxo.pos.domain.model.ProductCategory.RESTAURANT }
}

/** The shop's logo picture, or null when none was added. Home and the receipts use it. */
val LocalStoreLogo = staticCompositionLocalOf<android.graphics.Bitmap?> { null }

/** Days before expiry that stock is flagged "expiring soon" (the Super Admin's setting in Billing settings). */
val LocalExpiryWarningDays = staticCompositionLocalOf { com.auxo.pos.domain.model.ExpiryWarning.DEFAULT_DAYS }

/** Where product photos are loaded from. Null in previews and tests, where products show their category icon. */
val LocalProductPhotos = staticCompositionLocalOf<com.auxo.pos.core.photos.ProductPhotoStore?> { null }

/** Prints receipts on the shop's Bluetooth printer. Null in previews and tests, where the button is left out. */
val LocalReceiptPrinting = staticCompositionLocalOf<com.auxo.pos.core.printer.ReceiptPrinting?> { null }
