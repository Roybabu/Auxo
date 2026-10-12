package com.auxo.pos.domain.barcode

enum class BarcodeSymbology { EAN_13, EAN_8, UPC_A, CODE_128 }

enum class BarcodeError { EMPTY, INVALID_CHECK_DIGIT, INVALID_CHARACTERS, TOO_LONG }

sealed interface BarcodeCheck {
    data class Valid(val symbology: BarcodeSymbology) : BarcodeCheck
    data class Invalid(val error: BarcodeError) : BarcodeCheck
}

/**
 * Decides how a barcode value can be printed and whether it is acceptable.
 *
 * - 8, 12 or 13 digits are treated as EAN-8, UPC-A or EAN-13 and must have a valid check digit.
 * - Anything else is printed as Code 128 and may use letters, digits and - . _ /
 */
object BarcodeRules {
    const val MAX_LENGTH = 32

    private val CODE_128_CHARS = Regex("""[A-Za-z0-9\-._/]+""")

    fun check(value: String): BarcodeCheck {
        if (value.isEmpty()) return BarcodeCheck.Invalid(BarcodeError.EMPTY)
        if (value.length > MAX_LENGTH) return BarcodeCheck.Invalid(BarcodeError.TOO_LONG)

        val allDigits = value.all { it in '0'..'9' }
        if (allDigits && value.length in setOf(8, 12, 13)) {
            if (!Gs1.isValid(value)) return BarcodeCheck.Invalid(BarcodeError.INVALID_CHECK_DIGIT)
            val symbology = when (value.length) {
                8 -> BarcodeSymbology.EAN_8
                12 -> BarcodeSymbology.UPC_A
                else -> BarcodeSymbology.EAN_13
            }
            return BarcodeCheck.Valid(symbology)
        }

        return if (CODE_128_CHARS.matches(value)) {
            BarcodeCheck.Valid(BarcodeSymbology.CODE_128)
        } else {
            BarcodeCheck.Invalid(BarcodeError.INVALID_CHARACTERS)
        }
    }

    /**
     * Forms a scanner may send for the same product. Many scanners send a 12-digit
     * UPC-A as a 13-digit EAN-13 with a leading 0, or the other way round; GS1 check
     * digits are the same for both.
     */
    fun equivalents(scanned: String): Set<String> {
        val code = scanned.trim()
        return when {
            code.length == 13 && code.startsWith('0') && Gs1.isValid(code) -> setOf(code, code.drop(1))
            code.length == 12 && Gs1.isValid(code) -> setOf(code, "0$code")
            else -> setOf(code)
        }
    }
}
