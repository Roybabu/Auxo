package com.invision.pos.domain.reports

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportPagingTest {

    @Test
    fun pageCount_roundsUp_andIsAtLeastOne() {
        assertEquals(1, ReportPaging.pageCount(0))
        assertEquals(1, ReportPaging.pageCount(100))
        assertEquals(2, ReportPaging.pageCount(101))
        assertEquals(3, ReportPaging.pageCount(250))
    }

    @Test
    fun range_coversEveryRowOnceAcrossThePages() {
        val seen = (0 until ReportPaging.pageCount(250)).flatMap { ReportPaging.range(it, 250).toList() }
        assertEquals((0 until 250).toList(), seen)
        assertEquals(200..249, ReportPaging.range(2, 250))
    }

    @Test
    fun range_staysInsideTheDataWhenTheTableShrinks() {
        // On page 3 of 250 rows, a refresh leaves only 120: show the last page instead of nothing.
        assertEquals(1, ReportPaging.clamp(2, 120))
        assertEquals(100..119, ReportPaging.range(2, 120))
        assertEquals(0, ReportPaging.clamp(-1, 120))
        assertEquals(IntRange.EMPTY, ReportPaging.range(0, 0))
    }
}
