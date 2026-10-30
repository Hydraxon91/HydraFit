package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ApplyGuidedWorkoutTargetsUseCase
import com.hydrafit.app.core.domain.schedule.GuidedTargetField
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetOverride
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargets
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetsRepository
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetsRequest
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

private const val EXERCISE = "barbell-bench-press"
private const val NOW = 1_000_000_000L
private const val OCCURRENCE_ID = 1L
private const val ENTRY_ID = 11L

private val POLICY = SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 2.5)
private val ORDERED_POLICY = SlotProgressionPolicy(minReps = 6, maxReps = 10, incrementKg = 2.5)

private fun entry(
    id: Long = ENTRY_ID,
    reps: Int? = 6,
    weightKg: Double? = 80.0,
    sets: Int = 3,
    loadKind: LoadKind = LoadKind.EXTERNAL,
    durationSeconds: Int? = null,
    policy: SlotProgressionPolicy? = POLICY,
    setTargets: SlotSetTargets? = null,
    guidedTargets: GuidedWorkoutTargets? = null,
    cluster: ClusterPrescription? = null,
    requiredEquipment: Set<EquipmentTag> = setOf(EquipmentTag.BARBELL)
) = OccurrenceEntry(
    id = id,
    sourceActivationEntryId = 21L,
    position = 0,
    exerciseId = EXERCISE,
    exerciseName = "Bench Press",
    movementPattern = MovementPattern.HORIZONTAL_PUSH,
    requiredEquipment = requiredEquipment,
    sets = sets,
    reps = reps,
    durationSeconds = durationSeconds,
    weightKg = weightKg,
    loadKind = loadKind,
    progressionPolicy = policy,
    setTargets = setTargets,
    guidedTargets = guidedTargets,
    cluster = cluster
)

private fun schedule(
    entry: OccurrenceEntry,
    weekNumber: Int? = 1,
    status: OccurrenceStatus = OccurrenceStatus.PENDING,
    selected: Boolean = true
): FakeSchedule = schedule(
    entries = listOf(entry),
    weekNumber = weekNumber,
    status = status,
    selected = selected
)

private fun schedule(
    entries: List<OccurrenceEntry>,
    weekNumber: Int? = 1,
    status: OccurrenceStatus = OccurrenceStatus.PENDING,
    selected: Boolean = true
): FakeSchedule {
    val occurrence = WorkoutOccurrence(
        id = OCCURRENCE_ID,
        activationId = 1L,
        activationWorkoutId = 1L,
        queuePosition = 0,
        status = status,
        entries = entries
    )
    val activation = TrainingActivation(
        id = 1L,
        name = "Block",
        createdAtMillis = 0L,
        startEpochDay = 0L,
        mode = ScheduleMode.SEQUENCE,
        weekdays = emptySet(),
        status = ActivationStatus.ACTIVE,
        weekNumber = weekNumber
    )
    return FakeSchedule(
        occurrence = occurrence,
        state = WorkoutScheduleState(
            activeActivationId = 1L,
            selectedOccurrenceId = if (selected) OCCURRENCE_ID else null,
            legacyFallbackEnabled = false
        ),
        activation = activation
    )
}

private fun scalarHistory(
    reps: Int = 6,
    load: Double = 80.0,
    sets: Int = 3,
    policy: SlotProgressionPolicy = POLICY,
    sessions: Int = 3
): ProgressionHistory {
    val recordedSets = mutableListOf<WorkoutSet>()
    val recordedSessions = mutableListOf<WorkoutSession>()
    var setId = 1L
    var time = 1_000L
    repeat(sessions) { index ->
        val sessionId = "session-$index"
        recordedSessions += WorkoutSession(
            id = sessionId,
            startedAtMillis = time,
            endedAtMillis = time + 100L,
            localEpochDay = 0L
        )
        repeat(sets) {
            recordedSets += WorkoutSet(
                id = setId++,
                exerciseId = EXERCISE,
                reps = reps,
                weightKg = load,
                performedAtMillis = time + it,
                sessionId = sessionId,
                prescriptionSnapshot = WorkoutPrescriptionSnapshot(
                    exerciseId = EXERCISE,
                    sets = sets,
                    reps = reps,
                    loadKind = LoadKind.EXTERNAL,
                    weightKg = load,
                    isDeload = false,
                    progressionPolicy = policy
                )
            )
        }
        time += 1_000L
    }
    return ProgressionHistory(sets = recordedSets, sessions = recordedSessions)
}

