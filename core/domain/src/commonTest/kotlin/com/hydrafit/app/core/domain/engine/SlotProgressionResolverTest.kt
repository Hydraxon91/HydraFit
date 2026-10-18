package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val NOW = 1_000_000_000L
private const val EXERCISE = "bench-press"

/** The policy both the request and its recorded targets share, unless a test names its own. */
private val DEFAULT_POLICY = SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 2.5)

private fun recordedTarget(
    reps: Int,
    weightKg: Double,
    sets: Int = 3,
    isDeload: Boolean = false,
    policy: SlotProgressionPolicy = DEFAULT_POLICY
) = WorkoutPrescriptionSnapshot(
    exerciseId = EXERCISE,
    sets = sets,
    reps = reps,
    loadKind = LoadKind.EXTERNAL,
    weightKg = weightKg,
    isDeload = isDeload,
    progressionPolicy = policy
)

/** Builds recorded sessions in time order so the trailing run is unambiguous. */
private class RecordedHistory {
    private val sets = mutableListOf<WorkoutSet>()
    private val sessions = mutableListOf<WorkoutSession>()
    private var nextSetId = 1L
    private var endedAt = 1_000_000L

    /** One closed session where [met] of the target's sets reached its rep count and load. */
    fun exposure(target: WorkoutPrescriptionSnapshot, met: Int): RecordedHistory {
        val end = endedAt
        val sessionId = "session-$end"
        sessions += WorkoutSession(
            id = sessionId,
            startedAtMillis = end - 3_600_000L,
            endedAtMillis = end,
            localEpochDay = 0
        )
        repeat(target.sets) { index ->
            val reached = index < met
            sets += WorkoutSet(
                id = nextSetId++,
                exerciseId = target.exerciseId,
                reps = target.reps,
                weightKg = if (reached) {
                    target.weightKg
                } else {
                    requireNotNull(target.weightKg) - 1.0
                },
                performedAtMillis = end - 1_800_000L + index,
                sessionId = sessionId,
                prescriptionSnapshot = target
            )
        }
        endedAt += 86_400_000L
        return this
    }

    /** A closed session with no recorded target at all: legacy work, which stays unjudgeable. */
    fun untargetedExposure(): RecordedHistory {
        val end = endedAt
        val sessionId = "session-$end"
        sessions += WorkoutSession(
            id = sessionId,
            startedAtMillis = end - 3_600_000L,
            endedAtMillis = end,
            localEpochDay = 0
        )
        sets += WorkoutSet(
            id = nextSetId++,
            exerciseId = EXERCISE,
            reps = 5,
            weightKg = 100.0,
            performedAtMillis = end - 1_800_000L,
            sessionId = sessionId
        )
        endedAt += 86_400_000L
        return this
    }

    fun build(): ProgressionHistory = ProgressionHistory(sets.toList(), sessions.toList())
}

private fun request(
    policy: SlotProgressionPolicy = DEFAULT_POLICY,
    reps: Int? = 5,
    loadKg: Double? = 100.0,
    loadKind: LoadKind = LoadKind.EXTERNAL,
    ceilingKg: Double? = null,
    inventory: LoadingInventory? = null,
    isDeload: Boolean = false
) = SlotProgressionRequest(
    exerciseId = EXERCISE,
    policy = policy,
    sets = 3,
    reps = reps,
    loadKind = loadKind,
    loadKg = loadKg,
    equipmentCeilingKg = ceilingKg,
    loadingInventory = inventory,
    isDeload = isDeload
)

private fun kilogramsBarInventory() = LoadingInventory(
    unit = "KG",
    plates = listOf(OwnedPlate(weightTicks = 5_000, count = 2), OwnedPlate(2_500, count = 2)),
    bars = listOf(LoadingBar(id = "bar", name = "Bar", weightTicks = 20_000))
)

class SlotProgressionResolverTest {

    private val useCase = ResolveSlotProgressionUseCase()

