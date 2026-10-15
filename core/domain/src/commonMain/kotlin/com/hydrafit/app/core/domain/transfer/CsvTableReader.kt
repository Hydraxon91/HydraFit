package com.hydrafit.app.core.domain.transfer

/** One CSV record plus the source position used to report row diagnostics. */
data class CsvRecord(val sourceRowIndex: Int, val values: List<String>)

/** The records of a scanned CSV file in source order, with truly blank lines removed. */
data class CsvTable(val records: List<CsvRecord>)

/**
 * A minimal, strict RFC-4180-style scanner over one delimiter.
 *
 * Handles a leading UTF-8 BOM, LF/CRLF (and lone CR) record separators, and quoted fields that may
 * contain the delimiter, newlines and doubled quotes. Rejects the malformed cases the importer must
 * not guess through: an unclosed quote, a quote inside an unquoted field, text after a closing quote
 * and a record wider than [WorkoutCsvLimits.MAX_COLUMNS]. Truly blank lines are skipped; a line whose
 * only field is an empty unquoted value counts as blank, while an empty value between delimiters or
 * inside quotes is a field.
 */
object CsvTableReader {

    fun read(text: String, delimiter: Char): CsvTable {
        val records = ArrayList<CsvRecord>()
        val fields = ArrayList<String>()
        val field = StringBuilder()

        var inQuotes = false
        var quoteClosed = false
        var sawQuote = false
        var sawDelimiter = false
        var recordCount = 0
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        val length = text.length

        fun append(char: Char) {
            field.append(char)
            if (field.length > WorkoutCsvLimits.MAX_FIELD_CHARS) {
                throw WorkoutCsvException(WorkoutCsvFailure.FIELD_TOO_LONG)
            }
        }

        fun endField() {
            fields.add(field.toString())
            field.clear()
            quoteClosed = false
            if (fields.size >= WorkoutCsvLimits.MAX_COLUMNS) {
                throw WorkoutCsvException(WorkoutCsvFailure.TOO_MANY_COLUMNS)
            }
        }

        fun endRecord() {
            if (fields.size >= WorkoutCsvLimits.MAX_COLUMNS) {
                throw WorkoutCsvException(WorkoutCsvFailure.TOO_MANY_COLUMNS)
            }
            fields.add(field.toString())
            field.clear()
            quoteClosed = false
            val blank = !sawQuote && !sawDelimiter && fields.size == 1 && fields[0].isEmpty()
            if (blank) {
                fields.clear()
            } else {
                recordCount++
                if (recordCount - 1 > WorkoutCsvLimits.MAX_DATA_ROWS) {
                    throw WorkoutCsvException(WorkoutCsvFailure.TOO_MANY_ROWS)
                }
                records.add(CsvRecord(recordCount, fields.toList()))
                fields.clear()
            }
            sawQuote = false
            sawDelimiter = false
        }

        while (i < length) {
            val char = text[i]
            if (inQuotes) {
                when {
                    char == '"' && i + 1 < length && text[i + 1] == '"' -> {
                        append('"')
                        i += 2
                    }

                    char == '"' -> {
                        inQuotes = false
                        quoteClosed = true
                        i++
                    }

                    else -> {
                        append(char)
                        i++
                    }
                }
            } else if (quoteClosed) {
                when (char) {
                    delimiter -> {
                        endField()
                        sawDelimiter = true
                        i++
                    }

                    '\r' -> {
                        i++
                        endRecord()
                        if (i < length && text[i] == '\n') i++
                    }

                    '\n' -> {
                        i++
                        endRecord()
                    }

                    else -> throw WorkoutCsvException(
                        WorkoutCsvFailure.MALFORMED_CSV,
                        "text after a closing quote"
                    )
                }
            } else {
                when {
                    char == '"' -> {
                        if (field.isEmpty()) {
                            inQuotes = true
                            sawQuote = true
                            i++
                        } else {
                            throw WorkoutCsvException(
                                WorkoutCsvFailure.MALFORMED_CSV,
                                "quote inside an unquoted field"
                            )
                        }
                    }

                    char == delimiter -> {
                        endField()
                        sawDelimiter = true
                        i++
                    }

                    char == '\r' -> {
                        i++
                        endRecord()
                        if (i < length && text[i] == '\n') i++
                    }

                    char == '\n' -> {
                        i++
                        endRecord()
                    }

                    else -> {
                        append(char)
                        i++
                    }
                }
            }
        }

        if (inQuotes) {
            throw WorkoutCsvException(WorkoutCsvFailure.MALFORMED_CSV, "unclosed quoted field")
        }
        if (field.isNotEmpty() || fields.isNotEmpty() || sawQuote || sawDelimiter) {
            endRecord()
        }
        return CsvTable(records)
    }
}