private fun orderedHistory(
    targets: List<Pair<Int, Double>>,
    policy: SlotProgressionPolicy = ORDERED_POLICY,
    sessions: Int = 3,
    loadKind: LoadKind = LoadKind.EXTERNAL
): ProgressionHistory {
    val recordedSets = mutableListOf<WorkoutSet>()
    val recordedSessions = mutableListOf<WorkoutSession>()
    var setId = 1L
    var time = 1_000L
    repeat(sessions) { index ->
        val sessionId = "session-$index"
        recordedSessions += WorkoutSession(
            id = sessionId,
            startedAtMillis = time,
            endedAtMillis = time + 100L,
            localEpochDay = 0L
        )
        targets.forEachIndexed { setIndex, (reps, load) ->
            recordedSets += WorkoutSet(
                id = setId++,
                exerciseId = EXERCISE,
                reps = reps,
                weightKg = load,
                performedAtMillis = time + setIndex,
                sessionId = sessionId,
                prescriptionSnapshot = WorkoutPrescriptionSnapshot(
                    exerciseId = EXERCISE,
                    sets = targets.size,
                    reps = reps,
                    loadKind = loadKind,
                    weightKg = load,
                    isDeload = false,
                    progressionPolicy = policy,
                    setIndex = setIndex
                )
            )
        }
        time += 1_000L
    }
    return ProgressionHistory(sets = recordedSets, sessions = recordedSessions)
}

private fun suggest(
    schedule: FakeSchedule,
    history: ProgressionHistory,
    trackKey: String? = "T7:0:0",
    inventory: LoadingInventory? = null,
    maxWeights: Map<EquipmentTag, Double> = emptyMap(),
    periodization: PeriodizationConfig = PeriodizationConfig()
) = SuggestSlotProgressionUseCase(
    schedule = schedule,
    contexts = FakeContexts(
        contexts = mapOf(ENTRY_ID to SlotProgressionContext(trackKey, history)),
        inventory = inventory,
        maxWeights = maxWeights
    ),
    timeProvider = TimeProvider { NOW },
    resolver = ResolveSlotProgressionUseCase(),
    periodization = periodization
)

class SuggestSlotProgressionUseCaseTest {

    @Test
    fun anEntryWithoutAPolicyHasNoSuggestion() = runTest {
        val result = suggest(schedule(entry(policy = null)), scalarHistory())
            .invoke(OCCURRENCE_ID, ENTRY_ID)

        assertNull(result)
    }

    @Test
    fun aSlotWithoutAStableTrackKeyHolds() = runTest {
        val result = assertNotNull(
            suggest(schedule(entry()), scalarHistory(), trackKey = null)
                .invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.INSUFFICIENT_COMPARABLE_WORK, result.reason)
    }

