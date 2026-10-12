package com.auxo.pos.domain.imports

/** One data row of a CSV file. [number] is its row in the file (the header is row 1), so messages match a spreadsheet. */
data class CsvRow(val number: Int, val cells: List<String>)

data class CsvTable(val header: List<String>, val rows: List<CsvRow>, val delimiter: Char)

sealed interface CsvParseResult {
    data class Parsed(val table: CsvTable) : CsvParseResult

    /** No header, or a header and no rows. */
    data object Empty : CsvParseResult

    data class TooManyRows(val max: Int) : CsvParseResult

    /** A quoted cell is never closed, so the rest of the file would be swallowed into it. */
    data object Malformed : CsvParseResult
}

/**
 * Reads CSV text the way spreadsheets write it: quoted cells that may hold commas, quotes ("") and line breaks,
 * CRLF or LF line ends, an optional byte-order mark, and a comma, semicolon or tab as the separator (found from the
 * first line, because Excel in many regions writes semicolons). Blank lines are skipped.
 */
object CsvParser {
    const val MAX_ROWS = 1000

    fun parse(raw: String): CsvParseResult {
        val text = raw.removePrefix("\uFEFF")
        val delimiter = detectDelimiter(text)
        val records = ArrayList<CsvRow>()
        var cells = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var recordNumber = 0

        fun endCell() {
            cells.add(cell.toString())
            cell.setLength(0)
        }

        fun endRecord() {
            endCell()
            recordNumber++
            if (!(cells.size == 1 && cells[0].isBlank())) records.add(CsvRow(recordNumber, cells))
            cells = ArrayList()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when {
                    // A quote only opens a quoted cell at the start of the cell; elsewhere it is plain text.
                    c == '"' && cell.isEmpty() -> inQuotes = true
                    c == delimiter -> endCell()
                    c == '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        endRecord()
                    }
                    c == '\n' -> endRecord()
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (inQuotes) return CsvParseResult.Malformed
        if (cell.isNotEmpty() || cells.isNotEmpty()) endRecord()

        if (records.size < 2) return CsvParseResult.Empty
        val data = records.drop(1)
        if (data.size > MAX_ROWS) return CsvParseResult.TooManyRows(MAX_ROWS)
        return CsvParseResult.Parsed(CsvTable(records[0].cells.map { it.trim() }, data, delimiter))
    }

    /** The separator that appears most often outside quotes on the first non-blank line; a comma when none does. */
    fun detectDelimiter(text: String): Char {
        val candidates = charArrayOf(',', ';', '\t')
        val counts = IntArray(candidates.size)
        var inQuotes = false
        var seenText = false
        for (c in text) {
            if (c == '"') inQuotes = !inQuotes
            if (inQuotes) continue
            if (c == '\n' || c == '\r') {
                if (seenText) break
                continue
            }
            if (!c.isWhitespace() || c == '\t') seenText = true
            val index = candidates.indexOf(c)
            if (index >= 0) counts[index]++
        }
        val best = counts.indices.maxByOrNull { counts[it] } ?: return ','
        return if (counts[best] == 0) ',' else candidates[best]
    }
}
