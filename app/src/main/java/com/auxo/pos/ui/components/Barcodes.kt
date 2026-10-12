package com.auxo.pos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.auxo.pos.R
import com.auxo.pos.core.barcode.BarcodeEncoder

/** Draws the bars for [value]. Draws nothing if the value can't be encoded. */
@Composable
fun BarcodeImage(
    value: String,
    modifier: Modifier = Modifier,
    barColor: Color = Color.Black,
) {
    val pattern = remember(value) { BarcodeEncoder.encode(value) } ?: return
    val description = stringResource(R.string.barcode_description, value)
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val totalModules = pattern.modules.size + pattern.quietZoneModules * 2
        val moduleWidth = size.width / totalModules
        var x = pattern.quietZoneModules * moduleWidth
        for (isBar in pattern.modules) {
            if (isBar) {
                drawRect(color = barColor, topLeft = Offset(x, 0f), size = Size(moduleWidth, size.height))
            }
            x += moduleWidth
        }
    }
}

sealed interface ScanOutcome {
    data class Scanned(val value: String) : ScanOutcome
    data object Cancelled : ScanOutcome

    /** Google Play services or the scanner module is not available. */
    data object Unavailable : ScanOutcome
}

/**
 * Returns a function that opens Google's code scanner (camera UI supplied by Play
 * services, so the app needs no camera permission) and reports the result.
 * USB and Bluetooth scanners work separately: they type into the focused text field.
 */
@Composable
fun rememberBarcodeScanner(onResult: (ScanOutcome) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnResult by rememberUpdatedState(onResult)
    return remember(context) {
        {
            try {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_QR_CODE,
                    )
                    .enableAutoZoom()
                    .build()
                GmsBarcodeScanning.getClient(context, options)
                    .startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue?.trim()
                        latestOnResult(if (raw.isNullOrEmpty()) ScanOutcome.Cancelled else ScanOutcome.Scanned(raw))
                    }
                    .addOnCanceledListener { latestOnResult(ScanOutcome.Cancelled) }
                    .addOnFailureListener { latestOnResult(ScanOutcome.Unavailable) }
            } catch (e: RuntimeException) {
                latestOnResult(ScanOutcome.Unavailable)
            }
        }
    }
}
