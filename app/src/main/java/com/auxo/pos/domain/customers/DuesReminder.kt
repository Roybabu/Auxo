package com.auxo.pos.domain.customers

import com.auxo.pos.domain.billing.IndianPhone
import com.auxo.pos.domain.billing.Upi
import com.auxo.pos.domain.model.Language
import com.auxo.pos.domain.model.Money
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

/** What a reminder says about the shop: its name, the UPI ID customers can pay to (valid ones only), and the language to write in. */
data class ReminderShop(val shopName: String, val upiId: String?, val language: Language) {
    companion object {
        /** [upiId] is kept only when it is a valid UPI ID, so a mistyped one never goes into a message. */
        fun of(shopName: String, upiId: String, language: Language) =
            ReminderShop(shopName.trim(), upiId.trim().takeIf { Upi.isValidVpa(it) }, language)
    }
}

/** How a customer's dues are shown in the Owing list. */
enum class OwingSort { AMOUNT, AGE }

object DuesReminder {

    /**
     * The reminder text. [body] has %1$s (customer), %2$s (shop) and %3$s (amount); [upiLine] has %1$s (UPI ID) and
     * is added only when the shop has a valid UPI ID. Nothing else is added: no link, no bank details.
     */
    fun message(body: String, upiLine: String, customerName: String, shop: ReminderShop, amountText: String): String {
        val name = customerName.trim().ifEmpty { "-" }
        val text = String.format(java.util.Locale.ENGLISH, body, name, shop.shopName, amountText)
        return if (shop.upiId == null) text else text + "\n" + String.format(java.util.Locale.ENGLISH, upiLine, shop.upiId)
    }

    /** The number WhatsApp wants: country code 91 and the 10-digit mobile, or null if [phone] is missing or not a mobile number. */
    fun whatsAppNumber(phone: String?): String? = phone?.let { IndianPhone.normalizeMobile(it) }?.let { "91$it" }

    /** A wa.me link that opens a chat with [number] with [message] typed in (not sent). */
    fun whatsAppLink(number: String, message: String): String =
        "https://wa.me/$number?text=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20")

    /** Whole days since [sinceMillis], 0 on the same day. Null when nothing is owed. */
    fun ageInDays(sinceMillis: Long?, today: LocalDate, zone: ZoneId): Int? {
        sinceMillis ?: return null
        val day = java.time.Instant.ofEpochMilli(sinceMillis).atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(day, today).toInt().coerceAtLeast(0)
    }

    /**
     * The Owing list: customers who owe money, largest amount first or oldest dues first. Ties fall back to the
     * other measure, then the name, so the order never jumps around.
     */
    fun sortOwing(accounts: List<com.auxo.pos.domain.model.CustomerAccount>, sort: OwingSort): List<com.auxo.pos.domain.model.CustomerAccount> {
        val owing = accounts.filter { it.balance > Money.ZERO }
        val byName = compareBy<com.auxo.pos.domain.model.CustomerAccount> { it.customer.name.lowercase() }
        return when (sort) {
            OwingSort.AMOUNT -> owing.sortedWith(compareByDescending<com.auxo.pos.domain.model.CustomerAccount> { it.balance }.thenBy { it.owingSinceMillis ?: Long.MAX_VALUE }.then(byName))
            OwingSort.AGE -> owing.sortedWith(compareBy<com.auxo.pos.domain.model.CustomerAccount> { it.owingSinceMillis ?: Long.MAX_VALUE }.thenByDescending { it.balance }.then(byName))
        }
    }
}
