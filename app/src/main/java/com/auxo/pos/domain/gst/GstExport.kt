package com.auxo.pos.domain.gst

import com.auxo.pos.domain.reports.ReportCsv
import java.time.ZoneId

/** The GST report as files for the accountant. */
object GstExport {

    /** One CSV with every table one after the other, each under its title and followed by a blank line. */
    fun csv(report: GstReport, zone: ZoneId): String = buildString {
        report.tables.forEach { table ->
            append(ReportCsv.escape(table.title)).append("\r\n")
            append(ReportCsv.write(table, zone))
            append("\r\n")
        }
    }

    /** File name without extension, e.g. "GST-report-2026-10-01_2026-10-31". */
    fun baseName(report: GstReport): String = "GST-report-" + report.period.slug
}
