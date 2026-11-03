// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Restores a small shared file into a real database, twice. The second restore must replace the first, not add to it,
 * and every restored record must be linked to the shared ID it had in the file.
 */
@RunWith(AndroidJUnit4::class)
class SharedRestoreDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "shared-restore-device.db"

    @Before
    fun removeAnyOldFile() {
        context.deleteDatabase(name)
    }

    @After
    fun removeTheFile() {
        context.deleteDatabase(name)
    }

    private fun plan(): RestorePlan {
        val settings = mapOf<String, Any?>(
            "id" to "shop", "storeName" to "Shop", "address" to "Road", "gstin" to "27AAPFU0939F1ZV", "stateCode" to "27",
            "gstRegistered" to true, "pricesIncludeTax" to true, "roundOff" to true, "invoicePrefix" to "INV",
            "creditNotePrefix" to "CN", "phone" to "", "fssai" to "", "upiId" to "", "upiPayeeName" to "",
            "receiptPaper" to "THERMAL_80", "receiptFooter" to "Thanks", "receiptLanguage" to "en",
            "receiptShowHsn" to true, "receiptShowTaxBreakdown" to true, "receiptShowCustomer" to true,
            "receiptShowSavings" to true, "receiptShowBarcode" to true, "receiptShowUpiQr" to false,
            "employeeDiscountLimitBp" to 0L, "expiryWarningDays" to 30L,
        )
        val general = mapOf<String, Any?>(
            "id" to "cat-general", "name" to "General", "profile" to "GENERAL", "enabled" to true, "sortOrder" to 1L, "builtIn" to true,
        )
        val mug = mapOf<String, Any?>(
            "id" to "p1", "name" to "Mug", "category" to "GENERAL", "brand" to null, "description" to null, "material" to null,
            "dietType" to null, "hsnCode" to null, "gstRateBasisPoints" to null, "apparelGst" to false,
            "createdAt" to 1L, "updatedAt" to 2L, "stockUnit" to "UNIT", "available" to true, "tracksStock" to true,
            "categoryId" to "cat-general", "subcategory" to null, "preferredSupplierId" to null, "photoId" to null,
            "warrantyMonths" to null, "prescriptionRequired" to false, "metal" to null, "makingChargeBasisPoints" to null,
        )
        val variant = mapOf<String, Any?>(
            "id" to "v1", "productId" to "p1", "sku" to "", "barcode" to "890", "size" to "", "colour" to "",
            "netQuantityMilli" to null, "netQuantityUnit" to null, "pricePaise" to "1000", "mrpPaise" to null, "costPaise" to null,
            "stockQuantity" to 3L, "lowStockThreshold" to 1L, "reorderQuantity" to 2L, "photoId" to null,
        )
        val document = SharedBackupReader.check(
            mapOf(
                "format" to "auxo-backup", "version" to 1L, "sharedModelVersion" to 1L, "createdAt" to 5L,
                "source" to mapOf("platform" to "web", "appVersion" to "1"), "shopName" to "Shop",
                "tables" to SharedFile.TABLES.associateWith { table ->
                    when (table) {
                        "settings" -> listOf(settings)
                        "categories" -> listOf(general)
                        "products" -> listOf(mug)
                        "variants" -> listOf(variant)
                        else -> emptyList<Map<String, Any?>>()
                    }
                },
                "photos" to emptyList<Any?>(),
            ),
        )
        return SharedRestorePlanner.plan(document)
    }

    @Test
    fun aRestore_replacesTheRecords_andLinksEachOne_toItsSharedId() = runBlocking {
        val db = AuxoDatabase.builder(context, name).build()
        try {
            val applier = SharedRestoreApplier(db, photos = null, workDir = context.cacheDir)
            applier.apply(plan())
            applier.apply(plan())

            val productId = db.openHelper.readableDatabase.query("SELECT id FROM products").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                cursor.getLong(0)
            }
            assertEquals("p1", db.sharedIdLinkDao().sharedIdOf("product", productId))
            val variantProduct = db.openHelper.readableDatabase.query("SELECT product_id FROM product_variants").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                cursor.getLong(0)
            }
            assertEquals(productId, variantProduct)
        } finally {
            db.close()
        }
    }
}
