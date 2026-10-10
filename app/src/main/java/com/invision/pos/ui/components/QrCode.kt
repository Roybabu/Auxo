package com.invision.pos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrEncoder {
    /** Encodes [content] as a QR code with a 4-module quiet zone, or null if it is too long. */
    fun encode(content: String): BitMatrix? = try {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    } catch (e: WriterException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Draws a QR code for [content], scaled to the composable's size. */
@Composable
fun QrCode(content: String, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { QrEncoder.encode(content) } ?: return
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val modules = matrix.width
        val cell = minOf(size.width, size.height) / modules
        drawRect(Color.White, size = Size(cell * modules, cell * modules))
        for (y in 0 until matrix.height) {
            for (x in 0 until modules) {
                if (matrix.get(x, y)) {
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