    @Test
    fun aClusterSlotHolds() = runTest {
        val cluster = ClusterPrescription(
            kind = ClusterKind.DROP_SET,
            segments = listOf(
                ClusterSegmentTarget(6, 80.0),
                ClusterSegmentTarget(4, 60.0)
            ),
            restSeconds = 15
        )
        val scenario = schedule(entry(cluster = cluster))

        val result =
            assertNotNull(suggest(scenario, scalarHistory()).invoke(OCCURRENCE_ID, ENTRY_ID))

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, result.reason)
        assertNull(result.suggestedReps)
    }

    @Test
    fun aBodyweightSlotHolds() = runTest {
        val scenario = schedule(entry(weightKg = null, loadKind = LoadKind.BODYWEIGHT))

        val result =
            assertNotNull(suggest(scenario, scalarHistory()).invoke(OCCURRENCE_ID, ENTRY_ID))

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, result.reason)
    }

    @Test
    fun aTimedSlotHolds() = runTest {
        val scenario = schedule(
            entry(reps = null, weightKg = null, durationSeconds = 60)
        )

        val result =
            assertNotNull(suggest(scenario, scalarHistory()).invoke(OCCURRENCE_ID, ENTRY_ID))

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, result.reason)
    }

    @Test
    fun aHandEditedMixedScalarSlotHolds() = runTest {
        val edited = GuidedWorkoutTargets(
            overrides = listOf(
                GuidedWorkoutTargetOverride(
                    setIndex = 1,
                    target = SetTarget(reps = 7, loadKind = LoadKind.EXTERNAL, weightKg = 80.0)
                )
            )
        )
        val scenario = schedule(entry(guidedTargets = edited))

        val result =
            assertNotNull(suggest(scenario, scalarHistory()).invoke(OCCURRENCE_ID, ENTRY_ID))

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, result.reason)
    }

    @Test
    fun aPolicyEquipmentAbsentFromTheInventoryHolds() = runTest {
        val policy = POLICY.copy(equipmentId = "missing-bar")
        val scenario = schedule(entry(policy = policy))
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(2_000))
            )
        )

        val result = assertNotNull(
            suggest(scenario, scalarHistory(policy = policy), inventory = inventory)
                .invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.EQUIPMENT_UNRESOLVED, result.reason)
    }

    @Test
    fun aDeloadWeekHolds() = runTest {
        val scenario = schedule(entry(reps = 8), weekNumber = 4)

        val result = assertNotNull(
            suggest(scenario, scalarHistory(reps = 8)).invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.PAUSED_DELOAD, result.reason)
    }

    @Test
    fun advancesRepsAtTheSuccessStreak() = runTest {
        val result = assertNotNull(
            suggest(schedule(entry()), scalarHistory()).invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.ADVANCE_REPS, result.action)
        assertEquals(SlotProgressionReason.SUCCESS_STREAK, result.reason)
        assertEquals(6, result.currentReps)
        assertEquals(7, result.suggestedReps)
        assertEquals(80.0, result.suggestedLoadKg)
        assertEquals(3, result.comparableSuccesses)
        assertTrue(result.canApply)
    }

    @Test
    fun advancesLoadOnTheEquipmentGridAtTheRangeTop() = runTest {
        val policy = POLICY.copy(equipmentId = "db")
        val scenario = schedule(
            entry(
                reps = 8,
                weightKg = 20.0,
                policy = policy,
                requiredEquipment = setOf(EquipmentTag.DUMBBELL)
            )
        )
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 22_500, 25_000)
                )
            )
        )

        val result = assertNotNull(
            suggest(
                scenario,
                scalarHistory(reps = 8, load = 20.0, policy = policy),
                inventory = inventory,
                maxWeights = mapOf(EquipmentTag.DUMBBELL to 100.0)
            ).invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.ADVANCE_LOAD, result.action)
        assertEquals(5, result.suggestedReps)
        assertEquals(22.5, result.suggestedLoadKg)
        assertTrue(result.canApply)
    }

    @Test
    fun anEquipmentCeilingBelowTheNextStepHolds() = runTest {
        val policy = POLICY.copy(equipmentId = "db")
        val scenario = schedule(
            entry(
                reps = 8,
                weightKg = 20.0,
                policy = policy,
                requiredEquipment = setOf(EquipmentTag.DUMBBELL)
            )
        )
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 22_500, 25_000)
                )
            )
        )

        val result = assertNotNull(
            suggest(
                scenario,
                scalarHistory(reps = 8, load = 20.0, policy = policy),
                inventory = inventory,
                maxWeights = mapOf(EquipmentTag.DUMBBELL to 21.0)
            ).invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.LOAD_CEILING_REACHED, result.reason)
        assertEquals(false, result.canApply)
    }

    @Test
    fun anOrderedSlotAdvancesEverySet() = runTest {
        val targets = listOf(8 to 60.0, 8 to 65.0, 8 to 70.0)
        val ordered = SlotSetTargets.of(
            targets.map { (reps, load) ->
                SetTarget(reps = reps, loadKind = LoadKind.EXTERNAL, weightKg = load)
            }
        )
        val scenario = schedule(
            entry(reps = 8, weightKg = 60.0, setTargets = ordered, policy = ORDERED_POLICY)
        )

        val result = assertNotNull(
            suggest(scenario, orderedHistory(targets))
                .invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.ADVANCE_REPS, result.action)
        val suggestion = assertNotNull(result.ordered?.suggested)
        assertEquals(listOf(9, 9, 9), suggestion.targets.map { it.reps })
        assertEquals(listOf(60.0, 65.0, 70.0), suggestion.targets.map { it.weightKg })
        assertTrue(result.canApply)
    }

    @Test
    fun anOrderedAddedLoadSlotHoldsInsteadOfScoringOrReviewing() = runTest {
        val targets = listOf(8 to 10.0, 8 to 12.0)
        val ordered = SlotSetTargets.of(
            targets.map { (reps, load) ->
                SetTarget(reps = reps, loadKind = LoadKind.ADDED, weightKg = load)
            }
        )
        val scenario = schedule(
            entry(
                reps = 8,
                weightKg = 10.0,
                loadKind = LoadKind.ADDED,
                sets = 2,
                setTargets = ordered,
                policy = ORDERED_POLICY
            )
        )

        val result = assertNotNull(
            suggest(
                scenario,
                orderedHistory(targets, loadKind = LoadKind.ADDED)
            ).invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, result.reason)
        assertNull(result.ordered?.suggested)
        assertEquals(false, result.canApply)
    }

    @Test
    fun theInjectedDeloadWeekPausesAnOtherwiseEligibleSlot() = runTest {
        // Week 2 is ordinary under the defaults; a configured deload week of 2 must pause it.
        val scenario = schedule(entry(), weekNumber = 2)
        val result = assertNotNull(
            suggest(scenario, scalarHistory(), periodization = PeriodizationConfig(deloadWeek = 2))
                .invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.PAUSED_DELOAD, result.reason)
    }

    @Test
    fun aTwoEntryOccurrenceIsDecidedFromOneContextRead() = runTest {
        val first = entry(id = ENTRY_ID)
        val second = entry(id = 12L, weightKg = 60.0)
        val contexts = FakeContexts(
            contexts = mapOf(
                ENTRY_ID to SlotProgressionContext("T7:0:0", scalarHistory()),
                12L to SlotProgressionContext("T7:0:0", scalarHistory(load = 60.0))
            )
        )
        val useCase = SuggestSlotProgressionUseCase(
            schedule = schedule(listOf(first, second)),
            contexts = contexts,
            timeProvider = TimeProvider { NOW },
            resolver = ResolveSlotProgressionUseCase()
        )

        val decisions = useCase.decisionsFor(OCCURRENCE_ID)

        assertEquals(setOf(ENTRY_ID, 12L), decisions.keys)
        assertEquals(1, contexts.reads)
    }

    @Test
    fun theEquipmentCeilingIsTheLowerOfTheNamedImplementAndRequiredEquipment() = runTest {
        val policy = POLICY.copy(equipmentId = "db")
        val scenario = schedule(
            entry(
                reps = 8,
                weightKg = 20.0,
                policy = policy,
                requiredEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BARBELL)
            )
        )
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 22_500, 25_000)
                )
            )
        )

        // The named dumbbell allows 40 kg, but the required barbell records 21 kg; the lower ceiling
        // must win, so the 22.5 kg step is blocked.
        val result = assertNotNull(
            suggest(
                scenario,
                scalarHistory(reps = 8, load = 20.0, policy = policy),
                inventory = inventory,
                maxWeights = mapOf(
                    EquipmentTag.DUMBBELL to 40.0,
                    EquipmentTag.BARBELL to 21.0
                )
            ).invoke(OCCURRENCE_ID, ENTRY_ID)
        )

        assertEquals(SlotProgressionAction.HOLD, result.action)
        assertEquals(SlotProgressionReason.LOAD_CEILING_REACHED, result.reason)
    }
}

