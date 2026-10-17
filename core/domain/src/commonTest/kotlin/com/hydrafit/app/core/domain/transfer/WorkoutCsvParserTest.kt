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
        assertNull(row.durationSeconds)
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
                "", "", "", "", "", "3600"
            )
        )

        val row = parsed.rows.single()
        assertEquals(SourceCivilDate(2024, 7, 2), row.day)
        assertEquals(36187, row.timeOfDaySeconds)
        assertEquals("Push Day", row.workoutTitle)
        assertEquals(80.0, row.weight)
        assertEquals(WeightUnit.KG, row.unit)
        assertEquals(5, row.reps)
        assertNull(row.durationSeconds)
        assertNull(row.warmup)
        assertEquals(listOf("RPE", "Workout Duration"), row.diagnostics.map { it.field })
        assertEquals(listOf("8", "3600"), row.diagnostics.map { it.value })
    }

    @Test
    fun strongParsesATimedSetFromTheSecondsColumn() {
        // Vendors write 0 in the inapplicable numeric column, so a positive Seconds with a zero or
        // blank Reps is a duration set: not a rep set, and not an error.
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push", "Plank Hold", "1", "0", "kg", "0", "", "", "", "90",
                "", "", ""
            ),
            semiRow(
                "2024-07-02 10:05:07", "Push", "Plank Hold", "2", "0", "kg", "", "", "", "", "45",
                "", "", ""
            )
        )

        assertEquals(listOf(null, null), parsed.rows.map { it.reps })
        assertEquals(listOf(90, 45), parsed.rows.map { it.durationSeconds })
        assertTrue(parsed.rows.all { it.diagnostics.isEmpty() })
    }

    @Test
    fun strongKeepsAZeroSecondRepRowCounted() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push", "Bench", "1", "80", "kg", "5", "", "", "", "0",
                "", "", ""
            )
        )

        val row = parsed.rows.single()
        assertEquals(5, row.reps)
        assertNull(row.durationSeconds)
        // The counted row keeps the inapplicable Seconds value as unsupported metadata, because the
        // canonical import identity includes diagnostics: a rep row imported before duration support
        // existed must still match the bytes the legacy parser wrote for it.
        assertEquals(
            listOf(WorkoutCsvDiagnosticCode.UNSUPPORTED_METADATA),
            row.diagnostics.map { it.code }
        )
        assertEquals(listOf("Seconds"), row.diagnostics.map { it.field })
        assertEquals(listOf("0"), row.diagnostics.map { it.value })
    }

    @Test
    fun strongRejectsARowRecordingBothRepsAndSeconds() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push", "Bench", "1", "80", "kg", "5", "", "", "", "90",
                "", "", ""
            )
        )

        assertTrue(parsed.rows.isEmpty())
        assertEquals(WorkoutCsvDiagnosticCode.AMBIGUOUS_ROW_SHAPE, parsed.rejectedCode())
    }

    @Test
    fun strongRejectsARowWithNeitherRepsNorDuration() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push", "Bench", "1", "80", "kg", "", "", "", "", "",
                "", "", ""
            ),
            semiRow(
                "2024-07-02 10:04:07", "Push", "Bench", "2", "80", "kg", "0", "", "", "", "0",
                "", "", ""
            )
        )

        assertTrue(parsed.rows.isEmpty())
        assertEquals(listOf(2, 3), parsed.rejectedRows.map { it.sourceRowIndex })
        assertEquals(
            listOf(
                WorkoutCsvDiagnosticCode.INVALID_REPS,
                WorkoutCsvDiagnosticCode.INVALID_REPS
            ),
            parsed.rejectedRows.map { it.diagnostics.single().code }
        )
    }

    @Test
    fun strongRejectsAnUnparseableDurationInsteadOfDroppingIt() {
        val parsed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            semiRow(
                "2024-07-02 10:03:07", "Push", "Bench", "1", "80", "kg", "5", "", "", "", "abc",
                "", "", ""
            )
        )

        assertTrue(parsed.rows.isEmpty())
        assertEquals(WorkoutCsvDiagnosticCode.INVALID_DURATION, parsed.rejectedCode())
    }

    @Test
    fun strongAcceptsTenDigitDurationsUpToIntMaxAndRefusesOverflow() {
        val accepted = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            strongRow("1", duration = "999999999"),
            strongRow("2", duration = "1000000000"),
            strongRow("3", duration = "2147483647")
        )

        assertEquals(
            listOf(999999999, 1000000000, 2147483647),
            accepted.rows.map { it.durationSeconds }
        )
        // A timed row consumes Seconds as its measurement, so it carries no Seconds metadata note.
        assertTrue(accepted.rows.all { it.reps == null && it.diagnostics.isEmpty() })

        val overflow = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            strongRow("1", duration = "2147483648"),
            strongRow("2", duration = "9999999999")
        )

        assertTrue(overflow.rows.isEmpty())
        assertEquals(
            listOf(
                WorkoutCsvDiagnosticCode.INVALID_DURATION,
                WorkoutCsvDiagnosticCode.INVALID_DURATION
            ),
            overflow.rejectedRows.map { it.diagnostics.single().code }
        )
    }

    @Test
    fun strongRefusesAZeroOrMalformedDuration() {
        // Zero in the inapplicable Seconds column means "no duration recorded", so a row with no rep
        // count either way has no measurement at all and is refused rather than read as a timed set.
        val zero = parse(WorkoutCsvSource.STRONG, STRONG_HEADER, strongRow("1", duration = "0"))
        assertTrue(zero.rows.isEmpty())
        assertEquals(
            WorkoutCsvDiagnosticCode.UNSUPPORTED_ROW_SHAPE,
            zero.rejectedRows.single().diagnostics.single().code
        )

        val malformed = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            strongRow("1", duration = "abc"),
            strongRow("2", duration = "-5"),
            strongRow("3", duration = "1.5")
        )
        assertTrue(malformed.rows.isEmpty())
        assertEquals(
            listOf(
                WorkoutCsvDiagnosticCode.INVALID_DURATION,
                WorkoutCsvDiagnosticCode.INVALID_DURATION,
                WorkoutCsvDiagnosticCode.INVALID_DURATION
            ),
            malformed.rejectedRows.map { it.diagnostics.single().code }
        )
    }

    @Test
    fun strongRejectsMalformedRepsOnATimedRowBeforeChoosingTheDuration() {
        // A blank or zero Reps value is the vendor's way of saying "not a rep set", so a positive
        // Seconds column still yields a timed set.
        val accepted = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            strongRow("1", reps = "", duration = "90"),
            strongRow("2", reps = "0", duration = "90")
        )
        assertEquals(listOf(null, null), accepted.rows.map { it.reps })
        assertEquals(listOf(90, 90), accepted.rows.map { it.durationSeconds })
        assertTrue(accepted.rows.all { it.diagnostics.isEmpty() })

        // A non-blank Reps value that is not a whole non-negative number is malformed: it rejects the
        // row instead of being silently treated as an absent rep count.
        val malformedReps = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            strongRow("1", reps = "-1", duration = "90"),
            strongRow("2", reps = "abc", duration = "90"),
            strongRow("3", reps = "1.5", duration = "90"),
            strongRow("4", reps = "1000000000", duration = "90")
        )
        assertTrue(malformedReps.rows.isEmpty())
        assertEquals(
            listOf(
                WorkoutCsvDiagnosticCode.INVALID_REPS,
                WorkoutCsvDiagnosticCode.INVALID_REPS,
                WorkoutCsvDiagnosticCode.INVALID_REPS,
                WorkoutCsvDiagnosticCode.INVALID_REPS
            ),
            malformedReps.rejectedRows.map { it.diagnostics.single().code }
        )

        // Widening the accepted duration bound does not let a row recording both metrics through.
        val ambiguous = parse(
            WorkoutCsvSource.STRONG,
            STRONG_HEADER,
            strongRow("1", reps = "5", duration = "2147483647")
        )
        assertTrue(ambiguous.rows.isEmpty())
        assertEquals(WorkoutCsvDiagnosticCode.AMBIGUOUS_ROW_SHAPE, ambiguous.rejectedCode())
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
                "1", "warmup", "20", "10", "1.5", "", "7"
            )
        )

        val row = parsed.rows.single()
        assertEquals(WeightUnit.KG, row.unit)
        assertEquals(20.0, row.weight)
        assertEquals(true, row.warmup)
        assertNull(row.durationSeconds)
        assertEquals(
            listOf("distance_km", "rpe"),
            row.diagnostics.map { it.field }
        )
    }

    @Test
    fun hevyParsesATimedSetFromTheDurationColumn() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Plank Hold", "", "",
                "0", "normal", "0", "", "", "300", ""
            )
        )

        val row = parsed.rows.single()
        assertNull(row.reps)
        assertEquals(300, row.durationSeconds)
        assertTrue(row.diagnostics.isEmpty())
    }

    @Test
    fun hevyKeepsAZeroDurationRepRowCountedWithItsLegacyMetadata() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            hevyRow(reps = "5", duration = "0")
        )

        val row = parsed.rows.single()
        assertEquals(5, row.reps)
        assertNull(row.durationSeconds)
        // Counted rows keep the inapplicable duration_seconds value as metadata, matching the bytes
        // the legacy parser wrote into the import ledger.
        assertEquals(
            listOf(WorkoutCsvDiagnosticCode.UNSUPPORTED_METADATA),
            row.diagnostics.map { it.code }
        )
        assertEquals(listOf("duration_seconds"), row.diagnostics.map { it.field })
        assertEquals(listOf("0"), row.diagnostics.map { it.value })
    }

    @Test
    fun hevyAcceptsBlankOrZeroRepsForATimedSetAndRejectsMalformedReps() {
        val accepted = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            hevyRow(reps = "", duration = "300"),
            hevyRow(reps = "0", duration = "300")
        )
        assertEquals(listOf(null, null), accepted.rows.map { it.reps })
        assertEquals(listOf(300, 300), accepted.rows.map { it.durationSeconds })
        assertTrue(accepted.rows.all { it.diagnostics.isEmpty() })

        val malformedReps = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            hevyRow(reps = "-1", duration = "300"),
            hevyRow(reps = "abc", duration = "300"),
            hevyRow(reps = "1.5", duration = "300"),
            hevyRow(reps = "1000000000", duration = "300")
        )
        assertTrue(malformedReps.rows.isEmpty())
        assertEquals(
            listOf(
                WorkoutCsvDiagnosticCode.INVALID_REPS,
                WorkoutCsvDiagnosticCode.INVALID_REPS,
                WorkoutCsvDiagnosticCode.INVALID_REPS,
                WorkoutCsvDiagnosticCode.INVALID_REPS
            ),
            malformedReps.rejectedRows.map { it.diagnostics.single().code }
        )
    }

    @Test
    fun hevyRejectsARowRecordingBothRepsAndDuration() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Bench Press", "", "",
                "0", "normal", "135", "5", "", "300", ""
            )
        )

        assertTrue(parsed.rows.isEmpty())
        assertEquals(WorkoutCsvDiagnosticCode.AMBIGUOUS_ROW_SHAPE, parsed.rejectedCode())
    }

    @Test
    fun hevyRejectsARowWithNeitherRepsNorDuration() {
        val parsed = parse(
            WorkoutCsvSource.HEVY,
            HEVY_LB_HEADER,
            commaRow(
                "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Bench Press", "", "",
                "0", "normal", "135", "", "", "", ""
            )
        )

        assertTrue(parsed.rows.isEmpty())
        assertEquals(WorkoutCsvDiagnosticCode.INVALID_REPS, parsed.rejectedCode())
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

    /** A Strong timed/counted probe row on the shared STRONG_HEADER shape. */
    private fun strongRow(setOrder: String, reps: String = "", duration: String = ""): String =
        semiRow(
            "2024-07-02 10:03:07", "Push", "Plank Hold", setOrder, "0", "kg", reps,
            "", "", "", duration, "", "", ""
        )

    /** A Hevy probe row on the shared HEVY_LB_HEADER shape. */
    private fun hevyRow(reps: String, duration: String): String = commaRow(
        "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Plank Hold", "", "",
        "0", "normal", "0", reps, "", duration, ""
    )

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
