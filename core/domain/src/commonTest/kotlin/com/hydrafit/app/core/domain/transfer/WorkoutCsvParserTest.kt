package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.unit.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutCsvParserTest {

    // --- FitNotes ---

    @Test
    fun fitNotesParsesAWeightedRepRowInPounds() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            FITNOTES_LB_HEADER,
            "2024-01-02,Bench Press,Chest,135,5,,,"
        )

        assertEquals(WorkoutCsvSource.FITNOTES, parsed.source)
        assertEquals(8, parsed.columns.size)
        assertTrue(parsed.rejectedRows.isEmpty())
        val row = parsed.rows.single()
        assertEquals(2, row.sourceRowIndex)
        assertEquals("Bench Press", row.exerciseName)
        assertEquals(SourceCivilDate(2024, 1, 2), row.day)
        assertEquals(135.0, row.weight)
        assertEquals(WeightUnit.LB, row.unit)
        assertEquals(5, row.reps)
        assertEquals("Chest", row.groupHint)
        assertNull(row.timeOfDaySeconds)
        assertNull(row.endDay)
        assertNull(row.warmup)
        assertTrue(row.diagnostics.isEmpty())
    }

    @Test
    fun fitNotesAcceptsTheKilogramHeaderVariant() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (kg),Reps",
            "2024-05-31,Lunge,20,8"
        )

        val row = parsed.rows.single()
        assertEquals(20.0, row.weight)
        assertEquals(WeightUnit.KG, row.unit)
        assertNull(row.groupHint)
    }

    @Test
    fun fitNotesPreservesUnsupportedMetadataAsDiagnosticsOnAValidRow() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps,Distance,Distance Unit,Time",
            "2024-01-02,Rowing,100,10,500,m,00:03:20"
        )

        val row = parsed.rows.single()
        assertEquals(
            listOf("Distance", "Distance Unit", "Time"),
            row.diagnostics.map { it.field }
        )
        assertTrue(row.diagnostics.all { it.code == WorkoutCsvDiagnosticCode.UNSUPPORTED_METADATA })
        assertEquals(listOf("500", "m", "00:03:20"), row.diagnostics.map { it.value })
    }

    @Test
    fun fitNotesKeepsABlankWeightNullAndAZeroWeightAsZero() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps",
            "2024-01-02,Pull Up,,8",
            "2024-01-03,Squat,0,5"
        )

        assertNull(parsed.rows[0].weight)
        assertEquals(0.0, parsed.rows[1].weight)
    }

    @Test
    fun fitNotesRejectsANegativeWeight() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps",
            "2024-01-02,Squat,-5,5"
        )

        assertTrue(parsed.rows.isEmpty())
        val rejected = parsed.rejectedRows.single()
        assertEquals(2, rejected.sourceRowIndex)
        assertEquals(WorkoutCsvDiagnosticCode.INVALID_WEIGHT, rejected.diagnostics.single().code)
    }

    @Test
    fun fitNotesAcceptsALeapDayAndRejectsAnImpossibleOne() {
        val valid = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps",
            "2024-02-29,Deadlift,225,3"
        )
        val invalid = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps",
            "2023-02-29,Deadlift,225,3"
        )

        assertEquals(SourceCivilDate(2024, 2, 29), valid.rows.single().day)
        assertTrue(invalid.rows.isEmpty())
        assertEquals(WorkoutCsvDiagnosticCode.INVALID_DATE, invalid.rejectedCode())
    }

    @Test
    fun fitNotesRejectsAZeroRepRow() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps",
            "2024-01-02,Squat,100,0"
        )

        assertEquals(WorkoutCsvDiagnosticCode.INVALID_REPS, parsed.rejectedCode())
    }

    @Test
    fun fitNotesRejectsADistanceOnlyRowAsAnUnsupportedShape() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps,Distance,Distance Unit,Time",
            "2024-01-02,Run,,,5000,m,00:30:00"
        )

        assertEquals(
            WorkoutCsvDiagnosticCode.UNSUPPORTED_ROW_SHAPE,
            parsed.rejectedRows.single().diagnostics.first().code
        )
    }

    @Test
    fun fitNotesRejectsARowWithNoExerciseName() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps",
            "2024-01-02,,100,5"
        )

        assertEquals(
            WorkoutCsvDiagnosticCode.MISSING_EXERCISE_NAME,
            parsed.rejectedRows.single().diagnostics.single().code
        )
    }

    @Test
    fun fitNotesRejectsAnUnknownHeader() {
        assertEquals(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            parseFailure(
                WorkoutCsvSource.FITNOTES,
                "WorkoutDate,Exercise,Weight (lbs),Reps",
                "2024-01-02,Squat,100,5"
            )
        )
    }

    @Test
    fun fitNotesRejectsBothWeightUnitColumnsAtOnce() {
        assertEquals(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            parseFailure(
                WorkoutCsvSource.FITNOTES,
                "Date,Exercise,Weight (lbs),Weight (kg),Reps",
                "2024-01-02,Squat,100,45,5"
            )
        )
    }

    @Test
    fun rejectsDuplicateHeaderNames() {
        assertEquals(
            WorkoutCsvFailure.DUPLICATE_HEADER,
            parseFailure(
                WorkoutCsvSource.FITNOTES,
                "Date,Exercise,Weight (lbs),Date,Reps",
                "2024-01-02,Squat,100,2024-01-03,5"
            )
        )
    }

    @Test
    fun preservesRowOrderAndDuplicateRows() {
        val parsed = parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps",
            "2024-01-02,Squat,100,5",
            "2024-01-02,Squat,100,5",
            "2024-01-03,Squat,105,5"
        )

        assertEquals(listOf(2, 3, 4), parsed.rows.map { it.sourceRowIndex })
        val first = parsed.rows[0]
        val second = parsed.rows[1]
        assertEquals(first.copy(sourceRowIndex = second.sourceRowIndex), second)
    }

    // --- Strong ---

    @Test
    fun strongParsesADateTimeRowAndPreservesUnsupportedMetadata() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push Day", "Bench Press", "1", "80", "kg", "5", "8",
                "", "", "300", "", "", "3600"
            )
        )

        val row = parsed.rows.single()
        assertEquals(SourceCivilDate(2024, 7, 2), row.day)
        assertEquals(36187, row.timeOfDaySeconds)
        assertEquals("Push Day", row.workoutTitle)
        assertEquals(80.0, row.weight)
        assertEquals(WeightUnit.KG, row.unit)
        assertEquals(5, row.reps)
        assertNull(row.warmup)
        assertEquals(listOf("RPE", "Seconds", "Workout Duration"), row.diagnostics.map { it.field })
        assertEquals(listOf("8", "300", "3600"), row.diagnostics.map { it.value })
    }

    @Test
    fun strongAcceptsLbAndLbsUnitsCaseInsensitively() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push", "Bench", "", "100", "lbs", "5",
                "", "", "", "", "", "", ""
            ),
            semiRow(
                "2024-07-03 10:03:07", "Push", "Bench", "", "100", "LB", "5",
                "", "", "", "", "", "", ""
            )
        )

        assertEquals(listOf(WeightUnit.LB, WeightUnit.LB), parsed.rows.map { it.unit })
    }

    @Test
    fun strongRejectsAnUnknownWeightUnit() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push", "Bench", "", "80", "stone", "5",
                "", "", "", "", "", "", ""
            )
        )

        assertEquals(WorkoutCsvDiagnosticCode.INVALID_UNIT, parsed.rejectedCode())
    }

    @Test
    fun strongRejectsAnOutOfRangeTime() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 25:03:07", "Push", "Bench", "", "80", "kg", "5",
                "", "", "", "", "", "", ""
            )
        )

        assertEquals(WorkoutCsvDiagnosticCode.INVALID_TIME, parsed.rejectedCode())
    }

    @Test
    fun strongRejectsTheUnitlessNativeExportInsteadOfGuessingItsUnit() {
        // The current native Strong export is comma-separated and omits the weight unit entirely:
        // Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,
        // Workout Notes,RPE (a real public sample: github.com/AlexandrosKyriakakis/StrongAppAnalytics).
        // That shape is refused, and so is the same column set with the supported delimiter: without a
        // Weight Unit column the load's unit is unknowable, so importing could be ~2.2x wrong.
        assertEquals(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            parseFailure(
                WorkoutCsvSource.STRONG,
                "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance," +
                    "Seconds,Notes,Workout Notes,RPE",
                "2020-12-30 18:51:52,Evening Workout,2h 38m,Snatch (Barbell),1,40.0,3,0,0,,,"
            )
        )
        assertEquals(
            WorkoutCsvFailure.UNSUPPORTED_HEADER,
            parseFailure(
                WorkoutCsvSource.STRONG,
                "Date;Workout Name;Exercise Name;Set Order;Weight;Reps",
                semiRow("2024-07-02 10:03:07", "Push", "Bench", "1", "80", "5")
            )
        )
    }

    // --- Hevy ---

    @Test
    fun hevyParsesAStartAndEndTimeWithAPoundWeight() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Bench Press", "", "",
                "0", "normal", "135", "5", "", "", ""
            )
        )

        val row = parsed.rows.single()
        assertEquals(SourceCivilDate(2024, 7, 2), row.day)
        assertEquals(36180, row.timeOfDaySeconds)
        assertEquals(SourceCivilDate(2024, 7, 2), row.endDay)
        assertEquals(39600, row.endTimeOfDaySeconds)
        assertEquals("Push", row.workoutTitle)
        assertEquals(135.0, row.weight)
        assertEquals(WeightUnit.LB, row.unit)
        assertEquals(5, row.reps)
        assertEquals(false, row.warmup)
        assertTrue(row.diagnostics.isEmpty())
    }

    @Test
    fun hevyParsesTheKilogramVariantAndFlagsUnsupportedMetadata() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_KG_HEADER,
            commaRow(
                "Row", "15 Sep 2023, 09:30", "15 Sep 2023, 10:15", "", "Dumbbell Row", "", "",
                "1", "warmup", "20", "10", "1.5", "300", "7"
            )
        )

        val row = parsed.rows.single()
        assertEquals(WeightUnit.KG, row.unit)
        assertEquals(20.0, row.weight)
        assertEquals(true, row.warmup)
        assertEquals(
            listOf("distance_km", "duration_seconds", "rpe"),
            row.diagnostics.map { it.field }
        )
    }

    @Test
    fun hevyRejectsAnUnsupportedSetTypeInsteadOfReinterpretingIt() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Bench Press", "", "",
                "0", "drop", "135", "5", "", "", ""
            )
        )

        assertTrue(parsed.rows.isEmpty())
        assertEquals(WorkoutCsvDiagnosticCode.UNSUPPORTED_SET_TYPE, parsed.rejectedCode())
    }

    @Test
    fun hevyKeepsWarmupUnknownWhenTheSetTypeIsBlank() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Bench Press", "", "",
                "0", "", "135", "5", "", "", ""
            )
        )

        assertNull(parsed.rows.single().warmup)
    }

    @Test
    fun hevyRejectsAnEndTimeThatPrecedesTheStart() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "02 Jul 2024, 11:00", "02 Jul 2024, 10:00", "", "Bench Press", "", "",
                "0", "normal", "135", "5", "", "", ""
            )
        )

        assertEquals(WorkoutCsvDiagnosticCode.INVALID_END_TIME, parsed.rejectedCode())
    }

    @Test
    fun hevyRejectsAMalformedStartTime() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "not a date", "", "", "Bench Press", "", "",
                "0", "normal", "135", "5", "", "", ""
            )
        )

        assertEquals(WorkoutCsvDiagnosticCode.INVALID_DATE, parsed.rejectedCode())
    }

    // --- Whole-file failures ---

    @Test
    fun rejectsAnEmptyFile() {
        assertEquals(WorkoutCsvFailure.EMPTY_INPUT, parseFailure(WorkoutCsvSource.FITNOTES, ""))
    }

    @Test
    fun rejectsAHeaderOnlyFile() {
        assertEquals(
            WorkoutCsvFailure.EMPTY_INPUT,
            parseFailure(WorkoutCsvSource.FITNOTES, "Date,Exercise,Weight (lbs),Reps")
        )
    }

    @Test
    fun rejectsARowWhoseWidthDoesNotMatchTheHeader() {
        assertEquals(
            WorkoutCsvFailure.MALFORMED_CSV,
            parseFailure(
                WorkoutCsvSource.FITNOTES,
                "Date,Exercise,Weight (lbs),Reps",
                "2024-01-02,Bench Press,100"
            )
        )
    }

    @Test
    fun rejectsAFileLargerThanTheByteLimit() {
        val tooLarge = "a".repeat((WorkoutCsvLimits.MAX_UTF8_BYTES + 1).toInt())

        assertEquals(WorkoutCsvFailure.TOO_LARGE, parseFailure(WorkoutCsvSource.FITNOTES, tooLarge))
    }

    @Test
    fun handlesABomAndCrlfEndings() {
        val parsed = WorkoutCsvParser.parse(
            WorkoutCsvSource.FITNOTES,
            "\uFEFFDate,Exercise,Weight (lbs),Reps\r\n2024-01-02,Squat,100,5\r\n"
        )

        assertEquals(SourceCivilDate(2024, 1, 2), parsed.rows.single().day)
    }

    // --- Helpers ---

    private fun parse(source: WorkoutCsvSource, vararg lines: String): ParsedWorkoutCsv =
        WorkoutCsvParser.parse(source, lines.joinToString("\n", postfix = "\n"))

    private fun parseFailure(source: WorkoutCsvSource, vararg lines: String): WorkoutCsvFailure =
        assertFailsWith<WorkoutCsvException> {
            WorkoutCsvParser.parse(source, lines.joinToString("\n", postfix = "\n"))
        }.failure

    private fun ParsedWorkoutCsv.rejectedCode(): WorkoutCsvDiagnosticCode =
        rejectedRows.single().diagnostics.single().code

    private fun commaRow(vararg values: String): String = values.joinToString(",") { quoted(it) }

    private fun semiRow(vararg values: String): String = values.joinToString(";")

    private fun quoted(value: String): String = if (value.contains(',') || value.contains('"')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

    private companion object {
        const val FITNOTES_LB_HEADER =
            "Date,Exercise,Category,Weight (lbs),Reps,Distance,Distance Unit,Time"

        const val STRONG_HEADER =
            "Date;Workout Name;Exercise Name;Set Order;Weight;Weight Unit;Reps;RPE;Distance;" +
                "Distance Unit;Seconds;Notes;Workout Notes;Workout Duration"

        const val HEVY_LB_HEADER =
            "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes," +
                "set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe"

        const val HEVY_KG_HEADER =
            "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes," +
                "set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe"
    }
}
