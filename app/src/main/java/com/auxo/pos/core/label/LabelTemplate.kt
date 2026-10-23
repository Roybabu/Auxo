// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.label

import kotlin.math.ceil

/** Physical layout of a label stock, in millimetres. */
data class LabelTemplate(
    val id: String,
    val pageWidthMm: Float,
    val pageHeightMm: Float,
    val columns: Int,
    val rows: Int,
    val labelWidthMm: Float,
    val labelHeightMm: Float,
    val marginLeftMm: Float = 0f,
    val marginTopMm: Float = 0f,
    val gapXMm: Float = 0f,
    val gapYMm: Float = 0f,
) {
    val labelsPerPage: Int get() = columns * rows

    companion object {
        /** A4 sheet with 24 labels of 63.5 x 33.9 mm (3 x 8), a common office label size. */
        val A4_24 = LabelTemplate(
            id = "a4_24",
            pageWidthMm = 210f,
            pageHeightMm = 297f,
            columns = 3,
            rows = 8,
            labelWidthMm = 63.5f,
            labelHeightMm = 33.9f,
            marginLeftMm = 7.25f,
            marginTopMm = 12.9f,
            gapXMm = 2.5f,
        )

        /** One 50 x 25 mm label per page, for label-roll printers. */
        val ROLL_50X25 = LabelTemplate(
            id = "roll_50x25",
            pageWidthMm = 50f,
            pageHeightMm = 25f,
            columns = 1,
            rows = 1,
            labelWidthMm = 50f,
            labelHeightMm = 25f,
        )

        val ALL = listOf(A4_24, ROLL_50X25)
    }
}

/** Where one label goes: page index and top-left corner in millimetres. */
data class LabelSlot(val pageIndex: Int, val leftMm: Float, val topMm: Float)

object LabelSheetLayout {

    fun pageCount(template: LabelTemplate, labelCount: Int): Int =
        if (labelCount <= 0) 0 else ceil(labelCount / template.labelsPerPage.toDouble()).toInt()

    /** Fills each page left to right, top to bottom. */
    fun slots(template: LabelTemplate, labelCount: Int): List<LabelSlot> =
        (0 until labelCount.coerceAtLeast(0)).map { index ->
            val page = index / template.labelsPerPage
            val positionOnPage = index % template.labelsPerPage
            val column = positionOnPage % template.columns
            val row = positionOnPage / template.columns
            LabelSlot(
                pageIndex = page,
                leftMm = template.marginLeftMm + column * (template.labelWidthMm + template.gapXMm),
                topMm = template.marginTopMm + row * (template.labelHeightMm + template.gapYMm),
            )
        }

    fun mmToPoints(mm: Float): Float = mm * POINTS_PER_INCH / MM_PER_INCH

    private const val POINTS_PER_INCH = 72f
    private const val MM_PER_INCH = 25.4f
}

/** Text and barcode printed on one label. */
data class LabelContent(
    val title: String,
    val subtitle: String?,
    val price: String,
    val mrp: String?,
    val barcode: String,
)
