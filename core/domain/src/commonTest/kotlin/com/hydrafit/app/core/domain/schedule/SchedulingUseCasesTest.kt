package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.LoadBaselineExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.dayOfWeekForEpochDay
import com.hydrafit.app.core.domain.time.daysFromCivil
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.WorkoutCluster
import com.hydrafit.app.core.domain.workout.WorkoutClusterSummary
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

class SchedulingUseCasesTest {

    @Test
    fun activatingRoutinePreservesClusterInActivationAndOccurrence() = runTest {
        val cluster = ClusterPrescription(
            kind = ClusterKind.DROP_SET,
            segments = listOf(ClusterSegmentTarget(8, 80.0), ClusterSegmentTarget(6, 60.0)),
            restSeconds = 15
        )
        val original = routine()
        val workout = original.workouts.first()
        val withCluster = original.copy(
            workouts = listOf(
                workout.copy(entries = listOf(workout.entries.first().copy(cluster = cluster)))
            )
        )
        val id = activateRoutine(withCluster, request(replaceActive = false))

        assertEquals(
            cluster,
            requireNotNull(
                scheduleRepository.getActivation(id)
            ).workouts.first().entries.first().cluster
        )
        assertEquals(cluster, scheduleRepository.occurrences(id).first().entries.first().cluster)
    }

    @Test
    fun freezesOccurrenceSlotCoversWorkingWarmupMatchingAndForeignSets() {
        // Any set attributed to the slot - working or warm-up - freezes it.
        assertTrue(
            workoutSet(occurrenceId = 10L, occurrenceEntryId = 20L, isWarmup = false)
                .freezesOccurrenceSlot(10L, 20L, "back-squat")
        )
        assertTrue(
            workoutSet(occurrenceId = 10L, occurrenceEntryId = 20L, isWarmup = true)
                .freezesOccurrenceSlot(10L, 20L, "back-squat")
        )
        // An occurrence-level set with no resolved entry freezes only the matching exercise's slot.
        assertTrue(
            workoutSet(occurrenceId = 10L, occurrenceEntryId = null, isWarmup = false)
                .freezesOccurrenceSlot(10L, 20L, "back-squat")
        )
        assertTrue(
            !workoutSet(occurrenceId = 10L, occurrenceEntryId = null, isWarmup = false)
                .freezesOccurrenceSlot(10L, 20L, "bench-press")
        )
        // The entry id is authoritative: an entry-linked set freezes its entry even when its
        // occurrence id is missing or disagrees with the slot's occurrence.
        assertTrue(
            workoutSet(occurrenceId = 11L, occurrenceEntryId = 20L, isWarmup = false)
                .freezesOccurrenceSlot(10L, 20L, "back-squat")
        )
        assertTrue(
            workoutSet(occurrenceId = null, occurrenceEntryId = 20L, isWarmup = false)
                .freezesOccurrenceSlot(10L, 20L, "back-squat")
        )
        // Another entry's set and a fully unattached set leave the slot untouched.
        assertTrue(
            !workoutSet(occurrenceId = 10L, occurrenceEntryId = 21L, isWarmup = false)
                .freezesOccurrenceSlot(10L, 20L, "back-squat")
        )
        assertTrue(
            !workoutSet(occurrenceId = null, occurrenceEntryId = null, isWarmup = false)
                .freezesOccurrenceSlot(10L, 20L, "back-squat")
        )
    }

    private fun workoutSet(
        occurrenceId: Long?,
        occurrenceEntryId: Long?,
        isWarmup: Boolean,
        exerciseId: String = "back-squat"
    ): WorkoutSet = WorkoutSet(
        exerciseId = exerciseId,
        reps = 5,
        weightKg = null,
        performedAtMillis = 1L,
        isWarmup = isWarmup,
        occurrenceId = occurrenceId,
        occurrenceEntryId = occurrenceEntryId
    )

