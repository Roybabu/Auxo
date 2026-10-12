package com.auxo.pos.domain.barcode

import kotlin.random.Random

/**
 * Creates EAN-13 codes for items that have no manufacturer barcode.
 *
 * Codes start with "2", the GS1 prefix range reserved for in-store use, so they
 * never clash with real product barcodes. Uniqueness against saved products is
 * checked by the caller.
 */
class InternalBarcodeGenerator(
    private val random: Random = Random.Default,
) {
    fun next(): String {
        val payload = buildString {
            append(IN_STORE_PREFIX)
            repeat(PAYLOAD_LENGTH - IN_STORE_PREFIX.length) { append(random.nextInt(10)) }
        }
        return payload + Gs1.checkDigit(payload)
    }

    /** Returns a code that is not in [taken] and for which [isUsed] is false. */
    suspend fun nextUnique(
        taken: Set<String>,
        isUsed: suspend (String) -> Boolean,
    ): String {
        repeat(MAX_ATTEMPTS) {
            val candidate = next()
            if (candidate !in taken && !isUsed(candidate)) return candidate
        }
        error("Could not generate a unique barcode after $MAX_ATTEMPTS attempts")
    }

    companion object {
        const val IN_STORE_PREFIX = "2"
        private const val PAYLOAD_LENGTH = 12
        private const val MAX_ATTEMPTS = 20

        fun isInStoreCode(code: String): Boolean =
            code.length == 13 && code.startsWith(IN_STORE_PREFIX) && Gs1.isValid(code)
    }
}
