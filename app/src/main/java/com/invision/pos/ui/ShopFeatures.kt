package com.invision.pos.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the shop uses suppliers (Suppliers, Purchase orders, Receive stock, supplier fields and
 * purchase figures). Screens leave all of it out when this is false. On unless the shop turns it off
 * in Shop settings, and on in tests that do not set it.
 */
val LocalSuppliersEnabled = staticCompositionLocalOf { true }

/** The shop's logo picture, or null when none was added. Home and the receipts use it. */
val LocalStoreLogo = staticCompositionLocalOf<android.graphics.Bitmap?> { null }

/** Days before expiry that stock is flagged "expiring soon" (the Super Admin's setting in Billing settings). */
val LocalExpiryWarningDays = staticCompositionLocalOf { com.invision.pos.domain.model.ExpiryWarning.DEFAULT_DAYS }
