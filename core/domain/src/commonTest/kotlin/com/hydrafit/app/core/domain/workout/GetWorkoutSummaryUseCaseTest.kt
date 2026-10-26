package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

/**
 * The use case is the read seam between the pure C1 projection and storage: it resolves an
 * occurrence, its recorded sets, the sessions those sets span, their notes and the current
 * exercise names, then delegates. These tests cover what it adds over `WorkoutSummaryTest` -
 * resolution, null handling and the extra-set catalog lookup.
 */
class GetWorkoutSummaryUseCaseTest {

    @Test
    fun returnsNullWhenTheOccurrenceNoLongerExists() = runTest {
        val useCase = useCase(occurrence = null)

        assertNull(useCase(30L))
    }

    @Test
    fun resolvesTheOccurrenceSetsSessionsAndStoredNotes() = runTest {
        val useCase = useCase(
            occurrence = occurrence(entry(id = 40, sets = 2)),
            sets = listOf(
                set(id = 1, occurrenceEntryId = 40, sessionId = "s1"),
                set(id = 2, occurrenceEntryId = 40, sessionId = "s2")
            ),
            sessions = listOf(
                WorkoutSession("s1", 100L, 200L, 0L, 30L),
                WorkoutSession("s2", 300L, 400L, 0L, 30L),
                WorkoutSession("s3", 500L, 600L, 0L, 30L)
            ),
            notes = mapOf("s1" to "felt strong", "s2" to "")
        )

        val summary = requireNotNull(useCase(30L))

        assertEquals(30L, summary.occurrenceId)
        assertEquals("Workout A", summary.workoutName)
        assertEquals(OccurrenceStatus.FINISHED, summary.status)
        assertEquals(1_000L, summary.startedAtMillis)
        assertEquals(2_000L, summary.resolvedAtMillis)
        assertEquals(2, summary.exercises.single().performedSets.size)
        assertEquals(0, summary.remainingSets)
        // Only the sessions the recorded work spans, with their stored note encoding preserved.
        assertEquals(listOf("s1", "s2"), summary.sessions.map { it.sessionId })
        assertEquals("felt strong", summary.sessions[0].note)
        assertEquals("", summary.sessions[1].note)
    }

    @Test
    fun ignoresASessionIdentityWithNoStoredSessionRow() = runTest {
        val useCase = useCase(
            occurrence = occurrence(entry(id = 40, sets = 2)),
            sets = listOf(
                set(id = 1, occurrenceEntryId = 40, sessionId = "missing"),
                set(id = 2, occurrenceEntryId = 40, sessionId = null)
            ),
            sessions = emptyList()
        )

        val summary = requireNotNull(useCase(30L))

        assertTrue(summary.sessions.isEmpty())
        assertEquals(2, summary.exercises.single().performedSets.size)
    }

    @Test
    fun readsTheCurrentCatalogNameForASetThatBelongsToNoSlot() = runTest {
        val useCase = useCase(
            occurrence = occurrence(entry(id = 40, sets = 1)),
            sets = listOf(
                set(
                    id = 1,
                    exerciseId = "unlisted-curl",
                    sessionId = null,
                    occurrenceEntryId = null
                ),
                set(
                    id = 2,
                    exerciseId = "unlisted-curl",
                    sessionId = null,
                    occurrenceEntryId = 999L
                )
            ),
            catalog = listOf(exercise("unlisted-curl", "Cable Curl"))
        )

        val summary = requireNotNull(useCase(30L))

        assertEquals(listOf(1L, 2L), summary.extraSets.map { it.id })
        assertEquals(listOf("Cable Curl", "Cable Curl"), summary.extraSets.map { it.exerciseName })
        assertNull(summary.extraSets[0].sessionId)
    }

    @Test
    fun marksAReplacedSlotOnlyFromItsSourceActivationEntry() = runTest {
        val replaced = useCase(
            occurrence = occurrence(
                entry(id = 40, exerciseId = "front-squat", sourceActivationEntryId = 5L)
            ),
            activation = activation(activationEntry(id = 5L, exerciseId = "back-squat"))
        )
        val untouched = useCase(
            occurrence = occurrence(
                entry(id = 40, exerciseId = "back-squat", sourceActivationEntryId = 5L)
            ),
            activation = activation(activationEntry(id = 5L, exerciseId = "back-squat"))
        )

        assertTrue(requireNotNull(replaced(30L)).exercises.single().isReplaced)
        assertFalse(requireNotNull(untouched(30L)).exercises.single().isReplaced)
    }

