package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewWorkoutImportTest {
    private val exercise = Exercise("mapped", "Press", emptySet(), setOf(MuscleGroup.CHEST_UPPER))
    private val parsed = WorkoutCsvParser.parse(
        WorkoutCsvSource.FITNOTES,
        "Date,Exercise,Weight (lbs),Reps\n2024-01-02,Press,100,8\n2024-01-02,Press,100,8"
    )
    private val choices = WorkoutImportChoices(
        exercises = mapOf(
            "Press" to WorkoutImportExerciseChoice("mapped", LoadKind.EXTERNAL, true, true, true)
        ),
        sessions = listOf(WorkoutImportSessionChoice(listOf(2, 3), 60, true, true, 36000)),
        acknowledgedUnsupportedFields = true,
        acknowledgedHistoryEffects = true
    )

    @Test
    fun preservesIdenticalSetsAndRequiresExplicitOffsetAndAttribution() {
        val preview = build()
        assertTrue(preview.canImport)
        val session = preview.sessions.single()
        assertEquals(2, session.sets.size)
        assertEquals(session.sets[0], session.sets[1])
        assertEquals(1704186000000L, session.startedAtMillis)
        assertEquals(45.35923700010001, session.sets.first().weightKg!!, 0.000001)
        assertTrue(
            session.sets.all {
                it.rir == null && it.sessionId == null && it.occurrenceId == null
            }
        )
        assertEquals(exercise, session.exercises["mapped"])
        assertTrue(session.inferredSetTimes)
    }

    @Test
    fun rejectsMissingMembershipDuplicateMembershipAndUnknownRows() {
        listOf(listOf(2), listOf(2, 2, 3), listOf(2, 3, 4)).forEach { rows ->
            assertIssue(
                WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP,
                build(withSession(choices.sessions.single().copy(sourceRows = rows)))
            )
        }
    }

    @Test
    fun dateOnlyNeedsExplicitTimeAndSessionConfirmation() {
        assertIssue(
            WorkoutImportIssue.INVALID_TIME,
            build(withSession(choices.sessions.single().copy(dateOnlySecondOfDay = null)))
        )
        assertIssue(
            WorkoutImportIssue.UNCONFIRMED_SESSION,
            build(withSession(choices.sessions.single().copy(confirmedSession = false)))
        )
        assertIssue(
            WorkoutImportIssue.INVALID_TIME,
            build(withSession(choices.sessions.single().copy(utcOffsetMinutes = 1081)))
        )
    }

    @Test
    fun missingMappingWarmupAndLoadInterpretationBlockImport() {
        assertIssue(
            WorkoutImportIssue.MISSING_EXERCISE,
            build(choices.copy(exercises = emptyMap()))
        )
        val choice = choices.exercises.getValue("Press")
        assertIssue(
            WorkoutImportIssue.UNKNOWN_WARMUP,
            build(withExercise(choice.copy(unknownWarmupIsWorking = false)))
        )
        assertIssue(
            WorkoutImportIssue.UNCONFIRMED_LOAD,
            build(withExercise(choice.copy(confirmedLoadMeaning = false)))
        )
    }

    @Test
    fun capabilityValidationDoesNotSilentlyDiscardNumericBodyweight() {
        val bodyweight = exercise.copy(loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY)
        val choice = choices.exercises.getValue("Press").copy(loadKind = LoadKind.BODYWEIGHT)
        assertIssue(
            WorkoutImportIssue.INVALID_LOAD,
            PreviewWorkoutImport.build(parsed, withExercise(choice), listOf(bodyweight))
        )
        val valid = PreviewWorkoutImport.build(
            parsed,
            withExercise(choice.copy(useSourceWeight = false)),
            listOf(bodyweight)
        )
        assertTrue(valid.canImport)
        assertEquals(null, valid.sessions.single().sets.first().weightKg)
    }

    @Test
    fun rejectedRowsRequireExplicitExclusionAndEffectsRequireAcknowledgement() {
        val bad = WorkoutCsvParser.parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps\n2024-01-02,Press,100,8\n2024-01-02,Press,-1,8"
        )
        val selection = withSession(choices.sessions.single().copy(sourceRows = listOf(2)))
        assertIssue(
            WorkoutImportIssue.UNRESOLVED_EXCLUSION,
            PreviewWorkoutImport.build(bad, selection, listOf(exercise))
        )
        val resolved = PreviewWorkoutImport.build(
            bad,
            selection.copy(excludedRows = setOf(3)),
            listOf(exercise)
        )
        assertTrue(resolved.canImport)
        assertIssue(
            WorkoutImportIssue.UNACKNOWLEDGED_HISTORY_EFFECTS,
            build(choices.copy(acknowledgedHistoryEffects = false))
        )
    }

    @Test
    fun metadataRequiresAcknowledgementRatherThanBecomingEffort() {
        val strong = WorkoutCsvParser.parse(
            WorkoutCsvSource.STRONG,
            "Date;Workout Name;Exercise Name;Weight;Weight Unit;Reps;RPE\n" +
                "2024-01-02 10:00:00;A;Press;50;kg;8;9"
        )
        val selection = choices.copy(
            sessions = listOf(choices.sessions.single().copy(sourceRows = listOf(2))),
            acknowledgedUnsupportedFields = false
        )
        val preview = PreviewWorkoutImport.build(strong, selection, listOf(exercise))
        assertIssue(WorkoutImportIssue.UNACKNOWLEDGED_METADATA, preview)
        assertEquals(null, preview.sessions.single().sets.single().rir)
    }

    @Test
    fun unchangedSourceSkipsDespiteLocalEditsAndExportRowOffsets() {
        val original = build().sessions.single()
        val ledger = ledger(original)
        val shifted = parsed.copy(
            rows = parsed.rows.map { it.copy(sourceRowIndex = it.sourceRowIndex + 10) }
        )
        val selection = withSession(choices.sessions.single().copy(sourceRows = listOf(12, 13)))
        val existing = WorkoutSession(
            "existing",
            original.startedAtMillis,
            null,
            original.localEpochDay
        )
        val preview = PreviewWorkoutImport.build(
            shifted,
            selection,
            listOf(exercise),
            listOf(ledger),
            listOf(existing)
        )
        assertFalse(preview.canImport)
        assertEquals("existing", preview.sessions.single().skipExistingSessionId)
        assertEquals(
            listOf(WorkoutImportIssue.UNCHANGED_SOURCE_SKIPPED),
            preview.notices.map { it.issue }
        )
    }

    @Test
    fun deletedImportedHistoryIsDisclosedAndNotRestored() {
        val original = build().sessions.single()
        val preview = PreviewWorkoutImport.build(
            parsed,
            choices,
            listOf(exercise),
            listOf(ledger(original)),
            emptyList()
        )
        assertFalse(preview.canImport)
        assertTrue(preview.problems.isEmpty())
        assertEquals(
            listOf(WorkoutImportIssue.REMOVED_HISTORY_SKIPPED),
            preview.notices.map { it.issue }
        )
    }

    @Test
    fun changedOrPartialSourceAndAmbiguousLedgerCannotOverwrite() {
        val original = build().sessions.single()
        val ledger = ledger(original)
        val changed = parsed.copy(rows = parsed.rows.map { it.copy(reps = 9) })
        assertIssue(
            WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE,
            PreviewWorkoutImport.build(changed, choices, listOf(exercise), listOf(ledger))
        )
        assertIssue(
            WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE,
            PreviewWorkoutImport.build(
                parsed,
                choices,
                listOf(exercise),
                listOf(ledger, ledger.copy(importedSessionId = "other"))
            )
        )
    }

    @Test
    fun existingOpenSessionAndBoundaryEqualityBlockImportWithoutResegmentation() {
        val original = build().sessions.single()
        val existing = WorkoutSession(
            "live",
            original.startedAtMillis,
            null,
            original.localEpochDay
        )
        assertIssue(
            WorkoutImportIssue.SESSION_OVERLAP,
            PreviewWorkoutImport.build(
                parsed,
                choices,
                listOf(exercise),
                existingSessions = listOf(existing)
            )
        )
    }

    @Test
    fun distinctSameDaySessionsAreKeptAndSplitsStayDistinctByMembership() {
        val different = parsed.copy(
            rows = parsed.rows.mapIndexed { i, row -> row.copy(reps = 8 + i) }
        )
        val selection = choices.copy(
            sessions = listOf(
                choices.sessions.single().copy(sourceRows = listOf(2), dateOnlySecondOfDay = 36000),
                choices.sessions.single().copy(sourceRows = listOf(3), dateOnlySecondOfDay = 72000)
            )
        )
        val valid = PreviewWorkoutImport.build(different, selection, listOf(exercise))
        assertTrue(valid.canImport)
        assertEquals(2, valid.sessions.size)

        // The same two rows with identical content are still two source workouts: membership, not
        // content, is the identity, so a split is preserved instead of being rejected as ambiguous.
        val split = build(selection)
        assertTrue(split.canImport, split.problems.toString())
        assertEquals(2, split.sessions.size)
        assertEquals(listOf(2), split.sessions[0].sourceRows)
        assertEquals(listOf(3), split.sessions[1].sourceRows)
        assertNotEquals(
            build(withSession(choices.sessions.single())).buckets.single().partition,
            split.buckets.single().partition
        )
    }

    @Test
    fun aTitledProviderKeepsTwoWorkoutsOnOneDayApartAndSkipsThemIndependently() {
        fun strong(workout: String, start: String, reps: Int) =
            "Date;Workout Name;Exercise Name;Weight;Weight Unit;Reps\n" +
                "2024-01-02 $start;$workout;Press;50;kg;$reps\n" +
                "2024-01-02 $start;$workout;Squat;60;kg;5"
        val morning = WorkoutCsvParser.parse(
            WorkoutCsvSource.STRONG,
            strong("Morning", "10:00:00", 8)
        )
        val evening = WorkoutCsvParser.parse(
            WorkoutCsvSource.STRONG,
            strong("Evening", "18:00:00", 9)
        )
        val both = morning.copy(
            rows = morning.rows +
                evening.rows.map { it.copy(sourceRowIndex = it.sourceRowIndex + 2) }
        )
        val selection = choices.copy(
            exercises = choices.exercises + (
                "Squat" to WorkoutImportExerciseChoice(
                    exerciseId = "mapped-squat",
                    loadKind = LoadKind.EXTERNAL,
                    useSourceWeight = true,
                    confirmedLoadMeaning = true,
                    unknownWarmupIsWorking = true
                )
                ),
            sessions = listOf(
                choices.sessions.single().copy(sourceRows = listOf(2, 3)),
                choices.sessions.single().copy(sourceRows = listOf(4, 5))
            ),
            acknowledgedUnsupportedFields = true
        )
        val catalog = listOf(exercise, exercise.copy(id = "mapped-squat", name = "Squat"))
        val first = PreviewWorkoutImport.build(both, selection, catalog)
        assertTrue(first.canImport, first.problems.toString())
        assertEquals(2, first.sessions.map { it.candidateKey }.distinct().size)

        // Both are unchanged on a second pass, independently, and neither reports a change.
        val ledger = first.sessions.map {
            WorkoutImportProvenance(
                WorkoutCsvSource.STRONG,
                it.candidateKey,
                it.content,
                it.candidateKey
            )
        }
        val again = PreviewWorkoutImport.build(both, selection, catalog, ledger)
        assertTrue(again.problems.isEmpty(), again.problems.toString())
        assertEquals(2, again.sessions.mapNotNull { it.skipExistingSessionId }.size)

        // Renaming one workout makes that one a new candidate rather than a changed one.
        val renamed = both.copy(
            rows = both.rows.map {
                if (it.workoutTitle == "Evening") it.copy(workoutTitle = "Night") else it
            }
        )
        val renamedPreview = PreviewWorkoutImport.build(renamed, selection, catalog, ledger)
        assertTrue(renamedPreview.problems.isEmpty(), renamedPreview.problems.toString())
        assertEquals(1, renamedPreview.sessions.count { it.skipExistingSessionId != null })
    }

    @Test
    fun aSessionMayNotMixSourceWorkouts() {
        val strong = WorkoutCsvParser.parse(
            WorkoutCsvSource.STRONG,
            "Date;Workout Name;Exercise Name;Weight;Weight Unit;Reps\n" +
                "2024-01-02 10:00:00;Morning;Press;50;kg;8\n" +
                "2024-01-02 18:00:00;Evening;Press;50;kg;8"
        )
        val selection = choices.copy(
            sessions = listOf(choices.sessions.single().copy(sourceRows = listOf(2, 3))),
            acknowledgedUnsupportedFields = true
        )
        assertIssue(
            WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP,
            PreviewWorkoutImport.build(strong, selection, listOf(exercise))
        )
    }

    @Test
    fun anUnchangedStoredBucketIsSkippedByItsOwnPartitionRatherThanRefused() {
        // The stored bucket carries the partition the user approved, so a repeat read of identical
        // content reproduces it and the bucket is skipped — not reported as a changed source.
        val stored = build().buckets.single().copy(importedSessionIds = listOf("existing"))

        val preview = PreviewWorkoutImport.build(
            parsed,
            choices,
            listOf(exercise),
            buckets = listOf(stored)
        )

        assertTrue(preview.problems.isEmpty(), preview.problems.toString())
        assertEquals("existing", preview.sessions.single().skipExistingSessionId)
        assertTrue(
            preview.notices.any { it.issue == WorkoutImportIssue.REMOVED_HISTORY_SKIPPED },
            preview.notices.toString()
        )
    }

    @Test
    fun aStoredBucketApprovedWithADifferentPartitionIsReportedAsChanged() {
        val proposed = build().buckets.single()
        val split = proposed.copy(
            partition = WorkoutImportPartition(
                2,
                listOf(listOf(0), listOf(1)),
                emptySet()
            ).canonical(),
            importedSessionIds = listOf("first", "second")
        )

        assertIssue(
            WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE,
            PreviewWorkoutImport.build(parsed, choices, listOf(exercise), buckets = listOf(split))
        )
    }

    @Test
    fun aStoredBucketWhoseLinksDisagreeWithItsPartitionIsReportedAsChanged() {
        // Same membership, but two recorded sessions for a one-session partition: the record cannot be
        // trusted, so it blocks instead of being reused as the approved split.
        val proposed = build().buckets.single()
        val inconsistent = proposed.copy(importedSessionIds = listOf("first", "second"))

        assertIssue(
            WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE,
            PreviewWorkoutImport.build(
                parsed,
                choices,
                listOf(exercise),
                buckets = listOf(inconsistent)
            )
        )
    }

    @Test
    fun aCountedRowKeepsTheCanonicalIdentityItAlreadyHad() {
        // The dedupe ledger stores this exact string, so a counted row must keep the same canonical
        // bytes it had before duration import existed; otherwise every previously imported rep row
        // would read as a changed source on the next import.
        val counted = WorkoutCsvParser.parse(
            WorkoutCsvSource.FITNOTES,
            "Date,Exercise,Weight (lbs),Reps\n2024-01-02,Press,100,8"
        )

        assertEquals(
            "86:5:Press42:SourceCivilDate(year=2024, month=1, day=2)-1:-1:-1:-1:-1:5:100.02:LB1:8-1:0:",
            canonicalBucketContent(counted.rows)
        )
    }

    @Test
    fun aTimedRowBecomesADurationSetAndKeepsItsOwnCanonicalIdentity() {
        val timed = WorkoutCsvParser.parse(
            WorkoutCsvSource.STRONG,
            "Date;Workout Name;Exercise Name;Weight;Weight Unit;Reps;Seconds\n" +
                "2024-01-02 10:00:00;A;Press;0;kg;0;90"
        )
        val selection = choices.copy(
            sessions = listOf(choices.sessions.single().copy(sourceRows = listOf(2)))
        )

        val preview = PreviewWorkoutImport.build(timed, selection, listOf(exercise))

        assertTrue(preview.canImport, preview.problems.toString())
        val session = preview.sessions.single()
        val set = session.sets.single()
        assertNull(set.reps)
        assertEquals(90, set.durationSeconds)

        // The same row with a rep count instead of a duration must canonicalize differently, or a
        // timed row could collide with — or dedupe as — a rep row.
        val countedSameShape = timed.copy(
            rows = timed.rows.map { it.copy(reps = 90, durationSeconds = null) }
        )
        assertNotEquals(
            canonicalBucketContent(countedSameShape.rows),
            canonicalBucketContent(timed.rows)
        )

        // And the timed row's own identity is stable, so a repeat read skips instead of re-importing.
        val existing = WorkoutSession(
            id = "existing",
            startedAtMillis = session.startedAtMillis,
            endedAtMillis = null,
            localEpochDay = session.localEpochDay
        )
        val again = PreviewWorkoutImport.build(
            timed,
            selection,
            listOf(exercise),
            provenance = listOf(
                WorkoutImportProvenance(
                    source = WorkoutCsvSource.STRONG,
                    candidateKey = session.candidateKey,
                    content = session.content,
                    importedSessionId = "existing"
                )
            ),
            existingSessions = listOf(existing)
        )
        assertFalse(again.canImport)
        assertEquals(
            listOf(WorkoutImportIssue.UNCHANGED_SOURCE_SKIPPED),
            again.notices.map { it.issue }
        )
    }

    @Test
    fun aStrongCountedRowWithZeroSecondsKeepsItsLegacyCanonicalIdentity() {
        // Seconds was recorded as unsupported metadata on a rep row before duration import existed, so
        // the ledger already stores these exact bytes. Reproducing them is what lets an unchanged
        // source dedupe instead of reading as a changed source on the next import.
        val counted = WorkoutCsvParser.parse(
            WorkoutCsvSource.STRONG,
            strongCsv(
                strongRow(
                    "2024-07-02 10:03:07", "Push", "Bench", "1", "80", "kg", "5",
                    "", "", "", "0", "", "", ""
                )
            )
        )

        val row = counted.rows.single()
        assertEquals(5, row.reps)
        assertNull(row.durationSeconds)
        assertEquals(listOf("Seconds"), row.diagnostics.map { it.field })
        assertEquals(LEGACY_STRONG_ZERO_SECONDS_CONTENT, canonicalBucketContent(counted.rows))
    }

    @Test
    fun aHevyCountedRowWithZeroDurationSecondsKeepsItsLegacyCanonicalIdentity() {
        val counted = WorkoutCsvParser.parse(
            WorkoutCsvSource.HEVY,
            hevyCsv(
                hevyRow(
                    "Push", "02 Jul 2024, 10:03", "02 Jul 2024, 11:00", "", "Bench Press", "", "",
                    "0", "normal", "135", "5", "", "0", ""
                )
            )
        )

        val row = counted.rows.single()
        assertEquals(5, row.reps)
        assertNull(row.durationSeconds)
        assertEquals(listOf("duration_seconds"), row.diagnostics.map { it.field })
        assertEquals(LEGACY_HEVY_ZERO_DURATION_CONTENT, canonicalBucketContent(counted.rows))
    }

    @Test
    fun aZeroSecondCountedRowStillMatchesItsLegacyBucketAndProvenance() {
        val counted = WorkoutCsvParser.parse(
            WorkoutCsvSource.STRONG,
            strongCsv(
                strongRow(
                    "2024-07-02 10:03:07", "Push", "Bench", "1", "80", "kg", "5",
                    "", "", "", "0", "", "", ""
                )
            )
        )
        val selection = countedChoices()
        val first = PreviewWorkoutImport.build(counted, selection, listOf(exercise))
        assertTrue(first.canImport, first.problems.toString())
        val session = first.sessions.single()
        assertEquals(LEGACY_STRONG_ZERO_SECONDS_CONTENT, session.content)
        val legacy = WorkoutImportProvenance(
            WorkoutCsvSource.STRONG,
            session.candidateKey,
            LEGACY_STRONG_ZERO_SECONDS_CONTENT,
            "legacy"
        )

        // The legacy provenance ledger already holds these bytes, so the repeat import is skipped.
        val existing = WorkoutSession(
            "legacy",
            session.startedAtMillis,
            null,
            session.localEpochDay
        )
        val again = PreviewWorkoutImport.build(
            counted,
            selection,
            listOf(exercise),
            provenance = listOf(legacy),
            existingSessions = listOf(existing)
        )
        assertTrue(again.problems.isEmpty(), again.problems.toString())
        assertEquals("legacy", again.sessions.single().skipExistingSessionId)
        assertEquals(
            listOf(WorkoutImportIssue.UNCHANGED_SOURCE_SKIPPED),
            again.notices.map { it.issue }
        )

        // The stored bucket reproduces the same bytes and partition, so it is reused rather than
        // reported as a changed source.
        val stored = first.buckets.single().copy(importedSessionIds = listOf("legacy"))
        val viaBucket = PreviewWorkoutImport.build(
            counted,
            selection,
            listOf(exercise),
            buckets = listOf(stored)
        )
        assertTrue(viaBucket.problems.isEmpty(), viaBucket.problems.toString())
        assertEquals("legacy", viaBucket.sessions.single().skipExistingSessionId)

        // A genuine change to the row's measurement is still a changed source, not a match.
        val changed = counted.copy(rows = counted.rows.map { it.copy(reps = 6) })
        assertIssue(
            WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE,
            PreviewWorkoutImport.build(
                changed,
                selection,
                listOf(exercise),
                provenance = listOf(legacy)
            )
        )
    }

    @Test
    fun splittingAWorkoutLeavesItsCanonicalBucketContentUnchanged() {
        // The split lives in the partition, not the content: two identical rows produce the same
        // canonical bytes whether they are approved as one session or two, so a repeat read still
        // recognises the bucket.
        val single = build()
        val splitChoices = choices.copy(
            sessions = listOf(
                choices.sessions.single().copy(sourceRows = listOf(2), dateOnlySecondOfDay = 36000),
                choices.sessions.single().copy(sourceRows = listOf(3), dateOnlySecondOfDay = 72000)
            )
        )
        val split = build(splitChoices)

        assertEquals(single.buckets.single().content, split.buckets.single().content)
        assertNotEquals(single.buckets.single().partition, split.buckets.single().partition)
        assertTrue(split.canImport, split.problems.toString())
    }

    @Test
    fun nullAndEmptyCanonicalFieldsCannotCollide() {
        assertFalse(canonicalFields(listOf(null)) == canonicalFields(listOf("")))
        assertFalse(canonicalFields(listOf("a", "bc")) == canonicalFields(listOf("ab", "c")))
    }

    private fun build(selection: WorkoutImportChoices = choices) =
        PreviewWorkoutImport.build(parsed, selection, listOf(exercise))

    private fun withSession(session: WorkoutImportSessionChoice) =
        choices.copy(sessions = listOf(session))

    private fun withExercise(exercise: WorkoutImportExerciseChoice) =
        choices.copy(exercises = mapOf("Press" to exercise))

    private fun ledger(session: WorkoutImportSession) = WorkoutImportProvenance(
        parsed.source,
        session.candidateKey,
        session.content,
        "existing"
    )

    private fun assertIssue(issue: WorkoutImportIssue, preview: WorkoutImportPreview) {
        assertFalse(preview.canImport)
        assertTrue(preview.problems.any { it.issue == issue }, preview.problems.toString())
    }

    private fun strongCsv(vararg rows: String): String = buildString {
        append("Date;Workout Name;Exercise Name;Set Order;Weight;Weight Unit;Reps;RPE;Distance;")
        append("Distance Unit;Seconds;Notes;Workout Notes;Workout Duration\n")
        rows.forEach { append(it).append('\n') }
    }

    private fun strongRow(vararg values: String): String = values.joinToString(";")

    private fun hevyCsv(vararg rows: String): String = buildString {
        append("title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,")
        append("set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n")
        rows.forEach { append(it).append('\n') }
    }

    private fun hevyRow(vararg values: String): String = values.joinToString(",") { value ->
        if (value.contains(',') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun countedChoices() = WorkoutImportChoices(
        exercises = mapOf(
            "Bench" to WorkoutImportExerciseChoice("mapped", LoadKind.EXTERNAL, true, true, true)
        ),
        sessions = listOf(WorkoutImportSessionChoice(listOf(2), 0, true, true)),
        acknowledgedUnsupportedFields = true,
        acknowledgedHistoryEffects = true
    )

    private companion object {
        /**
         * The canonical bytes the parser wrote for a Strong rep row carrying an explicit `Seconds=0`
         * before duration import existed. The legacy provenance ledger stores this exact string.
         */
        const val LEGACY_STRONG_ZERO_SECONDS_CONTENT =
            "131:5:Bench42:SourceCivilDate(year=2024, month=7, day=2)5:36187-1:-1:4:Push-1:4:80.02:KG1:5-1:38:35:20:UNSUPPORTED_METADATA7:Seconds1:0"

        /** The same, for a Hevy rep row carrying an explicit `duration_seconds=0`. */
        const val LEGACY_HEVY_ZERO_DURATION_CONTENT =
            "199:11:Bench Press42:SourceCivilDate(year=2024, month=7, day=2)5:3618042:SourceCivilDate(year=2024, month=7, day=2)5:396004:Push-1:5:135.02:LB1:55:false48:45:20:UNSUPPORTED_METADATA16:duration_seconds1:0"
    }
}
