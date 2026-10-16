package com.hydrafit.app.core.domain.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvTableReaderTest {

    @Test
    fun readsASimpleTable() {
        val table = CsvTableReader.read("a,b,c\n1,2,3\n", ',')

        assertEquals(
            listOf(
                CsvRecord(1, listOf("a", "b", "c")),
                CsvRecord(2, listOf("1", "2", "3"))
            ),
            table.records
        )
    }

    @Test
    fun readsCrlfAndLoneCrSeparators() {
        val crlf = CsvTableReader.read("a,b\r\n1,2\r\n", ',')
        val loneCr = CsvTableReader.read("a,b\r1,2", ',')

        val expected = listOf(CsvRecord(1, listOf("a", "b")), CsvRecord(2, listOf("1", "2")))
        assertEquals(expected, crlf.records)
        assertEquals(expected, loneCr.records)
    }

    @Test
    fun stripsALeadingByteOrderMark() {
        val table = CsvTableReader.read("\uFEFFa,b\n1,2\n", ',')

        assertEquals("a", table.records.first().values.first())
    }

    @Test
    fun keepsQuotedDelimitersNewlinesAndEscapedQuotes() {
        val text = "name,note\n\"a,b\",\"line1\nline2\"\n\"he said \"\"hi\"\"\",x\n"

        val table = CsvTableReader.read(text, ',')

        assertEquals(
            listOf(
                CsvRecord(1, listOf("name", "note")),
                CsvRecord(2, listOf("a,b", "line1\nline2")),
                CsvRecord(3, listOf("he said \"hi\"", "x"))
            ),
            table.records
        )
    }

    @Test
    fun skipsBlankLinesButKeepsEmptyFields() {
        val table = CsvTableReader.read("a,b\n\n1,\n\n,2\n", ',')

        assertEquals(
            listOf(
                CsvRecord(1, listOf("a", "b")),
                CsvRecord(2, listOf("1", "")),
                CsvRecord(3, listOf("", "2"))
            ),
            table.records
        )
    }

    @Test
    fun treatsAQuotedEmptyFieldAsDataRatherThanABlankLine() {
        val table = CsvTableReader.read("a\n\"\"\n", ',')

        assertEquals(listOf(CsvRecord(1, listOf("a")), CsvRecord(2, listOf(""))), table.records)
    }

    @Test
    fun keepsATrailingDelimiterAsAnEmptyField() {
        val table = CsvTableReader.read("a,b\n1,\n", ',')

        assertEquals(listOf("1", ""), table.records[1].values)
    }

    @Test
    fun rejectsAnUnclosedQuote() {
        assertEquals(
            WorkoutCsvFailure.MALFORMED_CSV,
            assertFailsWith<WorkoutCsvException> { CsvTableReader.read("a\n\"x\n", ',') }.failure
        )
    }

    @Test
    fun rejectsAQuoteInsideAnUnquotedField() {
        assertEquals(
            WorkoutCsvFailure.MALFORMED_CSV,
            assertFailsWith<WorkoutCsvException> { CsvTableReader.read("a\nx\"y\n", ',') }.failure
        )
    }

    @Test
    fun rejectsTextAfterAClosingQuote() {
        assertEquals(
            WorkoutCsvFailure.MALFORMED_CSV,
            assertFailsWith<WorkoutCsvException> { CsvTableReader.read("a\n\"x\"y\n", ',') }.failure
        )
    }

    @Test
    fun rejectsTooManyColumns() {
        val text = (1..WorkoutCsvLimits.MAX_COLUMNS + 1).joinToString(",")

        assertEquals(
            WorkoutCsvFailure.TOO_MANY_COLUMNS,
            assertFailsWith<WorkoutCsvException> { CsvTableReader.read(text, ',') }.failure
        )
    }

    @Test
    fun rejectsAFieldLongerThanTheLimit() {
        val text = "a\n" + "x".repeat(WorkoutCsvLimits.MAX_FIELD_CHARS + 1)

        assertEquals(
            WorkoutCsvFailure.FIELD_TOO_LONG,
            assertFailsWith<WorkoutCsvException> { CsvTableReader.read(text, ',') }.failure
        )
    }

    @Test
    fun rejectsMoreDataRowsThanTheLimit() {
        val text = buildString {
            append("header\n")
            repeat(WorkoutCsvLimits.MAX_DATA_ROWS + 1) { append("v\n") }
        }

        assertEquals(
            WorkoutCsvFailure.TOO_MANY_ROWS,
            assertFailsWith<WorkoutCsvException> { CsvTableReader.read(text, ',') }.failure
        )
    }

    @Test
    fun readsTheEmptyInputAsNoRecords() {
        assertTrue(CsvTableReader.read("", ',').records.isEmpty())
        assertTrue(CsvTableReader.read("\n\n", ',').records.isEmpty())
    }
}
