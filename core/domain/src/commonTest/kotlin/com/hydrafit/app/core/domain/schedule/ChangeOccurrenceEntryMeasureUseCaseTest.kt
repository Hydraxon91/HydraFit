package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ChangeOccurrenceEntryMeasureUseCaseTest {

    @Test
    fun eachMeasureWritesOnlyItsOwnFieldAndClearsTheOthers() = runTest {
        for (measure in OccurrenceEntryMeasure.entries) {
            val schedule = FakeSchedule(entry())
            val targets = RecordingTargets()
            ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)
                .invoke(1L, 10L, measure, 12.0)

            val written = targets.requests.single().target
            assertEquals(measure == OccurrenceEntryMeasure.REPETITIONS, written.reps != null)
            assertEquals(measure == OccurrenceEntryMeasure.SECONDS, written.durationSeconds != null)
            assertEquals(
                measure == OccurrenceEntryMeasure.DISTANCE_METERS,
                written.distanceMeters != null
            )
            assertEquals(
                measure == OccurrenceEntryMeasure.FORCE_NEWTONS,
                written.forceNewtons != null
            )
        }
    }

    @Test
    fun repsKeepsTheCompatibleExistingLoadAndSecondsClearsIt() = runTest {
        val schedule = FakeSchedule(entry())
        val targets = RecordingTargets()
        val useCase = ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)

        useCase.invoke(1L, 10L, OccurrenceEntryMeasure.REPETITIONS, 8.0)
        val repsTarget = targets.requests.single().target
        assertEquals(LoadKind.EXTERNAL, repsTarget.loadKind)
        assertEquals(60.0, repsTarget.weightKg)

        useCase.invoke(1L, 10L, OccurrenceEntryMeasure.SECONDS, 30.0)
        val secondsTarget = targets.requests.last().target
        assertEquals(LoadKind.EXTERNAL, secondsTarget.loadKind)
        assertNull(secondsTarget.weightKg)
    }

    @Test
    fun distanceKeepsALoadOnlyWhenTheCapabilityPermitsIt() = runTest {
        val schedule = FakeSchedule(entry(loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY))
        val targets = RecordingTargets(ExerciseLoadCapability.BODYWEIGHT_ONLY)
        val useCase = ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)

        useCase.invoke(1L, 10L, OccurrenceEntryMeasure.DISTANCE_METERS, 500.0)

        val target = targets.requests.single().target
        assertEquals(LoadKind.BODYWEIGHT, target.loadKind)
        assertNull(target.weightKg)
    }

    @Test
    fun refusesOnceAnySetIsRecordedIncludingAWarmup() = runTest {
        val schedule = FakeSchedule(entry())
        val log = FakeLog(
            sets = listOf(
                WorkoutSet(
                    exerciseId = "bench-press",
                    reps = 5,
                    weightKg = null,
                    performedAtMillis = 1L,
                    isWarmup = true,
                    occurrenceId = 1L,
                    occurrenceEntryId = 10L
                )
            )
        )
        val targets = RecordingTargets()
        val error = assertFailsWith<ScheduleException> {
            ChangeOccurrenceEntryMeasureUseCase(schedule, log, targets)
                .invoke(1L, 10L, OccurrenceEntryMeasure.SECONDS, 30.0)
        }

        assertEquals("This workout has recorded sets", error.message)
        assertEquals(emptyList(), targets.requests)
    }

    @Test
    fun refusesASlotWithOrderedTargets() = runTest {
        val schedule = FakeSchedule(
            entry().copy(
                setTargets = com.hydrafit.app.core.domain.engine.SlotSetTargets.of(
                    listOf(SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 60.0))
                )
            )
        )
        val targets = RecordingTargets()
        assertFailsWith<ScheduleException> {
            ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)
                .invoke(1L, 10L, OccurrenceEntryMeasure.SECONDS, 30.0)
        }
        assertEquals(emptyList(), targets.requests)
    }

    @Test
    fun refusesASlotWithACluster() = runTest {
        val schedule = FakeSchedule(
            entry().copy(
                cluster = ClusterPrescription(
                    kind = ClusterKind.REST_PAUSE,
                    segments = listOf(
                        ClusterSegmentTarget(reps = 8, weightKg = 60.0),
                        ClusterSegmentTarget(reps = 6, weightKg = 60.0)
                    ),
                    restSeconds = 15
                )
            )
        )
        val targets = RecordingTargets()
        assertFailsWith<ScheduleException> {
            ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)
                .invoke(1L, 10L, OccurrenceEntryMeasure.SECONDS, 30.0)
        }
        assertEquals(emptyList(), targets.requests)
    }

    @Test
    fun refusesAStaleRevision() = runTest {
        val schedule = FakeSchedule(entry(), revision = 1)
        val targets = RecordingTargets(actualRevision = 2)
        assertFailsWith<ScheduleException> {
            ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)
                .invoke(1L, 10L, OccurrenceEntryMeasure.SECONDS, 30.0)
        }
        assertEquals(emptyList(), targets.requests)
    }

    @Test
    fun refusesAForceValueOutsideThePrescriptionBounds() = runTest {
        val schedule = FakeSchedule(entry())
        val targets = RecordingTargets()
        assertFailsWith<IllegalArgumentException> {
            ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)
                .invoke(1L, 10L, OccurrenceEntryMeasure.FORCE_NEWTONS, 0.05)
        }
        assertEquals(emptyList(), targets.requests)
    }

    @Test
    fun refusesAnOutOfBoundsValueBeforeWriting() = runTest {
        val schedule = FakeSchedule(entry())
        val targets = RecordingTargets()
        assertFailsWith<IllegalArgumentException> {
            ChangeOccurrenceEntryMeasureUseCase(schedule, FakeLog(), targets)
                .invoke(1L, 10L, OccurrenceEntryMeasure.REPETITIONS, 0.0)
        }
        assertEquals(emptyList(), targets.requests)
    }

    private fun entry(loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL) =
        OccurrenceEntry(
            id = 10L,
            position = 0,
            exerciseId = "bench-press",
            exerciseName = "Barbell Bench Press",
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            sets = 3,
            reps = 8,
            weightKg = 60.0,
            loadCapability = loadCapability,
            loadKind = LoadKind.EXTERNAL
        )

    private class RecordingTargets(
        private val capability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
        private val actualRevision: Int? = null
    ) : GuidedWorkoutTargetsRepository {
        val requests = mutableListOf<OccurrenceEntryMeasureRequest>()

        override suspend fun applyTargets(request: GuidedWorkoutTargetsRequest) = error("unused")

        override suspend fun changeEntryMeasure(request: OccurrenceEntryMeasureRequest) {
            if (actualRevision != null && request.occurrenceRevision != actualRevision) {
                throw ScheduleException("That workout changed; refresh and try again")
            }
            WorkoutLoadPolicy.validate(capability, request.target.loadKind, request.target.weightKg)
            requests.add(request)
        }
    }

    private class FakeLog(private val sets: List<WorkoutSet> = emptyList()) : WorkoutLogRepository {
        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = sets

        override suspend fun add(set: WorkoutSet) = error("unused")
        override suspend fun addCluster(
            set: WorkoutSet,
            cluster: com.hydrafit.app.core.domain.workout.WorkoutCluster
        ) = error("unused")

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<com.hydrafit.app.core.domain.workout.PerformedClusterSegment>
        ) = error("unused")

        override suspend fun assignSession(setId: Long, sessionId: String) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = error(
            "unused"
        )

        override suspend fun all(): List<WorkoutSet> = error("unused")
        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = error("unused")
        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())
        override suspend fun loggedSets(): List<LoggedSet> = error("unused")

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = error("unused")
    }

    private class FakeSchedule(private val entry: OccurrenceEntry, revision: Int = 1) :
        WorkoutScheduleRepository {
        private val occurrence = WorkoutOccurrence(
            id = 1L,
            activationId = 2L,
            activationWorkoutId = 3L,
            queuePosition = 0,
            status = OccurrenceStatus.PENDING,
            revision = revision,
            entries = listOf(entry)
        )
        private val state = WorkoutScheduleState(
            activeActivationId = 2L,
            selectedOccurrenceId = 1L,
            legacyFallbackEnabled = false
        )

        override suspend fun scheduleState(): WorkoutScheduleState = state
        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? =
            occurrence.takeIf { it.id == id }

        override fun observeScheduleState(): Flow<WorkoutScheduleState> = flowOf(state)
        override fun observeActiveActivation(): Flow<TrainingActivation?> = flowOf(null)
        override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
            flowOf(listOf(occurrence))

        override suspend fun setScheduleState(state: WorkoutScheduleState) = error("unused")
        override suspend fun activeActivation(): TrainingActivation? = null
        override suspend fun getActivation(id: Long): TrainingActivation? = null
        override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> =
            listOf(occurrence)

        override suspend fun acceptAndActivate(
            acceptedPlan: AcceptedPlan?,
            activation: TrainingActivation,
            scheduledEpochDays: List<Long?>,
            replaceActive: Boolean
        ): Long = error("unused")

        override suspend fun resolveOccurrence(
            occurrenceId: Long,
            expectedRevision: Int,
            status: OccurrenceStatus,
            resolvedAtMillis: Long,
            entries: List<OccurrenceEntry>
        ): WorkoutScheduleState = error("unused")

        override suspend fun updateActivationHeader(activation: TrainingActivation) =
            error("unused")
        override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = error("unused")
        override suspend fun replaceOccurrenceEntries(
            occurrenceId: Long,
            entries: List<OccurrenceEntry>
        ) = error("unused")

        override suspend fun deleteOccurrencesForActivation(activationId: Long) = error("unused")
        override suspend fun isTemplateReferenced(templateId: Long): Boolean = false
    }
}
