package com.invision.pos.domain.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {

    private fun parsed(text: String): CsvTable = (CsvParser.parse(text) as CsvParseResult.Parsed).table

    @Test
    fun readsAHeaderAndRows() {
        val table = parsed("name,price\nShirt,499\nBiscuits,20\n")
        assertEquals(listOf("name", "price"), table.header)
        assertEquals(listOf(listOf("Shirt", "499"), listOf("Biscuits", "20")), table.rows.map { it.cells })
        assertEquals(listOf(2, 3), table.rows.map { it.number })
    }

    @Test
    fun quotedCells_mayHoldCommasQuotesAndLineBreaks() {
        val table = parsed("name,note\n\"Shirt, blue\",\"He said \"\"hi\"\"\"\n\"Two\nlines\",x\n")
        assertEquals("Shirt, blue", table.rows[0].cells[0])
        assertEquals("He said \"hi\"", table.rows[0].cells[1])
        assertEquals("Two\nlines", table.rows[1].cells[0])
    }

    @Test
    fun windowsLineEnds_andAByteOrderMark_areHandled() {
        val table = parsed("﻿name,price\r\nShirt,499\r\nSocks,99")
        assertEquals(listOf("name", "price"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals("99", table.rows[1].cells[1])
    }

    @Test
    fun theSeparatorIsFoundFromTheFirstLine() {
        assertEquals(';', parsed("name;price\nShirt;499").delimiter)
        assertEquals('\t', parsed("name\tprice\nShirt\t499").delimiter)
        assertEquals(',', parsed("name,price\nShirt,499").delimiter)
        assertEquals(listOf("Shirt", "499"), parsed("name;price\nShirt;499").rows[0].cells)
    }

    @Test
    fun aCommaInsideQuotes_doesNotChangeTheSeparator() {
        val table = parsed("\"name, full\";price\nShirt;499")
        assertEquals(';', table.delimiter)
        assertEquals("name, full", table.header[0])
    }

    @Test
    fun blankLinesAreSkipped_butRowNumbersStillMatchTheFile() {
        val table = parsed("name,price\n\nShirt,499\n\n\nSocks,99\n")
        assertEquals(listOf(3, 6), table.rows.map { it.number })
    }

    @Test
    fun shortRowsAreKept_theMissingCellsAreReadAsBlank() {
        val table = parsed("name,price,sku\nShirt,499\n")
        assertEquals(listOf("Shirt", "499"), table.rows[0].cells)
    }

    @Test
    fun aQuoteInTheMiddleOfACell_isPlainText() {
        val table = parsed("name,price\n5\" nail,10\n")
        assertEquals("5\" nail", table.rows[0].cells[0])
    }

    @Test
    fun aHeaderWithNoRows_isEmpty() {
        assertEquals(CsvParseResult.Empty, CsvParser.parse("name,price\n"))
        assertEquals(CsvParseResult.Empty, CsvParser.parse(""))
    }

    @Test
    fun anUnclosedQuote_isMalformed() {
        assertEquals(CsvParseResult.Malformed, CsvParser.parse("name,price\n\"Shirt,499\nSocks,99\n"))
    }

    @Test
    fun tooManyRows_areRefused() {
        val rows = (1..CsvParser.MAX_ROWS + 1).joinToString("\n") { "item$it,10" }
        assertEquals(CsvParseResult.TooManyRows(CsvParser.MAX_ROWS), CsvParser.parse("name,price\n$rows"))
        val ok = (1..CsvParser.MAX_ROWS).joinToString("\n") { "item$it,10" }
        assertTrue(CsvParser.parse("name,price\n$ok") is CsvParseResult.Parsed)
    }

    @Test
    fun theTemplate_isReadBackWithEveryColumn() {
        val table = parsed(ProductImportTemplate.csv())
        assertEquals(ImportColumn.entries.map { it.key }, table.header)
        assertEquals(5, table.rows.size)
        assertTrue(table.rows.all { it.cells.size == ImportColumn.entries.size })
    }

    @Test
    fun csvCell_quotesWhatNeedsQuoting() {
        assertEquals("plain", ProductImportTemplate.csvCell("plain"))
        assertEquals("\"a,b\"", ProductImportTemplate.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", ProductImportTemplate.csvCell("say \"hi\""))
    }
}