    @Test
    fun threeComparableSuccessesAdvanceOneRepInsideTheRange() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(), history, NOW)

        assertEquals(SlotProgressionAction.ADVANCE_REPS, suggestion.action)
        assertEquals(SlotProgressionReason.SUCCESS_STREAK, suggestion.reason)
        assertEquals(6, suggestion.suggestedReps)
        assertEquals(100.0, suggestion.suggestedLoadKg)
        assertEquals(3, suggestion.comparableSuccesses)
    }

    @Test
    fun successAtTheTopOfTheRangeTakesTheLoadStepAndReturnsToTheFloor() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 8, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(reps = 8), history, NOW)

        assertEquals(SlotProgressionAction.ADVANCE_LOAD, suggestion.action)
        assertEquals(5, suggestion.suggestedReps)
        assertEquals(102.5, suggestion.suggestedLoadKg)
    }

    @Test
    fun threeComparableMissesAskForReviewInsteadOfRemovingLoad() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 0)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 1)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 0)
            .build()

        val suggestion = useCase(request(), history, NOW)

        assertEquals(SlotProgressionAction.REVIEW, suggestion.action)
        assertEquals(SlotProgressionReason.MISS_STREAK, suggestion.reason)
        assertNull(suggestion.suggestedReps)
        assertNull(suggestion.suggestedLoadKg)
        assertEquals(3, suggestion.comparableMisses)
    }

    @Test
    fun anUntargetedExposureBreaksTheRunWithoutCountingAsAMiss() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .untargetedExposure()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(), history, NOW)

        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(SlotProgressionReason.INSUFFICIENT_COMPARABLE_WORK, suggestion.reason)
        assertEquals(2, suggestion.comparableSuccesses)
        assertEquals(0, suggestion.comparableMisses)
    }

    @Test
    fun aChangedComparableTargetRestartsTheRun() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 6, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(), history, NOW)

        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(2, suggestion.comparableSuccesses)
    }

    @Test
    fun aChangedApprovedPolicyRestartsTheRun() {
        val otherPolicy = SlotProgressionPolicy(minReps = 6, maxReps = 10, incrementKg = 5.0)
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0, policy = otherPolicy), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(), history, NOW)

        // Work earned under another approved rule is never stitched into this policy's streak.
        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(2, suggestion.comparableSuccesses)
    }

    @Test
    fun aRecordedDeloadTargetIsNeverComparableWork() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0, isDeload = true), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0, isDeload = true), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0, isDeload = true), met = 3)
            .build()

        val suggestion = useCase(request(), history, NOW)

        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(SlotProgressionReason.INSUFFICIENT_COMPARABLE_WORK, suggestion.reason)
    }

    @Test
    fun aTimedOrNonExternalSlotIsNotEligible() {
        val history = RecordedHistory().build()

        val bodyweight = useCase(request(loadKind = LoadKind.BODYWEIGHT), history, NOW)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, bodyweight.reason)

        val timed = useCase(request(reps = null), history, NOW)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, timed.reason)

        val blankLoad = useCase(request(loadKg = null), history, NOW)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, blankLoad.reason)

        val zeroLoad = useCase(request(loadKg = 0.0), history, NOW)
        assertEquals(SlotProgressionReason.NOT_ELIGIBLE, zeroLoad.reason)
    }

    @Test
    fun aDeloadWeekPausesTheSuggestion() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(isDeload = true), history, NOW)

        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(SlotProgressionReason.PAUSED_DELOAD, suggestion.reason)
    }

    @Test
    fun aNamedIdentityMissingFromTheInventoryHoldsInsteadOfGuessing() {
        val policy = SlotProgressionPolicy(
            minReps = 5,
            maxReps = 8,
            incrementKg = 2.5,
            equipmentId = "ghost-bar"
        )
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 8, weightKg = 100.0, policy = policy), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 100.0, policy = policy), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 100.0, policy = policy), met = 3)
            .build()

        val suggestion = useCase(
            request(policy = policy, reps = 8, inventory = kilogramsBarInventory()),
            history,
            NOW
        )

        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(SlotProgressionReason.EQUIPMENT_UNRESOLVED, suggestion.reason)
    }

    @Test
    fun aNamedGridSnapsTheStepOntoTheNextLoadableWeight() {
        val policy = SlotProgressionPolicy(
            minReps = 5,
            maxReps = 8,
            incrementKg = 2.5,
            equipmentId = "bar"
        )
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 8, weightKg = 20.0, policy = policy), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 20.0, policy = policy), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 20.0, policy = policy), met = 3)
            .build()

        val suggestion = useCase(
            request(policy = policy, reps = 8, loadKg = 20.0, inventory = kilogramsBarInventory()),
            history,
            NOW
        )

        // 22.5 kg is not loadable from a 20 kg bar with 5 kg and 2.5 kg pairs; 25 kg is the next one.
        assertEquals(SlotProgressionAction.ADVANCE_LOAD, suggestion.action)
        assertEquals(5, suggestion.suggestedReps)
        assertEquals(25.0, suggestion.suggestedLoadKg)
    }

    @Test
    fun aStepBeyondAKnownCeilingHolds() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 8, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 8, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(reps = 8, ceilingKg = 101.0), history, NOW)

        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(SlotProgressionReason.LOAD_CEILING_REACHED, suggestion.reason)
    }

    @Test
    fun belowThresholdWorkHoldsWithTheObservedRun() {
        val history = RecordedHistory()
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .exposure(recordedTarget(reps = 5, weightKg = 100.0), met = 3)
            .build()

        val suggestion = useCase(request(), history, NOW)

        assertEquals(SlotProgressionAction.HOLD, suggestion.action)
        assertEquals(SlotProgressionReason.INSUFFICIENT_COMPARABLE_WORK, suggestion.reason)
        assertEquals(2, suggestion.comparableSuccesses)
        assertEquals(false, suggestion.changesTarget)
    }
}