    @Test
    fun occurrenceReplacementUsesItsOwnLoadAndNeverMutatesTheMasterOrOtherOccurrence() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val master = scheduleRepository.getActivation(activationId)
        val occurrences = scheduleRepository.occurrences(activationId)
        val occurrence = occurrences.first()
        val replacementExercise = catalog.all().first().copy(
            id = "alternative",
            name = "Alternative"
        )
        val replacementCatalog = object : ExerciseCatalog {
            override suspend fun all() = catalog.all() + replacementExercise
        }
        val writer = object : OccurrenceReplacementRepository {
            override suspend fun replaceUntouchedEntry(
                occurrenceId: Long,
                expectedRevision: Int,
                replacement: OccurrenceEntry,
                nowMillis: Long
            ) {
                val current = requireNotNull(scheduleRepository.getOccurrence(occurrenceId))
                assertEquals(expectedRevision, current.revision)
                scheduleRepository.replaceOccurrenceEntries(occurrenceId, listOf(replacement))
                scheduleRepository.updateOccurrence(
                    current.copy(entries = listOf(replacement), revision = expectedRevision + 1)
                )
            }
        }
        val useCase = replacementUseCase(replacementCatalog, writer)
        val inputs = replacementInputs().copy(suggestedWeightsKg = mapOf("alternative" to 200.0))
        val choices = useCase.candidates(occurrence.id, occurrence.entries.single().id, inputs)
        val candidate = choices.alternatives.single()
        assertEquals(145.0, candidate.weightKg)
        assertEquals(occurrence.entries.single().sets, candidate.sets)
        assertEquals(occurrence.entries.single().reps, candidate.reps)
        useCase(choices, "alternative", inputs)
        assertEquals(candidate, scheduleRepository.getOccurrence(occurrence.id)?.entries?.single())
        assertEquals(master, scheduleRepository.getActivation(activationId))
        assertEquals(occurrences.last(), scheduleRepository.getOccurrence(occurrences.last().id))
        assertTrue(logRepository.setsForOccurrence(occurrence.id).isEmpty())
    }

    @Test
    fun replacementRejectsRecordedWarmupStaleRevisionAndUnavailableCandidates() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        val alternative = catalog.all().first().copy(id = "alternative", name = "Alternative")
        val replacementCatalog = object : ExerciseCatalog {
            override suspend fun all() = catalog.all() + alternative
        }
        var writes = 0
        val writer = object : OccurrenceReplacementRepository {
            override suspend fun replaceUntouchedEntry(
                occurrenceId: Long,
                expectedRevision: Int,
                replacement: OccurrenceEntry,
                nowMillis: Long
            ) {
                writes++
            }
        }
        val useCase = replacementUseCase(replacementCatalog, writer)
        val inputs = replacementInputs()
        val choices = useCase.candidates(occurrence.id, occurrence.entries.single().id, inputs)
        assertNull(choices.alternatives.single().weightKg)
        assertFailsWith<OccurrenceReplacementException> {
            useCase(
                choices,
                alternative.id,
                inputs.copy(excludedExerciseIds = setOf(alternative.id))
            )
        }
        val unavailable = useCase.candidates(
            occurrence.id,
            choices.original.id,
            inputs.copy(availableEquipment = emptySet())
        )
        assertTrue(unavailable.alternatives.isEmpty())
        startOccurrence(occurrence.id)
        assertFailsWith<OccurrenceReplacementException> { useCase(choices, alternative.id, inputs) }
        val startedChoices = useCase.candidates(occurrence.id, choices.original.id, inputs)
        assertEquals(choices.original, startedChoices.original)
        logRepository.add(
            WorkoutSet(
                exerciseId = choices.original.exerciseId,
                reps = 3,
                weightKg = null,
                performedAtMillis = now,
                isWarmup = true,
                occurrenceId = occurrence.id,
                occurrenceEntryId = choices.original.id
            )
        )
        val error = assertFailsWith<OccurrenceReplacementException> {
            useCase(startedChoices, alternative.id, inputs)
        }
        assertEquals(OccurrenceReplacementFailure.RECORDED_SLOT, error.reason)
        assertEquals(0, writes)
    }

    private fun replacementInputs() = com.hydrafit.app.core.domain.engine.PlanRequest(
        daysPerWeek = 2,
        availableEquipment = setOf(EquipmentTag("barbell")),
        muscleFatigue = emptyMap(),
        nowMillis = now
    )

    private fun replacementUseCase(
        catalog: ExerciseCatalog,
        writer: OccurrenceReplacementRepository
    ) = ReplaceOccurrenceExerciseUseCase(
        scheduleRepository,
        logRepository,
        catalog,
        writer,
        com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine(catalog),
        com.hydrafit.app.core.domain.engine.SuggestedWeightConfig(),
        com.hydrafit.app.core.domain.engine.PeriodizationConfig()
    )

    private var now = 1_000L
    private val timeProvider = TimeProvider { now }
    private val catalog = FakeCatalog()
    private val scheduleRepository = FakeWorkoutScheduleRepository()
    private val logRepository = FakeWorkoutLogRepository()

    private val preview = PreviewWorkoutScheduleUseCase()
    private val createActivation =
        CreateTrainingActivationUseCase(scheduleRepository, preview, timeProvider)
    private val activateRoutine = ActivateRoutineUseCase(catalog, createActivation)

    private val finish = FinishWorkoutOccurrenceUseCase(
        scheduleRepository,
        logRepository,
        timeProvider
    )
    private val skip = SkipWorkoutOccurrenceUseCase(
        scheduleRepository,
        logRepository,
        timeProvider
    )
    private val finishBlock = FinishTrainingBlockUseCase(scheduleRepository, timeProvider)
    private val cancelBlock = CancelTrainingActivationUseCase(scheduleRepository, timeProvider)
    private val move = MoveWorkoutOccurrenceUseCase(scheduleRepository, preview)
    private val switchMode = SwitchScheduleModeUseCase(scheduleRepository, preview)
    private val edit = EditUnstartedOccurrenceUseCase(scheduleRepository, logRepository, catalog)
    private val startOccurrence = StartWorkoutOccurrenceUseCase(scheduleRepository, timeProvider)

    // 2026-10-09 is a Friday.
    private val friday = daysFromCivil(2026, 10, 9)
    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    @Test
    fun previewsChosenWeekdaysAcrossCalendarBoundariesWithoutCompression() {
        val dates = preview(
            count = 5,
            startEpochDay = friday,
            mode = ScheduleMode.WEEKDAY,
            weekdays = weekdays,
            startToday = true
        )

        assertEquals(friday, dates[0])
        assertEquals(DayOfWeek.FRIDAY, dayOfWeekForEpochDay(requireNotNull(dates[0])))
        assertEquals(daysFromCivil(2026, 10, 12), dates[1])
        assertEquals(daysFromCivil(2026, 10, 13), dates[2])
        assertEquals(daysFromCivil(2026, 10, 14), dates[3])
        assertEquals(daysFromCivil(2026, 10, 15), dates[4])
    }

    @Test
    fun previewsSequenceModeWithoutDates() {
        assertEquals(listOf(null, null, null), preview(3, friday, ScheduleMode.SEQUENCE, weekdays))
    }

    @Test
    fun activatingARoutineSnapshotsPrescriptionsAndSelectsTheFirstWorkout() = runTest {
        val id = activateRoutine(routine(), request(replaceActive = false))

        val activation = requireNotNull(scheduleRepository.getActivation(id))
        assertEquals(ActivationStatus.ACTIVE, activation.status)
        assertEquals(weekdays, activation.weekdays)
        assertEquals("Barbell Bench Press", activation.workouts[0].entries[0].exerciseName)
        assertEquals(
            MovementPattern.HORIZONTAL_PUSH,
            activation.workouts[0].entries[0].movementPattern
        )
        assertEquals(
            mapOf(com.hydrafit.app.core.domain.fatigue.MuscleGroup.CHEST_UPPER to 0.6),
            activation.workouts[0].entries[0].involvements
        )

        val occurrences = scheduleRepository.occurrences(id)
        assertEquals(listOf(0, 1), occurrences.map { it.queuePosition })
        assertEquals(
            listOf(friday, daysFromCivil(2026, 10, 12)),
            occurrences.map {
                it.scheduledEpochDay
            }
        )
        assertTrue(occurrences.all { it.status == OccurrenceStatus.PENDING })
        assertEquals(occurrences[0].id, scheduleRepository.scheduleState().selectedOccurrenceId)
        assertEquals(id, scheduleRepository.scheduleState().activeActivationId)
    }

    @Test
    fun activatingATimedSlotSnapshotsItsSecondsOntoTheActivationAndItsOccurrence() = runTest {
        val timed = routine().copy(
            workouts = listOf(
                RoutineWorkout(
                    position = 0,
                    name = "Upper",
                    focus = SplitFocus.UPPER,
                    entries = listOf(
                        RoutineEntry(
                            exerciseId = "bench-press",
                            sets = 4,
                            reps = null,
                            durationSeconds = 90,
                            weightKg = 80.0
                        )
                    )
                )
            )
        )

        val id = activateRoutine(timed, request(replaceActive = false))

        val activationEntry = requireNotNull(scheduleRepository.getActivation(id))
            .workouts[0].entries.single()
        assertNull(activationEntry.reps)
        assertEquals(90, activationEntry.durationSeconds)
        assertEquals(80.0, activationEntry.weightKg)

        // The occurrence copies the frozen slot, so its seconds travel with it.
        val occurrenceEntry = scheduleRepository.occurrences(id).first().entries.single()
        assertNull(occurrenceEntry.reps)
        assertEquals(90, occurrenceEntry.durationSeconds)
        assertEquals(80.0, occurrenceEntry.weightKg)
    }

    @Test
    fun freezesTheSourcePlansSlotReasoningOntoTheActivatedEntries() = runTest {
        val recorded = slotExplanation()

        val id = activateRoutine(
            routine(),
            request(replaceActive = false),
            explanationSource = acceptedPlanWithReasoning(recorded)
        )

        val activation = requireNotNull(scheduleRepository.getActivation(id))
        assertEquals(recorded, activation.workouts[0].entries[0].explanation)
        // A workout with no matching plan day keeps no reasoning rather than borrowing one.
        assertNull(activation.workouts[1].entries[0].explanation)
    }

    @Test
    fun aManualRoutineHasNoPlannerReasoning() = runTest {
        val id = activateRoutine(routine(), request(replaceActive = false))

        val activation = requireNotNull(scheduleRepository.getActivation(id))
        assertNull(activation.workouts[0].entries[0].explanation)
    }

    private fun slotExplanation() = ExerciseTargetExplanation(
        baseline = LoadBaselineExplanation(
            source = LoadBaselineSource.RECENT_PERFORMANCE,
            recentEstimateKg = 100.0,
            qualifyingSetCount = 3,
            baselineKg = 100.0
        ),
        setCount = SetCountExplanation(
            baseSets = 3,
            deloadApplied = false,
            deloadVolumeScale = 0.7,
            fatigueEffect = FatigueSetEffect.NONE,
            finalSets = 3
        )
    )

    private fun acceptedPlanWithReasoning(recorded: ExerciseTargetExplanation) = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PUSH,
                exercises = listOf(
                    AcceptedExercise(
                        exerciseId = "bench-press",
                        sets = 3,
                        reps = 8,
                        name = "Barbell Bench Press",
                        movementPattern = MovementPattern.HORIZONTAL_PUSH,
                        suggestedWeightKg = 82.5,
                        explanation = recorded
                    )
                )
            )
        )
    )

    @Test
    fun activatingRejectsASecondBlockUnlessReplacing() = runTest {
        activateRoutine(routine(), request(replaceActive = false))

        assertFailsWith<ScheduleException> {
            activateRoutine(routine(), request(replaceActive = false))
        }

        val replaced = activateRoutine(routine(), request(replaceActive = true))
        val activations = scheduleRepository.allActivations()
        assertEquals(ActivationStatus.CANCELLED, activations.first { it.id != replaced }.status)
        assertEquals(ActivationStatus.ACTIVE, scheduleRepository.getActivation(replaced)?.status)
    }

    @Test
    fun fullFinishRequiresEveryPrescribedSet() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        startOccurrence(occurrence.id)
        logWorkingSet(occurrence, sets = 1)

        assertFailsWith<ScheduleException> { finish(occurrence.id, FinishMode.FULL) }
    }

    @Test
    fun partialFinishRecordsOmittedRemainderAndAdvances() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrences = scheduleRepository.occurrences(activationId)
        startOccurrence(occurrences[0].id)
        logWorkingSet(occurrences[0], sets = 1)

        val state = finish(occurrences[0].id, FinishMode.PARTIAL)

        val resolved = requireNotNull(scheduleRepository.getOccurrence(occurrences[0].id))
        assertEquals(OccurrenceStatus.FINISHED_PARTIAL, resolved.status)
        assertEquals(RemainingDisposition.OMITTED, resolved.entries[0].remainingDisposition)
        assertEquals(2, resolved.entries[0].terminalRemainingSets)
        assertEquals(occurrences[1].id, state.selectedOccurrenceId)
    }

    @Test
    fun fullFinishResolvesWithoutResidueWhenAllSetsCount() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        startOccurrence(occurrence.id)
        logWorkingSet(occurrence, sets = 3)

        finish(occurrence.id, FinishMode.FULL)

        val resolved = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
        assertEquals(OccurrenceStatus.FINISHED, resolved.status)
        assertNull(resolved.entries[0].remainingDisposition)
        assertNull(resolved.entries[0].terminalRemainingSets)
    }

    @Test
    fun skippingAnUntouchedWorkoutWritesNoDisposition() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()

        skip(occurrence.id)

        val resolved = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
        assertEquals(OccurrenceStatus.SKIPPED, resolved.status)
        assertNull(resolved.entries[0].remainingDisposition)
    }

    @Test
    fun skippingAWarmupOnlyWorkoutStillConcludesItsSession() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        startOccurrence(occurrence.id)
        logRepository.add(
            WorkoutSet(
                exerciseId = occurrence.entries[0].exerciseId,
                reps = occurrence.entries[0].reps,
                weightKg = occurrence.entries[0].weightKg,
                performedAtMillis = now,
                isWarmup = true,
                occurrenceId = occurrence.id,
                occurrenceEntryId = occurrence.entries[0].id
            )
        )

        skip(occurrence.id)

        val resolved = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
        // Warm-up-only work is recorded work: the workout concludes partially, so its closure is
        // not bypassed, while the prescribed working sets keep their own completion count.
        assertEquals(OccurrenceStatus.FINISHED_PARTIAL, resolved.status)
        assertEquals(RemainingDisposition.SKIPPED, resolved.entries[0].remainingDisposition)
        assertEquals(occurrence.entries[0].sets, resolved.entries[0].terminalRemainingSets)
    }

    @Test
    fun skippingWorkWithoutAnEntryLinkStillConcludesItsSession() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        startOccurrence(occurrence.id)
        logRepository.add(
            WorkoutSet(
                exerciseId = occurrence.entries[0].exerciseId,
                reps = occurrence.entries[0].reps,
                weightKg = occurrence.entries[0].weightKg,
                performedAtMillis = now,
                isWarmup = false,
                occurrenceId = occurrence.id,
                occurrenceEntryId = null
            )
        )

        skip(occurrence.id)

        val resolved = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
        assertEquals(OccurrenceStatus.FINISHED_PARTIAL, resolved.status)
        assertEquals(occurrence.entries[0].sets, resolved.entries[0].terminalRemainingSets)
    }

    @Test
    fun skippingAStartedWorkoutMarksTheRemainderSkipped() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        startOccurrence(occurrence.id)
        logWorkingSet(occurrence, sets = 1)

        skip(occurrence.id)

        val resolved = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
        assertEquals(OccurrenceStatus.FINISHED_PARTIAL, resolved.status)
        assertEquals(RemainingDisposition.SKIPPED, resolved.entries[0].remainingDisposition)
    }

    @Test
    fun loggingASetOrEndingASessionDoesNotAdvanceTheQueue() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val first = scheduleRepository.occurrences(activationId).first()

        logWorkingSet(first, sets = 1)

        assertEquals(first.id, scheduleRepository.scheduleState().selectedOccurrenceId)
        assertEquals(OccurrenceStatus.PENDING, scheduleRepository.getOccurrence(first.id)?.status)
    }

    @Test
    fun finishingTheBlockRequiresEveryWorkoutResolved() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))

        assertFailsWith<ScheduleException> { finishBlock(activationId) }

        scheduleRepository.occurrences(activationId).forEach { skip(it.id) }
        finishBlock(activationId)

        assertEquals(
            ActivationStatus.FINISHED,
            scheduleRepository.getActivation(activationId)?.status
        )
        assertNull(scheduleRepository.scheduleState().activeActivationId)
    }

    @Test
    fun cancellingKeepsRecordedWorkAndClearsTheCursor() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val first = scheduleRepository.occurrences(activationId).first()
        logWorkingSet(first, sets = 2)

        cancelBlock(activationId)

        assertEquals(
            ActivationStatus.CANCELLED,
            scheduleRepository.getActivation(activationId)?.status
        )
        assertEquals(2, logRepository.setsForOccurrence(first.id).size)
        assertNull(scheduleRepository.scheduleState().activeActivationId)
    }

    @Test
    fun switchingModesPreservesIdsAndRehomesPendingDates() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val before = scheduleRepository.occurrences(activationId).map { it.id }

        switchMode(activationId, ScheduleMode.SEQUENCE, weekdays, friday)
        assertEquals(
            listOf(null, null),
            scheduleRepository.occurrences(activationId).map {
                it.scheduledEpochDay
            }
        )
        assertEquals(before, scheduleRepository.occurrences(activationId).map { it.id })

        switchMode(activationId, ScheduleMode.WEEKDAY, weekdays, friday)
        val after = scheduleRepository.occurrences(activationId)
        assertEquals(
            listOf(friday, daysFromCivil(2026, 10, 12)),
            after.map {
                it.scheduledEpochDay
            }
        )
        assertEquals(before, after.map { it.id })
        assertEquals(ScheduleMode.WEEKDAY, scheduleRepository.getActivation(activationId)?.mode)
    }

    @Test
    fun postponingReflowsTheUnstartedSuffix() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrences = scheduleRepository.occurrences(activationId)
        val saturdayAfter = daysFromCivil(2026, 10, 10)

        move(occurrences[0].id, saturdayAfter)

        val reflowed = scheduleRepository.occurrences(activationId).sortedBy { it.queuePosition }
        assertEquals(saturdayAfter, reflowed[0].scheduledEpochDay)
        assertEquals(daysFromCivil(2026, 10, 12), reflowed[1].scheduledEpochDay)
    }

    @Test
    fun editingIsAllowedBeforeWorkAndBlockedAfter() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()

        edit(
            occurrence.id,
            listOf(OccurrenceEntryDraft("barbell-squat", sets = 5, reps = 5, weightKg = 100.0))
        )
        val edited = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
        assertEquals(listOf("barbell-squat"), edited.entries.map { it.exerciseId })
        assertEquals(5, edited.entries[0].sets)

        startOccurrence(occurrence.id)
        logWorkingSet(edited, sets = 1)
        assertFailsWith<ScheduleException> {
            edit(occurrence.id, listOf(OccurrenceEntryDraft("bench-press", sets = 3, reps = 8)))
        }
    }

    @Test
    fun editingASlotToANonRepsMeasureDropsItsOrderedTargets() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        val targets = SlotSetTargets.of(listOf(SetTarget(reps = 8, weightKg = 30.0)))
        occurrence.entries.forEach { entry ->
            scheduleRepository.replaceOccurrenceEntries(
                occurrence.id,
                occurrence.entries.map { it.copy(setTargets = targets) }
            )
        }

        edit(
            occurrence.id,
            listOf(
                OccurrenceEntryDraft(
                    exerciseId = "bench-press",
                    sets = 3,
                    reps = null,
                    forceNewtons = 250.0,
                    loadKind = LoadKind.BODYWEIGHT
                )
            )
        )

        val stored = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
            .entries.single()
        assertEquals(250.0, stored.forceNewtons)
        // Ordered targets are rep work, so a force slot does not keep them.
        assertNull(stored.setTargets)
    }

    @Test
    fun editingASlotThatStaysRepBasedKeepsItsOrderedTargets() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        val targets = SlotSetTargets.of(
            List(3) { SetTarget(reps = 8, weightKg = 30.0) }
        )
        scheduleRepository.replaceOccurrenceEntries(
            occurrence.id,
            occurrence.entries.map { it.copy(setTargets = targets) }
        )

        edit(
            occurrence.id,
            listOf(OccurrenceEntryDraft("bench-press", sets = 3, reps = 8, weightKg = 30.0))
        )

        val stored = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
            .entries.single()
        assertEquals(8, stored.reps)
        assertEquals(targets, stored.setTargets)
    }

    @Test
    fun editingDuplicateOrdinarySlotsIsRejectedWithoutLosingEitherMetadataRecord() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        val targets = listOf(
            SlotSetTargets.of(List(3) { SetTarget(reps = 8, weightKg = 30.0) }),
            SlotSetTargets.of(List(3) { SetTarget(reps = 6, weightKg = 35.0) })
        )
        val policy = SlotProgressionPolicy(minReps = 6, maxReps = 10, incrementKg = 2.5)
        val duplicateEntries = listOf(
            occurrence.entries.single().copy(
                position = 0,
                setTargets = targets[0],
                progressionPolicy = policy
            ),
            occurrence.entries.single().copy(
                position = 1,
                setTargets = targets[1],
                progressionPolicy = null
            )
        )
        scheduleRepository.replaceOccurrenceEntries(occurrence.id, duplicateEntries)
        var writes = 0
        val writer = object : WorkoutScheduleRepository by scheduleRepository {
            override suspend fun replaceOccurrenceEntries(
                occurrenceId: Long,
                entries: List<OccurrenceEntry>
            ) {
                writes++
                scheduleRepository.replaceOccurrenceEntries(occurrenceId, entries)
            }
        }
        val guardedEdit = EditUnstartedOccurrenceUseCase(writer, logRepository, catalog)

        assertFailsWith<ScheduleException> {
            guardedEdit(
                occurrence.id,
                listOf(OccurrenceEntryDraft("bench-press", sets = 3, reps = 8, weightKg = 30.0))
            )
        }

        assertEquals(0, writes)
        assertEquals(duplicateEntries, scheduleRepository.getOccurrence(occurrence.id)?.entries)
    }

    @Test
    fun editingKeepsAClusterOnlyForTheSameOpeningRepsAndExternalLoad() = runTest {
        val cluster = ClusterPrescription(
            kind = ClusterKind.DROP_SET,
            segments = listOf(ClusterSegmentTarget(8, 80.0), ClusterSegmentTarget(6, 60.0)),
            restSeconds = 15
        )
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        scheduleRepository.replaceOccurrenceEntries(
            occurrence.id,
            occurrence.entries.map { it.copy(cluster = cluster) }
        )

        edit(
            occurrence.id,
            listOf(OccurrenceEntryDraft("bench-press", sets = 3, reps = 8, weightKg = 80.0))
        )

        assertEquals(
            cluster,
            requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
                .entries.single().cluster
        )
    }

    @Test
    fun editingAClusteredSlotWithIncompatiblePrescriptionIsRejectedWithoutWrites() = runTest {
        val cluster = ClusterPrescription(
            kind = ClusterKind.REST_PAUSE,
            segments = listOf(ClusterSegmentTarget(8, 80.0), ClusterSegmentTarget(4, 80.0)),
            restSeconds = 20
        )
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        val clusteredEntries = occurrence.entries.map { it.copy(cluster = cluster) }
        scheduleRepository.replaceOccurrenceEntries(occurrence.id, clusteredEntries)
        var writes = 0
        val writer = object : WorkoutScheduleRepository by scheduleRepository {
            override suspend fun replaceOccurrenceEntries(
                occurrenceId: Long,
                entries: List<OccurrenceEntry>
            ) {
                writes++
                scheduleRepository.replaceOccurrenceEntries(occurrenceId, entries)
            }
        }
        val guardedEdit = EditUnstartedOccurrenceUseCase(writer, logRepository, catalog)

        assertFailsWith<ScheduleException> {
            guardedEdit(
                occurrence.id,
                listOf(OccurrenceEntryDraft("bench-press", sets = 3, reps = 7, weightKg = 80.0))
            )
        }

        assertEquals(0, writes)
        assertEquals(clusteredEntries, scheduleRepository.getOccurrence(occurrence.id)?.entries)
    }

    @Test
    fun editingAnUnstartedSlotKeepsATimedPrescriptionExpressedInSeconds() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()

        edit(
            occurrence.id,
            listOf(
                OccurrenceEntryDraft(
                    exerciseId = "bench-press",
                    sets = 3,
                    reps = null,
                    durationSeconds = 90,
                    weightKg = 80.0
                )
            )
        )

        val stored = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
            .entries.single()
        assertNull(stored.reps)
        assertEquals(90, stored.durationSeconds)
        assertEquals(3, stored.sets)
        assertEquals(80.0, stored.weightKg)

        // A draft with both measurements, or with neither, is refused and leaves the slot alone.
        listOf(
            OccurrenceEntryDraft("bench-press", sets = 3, reps = 8, durationSeconds = 90),
            OccurrenceEntryDraft("bench-press", sets = 3)
        ).forEach { invalid ->
            assertFailsWith<ScheduleException> { edit(occurrence.id, listOf(invalid)) }
        }
        val unchanged = requireNotNull(scheduleRepository.getOccurrence(occurrence.id))
            .entries.single()
        assertNull(unchanged.reps)
        assertEquals(90, unchanged.durationSeconds)
    }

    @Test
    fun repeatingCreatesFreshIdsFromTheFrozenMaster() = runTest {
        val originalId = activateRoutine(routine(), request(replaceActive = false))
        val original = scheduleRepository.occurrences(originalId)
        val repeat = RepeatTrainingBlockUseCase(scheduleRepository, createActivation)

        val repeatedId = repeat(originalId, daysFromCivil(2026, 11, 1))

        assertTrue(repeatedId != originalId)
        val repeated = scheduleRepository.occurrences(repeatedId)
        assertEquals(original.size, repeated.size)
        assertTrue(repeated.map { it.id }.none { it in original.map { o -> o.id } })
        assertEquals(
            original[0].entries.map { it.exerciseId },
            repeated[0].entries.map { it.exerciseId }
        )
    }

    @Test
    fun resolvingTwiceIsIdempotent() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        startOccurrence(occurrence.id)
        logWorkingSet(occurrence, sets = 3)

        finish(occurrence.id, FinishMode.FULL)
        val resolvedAt = scheduleRepository.getOccurrence(occurrence.id)?.resolvedAtMillis

        finish(occurrence.id, FinishMode.FULL)
        assertEquals(resolvedAt, scheduleRepository.getOccurrence(occurrence.id)?.resolvedAtMillis)
    }

    @Test
    fun movingAStartedOccurrenceIsRejected() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrence = scheduleRepository.occurrences(activationId).first()
        startOccurrence(occurrence.id)

        assertFailsWith<ScheduleException> { move(occurrence.id, 999L) }
    }

    @Test
    fun switchingModeLeavesStartedOccurrencesUntouchedAndClearsPendingOnes() = runTest {
        val activationId = activateRoutine(routine(), request(replaceActive = false))
        val occurrences = scheduleRepository.occurrences(activationId)
        startOccurrence(occurrences[0].id)
        val startedDate = scheduleRepository.getOccurrence(occurrences[0].id)?.scheduledEpochDay

        switchMode(activationId, ScheduleMode.SEQUENCE, weekdays, friday)

        assertEquals(
            startedDate,
            scheduleRepository.getOccurrence(occurrences[0].id)?.scheduledEpochDay
        )
        assertNull(scheduleRepository.getOccurrence(occurrences[1].id)?.scheduledEpochDay)
    }

    @Test
    fun activatingAnArchivedRoutineIsRejected() = runTest {
        assertFailsWith<ScheduleException> {
            activateRoutine(
                routine().copy(archivedAtMillis = 1L),
                request(replaceActive = false)
            )
        }
    }

    @Test
    fun activatingWithAnUnknownExerciseIsRejected() = runTest {
        val template = routine().copy(
            workouts = listOf(
                RoutineWorkout(
                    name = "Day 1",
                    entries = listOf(RoutineEntry(exerciseId = "ghost", sets = 3, reps = 8))
                )
            )
        )

        assertFailsWith<ScheduleException> {
            activateRoutine(template, request(replaceActive = false))
        }
    }

    private fun request(replaceActive: Boolean) = ActivationRequest(
        name = "Upper/Lower",
        startEpochDay = friday,
        mode = ScheduleMode.WEEKDAY,
        weekdays = weekdays,
        templateId = 1L,
        templateRevision = 1,
        startToday = true,
        replaceActive = replaceActive
    )

    private fun routine() = RoutineTemplate(
        id = 1L,
        name = "Upper/Lower",
        revision = 1,
        workouts = listOf(
            RoutineWorkout(
                position = 0,
                name = "Upper",
                focus = com.hydrafit.app.core.domain.engine.SplitFocus.UPPER,
                entries = listOf(
                    RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8, weightKg = 80.0)
                )
            ),
            RoutineWorkout(
                position = 1,
                name = "Lower",
                focus = com.hydrafit.app.core.domain.engine.SplitFocus.LOWER,
                entries = listOf(
                    RoutineEntry(exerciseId = "barbell-squat", sets = 3, reps = 5, weightKg = 100.0)
                )
            )
        )
    )

    private suspend fun logWorkingSet(occurrence: WorkoutOccurrence, sets: Int) {
        repeat(sets) {
            logRepository.add(
                WorkoutSet(
                    exerciseId = occurrence.entries[0].exerciseId,
                    reps = occurrence.entries[0].reps,
                    weightKg = occurrence.entries[0].weightKg,
                    performedAtMillis = now,
                    isWarmup = false,
                    occurrenceId = occurrence.id,
                    occurrenceEntryId = occurrence.entries[0].id
                )
            )
        }
    }
}

