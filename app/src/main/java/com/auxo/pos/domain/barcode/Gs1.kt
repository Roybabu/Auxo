package com.auxo.pos.domain.barcode

/** GS1 modulo-10 check digits used by EAN-8, UPC-A, EAN-13 and GTIN-14. */
object Gs1 {

    /** Check digit for [payload], the code without its last digit. */
    fun checkDigit(payload: String): Int {
        require(payload.isNotEmpty() && payload.all { it in '0'..'9' }) { "Payload must be digits" }
        var sum = 0
        // Weights alternate 3, 1, 3, ... starting from the rightmost payload digit.
        payload.reversed().forEachIndexed { index, char ->
            val digit = char - '0'
            sum += if (index % 2 == 0) digit * 3 else digit
        }
        return (10 - sum % 10) % 10
    }

    /** True if [code] is all digits, a GS1 length, and its last digit is the correct check digit. */
    fun isValid(code: String): Boolean {
        if (code.length !in GS1_LENGTHS || !code.all { it in '0'..'9' }) return false
        return checkDigit(code.dropLast(1)) == code.last() - '0'
    }

    private val GS1_LENGTHS = setOf(8, 12, 13, 14)
}