    @Test
    fun reportsNoWorkoutNameWhenTheActivationIsUnavailable() = runTest {
        val useCase = useCase(occurrence = occurrence(entry(id = 40, sets = 1)), activation = null)

        val summary = requireNotNull(useCase(30L))

        assertNull(summary.workoutName)
        assertFalse(summary.exercises.single().isReplaced)
    }

    @Test
    fun keepsTheCatalogNameForAWarmupThatBelongsToNoSlot() = runTest {
        // R10-12: a warm-up with no entry link is named from the catalog too, even when every
        // working set is attributed. The narrowed read must not drop its name.
        val useCase = useCase(
            occurrence = occurrence(entry(id = 40, sets = 1)),
            sets = listOf(
                set(id = 1, occurrenceEntryId = 40),
                set(id = 2, exerciseId = "unlisted-curl", sessionId = null, isWarmup = true)
            ),
            catalog = listOf(exercise("unlisted-curl", "Cable Curl"))
        )

        val summary = requireNotNull(useCase(30L))

        assertTrue(summary.extraSets.isEmpty())
        assertEquals("Cable Curl", summary.warmupSets.single().exerciseName)
    }

    @Test
    fun skipsTheCatalogWhenEveryRecordedSetIsNamedByItsEntry() = runTest {
        var reads = 0
        val useCase = useCase(
            occurrence = occurrence(entry(id = 40, sets = 2)),
            sets = listOf(
                set(id = 1, occurrenceEntryId = 40),
                set(id = 2, occurrenceEntryId = 40, isWarmup = true)
            ),
            onCatalogRead = { reads++ }
        )

        val summary = requireNotNull(useCase(30L))

        assertEquals(0, reads)
        assertEquals("back-squat", summary.warmupSets.single().exerciseName)
        assertEquals("back-squat", summary.exercises.single().performedSets.single().exerciseName)
    }