private class FakeCatalog : ExerciseCatalog {
    private val exercises = listOf(
        Exercise(
            id = "bench-press",
            name = "Barbell Bench Press",
            requiredEquipment = setOf(EquipmentTag("barbell")),
            primaryMuscles = emptySet(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                com.hydrafit.app.core.domain.fatigue.MuscleGroup.CHEST_UPPER to 0.6
            )
        ),
        Exercise(
            id = "barbell-squat",
            name = "Barbell Squat",
            requiredEquipment = setOf(EquipmentTag("barbell")),
            primaryMuscles = emptySet(),
            movementPattern = MovementPattern.SQUAT,
            involvements = mapOf(com.hydrafit.app.core.domain.fatigue.MuscleGroup.QUADS to 0.7)
        )
    )

    override suspend fun all(): List<Exercise> = exercises
}

private class FakeWorkoutScheduleRepository : WorkoutScheduleRepository {
    private val state = MutableStateFlow(WorkoutScheduleState())
    private val activations = LinkedHashMap<Long, TrainingActivation>()
    private val occurrences = LinkedHashMap<Long, WorkoutOccurrence>()
    private var nextActivationId = 1L
    private var nextWorkoutId = 1L
    private var nextActivationEntryId = 1L
    private var nextOccurrenceId = 1L
    private var nextOccurrenceEntryId = 1L

