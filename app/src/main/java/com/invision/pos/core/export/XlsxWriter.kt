package com.invision.pos.core.export

import com.invision.pos.domain.model.formatRatePercent
import com.invision.pos.domain.reports.ReportTable
import com.invision.pos.domain.reports.ReportValue
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes report tables as an Excel workbook (.xlsx), one sheet per table, with no extra library.
 * Amounts and counts are real numbers (so the accountant can add them up), headings and totals are bold,
 * and text is stored as text, never as a formula.
 */
object XlsxWriter {

    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    // Cell formats in styles.xml: the index is the s="" attribute of a cell.
    private const val STYLE_DEFAULT = 0
    private const val STYLE_BOLD = 1
    private const val STYLE_MONEY = 2
    private const val STYLE_MONEY_BOLD = 3
    private const val STYLE_COUNT = 4
    private const val STYLE_COUNT_BOLD = 5

    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun write(tables: List<ReportTable>, zone: ZoneId): ByteArray {
        require(tables.isNotEmpty()) { "A workbook needs at least one sheet" }
        val names = sheetNames(tables.map { it.title })
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(tables.size))
            entry("_rels/.rels", rootRels())
            entry("xl/workbook.xml", workbook(names))
            entry("xl/_rels/workbook.xml.rels", workbookRels(tables.size))
            entry("xl/styles.xml", styles())
            tables.forEachIndexed { index, table -> entry("xl/worksheets/sheet${index + 1}.xml", sheet(table, zone)) }
        }
        return out.toByteArray()
    }

    /** Excel allows 31 characters and no : \ / ? * [ ]; names must be different. */
    internal fun sheetNames(titles: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return titles.map { title ->
            val base = title.replace(Regex("[:\\\\/?*\\[\\]]"), " ").trim().ifEmpty { "Sheet" }.take(31)
            var name = base
            var n = 2
            while (!used.add(name.lowercase())) {
                val suffix = " ($n)"
                name = base.take(31 - suffix.length) + suffix
                n++
            }
            name
        }
    }

    internal fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.append('A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.reverse().toString()
    }

    internal fun escape(text: String): String = buildString {
        for (c in text) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                // Characters XML cannot hold are left out.
                c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' -> Unit
                else -> append(c)
            }
        }
    }

    private fun sheet(table: ReportTable, zone: ZoneId): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("""<cols><col min="1" max="${table.columns.size.coerceAtLeast(1)}" width="18" customWidth="1"/></cols>""")
        append("<sheetData>")
        var rowNumber = 1
        fun row(cells: List<String>) {
            append("""<row r="$rowNumber">""")
            cells.forEachIndexed { i, xml -> append(xml.replace("%REF%", "${columnName(i)}$rowNumber")) }
            append("</row>")
            rowNumber++
        }
        row(table.columns.map { text(it.title, bold = true) })
        table.rows.forEach { values -> row(values.map { cell(it, zone, bold = false) }) }
        table.totals?.let { totals -> row(totals.map { cell(it, zone, bold = true) }) }
        append("</sheetData></worksheet>")
    }

    private fun text(value: String, bold: Boolean) =
        """<c r="%REF%" s="${if (bold) STYLE_BOLD else STYLE_DEFAULT}" t="inlineStr"><is><t xml:space="preserve">${escape(value)}</t></is></c>"""

    private fun number(value: String, style: Int) = """<c r="%REF%" s="$style"><v>$value</v></c>"""

    private fun cell(value: ReportValue, zone: ZoneId, bold: Boolean): String = when (value) {
        is ReportValue.Text -> text(value.value, bold)
        is ReportValue.Count -> number(value.value.toString(), if (bold) STYLE_COUNT_BOLD else STYLE_COUNT)
        is ReportValue.Amount -> number(value.value.toDecimalString(), if (bold) STYLE_MONEY_BOLD else STYLE_MONEY)
        is ReportValue.Quantity -> text(value.unit.format(value.value), bold)
        is ReportValue.Day -> text(value.value.toString(), bold)
        is ReportValue.Moment -> text(DATE_TIME.format(Instant.ofEpochMilli(value.epochMillis).atZone(zone)), bold)
        is ReportValue.Rate -> text(formatRatePercent(value.basisPoints) + "%", bold)
        ReportValue.Empty -> """<c r="%REF%" s="${if (bold) STYLE_BOLD else STYLE_DEFAULT}"/>"""
    }

    private fun contentTypes(sheets: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheets) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRels() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        names.forEachIndexed { i, name -> append("""<sheet name="${escape(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheets: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheets) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheets + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    /** Index 0 plain, 1 bold, 2 money (#,##0.00), 3 bold money, 4 whole number, 5 bold whole number. */
    private fun styles() =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="6">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="4" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
            """<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="1" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
            "</cellXfs></styleSheet>"
}
