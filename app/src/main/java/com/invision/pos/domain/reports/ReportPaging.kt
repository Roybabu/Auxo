package com.invision.pos.domain.reports

/** Splits a long report into fixed-size pages for the screen. The CSV export is never paged. */
object ReportPaging {

    const val PAGE_SIZE = 100

    fun pageCount(rowCount: Int, pageSize: Int = PAGE_SIZE): Int =
        if (rowCount <= 0) 1 else (rowCount + pageSize - 1) / pageSize

    /** Keeps [page] (counted from 0) inside the pages that exist. */
    fun clamp(page: Int, rowCount: Int, pageSize: Int = PAGE_SIZE): Int =
        page.coerceIn(0, pageCount(rowCount, pageSize) - 1)

    /** The rows of [page] as a start (inclusive) and end (exclusive) index. */
    fun range(page: Int, rowCount: Int, pageSize: Int = PAGE_SIZE): IntRange {
        val safe = clamp(page, rowCount, pageSize)
        val start = safe * pageSize
        return start until minOf(rowCount, start + pageSize)
    }
}
