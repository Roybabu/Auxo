// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Version 16 moves the restaurant menu into the product catalogue. Each dish becomes a product in the Restaurant
 * category with one variant and no stock count; the menu's section becomes the product's subcategory. Past
 * invoices keep their own copy of the name, price and tax, so history does not change; their dish lines are only
 * linked to the new product. Parked bills, quotations and kitchen tickets that name a dish are pointed at it.
 * Recipes are not carried over: the menu and recipe tables are dropped afterwards.
 */
internal object DishMigration {

    /** Where a dish went. */
    data class Placement(val productId: Long, val variantId: Long)

    /** First key used for one-off items; dish keys in a saved cart were minus the dish id, far above this. */
    private const val CUSTOM_KEY_BASE: Long = -(1L shl 40)

    fun run(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN subcategory TEXT")
        db.execSQL("ALTER TABLE products ADD COLUMN available INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE products ADD COLUMN tracks_stock INTEGER NOT NULL DEFAULT 1")

        val moved = moveDishes(db)
        for ((dishId, placement) in moved) {
            db.execSQL(
                "UPDATE invoice_lines SET variant_id = ? WHERE menu_item_id = ? AND variant_id IS NULL",
                arrayOf<Any?>(placement.variantId, dishId),
            )
        }
        repointCarts(db, "held_bills", moved)
        repointCarts(db, "quotations", moved)
        repointKitchenTickets(db, moved)

        db.execSQL("DROP TABLE recipe_lines")
        db.execSQL("DROP TABLE menu_items")
    }

    private fun moveDishes(db: SupportSQLiteDatabase): Map<Long, Placement> {
        data class Dish(
            val id: Long, val name: String, val section: String, val description: String?, val priceMinor: Long,
            val gstBasisPoints: Int, val sacCode: String?, val dietType: String?, val available: Boolean,
            val createdAt: Long, val updatedAt: Long,
        )

        val dishes = mutableListOf<Dish>()
        db.query(
            "SELECT id, name, section, description, price_minor, gst_rate_bp, sac_code, diet_type, available, created_at, updated_at " +
                "FROM menu_items ORDER BY id",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                dishes += Dish(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    section = cursor.getString(2),
                    description = if (cursor.isNull(3)) null else cursor.getString(3),
                    priceMinor = cursor.getLong(4),
                    gstBasisPoints = cursor.getInt(5),
                    sacCode = if (cursor.isNull(6)) null else cursor.getString(6),
                    dietType = if (cursor.isNull(7)) null else cursor.getString(7),
                    available = cursor.getInt(8) != 0,
                    createdAt = cursor.getLong(9),
                    updatedAt = cursor.getLong(10),
                )
            }
        }

        val moved = linkedMapOf<Long, Placement>()
        for (dish in dishes) {
            db.execSQL(
                "INSERT INTO products (name, brand, category, description, material, diet_type, hsn_code, gst_rule, gst_rate_bp, " +
                    "created_at, updated_at, stock_unit, preferred_supplier_id, photo_file, subcategory, available, tracks_stock) " +
                    "VALUES (?, NULL, 'RESTAURANT', ?, NULL, ?, ?, 'FIXED', ?, ?, ?, NULL, NULL, NULL, ?, ?, 0)",
                arrayOf<Any?>(
                    dish.name, dish.description, dish.dietType, dish.sacCode, dish.gstBasisPoints,
                    dish.createdAt, dish.updatedAt, dish.section.trim().ifEmpty { null }, if (dish.available) 1 else 0,
                ),
            )
            val productId = lastInsertedId(db)
            db.execSQL(
                "INSERT INTO product_variants (product_id, sku, barcode, size, colour, net_quantity_milli, net_quantity_unit, " +
                    "price_minor, mrp_minor, cost_minor, stock_quantity, low_stock_threshold, batch_number, expiry_epoch_day, reorder_quantity) " +
                    "VALUES (?, NULL, ?, NULL, NULL, NULL, NULL, ?, NULL, NULL, 0, 0, NULL, NULL, 0)",
                arrayOf<Any?>(productId, barcodeFor(dish.id), dish.priceMinor),
            )
            moved[dish.id] = Placement(productId, lastInsertedId(db))
        }
        return moved
    }

    /** The in-store code a moved dish gets; letters, digits and a dash are a valid Code 128 barcode. */
    private fun barcodeFor(dishId: Long): String = "DISH-$dishId"

    private fun lastInsertedId(db: SupportSQLiteDatabase): Long =
        db.query("SELECT last_insert_rowid()").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    /** Rewrites the cart text of parked bills or quotations so dish lines become ordinary product lines. */
    private fun repointCarts(db: SupportSQLiteDatabase, table: String, moved: Map<Long, Placement>) {
        val rows = mutableListOf<Pair<Long, String>>()
        db.query("SELECT id, payload FROM $table").use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getLong(0) to cursor.getString(1)
        }
        for ((id, payload) in rows) {
            val rewritten = repointCart(payload, moved) ?: continue
            db.execSQL("UPDATE $table SET payload = ? WHERE id = ?", arrayOf<Any?>(rewritten, id))
        }
    }

    /** The cart text with its dish lines repointed, or null when nothing changed or the text is not readable. */
    fun repointCart(payload: String, moved: Map<Long, Placement>): String? = try {
        val root = JSONObject(payload)
        val lines = root.getJSONArray("lines")
        var changed = false
        for (index in 0 until lines.length()) {
            val line = lines.getJSONObject(index)
            if (!line.has("menuItemId")) continue
            val dishId = line.getLong("menuItemId")
            val placement = moved[dishId]
            line.remove("menuItemId")
            if (placement != null) {
                line.put("key", placement.variantId)
                line.put("productId", placement.productId)
                line.put("barcode", barcodeFor(dishId))
            } else {
                // The dish was deleted before the move: keep the line as a one-off item so the bill still opens.
                line.put("custom", true)
            }
            changed = true
        }
        if (changed) root.toString() else null
    } catch (e: JSONException) {
        null
    }

    /** What the kitchen has been told is kept per line key, so the keys of moved dishes change too. */
    private fun repointKitchenTickets(db: SupportSQLiteDatabase, moved: Map<Long, Placement>) {
        val rows = mutableListOf<Pair<Long, String>>()
        db.query("SELECT id, kot_sent FROM held_bills WHERE kot_sent IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getLong(0) to cursor.getString(1)
        }
        for ((id, text) in rows) {
            val rewritten = repointTickets(text, moved) ?: continue
            db.execSQL("UPDATE held_bills SET kot_sent = ? WHERE id = ?", arrayOf<Any?>(rewritten, id))
        }
    }

    fun repointTickets(text: String, moved: Map<Long, Placement>): String? = try {
        val items = JSONArray(text)
        var changed = false
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val key = item.getLong("key")
            // A dish was kept under minus its id; one-off items sit far below that.
            if (key < 0 && key > CUSTOM_KEY_BASE) {
                moved[-key]?.let {
                    item.put("key", it.variantId)
                    changed = true
                }
            }
        }
        if (changed) items.toString() else null
    } catch (e: JSONException) {
        null
    }
}
