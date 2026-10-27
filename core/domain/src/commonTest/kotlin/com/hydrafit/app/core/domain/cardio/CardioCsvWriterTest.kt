package com.hydrafit.app.core.domain.cardio

import kotlin.test.Test
import kotlin.test.assertEquals

class CardioCsvWriterTest {
    @Test
    fun formulaLikeNotesAreNeutralizedWithoutChangingStoredNotes() {
        listOf("=1+1", "+1", "-1", "@SUM(A1)", "  =1", "\tformula").forEach { note ->
            val activity = CardioActivity(
                identity = "a",
                type = CardioActivityType.OTHER,
                startedAtMillis = 1000L,
                durationSeconds = 60,
                note = note
            )
            assertEquals(
                "OTHER,1000,60,,'$note",
                CardioCsvWriter.write(listOf(activity)).lines()[1]
            )
            assertEquals(note, activity.note)
        }
    }

    @Test
    fun theExportKeepsCardioOutOfTheResistanceColumns() {
        val csv = CardioCsvWriter.write(
            listOf(
                CardioActivity(
                    identity = "a",
                    type = CardioActivityType.RUNNING,
                    startedAtMillis = 2_000L,
                    durationSeconds = 1800,
                    distanceMeters = 5000,
                    note = null
                ),
                CardioActivity(
                    identity = "b",
                    type = CardioActivityType.OTHER,
                    startedAtMillis = 1_000L,
                    durationSeconds = 600
                )
            )
        )

        val lines = csv.trim().lines()
        assertEquals(
            "activityType,startedAtMillis,durationSeconds,distanceMeters,note",
            lines.first()
        )
        // Oldest first, and an absent distance stays empty rather than becoming a zero.
        assertEquals("OTHER,1000,600,,", lines[1])
        assertEquals("RUNNING,2000,1800,5000,", lines[2])
    }

    @Test
    fun aNoteWithCommasOrQuotesIsEscaped() {
        val csv = CardioCsvWriter.write(
            listOf(
                CardioActivity(
                    identity = "a",
                    type = CardioActivityType.CYCLING,
                    startedAtMillis = 1_000L,
                    durationSeconds = 600,
                    note = "Easy, then \"hard\""
                )
            )
        )

        assertEquals("CYCLING,1000,600,,\"Easy, then \"\"hard\"\"\"", csv.trim().lines()[1])
    }
}
