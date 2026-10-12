package com.auxo.pos.data.repository

import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.billing.SentItem
import com.auxo.pos.domain.billing.ServiceType
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns a cart into text and back, so a parked bill survives the app closing. Loyalty points are not kept:
 * they depend on the customer's balance, which may have changed, so they are chosen again on resume.
 * Stock figures are not kept either; the screen looks them up when the bill is resumed.
 */
object HeldCartCodec {
    private const val VERSION = 1

    fun encode(cart: Cart): String = JSONObject().apply {
        put("v", VERSION)
        put("lines", JSONArray().also { array -> cart.lines.forEach { array.put(encodeLine(it)) } })
        cart.billDiscount?.let { put("billDiscount", encodeDiscount(it)) }
        put("billDiscountReason", cart.billDiscountReason)
        put("customer", encodeCustomer(cart.customer))
        cart.placeOfSupplyStateCode?.let { put("placeOfSupply", it) }
        cart.serviceType?.let { put("service", it.name) }
        put("table", cart.tableNumber)
    }.toString()

    /** The cart, or null if the text is damaged or from a newer version of the app. */
    fun decode(text: String): Cart? = try {
        val root = JSONObject(text)
        if (root.optInt("v", 0) != VERSION) {
            null
        } else {
            val lines = root.getJSONArray("lines")
            Cart(
                lines = (0 until lines.length()).map { decodeLine(lines.getJSONObject(it)) },
                billDiscount = root.optJSONObject("billDiscount")?.let(::decodeDiscount),
                billDiscountReason = root.optString("billDiscountReason", ""),
                customer = decodeCustomer(root.getJSONObject("customer")),
                placeOfSupplyStateCode = if (root.has("placeOfSupply")) root.getString("placeOfSupply") else null,
                serviceType = if (root.has("service")) ServiceType.entries.firstOrNull { it.name == root.getString("service") } else null,
                tableNumber = root.optString("table", ""),
            )
        }
    } catch (e: org.json.JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun encodeLine(line: CartLine) = JSONObject().apply {
        put("key", line.variantId)
        put("productId", line.productId)
        put("name", line.productName)
        put("variant", line.variantLabel)
        put("barcode", line.barcode)
        line.hsnCode?.let { put("hsn", it) }
        put("price", line.unitPrice.minorUnits)
        line.mrp?.let { put("mrp", it.minorUnits) }
        line.gstRate?.let { put("gst", encodeRate(it)) }
        put("qty", line.quantity)
        line.discount?.let { put("discount", encodeDiscount(it)) }
        put("discountReason", line.discountReason)
        line.menuItemId?.let { put("menuItemId", it) }
        put("custom", line.isCustom)
    }

    private fun decodeLine(o: JSONObject): CartLine = CartLine(
        variantId = o.getLong("key"),
        productId = o.getLong("productId"),
        productName = o.getString("name"),
        variantLabel = o.optString("variant", ""),
        barcode = o.optString("barcode", ""),
        hsnCode = if (o.has("hsn")) o.getString("hsn") else null,
        unitPrice = Money(o.getLong("price")),
        mrp = if (o.has("mrp")) Money(o.getLong("mrp")) else null,
        gstRate = if (o.has("gst")) decodeRate(o.getString("gst")) else null,
        quantity = o.getInt("qty"),
        discount = o.optJSONObject("discount")?.let(::decodeDiscount),
        discountReason = o.optString("discountReason", ""),
        // Shop products are looked up again from the shop's stock when the bill is resumed; dishes and
        // one-off items have no stock of their own.
        availableStock = if (o.optBoolean("custom", false) || o.has("menuItemId")) CartLine.UNLIMITED else null,
        menuItemId = if (o.has("menuItemId")) o.getLong("menuItemId") else null,
        isCustom = o.optBoolean("custom", false),
    )

    private fun encodeRate(rate: GstRate): String = when (rate) {
        is GstRate.Fixed -> "F${rate.basisPoints}"
        GstRate.Apparel -> "A"
    }

    private fun decodeRate(text: String): GstRate = when {
        text == "A" -> GstRate.Apparel
        text.startsWith("F") -> GstRate.Fixed(text.drop(1).toInt())
        else -> throw IllegalArgumentException("Unknown GST rate")
    }

    private fun encodeDiscount(discount: Discount) = JSONObject().apply {
        when (discount) {
            is Discount.Amount -> { put("type", "amount"); put("value", discount.value.minorUnits) }
            is Discount.Percent -> { put("type", "percent"); put("value", discount.basisPoints) }
        }
    }

    private fun decodeDiscount(o: JSONObject): Discount = when (o.getString("type")) {
        "amount" -> Discount.Amount(Money(o.getLong("value")))
        "percent" -> Discount.Percent(o.getInt("value"))
        else -> throw IllegalArgumentException("Unknown discount")
    }

    private fun encodeCustomer(c: CustomerDetails) = JSONObject().apply {
        put("name", c.name)
        put("phone", c.phone)
        put("gstin", c.gstin)
        c.customerId?.let { put("id", it) }
    }

    private fun decodeCustomer(o: JSONObject) = CustomerDetails(
        name = o.optString("name", ""),
        phone = o.optString("phone", ""),
        gstin = o.optString("gstin", ""),
        customerId = if (o.has("id")) o.getLong("id") else null,
    )
}

/** Keeps what the kitchen has been told as text on the open order. */
object KitchenOrderCodec {
    fun encode(items: List<SentItem>): String = JSONArray().also { array ->
        items.forEach { array.put(JSONObject().put("key", it.key).put("name", it.name).put("variant", it.variant).put("qty", it.quantity)) }
    }.toString()

    /** The items, or an empty list for missing or damaged text (the kitchen then gets everything again). */
    fun decode(text: String?): List<SentItem> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                SentItem(o.getLong("key"), o.getString("name"), o.optString("variant", ""), o.getInt("qty"))
            }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }
}
