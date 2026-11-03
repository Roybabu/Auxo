// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

/** Puts the mapped tables together into the shared auxo-backup document (docs/BACKUP_FORMAT.md section 2). */
object SharedFile {
    val TABLES: List<String> = listOf(
        "settings", "categories", "subcategories", "products", "variants", "stockBatches", "stockMovements",
        "customers", "customerLedger", "invoices", "invoiceLines", "payments", "suppliers", "purchaseOrders",
        "purchaseOrderLines", "receipts", "receiptLines", "dayClosings", "loyaltySettings", "loyaltyLedger",
        "heldBills", "heldBillLines", "quotations", "quotationLines", "stockCounts", "stockCountLines",
        "auditLog", "numberSequences", "extensions",
    )

    /** Every table must be given, even if empty. A missing table would silently drop data, so it is refused. */
    fun assemble(
        tables: Map<String, List<Map<String, Any?>>>,
        shopName: String,
        createdAt: Long,
        appVersion: String,
        photos: List<Map<String, Any?>> = emptyList(),
    ): Map<String, Any?> {
        val missing = TABLES.filterNot { tables.containsKey(it) }
        require(missing.isEmpty()) { "Backup is missing tables: ${missing.joinToString()}" }
        return mapOf(
            "format" to "auxo-backup",
            "version" to 1,
            "sharedModelVersion" to 1,
            "createdAt" to createdAt,
            "source" to mapOf("platform" to "android", "appVersion" to appVersion),
            "shopName" to shopName,
            "tables" to TABLES.associateWith { tables.getValue(it) },
            "photos" to photos,
        )
    }

    fun toJson(file: Map<String, Any?>): String = SharedJson.encode(file)
}