class ApplySlotProgressionSuggestionUseCaseTest {

    private fun applyUseCase(
        schedule: FakeSchedule,
        log: FakeWorkoutLog,
        history: ProgressionHistory,
        tracks: RecordingTargets = RecordingTargets()
    ): Pair<ApplySlotProgressionSuggestionUseCase, RecordingTargets> {
        val suggestion = suggest(schedule, history)
        val applyGuided = ApplyGuidedWorkoutTargetsUseCase(schedule, log, tracks)
        return ApplySlotProgressionSuggestionUseCase(
            suggest = suggestion,
            schedule = schedule,
            log = log,
            applyGuidedTargets = applyGuided
        ) to tracks
    }

    @Test
    fun refusesWhenThereIsNoApplicableAdvance() = runTest {
        val (apply, tracks) = applyUseCase(
            schedule(entry()),
            FakeWorkoutLog(),
            ProgressionHistory()
        )

        assertFailsWith<ScheduleException> { apply(OCCURRENCE_ID, ENTRY_ID) }
        assertNull(tracks.request)
    }

    @Test
    fun refusesWhenTheSlotAlreadyHasRecordedWork() = runTest {
        val recorded = WorkoutSet(
            id = 1L,
            exerciseId = EXERCISE,
            reps = 6,
            weightKg = 80.0,
            performedAtMillis = 10L,
            occurrenceId = OCCURRENCE_ID,
            occurrenceEntryId = ENTRY_ID
        )
        val (apply, tracks) = applyUseCase(
            schedule(entry()),
            FakeWorkoutLog(listOf(recorded)),
            scalarHistory()
        )

        assertFailsWith<ScheduleException> { apply(OCCURRENCE_ID, ENTRY_ID) }
        assertNull(tracks.request)
    }