    fun allActivations(): List<TrainingActivation> = activations.values.toList()

    override fun observeScheduleState(): Flow<WorkoutScheduleState> = state.map { it }

    override fun observeActiveActivation(): Flow<TrainingActivation?> =
        state.map { activeActivation() }

    override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
        state.map { occurrences(activationId) }

    override suspend fun scheduleState(): WorkoutScheduleState = state.value

    override suspend fun setScheduleState(state: WorkoutScheduleState) {
        this.state.value = state
    }

    override suspend fun activeActivation(): TrainingActivation? =
        activations.values.lastOrNull { it.status == ActivationStatus.ACTIVE }

    override suspend fun getActivation(id: Long): TrainingActivation? = activations[id]

    override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> =
        occurrences.values.filter { it.activationId == activationId }.sortedBy { it.queuePosition }

    override suspend fun getOccurrence(id: Long): WorkoutOccurrence? = occurrences[id]

    override suspend fun acceptAndActivate(
        acceptedPlan: AcceptedPlan?,
        activation: TrainingActivation,
        scheduledEpochDays: List<Long?>,
        replaceActive: Boolean
    ): Long {
        val active = activeActivation()
        if (active != null) {
            if (!replaceActive) throw ScheduleException("A training block is already active")
            activations[active.id] = active.copy(
                status = ActivationStatus.CANCELLED,
                endedAtMillis = activation.createdAtMillis,
                revision = active.revision + 1
            )
        }
        val id = nextActivationId++
        val stored = activation.copy(
            id = id,
            workouts = activation.workouts.map { workout ->
                workout.copy(
                    id = nextWorkoutId++,
                    entries = workout.entries.map { it.copy(id = nextActivationEntryId++) }
                )
            }
        )
        activations[id] = stored
        val occurrenceIds = stored.workouts.mapIndexed { index, workout ->
            val occurrenceId = nextOccurrenceId++
            this.occurrences[occurrenceId] = WorkoutOccurrence(
                id = occurrenceId,
                activationId = id,
                activationWorkoutId = workout.id,
                queuePosition = index,
                scheduledEpochDay = scheduledEpochDays.getOrNull(index),
                status = OccurrenceStatus.PENDING,
                revision = 1,
                entries = workout.entries.mapIndexed { entryIndex, entry ->
                    OccurrenceEntry(
                        id = nextOccurrenceEntryId++,
                        sourceActivationEntryId = entry.id,
                        position = entryIndex,
                        exerciseId = entry.exerciseId,
                        exerciseName = entry.exerciseName,
                        movementPattern = entry.movementPattern,
                        requiredEquipment = entry.requiredEquipment,
                        involvements = entry.involvements,
                        isUnilateral = entry.isUnilateral,
                        sets = entry.sets,
                        reps = entry.reps,
                        durationSeconds = entry.durationSeconds,
                        weightKg = entry.weightKg,
                        cluster = entry.cluster
                    )
                }
            )
            occurrenceId
        }
        state.value = WorkoutScheduleState(
            activeActivationId = id,
            selectedOccurrenceId = occurrenceIds.firstOrNull(),
            legacyFallbackEnabled = false
        )
        return id
    }

