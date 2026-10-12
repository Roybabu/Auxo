package com.auxo.pos.core.barcode

import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.oned.EAN8Writer
import com.auxo.pos.domain.barcode.BarcodeCheck
import com.auxo.pos.domain.barcode.BarcodeRules
import com.auxo.pos.domain.barcode.BarcodeSymbology

/**
 * Bars of a 1D barcode as a row of modules (true = black bar), plus the
 * human-readable text printed under it.
 */
class BarcodePattern(
    val modules: BooleanArray,
    val symbology: BarcodeSymbology,
    val text: String,
) {
    /** Blank modules needed on each side so scanners can find the code. */
    val quietZoneModules: Int
        get() = if (symbology == BarcodeSymbology.CODE_128) 10 else 9
}

object BarcodeEncoder {

    /** Encodes [value] using the symbology chosen by [BarcodeRules]. Returns null if the value is invalid. */
    fun encode(value: String): BarcodePattern? {
        val check = BarcodeRules.check(value) as? BarcodeCheck.Valid ?: return null
        val modules = try {
            when (check.symbology) {
                BarcodeSymbology.EAN_13 -> EAN13Writer().encode(value)
                // UPC-A is an EAN-13 with a leading zero; the bars are identical.
                BarcodeSymbology.UPC_A -> EAN13Writer().encode("0$value")
                BarcodeSymbology.EAN_8 -> EAN8Writer().encode(value)
                BarcodeSymbology.CODE_128 -> Code128Writer().encode(value)
            }
        } catch (e: IllegalArgumentException) {
            return null
        }
        return BarcodePattern(modules, check.symbology, value)
    }
}
