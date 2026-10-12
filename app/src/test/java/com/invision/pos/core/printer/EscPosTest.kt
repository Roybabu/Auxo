package com.invision.pos.core.printer

import com.invision.pos.domain.model.ReceiptPaper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun pixels_packEightDotsToAByte_leftmostInTheHighestBit() {
        val image = DotImages.fromPixels(intArrayOf(black, white, white, black, white, white, white, black), 8, 1)
        assertEquals(1, image.bytesPerRow)
        assertEquals(0b1001_0001, image.data[0].toInt() and 0xFF)
    }

    @Test
    fun rowsArePaddedWithWhite_whenTheWidthIsNotAMultipleOfEight() {
        val image = DotImages.fromPixels(IntArray(10) { black }, 10, 1)
        assertEquals(2, image.bytesPerRow)
        assertEquals(0xFF, image.data[0].toInt() and 0xFF)
        assertEquals(0b1100_0000, image.data[1].toInt() and 0xFF)
    }

    @Test
    fun transparentAndLightPixelsAreWhite_darkOnesAreBlack() {
        assertFalse(DotImages.isBlack(0x00000000))
        assertFalse(DotImages.isBlack(white))
        assertFalse(DotImages.isBlack(0xFFC8C8C8.toInt()))
        assertTrue(DotImages.isBlack(0xFF505050.toInt()))
        assertTrue(DotImages.isBlack(black))
        // A fully transparent black pixel (the empty background of a drawing) must not print.
        assertFalse(DotImages.isBlack(0x00000000))
    }

    @Test
    fun rollsPrintAtTheirOwnDotWidth_sheetsDoNot() {
        assertEquals(384, PrinterDots.widthFor(ReceiptPaper.THERMAL_58))
        assertEquals(512, PrinterDots.widthFor(ReceiptPaper.THERMAL_76))
        assertEquals(576, PrinterDots.widthFor(ReceiptPaper.THERMAL_80))
        assertNull(PrinterDots.widthFor(ReceiptPaper.A4))
        assertNull(PrinterDots.widthFor(ReceiptPaper.A5))
    }

    @Test
    fun rasterBand_startsWithTheGsV0Header_withWidthInBytesAndRowsLittleEndian() {
        val image = DotImage(384, 3, ByteArray(48 * 3) { 0x0F })
        val band = EscPos.rasterBand(image, 0, 3)
        assertArrayEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 48, 0, 3, 0), band.copyOfRange(0, 8))
        assertEquals(8 + 48 * 3, band.size)
    }

    @Test
    fun rasterBand_widthOver255Bytes_usesTheHighByte() {
        // 2400 dots = 300 bytes per row = 0x012C.
        val image = DotImage(2400, 1, ByteArray(300))
        val band = EscPos.rasterBand(image, 0, 1)
        assertEquals(0x2C, band[4].toInt())
        assertEquals(0x01, band[5].toInt())
    }

    @Test
    fun job_splitsATallPictureIntoBands_andEndsWithAFeed() {
        val rows = EscPos.BAND_ROWS * 2 + 5
        val image = DotImage(384, rows, ByteArray(48 * rows))
        val job = EscPos.job(image, feedLines = 3, cut = false)
        assertArrayEquals(EscPos.INIT, job.copyOfRange(0, 2))
        // Three raster commands, each with its own header.
        var headers = 0
        var i = 2
        while (i < job.size - 3) {
            if (job[i] == 0x1D.toByte() && job[i + 1] == 0x76.toByte() && job[i + 2] == 0x30.toByte()) {
                val bandRows = (job[i + 6].toInt() and 0xFF) or ((job[i + 7].toInt() and 0xFF) shl 8)
                headers++
                i += 8 + 48 * bandRows
            } else {
                i++
            }
        }
        assertEquals(3, headers)
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 3), job.copyOfRange(job.size - 3, job.size))
    }

    @Test
    fun job_addsTheCutCommandOnlyWhenAsked() {
        val image = DotImage(8, 1, ByteArray(1))
        assertFalse(EscPos.job(image, cut = false).toList().windowed(2).any { it == listOf<Byte>(0x1D, 0x56) })
        val withCut = EscPos.job(image, cut = true)
        assertArrayEquals(EscPos.CUT, withCut.copyOfRange(withCut.size - 4, withCut.size))
    }

    @Test
    fun stack_joinsPicturesOfTheSameWidth_andRefusesDifferentOnes() {
        val a = DotImage(8, 1, byteArrayOf(1))
        val b = DotImage(8, 2, byteArrayOf(2, 3))
        val stacked = DotImage.stack(listOf(a, b))
        assertEquals(3, stacked.heightDots)
        assertArrayEquals(byteArrayOf(1, 2, 3), stacked.data)
        val failed = runCatching { DotImage.stack(listOf(a, DotImage(16, 1, ByteArray(2)))) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun feed_isCappedToWhatTheCommandCanCarry() {
        assertEquals(255, EscPos.feed(1000)[2].toInt() and 0xFF)
        assertEquals(0, EscPos.feed(-4)[2].toInt())
    }
}
