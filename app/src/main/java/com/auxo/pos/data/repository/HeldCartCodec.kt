package com.auxo.pos.data.repository

import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
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
        if (line.stockUnit != com.auxo.pos.domain.model.StockUnit.UNIT) put("unit", line.stockUnit.name)
        line.discount?.let { put("discount", encodeDiscount(it)) }
        put("discountReason", line.discountReason)
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
        stockUnit = com.auxo.pos.domain.model.StockUnit.entries.firstOrNull { it.name == o.optString("unit", "") } ?: com.auxo.pos.domain.model.StockUnit.UNIT,
        discount = o.optJSONObject("discount")?.let(::decodeDiscount),
        discountReason = o.optString("discountReason", ""),
        // Products are looked up again from the shop's stock when the bill is resumed; one-off items have
        // no stock of their own.
        availableStock = if (o.optBoolean("custom", false)) CartLine.UNLIMITED else null,
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