    override suspend fun resolveOccurrence(
        occurrenceId: Long,
        expectedRevision: Int,
        status: OccurrenceStatus,
        resolvedAtMillis: Long,
        entries: List<OccurrenceEntry>
    ): WorkoutScheduleState {
        val occurrence = occurrences[occurrenceId]
            ?: throw ScheduleException("That workout no longer exists")
        if (state.value.activeActivationId != occurrence.activationId) {
            throw ScheduleException("That workout is not in the active block")
        }
        if (occurrence.revision != expectedRevision) {
            throw ScheduleException("That workout changed; refresh and try again")
        }
        occurrences[occurrenceId] = occurrence.copy(
            entries = entries.map {
                it.copy(
                    id = if (it.id ==
                        0L
                    ) {
                        nextOccurrenceEntryId++
                    } else {
                        it.id
                    }
                )
            },
            status = status,
            startedAtMillis = occurrence.startedAtMillis ?: resolvedAtMillis,
            resolvedAtMillis = resolvedAtMillis,
            revision = occurrence.revision + 1
        )
        val next = occurrences.values
            .filter { it.activationId == occurrence.activationId && !it.isResolved }
            .minByOrNull { it.queuePosition }
        val updated = state.value.copy(selectedOccurrenceId = next?.id)
        state.value = updated
        return updated
    }

