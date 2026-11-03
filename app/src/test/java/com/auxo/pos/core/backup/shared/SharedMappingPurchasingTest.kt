// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.PurchaseOrderEntity
import com.auxo.pos.data.local.entity.PurchaseOrderLineEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptLineEntity
import com.auxo.pos.data.local.entity.ShopCategoryEntity
import com.auxo.pos.data.local.entity.ShopSubcategoryEntity
import com.auxo.pos.data.local.entity.SupplierEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedMappingPurchasingTest {
    @Test
    fun built_in_categories_use_the_same_fixed_id_as_the_web_app() {
        assertEquals("21698313-7bd8-56ea-9c69-9ebe0fc84a82", SharedIds.category("GARMENT", builtIn = true, id = 1))
        assertEquals("21698313-7bd8-56ea-9c69-9ebe0fc84a82", SharedIds.category("GARMENT", builtIn = true, id = 99))
    }

    @Test
    fun custom_categories_get_a_derived_id() {
        assertEquals(SharedIds.derive("category", "5"), SharedIds.category("GARMENT", builtIn = false, id = 5))
    }

    @Test
    fun category_and_subcategory_rows_link_by_shared_id() {
        val category = SharedMapping.category(ShopCategoryEntity(id = 1, name = null, profile = "GARMENT", enabled = true, sortOrder = 0, builtIn = true))
        assertEquals("21698313-7bd8-56ea-9c69-9ebe0fc84a82", category["id"])
        val sub = SharedMapping.subcategory(ShopSubcategoryEntity(id = 3, categoryId = 1, name = "Shirts", sortOrder = 1), { id ->
            SharedIds.category("GARMENT", builtIn = true, id = id)
        })
        assertEquals("21698313-7bd8-56ea-9c69-9ebe0fc84a82", sub["categoryId"])
    }

    @Test
    fun supplier_and_purchase_order_use_spec_names_and_dates() {
        val supplier = SharedMapping.supplier(
            SupplierEntity(id = 2, name = "Fabric Co", contactPerson = null, phone = null, email = null, gstin = null,
                stateCode = null, address = null, notes = null, active = true, createdAt = 1, updatedAt = 2),
        )
        assertEquals(true, supplier["active"])
        val order = SharedMapping.purchaseOrder(
            PurchaseOrderEntity(id = 3, number = "PO/26-27/00001", supplierId = 2, status = "ORDERED", createdAt = 1,
                updatedAt = 2, expectedEpochDay = 0L, note = null),
        )
        assertEquals("1970-01-01", order["expectedDate"])
        assertEquals(SharedIds.derive("supplier", "2"), order["supplierId"])
        val line = SharedMapping.purchaseOrderLine(
            PurchaseOrderLineEntity(id = 12, orderId = 3, variantId = 11, productName = "Shirt", variantLabel = "M",
                stockUnit = "UNIT", quantityOrdered = 10, quantityReceived = 0, unitCostMinor = 30000),
        )
        assertEquals(SharedIds.derive("purchaseOrder", "3"), line["orderId"])
        assertEquals("30000", line["unitCostPaise"])
    }

    @Test
    fun receipt_links_its_order_and_its_batch_and_gets_a_request_id() {
        val receipt = SharedMapping.receipt(
            PurchaseReceiptEntity(id = 8, number = "GRN/26-27/00001", supplierId = 2, orderId = 3, supplierInvoiceNumber = "FC-1",
                receivedAt = 5, note = null, totalMinor = 300000),
        )
        assertEquals("receipt-8", receipt["requestId"])
        assertEquals(SharedIds.derive("purchaseOrder", "3"), receipt["orderId"])
        assertNull(receipt["supplierName"])
        val line = SharedMapping.receiptLine(
            PurchaseReceiptLineEntity(id = 9, receiptId = 8, variantId = 11, orderLineId = 12, productName = "Shirt",
                variantLabel = "M", stockUnit = "UNIT", quantity = 10, unitCostMinor = 30000, batchNumber = null,
                expiryEpochDay = 0L, batchId = 21),
        )
        assertEquals(SharedIds.derive("batch", "21"), line["batchId"])
        assertEquals(SharedIds.derive("purchaseOrderLine", "12"), line["orderLineId"])
        assertEquals("1970-01-01", line["expiryDate"])
    }
}
