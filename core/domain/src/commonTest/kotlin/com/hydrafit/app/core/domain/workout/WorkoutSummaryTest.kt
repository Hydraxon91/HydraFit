package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutSummaryTest {

    @Test
    fun reconcilesAFullyRecordedSlotWithItsFrozenTarget() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, sets = 3, reps = 8, weightKg = 100.0)),
            sets = listOf(
                set(id = 1, occurrenceEntryId = 40, reps = 8),
                set(id = 2, occurrenceEntryId = 40, reps = 8),
                set(id = 3, occurrenceEntryId = 40, reps = 7)
            ),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        val exercise = summary.exercises.single()
        assertEquals(3, exercise.prescribedSets)
        assertEquals(8, exercise.prescribedReps)
        assertEquals(100.0, exercise.prescribedWeightKg)
        assertEquals(listOf(8, 8, 7), exercise.performedSets.map { it.reps })
        assertEquals(3, exercise.recordedSets)
        assertEquals(0, exercise.remainingSets)
        assertEquals(0, exercise.setsBeyondTarget)
        assertTrue(exercise.isComplete)
        assertNull(exercise.remainingDisposition)
        assertFalse(exercise.isReplaced)
        assertEquals(3, summary.prescribedSets)
        assertEquals(3, summary.recordedWorkingSets)
        assertEquals(0, summary.remainingSets)
        assertTrue(summary.isComplete)
        assertEquals(OccurrenceStatus.FINISHED, summary.status)
        assertEquals(1_000L, summary.startedAtMillis)
        assertEquals(2_000L, summary.resolvedAtMillis)
    }

    @Test
    fun countsATimedSetAsRecordedWorkAndCarriesItsSeconds() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, sets = 3, reps = 8)),
            sets = listOf(set(id = 1, occurrenceEntryId = 40, reps = null, durationSeconds = 60)),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        val exercise = summary.exercises.single()
        assertEquals(1, exercise.recordedSets)
        assertEquals(2, exercise.remainingSets)
        assertEquals(1, summary.recordedWorkingSets)
        val performed = exercise.performedSets.single()
        assertNull(performed.reps)
        assertEquals(60, performed.durationSeconds)
    }

    @Test
    fun carriesATimedPrescriptionWithItsSecondsAndCountsItsRecordedSetsNotReps() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(
                entry(id = 40, sets = 3).copy(reps = null, durationSeconds = 90)
            ),
            sets = listOf(
                set(id = 1, occurrenceEntryId = 40, reps = null, durationSeconds = 90),
                set(id = 2, occurrenceEntryId = 40, reps = null, durationSeconds = 60)
            ),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        val exercise = summary.exercises.single()
        assertEquals(3, exercise.prescribedSets)
        assertNull(exercise.prescribedReps)
        assertEquals(90, exercise.prescribedDurationSeconds)
        assertEquals(2, exercise.recordedSets)
        assertEquals(1, exercise.remainingSets)
        // Both recorded sets count as working work; only the rep count is absent.
        assertEquals(2, summary.recordedWorkingSets)
        // Timed work is counted as recorded sets; it never contributes a rep count.
        assertEquals(listOf(90, 60), exercise.performedSets.map { it.durationSeconds })
        assertTrue(exercise.performedSets.all { it.reps == null })
    }

    @Test
    fun derivesRemainingWorkFromRecordedSetsAndCarriesTheOmittedDisposition() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(
                entry(
                    id = 40,
                    sets = 3,
                    remainingDisposition = RemainingDisposition.OMITTED,
                    terminalRemainingSets = 2
                ),
                status = OccurrenceStatus.FINISHED_PARTIAL
            ),
            sets = listOf(set(id = 1, occurrenceEntryId = 40)),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        val exercise = summary.exercises.single()
        assertEquals(2, exercise.remainingSets)
        assertEquals(RemainingDisposition.OMITTED, exercise.remainingDisposition)
        assertFalse(exercise.isComplete)
        assertFalse(summary.isComplete)
        assertEquals(1, summary.recordedWorkingSets)
    }

    @Test
    fun carriesASkippedDispositionWhenWorkWasWaived() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(
                entry(
                    id = 40,
                    sets = 3,
                    remainingDisposition = RemainingDisposition.SKIPPED,
                    terminalRemainingSets = 2
                ),
                status = OccurrenceStatus.FINISHED_PARTIAL
            ),
            sets = listOf(set(id = 1, occurrenceEntryId = 40)),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        assertEquals(RemainingDisposition.SKIPPED, summary.exercises.single().remainingDisposition)
    }

    @Test
    fun recomputesRemainingSoTheSummaryReconcilesWithStoredWork() {
        // A full finish whose stored conclusion bookkeeping is empty, but one recorded set was
        // removed afterwards: the summary must show the remaining set, not claim completion.
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, sets = 3), status = OccurrenceStatus.FINISHED),
            sets = listOf(set(id = 1, occurrenceEntryId = 40), set(id = 2, occurrenceEntryId = 40)),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        assertEquals(1, summary.exercises.single().remainingSets)
        assertFalse(summary.isComplete)
    }

    @Test
    fun marksASlotReplacedOnlyFromItsSourceActivationEntry() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(
                entry(id = 40, exerciseId = "band-pulldown", sourceActivationEntryId = 100),
                entry(id = 41, exerciseId = "back-squat", sourceActivationEntryId = 101)
            ),
            sets = emptyList(),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation(
                activationEntry(id = 100, exerciseId = "lat-pulldown"),
                activationEntry(id = 101, exerciseId = "back-squat")
            )
        )

        assertTrue(summary.exercises[0].isReplaced)
        assertFalse(summary.exercises[1].isReplaced)
    }

    @Test
    fun makesNoReplacementClaimWithoutProvenance() {
        val withoutSource = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, exerciseId = "band-pulldown")),
            sets = emptyList(),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation(activationEntry(id = 100, exerciseId = "lat-pulldown"))
        )
        val withoutActivation = buildWorkoutSummary(
            occurrence = occurrence(
                entry(id = 40, exerciseId = "band-pulldown", sourceActivationEntryId = 100)
            ),
            sets = emptyList(),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = null
        )

        assertFalse(withoutSource.exercises.single().isReplaced)
        assertFalse(withoutActivation.exercises.single().isReplaced)
        assertNull(withoutActivation.workoutName)
    }

    @Test
    fun separatesAdditionalWorkFromPrescribedSlots() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, exerciseId = "back-squat", sets = 3)),
            sets = listOf(
                set(id = 1, exerciseId = "back-squat", occurrenceEntryId = 40),
                set(id = 2, exerciseId = "curl", occurrenceEntryId = null),
                set(id = 3, exerciseId = "curl", occurrenceEntryId = 99)
            ),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation(),
            exerciseNames = mapOf("curl" to "Biceps Curl")
        )

        val exercise = summary.exercises.single()
        assertEquals(1, exercise.recordedSets)
        assertEquals(2, exercise.remainingSets)
        assertEquals(
            listOf("Biceps Curl", "Biceps Curl"),
            summary.extraSets.map { it.exerciseName }
        )
        assertEquals(listOf(2L, 3L), summary.extraSets.map { it.id })
        assertEquals(3, summary.recordedWorkingSets)
    }

    @Test
    fun countsSetsBeyondTheTargetWithoutReducingRemainingBelowZero() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, sets = 2)),
            sets = (1L..5L).map { set(id = it, occurrenceEntryId = 40) },
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        val exercise = summary.exercises.single()
        assertEquals(5, exercise.recordedSets)
        assertEquals(0, exercise.remainingSets)
        assertEquals(3, exercise.setsBeyondTarget)
        assertTrue(exercise.isComplete)
        assertTrue(summary.extraSets.isEmpty())
    }

    @Test
    fun keepsWarmUpsOutOfProgressAndNamesThemHonestly() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, exerciseId = "back-squat", sets = 3)),
            sets = listOf(
                set(id = 1, exerciseId = "back-squat", occurrenceEntryId = 40, isWarmup = true),
                set(id = 2, exerciseId = "curl", occurrenceEntryId = null, isWarmup = true),
                set(id = 3, exerciseId = "back-squat", occurrenceEntryId = 40)
            ),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation(),
            exerciseNames = mapOf("curl" to "Biceps Curl")
        )

        assertEquals(1, summary.exercises.single().recordedSets)
        assertEquals(2, summary.exercises.single().remainingSets)
        assertEquals(listOf(1L, 2L), summary.warmupSets.map { it.id })
        assertEquals(
            listOf("back-squat", "Biceps Curl"),
            summary.warmupSets.map { it.exerciseName }
        )
        assertTrue(summary.warmupSets.all { it.isWarmup })
        assertEquals(1, summary.recordedWorkingSets)
    }

    @Test
    fun ordersSlotsSetsAndSessionsDeterministically() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(
                entry(id = 41, position = 1, exerciseId = "bench-press", sets = 2),
                entry(id = 40, position = 0, exerciseId = "back-squat", sets = 2)
            ),
            sets = listOf(
                set(id = 9, occurrenceEntryId = 40, performedAtMillis = 500L),
                set(id = 3, occurrenceEntryId = 40, performedAtMillis = 100L),
                set(id = 7, occurrenceEntryId = 40, performedAtMillis = 100L)
            ),
            sessions = listOf(
                WorkoutSession("s2", 900L, null, 0L, 30L),
                WorkoutSession("s1", 100L, 200L, 0L, 30L)
            ),
            sessionNotes = emptyMap(),
            activation = activation()
        )

        assertEquals(listOf(40L, 41L), summary.exercises.map { it.occurrenceEntryId })
        assertEquals(
            listOf(100L, 100L, 500L),
            summary.exercises[0].performedSets.map { it.performedAtMillis }
        )
        assertEquals(listOf(3L, 7L, 9L), summary.exercises[0].performedSets.map { it.id })
        assertEquals(listOf("s1", "s2"), summary.sessions.map { it.sessionId })
    }

    @Test
    fun carriesStoredSessionNotesIncludingAnExplicitClear() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, sets = 3)),
            sets = listOf(set(id = 1, occurrenceEntryId = 40, sessionId = "s1")),
            sessions = listOf(
                WorkoutSession("s1", 100L, 200L, 0L, 30L),
                WorkoutSession("s2", 300L, null, 0L, 30L),
                WorkoutSession("s3", 400L, null, 0L, 30L)
            ),
            sessionNotes = mapOf("s1" to "felt strong", "s2" to ""),
            activation = activation()
        )

        assertEquals("felt strong", summary.sessions[0].note)
        assertTrue(summary.sessions[0].hasNote)
        assertEquals("", summary.sessions[1].note)
        assertFalse(summary.sessions[1].hasNote)
        assertNull(summary.sessions[2].note)
        assertFalse(summary.sessions[2].hasNote)
        assertEquals("s1", summary.exercises.single().performedSets.single().sessionId)
    }

    @Test
    fun readsTheWorkoutNameFromTheActivation() {
        val summary = buildWorkoutSummary(
            occurrence = occurrence(entry(id = 40, sets = 3)),
            sets = emptyList(),
            sessions = emptyList(),
            sessionNotes = emptyMap(),
            activation = activation(workoutName = "Pull Day")
        )

        assertEquals("Pull Day", summary.workoutName)
    }

    private fun occurrence(
        vararg entries: OccurrenceEntry,
        status: OccurrenceStatus = OccurrenceStatus.FINISHED
    ) = WorkoutOccurrence(
        id = 30L,
        activationId = 5L,
        activationWorkoutId = 9L,
        queuePosition = 0,
        startedAtMillis = 1_000L,
        resolvedAtMillis = 2_000L,
        status = status,
        entries = entries.toList()
    )

    private fun entry(
        id: Long,
        position: Int = 0,
        exerciseId: String = "back-squat",
        sets: Int = 3,
        reps: Int = 8,
        weightKg: Double? = 100.0,
        sourceActivationEntryId: Long? = null,
        remainingDisposition: RemainingDisposition? = null,
        terminalRemainingSets: Int? = null
    ) = OccurrenceEntry(
        id = id,
        sourceActivationEntryId = sourceActivationEntryId,
        position = position,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        movementPattern = MovementPattern.SQUAT,
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        loadCapability = ExerciseLoadCapability.EXTERNAL,
        loadKind = LoadKind.EXTERNAL,
        remainingDisposition = remainingDisposition,
        terminalRemainingSets = terminalRemainingSets
    )

    private fun set(
        id: Long,
        exerciseId: String = "back-squat",
        reps: Int? = 8,
        performedAtMillis: Long = 1_000L + id,
        isWarmup: Boolean = false,
        sessionId: String? = "session-1",
        occurrenceId: Long? = 30L,
        occurrenceEntryId: Long? = null,
        durationSeconds: Int? = null
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        reps = reps,
        weightKg = 100.0,
        loadKind = LoadKind.EXTERNAL,
        performedAtMillis = performedAtMillis,
        isWarmup = isWarmup,
        sessionId = sessionId,
        occurrenceId = occurrenceId,
        occurrenceEntryId = occurrenceEntryId,
        durationSeconds = durationSeconds
    )

    private fun activation(vararg entries: ActivationEntry, workoutName: String = "Workout A") =
        TrainingActivation(
            id = 5L,
            name = "Block",
            createdAtMillis = 1L,
            startEpochDay = 0L,
            mode = ScheduleMode.SEQUENCE,
            weekdays = emptySet(),
            status = ActivationStatus.ACTIVE,
            workouts = listOf(
                ActivationWorkout(
                    id = 9L,
                    position = 0,
                    name = workoutName,
                    entries = entries.toList()
                )
            )
        )

    private fun activationEntry(id: Long, exerciseId: String) = ActivationEntry(
        id = id,
        position = 0,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        movementPattern = MovementPattern.SQUAT,
        sets = 3,
        reps = 8,
        weightKg = 100.0,
        loadCapability = ExerciseLoadCapability.EXTERNAL,
        loadKind = LoadKind.EXTERNAL
    )
}