    override suspend fun updateActivationHeader(activation: TrainingActivation) {
        if (activations.containsKey(activation.id)) activations[activation.id] = activation
    }

    override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) {
        val existing = occurrences[occurrence.id] ?: return
        occurrences[occurrence.id] = existing.copy(
            queuePosition = occurrence.queuePosition,
            scheduledEpochDay = occurrence.scheduledEpochDay,
            notBeforeEpochDay = occurrence.notBeforeEpochDay,
            startedAtMillis = occurrence.startedAtMillis,
            resolvedAtMillis = occurrence.resolvedAtMillis,
            status = occurrence.status,
            revision = occurrence.revision
        )
    }

    override suspend fun replaceOccurrenceEntries(
        occurrenceId: Long,
        entries: List<OccurrenceEntry>
    ) {
        val occurrence = occurrences[occurrenceId] ?: return
        occurrences[occurrenceId] = occurrence.copy(
            entries = entries.map { entry ->
                entry.copy(id = if (entry.id == 0L) nextOccurrenceEntryId++ else entry.id)
            }
        )
    }

    override suspend fun deleteOccurrencesForActivation(activationId: Long) {
        occurrences.entries.removeAll { it.value.activationId == activationId }
    }

    override suspend fun isTemplateReferenced(templateId: Long): Boolean =
        activations.values.any { it.templateId == templateId }
}

private class FakeWorkoutLogRepository : WorkoutLogRepository {
    private val sets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private var nextId = 1L

    override suspend fun add(set: WorkoutSet) {
        sets.value = sets.value + set.copy(id = nextId++)
    }

    override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = add(
        set.copy(
            cluster = WorkoutClusterSummary(
                kind = cluster.kind,
                plannedSegments = cluster.plannedSegments,
                performedSegments = cluster.performedSegments
            )
        )
    )

    override suspend fun appendClusterSegments(
        setId: Long,
        segments: List<PerformedClusterSegment>
    ) = Unit

    override suspend fun assignSession(setId: Long, sessionId: String) = Unit

    override suspend fun delete(id: Long) {
        sets.value = sets.value.filterNot { it.id == id }
    }

    override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

    override suspend fun all(): List<WorkoutSet> = sets.value

    override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

    override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> =
        sets.value.filter { it.occurrenceId == occurrenceId }

    override fun setsFlow(): Flow<List<WorkoutSet>> = sets.map { it }

    override suspend fun loggedSets(): List<LoggedSet> = emptyList()

    override fun loggedSetsFlow(): Flow<List<LoggedSet>> = sets.map { emptyList() }

    override suspend fun clear() {
        sets.value = emptyList()
    }
}
