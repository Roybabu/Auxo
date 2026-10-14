package com.auxo.pos.domain.billing

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.auxo.pos.domain.model.Money

/** GST identification numbers (GSTIN): 15 characters ending in a mod-36 check character. */
/**
 * FSSAI licence or registration number: 14 digits. There is no public check digit, so only the shape can be
 * checked; the app cannot confirm the number with FSSAI.
 */
object Fssai {
    const val LENGTH = 14

    /** Digits only: spaces, dashes and letters typed or pasted with the number are dropped. */
    fun normalize(text: String): String = text.filter { it in '0'..'9' }

    fun isValid(text: String): Boolean = normalize(text).length == LENGTH
}

object Gstin {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val PATTERN = Regex("""[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]""")

    fun normalize(text: String): String = text.trim().uppercase().replace(" ", "")

    fun isValid(text: String): Boolean {
        val gstin = normalize(text)
        if (!PATTERN.matches(gstin)) return false
        if (IndianStates.byCode(gstin.take(2)) == null) return false
        return checkCharacter(gstin.take(14)) == gstin[14]
    }

    /** The two-digit state code at the start of a GSTIN. */
    fun stateCode(gstin: String): String = normalize(gstin).take(2)

    fun checkCharacter(first14: String): Char {
        var factor = 2
        var sum = 0
        for (char in first14.reversed()) {
            val addend = factor * CHARS.indexOf(char)
            factor = if (factor == 2) 1 else 2
            sum += addend / 36 + addend % 36
        }
        return CHARS[(36 - sum % 36) % 36]
    }
}

/** Indian mobile numbers: 10 digits starting with 6-9, optionally written with +91 or 0. */
object IndianPhone {
    private val MOBILE = Regex("""[6-9]\d{9}""")

    /** The 10-digit number, or null if [text] is not a valid mobile number. */
    fun normalizeMobile(text: String): String? {
        var digits = text.filter { it.isDigit() }
        if (text.trim().startsWith("+") && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.drop(1)
        return digits.takeIf { MOBILE.matches(it) }
    }

    /** Store phone numbers may be landlines: 6 to 15 digits, spaces, dashes and a leading +. */
    fun isValidContactNumber(text: String): Boolean {
        val trimmed = text.trim()
        if (!Regex("""\+?[0-9 \-]{6,20}""").matches(trimmed)) return false
        return trimmed.count { it.isDigit() } in 6..15
    }
}

/** UPI virtual payment addresses and the upi://pay links scanned by UPI apps. */
object Upi {
    private val VPA = Regex("""[A-Za-z0-9.\-_]{2,256}@[A-Za-z][A-Za-z0-9]{1,64}""")

    fun isValidVpa(text: String): Boolean = VPA.matches(text.trim())

    /** Payment link with a fixed amount, following the NPCI UPI linking format. */
    fun paymentUri(vpa: String, payeeName: String, amount: Money, note: String, reference: String): String {
        val params = listOf(
            "pa" to vpa.trim(),
            "pn" to payeeName.trim(),
            "am" to IndianNumberFormat.amount(amount).replace(",", ""),
            "cu" to "INR",
            "tn" to note,
            "tr" to reference,
        ).filter { it.second.isNotEmpty() }
        return "upi://pay?" + params.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
    }

    /**
     * The UPI reference for an invoice: letters and digits only, at most 35 characters, which is what UPI apps
     * accept ("INV/26-27/00007" becomes "INV262700007").
     */
    fun reference(invoiceNumber: String): String = invoiceNumber.filter { it.isLetterOrDigit() }.take(MAX_REFERENCE)

    /**
     * The link a customer scans to pay one bill: the shop's UPI ID, the exact [amount] and the invoice number as
     * the reference and the note. Null when there is nothing safe to show: no UPI ID, a UPI ID that is not
     * valid, or an amount that is not above zero.
     */
    fun billLink(vpa: String, payeeName: String, shopName: String, amount: Money, invoiceNumber: String): String? {
        if (!isValidVpa(vpa) || amount <= Money.ZERO) return null
        val payee = payeeName.trim().ifEmpty { shopName.trim() }.ifEmpty { vpa.trim() }.take(MAX_PAYEE)
        val number = invoiceNumber.trim()
        val note = if (number.isEmpty()) shopName.trim() else "Invoice $number"
        return paymentUri(vpa, payee, amount, note.take(MAX_NOTE), reference(number))
    }

    private const val MAX_REFERENCE = 35
    private const val MAX_NOTE = 50
    private const val MAX_PAYEE = 50

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

data class IndianState(val code: String, val name: String)

/** States and union territories with their GST state codes. */
object IndianStates {
    val ALL: List<IndianState> = listOf(
        IndianState("35", "Andaman and Nicobar Islands"),
        IndianState("37", "Andhra Pradesh"),
        IndianState("12", "Arunachal Pradesh"),
        IndianState("18", "Assam"),
        IndianState("10", "Bihar"),
        IndianState("04", "Chandigarh"),
        IndianState("22", "Chhattisgarh"),
        IndianState("26", "Dadra and Nagar Haveli and Daman and Diu"),
        IndianState("07", "Delhi"),
        IndianState("30", "Goa"),
        IndianState("24", "Gujarat"),
        IndianState("06", "Haryana"),
        IndianState("02", "Himachal Pradesh"),
        IndianState("01", "Jammu and Kashmir"),
        IndianState("20", "Jharkhand"),
        IndianState("29", "Karnataka"),
        IndianState("32", "Kerala"),
        IndianState("38", "Ladakh"),
        IndianState("31", "Lakshadweep"),
        IndianState("23", "Madhya Pradesh"),
        IndianState("27", "Maharashtra"),
        IndianState("14", "Manipur"),
        IndianState("17", "Meghalaya"),
        IndianState("15", "Mizoram"),
        IndianState("13", "Nagaland"),
        IndianState("21", "Odisha"),
        IndianState("34", "Puducherry"),
        IndianState("03", "Punjab"),
        IndianState("08", "Rajasthan"),
        IndianState("11", "Sikkim"),
        IndianState("33", "Tamil Nadu"),
        IndianState("36", "Telangana"),
        IndianState("16", "Tripura"),
        IndianState("09", "Uttar Pradesh"),
        IndianState("05", "Uttarakhand"),
        IndianState("19", "West Bengal"),
        IndianState("97", "Other Territory"),
    )

    /** Older codes still found in some GSTINs. */
    private val LEGACY = listOf(
        IndianState("25", "Daman and Diu"),
        IndianState("28", "Andhra Pradesh (old)"),
        IndianState("99", "Centre Jurisdiction"),
    )

    fun byCode(code: String): IndianState? = (ALL + LEGACY).firstOrNull { it.code == code }

    fun label(code: String): String = byCode(code)?.let { "${it.name} (${it.code})" } ?: code
}