    @Test
    fun writesOneRepsPatchPerSetPosition() = runTest {
        val (apply, tracks) = applyUseCase(schedule(entry()), FakeWorkoutLog(), scalarHistory())

        apply(OCCURRENCE_ID, ENTRY_ID)

        val request = assertNotNull(tracks.request)
        assertEquals(3, request.patches.size)
        assertEquals(setOf(0, 1, 2), request.patches.keys)
        request.patches.values.forEach { patch ->
            assertEquals(setOf(GuidedTargetField.REPS), patch.fields)
            assertEquals(7, patch.target.reps)
            assertTrue(patch.laterClusterSegments.isEmpty())
        }
        assertEquals(false, request.resetRemaining)
        assertEquals(3, request.entrySets)
    }
}

private class FakeSchedule(
    private val occurrence: WorkoutOccurrence,
    private val state: WorkoutScheduleState,
    private val activation: TrainingActivation?
) : WorkoutScheduleRepository {
    override fun observeScheduleState(): Flow<WorkoutScheduleState> = flowOf(state)

    override fun observeActiveActivation(): Flow<TrainingActivation?> = flowOf(activation)

    override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
        flowOf(listOf(occurrence))

    override suspend fun scheduleState(): WorkoutScheduleState = state

    override suspend fun setScheduleState(state: WorkoutScheduleState) = notUsed()

    override suspend fun activeActivation(): TrainingActivation? = activation

    override suspend fun getActivation(id: Long): TrainingActivation? =
        activation?.takeIf { it.id == id }

    override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> =
        listOf(occurrence).filter { it.activationId == activationId }

    override suspend fun getOccurrence(id: Long): WorkoutOccurrence? =
        occurrence.takeIf { it.id == id }

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

    private fun notUsed(): Nothing = error("Not used by this test")
}

private class FakeContexts(
    private val contexts: Map<Long, SlotProgressionContext>,
    private val inventory: LoadingInventory? = null,
    private val maxWeights: Map<EquipmentTag, Double> = emptyMap()
) : SlotProgressionContextRepository {
    var reads: Int = 0
        private set

    override suspend fun inputsFor(occurrenceId: Long): SlotProgressionInputs {
        reads++
        return SlotProgressionInputs(contexts, inventory, maxWeights)
    }
}

private class FakeWorkoutLog(private val sets: List<WorkoutSet> = emptyList()) :
    WorkoutLogRepository {
    override suspend fun add(set: WorkoutSet) = notUsed()

    override suspend fun addCluster(
        set: WorkoutSet,
        cluster: com.hydrafit.app.core.domain.workout.WorkoutCluster
    ) = notUsed()

    override suspend fun appendClusterSegments(
        setId: Long,
        segments: List<com.hydrafit.app.core.domain.workout.PerformedClusterSegment>
    ) = notUsed()

    override suspend fun assignSession(setId: Long, sessionId: String) = notUsed()

    override suspend fun delete(id: Long) = notUsed()

    override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = notUsed()

    override suspend fun all(): List<WorkoutSet> = sets

    override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = notUsed()

    override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> =
        sets.filter { it.occurrenceId == occurrenceId }

    override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets)

    override suspend fun loggedSets() = notUsed()

    override fun loggedSetsFlow() = notUsed()

    override suspend fun clear() = notUsed()

    private fun notUsed(): Nothing = error("Not used by this test")
}

private class RecordingTargets : GuidedWorkoutTargetsRepository {
    var request: GuidedWorkoutTargetsRequest? = null

    override suspend fun applyTargets(request: GuidedWorkoutTargetsRequest) {
        this.request = request
    }
}
