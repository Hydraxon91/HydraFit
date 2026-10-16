package com.hydrafit.app.core.domain.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The analysis export: explicit units/instants, blank-versus-zero, quoting and formula safety. */
class WorkoutHistoryCsvWriterTest {

    private fun row(
        setId: Long = 1L,
        performedAt: Long = 0L,
        reps: Int = 5,
        weightKg: Double? = 100.0,
        name: String = "Back Squat",
        sessionId: String? = "s1",
        isWarmup: Boolean = false,
        rir: Int? = null
    ) = WorkoutHistoryCsvRow(
        sessionId = sessionId,
        sessionStartedAtMillis = sessionId?.let { performedAt - 60_000L },
        localEpochDay = sessionId?.let { 0L },
        setId = setId,
        performedAtMillis = performedAt,
        exerciseId = "back-squat",
        exerciseName = name,
        reps = reps,
        weightKg = weightKg,
        loadKind = "EXTERNAL",
        isWarmup = isWarmup,
        rir = rir,
        timingProvenance = "LIVE"
    )

    @Test
    fun writesTheDocumentedHeaderAndOneRowPerSet() {
        val text = WorkoutHistoryCsvWriter.write(listOf(row()))
        val lines = text.trimEnd().split("\r\n")
        assertEquals(WorkoutHistoryCsvWriter.COLUMNS, lines.first().split(","))
        assertEquals(2, lines.size)
        // The documented header names the unit explicitly and carries no notes or credentials column.
        assertTrue("weightKg" in lines.first())
        listOf("note", "credential", "apiKey", "explanation").forEach {
            assertFalse(lines.first().contains(it))
        }
    }

    @Test
    fun keepsAnAbsentLoadDistinctFromARecordedZero() {
        val text = WorkoutHistoryCsvWriter.write(
            listOf(row(weightKg = null), row(setId = 2L, weightKg = 0.0))
        )
        val rows = text.trimEnd().split("\r\n").drop(1)
        val weightIndex = WorkoutHistoryCsvWriter.COLUMNS.indexOf("weightKg")
        assertEquals("", rows[0].split(",")[weightIndex])
        assertEquals("0.0", rows[1].split(",")[weightIndex])
    }

    @Test
    fun writesUtcInstantsAndOrdersByRecordedTimeThenId() {
        // 2024-01-02T03:04:05Z
        val instant = 1_704_164_645_000L
        val text = WorkoutHistoryCsvWriter.write(
            listOf(
                row(setId = 9L, performedAt = instant + 1_000L),
                row(setId = 2L, performedAt = instant)
            )
        )
        val rows = text.trimEnd().split("\r\n").drop(1)
        val performedIndex = WorkoutHistoryCsvWriter.COLUMNS.indexOf("performedAtUtc")
        assertEquals(
            listOf("2024-01-02T03:04:05Z", "2024-01-02T03:04:06Z"),
            rows.map {
                it.split(",")[performedIndex]
            }
        )
    }

    @Test
    fun neutralizesFormulaTriggersAndQuotesEmbeddedDelimiters() {
        val text = WorkoutHistoryCsvWriter.write(
            listOf(
                row(name = "=cmd|' /C calc'!A0"),
                row(setId = 2L, name = "Note, \"quoted\" name")
            )
        )
        val rows = text.trimEnd().split("\r\n").drop(1)
        val nameIndex = WorkoutHistoryCsvWriter.COLUMNS.indexOf("exerciseName")
        // The formula trigger never starts the cell: the value is prefixed with an apostrophe.
        assertEquals("'=cmd|' /C calc'!A0", rows[0].split(",")[nameIndex])
        // An embedded delimiter and quote are RFC 4180 quoted and doubled.
        assertTrue(rows[1].contains("\"Note, \"\"quoted\"\" name\""), rows[1])
    }

    @Test
    fun anUnassignedSetKeepsItsRowWithEmptySessionColumns() {
        val text = WorkoutHistoryCsvWriter.write(listOf(row(sessionId = null)))
        val rows = text.trimEnd().split("\r\n").drop(1)
        val sessionIndex = WorkoutHistoryCsvWriter.COLUMNS.indexOf("sessionId")
        val startIndex = WorkoutHistoryCsvWriter.COLUMNS.indexOf("sessionStartedAtUtc")
        val dayIndex = WorkoutHistoryCsvWriter.COLUMNS.indexOf("localEpochDay")
        val fields = rows.single().split(",")
        assertEquals("", fields[sessionIndex])
        assertEquals("", fields[startIndex])
        assertEquals("", fields[dayIndex])
    }
}