    private fun useCase(
        occurrence: WorkoutOccurrence?,
        sets: List<WorkoutSet> = emptyList(),
        sessions: List<WorkoutSession> = emptyList(),
        notes: Map<String, String?> = emptyMap(),
        activation: TrainingActivation? = activation(),
        catalog: List<Exercise> = emptyList(),
        onCatalogRead: () -> Unit = {}
    ) = GetWorkoutSummaryUseCase(
        scheduleRepository = FakeScheduleRepository(occurrence, activation),
        workoutLogRepository = FakeWorkoutLogRepository(sets),
        sessionRepository = FakeWorkoutSessionRepository(sessions),
        sessionNoteRepository = FakeSessionNoteRepository(notes),
        exerciseCatalog = FakeCatalog(catalog, onCatalogRead)
    )

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
        exerciseId: String = "back-squat",
        sets: Int = 3,
        sourceActivationEntryId: Long? = null
    ) = OccurrenceEntry(
        id = id,
        sourceActivationEntryId = sourceActivationEntryId,
        position = 0,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        movementPattern = MovementPattern.SQUAT,
        sets = sets,
        reps = 8,
        weightKg = 100.0,
        loadCapability = ExerciseLoadCapability.EXTERNAL,
        loadKind = LoadKind.EXTERNAL
    )

    private fun set(
        id: Long,
        exerciseId: String = "back-squat",
        sessionId: String? = "session-1",
        occurrenceEntryId: Long? = null,
        isWarmup: Boolean = false
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        reps = 8,
        weightKg = 100.0,
        loadKind = LoadKind.EXTERNAL,
        performedAtMillis = 1_000L + id,
        isWarmup = isWarmup,
        sessionId = sessionId,
        occurrenceId = 30L,
        occurrenceEntryId = occurrenceEntryId
    )

    private fun activationEntry(id: Long, exerciseId: String) = ActivationEntry(
        id = id,
        position = 0,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        movementPattern = MovementPattern.SQUAT,
        sets = 3,
        reps = 8,
        weightKg = 100.0
    )

    private fun activation(vararg entries: ActivationEntry) = TrainingActivation(
        id = 5L,
        name = "Block",
        createdAtMillis = 1L,
        startEpochDay = 0L,
        mode = ScheduleMode.SEQUENCE,
        weekdays = emptySet(),
        status = ActivationStatus.ACTIVE,
        workouts = listOf(
            ActivationWorkout(id = 9L, position = 0, name = "Workout A", entries = entries.toList())
        )
    )

    private fun exercise(id: String, name: String) = Exercise(
        id = id,
        name = name,
        requiredEquipment = setOf(EquipmentTag.BODYWEIGHT),
        primaryMuscles = setOf(MuscleGroup.BICEPS)
    )

    private class FakeCatalog(
        private val exercises: List<Exercise>,
        private val onRead: () -> Unit = {}
    ) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> {
            onRead()
            return exercises
        }
    }

    private class FakeScheduleRepository(
        private val occurrence: WorkoutOccurrence?,
        private val activation: TrainingActivation?
    ) : WorkoutScheduleRepository {
        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? =
            occurrence?.takeIf { it.id == id }

        override suspend fun getActivation(id: Long): TrainingActivation? =
            activation?.takeIf { it.id == id }

        override fun observeScheduleState(): Flow<WorkoutScheduleState> = emptyFlow()

        override fun observeActiveActivation(): Flow<TrainingActivation?> = emptyFlow()

        override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
            emptyFlow()

        override suspend fun scheduleState(): WorkoutScheduleState = notUsed()

        override suspend fun setScheduleState(state: WorkoutScheduleState) = notUsed()

        override suspend fun activeActivation(): TrainingActivation? = notUsed()

        override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> = notUsed()

        override suspend fun acceptAndActivate(
            acceptedPlan: AcceptedPlan?,
            activation: TrainingActivation,
            scheduledEpochDays: List<Long?>,
            replaceActive: Boolean
        ): Long = notUsed()

        override suspend fun resolveOccurrence(
            occurrenceId: Long,
            expectedRevision: Int,
            status: OccurrenceStatus,
            resolvedAtMillis: Long,
            entries: List<OccurrenceEntry>
        ): WorkoutScheduleState = notUsed()

        override suspend fun updateActivationHeader(activation: TrainingActivation) = notUsed()

        override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = notUsed()

        override suspend fun replaceOccurrenceEntries(
            occurrenceId: Long,
            entries: List<OccurrenceEntry>
        ) = notUsed()

        override suspend fun deleteOccurrencesForActivation(activationId: Long) = notUsed()

        override suspend fun isTemplateReferenced(templateId: Long): Boolean = notUsed()
    }

    private class FakeWorkoutLogRepository(private val sets: List<WorkoutSet>) :
        WorkoutLogRepository {
        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> =
            sets.filter { it.occurrenceId == occurrenceId }

        override suspend fun add(set: WorkoutSet) = notUsed()

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = notUsed()

        override suspend fun assignSession(setId: Long, sessionId: String) = notUsed()

        override suspend fun delete(id: Long) = notUsed()

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = notUsed()

        override suspend fun all(): List<WorkoutSet> = notUsed()

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = notUsed()

        override fun setsFlow(): Flow<List<WorkoutSet>> = emptyFlow()

        override suspend fun loggedSets(): List<LoggedSet> = notUsed()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = emptyFlow()

        override suspend fun clear() = notUsed()
    }

    private class FakeWorkoutSessionRepository(private val sessions: List<WorkoutSession>) :
        WorkoutSessionRepository {
        override suspend fun byId(id: String): WorkoutSession? =
            sessions.firstOrNull { it.id == id }

        override suspend fun all(): List<WorkoutSession> = sessions

        override suspend fun create(session: WorkoutSession) = notUsed()

        override suspend fun end(id: String, endedAtMillis: Long) = notUsed()

        override suspend fun open(): WorkoutSession? = notUsed()

        override fun openFlow(): Flow<WorkoutSession?> = emptyFlow()
    }

    private class FakeSessionNoteRepository(private val notes: Map<String, String?>) :
        SessionNoteRepository {
        override suspend fun note(sessionId: String): String? = notes[sessionId]

        override suspend fun save(sessionId: String, text: String) = notUsed()

        override suspend fun clear(sessionId: String) = notUsed()
    }
}

/** Fails loudly if the read-only use case ever reaches a member it should not. */
private fun notUsed(): Nothing = error("not used by GetWorkoutSummaryUseCase")
