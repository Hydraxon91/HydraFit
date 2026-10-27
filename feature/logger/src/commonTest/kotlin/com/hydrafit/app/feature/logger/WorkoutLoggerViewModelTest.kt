package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.AiLoadExplanation
import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.LoadConversionExplanation
import com.hydrafit.app.core.domain.engine.LoadUnavailableReason
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.ProgressWeightsUseCase
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.FinishWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementException
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementFailure
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementRepository
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ReplaceOccurrenceExerciseUseCase
import com.hydrafit.app.core.domain.schedule.SkipWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.StartWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutLoggingActions
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import com.hydrafit.app.core.domain.time.BootIdentityProvider
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.DeviceAttribution
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.ExerciseNoteRepository
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutSummaryUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestAlertTiming
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.SessionNoteRepository
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupPreparationOverrideRepository
import com.hydrafit.app.core.domain.workout.WorkoutCluster
import com.hydrafit.app.core.domain.workout.WorkoutClusterSummary
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutLogMutations
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import com.hydrafit.app.core.userdata.equipment.EquipmentProfile
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLoggerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val timerViewModels = mutableListOf<WorkoutLoggerViewModel>()

    private fun runTimerTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            timerViewModels.forEach { it.cancelRestTimer() }
            timerViewModels.clear()
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cannotLogUntilAnExerciseAndValidRepsAreProvided() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canLog)

        viewModel.onExerciseSelected("back-squat")
        assertFalse(viewModel.state.value.canLog)

        viewModel.onRepsChanged("5")
        assertTrue(viewModel.state.value.canLog)
    }

    @Test
    fun manualInstructionsPreserveInputsAndClearOnExerciseSwitch() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onRepsChanged("7")
        viewModel.onWeightChanged("45")
        viewModel.onRirChanged("2")
        val baseline = viewModel.state.value

        viewModel.openManualInstructions()
        assertEquals("back-squat", viewModel.state.value.exerciseInstructions?.exerciseId)
        assertEquals("Back Squat", viewModel.state.value.exerciseInstructions?.exerciseName)
        assertEquals(baseline, viewModel.state.value.copy(exerciseInstructions = null))
        viewModel.dismissInstructions()
        assertEquals(baseline, viewModel.state.value)
        viewModel.openManualInstructions()
        viewModel.onExerciseSelected("bench-press")
        assertNull(viewModel.state.value.exerciseInstructions)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun draftInstructionsPreserveEditorAndRejectStaleSlotAfterContextSwitch() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), batchOccurrences(), selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(repository, schedule)
            advanceUntilIdle()
            val draft = viewModel.state.value.draftSets.first()
            viewModel.editDraft(draft)
            val baseline = viewModel.state.value
            viewModel.openDraftInstructions(draft)
            assertEquals(
                LoggerExerciseInstructions(draft.exerciseId, draft.name),
                viewModel.state.value.exerciseInstructions
            )
            assertEquals(baseline, viewModel.state.value.copy(exerciseInstructions = null))
            viewModel.dismissInstructions()
            assertEquals(baseline, viewModel.state.value)
            viewModel.openDraftInstructions(draft)
            schedule.set(blockActivation(), batchOccurrences(), selectedOccurrenceId = 31L)
            advanceUntilIdle()
            assertNull(viewModel.state.value.exerciseInstructions)
            viewModel.openDraftInstructions(draft)
            assertNull(viewModel.state.value.exerciseInstructions)
            assertTrue(repository.all().isEmpty())
        }

    @Test
    fun guidedInstructionsPreserveProgressAndClearOnOccurrenceSwitch() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), batchOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()
        val exercise = viewModel.state.value.guidedProgress!!.exercises.first()
        val baseline = viewModel.state.value
        viewModel.openGuidedInstructions(exercise.occurrenceEntryId)
        assertEquals(
            LoggerExerciseInstructions(exercise.exerciseId, exercise.exerciseName),
            viewModel.state.value.exerciseInstructions
        )
        assertEquals(baseline, viewModel.state.value.copy(exerciseInstructions = null))
        viewModel.dismissInstructions()
        assertEquals(baseline, viewModel.state.value)
        viewModel.openGuidedInstructions(exercise.occurrenceEntryId)
        schedule.set(blockActivation(), batchOccurrences(), selectedOccurrenceId = 31L)
        runCurrent()
        assertNull(viewModel.state.value.exerciseInstructions)
        viewModel.openGuidedInstructions(exercise.occurrenceEntryId)
        assertNull(viewModel.state.value.exerciseInstructions)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun filtersTheExercisePickerBySearchCaseInsensitively() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val all = viewModel.state.value.exercises.size

        viewModel.onExerciseSearchChanged("bench")
        assertEquals(
            listOf("Bench Press"),
            viewModel.state.value.visibleExercises.map { it.name }
        )

        viewModel.onExerciseSearchChanged("")
        assertEquals(all, viewModel.state.value.visibleExercises.size)
    }

    @Test
    fun instructionsAreClearedByANewSessionWithoutLoggingASet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.openManualInstructions()
        assertNotNull(viewModel.state.value.exerciseInstructions)
        viewModel.newSession()
        advanceUntilIdle()
        assertNull(viewModel.state.value.exerciseInstructions)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun searchMatchesHyphenatedNamesFromSpaces() = runTest(dispatcher) {
        val hyphenatedCatalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = listOf(
                Exercise(
                    id = "close-grip-bench-press",
                    name = "Close-grip Bench Press",
                    requiredEquipment = setOf(EquipmentTag.BARBELL),
                    primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
                    movementPattern = MovementPattern.HORIZONTAL_PUSH
                ),
                Exercise(
                    id = "bench-press",
                    name = "Bench Press",
                    requiredEquipment = setOf(EquipmentTag.BARBELL),
                    primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
                    movementPattern = MovementPattern.HORIZONTAL_PUSH
                )
            )
        }
        val viewModel = viewModel(catalog = hyphenatedCatalog)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.exercises.size)

        viewModel.onExerciseSearchChanged("close grip")
        assertEquals(
            listOf("Close-grip Bench Press"),
            viewModel.state.value.visibleExercises.map { it.name }
        )

        viewModel.onExerciseSearchChanged("-")
        assertEquals(2, viewModel.state.value.visibleExercises.size)

        viewModel.onExerciseSearchChanged("squat")
        assertTrue(viewModel.state.value.visibleExercises.isEmpty())
    }

    @Test
    fun prefillsFromTheLastLoggedSetWhenThePlanHasNoSuggestion() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 4,
                    weightKg = 70.0,
                    performedAtMillis = 1L
                ),
                WorkoutSet(
                    id = 2L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 80.0,
                    performedAtMillis = 2L
                )
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertEquals("80", viewModel.state.value.weightInput)
        assertEquals("5", viewModel.state.value.reps)
    }

    @Test
    fun planSuggestionWinsOverTheLastLoggedSet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 80.0,
                    performedAtMillis = 1L
                )
            )
        )
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertEquals("100", viewModel.state.value.weightInput)
    }

    @Test
    fun manualWeightUnitChangesKeepCanonicalLoadAcrossRepeatedToggles() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val units = MutableStateFlow(WeightUnit.KG)
        val viewModel = viewModel(repository = repository, weightUnitFlow = units)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("12.345678901234567")
        units.value = WeightUnit.KG
        runCurrent()
        assertEquals("12.345678901234567", viewModel.state.value.weightInput)

        repeat(4) {
            units.value = WeightUnit.LB
            runCurrent()
            assertEquals(
                exactEntryText(WeightUnit.LB.kilogramsToDisplay(12.345678901234567)),
                viewModel.state.value.weightInput
            )
            units.value = WeightUnit.KG
            runCurrent()
            assertEquals("12.345678901234567", viewModel.state.value.weightInput)
        }
        assertTrue(viewModel.state.value.canLog)

        viewModel.log()
        advanceUntilIdle()

        assertEquals(12.345678901234567, repository.all().single().weightKg)
    }

    @Test
    fun delayedManualPrefillUsesTheCurrentUnitAndRetainsItsCanonicalWeight() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 3,
                    weightKg = 60.0,
                    performedAtMillis = MONDAY - DAY
                )
            )
        )
        val units = MutableStateFlow(WeightUnit.KG)
        val viewModel =
            viewModel(repository = repository, timeMillis = MONDAY, weightUnitFlow = units)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.allGate = gate

        viewModel.onExerciseSelected("back-squat")
        runCurrent()
        units.value = WeightUnit.LB
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(WeightUnit.LB, viewModel.state.value.weightUnit)
        assertEquals(60.0, viewModel.state.value.manualWeightKg)
        assertEquals(
            exactEntryText(WeightUnit.LB.kilogramsToDisplay(60.0)),
            viewModel.state.value.weightInput
        )
        assertTrue(viewModel.state.value.canLog)
        viewModel.onRepsChanged("3")
        viewModel.log()
        advanceUntilIdle()
        assertEquals(60.0, repository.all().maxBy { it.id }.weightKg)
        units.value = WeightUnit.KG
        runCurrent()
        assertEquals("60", viewModel.state.value.weightInput)
    }

    @Test
    fun manualDistanceUnitChangesKeepWholeMetresAndInvalidTextUnresolved() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val units = MutableStateFlow(DistanceUnit.METERS)
        val viewModel = viewModel(repository = repository, distanceUnitFlow = units)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        viewModel.onMetricChanged(SetMetric.DISTANCE)
        viewModel.onDistanceChanged("123")

        repeat(4) {
            units.value = DistanceUnit.FEET
            runCurrent()
            assertEquals(
                exactEntryText(DistanceUnit.FEET.metresToDisplay(123)),
                viewModel.state.value.distanceMeters
            )
            units.value = DistanceUnit.METERS
            runCurrent()
            assertEquals("123", viewModel.state.value.distanceMeters)
        }
        assertTrue(viewModel.state.value.canLog)
        viewModel.log()
        advanceUntilIdle()
        assertEquals(123, repository.all().single().distanceMeters)

        viewModel.onDistanceChanged("1e")
        assertEquals("1e", viewModel.state.value.distanceMeters)
        units.value = DistanceUnit.FEET
        runCurrent()
        assertEquals("1e", viewModel.state.value.distanceMeters)
        assertFalse(viewModel.state.value.canLog)
        viewModel.log()
        advanceUntilIdle()
        assertEquals(1, repository.all().size)
    }

    @Test
    fun switchingAwayFromDistanceClearsItsCanonicalValueUntilPositiveReentry() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val viewModel = viewModel(repository = repository)
            advanceUntilIdle()
            viewModel.onExerciseSelected("back-squat")
            viewModel.onMetricChanged(SetMetric.DISTANCE)
            viewModel.onDistanceChanged("123")
            assertTrue(viewModel.state.value.canLog)

            viewModel.onMetricChanged(SetMetric.REPETITIONS)
            viewModel.onRepsChanged("5")
            viewModel.onMetricChanged(SetMetric.DISTANCE)

            assertNull(viewModel.state.value.manualDistanceMetres)
            assertFalse(viewModel.state.value.canLog)
            viewModel.log()
            advanceUntilIdle()
            assertTrue(repository.all().isEmpty())

            viewModel.onDistanceChanged("124")
            assertTrue(viewModel.state.value.canLog)
            viewModel.log()
            advanceUntilIdle()
            assertEquals(124, repository.all().single().distanceMeters)
        }

    @Test
    fun blankAndZeroManualWeightRemainDistinctAndInvalidWeightCannotLog() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("")
        assertTrue(viewModel.state.value.canLog)
        viewModel.log()
        advanceUntilIdle()
        assertNull(repository.all().single().weightKg)

        viewModel.onWeightChanged("0")
        assertEquals(0.0, viewModel.state.value.manualWeightKg)
        assertTrue(viewModel.state.value.canLog)
        viewModel.log()
        advanceUntilIdle()
        assertEquals(0.0, repository.all().last().weightKg)

        viewModel.onWeightChanged("1e")
        assertEquals("1e", viewModel.state.value.weightInput)
        val units = MutableStateFlow(WeightUnit.KG)
        val togglingViewModel =
            viewModel(repository = FakeWorkoutLogRepository(), weightUnitFlow = units)
        advanceUntilIdle()
        togglingViewModel.onExerciseSelected("back-squat")
        togglingViewModel.onRepsChanged("5")
        togglingViewModel.onWeightChanged("1e")
        units.value = WeightUnit.LB
        runCurrent()
        assertEquals("1e", togglingViewModel.state.value.weightInput)
        assertFalse(togglingViewModel.state.value.canLog)
        assertFalse(viewModel.state.value.canLog)
        viewModel.log()
        advanceUntilIdle()
        assertEquals(2, repository.all().size)
        viewModel.onWeightChanged("-1")
        assertEquals("-1", viewModel.state.value.weightInput)
        assertFalse(viewModel.state.value.canLog)
        viewModel.log()
        advanceUntilIdle()
        assertEquals(2, repository.all().size)
    }

    @Test
    fun quickFillsEveryFieldFromARecentSet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 4,
                    weightKg = 70.0,
                    performedAtMillis = 1L,
                    isWarmup = true,
                    rir = 3
                )
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onRecentSetSelected(viewModel.state.value.recentSets.single())

        val state = viewModel.state.value
        assertEquals("back-squat", state.selectedExerciseId)
        assertEquals("4", state.reps)
        assertEquals("70", state.weightInput)
        assertTrue(state.isWarmup)
        assertEquals("3", state.rir)
    }

    @Test
    fun recentWeightPrefillPreservesCanonicalPrecisionAcrossUnitTogglesAndLogging() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository(
                initial = listOf(
                    WorkoutSet(
                        id = 1L,
                        exerciseId = "back-squat",
                        reps = 4,
                        weightKg = 60.123,
                        performedAtMillis = 1L
                    )
                )
            )
            val units = MutableStateFlow(WeightUnit.KG)
            val viewModel = viewModel(repository = repository, weightUnitFlow = units)
            advanceUntilIdle()
            viewModel.onRecentSetSelected(viewModel.state.value.recentSets.single())
            assertEquals(exactEntryText(60.123), viewModel.state.value.weightInput)
            repeat(3) {
                units.value = WeightUnit.LB
                runCurrent()
                assertEquals(
                    exactEntryText(WeightUnit.LB.kilogramsToDisplay(60.123)),
                    viewModel.state.value.weightInput
                )
                units.value = WeightUnit.KG
                runCurrent()
                assertEquals(exactEntryText(60.123), viewModel.state.value.weightInput)
            }
            viewModel.onRepsChanged("4")
            viewModel.log()
            advanceUntilIdle()
            assertEquals(60.123, repository.all().maxBy { it.id }.weightKg)
        }

    @Test
    fun quickFillingAForceSetKeepsItsExactNewtonsAndDeviceRating() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = null,
                    weightKg = null,
                    performedAtMillis = 1L,
                    forceNewtons = 247.35,
                    device = DeviceAttribution("Grip meter", 60.25)
                )
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onRecentSetSelected(viewModel.state.value.recentSets.single())
        // The prefilled text parses back to the same stored double, so an untouched re-log cannot
        // drift the measurement by rounding it to one decimal.
        assertEquals("247.35", viewModel.state.value.forceNewtons)
        assertEquals("60.25", viewModel.state.value.deviceRatingKg)

        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().maxBy { it.id }
        assertEquals(247.35, logged.forceNewtons)
        assertEquals(DeviceAttribution("Grip meter", 60.25), logged.device)
    }

    @Test
    fun exactEntryTextStaysPlainDecimalAndRoundTripsRepresentativeValues() {
        // The entry filters accept digits and a dot only, so exponential output must never appear.
        listOf(250.0, 247.35, 0.5, 0.0005, 10_000_000.5, 1e20, 1e-18).forEach { value ->
            val text = exactEntryText(value)
            assertEquals(value, text.toDouble(), "text for $value was $text")
            assertTrue(text.none { it == 'E' || it == 'e' }, "text for $value was $text")
        }
        assertEquals("250", exactEntryText(250.0))
        assertEquals("247.35", exactEntryText(247.35))
        assertEquals("0.0005", exactEntryText(0.0005))
        assertEquals("1" + "0".repeat(20), exactEntryText(1e20))
    }

    @Test
    fun correctingOneFieldOfAForceSetKeepsItsExactForceAndRating() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = null,
                    weightKg = null,
                    performedAtMillis = 1L,
                    forceNewtons = 247.35,
                    device = DeviceAttribution("Grip meter", 60.25)
                )
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        viewModel.onLoggedSetRirChanged("2")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        val corrected = repository.all().single()
        assertEquals(247.35, corrected.forceNewtons)
        assertEquals(DeviceAttribution("Grip meter", 60.25), corrected.device)
        assertEquals(2, corrected.rir)
    }

    @Test
    fun quickFillsTheWeightInTheDisplayUnit() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 1L
                )
            )
        )
        val viewModel = viewModel(repository = repository, weightUnit = WeightUnit.LB)
        advanceUntilIdle()

        viewModel.onRecentSetSelected(viewModel.state.value.recentSets.single())

        assertEquals(
            exactEntryText(WeightUnit.LB.kilogramsToDisplay(100.0)),
            viewModel.state.value.weightInput
        )
    }

    @Test
    fun quickFillingABodyweightSetLeavesTheWeightFieldHidden() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "plank",
                    reps = 20,
                    weightKg = null,
                    performedAtMillis = 1L
                )
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onRecentSetSelected(viewModel.state.value.recentSets.single())

        assertFalse(viewModel.state.value.showWeightField)
        assertEquals("", viewModel.state.value.weightInput)
    }

    @Test
    fun quickFillWinsOverTheAcceptedPlanSuggestion() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 4,
                    weightKg = 80.0,
                    performedAtMillis = 1L
                )
            )
        )
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        viewModel.onRecentSetSelected(viewModel.state.value.recentSets.single())

        assertEquals("80", viewModel.state.value.weightInput)
        assertEquals("4", viewModel.state.value.reps)
    }

    @Test
    fun refreshesTheExerciseListWhenTheCatalogChanges() = runTest(dispatcher) {
        val catalog = MutableStateFlow(
            listOf(
                Exercise(
                    id = "back-squat",
                    name = "Back Squat",
                    requiredEquipment = emptySet(),
                    primaryMuscles = setOf(MuscleGroup.QUADS),
                    movementPattern = MovementPattern.SQUAT
                )
            )
        )
        val reactive = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = catalog.value

            override fun observeAll(): Flow<List<Exercise>> = catalog
        }
        val viewModel = viewModel(catalog = reactive)
        advanceUntilIdle()
        assertEquals(listOf("Back Squat"), viewModel.state.value.exercises.map { it.name })

        catalog.value = catalog.value + Exercise(
            id = "hammer-curl",
            name = "Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(MuscleGroup.BICEPS),
            movementPattern = MovementPattern.BICEPS_ISOLATION
        )
        advanceUntilIdle()

        assertEquals(
            listOf("Back Squat", "Hammer Curl"),
            viewModel.state.value.exercises.map { it.name }
        )
    }

    @Test
    fun flagsUnilateralExercisesAsPerHand() = runTest(dispatcher) {
        val unilateral = Exercise(
            id = "hammer-curl",
            name = "Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(MuscleGroup.BICEPS),
            movementPattern = MovementPattern.BICEPS_ISOLATION,
            isUnilateral = true
        )
        val reactive = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = listOf(unilateral)

            override fun observeAll(): Flow<List<Exercise>> = flowOf(listOf(unilateral))
        }
        val viewModel = viewModel(catalog = reactive)
        advanceUntilIdle()

        viewModel.onExerciseSelected("hammer-curl")

        assertTrue(viewModel.state.value.selectedExerciseIsUnilateral)
    }

    @Test
    fun draftsTodaysPlannedExercises() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        val draft = viewModel.state.value.draftSets.single()

        assertEquals("back-squat", draft.exerciseId)
        assertEquals(3, draft.sets)
        assertEquals(8, draft.reps)
        assertEquals(100.0, draft.weightKg)
    }

    @Test
    fun aTimedPrescriptionBecomesADraftCarryingItsSecondsInsteadOfReps() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val timed = twoOccurrences().map { occurrence ->
            if (occurrence.id != 30L) {
                return@map occurrence
            }
            occurrence.copy(
                entries = occurrence.entries.map { entry ->
                    entry.copy(sets = 3, reps = null, durationSeconds = 90)
                }
            )
        }
        schedule.set(blockActivation(), timed, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        val draft = viewModel.state.value.draftSets.single()
        assertEquals(3, draft.sets)
        assertNull(draft.reps)
        assertEquals(90, draft.durationSeconds)
        assertEquals(100.0, draft.weightKg)
    }

    @Test
    fun confirmingATimedDraftRecordsItsSecondsAndRepeatsWriteNothing() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val timed = twoOccurrences().map { occurrence ->
            if (occurrence.id != 30L) {
                return@map occurrence
            }
            occurrence.copy(
                entries = occurrence.entries.map { entry ->
                    entry.copy(sets = 3, reps = null, durationSeconds = 90)
                }
            )
        }
        schedule.set(blockActivation(), timed, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.confirmDraft(draft)
        advanceUntilIdle()

        val logged = repository.all()
        assertEquals(3, logged.size)
        assertTrue(logged.all { it.reps == null })
        assertTrue(logged.all { it.durationSeconds == 90 })
        assertEquals(100.0, logged.first().weightKg)
        assertTrue(viewModel.state.value.draftSets.isEmpty())

        // The confirmed slot is gone, so repeating the submission records nothing further.
        viewModel.confirmDraft(draft)
        advanceUntilIdle()
        assertEquals(3, repository.all().size)
    }

    @Test
    fun editingATimedDraftWritesItsEditedSecondsAndNeverReps() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)

        // The editor opens on the frozen measure: its seconds, never a null rep count.
        assertEquals("90", viewModel.state.value.draftEdit?.durationSeconds)
        assertEquals("", viewModel.state.value.draftEdit?.reps)

        viewModel.onDraftDurationSecondsChanged("120")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        val logged = repository.all()
        assertEquals(3, logged.size)
        assertTrue(logged.all { it.durationSeconds == 120 && it.reps == null })

        // The confirmed slot is gone, so repeating the submission records nothing further.
        viewModel.confirmDraft(draft)
        advanceUntilIdle()
        assertEquals(3, repository.all().size)
    }

    @Test
    fun aTimedDraftEditRefusesANonPositiveOrUnstorableSecondsValue() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.editDraft(viewModel.state.value.draftSets.single())
        viewModel.onDraftDurationSecondsChanged("0")
        assertFalse(viewModel.state.value.draftEdit?.enteredMetricIsPositive ?: true)
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        // A value too large for whole seconds is unstorable, so it is refused the same way.
        viewModel.onDraftDurationSecondsChanged("99999999999")
        assertFalse(viewModel.state.value.draftEdit?.enteredMetricIsPositive ?: true)
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
        assertEquals(90, viewModel.state.value.draftSets.single().durationSeconds)
    }

    @Test
    fun cancellingATimedDraftEditWritesNothingAndKeepsItsSeconds() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftDurationSecondsChanged("120")
        viewModel.cancelDraftEdit()

        assertNull(viewModel.state.value.draftEdit)
        assertTrue(repository.all().isEmpty())
        assertEquals(90, viewModel.state.value.draftSets.single().durationSeconds)
    }

    @Test
    fun enteringAMissingLoadForATimedDraftStillWritesItsSeconds() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val noLoad = timedOccurrences().map { occurrence ->
            if (occurrence.id != 30L) return@map occurrence
            occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = null) })
        }
        schedule.set(blockActivation(), noLoad, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        assertNotNull(viewModel.state.value.missingLoadPrompt)
        viewModel.enterMissingLoad()
        viewModel.onDraftWeightChanged("60")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        val logged = repository.all()
        assertEquals(3, logged.size)
        assertTrue(
            logged.all { it.durationSeconds == 90 && it.reps == null && it.weightKg == 60.0 }
        )
    }

    @Test
    fun aFailedTimedDraftWriteRetriesWithItsEditedSeconds() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftDurationSecondsChanged("120")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
        val retry = viewModel.state.value.draftWriteRetries.single()
        assertEquals(120, retry.draft.durationSeconds)
        assertNull(retry.draft.reps)

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        val logged = repository.all()
        assertEquals(3, logged.size)
        assertTrue(logged.all { it.durationSeconds == 120 && it.reps == null })
        assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())
    }

    @Test
    fun aTimedDraftEditIsDroppedWhenTheOccurrenceChanges() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.editDraft(viewModel.state.value.draftSets.single())
        viewModel.onDraftDurationSecondsChanged("120")
        assertNotNull(viewModel.state.value.draftEdit)

        schedule.set(blockActivation(), timedOccurrences(), selectedOccurrenceId = 31L)
        advanceUntilIdle()

        assertNull(viewModel.state.value.draftEdit)
        viewModel.confirmDraftEdit()
        advanceUntilIdle()
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun anAppliedGuidedDraftEditSurvivesARefreshAndOneLogConsumesIt() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("10")
        viewModel.onDraftWeightChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()

        assertTrue(repository.all().isEmpty())
        assertNull(viewModel.state.value.draftEdit)
        assertEquals(10, viewModel.state.value.draftSets.single().reps)
        assertEquals(120.0, viewModel.state.value.draftSets.single().weightKg)

        // An ordinary re-read of the same occurrence keeps the applied values.
        val refreshed = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(revision = occurrence.revision + 1)
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), refreshed, selectedOccurrenceId = 30L)
        advanceUntilIdle()
        assertEquals(10, viewModel.state.value.draftSets.single().reps)
        assertEquals(120.0, viewModel.state.value.draftSets.single().weightKg)
        assertEquals(1, viewModel.state.value.appliedGuidedEdits.size)

        // The explicit logging action writes the edited values once.
        viewModel.confirmGuidedSet(40L)
        runCurrent()
        val logged = repository.all().single()
        assertEquals(10, logged.reps)
        assertEquals(120.0, logged.weightKg)

        // The remaining set of the slot follows the plan again.
        assertTrue(viewModel.state.value.appliedGuidedEdits.isEmpty())
        assertEquals(8, viewModel.state.value.draftSets.single().reps)
        assertEquals(100.0, viewModel.state.value.draftSets.single().weightKg)
    }

    @Test
    fun forceDraftEditKeepsExactForceAndDeviceRatingThroughResetApplyReopenAndLog() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val forceOccurrences = twoOccurrences().map { occurrence ->
            if (occurrence.id != 30L) return@map occurrence
            occurrence.copy(
                entries = occurrence.entries.map { entry ->
                    entry.copy(
                        sets = 2,
                        reps = null,
                        durationSeconds = null,
                        weightKg = null,
                        forceNewtons = 247.35
                    )
                }
            )
        }
        schedule.set(blockActivation(), forceOccurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        assertEquals("247.35", viewModel.state.value.draftEdit?.forceNewtons)
        viewModel.onDraftForceChanged("300")
        viewModel.onDraftDeviceNameChanged("Grip meter")
        viewModel.onDraftDeviceRatingChanged("70.75")
        viewModel.resetDraftEdit()
        assertEquals("247.35", viewModel.state.value.draftEdit?.forceNewtons)
        assertEquals("", viewModel.state.value.draftEdit?.deviceRatingKg)

        viewModel.onDraftDeviceNameChanged("Grip meter")
        viewModel.onDraftDeviceRatingChanged("60.25")
        viewModel.confirmDraftEdit()
        runCurrent()
        assertTrue(repository.all().isEmpty())

        viewModel.editGuidedSet(40L)
        assertEquals("247.35", viewModel.state.value.draftEdit?.forceNewtons)
        assertEquals("60.25", viewModel.state.value.draftEdit?.deviceRatingKg)
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        val logged = repository.all().single()
        assertEquals(247.35, logged.forceNewtons)
        assertEquals(DeviceAttribution("Grip meter", 60.25), logged.device)
    }

    @Test
    fun anAppliedScalarEditExpiresWhenTheSetIsLoggedThroughTheManualForm() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("10")
        viewModel.onDraftWeightChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()
        assertEquals(10, viewModel.state.value.draftSets.single().reps)
        assertEquals(120.0, viewModel.state.value.draftSets.single().weightKg)

        // An ordinary re-read of the same occurrence keeps the applied scalar values.
        schedule.set(
            blockActivation(),
            twoOccurrences().map { occurrence ->
                if (occurrence.id == 30L) {
                    occurrence.copy(revision = occurrence.revision + 1)
                } else {
                    occurrence
                }
            },
            selectedOccurrenceId = 30L
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.appliedGuidedEdits.size)
        assertEquals(10, viewModel.state.value.draftSets.single().reps)

        // The manual Log form writes the pending set; the pending overlay described the consumed
        // position, so the next remaining set follows the plan instead of repeating it.
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertEquals(8, logged.reps)
        assertEquals(100.0, logged.weightKg)
        assertTrue(viewModel.state.value.appliedGuidedEdits.isEmpty())
        val remaining = viewModel.state.value.draftSets.single()
        assertEquals(1, remaining.sets)
        assertEquals(8, remaining.reps)
        assertEquals(100.0, remaining.weightKg)
    }

    @Test
    fun cancellingAGuidedDraftEditWritesNothingAndKeepsThePlanValues() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("10")
        viewModel.cancelDraftEdit()
        runCurrent()

        assertTrue(repository.all().isEmpty())
        assertNull(viewModel.state.value.draftEdit)
        assertTrue(viewModel.state.value.appliedGuidedEdits.isEmpty())
        assertEquals(8, viewModel.state.value.draftSets.single().reps)
    }

    @Test
    fun anAppliedGuidedDraftEditIsDiscardedWhenTheWorkoutChanges() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("10")
        viewModel.confirmDraftEdit()
        runCurrent()
        assertEquals(10, viewModel.state.value.draftSets.single().reps)

        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.appliedGuidedEdits.isEmpty())
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun applyingAGuidedTimedEditWritesNothingAndTheLaterLogWritesItsSeconds() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        assertEquals("90", viewModel.state.value.draftEdit?.durationSeconds)
        viewModel.onDraftDurationSecondsChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()

        // Apply only edits the pending set: no write has happened.
        assertTrue(repository.all().isEmpty())
        assertNull(viewModel.state.value.draftEdit)
        assertEquals(120, viewModel.state.value.draftSets.single().durationSeconds)

        viewModel.confirmGuidedSet(40L)
        runCurrent()

        val logged = repository.all().single()
        assertEquals(120, logged.durationSeconds)
        assertNull(logged.reps)
        assertEquals(40L, logged.occurrenceEntryId)
    }

    @Test
    fun confirmingADraftLogsItsSetsAndRemovesIt() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.confirmDraft(draft)
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
        val targets = repository.all().map { it.prescriptionSnapshot }
        assertEquals(1, targets.distinct().size)
        assertEquals(100.0, assertNotNull(targets.first()).weightKg)
        assertEquals(3, assertNotNull(targets.first()).sets)
    }

    @Test
    fun anUntouchedOrderedDraftLogsEverySetAgainstItsOwnTarget() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), orderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        val draft = viewModel.state.value.draftSets.single()
        // The draft's scalars are seeded from the first ordered target.
        assertEquals(3, draft.sets)
        assertEquals(10, draft.reps)
        assertEquals(60.0, draft.weightKg)

        viewModel.confirmDraft(draft)
        advanceUntilIdle()

        // Fails pre-correction: HEAD logged the draft's scalar values (10 @ 60) for every set and
        // captured one scalar snapshot with no setIndex, so neither the per-set reps/loads nor the
        // ordered indices below could hold.
        val logged = repository.all().sortedBy { it.id }
        assertEquals(listOf(10, 8, 6), logged.map { it.reps })
        assertEquals(listOf(60.0, 70.0, 80.0), logged.map { it.weightKg })
        val snapshots = logged.mapNotNull { it.prescriptionSnapshot }
        assertEquals(3, snapshots.size)
        assertEquals(listOf(0, 1, 2), snapshots.map { it.setIndex })
        assertEquals(listOf(10, 8, 6), snapshots.map { it.reps })
        assertEquals(listOf(60.0, 70.0, 80.0), snapshots.map { it.weightKg })
    }

    @Test
    fun aDraftLevelEditOverridesEveryOrderedSet() = runTest(dispatcher) {
        // An explicit reps/load edit must win over every ordered target, and the sets must not claim
        // an ordered slot index. This also passed against the reproducible pre-correction baseline
        // (HEAD), because HEAD always logged the draft's own scalars; it guards the override path so a
        // future regression cannot re-attach ordered targets to edited sets.
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), orderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        val draft = viewModel.state.value.draftSets.single()
        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("5")
        viewModel.onDraftWeightChanged("50")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        val logged = repository.all().sortedBy { it.id }
        assertEquals(3, logged.size)
        assertTrue(logged.all { it.reps == 5 })
        assertTrue(logged.all { it.weightKg == 50.0 })
        assertEquals(3, logged.mapNotNull { it.prescriptionSnapshot }.size)
        assertTrue(logged.all { it.prescriptionSnapshot?.setIndex == null })
    }

    @Test
    fun aPartiallyPerformedOccurrenceOffsetsTheOrderedSlotIndices() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(initial = listOf(occurrenceSet(entryId = 40L)))
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), orderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val existingIds = repository.all().map { it.id }.toSet()

        val draft = viewModel.state.value.draftSets.single()
        // One of the three sets is already recorded, so the draft is seeded from the second target.
        assertEquals(2, draft.sets)
        assertEquals(8, draft.reps)
        assertEquals(70.0, draft.weightKg)

        viewModel.confirmDraft(draft)
        advanceUntilIdle()

        // Fails pre-correction: HEAD logged `draft.sets` scalar sets with no setIndex, so the offset
        // into the ordered list (index 1 for the next set, 2 after it) could not be observed.
        val written = repository.all().filter { it.id !in existingIds }.sortedBy { it.id }
        assertEquals(2, written.size)
        assertEquals(listOf(8, 6), written.map { it.reps })
        assertEquals(listOf(70.0, 80.0), written.map { it.weightKg })
        assertEquals(listOf(1, 2), written.mapNotNull { it.prescriptionSnapshot?.setIndex })
    }

    @Test
    fun repeatedDraftConfirmationIsIgnoredAndPartialFailureRetriesOnlyRemainingSets() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository(failOnAddAttempt = 2)
            val viewModel = viewModel(
                repository = repository,
                timeMillis = MONDAY,
                acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
            )
            advanceUntilIdle()
            val draft = viewModel.state.value.draftSets.single()

            viewModel.confirmDraft(draft)
            viewModel.confirmDraft(draft)
            advanceUntilIdle()

            assertEquals(1, repository.all().size)
            assertEquals(2, viewModel.state.value.draftSets.single().sets)
            assertEquals(1, viewModel.state.value.draftWriteRetries.size)

            viewModel.confirmDraft(viewModel.state.value.draftSets.single())
            advanceUntilIdle()

            assertEquals(3, repository.all().size)
            assertTrue(viewModel.state.value.draftSets.isEmpty())
            assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())
            assertTrue(repository.all().all { it.prescriptionSnapshot?.sets == 3 })
        }

    @Test
    fun confirmAllKeepsOnlyRemainingSetsAfterAPartialWriteFailure() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 2)
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(
                listOf("back-squat", "bench-press"),
                suggestedWeightKg = 80.0
            )
        )
        advanceUntilIdle()

        viewModel.confirmAllDrafts()
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertEquals(listOf(2, 3), viewModel.state.value.draftSets.map { it.sets })
        assertEquals(1, viewModel.state.value.draftWriteRetries.size)
        assertFalse(viewModel.state.value.confirmingAllDrafts)

        viewModel.confirmAllDrafts()
        advanceUntilIdle()

        assertEquals(6, repository.all().size)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
    }

    @Test
    fun editedConfirmAllRetryKeepsRirAndExplicitTimeAfterPartialFailure() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(2))
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        val chosen = MONDAY - DAY

        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("6")
        viewModel.onDraftRirChanged("2")
        viewModel.onDraftPerformedAtChanged(chosen)
        viewModel.confirmAllDrafts()
        advanceUntilIdle()
        assertEquals(1, repository.all().size)

        viewModel.confirmAllDrafts()
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(
            repository.all().all {
                it.reps == 6 && it.rir == 2 && it.performedAtMillis == chosen
            }
        )
    }

    @Test
    fun confirmAllFailureFeedbackCountsPreviouslyCompletedDrafts() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(4))
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(
                listOf("back-squat", "bench-press"),
                suggestedWeightKg = 80.0
            )
        )
        advanceUntilIdle()

        viewModel.confirmAllDrafts()
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertEquals(listOf("bench-press"), viewModel.state.value.draftSets.map { it.exerciseId })
        assertEquals(3, viewModel.state.value.draftWriteRetries.single().savedSets)
        assertTrue(viewModel.state.value.draftWriteRetries.single().anyRecorded)
    }

    @Test
    fun repeatedLegacyRetryUsesRemainingCountAndEventuallyCompletes() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(2, 3))
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(
                listOf("back-squat"),
                suggestedWeightKg = 100.0,
                loadKind = LoadKind.LEGACY_UNSPECIFIED
            )
        )
        advanceUntilIdle()
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        viewModel.resolveLegacyAsExternal()
        advanceUntilIdle()
        assertEquals(1, repository.all().size)
        assertNull(viewModel.state.value.legacyResolution)
        assertEquals(2, viewModel.state.value.draftSets.single().sets)

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        assertEquals(1, repository.all().size)
        assertEquals(2, viewModel.state.value.draftSets.single().sets)
        assertTrue(viewModel.state.value.draftWriteRetries.single().anyRecorded)

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        assertEquals(3, repository.all().size)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
    }

    @Test
    fun retryEditorPreservesExplicitUseNowAsNull() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(2))
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        viewModel.onPerformedAtChanged(MONDAY - DAY)
        viewModel.editDraft(draft)
        viewModel.onDraftPerformedAtChanged(null)
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        viewModel.editDraft(viewModel.state.value.draftSets.single())
        assertTrue(viewModel.state.value.draftEdit?.performedAtExplicit == true)
        assertNull(viewModel.state.value.draftEdit?.performedAtMillis)
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(repository.all().all { it.performedAtMillis == MONDAY })
    }

    @Test
    fun cancellingUseLastDuringItsLookupLogsNothing() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 90.0,
                    performedAtMillis = MONDAY - DAY
                )
            )
        )
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.missingLoadPrompt)

        val gate = CompletableDeferred<Unit>()
        repository.allGate = gate
        viewModel.useLastLoggedLoad()
        runCurrent()

        viewModel.cancelMissingLoadPrompt()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertEquals(listOf("back-squat"), viewModel.state.value.draftSets.map { it.exerciseId })
        assertNull(viewModel.state.value.draftEdit)
    }

    @Test
    fun cancelAndReopenEqualUseLastPromptDoesNotReuseOldLookup() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 90.0,
                    performedAtMillis = MONDAY - DAY
                )
            )
        )
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        viewModel.confirmDraft(draft)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.allGate = gate

        viewModel.useLastLoggedLoad()
        runCurrent()
        viewModel.cancelMissingLoadPrompt()
        viewModel.confirmDraft(draft)
        assertNull(viewModel.state.value.missingLoadPrompt)
        gate.complete(Unit)
        advanceUntilIdle()

        viewModel.confirmDraft(draft)
        advanceUntilIdle()
        assertEquals(1, repository.all().size)
        assertEquals(draft, viewModel.state.value.missingLoadPrompt)
    }

    @Test
    fun useLastLoggedLoadWithNoHistoryOpensTheEditorWithoutLogging() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        viewModel.useLastLoggedLoad()
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
        assertNotNull(viewModel.state.value.draftEdit)
        assertNull(viewModel.state.value.missingLoadPrompt)
        assertEquals(listOf("back-squat"), viewModel.state.value.draftSets.map { it.exerciseId })
    }

    @Test
    fun partialRetryKeepsEditedRepsRirAndTime() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 2)
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        val chosen = MONDAY - DAY

        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("6")
        viewModel.onDraftRirChanged("2")
        assertTrue(viewModel.onDraftPerformedAtChanged(chosen))
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertEquals(1, viewModel.state.value.draftWriteRetries.size)

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(
            repository.all().all {
                it.reps == 6 && it.rir == 2 && it.performedAtMillis == chosen
            }
        )
    }

    @Test
    fun partialRetrySurvivesAnOccurrenceRefresh() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 2)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("7")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()
        assertEquals(1, repository.all().size)
        assertEquals(1, viewModel.state.value.draftWriteRetries.size)

        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        advanceUntilIdle()

        val retryDraft = viewModel.state.value.draftSets.single()
        assertEquals(1, retryDraft.sets)
        assertEquals(7, retryDraft.reps)
        assertEquals(1, viewModel.state.value.draftWriteRetries.size)
    }

    @Test
    fun switchingOccurrenceDuringAPendingConfirmAllEndsTheBatch() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), batchOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.confirmAllDrafts()
        assertTrue(viewModel.state.value.confirmingAllDrafts)
        assertNotNull(viewModel.state.value.missingLoadPrompt)

        schedule.set(blockActivation(), batchOccurrences(), selectedOccurrenceId = 31L)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.confirmingAllDrafts)
        assertNull(viewModel.state.value.missingLoadPrompt)
        assertEquals(listOf("deadlift"), viewModel.state.value.draftSets.map { it.exerciseId })
    }

    @Test
    fun switchingOccurrenceDuringAnExecutingBatchStopsOldContinuation() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(2))
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = batchOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = 100.0) })
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        repository.addGateOnAttempt = 2

        viewModel.confirmAllDrafts()
        runCurrent()
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 31L)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertEquals(30L, repository.all().single().occurrenceId)
        assertEquals(listOf("deadlift"), viewModel.state.value.draftSets.map { it.exerciseId })
    }

    @Test
    fun individualFinalWriteDoesNotRemoveEqualReplacementPlanDraft() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val firstPlan = oneSetPlan(
            acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        val history = FakePlanHistoryRepository(firstPlan)
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = firstPlan,
            history = history
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.confirmDraft(draft)
        runCurrent()
        history.accepted = firstPlan.copy(acceptedAtMillis = 1L)
        runCurrent()
        assertEquals(listOf(draft), viewModel.state.value.draftSets)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertEquals(listOf(draft), viewModel.state.value.draftSets)
        assertFalse(viewModel.state.value.confirmingAllDrafts)
    }

    @Test
    fun plannedTodayDraftKeepsTheAcceptedSlotsRecordedReasoning() = runTest(dispatcher) {
        val explanation = targetExplanation()
        val plan =
            acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0, explanation = explanation)
        val viewModel = viewModel(acceptedPlan = plan, timeMillis = MONDAY)

        advanceUntilIdle()

        assertEquals(explanation, viewModel.state.value.draftSets.single().explanation)
    }

    @Test
    fun aWithheldAcceptedAiNumberIsExplainedAsAnInventoryLimit() {
        // A sanitized AI number accepted by the app and then withheld by the saved implement keeps
        // the inventory explanation, exactly like a converted deterministic number.
        val withheldAi = ExerciseTargetExplanation(
            aiAdjustment = AiLoadExplanation(
                proposedKg = 500.0,
                proposedAccepted = true,
                deloadScaled = false,
                deloadIntensityScale = 1.0,
                progressionBoundKg = null,
                boundedByProgression = false,
                equipmentCeilingKg = null,
                cappedByEquipment = false
            )
        )
        val converted = ExerciseTargetExplanation(
            conversion = LoadConversionExplanation(
                reps = 6,
                intensityFraction = 0.765,
                uncappedWorkingKg = 92.5
            )
        )
        assertEquals(NoLoadReason.INVENTORY, noLoadReason(withheldAi))
        assertEquals(NoLoadReason.INVENTORY, noLoadReason(converted))
        assertEquals(
            NoLoadReason.INSUFFICIENT_HISTORY,
            noLoadReason(
                ExerciseTargetExplanation(
                    unavailableReason = LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
                )
            )
        )
        assertNull(noLoadReason(null))
        assertNull(noLoadReason(ExerciseTargetExplanation()))
        assertNull(
            noLoadReason(
                ExerciseTargetExplanation(
                    unavailableReason = LoadUnavailableReason.AI_PROPOSAL_OMITTED
                )
            )
        )
    }

    @Test
    fun legacyFinalWriteDoesNotAdvanceQueueAfterPlanReplacement() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val firstPlan = oneSetPlan(
            acceptedPlan(
                listOf("back-squat", "bench-press"),
                suggestedWeightKg = 100.0,
                loadKind = LoadKind.LEGACY_UNSPECIFIED
            )
        )
        val history = FakePlanHistoryRepository(firstPlan)
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = firstPlan,
            history = history
        )
        advanceUntilIdle()
        viewModel.confirmAllDrafts()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.legacyResolution?.items?.size)
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.resolveLegacyAsExternal()
        runCurrent()
        history.accepted = oneSetPlan(
            acceptedPlan(listOf("deadlift"), suggestedWeightKg = 80.0)
                .copy(acceptedAtMillis = 1L)
        )
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertNull(viewModel.state.value.legacyResolution)
        assertEquals(listOf("deadlift"), viewModel.state.value.draftSets.map { it.exerciseId })
    }

    @Test
    fun independentDraftRetriesKeepTheirOwnEditsAcrossFailuresAndConfirmAll() =
        runTest(dispatcher) {
            listOf(false, true).forEach { confirmAll ->
                val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(2, 4, 5))
                val viewModel = viewModel(
                    repository = repository,
                    timeMillis = MONDAY,
                    acceptedPlan = acceptedPlan(
                        listOf("back-squat", "bench-press"),
                        suggestedWeightKg = 80.0
                    )
                )
                advanceUntilIdle()
                val drafts = viewModel.state.value.draftSets
                drafts.forEachIndexed { index, draft ->
                    viewModel.editDraft(draft)
                    viewModel.onDraftRepsChanged((6 + index).toString())
                    viewModel.onDraftWeightChanged((90 + index).toString())
                    viewModel.onDraftRirChanged((2 + index).toString())
                    viewModel.onDraftPerformedAtChanged(MONDAY - (index + 1) * DAY)
                    viewModel.confirmDraftEdit()
                    advanceUntilIdle()
                }
                assertEquals(2, repository.all().size)
                assertEquals(2, viewModel.state.value.draftWriteRetries.size)

                if (confirmAll) {
                    viewModel.confirmAllDrafts()
                } else {
                    viewModel.confirmDraft(viewModel.state.value.draftSets.first())
                }
                advanceUntilIdle()
                assertEquals(2, viewModel.state.value.draftWriteRetries.size)
                viewModel.onResume()
                advanceUntilIdle()
                if (confirmAll) {
                    viewModel.confirmAllDrafts()
                    advanceUntilIdle()
                } else {
                    viewModel.state.value.draftSets.toList().forEach { draft ->
                        viewModel.confirmDraft(draft)
                        advanceUntilIdle()
                    }
                }

                assertEquals(6, repository.all().size)
                drafts.forEachIndexed { index, draft ->
                    val recorded = repository.all().filter { it.exerciseId == draft.exerciseId }
                    assertEquals(3, recorded.size)
                    assertTrue(
                        recorded.all {
                            it.reps == 6 + index &&
                                it.weightKg == (90 + index).toDouble() &&
                                it.rir == 2 + index &&
                                it.performedAtMillis == MONDAY - (index + 1) * DAY
                        }
                    )
                }
                assertTrue(viewModel.state.value.draftSets.isEmpty())
                assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())
            }
        }

    @Test
    fun successfulLegacyResolutionDoesNotClearAnotherDraftRetry() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 2)
        val original = acceptedPlan(
            listOf("back-squat", "bench-press"),
            suggestedWeightKg = 80.0
        )
        val plan = original.copy(
            days = original.days.map { day ->
                day.copy(
                    exercises = day.exercises.map { exercise ->
                        if (exercise.exerciseId == "bench-press") {
                            exercise.copy(
                                loadKind = LoadKind.LEGACY_UNSPECIFIED,
                                loadCapability = ExerciseLoadCapability.UNSPECIFIED
                            )
                        } else {
                            exercise
                        }
                    }
                )
            }
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY, acceptedPlan = plan)
        advanceUntilIdle()
        val drafts = viewModel.state.value.draftSets
        viewModel.editDraft(drafts.first())
        viewModel.onDraftRirChanged("2")
        viewModel.onDraftPerformedAtChanged(MONDAY - DAY)
        viewModel.confirmDraftEdit()
        advanceUntilIdle()
        val retry = viewModel.state.value.draftWriteRetries.single()

        viewModel.confirmDraft(drafts.last())
        viewModel.resolveLegacyAsExternal()
        advanceUntilIdle()
        assertEquals(listOf(retry), viewModel.state.value.draftWriteRetries)
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        assertEquals(6, repository.all().size)
        assertTrue(
            repository.all().filter { it.exerciseId == "back-squat" }.all {
                it.rir == 2 && it.performedAtMillis == MONDAY - DAY
            }
        )
        assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())
    }

    @Test
    fun occurrenceRefreshPreservesEveryRetryAndConfirmAllKeepsFrozenSlots() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(2, 4))
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(
                    entries = occurrence.entries + occurrence.entries.single().copy(
                        id = 42L,
                        position = 1,
                        exerciseId = "bench-press",
                        exerciseName = "Bench Press"
                    )
                )
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        viewModel.state.value.draftSets.toList().forEachIndexed { index, draft ->
            viewModel.editDraft(draft)
            viewModel.onDraftRirChanged((2 + index).toString())
            viewModel.onDraftPerformedAtChanged(MONDAY - (index + 1) * DAY)
            viewModel.confirmDraftEdit()
            advanceUntilIdle()
        }
        val retries = viewModel.state.value.draftWriteRetries
        assertEquals(2, retries.size)
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        advanceUntilIdle()
        assertEquals(retries, viewModel.state.value.draftWriteRetries)
        assertEquals(listOf(1, 1), viewModel.state.value.draftSets.map { it.sets })

        viewModel.confirmAllDrafts()
        advanceUntilIdle()
        assertEquals(4, repository.all().size)
        listOf(40L, 42L).forEachIndexed { index, entryId ->
            val recorded = repository.all().filter { it.occurrenceEntryId == entryId }
            assertEquals(2, recorded.size)
            assertTrue(
                recorded.all {
                    it.occurrenceId == 30L &&
                        it.rir == 2 + index &&
                        it.performedAtMillis == MONDAY - (index + 1) * DAY
                }
            )
        }
        assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())
    }

    @Test
    fun dismissingOneRetryKeepsTheOtherUntilAPlanContextChange() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempts = setOf(2, 4))
        val plan = acceptedPlan(listOf("back-squat", "bench-press"), suggestedWeightKg = 80.0)
        val history = FakePlanHistoryRepository(plan)
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY, history = history)
        advanceUntilIdle()
        viewModel.state.value.draftSets.toList().forEach { draft ->
            viewModel.confirmDraft(draft)
            advanceUntilIdle()
        }
        assertEquals(2, viewModel.state.value.draftWriteRetries.size)
        viewModel.dismissDraft(viewModel.state.value.draftSets.first())
        assertEquals(
            "bench-press",
            viewModel.state.value.draftWriteRetries.single().draft.exerciseId
        )

        history.accepted = acceptedPlan(listOf("pull-up")).copy(acceptedAtMillis = 1L)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())
        assertEquals(listOf("pull-up"), viewModel.state.value.draftSets.map { it.exerciseId })
        assertEquals(2, repository.all().size)
    }

    @Test
    fun migratedAcceptedDraftUsesCurrentCapabilityOnlyForExplicitLegacyResolution() =
        runTest(dispatcher) {
            val capabilities = listOf<ExerciseLoadCapability?>(
                ExerciseLoadCapability.EXTERNAL,
                ExerciseLoadCapability.BODYWEIGHT_ONLY,
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                ExerciseLoadCapability.UNSPECIFIED,
                null
            )
            capabilities.forEach { capability ->
                listOf<Double?>(null, 0.0, 80.0).forEach { weight ->
                    val repository = FakeWorkoutLogRepository()
                    val plan = acceptedPlan(
                        listOf("back-squat"),
                        suggestedWeightKg = weight,
                        loadKind = LoadKind.LEGACY_UNSPECIFIED
                    ).let { original ->
                        original.copy(
                            days = original.days.map { day ->
                                day.copy(
                                    exercises = day.exercises.map {
                                        it.copy(loadCapability = ExerciseLoadCapability.UNSPECIFIED)
                                    }
                                )
                            }
                        )
                    }
                    val history = FakePlanHistoryRepository(plan)
                    val catalog = object : ExerciseCatalog {
                        override suspend fun all(): List<Exercise> = if (capability == null) {
                            emptyList()
                        } else {
                            FakeExerciseCatalog.all().map { it.copy(loadCapability = capability) }
                        }
                    }
                    val viewModel = viewModel(
                        repository = repository,
                        timeMillis = MONDAY,
                        history = history,
                        catalog = catalog
                    )
                    advanceUntilIdle()
                    viewModel.confirmDraft(viewModel.state.value.draftSets.single())
                    val resolution = assertNotNull(viewModel.state.value.legacyResolution)
                    assertEquals(
                        capability == ExerciseLoadCapability.EXTERNAL,
                        resolution.current.canBeExternal
                    )
                    assertTrue(repository.all().isEmpty())

                    viewModel.dismissLegacyResolution()
                    assertEquals(1, viewModel.state.value.draftSets.size)
                    assertTrue(repository.all().isEmpty())
                    viewModel.confirmDraft(viewModel.state.value.draftSets.single())
                    viewModel.resolveLegacyAsExternal()
                    advanceUntilIdle()
                    if (capability == ExerciseLoadCapability.EXTERNAL) {
                        assertEquals(3, repository.all().size)
                        assertTrue(
                            repository.all().all {
                                it.loadKind == LoadKind.EXTERNAL && it.weightKg == weight
                            }
                        )
                    } else {
                        assertTrue(repository.all().isEmpty())
                        viewModel.resolveLegacyAsBodyweight()
                        advanceUntilIdle()
                        assertEquals(3, repository.all().size)
                        assertTrue(
                            repository.all().all {
                                it.loadKind == LoadKind.BODYWEIGHT && it.weightKg == null
                            }
                        )
                    }
                    assertEquals(plan, history.latest())
                }
            }
        }

    @Test
    fun migratedOccurrenceDraftKeepsItsStoredLoadAndFrozenSlotAfterResolution() =
        runTest(dispatcher) {
            listOf<Double?>(null, 0.0, 100.0).forEach { weight ->
                val repository = FakeWorkoutLogRepository()
                val schedule = MutableWorkoutScheduleRepository()
                val occurrences = twoOccurrences().map { occurrence ->
                    occurrence.copy(
                        entries = occurrence.entries.map {
                            it.copy(
                                weightKg = weight,
                                loadKind = LoadKind.LEGACY_UNSPECIFIED,
                                loadCapability = ExerciseLoadCapability.UNSPECIFIED
                            )
                        }
                    )
                }
                schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
                val viewModel = occurrenceViewModel(repository, schedule)
                advanceUntilIdle()
                viewModel.confirmDraft(viewModel.state.value.draftSets.single())
                assertTrue(
                    assertNotNull(viewModel.state.value.legacyResolution).current.canBeExternal
                )
                assertTrue(repository.all().isEmpty())
                viewModel.resolveLegacyAsExternal()
                advanceUntilIdle()

                assertEquals(2, repository.all().size)
                assertTrue(
                    repository.all().all {
                        it.loadKind == LoadKind.EXTERNAL &&
                            it.weightKg == weight &&
                            it.occurrenceId == 30L &&
                            it.occurrenceEntryId == 40L
                    }
                )
                assertEquals(occurrences, schedule.observeOccurrences(5L).first())
            }
        }

    @Test
    fun migratedOccurrenceWithoutExternalCatalogCapabilityCannotResolveAsExternal() =
        runTest(dispatcher) {
            listOf<ExerciseLoadCapability?>(
                ExerciseLoadCapability.BODYWEIGHT_ONLY,
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                ExerciseLoadCapability.UNSPECIFIED,
                null
            ).forEach { capability ->
                val repository = FakeWorkoutLogRepository()
                val schedule = MutableWorkoutScheduleRepository()
                val occurrences = twoOccurrences().map { occurrence ->
                    occurrence.copy(
                        entries = occurrence.entries.map {
                            it.copy(
                                loadKind = LoadKind.LEGACY_UNSPECIFIED,
                                loadCapability = ExerciseLoadCapability.UNSPECIFIED
                            )
                        }
                    )
                }
                val catalog = object : ExerciseCatalog {
                    override suspend fun all(): List<Exercise> = if (capability == null) {
                        emptyList()
                    } else {
                        FakeExerciseCatalog.all().map { it.copy(loadCapability = capability) }
                    }
                }
                schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
                val viewModel = occurrenceViewModel(repository, schedule, catalog)
                advanceUntilIdle()
                viewModel.confirmDraft(viewModel.state.value.draftSets.single())
                assertFalse(
                    assertNotNull(viewModel.state.value.legacyResolution).current.canBeExternal
                )
                viewModel.resolveLegacyAsExternal()
                advanceUntilIdle()
                assertTrue(repository.all().isEmpty())
                viewModel.resolveLegacyAsBodyweight()
                advanceUntilIdle()
                assertEquals(2, repository.all().size)
                assertTrue(
                    repository.all().all {
                        it.loadKind == LoadKind.BODYWEIGHT &&
                            it.weightKg == null &&
                            it.occurrenceId == 30L &&
                            it.occurrenceEntryId == 40L
                    }
                )
                assertEquals(occurrences, schedule.observeOccurrences(5L).first())
            }
        }

    @Test
    fun typedDraftWithUnspecifiedFrozenCapabilityDoesNotUseLegacyCatalogException() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val plan = acceptedPlan(
                listOf("back-squat"),
                loadKind = LoadKind.BODYWEIGHT
            ).let { original ->
                original.copy(
                    days = original.days.map { day ->
                        day.copy(
                            exercises = day.exercises.map {
                                it.copy(loadCapability = ExerciseLoadCapability.UNSPECIFIED)
                            }
                        )
                    }
                )
            }
            val viewModel = viewModel(
                repository = repository,
                timeMillis = MONDAY,
                acceptedPlan = plan
            )
            advanceUntilIdle()
            viewModel.confirmDraft(viewModel.state.value.draftSets.single())
            advanceUntilIdle()

            assertNull(viewModel.state.value.legacyResolution)
            assertEquals(3, repository.all().size)
            assertTrue(
                repository.all().all {
                    it.loadKind == LoadKind.BODYWEIGHT && it.weightKg == null
                }
            )
        }

    @Test
    fun explicitFrozenCapabilitiesAreNotReplacedByCurrentCatalogDuringLegacyResolution() =
        runTest(dispatcher) {
            listOf(
                ExerciseLoadCapability.EXTERNAL,
                ExerciseLoadCapability.BODYWEIGHT_ONLY,
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE
            ).forEach { frozen ->
                val plan = acceptedPlan(
                    listOf("back-squat"),
                    suggestedWeightKg = 80.0,
                    loadKind = LoadKind.LEGACY_UNSPECIFIED
                ).let { original ->
                    original.copy(
                        days = original.days.map { day ->
                            day.copy(
                                exercises = day.exercises.map { it.copy(loadCapability = frozen) }
                            )
                        }
                    )
                }
                val catalog = object : ExerciseCatalog {
                    override suspend fun all(): List<Exercise> = FakeExerciseCatalog.all().map {
                        it.copy(
                            loadCapability = if (frozen == ExerciseLoadCapability.EXTERNAL) {
                                ExerciseLoadCapability.BODYWEIGHT_ONLY
                            } else {
                                ExerciseLoadCapability.EXTERNAL
                            }
                        )
                    }
                }
                val viewModel = viewModel(
                    timeMillis = MONDAY,
                    acceptedPlan = plan,
                    catalog = catalog
                )
                advanceUntilIdle()
                viewModel.confirmDraft(viewModel.state.value.draftSets.single())
                assertEquals(
                    frozen == ExerciseLoadCapability.EXTERNAL,
                    assertNotNull(viewModel.state.value.legacyResolution).current.canBeExternal
                )
            }
        }

    private fun oneSetPlan(plan: AcceptedPlan): AcceptedPlan = plan.copy(
        days = plan.days.map { day ->
            day.copy(exercises = day.exercises.map { exercise -> exercise.copy(sets = 1) })
        }
    )

    private fun batchOccurrences() = listOf(
        WorkoutOccurrence(
            id = 30L,
            activationId = 5L,
            activationWorkoutId = 9L,
            queuePosition = 0,
            status = OccurrenceStatus.PENDING,
            entries = listOf(
                OccurrenceEntry(
                    id = 40L,
                    sourceActivationEntryId = 20L,
                    position = 0,
                    exerciseId = "back-squat",
                    exerciseName = "Back Squat",
                    movementPattern = MovementPattern.SQUAT,
                    sets = 2,
                    reps = 8,
                    weightKg = null
                )
            )
        ),
        WorkoutOccurrence(
            id = 31L,
            activationId = 5L,
            activationWorkoutId = 10L,
            queuePosition = 1,
            status = OccurrenceStatus.PENDING,
            entries = listOf(
                OccurrenceEntry(
                    id = 41L,
                    sourceActivationEntryId = 21L,
                    position = 0,
                    exerciseId = "deadlift",
                    exerciseName = "Deadlift",
                    movementPattern = MovementPattern.HINGE,
                    sets = 2,
                    reps = 5,
                    weightKg = 140.0
                )
            )
        )
    )

    @Test
    fun dismissingADraftLogsNothing() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        viewModel.dismissDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
        assertTrue(viewModel.state.value.draftSets.isEmpty())
    }

    @Test
    fun missingExternalDraftLoadRequiresAnExplicitChoiceAndCanLogWithoutWeight() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val viewModel = viewModel(
                repository = repository,
                timeMillis = MONDAY,
                acceptedPlan = acceptedPlan(listOf("back-squat"))
            )
            advanceUntilIdle()
            val draft = viewModel.state.value.draftSets.single()

            viewModel.confirmDraft(draft)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.missingLoadPrompt)
            assertTrue(repository.all().isEmpty())

            viewModel.cancelMissingLoadPrompt()
            assertEquals(listOf(draft), viewModel.state.value.draftSets)
            viewModel.editDraft(draft)
            viewModel.onDraftRepsChanged("6")
            viewModel.onDraftRirChanged("1")
            viewModel.confirmDraft(draft)
            advanceUntilIdle()
            viewModel.logMissingLoadWithoutWeight()
            advanceUntilIdle()

            assertEquals(3, repository.all().size)
            assertTrue(
                repository.all().all {
                    it.loadKind == LoadKind.EXTERNAL &&
                        it.weightKg == null &&
                        it.reps == 6 &&
                        it.rir == 1
                }
            )
            assertTrue(viewModel.state.value.draftSets.isEmpty())
        }

    @Test
    fun useLastLoggedLoadConfirmsEverySetWithLatestExternalWeight() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 80.0,
                    performedAtMillis = MONDAY - DAY
                ),
                WorkoutSet(
                    id = 2L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 90.0,
                    performedAtMillis = MONDAY - 1
                )
            )
        )
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        viewModel.useLastLoggedLoad()
        advanceUntilIdle()

        val logged = repository.all().filter { it.id > 2L }
        assertEquals(3, logged.size)
        assertTrue(logged.all { it.weightKg == 90.0 && it.loadKind == LoadKind.EXTERNAL })
        assertTrue(viewModel.state.value.draftSets.isEmpty())
    }

    @Test
    fun enterMissingLoadOpensEditorAndUsesTypedWeight() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.confirmDraft(draft)
        advanceUntilIdle()
        viewModel.enterMissingLoad()
        viewModel.onDraftWeightChanged("75")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(repository.all().all { it.loadKind == LoadKind.EXTERNAL && it.weightKg == 75.0 })
    }

    @Test
    fun resetDraftEditRestoresDraftValues() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("12")
        viewModel.onDraftWeightChanged("120")
        viewModel.resetDraftEdit()
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(repository.all().all { it.reps == draft.reps && it.weightKg == draft.weightKg })
    }

    @Test
    fun legacyDraftWithMissingEditedLoadStillUsesLegacyResolutionFirst() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(
                listOf("back-squat"),
                suggestedWeightKg = 80.0,
                loadKind = LoadKind.LEGACY_UNSPECIFIED
            )
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftWeightChanged("")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.legacyResolution)
        assertNull(viewModel.state.value.missingLoadPrompt)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun dismissingDraftAlsoClearsItsEditAndMissingLoadPrompt() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        viewModel.editDraft(draft)
        viewModel.confirmDraft(draft)
        advanceUntilIdle()

        viewModel.dismissDraft(draft)

        assertNull(viewModel.state.value.draftEdit)
        assertNull(viewModel.state.value.missingLoadPrompt)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
    }

    @Test
    fun confirmingAllClearsTransientDraftUiState() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        viewModel.editDraft(draft)

        viewModel.confirmAllDrafts()
        advanceUntilIdle()

        assertNull(viewModel.state.value.draftEdit)
        assertNull(viewModel.state.value.missingLoadPrompt)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
        assertEquals(3, repository.all().size)
    }

    @Test
    fun confirmAllContinuesThroughMissingLoadPromptsWithoutDuplicateSets() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat", "bench-press"))
        )
        advanceUntilIdle()

        viewModel.confirmAllDrafts()
        assertEquals("back-squat", viewModel.state.value.missingLoadPrompt?.exerciseId)
        viewModel.enterMissingLoad()
        viewModel.onDraftWeightChanged("60")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(repository.all().all { it.weightKg == 60.0 })
        assertEquals("bench-press", viewModel.state.value.missingLoadPrompt?.exerciseId)
        assertEquals(1, viewModel.state.value.draftSets.size)

        viewModel.logMissingLoadWithoutWeight()
        advanceUntilIdle()

        assertEquals(6, repository.all().size)
        assertEquals(6, repository.all().map { it.id }.toSet().size)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
        assertNull(viewModel.state.value.missingLoadPrompt)
        assertFalse(viewModel.state.value.confirmingAllDrafts)
    }

    @Test
    fun cancellingMissingLoadPromptStopsConfirmAllAfterAlreadyLoggedDrafts() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat", "bench-press"))
        )
        advanceUntilIdle()

        viewModel.confirmAllDrafts()
        viewModel.logMissingLoadWithoutWeight()
        advanceUntilIdle()
        assertEquals("bench-press", viewModel.state.value.missingLoadPrompt?.exerciseId)

        viewModel.cancelMissingLoadPrompt()

        assertEquals(3, repository.all().size)
        assertEquals(
            listOf("bench-press"),
            viewModel.state.value.draftSets.map {
                it.exerciseId
            }
        )
        assertNull(viewModel.state.value.missingLoadPrompt)
        assertFalse(viewModel.state.value.confirmingAllDrafts)
    }

    @Test
    fun editingUsesTheLoadCapabilityFrozenInTheAcceptedPlanWhenCatalogEntryIsMissing() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val viewModel = viewModel(
                repository = repository,
                timeMillis = MONDAY,
                acceptedPlan = acceptedPlan(
                    listOf("removed-custom-exercise"),
                    loadKind = LoadKind.EXTERNAL
                )
            )
            advanceUntilIdle()
            val draft = viewModel.state.value.draftSets.single()

            assertEquals(ExerciseLoadCapability.EXTERNAL, draft.loadCapability)
            viewModel.editDraft(draft)
            viewModel.onDraftWeightChanged("75")
            viewModel.confirmDraftEdit()
            advanceUntilIdle()

            assertEquals(3, repository.all().size)
            assertTrue(
                repository.all().all {
                    it.exerciseId == "removed-custom-exercise" &&
                        it.loadKind == LoadKind.EXTERNAL &&
                        it.weightKg == 75.0
                }
            )
        }

    @Test
    fun editingDraftAppliesRepsLoadRirAndTimeOnlyToRecordedSets() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val plan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY, acceptedPlan = plan)
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        val chosen = MONDAY - DAY

        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("6")
        viewModel.onDraftWeightChanged("0")
        viewModel.onDraftRirChanged("2")
        assertTrue(viewModel.onDraftPerformedAtChanged(chosen))
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(3, repository.all().size)
        assertTrue(
            repository.all().all {
                it.reps == 6 &&
                    it.weightKg == 0.0 &&
                    it.loadKind == LoadKind.EXTERNAL &&
                    it.rir == 2 &&
                    it.performedAtMillis == chosen
            }
        )
        assertEquals(100.0, plan.days.single().exercises.single().suggestedWeightKg)
    }

    @Test
    fun draftUseNowOverridesTheScreenBackdatedTime() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()
        val backdated = MONDAY - DAY
        viewModel.onPerformedAtChanged(backdated)
        viewModel.editDraft(draft)
        assertTrue(viewModel.onDraftPerformedAtChanged(null))
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertTrue(repository.all().all { it.performedAtMillis == MONDAY })
    }

    @Test
    fun cancellingDraftEditLeavesDraftAndCreatesNoRecord() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("5")
        viewModel.cancelDraftEdit()

        assertEquals(listOf(draft), viewModel.state.value.draftSets)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun editingBodyweightAddableDraftRequiresRevealAndStoresAddedLoad() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val bodyweightViewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("pull-up"))
        )
        advanceUntilIdle()
        val draft = bodyweightViewModel.state.value.draftSets.single()

        bodyweightViewModel.editDraft(draft)
        bodyweightViewModel.onDraftWeightChanged("10")
        bodyweightViewModel.confirmDraftEdit()
        advanceUntilIdle()
        assertTrue(
            repository.all().all {
                it.loadKind == LoadKind.BODYWEIGHT && it.weightKg == null
            }
        )

        val secondRepository = FakeWorkoutLogRepository()
        val revealed = viewModel(
            repository = secondRepository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("pull-up"))
        )
        advanceUntilIdle()
        val secondDraft = revealed.state.value.draftSets.single()
        revealed.editDraft(secondDraft)
        revealed.onDraftWeightRevealed()
        revealed.onDraftWeightChanged("10")
        revealed.confirmDraftEdit()
        advanceUntilIdle()

        assertTrue(
            secondRepository.all().all {
                it.loadKind == LoadKind.ADDED && it.weightKg == 10.0
            }
        )
    }

    @Test
    fun clearingAnAddedLoadDraftPreservesAddedKindWithNoAmount() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(
                listOf("pull-up"),
                loadKind = LoadKind.ADDED,
                suggestedWeightKg = 10.0
            )
        )
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftWeightChanged("")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertTrue(repository.all().all { it.loadKind == LoadKind.ADDED && it.weightKg == null })
    }

    @Test
    fun confirmAllDraftsLogsEverything() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat", "bench-press"))
        )
        advanceUntilIdle()

        viewModel.confirmAllDrafts()
        advanceUntilIdle()

        while (viewModel.state.value.missingLoadPrompt != null) {
            viewModel.logMissingLoadWithoutWeight()
            advanceUntilIdle()
            if (viewModel.state.value.draftSets.isNotEmpty()) {
                viewModel.confirmAllDrafts()
                advanceUntilIdle()
            }
        }
        if (viewModel.state.value.draftSets.isNotEmpty()) {
            viewModel.confirmAllDrafts()
            advanceUntilIdle()
        }

        assertEquals(6, repository.all().size)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
    }

    @Test
    fun hasNoDraftsWithoutAnAcceptedPlan() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.draftSets.isEmpty())
    }

    @Test
    fun confirmingADraftKeepsItGoneAfterResume() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        viewModel.onResume()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.draftSets.isEmpty())
        assertEquals(3, repository.all().size)
    }

    @Test
    fun dismissingADraftKeepsItDismissedAfterResume() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()
        viewModel.dismissDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        viewModel.onResume()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.draftSets.isEmpty())
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun resumeKeepsAnEditedWeightWhenThePlanHasASuggestion() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        assertEquals("100", viewModel.state.value.weightInput)
        viewModel.onWeightChanged("120")

        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("120", viewModel.state.value.weightInput)
    }

    @Test
    fun resumeKeepsAnEditedWeightWhenThePlanHasNoSuggestion() = runTest(dispatcher) {
        val viewModel = viewModel(timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onWeightChanged("80")

        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("back-squat", viewModel.state.value.selectedExerciseId)
        assertEquals("80", viewModel.state.value.weightInput)
    }

    @Test
    fun resumeKeepsADeliberatelyClearedWeight() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onWeightChanged("")

        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.weightInput)
    }

    @Test
    fun resumeKeepsRepsAndWeightAcrossRepeatedResumes() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("12")
        viewModel.onWeightChanged("90")

        viewModel.onResume()
        advanceUntilIdle()
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("12", viewModel.state.value.reps)
        assertEquals("90", viewModel.state.value.weightInput)
    }

    @Test
    fun resumeAfterLoggingKeepsTheTypedWeightNotThePlannedSuggestion() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("95")
        viewModel.log()
        advanceUntilIdle()

        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("95", viewModel.state.value.weightInput)
        assertEquals("5", viewModel.state.value.reps)
        assertEquals(95.0, repository.all().single().weightKg)
    }

    @Test
    fun resumeKeepsARecentSetQuickFilledWeight() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 62.5,
                    performedAtMillis = 1L
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onRecentSetSelected(viewModel.state.value.recentSets.single())
        assertEquals("62.5", viewModel.state.value.weightInput)

        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("62.5", viewModel.state.value.weightInput)
        assertEquals("5", viewModel.state.value.reps)
    }

    @Test
    fun resumeKeepsTheLastSetFallbackWeightWhenThePlanHasNoSuggestion() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 72.5,
                    performedAtMillis = 2L
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        assertEquals("72.5", viewModel.state.value.weightInput)

        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("72.5", viewModel.state.value.weightInput)
    }

    @Test
    fun aNewLocalDayRefreshesTheSuggestionInsteadOfKeepingTheEditedWeight() = runTest(dispatcher) {
        var now = MONDAY
        val repository = FakeWorkoutLogRepository()
        val twoDayPlan = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 0L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "back-squat",
                            sets = 3,
                            reps = 8,
                            name = "back-squat",
                            movementPattern = MovementPattern.CORE,
                            suggestedWeightKg = 100.0
                        )
                    )
                ),
                AcceptedDay(
                    dayIndex = 1,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "back-squat",
                            sets = 3,
                            reps = 8,
                            name = "back-squat",
                            movementPattern = MovementPattern.CORE,
                            suggestedWeightKg = 200.0
                        )
                    )
                )
            ),
            id = 1L
        )
        val viewModel = WorkoutLoggerViewModel(
            logMutations = logMutations(repository, FakeWorkoutSessionRepository()),
            logWorkoutSet = logWorkoutSet(repository, FakeWorkoutSessionRepository()),
            getWorkoutLog = GetWorkoutLogUseCase(repository),
            loggingActions = loggingActions(FakePlanHistoryRepository(twoDayPlan)),
            exerciseCatalog = FakeExerciseCatalog,
            runtime = WorkoutLoggerRuntime(TimeProvider { now }),
            settings = WorkoutLoggerSettings(
                FakeWeightUnitRepository(WeightUnit.KG),
                FakeGuidedWorkoutPreferenceRepository(false),
                FakeRestPreferenceRepository(),
                FakeTrainingGuidancePreferences()
            ),
            exerciseNoteRepository = FakeExerciseNoteRepository(),
            sessionNoteRepository = FakeSessionNoteRepository(),
            warmupOverrideRepository = FakeWarmupPreparationOverrideRepository(),
            equipment = testEquipment()
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onWeightChanged("150")
        assertEquals("150", viewModel.state.value.weightInput)

        now = MONDAY + 3L * DAY
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("200", viewModel.state.value.weightInput)
    }

    @Test
    fun aNewViewModelDoesNotResurrectAConfirmedDraft() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val history = FakePlanHistoryRepository(
            acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        val first = viewModel(repository = repository, timeMillis = MONDAY, history = history)
        advanceUntilIdle()
        first.confirmDraft(first.state.value.draftSets.single())
        advanceUntilIdle()

        val second = viewModel(repository = repository, timeMillis = MONDAY, history = history)
        advanceUntilIdle()

        assertTrue(second.state.value.draftSets.isEmpty())
        assertEquals(3, repository.all().size)
    }

    @Test
    fun reemittingTheSamePlanAfterConfirmDoesNotResurrectTheDraft() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val history = FakePlanHistoryRepository(
            acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY, history = history)
        advanceUntilIdle()
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        history.reemit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.draftSets.isEmpty())
        assertEquals(3, repository.all().size)
    }

    @Test
    fun reemittingTheSamePlanAfterDismissKeepsTheDraftDismissed() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val history = FakePlanHistoryRepository(
            acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0)
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY, history = history)
        advanceUntilIdle()
        viewModel.dismissDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        history.reemit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.draftSets.isEmpty())
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun aNewPlanReDerivesDrafts() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val history = FakePlanHistoryRepository(
            acceptedPlan(listOf("back-squat")).copy(id = 1L)
        )
        val viewModel = WorkoutLoggerViewModel(
            logMutations = logMutations(repository, FakeWorkoutSessionRepository()),
            logWorkoutSet = logWorkoutSet(repository, FakeWorkoutSessionRepository()),
            getWorkoutLog = GetWorkoutLogUseCase(repository),
            loggingActions = loggingActions(history),
            exerciseCatalog = FakeExerciseCatalog,
            runtime = WorkoutLoggerRuntime(TimeProvider { MONDAY }),
            settings = WorkoutLoggerSettings(
                FakeWeightUnitRepository(WeightUnit.KG),
                FakeGuidedWorkoutPreferenceRepository(false),
                FakeRestPreferenceRepository(),
                FakeTrainingGuidancePreferences()
            ),
            exerciseNoteRepository = FakeExerciseNoteRepository(),
            sessionNoteRepository = FakeSessionNoteRepository(),
            warmupOverrideRepository = FakeWarmupPreparationOverrideRepository(),
            equipment = testEquipment()
        )
        advanceUntilIdle()
        assertEquals("back-squat", viewModel.state.value.draftSets.single().exerciseId)

        history.accepted = acceptedPlan(listOf("bench-press")).copy(id = 2L)
        advanceUntilIdle()

        assertEquals("bench-press", viewModel.state.value.draftSets.single().exerciseId)
    }

    @Test
    fun aDifferentUnsavedPlanWithTheSameIdReDerivesDrafts() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val history = FakePlanHistoryRepository(
            acceptedPlan(listOf("back-squat")).copy(acceptedAtMillis = 1L)
        )
        val viewModel = WorkoutLoggerViewModel(
            logMutations = logMutations(repository, FakeWorkoutSessionRepository()),
            logWorkoutSet = logWorkoutSet(repository, FakeWorkoutSessionRepository()),
            getWorkoutLog = GetWorkoutLogUseCase(repository),
            loggingActions = loggingActions(history),
            exerciseCatalog = FakeExerciseCatalog,
            runtime = WorkoutLoggerRuntime(TimeProvider { MONDAY }),
            settings = WorkoutLoggerSettings(
                FakeWeightUnitRepository(WeightUnit.KG),
                FakeGuidedWorkoutPreferenceRepository(false),
                FakeRestPreferenceRepository(),
                FakeTrainingGuidancePreferences()
            ),
            exerciseNoteRepository = FakeExerciseNoteRepository(),
            sessionNoteRepository = FakeSessionNoteRepository(),
            warmupOverrideRepository = FakeWarmupPreparationOverrideRepository(),
            equipment = testEquipment()
        )
        advanceUntilIdle()
        assertEquals("back-squat", viewModel.state.value.draftSets.single().exerciseId)

        history.accepted = acceptedPlan(listOf("bench-press")).copy(acceptedAtMillis = 2L)
        advanceUntilIdle()

        assertEquals("bench-press", viewModel.state.value.draftSets.single().exerciseId)
    }

    @Test
    fun aNewLocalDayReDerivesDrafts() = runTest(dispatcher) {
        var now = MONDAY
        val repository = FakeWorkoutLogRepository()
        val twoDayPlan = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 0L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "back-squat",
                            sets = 3,
                            reps = 8,
                            name = "back-squat",
                            movementPattern = MovementPattern.CORE
                        )
                    )
                ),
                AcceptedDay(
                    dayIndex = 1,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            name = "bench-press",
                            movementPattern = MovementPattern.CORE
                        )
                    )
                )
            ),
            id = 1L
        )
        val viewModel = WorkoutLoggerViewModel(
            logMutations = logMutations(repository, FakeWorkoutSessionRepository()),
            logWorkoutSet = logWorkoutSet(repository, FakeWorkoutSessionRepository()),
            getWorkoutLog = GetWorkoutLogUseCase(repository),
            loggingActions = loggingActions(FakePlanHistoryRepository(twoDayPlan)),
            exerciseCatalog = FakeExerciseCatalog,
            runtime = WorkoutLoggerRuntime(TimeProvider { now }),
            settings = WorkoutLoggerSettings(
                FakeWeightUnitRepository(WeightUnit.KG),
                FakeGuidedWorkoutPreferenceRepository(false),
                FakeRestPreferenceRepository(),
                FakeTrainingGuidancePreferences()
            ),
            exerciseNoteRepository = FakeExerciseNoteRepository(),
            sessionNoteRepository = FakeSessionNoteRepository(),
            warmupOverrideRepository = FakeWarmupPreparationOverrideRepository(),
            equipment = testEquipment()
        )
        advanceUntilIdle()
        assertEquals("back-squat", viewModel.state.value.draftSets.single().exerciseId)

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()

        now = MONDAY + 3L * DAY
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals("bench-press", viewModel.state.value.draftSets.single().exerciseId)
    }

    @Test
    fun stampsTheAcceptedWeeksContextOnLoggedSets() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val plan = acceptedPlan(listOf("back-squat")).copy(weekNumber = 2, cycleNumber = 1)
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY, acceptedPlan = plan)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()

        val set = repository.all().single()
        assertEquals(2, set.weekNumber)
        assertEquals(1, set.cycleNumber)
        assertEquals(0, set.dayIndex)
    }

    @Test
    fun guidedWriteStoresTheOccurrenceWorkoutPositionAsItsDay() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.confirmGuidedSet(40L)
        advanceUntilIdle()

        val set = repository.all().single()
        assertEquals(30L, set.occurrenceId)
        assertEquals(0, set.dayIndex)
        assertEquals("back-squat", assertNotNull(set.prescriptionSnapshot).exerciseId)
    }

    @Test
    fun aLaterWorkoutInTheBlockStoresItsOwnPosition() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.confirmGuidedSet(41L)
        advanceUntilIdle()

        val set = repository.all().single()
        assertEquals(31L, set.occurrenceId)
        assertEquals(1, set.dayIndex)
    }

    @Test
    fun occurrenceDayUsesStoredWorkoutPositionRatherThanListIndex() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val baseActivation = blockActivation()
        val activation = baseActivation.copy(
            workouts = baseActivation.workouts.mapIndexed { index, workout ->
                workout.copy(position = index * 2)
            }
        )
        schedule.set(activation, twoOccurrences(), selectedOccurrenceId = 31L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.confirmGuidedSet(41L)
        advanceUntilIdle()

        assertEquals(2, repository.all().single().dayIndex)
    }

    @Test
    fun manualLogInsideABlockAlsoStoresTheWorkoutPosition() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()

        val set = repository.all().single()
        assertEquals(30L, set.occurrenceId)
        assertEquals(0, set.dayIndex)
    }

    @Test
    fun manualLogUsesStoredWorkoutPositionRatherThanListIndex() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val baseActivation = blockActivation()
        val activation = baseActivation.copy(
            workouts = baseActivation.workouts.mapIndexed { index, workout ->
                workout.copy(position = index * 2)
            }
        )
        schedule.set(activation, twoOccurrences(), selectedOccurrenceId = 31L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.onExerciseSelected("deadlift")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("140")
        viewModel.log()
        advanceUntilIdle()

        val set = repository.all().single()
        assertEquals(31L, set.occurrenceId)
        assertEquals(2, set.dayIndex)
    }

    @Test
    fun loggingASetPersistsItAndShowsItInHistory() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.onWarmupToggled(false)
        viewModel.log()
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        val row = viewModel.state.value.recentSets.single()
        assertEquals("Back Squat", row.exerciseName)
        assertEquals(5, row.reps)
        assertEquals(100.0, row.weightKg)
        assertFalse(row.isWarmup)
        // The reps and weight are kept so the next set of the same exercise does not need retyping.
        assertEquals("5", viewModel.state.value.reps)
        assertEquals("100", viewModel.state.value.weightInput)
    }

    @Test
    fun marksWarmupSets() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("10")
        viewModel.onWarmupToggled(true)
        viewModel.log()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.recentSets.single().isWarmup)
        assertFalse(viewModel.state.value.isWarmup)
    }

    @Test
    fun aFailedManualSetWriteKeepsEveryEntryAndReportsFailure() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("6")
        viewModel.onWeightChanged("80")
        viewModel.onRirChanged("2")
        viewModel.onWarmupToggled(true)
        viewModel.log()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.manualLogFailed)
        assertFalse(state.manualLogRefreshFailed)
        assertFalse(state.manualLogInProgress)
        // Nothing was written, and every entered value is retained for a retry.
        assertTrue(repository.all().isEmpty())
        assertEquals("back-squat", state.selectedExerciseId)
        assertEquals("6", state.reps)
        assertEquals("80", state.weightInput)
        assertEquals("2", state.rir)
        // No success effect ran, so the warm-up flag the user set is still set.
        assertTrue(state.isWarmup)
    }

    @Test
    fun retryingAFailedManualSetLogsExactlyOneSet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.manualLogFailed)
        assertTrue(repository.all().isEmpty())

        // Retry is the ordinary Log set action.
        viewModel.log()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.manualLogFailed)
        val set = repository.all().single()
        assertEquals("back-squat", set.exerciseId)
        assertEquals(5, set.reps)
        assertEquals(100.0, set.weightKg)
    }

    @Test
    fun aFailedBackdatedManualSetKeepsTheChosenTimeAndRetryLogsOneSet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        assertTrue(viewModel.onPerformedAtChanged(MONDAY - DAY))
        viewModel.log()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.manualLogFailed)
        assertEquals(MONDAY - DAY, viewModel.state.value.performedAtMillis)
        assertTrue(repository.all().isEmpty())

        viewModel.log()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.manualLogFailed)
        val set = repository.all().single()
        assertEquals(MONDAY - DAY, set.performedAtMillis)
        assertEquals(WorkoutTimingProvenance.CATCH_UP, set.timingProvenance)
    }

    @Test
    fun repeatedTapsDuringAManualSetWriteAndItsSuccessHandlingLogOneSet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        advanceUntilIdle()

        val addGate = CompletableDeferred<Unit>()
        val allGate = CompletableDeferred<Unit>()
        repository.addGate = addGate
        repository.allGate = allGate
        viewModel.log()
        runCurrent()
        assertTrue(viewModel.state.value.manualLogInProgress)

        // Repeated taps during the write are refused by the synchronously claimed guard.
        viewModel.log()
        viewModel.log()
        runCurrent()

        addGate.complete(Unit)
        runCurrent()
        // The write landed and the guard is still held across its success handling.
        assertTrue(viewModel.state.value.manualLogInProgress)
        viewModel.log()
        viewModel.log()
        runCurrent()

        allGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertFalse(viewModel.state.value.manualLogInProgress)
    }

    @Test
    fun cancellingAManualSetWriteReleasesTheGuardWithoutReportingFailure() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        advanceUntilIdle()

        val addGate = CompletableDeferred<Unit>()
        repository.addGate = addGate
        viewModel.log()
        runCurrent()
        assertTrue(viewModel.state.value.manualLogInProgress)

        // Cancellation is rethrown, and the guard is released without being mislabelled as a failure.
        addGate.cancel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.manualLogInProgress)
        assertFalse(viewModel.state.value.manualLogFailed)
        assertFalse(viewModel.state.value.manualLogRefreshFailed)
        assertTrue(repository.all().isEmpty())

        // The released guard accepts a fresh submission.
        viewModel.log()
        advanceUntilIdle()
        assertEquals(1, repository.all().size)
    }

    @Test
    fun aSuccessfulManualSetWithAFailedRefreshIsNotReportedAsUnsaved() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        advanceUntilIdle()

        repository.failAll = true
        viewModel.log()
        advanceUntilIdle()

        // The set is persisted; only the refresh failure is reported, and no retry is invited.
        assertTrue(viewModel.state.value.manualLogRefreshFailed)
        assertFalse(viewModel.state.value.manualLogFailed)
        assertFalse(viewModel.state.value.manualLogInProgress)
        repository.failAll = false
        assertEquals(1, repository.all().size)
    }

    @Test
    fun aCommittedSetWithAFailedOccurrenceRefreshReportsTheRefreshFailureOnly() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(repository, schedule)
            advanceUntilIdle()

            viewModel.onExerciseSelected("back-squat")
            viewModel.onRepsChanged("8")
            viewModel.onWeightChanged("100")

            // The recent-sets read succeeds, and the occurrence read that follows it fails.
            advanceUntilIdle()
            val recentSetsRead = CompletableDeferred<Unit>()
            repository.allGate = recentSetsRead
            viewModel.log()
            runCurrent()
            repository.failAll = true
            recentSetsRead.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.state.value
            // The set is persisted and listed, and it is never reported as unsaved.
            assertEquals(1, state.recentSets.size)
            assertEquals("Back Squat", state.recentSets.single().exerciseName)
            assertTrue(state.manualLogRefreshFailed)
            assertFalse(state.manualLogFailed)
            assertFalse(state.manualLogInProgress)
            repository.failAll = false
            assertEquals(1, repository.all().size)

            // The released guard accepts a fresh submission.
            viewModel.log()
            advanceUntilIdle()
            assertEquals(2, repository.all().size)
        }

    @Test
    fun aRefreshFailureMessageIsDroppedWhenTheOccurrenceChanges() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")

        // The recent-sets read succeeds, and the occurrence read that follows it fails.
        advanceUntilIdle()
        val recentSetsRead = CompletableDeferred<Unit>()
        repository.allGate = recentSetsRead
        viewModel.log()
        runCurrent()
        repository.failAll = true
        recentSetsRead.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.manualLogRefreshFailed)
        repository.failAll = false

        // The next workout replaces the context the completed save's message described.
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.manualLogRefreshFailed)
        assertFalse(viewModel.state.value.manualLogFailed)
        assertEquals(31L, viewModel.state.value.activeOccurrence?.occurrenceId)
        // The saved set is untouched: only the stale message is dropped.
        assertEquals(1, repository.all().size)
    }

    @Test
    fun aFailedManualSetMessageSurvivesAnUnchangedOccurrenceRefresh() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.manualLogFailed)

        // An equipment change refreshes the same occurrence; it is not a new logging context.
        equipment.setMaxWeight(150.0)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.manualLogFailed)
        assertEquals(30L, viewModel.state.value.activeOccurrence?.occurrenceId)
        assertEquals("back-squat", viewModel.state.value.selectedExerciseId)
    }

    @Test
    fun aFailedManualSetMessageIsDroppedWhenTheOccurrenceChanges() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.manualLogFailed)

        // The next workout replaces the context the failure described.
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.manualLogFailed)
        assertEquals(31L, viewModel.state.value.activeOccurrence?.occurrenceId)
    }

    @Test
    fun aFailedManualSetMessageIsDroppedWhenTheOccurrenceIsRemoved() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.manualLogFailed)

        // The block stays active but its last occurrence disappears.
        schedule.set(blockActivation(), emptyList(), selectedOccurrenceId = null)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.manualLogFailed)
        assertNull(viewModel.state.value.activeOccurrence)
    }

    @Test
    fun switchingOccurrenceWhileAFailingManualWriteIsPendingDoesNotShowTheFailure() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(repository, schedule)
            advanceUntilIdle()

            viewModel.onExerciseSelected("back-squat")
            viewModel.onRepsChanged("8")
            viewModel.onWeightChanged("100")

            val writeGate = CompletableDeferred<Unit>()
            repository.addGate = writeGate
            viewModel.log()
            runCurrent()

            // The user switches workout while the write that will fail is still in flight.
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
            advanceUntilIdle()

            writeGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.manualLogFailed)
            assertFalse(viewModel.state.value.manualLogInProgress)
            assertTrue(repository.all().isEmpty())
        }

    @Test
    fun switchingOccurrenceWhileAFailingPostWriteRefreshIsPendingDoesNotShowTheFailure() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(repository, schedule)
            advanceUntilIdle()

            viewModel.onExerciseSelected("back-squat")
            viewModel.onRepsChanged("8")
            viewModel.onWeightChanged("100")

            // The recent-sets read suspends; the user switches workout before it returns.
            advanceUntilIdle()
            val recentSetsRead = CompletableDeferred<Unit>()
            repository.allGate = recentSetsRead
            viewModel.log()
            runCurrent()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
            runCurrent()

            // The replaced submission's occurrence read then fails, after the context changed.
            repository.failAll = true
            recentSetsRead.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.manualLogRefreshFailed)
            assertFalse(viewModel.state.value.manualLogFailed)
            assertFalse(viewModel.state.value.manualLogInProgress)
            repository.failAll = false
            assertEquals(1, repository.all().size)
        }

    @Test
    fun aReplacedManualFormIsNotResetByAnOlderCompletion() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        advanceUntilIdle()

        val addGate = CompletableDeferred<Unit>()
        repository.addGate = addGate
        viewModel.log()
        runCurrent()

        // The user replaces the whole form while the write is in flight.
        viewModel.onExerciseSelected("bench-press")
        viewModel.onWarmupToggled(true)
        runCurrent()

        addGate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.state.value
        // The newer selection and warm-up choice survive the older completion.
        assertEquals("bench-press", state.selectedExerciseId)
        assertTrue(state.isWarmup)
        assertFalse(state.manualLogFailed)
        assertFalse(state.manualLogRefreshFailed)
        // The committed set still carries the attribution and values captured at submission.
        val set = repository.all().single()
        assertEquals("back-squat", set.exerciseId)
        assertEquals(5, set.reps)
        assertFalse(set.isWarmup)
    }

    @Test
    fun aDelayedHistoryPrefillInvalidatesAnInFlightManualSubmission() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 3,
                    weightKg = 60.0,
                    performedAtMillis = MONDAY - DAY
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        // Hold the selection's history read so the prefill lands only after a manual write started.
        val prefillGate = CompletableDeferred<Unit>()
        repository.allGate = prefillGate
        viewModel.onExerciseSelected("back-squat")
        runCurrent()
        assertEquals("", viewModel.state.value.weightInput)

        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.onWarmupToggled(true)
        val addGate = CompletableDeferred<Unit>()
        repository.addGate = addGate
        viewModel.log()
        runCurrent()

        // The delayed prefill replaces the form while the write is still in flight.
        prefillGate.complete(Unit)
        runCurrent()
        assertEquals("3", viewModel.state.value.reps)
        assertEquals("60", viewModel.state.value.weightInput)

        addGate.complete(Unit)
        advanceUntilIdle()

        // The older submission's success handling must not reset the warm-up flag or the replaced
        // entries, while the committed set keeps the values and attribution captured at submission.
        val state = viewModel.state.value
        assertTrue(state.isWarmup)
        assertEquals("3", state.reps)
        assertEquals("60", state.weightInput)
        val set = repository.all().last()
        assertEquals("back-squat", set.exerciseId)
        assertEquals(5, set.reps)
        assertEquals(100.0, set.weightKg)
        assertTrue(set.isWarmup)
    }

    @Test
    fun aDelayedHistoryPrefillClearsAFailedManualSetMessage() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 3,
                    weightKg = 60.0,
                    performedAtMillis = MONDAY - DAY
                )
            ),
            failOnAddAttempt = 1
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        val prefillGate = CompletableDeferred<Unit>()
        repository.allGate = prefillGate
        viewModel.onExerciseSelected("back-squat")
        runCurrent()

        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()
        // The write failed while the delayed prefill was still pending, so the failure is shown.
        assertTrue(viewModel.state.value.manualLogFailed)

        // The prefill lands afterwards and replaces the entries the failure described.
        prefillGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.manualLogFailed)
        assertEquals("3", viewModel.state.value.reps)
        assertEquals("60", viewModel.state.value.weightInput)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun hidesTheWeightFieldForBodyweightExercisesUntilRevealed() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onExerciseSelected("pull-up")
        assertFalse(viewModel.state.value.showWeightField)

        viewModel.onRevealWeight()
        assertTrue(viewModel.state.value.showWeightField)
    }

    @Test
    fun neverLogsAWeightForAHiddenBodyweightField() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("plank")
        viewModel.onRepsChanged("10")
        viewModel.onWeightChanged("50")
        viewModel.log()
        advanceUntilIdle()

        assertEquals(null, repository.all().single().weightKg)
    }

    @Test
    fun logsAWeightWhenABodyweightExerciseIsRevealed() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("pull-up")
        viewModel.onRevealWeight()
        viewModel.onRepsChanged("10")
        viewModel.onWeightChanged("10")
        viewModel.log()
        advanceUntilIdle()

        val stored = repository.all().single()
        assertEquals(10.0, stored.weightKg)
        // Added kilograms, not total resistance.
        assertEquals(LoadKind.ADDED, stored.loadKind)
    }

    @Test
    fun prefillsARevealedBodyweightWeightFromTheAcceptedPlan() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("pull-up"), suggestedWeightKg = 42.0)
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("pull-up")
        assertFalse(viewModel.state.value.showWeightField)
        assertEquals("42", viewModel.state.value.weightInput)

        viewModel.onRevealWeight()
        assertTrue(viewModel.state.value.showWeightField)
        assertEquals("42", viewModel.state.value.weightInput)
    }

    @Test
    fun requiresExplicitResolutionBeforeLoggingALegacyPrescription() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(
                dayZeroExerciseIds = listOf("back-squat"),
                suggestedWeightKg = 32.5,
                loadKind = LoadKind.LEGACY_UNSPECIFIED
            )
        )
        advanceUntilIdle()

        // Confirming a legacy draft opens the resolution instead of writing the unconfirmed number.
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.legacyResolution)
        assertTrue(repository.all().isEmpty())

        // Confirming the recorded number as external records it with the chosen meaning.
        viewModel.resolveLegacyAsExternal()
        advanceUntilIdle()
        val stored = repository.all()
        assertEquals(3, stored.size)
        assertTrue(stored.all { it.loadKind == LoadKind.EXTERNAL && it.weightKg == 32.5 })
        assertNull(viewModel.state.value.legacyResolution)
    }

    @Test
    fun ignoresInvalidInput() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onRepsChanged("0")
        viewModel.log()
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun showsNoTodayFocusWithoutAnAcceptedPlan() = runTest(dispatcher) {
        val viewModel = viewModel(timeMillis = MONDAY)
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.todayFocus)
        assertEquals(
            listOf("Back Squat", "Bench Press", "Dumbbell Curl", "Plank", "Pull-up"),
            viewModel.state.value.exercises.map { it.name }
        )
    }

    @Test
    fun prioritizesTodaysExercisesFromTheAcceptedPlan() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(dayZeroExerciseIds = listOf("plank", "back-squat"))
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SplitFocus.FULL_BODY, state.todayFocus)
        assertEquals(
            listOf("Plank", "Back Squat", "Bench Press", "Dumbbell Curl", "Pull-up"),
            state.exercises.map { it.name }
        )
    }

    @Test
    fun appliesAcceptedPlanPriorityWhenCatalogLoadsAfterThePlan() = runTest(dispatcher) {
        val catalogReady = CompletableDeferred<Unit>()
        val catalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> {
                catalogReady.await()
                return FakeExerciseCatalog.all()
            }
        }
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("plank", "back-squat")),
            catalog = catalog
        )
        runCurrent()

        assertEquals(SplitFocus.FULL_BODY, viewModel.state.value.todayFocus)

        catalogReady.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("Plank", "Back Squat", "Bench Press", "Dumbbell Curl", "Pull-up"),
            viewModel.state.value.exercises.map { it.name }
        )
    }

    @Test
    fun convertsPoundsToKilogramsWhenLogging() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository, weightUnit = WeightUnit.LB)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("225")
        viewModel.log()
        advanceUntilIdle()

        // 225 lb / 2.2046 = 102.06 kg
        assertEquals(102.06, repository.all().single().weightKg!!, absoluteTolerance = 0.05)
    }

    @Test
    fun prefillsTheWeightFromTheAcceptedPlanSuggestionInTheDisplayUnit() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0),
            weightUnit = WeightUnit.LB
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")

        // Editable text preserves the exact converted value rather than a rounded display value.
        assertEquals(
            exactEntryText(WeightUnit.LB.kilogramsToDisplay(100.0)),
            viewModel.state.value.weightInput
        )
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        assertEquals(100.0, repository.all().single().weightKg)
    }

    @Test
    fun reprioritizesWhenTheAcceptedPlanChanges() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository(acceptedPlan(listOf("plank")))
        val viewModel = viewModel(timeMillis = MONDAY, history = history)
        advanceUntilIdle()

        assertEquals("Plank", viewModel.state.value.exercises.first().name)

        history.accepted = acceptedPlan(listOf("bench-press"))
        advanceUntilIdle()

        assertEquals("Bench Press", viewModel.state.value.exercises.first().name)
    }

    @Test
    fun deletesALoggedSet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 1L
                ),
                WorkoutSet(
                    id = 2L,
                    exerciseId = "bench-press",
                    reps = 8,
                    weightKg = 60.0,
                    performedAtMillis = 2L
                )
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.recentSets.size)

        viewModel.deleteSet(1L)
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.state.value.recentSets.map { it.id })
    }

    @Test
    fun correctingASetTimeRoutesToTheRepository() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 5_000L
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = 100_000_000L)
        advanceUntilIdle()

        val dateStartOfDay = localDateStartOfDayUtcMillis(5_000L, 0L)
        val accepted = viewModel.correctSetTime(1L, dateStartOfDay, 12, 0)
        advanceUntilIdle()

        val expected = pickedLocalDateTimeToEpochMillis(dateStartOfDay, 12, 0, 0L)
        assertTrue(accepted)
        assertEquals(expected, repository.all().single().performedAtMillis)
    }

    @Test
    fun reSortsRecentSetsAfterATimeCorrection() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 2_000L
                ),
                WorkoutSet(
                    id = 2L,
                    exerciseId = "bench-press",
                    reps = 8,
                    weightKg = 60.0,
                    performedAtMillis = 1_000L
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = 100_000_000L)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.state.value.recentSets.map { it.id })

        viewModel.correctSetTime(1L, 0L, 0, 0)
        advanceUntilIdle()

        assertEquals(listOf(2L, 1L), viewModel.state.value.recentSets.map { it.id })
    }

    @Test
    fun rejectsAFutureTimeOnASetCorrection() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 1_000L
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = 5_000L)
        advanceUntilIdle()

        val accepted = viewModel.correctSetTime(1L, 0L, 0, 10)
        advanceUntilIdle()

        assertFalse(accepted)
        assertEquals(1_000L, repository.all().single().performedAtMillis)
    }

    @Test
    fun reDerivesTodaysFocusOnResume() = runTest(dispatcher) {
        var now = 1_000L // Thursday: the plan's Monday day has no focus.
        val repository = FakeWorkoutLogRepository()
        val viewModel = WorkoutLoggerViewModel(
            logMutations = logMutations(repository, FakeWorkoutSessionRepository()),
            logWorkoutSet = logWorkoutSet(repository, FakeWorkoutSessionRepository()),
            getWorkoutLog = GetWorkoutLogUseCase(repository),
            loggingActions = loggingActions(
                FakePlanHistoryRepository(acceptedPlan(listOf("plank")))
            ),
            exerciseCatalog = FakeExerciseCatalog,
            runtime = WorkoutLoggerRuntime(TimeProvider { now }),
            settings = WorkoutLoggerSettings(
                FakeWeightUnitRepository(WeightUnit.KG),
                FakeGuidedWorkoutPreferenceRepository(false),
                FakeRestPreferenceRepository(),
                FakeTrainingGuidancePreferences()
            ),
            exerciseNoteRepository = FakeExerciseNoteRepository(),
            sessionNoteRepository = FakeSessionNoteRepository(),
            warmupOverrideRepository = FakeWarmupPreparationOverrideRepository(),
            equipment = testEquipment()
        )
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.todayFocus)

        now = MONDAY
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(SplitFocus.FULL_BODY, viewModel.state.value.todayFocus)
    }

    @Test
    fun logsRirWhenProvidedAndNullWhenBlank() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.onRirChanged("1")
        viewModel.log()
        advanceUntilIdle()
        assertEquals(1, repository.all().single().rir)

        viewModel.onRirChanged("")
        viewModel.log()
        advanceUntilIdle()
        assertEquals(null, repository.all().last().rir)
    }

    @Test
    fun quickPickedZeroIsLoggedAndCanBeClearedBackToUnreported() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.onRirChanged(nextRirQuickPickValue(viewModel.state.value.rir, 0))
        viewModel.log()
        advanceUntilIdle()
        assertEquals(0, repository.all().single().rir)

        viewModel.onRirChanged(nextRirQuickPickValue(viewModel.state.value.rir, 0))
        viewModel.log()
        advanceUntilIdle()
        assertEquals(null, repository.all().last().rir)
    }

    @Test
    fun ignoresOutOfRangeOrNonNumericRirInput() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRirChanged("15")
        assertEquals("", viewModel.state.value.rir)

        viewModel.onRirChanged("10")
        assertEquals("10", viewModel.state.value.rir)

        viewModel.onRirChanged("1a")
        assertEquals("1", viewModel.state.value.rir)
    }

    @Test
    fun firstSetAutoStartsAndStampsASession() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { MONDAY }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()

        val open = sessions.open()
        assertNotNull(open)
        assertEquals(open, viewModel.state.value.activeSession)
        assertEquals(open.id, logs.all().single().sessionId)
    }

    @Test
    fun aLaterSetInTheSameDayWithinTheWindowReusesTheOpenSession() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val first = sessions.open()

        now = MONDAY + 1.hours.inWholeMilliseconds
        viewModel.log()
        advanceUntilIdle()

        assertEquals(first, sessions.open())
        assertEquals(1, sessions.all().size)
        assertEquals(setOf(first!!.id), logs.all().map { it.sessionId }.toSet())
    }

    @Test
    fun aSetOnADifferentLocalDayClosesThePriorAndStartsANewSession() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val first = sessions.open()!!

        now = MONDAY + DAY
        viewModel.log()
        advanceUntilIdle()

        val second = sessions.open()
        assertNotNull(second)
        assertTrue(first.id != second.id)
        assertEquals(2, sessions.all().size)
    }

    @Test
    fun aSetBeyondTheInactivityWindowClosesThePriorAndStartsANewSession() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val first = sessions.open()!!

        now = MONDAY + SessionConfig().sessionInactivityWindow.inWholeMilliseconds + 1
        viewModel.log()
        advanceUntilIdle()

        val second = sessions.open()
        assertNotNull(second)
        assertTrue(first.id != second.id)
        assertEquals(2, sessions.all().size)
    }

    @Test
    fun endSessionThenTheNextSetStartsANewSession() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val first = sessions.open()!!

        viewModel.endSession()
        advanceUntilIdle()
        assertNull(sessions.open())
        assertNull(viewModel.state.value.activeSession)

        now = MONDAY + 1.hours.inWholeMilliseconds
        viewModel.log()
        advanceUntilIdle()

        val second = sessions.open()
        assertNotNull(second)
        assertTrue(first.id != second.id)
    }

    @Test
    fun newSessionClosesTheCurrentAndStartsAFreshOne() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val first = sessions.open()!!

        now = MONDAY + 1.hours.inWholeMilliseconds
        viewModel.newSession()
        advanceUntilIdle()

        val second = sessions.open()
        assertNotNull(second)
        assertTrue(first.id != second.id)
        assertEquals(second, viewModel.state.value.activeSession)
    }

    @Test
    fun newSessionWithNoOpenSessionStartsOne() = runTest(dispatcher) {
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(FakeWorkoutLogRepository(), sessions) { MONDAY }
        advanceUntilIdle()

        assertNull(viewModel.state.value.activeSession)

        viewModel.newSession()
        advanceUntilIdle()

        val open = sessions.open()
        assertNotNull(open)
        assertEquals(open, viewModel.state.value.activeSession)
    }

    @Test
    fun draftSetsStampTheSameSessionAsLiveSets() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = viewModel(
            repository = logs,
            sessionRepository = sessions,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()

        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        if (viewModel.state.value.missingLoadPrompt != null) {
            viewModel.logMissingLoadWithoutWeight()
            advanceUntilIdle()
        }

        assertEquals(3, logs.all().size)
        assertEquals(1, sessions.all().size)
        assertEquals(1, logs.all().map { it.sessionId }.toSet().size)
    }

    @Test
    fun activeSessionIsRestoredFromThePersistedOpenSessionAfterRestart() = runTest(dispatcher) {
        val sessions = FakeWorkoutSessionRepository()
        val existing = StartWorkoutSessionUseCase(sessions)(
            startedAtMillis = MONDAY,
            localEpochDay = MONDAY / DAY
        )

        val viewModel = sessionViewModel(FakeWorkoutLogRepository(), sessions) { MONDAY }
        advanceUntilIdle()

        assertEquals(existing, viewModel.state.value.activeSession)
    }

    @Test
    fun backdatedLogAttachesToTheOpenSessionOnTheSameDay() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val open = sessions.open()!!

        now = MONDAY + 1.hours.inWholeMilliseconds
        assertTrue(viewModel.onPerformedAtChanged(now))
        viewModel.log()
        advanceUntilIdle()

        assertEquals(open, sessions.open())
        assertEquals(1, sessions.all().size)
        assertEquals(setOf(open.id), logs.all().map { it.sessionId }.toSet())
    }

    @Test
    fun backdatedLogOnADifferentDayKeepsTheOpenSessionActive() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val open = sessions.open()!!

        val chosen = MONDAY - DAY
        assertTrue(viewModel.onPerformedAtChanged(chosen))
        viewModel.log()
        advanceUntilIdle()

        // The live open session is untouched and still reported as active.
        assertEquals(open, sessions.open())
        assertEquals(open, viewModel.state.value.activeSession)
        val backdated = logs.all().single { it.performedAtMillis == chosen }
        assertNotNull(backdated.sessionId)
        assertTrue(backdated.sessionId != open.id)
    }

    @Test
    fun forceNewSessionToggleCreatesAFreshSessionEvenWhenOneIsOpen() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val open = sessions.open()!!

        now = MONDAY + 1.hours.inWholeMilliseconds
        assertTrue(viewModel.onPerformedAtChanged(now))
        viewModel.onForceNewSessionChanged(true)
        viewModel.log()
        advanceUntilIdle()

        assertEquals(open, sessions.open())
        assertEquals(2, sessions.all().size)
        val backdated = logs.all().single { it.performedAtMillis == now }
        assertTrue(backdated.sessionId != open.id)
    }

    @Test
    fun backdatedTargetReflectsTheOpenSessionAndTheForceToggle() = runTest(dispatcher) {
        var now = MONDAY
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions) { now }
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()
        val open = sessions.open()!!

        now = MONDAY + 1.hours.inWholeMilliseconds
        viewModel.onPerformedAtChanged(now)
        advanceUntilIdle()
        assertEquals(open, viewModel.state.value.backdatedTargetSession)

        viewModel.onForceNewSessionChanged(true)
        assertNull(viewModel.state.value.backdatedTargetSession)
    }

    @Test
    fun backdatedDraftBatchSharesTheChosenTimeAndSession() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = viewModel(
            repository = logs,
            sessionRepository = sessions,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()
        val chosen = MONDAY - 3 * DAY

        viewModel.onPerformedAtChanged(chosen)
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        if (viewModel.state.value.missingLoadPrompt != null) {
            viewModel.logMissingLoadWithoutWeight()
            advanceUntilIdle()
        }

        assertEquals(3, logs.all().size)
        assertTrue(logs.all().all { it.performedAtMillis == chosen })
        assertEquals(1, logs.all().map { it.sessionId }.toSet().size)
        assertEquals(1, sessions.all().size)
        // The created backdated session is written closed at the chosen time.
        assertEquals(chosen, sessions.all().single().endedAtMillis)
    }

    @Test
    fun backdatedTimeStampsTheLoggedSetAndPersists() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()
        val chosen = MONDAY - 2 * DAY

        assertTrue(viewModel.onPerformedAtChanged(chosen))
        assertTrue(viewModel.state.value.isBackdated)

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()

        assertEquals(chosen, repository.all().single().performedAtMillis)
        assertEquals(
            WorkoutTimingProvenance.CATCH_UP,
            repository.all().single().timingProvenance
        )
        // The chosen time persists until it is cleared, so the next set is still backdated.
        assertEquals(chosen, viewModel.state.value.performedAtMillis)
    }

    @Test
    fun clearingTheBackdatedTimeRevertsToNow() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onPerformedAtChanged(MONDAY - DAY)
        assertTrue(viewModel.onPerformedAtChanged(null))
        assertNull(viewModel.state.value.performedAtMillis)
        assertFalse(viewModel.state.value.isBackdated)

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()

        assertEquals(MONDAY, repository.all().single().performedAtMillis)
        assertEquals(
            WorkoutTimingProvenance.UNKNOWN,
            repository.all().single().timingProvenance
        )
    }

    @Test
    fun rejectsAFutureBackdatedTimeAndKeepsTheCurrentSelection() = runTest(dispatcher) {
        val viewModel = viewModel(timeMillis = MONDAY)
        advanceUntilIdle()

        assertFalse(viewModel.onPerformedAtChanged(MONDAY + 1))
        assertNull(viewModel.state.value.performedAtMillis)

        val past = MONDAY - DAY
        assertTrue(viewModel.onPerformedAtChanged(past))
        assertFalse(viewModel.onPerformedAtChanged(MONDAY + 1))
        assertEquals(past, viewModel.state.value.performedAtMillis)
    }

    @Test
    fun aDraftBatchSharesTheBackdatedTime() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"))
        )
        advanceUntilIdle()
        val chosen = MONDAY - 3 * DAY

        viewModel.onPerformedAtChanged(chosen)
        viewModel.confirmDraft(viewModel.state.value.draftSets.single())
        advanceUntilIdle()
        if (viewModel.state.value.missingLoadPrompt != null) {
            viewModel.logMissingLoadWithoutWeight()
            advanceUntilIdle()
        }

        assertEquals(3, repository.all().size)
        assertTrue(repository.all().all { it.performedAtMillis == chosen })
    }

    @Test
    fun changingDraftTimeDoesNotReuseThePreviouslyResolvedBackdatedSession() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val plan = acceptedPlan(
            listOf("back-squat", "bench-press"),
            suggestedWeightKg = 80.0
        )
        val planned = viewModel(
            repository = repository,
            timeMillis = MONDAY,
            acceptedPlan = plan
        )
        advanceUntilIdle()
        val drafts = planned.state.value.draftSets
        val firstTime = MONDAY - DAY
        val secondTime = MONDAY - 2 * DAY

        planned.editDraft(drafts.first())
        assertTrue(planned.onDraftPerformedAtChanged(firstTime))
        planned.confirmDraftEdit()
        advanceUntilIdle()
        val secondDraft = planned.state.value.draftSets.single()
        planned.editDraft(secondDraft)
        assertTrue(planned.onDraftPerformedAtChanged(secondTime))
        planned.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(
            setOf(firstTime, secondTime),
            repository.all().map { it.performedAtMillis }.toSet()
        )
        assertEquals(2, repository.all().map { it.sessionId }.toSet().size)
    }

    @Test
    fun anEqualTimestampBatchDisplaysInAStableOrder() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()
        val chosen = MONDAY - DAY

        viewModel.onPerformedAtChanged(chosen)
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        // The manual log guard refuses a second submission until the first set and its refresh
        // complete, so advance between sets as the UI does.
        viewModel.log()
        advanceUntilIdle()
        viewModel.log()
        advanceUntilIdle()
        viewModel.log()
        advanceUntilIdle()

        // performedAt desc, ties broken by newest insertion (id desc).
        assertEquals(listOf(3L, 2L, 1L), viewModel.state.value.recentSets.map { it.id })
    }

    @Test
    fun recentSetsOrderByTimeThenNewestInsertionOnATie() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 200L
                ),
                WorkoutSet(
                    id = 2L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 200L
                ),
                WorkoutSet(
                    id = 3L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 100L
                )
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        assertEquals(listOf(2L, 1L, 3L), viewModel.state.value.recentSets.map { it.id })
    }

    @Test
    fun recentSessionBoundariesUseOnlyAdjacentExplicitIdentities() = runTest(dispatcher) {
        val cases = listOf(
            listOf("a", "a", "b", "a") to listOf(true, false, true, true),
            listOf(null, null) to listOf(false, false),
            listOf(null, "a", null, null, "a", "a") to
                listOf(false, true, false, false, true, false)
        )
        for ((identities, expected) in cases) {
            val sets = identities.mapIndexed { index, sessionId ->
                WorkoutSet(
                    id = (identities.size - index).toLong(),
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 200L,
                    sessionId = sessionId
                )
            }
            val viewModel = viewModel(repository = FakeWorkoutLogRepository(sets.reversed()))
            advanceUntilIdle()
            val rows = viewModel.state.value.recentSets
            assertEquals(sets.map { it.id }, rows.map { it.id })
            assertEquals(identities, rows.map { it.sessionId })
            assertEquals(
                expected,
                rows.mapIndexed { index, row ->
                    row.startsSessionAfter(rows.getOrNull(index - 1))
                }
            )
        }
    }

    @Test
    fun correctionAndDeletionRefreshAdjacentSessionBoundaries() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            listOf("a", "b", "a").mapIndexed { index, sessionId ->
                WorkoutSet(
                    id = index + 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 100.0,
                    performedAtMillis = 3_000L - index * 1_000L,
                    sessionId = sessionId
                )
            }
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        fun boundaries(): List<Boolean> {
            val rows = viewModel.state.value.recentSets
            return rows.mapIndexed { index, row ->
                row.startsSessionAfter(rows.getOrNull(index - 1))
            }
        }
        assertEquals(listOf(true, true, true), boundaries())

        assertTrue(viewModel.correctSetTime(2L, 0L, 0, 0))
        advanceUntilIdle()
        assertEquals(listOf(1L, 3L, 2L), viewModel.state.value.recentSets.map { it.id })
        assertEquals(listOf(true, false, true), boundaries())

        viewModel.deleteSet(1L)
        advanceUntilIdle()
        assertEquals(listOf(3L, 2L), viewModel.state.value.recentSets.map { it.id })
        assertEquals(listOf(true, true), boundaries())
        viewModel.deleteSet(2L)
        advanceUntilIdle()
        assertEquals(listOf(true), boundaries())
    }

    @Test
    fun anAppliedGuidedWeightKeepsItsKilogramsAcrossAUnitChange() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val units = MutableStateFlow(WeightUnit.KG)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            weightUnitFlow = units
        )
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftWeightChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()

        // The applied value is canonical kilograms, so switching display units only changes text.
        units.value = WeightUnit.LB
        runCurrent()
        viewModel.editDraft(viewModel.state.value.draftSets.single())
        assertEquals("264.6", viewModel.state.value.draftEdit?.weightInput)
        viewModel.confirmDraftEdit()
        runCurrent()
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        assertEquals(120.0, repository.all().single().weightKg)
    }

    @Test
    fun anAppliedPoundWeightKeepsItsPoundsAcrossAUnitChange() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val units = MutableStateFlow(WeightUnit.LB)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            weightUnitFlow = units
        )
        runCurrent()

        viewModel.editDraft(viewModel.state.value.draftSets.single())
        viewModel.onDraftWeightChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()

        units.value = WeightUnit.KG
        runCurrent()
        viewModel.editDraft(viewModel.state.value.draftSets.single())
        assertEquals(
            formatWeight(WeightUnit.KG.kilogramsToDisplay(WeightUnit.LB.displayToKilograms(120.0))),
            viewModel.state.value.draftEdit?.weightInput
        )
        viewModel.confirmDraftEdit()
        runCurrent()
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        assertEquals(WeightUnit.LB.displayToKilograms(120.0), repository.all().single().weightKg)
    }

    @Test
    fun reopeningAnAppliedGuidedEditRetainsRirAndPerformedTime() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()
        val performedAt = MONDAY - 60_000L

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("10")
        viewModel.onDraftRirChanged("2")
        viewModel.onDraftPerformedAtChanged(performedAt)
        viewModel.confirmDraftEdit()
        runCurrent()

        viewModel.editDraft(viewModel.state.value.draftSets.single())
        val reopened = assertNotNull(viewModel.state.value.draftEdit)
        assertEquals("2", reopened.rir)
        assertEquals(performedAt, reopened.performedAtMillis)
        assertTrue(reopened.performedAtExplicit)
    }

    @Test
    fun resettingAnAppliedGuidedEditRestoresTheUneditedSource() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("10")
        viewModel.onDraftWeightChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()

        viewModel.editDraft(viewModel.state.value.draftSets.single())
        viewModel.resetDraftEdit()
        val reset = assertNotNull(viewModel.state.value.draftEdit)
        assertEquals("8", reset.reps)
        assertEquals("100", reset.weightInput)
        assertEquals("", reset.rir)

        viewModel.confirmDraftEdit()
        runCurrent()
        val draft = viewModel.state.value.draftSets.single()
        assertEquals(8, draft.reps)
        assertEquals(100.0, draft.weightKg)
    }

    @Test
    fun anAppliedGuidedEditIsDroppedWhenTheSameEntryIsReplaced() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("10")
        viewModel.onDraftWeightChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()

        // The replacement keeps the entry id but changes the exercise and its prescription.
        val replaced = twoOccurrences().map { occurrence ->
            if (occurrence.id != 30L) return@map occurrence
            occurrence.copy(
                revision = occurrence.revision + 1,
                entries = occurrence.entries.map { entry ->
                    entry.copy(
                        exerciseId = "deadlift",
                        exerciseName = "Deadlift",
                        reps = 5,
                        weightKg = 140.0
                    )
                }
            )
        }
        schedule.set(blockActivation(), replaced, selectedOccurrenceId = 30L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.appliedGuidedEdits.isEmpty())
        val draft = viewModel.state.value.draftSets.single()
        assertEquals(5, draft.reps)
        assertEquals(140.0, draft.weightKg)
    }

    @Test
    fun guidedOrderedWritesFollowEachRemainingTargetThenTheLast() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), orderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.confirmGuidedSet(40L)
        runCurrent()
        val first = repository.all().single()
        assertEquals(10, first.reps)
        assertEquals(60.0, first.weightKg)
        assertEquals(0, first.prescriptionSnapshot?.setIndex)

        viewModel.confirmGuidedSet(40L)
        runCurrent()
        val second = repository.all().last()
        assertEquals(8, second.reps)
        assertEquals(70.0, second.weightKg)
        assertEquals(1, second.prescriptionSnapshot?.setIndex)

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        val third = repository.all().last()
        assertEquals(6, third.reps)
        assertEquals(80.0, third.weightKg)
        assertEquals(2, third.prescriptionSnapshot?.setIndex)
        assertEquals(3, repository.all().size)
    }

    @Test
    fun aGuidedOrderedWriteFailureRetriesTheSameFirstTarget() = runTimerTest {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), orderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.confirmGuidedSet(40L)
        runCurrent()
        assertTrue(repository.all().isEmpty())
        assertTrue(viewModel.state.value.guidedSetWriteFailed)

        viewModel.confirmGuidedSet(40L)
        runCurrent()
        val logged = repository.all().single()
        assertEquals(10, logged.reps)
        assertEquals(60.0, logged.weightKg)
        assertEquals(0, logged.prescriptionSnapshot?.setIndex)
    }

    @Test
    fun anAppliedTimedOrderedEditLogsItsOwnSecondsOnceThenResumesTheNextTarget() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOrderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        assertEquals("60", viewModel.state.value.draftEdit?.durationSeconds)
        viewModel.onDraftDurationSecondsChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()
        assertEquals(120, viewModel.state.value.draftSets.single().durationSeconds)

        viewModel.confirmGuidedSet(40L)
        runCurrent()

        // The edited seconds are logged once and the set does not claim the frozen 60-second target.
        val logged = repository.all().single()
        assertEquals(120, logged.durationSeconds)
        assertNull(logged.reps)
        assertNull(assertNotNull(logged.prescriptionSnapshot).setIndex)

        // The next untouched set follows its own ordered target again.
        val remaining = viewModel.state.value.draftSets.single()
        assertEquals(2, remaining.sets)
        assertEquals(90, remaining.durationSeconds)
        viewModel.confirmGuidedSet(40L)
        runCurrent()
        val second = repository.all().last()
        assertEquals(90, second.durationSeconds)
        assertEquals(1, second.prescriptionSnapshot?.setIndex)
    }

    @Test
    fun aFailedAppliedTimedOrderedEditRetriesTheEditedSecondsOnce() = runTimerTest {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), timedOrderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftDurationSecondsChanged("120")
        viewModel.confirmDraftEdit()
        runCurrent()
        viewModel.confirmGuidedSet(40L)
        runCurrent()
        assertTrue(repository.all().isEmpty())
        assertTrue(viewModel.state.value.guidedSetWriteFailed)

        viewModel.confirmGuidedSet(40L)
        runCurrent()
        val logged = repository.all().single()
        assertEquals(120, logged.durationSeconds)
        assertNull(assertNotNull(logged.prescriptionSnapshot).setIndex)
        assertEquals(90, viewModel.state.value.draftSets.single().durationSeconds)
    }

    @Test
    fun equalAdjacentOrderedTargetsExpireTheConsumedSetsEditorAndOverlay() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), equalOrderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        val first = viewModel.state.value.draftSets.single()
        assertEquals(3, first.sets)
        viewModel.editGuidedSet(40L)
        assertNotNull(viewModel.state.value.draftEdit)

        // Logging the open row consumes this set once; the next set has identical values but a
        // different position, so the consumed editor must not survive the refresh.
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        assertEquals(1, repository.all().size)
        assertNull(viewModel.state.value.draftEdit)
        assertTrue(viewModel.state.value.appliedGuidedEdits.isEmpty())
        val remaining = viewModel.state.value.draftSets.single()
        assertEquals(2, remaining.sets)
        assertEquals(10, remaining.reps)
        assertEquals(60.0, remaining.weightKg)

        // The next target is value-identical but a different position: editing and consuming it
        // must expire the same way rather than replay onto the third set.
        viewModel.editGuidedSet(40L)
        viewModel.onDraftWeightChanged("65")
        viewModel.confirmDraftEdit()
        runCurrent()
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        assertEquals(2, repository.all().size)
        assertEquals(65.0, repository.all().last().weightKg)
        assertNull(viewModel.state.value.draftEdit)
        assertTrue(viewModel.state.value.appliedGuidedEdits.isEmpty())
        val last = viewModel.state.value.draftSets.single()
        assertEquals(1, last.sets)
        assertEquals(8, last.reps)
        assertEquals(70.0, last.weightKg)
    }

    @Test
    fun anOpenDraftEditorKeepsItsCapturedUnitForTheLabelAndParserInBothDirections() =
        runTest(dispatcher) {
            data class Case(val captured: WeightUnit, val switched: WeightUnit, val typed: String)

            listOf(
                Case(WeightUnit.KG, WeightUnit.LB, "100"),
                Case(WeightUnit.LB, WeightUnit.KG, "100")
            ).forEach { case ->
                val repository = FakeWorkoutLogRepository()
                val schedule = MutableWorkoutScheduleRepository()
                schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
                val units = MutableStateFlow(case.captured)
                val viewModel = occurrenceViewModel(
                    repository,
                    schedule,
                    weightUnit = case.captured,
                    weightUnitFlow = units
                )
                advanceUntilIdle()

                viewModel.editDraft(viewModel.state.value.draftSets.single())
                units.value = case.switched
                advanceUntilIdle()

                // The already-open editor's label source and parser both stay on the captured unit.
                assertEquals(case.captured, viewModel.state.value.draftEdit?.weightUnit)
                viewModel.onDraftWeightChanged(case.typed)
                viewModel.confirmDraftEdit()
                advanceUntilIdle()

                val logged = repository.all()
                assertEquals(2, logged.size)
                val expectedKg = case.captured.displayToKilograms(case.typed.toDouble())
                assertTrue(logged.all { it.weightKg == expectedKg })
            }
        }

    @Test
    fun anAppliedOrderedEditLogsOneOverriddenSetThenResumesThePlan() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), orderedOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("12")
        viewModel.onDraftWeightChanged("65")
        viewModel.confirmDraftEdit()
        runCurrent()

        viewModel.confirmGuidedSet(40L)
        runCurrent()
        val logged = repository.all().single()
        assertEquals(12, logged.reps)
        assertEquals(65.0, logged.weightKg)
        assertNull(logged.prescriptionSnapshot?.setIndex)

        val remaining = viewModel.state.value.draftSets.single()
        assertEquals(8, remaining.reps)
        assertEquals(70.0, remaining.weightKg)
    }

    @Test
    fun skippingAStartedPairedMemberCancelsItsLiveTiming() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), supersetOccurrences(), selectedOccurrenceId = 30L)
        var elapsedNow = 10_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        runCurrent()

        viewModel.startGuidedSet(40L)
        assertEquals(40L, viewModel.state.value.startedSetEntryId)
        assertEquals(40L, viewModel.state.value.nextGuidedEntryId)

        viewModel.skipSupersetTurn(40L)
        runCurrent()

        assertNull(viewModel.state.value.startedSetEntryId)
        assertEquals(40L, viewModel.state.value.supersetSkippedEntryId)
        assertEquals(42L, viewModel.state.value.nextGuidedEntryId)
        assertTrue(repository.all().isEmpty())

        // The skipped start is gone: completing it now must write nothing.
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertTrue(repository.all().isEmpty())

        // The partner starts its own fresh timing; no stale duration from A is carried over.
        elapsedNow = 50_000L
        viewModel.startGuidedSet(42L)
        elapsedNow = 50_500L
        viewModel.confirmGuidedSetNow(42L)
        runCurrent()
        val logged = repository.all().single()
        assertEquals(50_000L, logged.startedAtElapsedMillis)
        assertEquals(50_500L, logged.completedAtElapsedMillis)
        assertEquals(500L, logged.completedAtElapsedMillis!! - logged.startedAtElapsedMillis!!)

        // A returns with no live start and needs a fresh Start before it can log.
        assertNull(viewModel.state.value.startedSetEntryId)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(1, repository.all().size)
    }

    private fun supersetOccurrences() = listOf(
        twoOccurrences().first().copy(
            entries = listOf(
                OccurrenceEntry(
                    id = 40L,
                    sourceActivationEntryId = 20L,
                    position = 0,
                    exerciseId = "back-squat",
                    exerciseName = "Back Squat",
                    movementPattern = MovementPattern.SQUAT,
                    sets = 2,
                    reps = 8,
                    weightKg = 100.0,
                    superset = Superset("arms", 10, 120)
                ),
                OccurrenceEntry(
                    id = 42L,
                    sourceActivationEntryId = 21L,
                    position = 1,
                    exerciseId = "deadlift",
                    exerciseName = "Deadlift",
                    movementPattern = MovementPattern.HINGE,
                    sets = 2,
                    reps = 5,
                    weightKg = 140.0,
                    superset = Superset("arms", 10, 120)
                )
            )
        )
    )

    @Test
    fun manualStationSelectionIsWrittenWithFrozenAttribution() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val station = EquipmentStation(
            id = 7L,
            profileId = 1L,
            equipmentId = EquipmentTag.BARBELL,
            name = "Rack A",
            loadBasis = StationLoadBasis.TOTAL
        )
        val profileState = EquipmentProfileState(
            EquipmentProfileContext(1L, 0L),
            listOf(
                EquipmentProfile(
                    id = 1L,
                    name = "Home",
                    selected = setOf(EquipmentTag.BARBELL),
                    maxWeightsKg = emptyMap(),
                    loadingInventory = LoadingInventory(),
                    stations = listOf(station)
                )
            )
        )
        val equipment = WorkoutLoggerEquipment(
            object : EquipmentProfileRepository {
                override fun observe() = flowOf(profileState)
                override suspend fun current() = profileState
                override suspend fun create(name: String, copyFrom: Long?): Long = error("unused")
                override suspend fun rename(context: EquipmentProfileContext, name: String) =
                    error("unused")
                override suspend fun activate(context: EquipmentProfileContext, profileId: Long) =
                    error("unused")
                override suspend fun delete(
                    context: EquipmentProfileContext,
                    profileId: Long,
                    replacementId: Long?
                ) = error("unused")
                override suspend fun saveEquipment(
                    context: EquipmentProfileContext,
                    selected: Set<EquipmentTag>,
                    maxWeightsKg: Map<EquipmentTag, Double>
                ) = error("unused")
                override suspend fun saveLoadingInventory(
                    context: EquipmentProfileContext,
                    inventory: LoadingInventory
                ) = error("unused")
                override suspend fun saveStation(
                    context: EquipmentProfileContext,
                    station: EquipmentStation
                ): Long = error("unused")
                override suspend fun retireStation(
                    context: EquipmentProfileContext,
                    stationId: Long
                ) = error("unused")
            }
        )
        val viewModel = viewModel(repository = repository, equipment = equipment)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onStationSelected(7L)
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()

        assertEquals(
            StationAttribution(7L, "Rack A", StationLoadBasis.TOTAL),
            repository.all().single().station
        )
    }

    @Test
    fun forceSetLogsItsNewtonsWithoutAWeightOrDevice() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        // A weight retained from before the metric switch must never ride along with a force reading.
        viewModel.onWeightChanged("60")
        viewModel.onMetricChanged(SetMetric.FORCE)
        viewModel.onForceChanged("250")
        assertTrue(viewModel.state.value.canLog)

        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertEquals(250.0, logged.forceNewtons)
        assertNull(logged.weightKg)
        assertNull(logged.device)
        assertNull(logged.reps)
        assertNull(logged.durationSeconds)
        assertNull(logged.distanceMeters)
        val row = viewModel.state.value.recentSets.single()
        assertEquals(250.0, row.forceNewtons)
        assertNull(row.weightKg)
        // A force row never reads through the load formatter: it holds no load to render.
        assertEquals(LoadDisplay.None, loadDisplayFor(row.loadKind, row.weightKg))
    }

    @Test
    fun forceKeepsTheExercisesOwnLoadIdentityInsteadOfBecomingExternal() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        // Force is authorable on a bodyweight-only exercise, and its set keeps that identity rather
        // than claiming an external load it never carried.
        viewModel.onExerciseSelected("plank")
        advanceUntilIdle()
        viewModel.onMetricChanged(SetMetric.FORCE)
        viewModel.onForceChanged("250")
        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertEquals(250.0, logged.forceNewtons)
        assertEquals(LoadKind.BODYWEIGHT, logged.loadKind)
        assertNull(logged.weightKg)
    }

    @Test
    fun forceNeverBecomesAddedLoadOnARevealedBodyweightExercise() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("pull-up")
        advanceUntilIdle()
        viewModel.onRevealWeight()
        viewModel.onWeightChanged("10")
        viewModel.onMetricChanged(SetMetric.FORCE)
        viewModel.onForceChanged("180")
        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertEquals(180.0, logged.forceNewtons)
        assertEquals(LoadKind.BODYWEIGHT, logged.loadKind)
        assertNull(logged.weightKg)
    }

    @Test
    fun forceSetCarriesAnOptionalDescriptiveDevice() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onMetricChanged(SetMetric.FORCE)
        viewModel.onForceChanged("180")
        viewModel.onDeviceNameChanged("Grip trainer")
        viewModel.onDeviceRatingChanged("45")
        assertTrue(viewModel.state.value.deviceIsValid)
        assertTrue(viewModel.state.value.canLog)

        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertEquals(180.0, logged.forceNewtons)
        assertEquals(DeviceAttribution("Grip trainer", 45.0), logged.device)
        assertNull(logged.weightKg)
        assertEquals(
            DeviceAttribution("Grip trainer", 45.0),
            viewModel.state.value.recentSets.single().device
        )
    }

    @Test
    fun switchingFromForceToRepetitionsClearsTheDeviceAndNewtons() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onMetricChanged(SetMetric.FORCE)
        viewModel.onForceChanged("200")
        viewModel.onDeviceNameChanged("Hand dynamometer")
        viewModel.onDeviceRatingChanged("50")

        viewModel.onMetricChanged(SetMetric.REPETITIONS)
        assertEquals("", viewModel.state.value.forceNewtons)
        assertEquals("", viewModel.state.value.deviceName)
        assertEquals("", viewModel.state.value.deviceRatingKg)

        viewModel.onRepsChanged("8")
        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertEquals(8, logged.reps)
        assertNull(logged.forceNewtons)
        assertNull(logged.device)
    }

    @Test
    fun aDeviceRatingWithoutANameBlocksAForceSet() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onMetricChanged(SetMetric.FORCE)
        viewModel.onForceChanged("200")
        viewModel.onDeviceRatingChanged("45")
        assertFalse(viewModel.state.value.deviceIsValid)
        assertFalse(viewModel.state.value.canLog)

        viewModel.log()
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun correctingASetToForceClearsItsRepsAndLoad() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 60.0,
                    performedAtMillis = 1L
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        assertNotNull(viewModel.state.value.loggedSetEdit)
        viewModel.onLoggedSetMetricChanged(SetMetric.FORCE)
        viewModel.onLoggedSetForceChanged("320")
        viewModel.onLoggedSetDeviceNameChanged("Hand dynamometer")
        viewModel.onLoggedSetDeviceRatingChanged("60")
        assertTrue(viewModel.state.value.loggedSetEdit!!.canSave)

        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        val corrected = repository.all().single()
        assertEquals(320.0, corrected.forceNewtons)
        assertEquals(DeviceAttribution("Hand dynamometer", 60.0), corrected.device)
        assertNull(corrected.reps)
        assertNull(corrected.weightKg)
    }

    @Test
    fun correctingAForceSetToRepetitionsClearsTheDevice() = runTest(dispatcher) {
        val device = DeviceAttribution("Hand dynamometer", 60.0)
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    forceNewtons = 320.0,
                    device = device,
                    weightKg = null,
                    performedAtMillis = 1L
                )
            )
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        val edit = viewModel.state.value.loggedSetEdit!!
        assertEquals(SetMetric.FORCE, edit.metric)
        assertEquals(device.name, edit.deviceName)
        assertEquals("60", edit.deviceRatingKg)

        viewModel.onLoggedSetMetricChanged(SetMetric.REPETITIONS)
        assertEquals("", viewModel.state.value.loggedSetEdit!!.deviceName)
        viewModel.onLoggedSetRepsChanged("6")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        val corrected = repository.all().single()
        assertEquals(6, corrected.reps)
        assertNull(corrected.forceNewtons)
        assertNull(corrected.device)
        assertNull(corrected.weightKg)
    }

    @Test
    fun aGuidedClusterDraftExposesItsPrescribedSegments() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), clusterOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        val draft = viewModel.state.value.draftSets.single()
        assertEquals(1, draft.sets)
        assertEquals(ClusterKind.DROP_SET, assertNotNull(draft.cluster).kind)

        viewModel.editGuidedSet(40L)
        val edit = assertNotNull(viewModel.state.value.draftEdit)
        // Segment 1 stays the form's own reps/load fields; only the later segments are listed.
        assertEquals(listOf("6", "4"), edit.laterSegments.map { it.reps })
        assertEquals(listOf("80", "60"), edit.laterSegments.map { it.weightInput })
        assertEquals(1, viewModel.state.value.draftSets.single().sets)
    }

    @Test
    fun confirmedSegmentsSurvivePauseAndResumeWithoutDuplicatingTheParent() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), clusterOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()
        viewModel.openClusterLog(viewModel.state.value.draftSets.single())
        runCurrent()
        viewModel.saveClusterSegment()
        viewModel.saveClusterSegment() // The same in-flight action is ignored.
        runCurrent()
        val parent = repository.all().single()
        assertEquals(1, parent.cluster?.performedSegments)
        assertEquals(false, parent.cluster?.isComplete)
        assertEquals(1, repository.clusterSegments(parent.id).size)
        viewModel.closeClusterLog()
        val recreated = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()
        recreated.openClusterLog(recreated.state.value.draftSets.single())
        runCurrent()
        assertEquals(1, assertNotNull(recreated.state.value.clusterLog).position)
        recreated.saveClusterSegment()
        runCurrent()
        assertEquals(2, repository.clusterSegments(parent.id).size)
        recreated.saveClusterSegment()
        runCurrent()
        assertNull(recreated.state.value.clusterLog)
        assertEquals(1, repository.all().size)
        assertEquals(true, repository.all().single().cluster?.isComplete)
        assertEquals(listOf(0, 1, 2), repository.clusterSegments(parent.id).map { it.position })
    }

    @Test
    fun loggingACompleteClusterWritesTheParentAndEverySegmentAsOneSet() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), clusterOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.confirmGuidedSet(40L)
        runCurrent()

        val parent = repository.all().single()
        assertEquals(8, parent.reps)
        assertEquals(100.0, parent.weightKg)
        assertEquals(30L, parent.occurrenceId)
        assertEquals(40L, parent.occurrenceEntryId)
        assertEquals(WorkoutClusterSummary(ClusterKind.DROP_SET, 3, 3), parent.cluster)
        val cluster = repository.clusters.single()
        assertEquals(
            listOf(8 to 100.0, 6 to 80.0, 4 to 60.0),
            cluster.segments.map { it.reps to it.weightKg }
        )
        assertEquals(listOf(0, 1, 2), cluster.segments.map { it.position })
    }

    @Test
    fun editingARecordedClusterShowsAndCorrectsItsSegmentsWithoutChangingTheSetCount() =
        runTimerTest {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), clusterOccurrences(), selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(repository, schedule, guided = true)
            runCurrent()

            viewModel.confirmGuidedSet(40L)
            runCurrent()

            val row = viewModel.state.value.recentSets.single()
            assertEquals(3, assertNotNull(row.cluster).performedSegments)

            viewModel.editLoggedSet(row)
            runCurrent()

            val edit = assertNotNull(viewModel.state.value.loggedSetEdit)
            assertEquals(listOf("6", "4"), edit.clusterSegments.map { it.reps })
            assertEquals(listOf("80", "60"), edit.clusterSegments.map { it.weightInput })

            viewModel.onLoggedSetClusterWeightChanged(1, "75")
            viewModel.saveLoggedSetEdit()
            runCurrent()

            val corrected = repository.clusterSegments(row.id)
            assertEquals(3, corrected.size)
            assertEquals(listOf(100.0, 75.0, 60.0), corrected.map { it.weightKg })
            assertEquals(listOf(0, 1, 2), corrected.map { it.position })
            assertEquals(1, repository.all().size)
        }

    @Test
    fun anOrdinaryRepDraftIsStillLoggedAsOneOrdinarySet() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.confirmGuidedSet(40L)
        runCurrent()

        val set = repository.all().single()
        assertEquals(8, set.reps)
        assertEquals(100.0, set.weightKg)
        assertNull(set.cluster)
        assertTrue(repository.clusters.isEmpty())
    }

    @Test
    fun anIncompleteOrNonPositiveSegmentRefusesTheClusterWithoutWriting() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), clusterOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftSegmentWeightChanged(1, "")
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        assertEquals(ClusterDraftError.MISSING_SEGMENT, viewModel.state.value.clusterDraftError)
        assertTrue(repository.all().isEmpty())
        assertTrue(repository.clusters.isEmpty())
        // The draft and its editor stay in place, and a cluster never changes the draft's set count.
        assertEquals(1, viewModel.state.value.draftSets.single().sets)
        assertNotNull(viewModel.state.value.draftEdit)
        assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())

        viewModel.onDraftSegmentRepsChanged(0, "0")
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        assertEquals(
            ClusterDraftError.NON_POSITIVE_SEGMENT,
            viewModel.state.value.clusterDraftError
        )
        assertTrue(repository.all().isEmpty())
        assertTrue(repository.clusters.isEmpty())
    }

    @Test
    fun aSegmentOrderThatContradictsTheClusterIsRefused() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), clusterOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        // A drop set needs a strictly lower load on each later segment.
        viewModel.onDraftSegmentWeightChanged(0, "120")
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        assertEquals(ClusterDraftError.INVALID_CLUSTER, viewModel.state.value.clusterDraftError)
        assertTrue(repository.all().isEmpty())
        assertTrue(repository.clusters.isEmpty())
        assertTrue(viewModel.state.value.draftWriteRetries.isEmpty())
        assertEquals(1, viewModel.state.value.draftSets.single().sets)
    }

    @Test
    fun aCorrectedSegmentIsLoggedInsteadOfThePrescription() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), clusterOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftSegmentWeightChanged(0, "70")
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        val cluster = repository.clusters.single()
        assertEquals(
            listOf(8 to 100.0, 6 to 70.0, 4 to 60.0),
            cluster.segments.map { it.reps to it.weightKg }
        )
        assertEquals(
            WorkoutClusterSummary(ClusterKind.DROP_SET, 3, 3),
            repository.all().single().cluster
        )
    }

    private fun viewModel(
        repository: WorkoutLogRepository = FakeWorkoutLogRepository(),
        sessionRepository: WorkoutSessionRepository = FakeWorkoutSessionRepository(),
        timeMillis: Long = 1_000L,
        acceptedPlan: AcceptedPlan? = null,
        history: FakePlanHistoryRepository = FakePlanHistoryRepository(acceptedPlan),
        catalog: ExerciseCatalog = FakeExerciseCatalog,
        weightUnit: WeightUnit = WeightUnit.KG,
        weightUnitFlow: MutableStateFlow<WeightUnit>? = null,
        distanceUnitFlow: MutableStateFlow<DistanceUnit>? = null,
        noteRepository: ExerciseNoteRepository = FakeExerciseNoteRepository(),
        sessionNoteRepository: SessionNoteRepository = FakeSessionNoteRepository(),
        equipment: WorkoutLoggerEquipment = testEquipment()
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessionRepository),
        logWorkoutSet = logWorkoutSet(repository, sessionRepository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(
            history,
            repository,
            sessionRepository = sessionRepository,
            sessionNoteRepository = sessionNoteRepository,
            catalog = catalog,
            timeMillis = timeMillis
        ),
        exerciseCatalog = catalog,
        runtime = WorkoutLoggerRuntime(TimeProvider { timeMillis }),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(weightUnitFlow ?: MutableStateFlow(weightUnit)),
            FakeGuidedWorkoutPreferenceRepository(false),
            FakeRestPreferenceRepository(),
            FakeTrainingGuidancePreferences(),
            distanceUnitRepository = distanceUnitFlow?.let(::FakeDistanceUnitRepository)
        ),
        exerciseNoteRepository = noteRepository,
        sessionNoteRepository = sessionNoteRepository,
        warmupOverrideRepository = FakeWarmupPreparationOverrideRepository(),
        equipment = equipment
    )

    private fun sessionViewModel(
        repository: WorkoutLogRepository,
        sessionRepository: WorkoutSessionRepository,
        noteRepository: ExerciseNoteRepository = FakeExerciseNoteRepository(),
        sessionNoteRepository: SessionNoteRepository = FakeSessionNoteRepository(),
        now: () -> Long
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessionRepository),
        logWorkoutSet = logWorkoutSet(repository, sessionRepository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(
            FakePlanHistoryRepository(null),
            repository,
            sessionRepository = sessionRepository,
            sessionNoteRepository = sessionNoteRepository
        ),
        exerciseCatalog = FakeExerciseCatalog,
        runtime = WorkoutLoggerRuntime(TimeProvider { now() }),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(WeightUnit.KG),
            FakeGuidedWorkoutPreferenceRepository(false),
            FakeRestPreferenceRepository(),
            FakeTrainingGuidancePreferences()
        ),
        exerciseNoteRepository = noteRepository,
        sessionNoteRepository = sessionNoteRepository,
        warmupOverrideRepository = FakeWarmupPreparationOverrideRepository(),
        equipment = testEquipment()
    )

    /** The set use case the ViewModel writes clusters through; production shares one singleton. */
    private fun logWorkoutSet(
        repository: WorkoutLogRepository,
        sessionRepository: WorkoutSessionRepository
    ): LogWorkoutSetUseCase = LogWorkoutSetUseCase(
        repository = repository,
        startWorkoutSession = StartWorkoutSessionUseCase(sessionRepository),
        endWorkoutSession = EndWorkoutSessionUseCase(sessionRepository),
        observeOpenWorkoutSession = ObserveOpenWorkoutSessionUseCase(sessionRepository),
        config = SessionConfig()
    )

    private fun logMutations(
        repository: WorkoutLogRepository,
        sessionRepository: WorkoutSessionRepository
    ): WorkoutLogMutations {
        val resegmenter = object : SessionResegmenter {
            override suspend fun resegmentAfterTimeCorrection(
                setId: Long,
                performedAtMillis: Long,
                utcOffsetMillis: Long
            ) {
                repository.updateSetPerformedAt(setId, performedAtMillis)
            }

            override suspend fun resegmentAfterSetCorrection(
                setId: Long,
                correction: WorkoutSetCorrection,
                utcOffsetMillis: Long
            ) {
                (repository as FakeWorkoutLogRepository).correct(setId, correction)
            }
        }
        return WorkoutLogMutations(
            logWorkoutSet = logWorkoutSet(repository, sessionRepository),
            deleteWorkoutSet = DeleteWorkoutSetUseCase(repository),
            correctWorkoutSetTime = CorrectWorkoutSetTimeUseCase(
                resegmenter = resegmenter,
                timeProvider = TimeProvider { 0L }
            ),
            correctWorkoutSet = CorrectWorkoutSetUseCase(resegmenter, TimeProvider { MONDAY })
        )
    }

    @Test
    fun logsSetsAgainstTheActiveOccurrenceWithoutAdvancingTheQueue() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)

        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        assertEquals("Upper", viewModel.state.value.activeOccurrence?.workoutName)

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()

        val set = repository.all().single()
        assertEquals(30L, set.occurrenceId)
        assertEquals(40L, set.occurrenceEntryId)
        assertEquals(30L, schedule.scheduleState().selectedOccurrenceId)
        assertEquals(1, viewModel.state.value.activeOccurrence?.performedSets)
    }

    @Test
    fun recentSetEditorChangesTheSameSetAndLeavesTheNewLogInputAlone() = runTest(dispatcher) {
        val original = WorkoutSet(
            id = 7L,
            exerciseId = "back-squat",
            reps = 8,
            weightKg = 100.0,
            performedAtMillis = MONDAY,
            rir = 2,
            weekNumber = 3,
            cycleNumber = 2,
            dayIndex = 0,
            sessionId = "recorded-session",
            occurrenceId = 30L,
            occurrenceEntryId = 40L
        )
        val repository = FakeWorkoutLogRepository(listOf(original))
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()
        viewModel.onRepsChanged("5")
        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        assertEquals("8", viewModel.state.value.loggedSetEdit?.reps)
        assertEquals("2", viewModel.state.value.loggedSetEdit?.rir)
        viewModel.onLoggedSetRepsChanged("10")
        viewModel.onLoggedSetWeightChanged("0")
        viewModel.onLoggedSetRirChanged("")
        assertTrue(viewModel.onLoggedSetTimeChanged(MONDAY - DAY))
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()
        assertEquals(
            listOf(
                original.copy(
                    reps = 10,
                    weightKg = 0.0,
                    rir = null,
                    performedAtMillis = MONDAY - DAY
                )
            ),
            repository.all()
        )
        assertEquals("5", viewModel.state.value.reps)
        assertNull(viewModel.state.value.loggedSetEdit)
        assertEquals(10, viewModel.state.value.recentSets.single().reps)
    }

    @Test
    fun aSetReachedFromHistoryCorrectsTheSameStoredRowThroughTheSharedEditor() =
        runTest(dispatcher) {
            val key = WorkoutHistoryKey.Session("recorded-session")
            val recorded = WorkoutHistorySet(
                id = 7L,
                exerciseId = "back-squat",
                exerciseName = "Back Squat",
                reps = 8,
                weightKg = 100.0,
                loadKind = LoadKind.EXTERNAL,
                isWarmup = true,
                rir = 2,
                performedAtMillis = MONDAY,
                timingProvenance = WorkoutTimingProvenance.UNKNOWN,
                occurrenceId = 30L
            )
            val stored = WorkoutSet(
                id = 7L,
                exerciseId = "back-squat",
                reps = 8,
                weightKg = 100.0,
                performedAtMillis = MONDAY,
                isWarmup = true,
                rir = 2,
                sessionId = "recorded-session",
                occurrenceId = 30L
            )
            val repository = FakeWorkoutLogRepository(listOf(stored))
            val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
            advanceUntilIdle()

            viewModel.editLoggedSet(recorded.toLoggedSetRow(key))
            assertEquals("8", viewModel.state.value.loggedSetEdit?.reps)
            assertEquals("2", viewModel.state.value.loggedSetEdit?.rir)

            viewModel.onLoggedSetRepsChanged("10")
            viewModel.saveLoggedSetEdit()
            advanceUntilIdle()

            assertEquals(1, repository.corrections)
            val corrected = repository.all().single()
            // The same row is corrected: identity, stored load, warm-up and occurrence are untouched.
            assertEquals(7L, corrected.id)
            assertEquals(10, corrected.reps)
            assertEquals(100.0, corrected.weightKg)
            assertEquals(2, corrected.rir)
            assertTrue(corrected.isWarmup)
            assertEquals(30L, corrected.occurrenceId)
            assertEquals("recorded-session", corrected.sessionId)
            assertNull(viewModel.state.value.loggedSetEdit)
        }

    @Test
    fun aHistoryCorrectionFailureKeepsTheEditorOpenForRetry() = runTest(dispatcher) {
        val recorded = WorkoutHistorySet(
            id = 7L,
            exerciseId = "back-squat",
            exerciseName = "Back Squat",
            reps = 8,
            weightKg = 100.0,
            loadKind = LoadKind.EXTERNAL,
            isWarmup = false,
            rir = null,
            performedAtMillis = MONDAY,
            timingProvenance = WorkoutTimingProvenance.UNKNOWN
        )
        val repository = FakeWorkoutLogRepository(
            listOf(
                WorkoutSet(
                    id = 7L,
                    exerciseId = "back-squat",
                    reps = 8,
                    weightKg = 100.0,
                    performedAtMillis = MONDAY,
                    sessionId = "recorded-session"
                )
            )
        )
        repository.failCorrection = true
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()

        viewModel.editLoggedSet(
            recorded.toLoggedSetRow(WorkoutHistoryKey.Session("recorded-session"))
        )
        viewModel.onLoggedSetRepsChanged("10")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedSetEditFailed)
        assertEquals("10", viewModel.state.value.loggedSetEdit?.reps)
        assertEquals(8, repository.all().single().reps)

        repository.failCorrection = false
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        assertNull(viewModel.state.value.loggedSetEdit)
        assertEquals(10, repository.all().single().reps)
    }

    @Test
    fun recentSetEditCancelAndInvalidValuesDoNotWrite() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            listOf(WorkoutSet(1L, "back-squat", 8, 100.0, performedAtMillis = MONDAY))
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()
        val before = repository.all()
        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        listOf("-1", "NaN", "not a weight").forEach {
            viewModel.onLoggedSetWeightChanged(it)
            viewModel.saveLoggedSetEdit()
        }
        viewModel.onLoggedSetWeightChanged("10")
        viewModel.onLoggedSetRirChanged("11")
        viewModel.saveLoggedSetEdit()
        assertFalse(viewModel.onLoggedSetTimeChanged(MONDAY + 1))
        viewModel.cancelLoggedSetEdit()
        advanceUntilIdle()
        assertEquals(before, repository.all())
        assertEquals(0, repository.corrections)
        assertNull(viewModel.state.value.loggedSetEdit)
    }

    @Test
    fun unchangedWeightKeepsPrecisionAndChangedPoundsConvertToKg() = runTest(dispatcher) {
        val original = WorkoutSet(1L, "back-squat", 8, 12.345678, performedAtMillis = MONDAY)
        val repository = FakeWorkoutLogRepository(listOf(original))
        val viewModel = viewModel(
            repository = repository,
            weightUnit = WeightUnit.LB,
            timeMillis = MONDAY
        )
        advanceUntilIdle()
        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        viewModel.onLoggedSetRepsChanged("9")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()
        assertEquals(original.weightKg, repository.all().single().weightKg)
        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        viewModel.onLoggedSetWeightChanged("20")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()
        assertEquals(WeightUnit.LB.displayToKilograms(20.0), repository.all().single().weightKg)
    }

    @Test
    fun repeatedSavesAreGuardedAndFailureKeepsEditsForRetry() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(
            listOf(WorkoutSet(1L, "back-squat", 8, 100.0, performedAtMillis = MONDAY))
        )
        val viewModel = viewModel(repository = repository, timeMillis = MONDAY)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.correctionGate = gate
        repository.failCorrection = true
        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        viewModel.onLoggedSetRepsChanged("10")
        viewModel.saveLoggedSetEdit()
        viewModel.saveLoggedSetEdit()
        viewModel.cancelLoggedSetEdit()
        advanceUntilIdle()
        assertEquals(1, repository.corrections)
        assertTrue(viewModel.state.value.savingLoggedSet)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loggedSetEditFailed)
        assertEquals("10", viewModel.state.value.loggedSetEdit?.reps)
        assertEquals(8, repository.all().single().reps)
        repository.failCorrection = false
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()
        assertEquals(10, repository.all().single().reps)
        assertNull(viewModel.state.value.loggedSetEdit)
    }

    @Test
    fun correctingAnOccurrenceSetKeepsItsCompletionCountAndPrescription() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.log()
        advanceUntilIdle()
        val occurrence = schedule.getOccurrence(30L)
        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        viewModel.onLoggedSetRepsChanged("6")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.activeOccurrence?.performedSets)
        assertEquals(occurrence, schedule.getOccurrence(30L))
        assertEquals(40L, repository.all().single().occurrenceEntryId)
    }

    @Test
    fun editedDraftSetsKeepTheirOccurrenceAttribution() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        val draft = viewModel.state.value.draftSets.single()

        viewModel.editDraft(draft)
        viewModel.onDraftRepsChanged("7")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        assertEquals(2, repository.all().size)
        assertTrue(
            repository.all().all {
                it.reps == 7 && it.occurrenceId == 30L && it.occurrenceEntryId == 40L
            }
        )
    }

    @Test
    fun finishingTheCurrentWorkoutAdvancesTheQueue() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)

        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        // Two prescribed sets, each submitted after the previous write completes (the guard refuses
        // an overlapping second tap).
        viewModel.log()
        advanceUntilIdle()
        viewModel.log()
        advanceUntilIdle()
        assertEquals(30L, schedule.scheduleState().selectedOccurrenceId)

        viewModel.finishWorkout()
        advanceUntilIdle()

        assertEquals(31L, schedule.scheduleState().selectedOccurrenceId)
        assertEquals("Lower", viewModel.state.value.activeOccurrence?.workoutName)
    }

    @Test
    fun finishingAWorkoutEmitsItsSummaryForTheOccurrenceJustFinished() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)

        viewModel.finishWorkout()
        advanceUntilIdle()

        val summary = assertNotNull(viewModel.state.value.summary)
        val ready = assertIs<WorkoutSummaryScreen.Ready>(summary).summary
        assertEquals(30L, ready.occurrenceId)
        assertEquals("Upper", ready.workoutName)
        assertEquals(OccurrenceStatus.FINISHED, ready.status)
        assertEquals(2, ready.prescribedSets)
        assertEquals(2, ready.recordedWorkingSets)
        assertEquals(0, ready.remainingSets)
        assertTrue(ready.isComplete)
    }

    @Test
    fun finishingPartiallyEmitsASummaryThatKeepsTheOmittedWork() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()

        viewModel.finishWorkoutPartially()
        advanceUntilIdle()

        val ready = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        assertEquals(OccurrenceStatus.FINISHED_PARTIAL, ready.status)
        assertEquals(1, ready.recordedWorkingSets)
        assertEquals(1, ready.remainingSets)
        assertEquals(RemainingDisposition.OMITTED, ready.exercises.single().remainingDisposition)
    }

    @Test
    fun skippingAWorkoutOffersNoSummary() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        advanceUntilIdle()

        viewModel.skipWorkout()
        advanceUntilIdle()

        assertNull(viewModel.state.value.occurrenceMessage)
        assertNull(viewModel.state.value.summary)
    }

    @Test
    fun aFinishThatFailsShowsItsMessageAndOffersNoSummary() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()

        viewModel.finishWorkout()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.occurrenceMessage)
        assertNull(viewModel.state.value.summary)
    }

    @Test
    fun dismissingTheSummaryClearsIt() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)

        viewModel.finishWorkout()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.summary)

        viewModel.onWorkoutSummaryShown()

        assertNull(viewModel.state.value.summary)
        assertEquals(31L, schedule.scheduleState().selectedOccurrenceId)
    }

    @Test
    fun aSummaryIsDroppedWhenTheBlockChangesDuringTheFinish() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)

        // The block is replaced as the finish commits, so the concluded workout is no longer the
        // user's current context and its summary must not be overlaid.
        schedule.cancelBlockOnResolve = true
        viewModel.finishWorkout()
        advanceUntilIdle()

        assertNull(viewModel.state.value.summary)
    }

    @Test
    fun aFailedSummaryReadKeepsTheRouteOpenAndRetries() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)

        schedule.failOccurrenceReadAfterResolve = true
        viewModel.finishWorkout()
        advanceUntilIdle()
        assertIs<WorkoutSummaryScreen.Failed>(viewModel.state.value.summary)

        schedule.failOccurrenceReadAfterResolve = false
        viewModel.retryWorkoutSummary()
        advanceUntilIdle()

        assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary)
    }

    @Test
    fun aSummaryIsUnavailableWhenTheOccurrenceNoLongerExists() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)

        schedule.dropOccurrenceAfterResolve = true
        viewModel.finishWorkout()
        advanceUntilIdle()

        assertIs<WorkoutSummaryScreen.Unavailable>(viewModel.state.value.summary)
    }

    @Test
    fun requestingHistoryFromTheSummaryRecordsTheFinishedSessionsIdentity() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)
        viewModel.finishWorkout()
        advanceUntilIdle()
        val ready = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary

        viewModel.onWorkoutSummaryHistoryRequested(ready)

        assertNull(viewModel.state.value.summary)
        assertEquals(
            ready.sessions.singleOrNull()?.sessionId?.let { WorkoutHistoryKey.Session(it) },
            viewModel.state.value.pendingHistoryKey
        )
        viewModel.onPendingHistoryKeyConsumed()
        assertNull(viewModel.state.value.pendingHistoryKey)
    }

    @Test
    fun editingASummarySetOpensTheEditorWithItsRecordedValues() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)
        viewModel.finishWorkout()
        advanceUntilIdle()
        val ready = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        val set = ready.exercises.single().performedSets.first()

        viewModel.editSummarySet(ready.occurrenceId, set)

        val edit = assertNotNull(viewModel.state.value.loggedSetEdit)
        assertEquals(set.id, edit.row.id)
        assertEquals(set.exerciseName ?: set.exerciseId, edit.row.exerciseName)
        assertEquals(set.reps.toString(), edit.reps)
        // The occurrence attribution the editor and rest-prompt cancellation need.
        assertEquals(ready.occurrenceId, edit.row.occurrenceId)
        assertEquals(set.sessionId, edit.row.sessionId)
    }

    @Test
    fun correctingASetFromTheSummaryRefreshesTheReconciliation() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)
        viewModel.finishWorkout()
        advanceUntilIdle()
        val ready = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        val set = ready.exercises.single().performedSets.first()

        viewModel.editSummarySet(ready.occurrenceId, set)
        viewModel.onLoggedSetRepsChanged("11")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        assertNull(viewModel.state.value.loggedSetEdit)
        assertEquals(11, repository.all().first { it.id == set.id }.reps)
        // The summary is re-read, never left showing the pre-correction reconciliation.
        val refreshed = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        assertEquals(listOf(11, 8), refreshed.exercises.single().performedSets.map { it.reps })
    }

    @Test
    fun cancellingASummarySetEditWritesNothing() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)
        viewModel.finishWorkout()
        advanceUntilIdle()
        val ready = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        val set = ready.exercises.single().performedSets.first()
        val before = repository.all().map { it.id to it.reps }

        viewModel.editSummarySet(ready.occurrenceId, set)
        viewModel.onLoggedSetRepsChanged("99")
        viewModel.cancelLoggedSetEdit()

        assertNull(viewModel.state.value.loggedSetEdit)
        assertEquals(before, repository.all().map { it.id to it.reps })
        val unchanged = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        assertEquals(listOf(8, 8), unchanged.exercises.single().performedSets.map { it.reps })
    }

    @Test
    fun aFailedSummarySetEditKeepsTheEditorAndARetryRefreshes() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)
        viewModel.finishWorkout()
        advanceUntilIdle()
        val ready = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        val set = ready.exercises.single().performedSets.first()

        repository.failCorrection = true
        viewModel.editSummarySet(ready.occurrenceId, set)
        viewModel.onLoggedSetRepsChanged("13")
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedSetEditFailed)
        assertEquals("13", viewModel.state.value.loggedSetEdit?.reps)
        assertEquals(8, repository.all().first { it.id == set.id }.reps)
        // A failed write leaves the reconciliation on screen rather than reloading a stale one.
        assertEquals(
            listOf(8, 8),
            assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
                .exercises.single().performedSets.map { it.reps }
        )

        repository.failCorrection = false
        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        assertNull(viewModel.state.value.loggedSetEdit)
        assertEquals(13, repository.all().first { it.id == set.id }.reps)
        assertEquals(
            listOf(13, 8),
            assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
                .exercises.single().performedSets.map { it.reps }
        )
    }

    @Test
    fun correctingASummarySetThatWasRemovedFailsWithoutRecreatingIt() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule)
        advanceUntilIdle()
        logTwoSets(viewModel)
        viewModel.finishWorkout()
        advanceUntilIdle()
        val ready = assertIs<WorkoutSummaryScreen.Ready>(viewModel.state.value.summary).summary
        val set = ready.exercises.single().performedSets.first()

        viewModel.editSummarySet(ready.occurrenceId, set)
        viewModel.onLoggedSetRepsChanged("12")
        // The row disappears between the summary read and the save, as another surface could.
        repository.delete(set.id)
        advanceUntilIdle()

        viewModel.saveLoggedSetEdit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedSetEditFailed)
        assertEquals("12", viewModel.state.value.loggedSetEdit?.reps)
        // A stale correction fails; it never recreates the deleted row.
        assertEquals(1, repository.all().size)
        assertTrue(repository.all().none { it.id == set.id })
    }

    /** Logs the two working sets the Upper occurrence prescribes. */
    private suspend fun TestScope.logTwoSets(viewModel: WorkoutLoggerViewModel) {
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWeightChanged("100")
        viewModel.log()
        // The manual log guard refuses an overlapping second submission, so the second prescribed
        // set is logged after the first write and its refresh complete, as the UI flow does.
        advanceUntilIdle()
        viewModel.log()
    }

    @Test
    fun guidedModeIsActiveOnlyWhenEnabledAndAnOccurrenceIsSelected() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)

        val off = occurrenceViewModel(repository, schedule, guided = false)
        advanceUntilIdle()
        assertFalse(off.state.value.isGuidedActive)

        val on = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()
        assertTrue(on.state.value.isGuidedActive)
        val exercise = assertNotNull(on.state.value.guidedProgress?.exercises?.single())
        assertEquals(40L, exercise.occurrenceEntryId)
        assertEquals(2, exercise.prescribedSets)
        assertEquals(0, exercise.performedWorkingSets)
    }

    @Test
    fun guidedOccurrenceAndRemainingDraftKeepTheFrozenReasoning() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val explanation = targetExplanation()
        val occurrences = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(
                    entries = occurrence.entries.map { it.copy(explanation = explanation) }
                )
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)

        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        assertEquals(
            explanation,
            viewModel.state.value.guidedProgress?.exercises?.single()?.explanation
        )
        assertEquals(explanation, viewModel.state.value.draftSets.single().explanation)
    }

    @Test
    fun guidedConfirmRecordsOneSetAndKeepsTheRemainingPrescription() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.confirmGuidedSet(40L)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertEquals(40L, repository.all().single().occurrenceEntryId)
        assertEquals(1, viewModel.state.value.draftSets.single().sets)
        val afterFirst = assertNotNull(viewModel.state.value.guidedProgress?.exercises?.single())
        assertEquals(1, afterFirst.performedWorkingSets)
        assertEquals(1, afterFirst.remainingSets)
        assertFalse(afterFirst.isComplete)

        viewModel.confirmGuidedSet(40L)
        advanceUntilIdle()

        assertEquals(2, repository.all().size)
        assertTrue(viewModel.state.value.draftSets.isEmpty())
        assertTrue(viewModel.state.value.guidedProgress?.allComplete == true)
    }

    @Test
    fun guidedWorkoutOffersWarmupGuidanceFromTheFrozenTarget() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        val guidance = assertNotNull(viewModel.state.value.visibleWarmupGuidance)
        assertEquals(
            listOf(WarmupAction.EASY_GENERAL_MOVEMENT, WarmupAction.LOWER_BODY_PRACTICE),
            guidance.preparation.map { it.action }
        )
        val ramp = guidance.ramps.single()
        assertEquals(40L, ramp.occurrenceEntryId)
        assertEquals(100.0, ramp.workingWeightKg)
        assertEquals(listOf(40.0, 60.0, 80.0), ramp.rampSets.map { it.weightKg })
    }

    @Test
    fun warmupGuidanceStaysHiddenWhenGuidedModeIsOff() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = false)
        advanceUntilIdle()

        assertNull(viewModel.state.value.visibleWarmupGuidance)
    }

    @Test
    fun warmupPreparationMarkTogglesAndHidesTheGuidance() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.onWarmupPreparationToggled(WarmupAction.LOWER_BODY_PRACTICE)
        assertEquals(
            setOf(WarmupAction.LOWER_BODY_PRACTICE),
            viewModel.state.value.warmupPreparationChecked
        )

        viewModel.onToggleWarmupGuidance()
        assertTrue(viewModel.state.value.warmupGuidanceHidden)
        assertNull(viewModel.state.value.visibleWarmupGuidance)

        viewModel.onToggleWarmupGuidance()
        assertNotNull(viewModel.state.value.visibleWarmupGuidance)

        viewModel.onWarmupPreparationToggled(WarmupAction.LOWER_BODY_PRACTICE)
        assertTrue(viewModel.state.value.warmupPreparationChecked.isEmpty())
    }

    @Test
    fun warmupGuidanceStateIsScopedToOneOccurrence() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.onWarmupPreparationToggled(WarmupAction.EASY_GENERAL_MOVEMENT)
        viewModel.onToggleWarmupGuidance()

        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.warmupPreparationChecked.isEmpty())
        assertFalse(viewModel.state.value.warmupGuidanceHidden)
        val next = assertNotNull(viewModel.state.value.visibleWarmupGuidance)
        assertEquals(140.0, next.ramps.single().workingWeightKg)
    }

    @Test
    fun loggingASuggestedRampRecordsOneWarmupWithoutAdvancingProgress() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()

        val recorded = repository.all().single()
        assertTrue(recorded.isWarmup)
        assertEquals(40.0, recorded.weightKg)
        assertEquals(8, recorded.reps)
        assertEquals(40L, recorded.occurrenceEntryId)
        assertEquals(LoadKind.EXTERNAL, recorded.loadKind)
        assertEquals(0, viewModel.state.value.guidedProgress?.performedSets)
        assertNull(viewModel.state.value.restTimer)
        assertFalse(viewModel.state.value.warmupRampWriteFailed)
    }

    @Test
    fun aRampIndexOutsideThePlanWritesNothing() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.logWarmupRamp(40L, rampIndex = 99)
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun aFailedRampWriteKeepsTheSuggestionForRetry() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.logWarmupRamp(40L, rampIndex = 1)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.warmupRampWriteFailed)
        assertFalse(viewModel.state.value.guidedSetWriteFailed)
        assertTrue(repository.all().isEmpty())

        viewModel.logWarmupRamp(40L, rampIndex = 1)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.warmupRampWriteFailed)
        assertEquals(60.0, repository.all().single().weightKg)
    }

    @Test
    fun aSecondRampTapDuringAnInFlightWriteIsIgnored() = runTest(dispatcher) {
        val writeGate = CompletableDeferred<Unit>()
        val repository = FakeWorkoutLogRepository().apply { addGate = writeGate }
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.logWarmupRamp(40L, rampIndex = 0)
        runCurrent()
        viewModel.logWarmupRamp(40L, rampIndex = 0)
        runCurrent()
        writeGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
    }

    @Test
    fun aRampWriteInFlightAcrossAnOccurrenceChangeLeavesTheNewContextIntact() =
        runTest(dispatcher) {
            val writeGate = CompletableDeferred<Unit>()
            val repository = FakeWorkoutLogRepository().apply { addGate = writeGate }
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(repository, schedule, guided = true)
            advanceUntilIdle()

            viewModel.logWarmupRamp(40L, rampIndex = 0)
            runCurrent()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
            advanceUntilIdle()
            writeGate.complete(Unit)
            runCurrent()

            // The new workout keeps its own guidance with no stale failure or preparation marks.
            assertFalse(viewModel.state.value.warmupRampWriteFailed)
            assertTrue(viewModel.state.value.warmupPreparationChecked.isEmpty())
            val next = assertNotNull(viewModel.state.value.visibleWarmupGuidance)
            assertEquals(140.0, next.ramps.single().workingWeightKg)
        }

    @Test
    fun warmupGuidanceIsRebuiltOnRecreationWithoutPersistingPreparationMarks() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            val first = occurrenceViewModel(repository, schedule, guided = true)
            advanceUntilIdle()
            first.onWarmupPreparationToggled(WarmupAction.EASY_GENERAL_MOVEMENT)

            val recreated = occurrenceViewModel(repository, schedule, guided = true)
            advanceUntilIdle()

            assertNotNull(recreated.state.value.visibleWarmupGuidance)
            assertTrue(recreated.state.value.warmupPreparationChecked.isEmpty())
        }

    @Test
    fun aRecordedRampStaysVisibleAsAWarmUpInRecentSets() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.logWarmupRamp(40L, rampIndex = 2)
        advanceUntilIdle()

        val row = viewModel.state.value.recentSets.single()
        assertTrue(row.isWarmup)
        assertEquals(80.0, row.weightKg)
        assertEquals(3, row.reps)
        assertEquals(0, viewModel.state.value.guidedProgress?.performedSets)
    }

    @Test
    fun aRampTapDuringARefreshThatChangesTheSlotWritesNothing() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        // A replacement arrives; its performed-set read suspends before new guidance is published.
        val readGate = CompletableDeferred<Unit>()
        repository.allGate = readGate
        val replaced = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(
                    revision = 2,
                    entries = occurrence.entries.map {
                        it.copy(
                            exerciseId = "front-squat",
                            exerciseName = "Front Squat",
                            weightKg = 60.0
                        )
                    }
                )
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), replaced, selectedOccurrenceId = 30L)
        runCurrent()

        viewModel.logWarmupRamp(40L, rampIndex = 0)
        runCurrent()
        assertTrue(repository.all().isEmpty())

        readGate.complete(Unit)
        advanceUntilIdle()

        // The published suggestion is the replaced prescription, and its own tap records it.
        val published = assertNotNull(viewModel.state.value.visibleWarmupGuidance)
        assertEquals(60.0, published.ramps.single().workingWeightKg)
        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()

        val recorded = repository.all().single()
        assertEquals("front-squat", recorded.exerciseId)
        assertEquals(25.0, recorded.weightKg)
        assertTrue(recorded.isWarmup)
    }

    @Test
    fun aSupersededRefreshCannotOverwriteNewerGuidance() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()
        assertEquals(
            100.0,
            assertNotNull(viewModel.state.value.visibleWarmupGuidance)
                .ramps.single().workingWeightKg
        )

        // A refresh for occurrence 30 suspends on its performed-set read.
        val readGate = CompletableDeferred<Unit>()
        repository.allGate = readGate
        val revision = twoOccurrences().map { it.copy(revision = 2) }
        schedule.set(blockActivation(), revision, selectedOccurrenceId = 30L)
        runCurrent()

        // The selected occurrence moves to 31 while that read is still in flight; its refresh
        // completes first and publishes 31's guidance.
        schedule.set(blockActivation(), revision, selectedOccurrenceId = 31L)
        runCurrent()
        assertEquals(
            140.0,
            assertNotNull(viewModel.state.value.visibleWarmupGuidance)
                .ramps.single().workingWeightKg
        )

        // Releasing the older read must not republish the superseded occurrence.
        readGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            140.0,
            assertNotNull(viewModel.state.value.visibleWarmupGuidance)
                .ramps.single().workingWeightKg
        )
        assertEquals(31L, viewModel.state.value.activeOccurrence?.occurrenceId)
    }

    @Test
    fun anEquipmentRefreshThatSupersedesAnOlderReadStillAllowsLogging() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        // An occurrence refresh suspends on its read, then an equipment change supersedes it.
        val readGate = CompletableDeferred<Unit>()
        repository.allGate = readGate
        schedule.set(
            blockActivation(),
            barbellOccurrences().map { it.copy(revision = 2) },
            selectedOccurrenceId = 30L
        )
        runCurrent()
        equipment.setMaxWeight(30.0)
        runCurrent()
        assertEquals(
            listOf(30.0),
            assertNotNull(viewModel.state.value.visibleWarmupGuidance).ramps.single()
                .rampSets.map { it.weightKg }
        )

        readGate.complete(Unit)
        advanceUntilIdle()

        // The superseded read must not leave the published generation behind, or every ramp tap
        // would be refused from here on.
        assertEquals(
            listOf(30.0),
            assertNotNull(viewModel.state.value.visibleWarmupGuidance).ramps.single()
                .rampSets.map { it.weightKg }
        )
        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()
        val recorded = repository.all().single()
        assertTrue(recorded.isWarmup)
        assertEquals(30.0, recorded.weightKg)
    }

    @Test
    fun aRefreshInFlightWhenTheBlockEndsCannotResurrectItsGuidance() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()
        viewModel.editWarmupRamp(40L, rampIndex = 0)
        assertNotNull(viewModel.state.value.warmupEditor)

        // The equipment refresh suspends mid-read; the block then ends while it is still in flight.
        val readGate = CompletableDeferred<Unit>()
        repository.allGate = readGate
        equipment.setMaxWeight(30.0)
        runCurrent()
        schedule.clearBlock()
        runCurrent()

        readGate.complete(Unit)
        advanceUntilIdle()

        // The superseded read cannot republish its occurrence, guidance or editor for an ended block.
        assertNull(viewModel.state.value.visibleWarmupGuidance)
        assertNull(viewModel.state.value.activeOccurrence)
        assertNull(viewModel.state.value.warmupEditor)
        assertTrue(viewModel.state.value.warmupRampEdits.isEmpty())
    }

    @Test
    fun warmupRampsRespectCurrentEquipmentAvailabilityAndCeiling() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(
                entries = occurrence.entries.map {
                    it.copy(requiredEquipment = setOf(EquipmentTag.BARBELL))
                }
            )
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)

        // Not owned: the ramp is withheld rather than suggested for unavailable equipment.
        val none = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()
        assertTrue(none.state.value.visibleWarmupGuidance?.ramps.orEmpty().isEmpty())

        // Owned with a 50 kg ceiling: the 60/80 % ramps clamp onto 50 kg and collapse to one row.
        val owned = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = WorkoutLoggerEquipment(
                FakeEquipment(
                    listOf(
                        Equipment(
                            id = EquipmentTag.BARBELL,
                            name = "Barbell",
                            isBuiltIn = true,
                            maxWeightKg = 50.0
                        )
                    )
                ),
                FakeSelection(setOf(EquipmentTag.BARBELL))
            )
        )
        advanceUntilIdle()

        val ramp = assertNotNull(owned.state.value.visibleWarmupGuidance).ramps.single()
        assertEquals(listOf(40.0, 50.0), ramp.rampSets.map { it.weightKg })
    }

    @Test
    fun warmupRampsWaitForTheFirstEquipmentSnapshot() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = GatedEquipment(
            items = listOf(
                Equipment(
                    id = EquipmentTag.BARBELL,
                    name = "Barbell",
                    isBuiltIn = true,
                    maxWeightKg = 50.0
                )
            ),
            selected = setOf(EquipmentTag.BARBELL)
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        // The occurrence is present but no equipment snapshot has arrived: preparation shows and the
        // ramp rows are withheld rather than offered without an availability or ceiling check.
        val before = assertNotNull(viewModel.state.value.visibleWarmupView)
        assertTrue(before.preparation.isNotEmpty())
        assertTrue(before.ramps.isEmpty())

        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()
        assertTrue(repository.all().isEmpty())

        // The first snapshot is capped: the bounded ramp rows become available.
        equipment.release()
        advanceUntilIdle()
        val ramp = assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single()
        assertEquals(listOf(40.0, 50.0), ramp.rows.map { it.weightKg })
    }

    @Test
    fun equipmentReadinessWaitsForTheEquipmentBoundedGuidance() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = GatedEquipment(
            items = listOf(
                Equipment(
                    id = EquipmentTag.BARBELL,
                    name = "Barbell",
                    isBuiltIn = true,
                    maxWeightKg = 50.0
                )
            ),
            selected = setOf(EquipmentTag.BARBELL)
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        // Guidance already exists, built without knowing availability or the ceiling; readiness
        // withholds its ramps.
        val before = assertNotNull(viewModel.state.value.visibleWarmupView)
        assertTrue(before.preparation.isNotEmpty())
        assertTrue(before.ramps.isEmpty())

        // The snapshot arrives while the performed-set read for the equipment-bounded refresh is
        // still in flight. Readiness must not be published ahead of that guidance, or the previous
        // snapshot's unrestricted ramps would briefly reappear for capped equipment.
        val readGate = CompletableDeferred<Unit>()
        repository.allGate = readGate
        equipment.release()
        advanceUntilIdle()
        assertTrue(assertNotNull(viewModel.state.value.visibleWarmupView).ramps.isEmpty())

        readGate.complete(Unit)
        advanceUntilIdle()

        // Readiness and the capped rows become available together.
        val ramp = assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single()
        assertEquals(listOf(40.0, 50.0), ramp.rows.map { it.weightKg })
    }

    @Test
    fun aSupersededEquipmentRefreshKeepsReadinessHidden() = runTest(dispatcher) {
        val cases = listOf(
            Triple(50.0, setOf(EquipmentTag.BARBELL), listOf(40.0, 50.0)),
            Triple(50.0, emptySet(), emptyList()),
            Triple(null, setOf(EquipmentTag.BARBELL), listOf(40.0, 60.0, 80.0))
        )
        for ((ceiling, selected, expectedWeights) in cases) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
            val equipment = GatedEquipment(
                items = listOf(
                    Equipment(
                        id = EquipmentTag.BARBELL,
                        name = "Barbell",
                        isBuiltIn = true,
                        maxWeightKg = ceiling
                    )
                ),
                selected = selected
            )
            val viewModel = occurrenceViewModel(
                repository,
                schedule,
                guided = true,
                equipment = equipment.collaborator()
            )
            advanceUntilIdle()
            assertFalse(viewModel.state.value.warmupEquipmentKnown)
            assertTrue(assertNotNull(viewModel.state.value.visibleWarmupView).ramps.isEmpty())

            val equipmentRead = CompletableDeferred<Unit>()
            val newerRead = CompletableDeferred<Unit>()
            try {
                repository.allGate = equipmentRead
                equipment.release()
                advanceUntilIdle()
                assertNull(repository.allGate)

                // Only the occurrences change: their independent collector supersedes the equipment
                // refresh, but cannot publish until its own performed-set read finishes.
                repository.allGate = newerRead
                schedule.set(
                    blockActivation(),
                    barbellOccurrences().map { it.copy(revision = 2) },
                    selectedOccurrenceId = 30L
                )
                advanceUntilIdle()
                assertNull(repository.allGate)
                equipmentRead.complete(Unit)
                advanceUntilIdle()

                viewModel.logWarmupRamp(40L, rampIndex = 0)
                advanceUntilIdle()
                assertTrue(repository.all().isEmpty())
                assertFalse(viewModel.state.value.warmupEquipmentKnown)
                val pending = assertNotNull(viewModel.state.value.visibleWarmupView)
                assertTrue(pending.preparation.isNotEmpty())
                assertTrue(pending.ramps.isEmpty())

                newerRead.complete(Unit)
                advanceUntilIdle()
                assertTrue(viewModel.state.value.warmupEquipmentKnown)
                val published = assertNotNull(viewModel.state.value.visibleWarmupView)
                assertEquals(
                    expectedWeights,
                    published.ramps.flatMap { it.rows }.map { it.weightKg }
                )
                viewModel.logWarmupRamp(40L, rampIndex = 0)
                advanceUntilIdle()
                assertEquals(expectedWeights.take(1), repository.all().map { it.weightKg })
            } finally {
                equipmentRead.complete(Unit)
                newerRead.complete(Unit)
                advanceUntilIdle()
            }
        }
    }

    @Test
    fun equipmentObservedBeforeAnActiveBlockIsReadyWithItsFirstGuidance() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val equipment = GatedEquipment(
            items = listOf(
                Equipment(
                    id = EquipmentTag.BARBELL,
                    name = "Barbell",
                    isBuiltIn = true,
                    maxWeightKg = 50.0
                )
            ),
            selected = setOf(EquipmentTag.BARBELL)
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()
        equipment.release()
        advanceUntilIdle()
        assertNull(viewModel.state.value.visibleWarmupView)

        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.warmupEquipmentKnown)
        val ramp = assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single()
        assertEquals(listOf(40.0, 50.0), ramp.rows.map { it.weightKg })
        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()
        assertEquals(40.0, repository.all().single().weightKg)
    }

    @Test
    fun warmupRampsUseTheFirstUncappedEquipmentSnapshot() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = GatedEquipment(
            items = listOf(
                Equipment(
                    id = EquipmentTag.BARBELL,
                    name = "Barbell",
                    isBuiltIn = true,
                    maxWeightKg = null
                )
            ),
            selected = setOf(EquipmentTag.BARBELL)
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()
        assertTrue(assertNotNull(viewModel.state.value.visibleWarmupView).ramps.isEmpty())

        equipment.release()
        advanceUntilIdle()

        // Unknown is not the same as unbounded: an uncapped snapshot offers the full profile.
        val ramp = assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single()
        assertEquals(listOf(40.0, 60.0, 80.0), ramp.rows.map { it.weightKg })
    }

    @Test
    fun anUnownedFirstEquipmentSnapshotOffersOnlyPreparation() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = GatedEquipment(
            items = listOf(
                Equipment(
                    id = EquipmentTag.BARBELL,
                    name = "Barbell",
                    isBuiltIn = true,
                    maxWeightKg = 50.0
                )
            ),
            selected = emptySet()
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        equipment.release()
        advanceUntilIdle()

        val view = assertNotNull(viewModel.state.value.visibleWarmupView)
        assertTrue(view.preparation.isNotEmpty())
        assertTrue(view.ramps.isEmpty())

        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun editingPreparationSavesTransientlyAndCancelWritesNothing() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
        assertNotNull(viewModel.state.value.warmupEditor)
        viewModel.onWarmupEditorRepsChanged("12")
        viewModel.saveWarmupEditor()

        assertNull(viewModel.state.value.warmupEditor)
        val edited = preparationRow(viewModel, WarmupAction.LOWER_BODY_PRACTICE)
        assertEquals(12, edited.reps)
        assertTrue(edited.edited)
        assertTrue(repository.all().isEmpty())

        viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
        viewModel.onWarmupEditorRepsChanged("99")
        viewModel.cancelWarmupEditor()
        assertNull(viewModel.state.value.warmupEditor)
        assertEquals(12, preparationRow(viewModel, WarmupAction.LOWER_BODY_PRACTICE).reps)
    }

    @Test
    fun resettingAnEditedPreparationRestoresItsDefault() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
        viewModel.onWarmupEditorRepsChanged("12")
        viewModel.saveWarmupEditor()
        viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
        viewModel.resetWarmupEditor()
        // Reset stages the default; Save is what applies it.
        viewModel.saveWarmupEditor()

        val row = preparationRow(viewModel, WarmupAction.LOWER_BODY_PRACTICE)
        assertEquals(10, row.reps)
        assertFalse(row.edited)
    }

    @Test
    fun resettingAGuidanceValueOnlyAppliesWhenSaved() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        // A preparation edit survives Reset + Cancel.
        viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
        viewModel.onWarmupEditorRepsChanged("12")
        viewModel.saveWarmupEditor()
        viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
        viewModel.resetWarmupEditor()
        assertEquals("10", assertNotNull(viewModel.state.value.warmupEditor).repsInput)
        viewModel.cancelWarmupEditor()
        val kept = preparationRow(viewModel, WarmupAction.LOWER_BODY_PRACTICE)
        assertEquals(12, kept.reps)
        assertTrue(kept.edited)

        // A ramp edit survives Reset + Cancel, and Reset + Save restores its default.
        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.onWarmupEditorWeightChanged("45")
        viewModel.saveWarmupEditor()
        assertEquals(45.0, rampRow(viewModel).weightKg)

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.resetWarmupEditor()
        viewModel.cancelWarmupEditor()
        assertEquals(45.0, rampRow(viewModel).weightKg)

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.resetWarmupEditor()
        viewModel.saveWarmupEditor()
        val restored = rampRow(viewModel)
        assertEquals(40.0, restored.weightKg)
        assertEquals(40, restored.percentOfWorkingWeight)
        assertTrue(viewModel.state.value.warmupRampEdits.isEmpty())
    }

    @Test
    fun skippingEveryPreparationKeepsRestoreReachable() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        // No numeric load means no ramp, so only the preparation rows remain.
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = null) })
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        val actions = assertNotNull(viewModel.state.value.visibleWarmupView)
            .preparation
            .map { it.action }
        assertTrue(actions.isNotEmpty())
        actions.forEach { viewModel.toggleWarmupPreparationSkipped(it) }

        val view = assertNotNull(viewModel.state.value.visibleWarmupView)
        assertTrue(view.preparation.isEmpty())
        assertEquals(actions.size, view.skippedPreparationCount)

        viewModel.restoreSkippedWarmupPreparation()
        assertEquals(
            actions.size,
            assertNotNull(viewModel.state.value.visibleWarmupView).preparation.size
        )
    }

    @Test
    fun skippingAndRestoringPreparationRemovesAndReturnsTheRow() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.toggleWarmupPreparationSkipped(WarmupAction.LOWER_BODY_PRACTICE)
        val view = assertNotNull(viewModel.state.value.visibleWarmupView)
        assertTrue(view.preparation.none { it.action == WarmupAction.LOWER_BODY_PRACTICE })

        viewModel.restoreSkippedWarmupPreparation()
        val restored = assertNotNull(viewModel.state.value.visibleWarmupView)
        assertTrue(restored.preparation.any { it.action == WarmupAction.LOWER_BODY_PRACTICE })
    }

    @Test
    fun anEditedRampLogsItsEditedValuesAndDropsItsPercentage() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.onWarmupEditorRepsChanged("6")
        viewModel.onWarmupEditorWeightChanged("45")
        viewModel.saveWarmupEditor()

        val row = assertNotNull(viewModel.state.value.visibleWarmupView)
            .ramps.single()
            .rows
            .first()
        assertEquals(6, row.reps)
        assertEquals(45.0, row.weightKg)
        assertNull(row.percentOfWorkingWeight)

        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()

        val recorded = repository.all().single()
        assertTrue(recorded.isWarmup)
        assertEquals(45.0, recorded.weightKg)
        assertEquals(6, recorded.reps)
    }

    @Test
    fun anEditedRampAboveTheEquipmentCeilingCannotBeSaved() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(
                entries = occurrence.entries.map {
                    it.copy(requiredEquipment = setOf(EquipmentTag.BARBELL))
                }
            )
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = WorkoutLoggerEquipment(
                FakeEquipment(
                    listOf(
                        Equipment(
                            id = EquipmentTag.BARBELL,
                            name = "Barbell",
                            isBuiltIn = true,
                            maxWeightKg = 50.0
                        )
                    )
                ),
                FakeSelection(setOf(EquipmentTag.BARBELL))
            )
        )
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.onWarmupEditorWeightChanged("80")

        val editor = assertNotNull(viewModel.state.value.warmupEditor)
        assertTrue(editor.weightExceedsCeiling)
        assertFalse(editor.canSave)
    }

    @Test
    fun aTimedPreparationIsEditedInMinutesAndStoredAsSeconds() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.editWarmupPreparation(WarmupAction.EASY_GENERAL_MOVEMENT)
        // The default 180 seconds opens as 3 minutes, never as 180.
        assertEquals("3", assertNotNull(viewModel.state.value.warmupEditor).durationInput)

        viewModel.onWarmupEditorDurationChanged("5")
        viewModel.saveWarmupEditor()

        val row = preparationRow(viewModel, WarmupAction.EASY_GENERAL_MOVEMENT)
        assertEquals(300, row.durationSeconds)
        assertTrue(row.edited)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun aTimedPreparationRejectsZeroAndAnUnstorableEntry() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.editWarmupPreparation(WarmupAction.EASY_GENERAL_MOVEMENT)
        viewModel.onWarmupEditorDurationChanged("0")
        assertFalse(assertNotNull(viewModel.state.value.warmupEditor).canSave)

        // There is no one-hour product limit; an hour and a minute is a valid warm-up.
        viewModel.onWarmupEditorDurationChanged("61")
        assertTrue(assertNotNull(viewModel.state.value.warmupEditor).canSave)
        viewModel.saveWarmupEditor()
        assertEquals(
            3_660,
            preparationRow(viewModel, WarmupAction.EASY_GENERAL_MOVEMENT).durationSeconds
        )

        // Only a duration that cannot be stored as whole seconds is refused.
        viewModel.editWarmupPreparation(WarmupAction.EASY_GENERAL_MOVEMENT)
        viewModel.onWarmupEditorDurationChanged("35791395")
        assertFalse(assertNotNull(viewModel.state.value.warmupEditor).canSave)

        viewModel.onWarmupEditorDurationChanged("35791394")
        assertTrue(assertNotNull(viewModel.state.value.warmupEditor).canSave)
    }

    @Test
    fun savingAPreparationOverridePersistsItForAFreshOccurrence() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val overrides = FakeWarmupPreparationOverrideRepository()
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            warmupOverrides = overrides
        )
        advanceUntilIdle()

        viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
        viewModel.onWarmupEditorRepsChanged("14")
        viewModel.saveWarmupEditor()
        advanceUntilIdle()

        // The edit is persisted, not only shown in the current session.
        assertEquals(
            WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 14),
            overrides.observeAll().first()[WarmupAction.LOWER_BODY_PRACTICE]
        )

        // A fresh ViewModel over the same store renders the persisted measure with its edited marker.
        val restarted = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            warmupOverrides = overrides
        )
        advanceUntilIdle()

        val row = preparationRow(restarted, WarmupAction.LOWER_BODY_PRACTICE)
        assertEquals(14, row.reps)
        assertTrue(row.edited)
    }

    @Test
    fun resettingAPreparationOverrideClearsTheStoredOverrideAndRestoresTheDefault() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            val overrides = FakeWarmupPreparationOverrideRepository()
            val viewModel = occurrenceViewModel(
                repository,
                schedule,
                guided = true,
                warmupOverrides = overrides
            )
            advanceUntilIdle()

            viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
            viewModel.onWarmupEditorRepsChanged("14")
            viewModel.saveWarmupEditor()
            advanceUntilIdle()
            assertTrue(overrides.observeAll().first().containsKey(WarmupAction.LOWER_BODY_PRACTICE))

            viewModel.editWarmupPreparation(WarmupAction.LOWER_BODY_PRACTICE)
            viewModel.resetWarmupEditor()
            // Reset stages the default; Save is what applies it and clears the stored override.
            viewModel.saveWarmupEditor()
            advanceUntilIdle()

            assertTrue(overrides.observeAll().first().isEmpty())
            val row = preparationRow(viewModel, WarmupAction.LOWER_BODY_PRACTICE)
            assertEquals(10, row.reps)
            assertFalse(row.edited)
        }

    @Test
    fun aTimedPreparationOverridePersistsItsSeconds() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val overrides = FakeWarmupPreparationOverrideRepository()
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            warmupOverrides = overrides
        )
        advanceUntilIdle()

        viewModel.editWarmupPreparation(WarmupAction.EASY_GENERAL_MOVEMENT)
        viewModel.onWarmupEditorDurationChanged("5")
        viewModel.saveWarmupEditor()
        advanceUntilIdle()

        assertEquals(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 300),
            overrides.observeAll().first()[WarmupAction.EASY_GENERAL_MOVEMENT]
        )

        val restarted = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            warmupOverrides = overrides
        )
        advanceUntilIdle()

        val row = preparationRow(restarted, WarmupAction.EASY_GENERAL_MOVEMENT)
        assertEquals(300, row.durationSeconds)
        assertTrue(row.edited)
    }

    @Test
    fun anEditedRampCeilingIsComparedInTheEnteredUnit() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(
                entries = occurrence.entries.map {
                    it.copy(requiredEquipment = setOf(EquipmentTag.BARBELL))
                }
            )
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = WorkoutLoggerEquipment(
                FakeEquipment(
                    listOf(
                        Equipment(
                            id = EquipmentTag.BARBELL,
                            name = "Barbell",
                            isBuiltIn = true,
                            maxWeightKg = 50.0
                        )
                    )
                ),
                FakeSelection(setOf(EquipmentTag.BARBELL))
            ),
            weightUnit = WeightUnit.LB
        )
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        // 80 lb is about 36 kg, so it stays under the 50 kg ceiling instead of being rejected.
        viewModel.onWarmupEditorWeightChanged("80")
        assertFalse(assertNotNull(viewModel.state.value.warmupEditor).weightExceedsCeiling)

        // 200 lb is about 91 kg, above the same ceiling.
        viewModel.onWarmupEditorWeightChanged("200")
        assertTrue(assertNotNull(viewModel.state.value.warmupEditor).weightExceedsCeiling)
    }

    @Test
    fun anUnchangedPoundDisplayKeepsItsExactKilogramRampAndCanSaveRepsChanges() =
        runTest(dispatcher) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            val occurrences = twoOccurrences().map { occurrence ->
                occurrence.copy(
                    entries = occurrence.entries.map {
                        it.copy(requiredEquipment = setOf(EquipmentTag.BARBELL))
                    }
                )
            }
            schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(
                repository,
                schedule,
                guided = true,
                equipment = WorkoutLoggerEquipment(
                    FakeEquipment(
                        listOf(
                            Equipment(
                                id = EquipmentTag.BARBELL,
                                name = "Barbell",
                                isBuiltIn = true,
                                maxWeightKg = 40.0
                            )
                        )
                    ),
                    FakeSelection(setOf(EquipmentTag.BARBELL))
                ),
                weightUnit = WeightUnit.LB
            )
            advanceUntilIdle()

            // The 40 kg default shows as 88.2 lb, which converts back to 40.006 kg.
            viewModel.editWarmupRamp(40L, rampIndex = 0)
            val editor = assertNotNull(viewModel.state.value.warmupEditor)
            assertEquals("88.2", editor.weightInput)
            // The untouched text still stands for the exact 40 kg default, not the rounded 88.2.
            assertFalse(editor.weightExceedsCeiling)

            viewModel.onWarmupEditorRepsChanged("7")
            viewModel.saveWarmupEditor()
            assertEquals(40.0, rampRow(viewModel).weightKg)

            viewModel.logWarmupRamp(40L, rampIndex = 0)
            advanceUntilIdle()
            val recorded = repository.all().single()
            assertTrue(recorded.isWarmup)
            assertEquals(40.0, recorded.weightKg)
            assertEquals(7, recorded.reps)

            // A genuinely changed value is still converted and checked against the ceiling.
            viewModel.editWarmupRamp(40L, rampIndex = 0)
            viewModel.onWarmupEditorWeightChanged("100")
            assertTrue(assertNotNull(viewModel.state.value.warmupEditor).weightExceedsCeiling)
        }

    @Test
    fun resettingRampKeepsTheEditorsFrozenUnitAfterAPreferenceChange() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val units = MutableStateFlow(WeightUnit.KG)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            weightUnit = WeightUnit.KG,
            weightUnitFlow = units
        )
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.onWarmupEditorWeightChanged("45")
        viewModel.saveWarmupEditor()
        assertEquals(45.0, rampRow(viewModel).weightKg)

        // Reopen in kg, then switch the preference while the editor is still open.
        viewModel.editWarmupRamp(40L, rampIndex = 0)
        units.value = WeightUnit.LB
        advanceUntilIdle()
        assertEquals(WeightUnit.KG, assertNotNull(viewModel.state.value.warmupEditor).weightUnit)

        // Reset fills the frozen unit's digits, not the newly selected preference's.
        viewModel.resetWarmupEditor()
        val reset = assertNotNull(viewModel.state.value.warmupEditor)
        assertEquals(WeightUnit.KG, reset.weightUnit)
        assertEquals("40", reset.weightInput)

        // A reps-only edit after Reset still saves the kilogram default exactly.
        viewModel.onWarmupEditorRepsChanged("6")
        viewModel.saveWarmupEditor()
        assertEquals(40.0, rampRow(viewModel).weightKg)
    }

    @Test
    fun anEditAtACollapsedRampPositionStaysVisibleCorrectableAndUnloggable() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        // Edit the last profile position, then drop the ceiling until the defaults dedupe to one.
        viewModel.editWarmupRamp(40L, rampIndex = 2)
        viewModel.onWarmupEditorWeightChanged("65")
        viewModel.saveWarmupEditor()
        equipment.setMaxWeight(30.0)
        advanceUntilIdle()

        val rows = assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single().rows
        assertEquals(listOf(0, 2), rows.map { it.key.rampIndex })
        val retained = rows.single { it.key.rampIndex == 2 }
        assertEquals(65.0, retained.weightKg)
        assertTrue(retained.exceedsCeiling)

        // Above the limit it is refused, not clamped or written.
        viewModel.logWarmupRamp(40L, rampIndex = 2)
        advanceUntilIdle()
        assertTrue(repository.all().isEmpty())

        // It is still correctable, and the correction keeps the row's own profile percentage.
        viewModel.editWarmupRamp(40L, rampIndex = 2)
        viewModel.onWarmupEditorWeightChanged("25")
        viewModel.saveWarmupEditor()
        val corrected = assertNotNull(viewModel.state.value.visibleWarmupView)
            .ramps.single().rows.single { it.key.rampIndex == 2 }
        assertEquals(25.0, corrected.weightKg)
        assertEquals(
            80,
            viewModel.state.value.warmupRampEdits[WarmupRampKey(40L, 2)]?.percentOfWorkingWeight
        )
    }

    @Test
    fun resettingAnEditWhoseDefaultCollapsedRemovesItOnlyWhenSaved() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 2)
        viewModel.onWarmupEditorWeightChanged("65")
        viewModel.saveWarmupEditor()
        equipment.setMaxWeight(30.0)
        advanceUntilIdle()
        val key = WarmupRampKey(40L, 2)
        assertNotNull(viewModel.state.value.warmupRampEdits[key])

        // Reset then Cancel still preserves the retained edit.
        viewModel.editWarmupRamp(40L, rampIndex = 2)
        viewModel.resetWarmupEditor()
        viewModel.cancelWarmupEditor()
        assertNotNull(viewModel.state.value.warmupRampEdits[key])

        // Reset then Save removes it, returning the collapsed defaults alone.
        viewModel.editWarmupRamp(40L, rampIndex = 2)
        viewModel.resetWarmupEditor()
        viewModel.saveWarmupEditor()
        assertNull(viewModel.state.value.warmupRampEdits[key])
        assertEquals(
            listOf(0),
            assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single().rows
                .map { it.key.rampIndex }
        )
    }

    @Test
    fun everyRampPositionKeepsItsEditVisibleCorrectableAndResettable() = runTest(dispatcher) {
        // Position 0 is the one whose default survives a 30 kg ceiling; positions 1 and 2 collapse
        // away, so each is checked for its own visibility, refusal, correction and reset behaviour.
        for (rampIndex in 0..2) {
            val repository = FakeWorkoutLogRepository()
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
            val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
            val viewModel = occurrenceViewModel(
                repository,
                schedule,
                guided = true,
                equipment = equipment.collaborator()
            )
            advanceUntilIdle()

            viewModel.editWarmupRamp(40L, rampIndex)
            viewModel.onWarmupEditorWeightChanged("65")
            viewModel.saveWarmupEditor()
            equipment.setMaxWeight(30.0)
            advanceUntilIdle()

            val retained = assertNotNull(viewModel.state.value.visibleWarmupView)
                .ramps.single().rows.single { it.key.rampIndex == rampIndex }
            assertEquals(65.0, retained.weightKg)
            assertTrue(retained.exceedsCeiling)

            // Above the limit it is refused, not clamped or written.
            viewModel.logWarmupRamp(40L, rampIndex)
            advanceUntilIdle()
            assertTrue(repository.all().isEmpty())

            // Corrected, it keeps the percentage of its own profile position and can be logged.
            viewModel.editWarmupRamp(40L, rampIndex)
            viewModel.onWarmupEditorWeightChanged("25")
            viewModel.saveWarmupEditor()
            val corrected = assertNotNull(viewModel.state.value.visibleWarmupView)
                .ramps.single().rows.single { it.key.rampIndex == rampIndex }
            assertEquals(25.0, corrected.weightKg)
            assertEquals(
                listOf(40, 60, 80)[rampIndex],
                viewModel.state.value.warmupRampEdits[WarmupRampKey(40L, rampIndex)]
                    ?.percentOfWorkingWeight
            )

            viewModel.logWarmupRamp(40L, rampIndex)
            advanceUntilIdle()
            val recorded = repository.all().single()
            assertTrue(recorded.isWarmup)
            assertEquals(25.0, recorded.weightKg)
            assertEquals(0, viewModel.state.value.guidedProgress?.performedSets)
            assertNull(viewModel.state.value.restTimer)

            // Reset then Cancel preserves the applied edit.
            viewModel.editWarmupRamp(40L, rampIndex)
            viewModel.resetWarmupEditor()
            viewModel.cancelWarmupEditor()
            assertEquals(
                25.0,
                assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single().rows
                    .single { it.key.rampIndex == rampIndex }.weightKg
            )

            // Reset then Save removes the edit, leaving only the default that survived.
            viewModel.editWarmupRamp(40L, rampIndex)
            viewModel.resetWarmupEditor()
            viewModel.saveWarmupEditor()
            assertNull(viewModel.state.value.warmupRampEdits[WarmupRampKey(40L, rampIndex)])
            val afterReset = assertNotNull(viewModel.state.value.visibleWarmupView)
                .ramps.single().rows
            assertEquals(listOf(0), afterReset.map { it.key.rampIndex })
            assertEquals(30.0, afterReset.single().weightKg)
            assertEquals(40, afterReset.single().percentOfWorkingWeight)
        }
    }

    @Test
    fun anEditAboveAReducedEquipmentCeilingCannotBeLogged() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(
            blockActivation(),
            barbellOccurrences(),
            selectedOccurrenceId = 30L
        )
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.onWarmupEditorWeightChanged("80")
        viewModel.saveWarmupEditor()
        assertEquals(
            80.0,
            assertNotNull(viewModel.state.value.visibleWarmupView)
                .ramps.single().rows.first().weightKg
        )

        // The equipment ceiling drops below the retained edit.
        equipment.setMaxWeight(50.0)
        advanceUntilIdle()

        val row = assertNotNull(viewModel.state.value.visibleWarmupView)
            .ramps.single().rows.first()
        // The value is kept and explained, never silently clamped back to the ceiling.
        assertEquals(80.0, row.weightKg)
        assertTrue(row.exceedsCeiling)

        viewModel.logWarmupRamp(40L, rampIndex = 0)
        advanceUntilIdle()
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun removingTheEquipmentDropsTheRampAndItsStaleEdit() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.onWarmupEditorWeightChanged("80")
        viewModel.saveWarmupEditor()

        equipment.deselect()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.visibleWarmupGuidance?.ramps.orEmpty().isEmpty())
        assertTrue(viewModel.state.value.warmupRampEdits.isEmpty())

        // Re-owning it restores the derived default, not the discarded edit.
        equipment.select()
        advanceUntilIdle()
        assertEquals(
            40.0,
            assertNotNull(viewModel.state.value.visibleWarmupView)
                .ramps.single().rows.first().weightKg
        )
    }

    @Test
    fun anOpenRampEditorTakesTheCurrentCeilingAndClosesWithoutItsRamp() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), barbellOccurrences(), selectedOccurrenceId = 30L)
        val equipment = MutableEquipment(EquipmentTag.BARBELL, maxWeightKg = 200.0)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            equipment = equipment.collaborator()
        )
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        assertEquals(200.0, assertNotNull(viewModel.state.value.warmupEditor).ceilingKg)

        equipment.setMaxWeight(50.0)
        advanceUntilIdle()
        assertEquals(50.0, assertNotNull(viewModel.state.value.warmupEditor).ceilingKg)

        viewModel.onWarmupEditorWeightChanged("80")
        assertTrue(assertNotNull(viewModel.state.value.warmupEditor).weightExceedsCeiling)

        equipment.deselect()
        advanceUntilIdle()
        assertNull(viewModel.state.value.warmupEditor)
    }

    @Test
    fun aReplacedSlotDropsItsRampEdit() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.editWarmupRamp(40L, rampIndex = 0)
        viewModel.onWarmupEditorWeightChanged("45")
        viewModel.saveWarmupEditor()
        assertNull(
            assertNotNull(viewModel.state.value.visibleWarmupView)
                .ramps.single().rows.first().percentOfWorkingWeight
        )

        val replaced = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(
                    revision = 2,
                    entries = occurrence.entries.map { it.copy(weightKg = 60.0) }
                )
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), replaced, selectedOccurrenceId = 30L)
        advanceUntilIdle()

        val row = assertNotNull(viewModel.state.value.visibleWarmupView)
            .ramps.single().rows.first()
        assertEquals(25.0, row.weightKg)
        assertEquals(40, row.percentOfWorkingWeight)
        assertTrue(viewModel.state.value.warmupRampEdits.isEmpty())
    }

    private fun preparationRow(
        viewModel: WorkoutLoggerViewModel,
        action: WarmupAction
    ): WarmupPreparationRow {
        val view = assertNotNull(viewModel.state.value.visibleWarmupView)
        return view.preparation.first { it.action == action }
    }

    private fun rampRow(viewModel: WorkoutLoggerViewModel): WarmupRampRow =
        assertNotNull(viewModel.state.value.visibleWarmupView).ramps.single().rows.first()

    @Test
    fun liveGuidedCompletionStartsTimerOnlyAfterSuccessfulWriteAndUsesNow() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        var elapsedNow = 50_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        advanceUntilIdle()
        viewModel.onPerformedAtChanged(MONDAY - 60_000L)

        viewModel.startGuidedSet(40L)
        assertEquals(0, repository.all().size)
        elapsedNow = 50_750L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()

        assertEquals(MONDAY, repository.all().single().performedAtMillis)
        assertEquals(
            WorkoutTimingProvenance.LIVE,
            repository.all().single().timingProvenance
        )
        assertEquals(50_000L, repository.all().single().startedAtElapsedMillis)
        assertEquals(50_750L, repository.all().single().completedAtElapsedMillis)
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)
        assertEquals("120", viewModel.state.value.restDurationSeconds)
        viewModel.cancelRestTimer()
    }

    @Test
    fun liveCompletionOfTheDayFinalSetStartsTheRestTimer() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        var elapsedNow = 50_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        elapsedNow = 50_750L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)

        // The day's last prescribed set intentionally starts the prompt too (user-approved 2026-10-15).
        elapsedNow = 60_000L
        viewModel.startGuidedSet(40L)
        elapsedNow = 60_750L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()

        assertEquals(2, repository.all().size)
        assertEquals(2, viewModel.state.value.guidedProgress?.performedSets)
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)
        viewModel.cancelRestTimer()
    }

    @Test
    fun liveTimerUsesGlobalDefaultAndRemembersOrResetsExerciseOverride() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val restPreferences = FakeRestPreferenceRepository(defaultSeconds = 180L)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            restPreferences = restPreferences
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(180_000L, viewModel.state.value.restTimer?.countdown?.durationMillis)
        assertFalse(viewModel.state.value.restDurationIsOverride)

        viewModel.onRestDurationChanged("240")
        assertFalse(viewModel.state.value.restDurationIsOverride)
        runCurrent()
        assertEquals(240L, restPreferences.exerciseOverrideSeconds("back-squat"))
        assertEquals(240_000L, viewModel.state.value.restTimer?.countdown?.durationMillis)
        assertTrue(viewModel.state.value.restDurationIsOverride)

        viewModel.resetExerciseRestDuration()
        runCurrent()
        assertNull(restPreferences.exerciseOverrideSeconds("back-squat"))
        assertEquals(180_000L, viewModel.state.value.restTimer?.countdown?.durationMillis)
        assertFalse(viewModel.state.value.restDurationIsOverride)
        viewModel.cancelRestTimer()
    }

    @Test
    fun correctingASetKeepsTheRestTimerRunningOnItsRemainingTime() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        var elapsedNow = 50_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        elapsedNow = 50_750L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)

        viewModel.editLoggedSet(viewModel.state.value.recentSets.single())
        viewModel.onLoggedSetRepsChanged("10")
        viewModel.saveLoggedSetEdit()
        runCurrent()

        assertEquals(10, repository.all().single().reps)
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)
    }

    @Test
    fun correctingAnUnrelatedSetLeavesTheRestTimerRunning() = runTimerTest {
        val otherSet = WorkoutSet(
            id = 900L,
            exerciseId = "back-squat",
            reps = 5,
            weightKg = 100.0,
            performedAtMillis = MONDAY,
            sessionId = "other-session",
            occurrenceId = 31L,
            occurrenceEntryId = 41L
        )
        val repository = FakeWorkoutLogRepository(initial = listOf(otherSet))
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertNotNull(viewModel.state.value.restTimer)

        viewModel.editLoggedSet(
            LoggedSetRow(
                id = otherSet.id,
                performedAtMillis = otherSet.performedAtMillis,
                exerciseId = otherSet.exerciseId,
                exerciseName = "Back Squat",
                reps = otherSet.reps,
                weightKg = otherSet.weightKg,
                isWarmup = false,
                occurrenceId = otherSet.occurrenceId,
                sessionId = otherSet.sessionId
            )
        )
        viewModel.onLoggedSetRepsChanged("6")
        viewModel.saveLoggedSetEdit()
        runCurrent()

        assertNotNull(viewModel.state.value.restTimer)
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)
    }

    @Test
    fun anOccurrenceRefreshKeepsTheRestTimerButSwitchingWorkoutClearsIt() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertNotNull(viewModel.state.value.restTimer)

        // The same workout is re-read with changed data (e.g. progress or a correction upstream).
        val refreshed = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(revision = occurrence.revision + 1)
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), refreshed, selectedOccurrenceId = 30L)
        // A running countdown keeps scheduling ticks, so advance only the immediate refresh work.
        runCurrent()

        assertNotNull(viewModel.state.value.restTimer)
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)

        schedule.set(
            blockActivation(),
            refreshed.map { it.copy(revision = it.revision + 1) },
            selectedOccurrenceId = 31L
        )
        advanceUntilIdle()

        assertNull(viewModel.state.value.restTimer)
    }

    @Test
    fun restoresPersistedCountdownThroughViewModelAfterSameBootRecreation() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val session = WorkoutSession(
            id = "session-restored",
            startedAtMillis = MONDAY,
            localEpochDay = 4L
        )
        val sessions = FakeWorkoutSessionRepository(session)
        val countdowns = FakeRestCountdownRepository(
            PersistedRestCountdown(
                deadlineElapsedMillis = 160_000L,
                durationMillis = 120_000L,
                bootIdentity = "boot-current",
                sessionId = session.id,
                occurrenceId = 30L,
                exerciseId = "back-squat"
            )
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            sessions = sessions,
            elapsedNow = { 100_000L },
            bootIdentity = { "boot-current" },
            restCountdownRepository = countdowns
        )

        advanceUntilIdle()
        assertTrue(viewModel.state.value.guidedEnabled)
        assertEquals(session.id, viewModel.state.value.activeSession?.id)
        viewModel.onResume()
        runCurrent()

        assertEquals(60_000L, viewModel.state.value.restTimer?.remainingMillis)
        assertEquals("back-squat", countdowns.countdown?.exerciseId)
        viewModel.cancelRestTimer()
    }

    @Test
    fun liveCompletionPersistsContextForSameBootViewModelRecreation() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val sessions = FakeWorkoutSessionRepository()
        val countdowns = FakeRestCountdownRepository(null)
        val firstViewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            sessions = sessions,
            elapsedNow = { 100_000L },
            bootIdentity = { "boot-current" },
            restCountdownRepository = countdowns
        )

        advanceUntilIdle()
        firstViewModel.startGuidedSet(40L)
        firstViewModel.confirmGuidedSetNow(40L)
        runCurrent()

        val stored = assertNotNull(countdowns.countdown)
        assertEquals(firstViewModel.state.value.activeSession?.id, stored.sessionId)
        assertEquals(30L, stored.occurrenceId)
        assertEquals("back-squat", stored.exerciseId)

        val recreatedViewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            sessions = sessions,
            elapsedNow = { 100_000L },
            bootIdentity = { "boot-current" },
            restCountdownRepository = countdowns
        )
        runCurrent()
        recreatedViewModel.onResume()
        runCurrent()

        assertEquals(120_000L, recreatedViewModel.state.value.restTimer?.remainingMillis)
        recreatedViewModel.cancelRestTimer()
        firstViewModel.cancelRestTimer()
    }

    @Test
    fun viewModelFailsClosedAndClearsExpiredOrWrongBootCountdowns() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val session = WorkoutSession(
            id = "session-restored",
            startedAtMillis = MONDAY,
            localEpochDay = 4L
        )
        val sessions = FakeWorkoutSessionRepository(session)
        val countdowns = FakeRestCountdownRepository(
            PersistedRestCountdown(
                deadlineElapsedMillis = 99_000L,
                durationMillis = 120_000L,
                bootIdentity = "boot-current",
                sessionId = session.id,
                occurrenceId = 30L,
                exerciseId = "back-squat"
            )
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            sessions = sessions,
            elapsedNow = { 100_000L },
            bootIdentity = { "boot-current" },
            restCountdownRepository = countdowns
        )

        advanceUntilIdle()
        viewModel.onResume()
        advanceUntilIdle()

        assertNull(viewModel.state.value.restTimer)
        assertNull(countdowns.countdown)
        viewModel.cancelRestTimer()
    }

    @Test
    fun viewModelClearsCountdownWhenBootIdentityChanges() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val session = WorkoutSession(
            id = "session-restored",
            startedAtMillis = MONDAY,
            localEpochDay = 4L
        )
        val sessions = FakeWorkoutSessionRepository(session)
        val countdowns = FakeRestCountdownRepository(
            PersistedRestCountdown(
                deadlineElapsedMillis = 160_000L,
                durationMillis = 120_000L,
                bootIdentity = "boot-previous",
                sessionId = session.id,
                occurrenceId = 30L,
                exerciseId = "back-squat"
            )
        )
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            sessions = sessions,
            elapsedNow = { 100_000L },
            bootIdentity = { "boot-current" },
            restCountdownRepository = countdowns
        )

        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()

        assertNull(viewModel.state.value.restTimer)
        assertNull(countdowns.countdown)
        viewModel.cancelRestTimer()
    }

    @Test
    fun guidedSubmissionStaysGuardedWhileRestPreferencesAreLoading() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val preferences = FakeRestPreferenceRepository()
        val gate = CompletableDeferred<Unit>()
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            restPreferences = preferences
        )
        runCurrent()
        preferences.overrideReadGate = gate
        try {
            viewModel.startGuidedSet(40L)
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            assertEquals(1, repository.all().size)
            assertTrue(viewModel.state.value.draftWriteInProgress)

            viewModel.confirmGuidedSetNow(40L)
            viewModel.startGuidedSet(40L)
            runCurrent()
            assertEquals(1, repository.all().size)
        } finally {
            gate.complete(Unit)
            runCurrent()
        }
        assertFalse(viewModel.state.value.draftWriteInProgress)
        assertNull(viewModel.state.value.startedSetEntryId)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun restLookupReturningAfterSessionEndCannotReviveTheTimerOrLiveEvent() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val preferences = FakeRestPreferenceRepository()
        val countdowns = FakeRestCountdownRepository(null)
        val gate = CompletableDeferred<Unit>()
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            restPreferences = preferences,
            restCountdownRepository = countdowns,
            bootIdentity = { "boot" },
            coaching = true
        )
        runCurrent()
        preferences.overrideReadGate = gate
        try {
            viewModel.startGuidedSet(40L)
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            assertEquals(1, repository.all().size)
            viewModel.endSession()
            runCurrent()
        } finally {
            gate.complete(Unit)
            runCurrent()
        }

        assertNull(viewModel.state.value.restTimer)
        assertNull(countdowns.countdown)
        assertNull(viewModel.state.value.startedSetEntryId)
        assertFalse(viewModel.state.value.draftWriteInProgress)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun secondRestReadReturningAfterOccurrenceSwitchCannotPersistTheOldTimer() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val preferences = FakeRestPreferenceRepository()
        val countdowns = FakeRestCountdownRepository(null)
        val gate = CompletableDeferred<Unit>()
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            restPreferences = preferences,
            restCountdownRepository = countdowns,
            bootIdentity = { "boot" },
            coaching = true
        )
        runCurrent()
        preferences.overrideReadsBeforeGate = 1
        preferences.overrideReadGate = gate
        try {
            viewModel.startGuidedSet(40L)
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            assertEquals(1, repository.all().size)
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
            runCurrent()
        } finally {
            gate.complete(Unit)
            runCurrent()
        }

        assertEquals(31L, viewModel.state.value.activeOccurrence?.occurrenceId)
        assertNull(viewModel.state.value.restTimer)
        assertNull(countdowns.countdown)
        assertNull(viewModel.state.value.startedSetEntryId)
        assertNull(viewModel.state.value.shortRestCoachingExerciseName)
        assertFalse(viewModel.state.value.draftWriteInProgress)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun liveNoHistoryLoadFallbackKeepsTimingThroughEditorAndFailedRetry() = runTimerTest {
        for (warmup in listOf(false, true)) {
            val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
            val schedule = MutableWorkoutScheduleRepository()
            val occurrences = twoOccurrences().map { occurrence ->
                occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = null) })
            }
            schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
            var elapsed = 1_000L
            val viewModel = occurrenceViewModel(
                repository,
                schedule,
                guided = true,
                elapsedNow = { elapsed }
            )
            runCurrent()
            viewModel.onWarmupToggled(warmup)
            viewModel.startGuidedSet(40L)
            elapsed = 2_000L
            viewModel.confirmGuidedSetNow(40L)
            viewModel.useLastLoggedLoad()
            runCurrent()
            assertNotNull(viewModel.state.value.draftEdit)
            viewModel.onDraftWeightChanged("80")
            elapsed = 3_000L
            viewModel.confirmDraftEdit()
            runCurrent()
            // Applying the editor changes the pending set only; the explicit completion is the write,
            // and its failure keeps the live start for the retry below.
            assertTrue(repository.all().isEmpty())
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            assertTrue(viewModel.state.value.guidedSetWriteFailed)
            assertTrue(repository.all().isEmpty())
            viewModel.onWarmupToggled(!warmup)
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()

            val logged = repository.all().single()
            assertEquals(WorkoutTimingProvenance.LIVE, logged.timingProvenance)
            assertEquals(1_000L, logged.startedAtElapsedMillis)
            assertEquals(2_000L, logged.completedAtElapsedMillis)
            assertEquals(warmup, logged.isWarmup)
            assertEquals(40L, logged.occurrenceEntryId)
            assertEquals(80.0, logged.weightKg)
            assertNull(viewModel.state.value.startedSetEntryId)
            if (warmup) {
                assertNull(viewModel.state.value.restTimer)
            } else {
                assertEquals(119_000L, viewModel.state.value.restTimer?.remainingMillis)
            }
            viewModel.cancelRestTimer()
        }
    }

    @Test
    fun cancellingNoHistoryLiveLoadEditorDiscardsTheEventWithoutWriting() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(
            blockActivation(),
            twoOccurrences().map { occurrence ->
                occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = null) })
            },
            selectedOccurrenceId = 30L
        )
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()
        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        viewModel.useLastLoggedLoad()
        runCurrent()
        assertNotNull(viewModel.state.value.draftEdit)

        viewModel.cancelDraftEdit()
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()

        assertTrue(repository.all().isEmpty())
        assertNull(viewModel.state.value.startedSetEntryId)
        assertNull(viewModel.state.value.restTimer)
    }

    @Test
    fun useLastLoadConsumesLiveCompletionSoItCannotBeReused() = runTimerTest {
        val repository = FakeWorkoutLogRepository(
            initial = listOf(
                WorkoutSet(
                    id = 1L,
                    exerciseId = "back-squat",
                    reps = 5,
                    weightKg = 80.0,
                    performedAtMillis = MONDAY - DAY
                )
            )
        )
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = null) })
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertNotNull(viewModel.state.value.missingLoadPrompt)

        viewModel.useLastLoggedLoad()
        runCurrent()

        assertEquals(2, repository.all().size)
        assertNull(viewModel.state.value.startedSetEntryId)
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(2, repository.all().size)
        viewModel.cancelRestTimer()
    }

    @Test
    fun failedLiveGuidedWriteDoesNotStartRestAndOrdinaryConfirmNeverStartsRest() = runTimerTest {
        val failedRepository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val failed = occurrenceViewModel(failedRepository, schedule, guided = true)
        advanceUntilIdle()

        failed.startGuidedSet(40L)
        failed.confirmGuidedSetNow(40L)
        advanceUntilIdle()

        assertNull(failed.state.value.restTimer)
        assertTrue(failed.state.value.guidedSetWriteFailed)
        assertEquals(40L, failed.state.value.startedSetEntryId)

        failed.confirmGuidedSetNow(40L)
        runCurrent()

        assertEquals(1, failedRepository.all().size)
        assertNull(failed.state.value.startedSetEntryId)
        failed.cancelRestTimer()

        val ordinaryRepository = FakeWorkoutLogRepository()
        val ordinarySchedule = MutableWorkoutScheduleRepository()
        ordinarySchedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val ordinary = occurrenceViewModel(ordinaryRepository, ordinarySchedule, guided = true)
        advanceUntilIdle()

        ordinary.confirmGuidedSet(40L)
        advanceUntilIdle()

        assertNull(ordinary.state.value.restTimer)
    }

    @Test
    fun guidedEditConfirmStaysLiveAndConsumesTheEventOnce() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        var elapsedNow = 10_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        advanceUntilIdle()
        viewModel.onPerformedAtChanged(MONDAY - 60_000L)

        viewModel.startGuidedSet(40L)
        elapsedNow = 10_400L
        viewModel.editGuidedSet(40L)
        viewModel.confirmDraftEdit()
        runCurrent()

        // Applying the editor changes the pending set only; the live start is still unconsumed.
        assertTrue(repository.all().isEmpty())
        assertEquals(40L, viewModel.state.value.startedSetEntryId)
        assertNull(viewModel.state.value.restTimer)

        viewModel.confirmGuidedSetNow(40L)
        runCurrent()

        val recorded = repository.all().single()
        assertEquals(WorkoutTimingProvenance.LIVE, recorded.timingProvenance)
        assertEquals(10_000L, recorded.startedAtElapsedMillis)
        assertEquals(10_400L, recorded.completedAtElapsedMillis)
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)
        assertNull(viewModel.state.value.startedSetEntryId)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(1, repository.all().size)
        viewModel.cancelRestTimer()
    }

    @Test
    fun editingAnotherGuidedSlotCannotDiscardAnActiveLiveStart() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoEntryOccurrence(), selectedOccurrenceId = 30L)
        var elapsedNow = 10_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        elapsedNow = 10_400L
        viewModel.editGuidedSet(42L)
        assertEquals(42L, viewModel.state.value.draftEdit?.draft?.occurrenceEntryId)
        viewModel.confirmDraftEdit()
        runCurrent()

        assertTrue(repository.all().isEmpty())
        assertEquals(40L, viewModel.state.value.startedSetEntryId)

        elapsedNow = 10_500L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()

        val recorded = repository.all().single()
        assertEquals(WorkoutTimingProvenance.LIVE, recorded.timingProvenance)
        assertEquals(10_000L, recorded.startedAtElapsedMillis)
        assertEquals(10_500L, recorded.completedAtElapsedMillis)
        viewModel.cancelRestTimer()
    }

    @Test
    fun startingTheNextSetCancelsThePreviousRestCountdownAndPersistedRow() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val countdowns = FakeRestCountdownRepository(null)
        var elapsed = 1_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsed },
            bootIdentity = { "boot" },
            restCountdownRepository = countdowns
        )
        runCurrent()

        viewModel.startGuidedSet(40L)
        elapsed = 2_000L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertNotNull(viewModel.state.value.restTimer)
        assertNotNull(countdowns.countdown)

        elapsed = 12_000L
        viewModel.startGuidedSet(40L)
        runCurrent()

        // The new start cancels the previous prompt's countdown and persisted row; the start itself stays.
        assertNull(viewModel.state.value.restTimer)
        assertNull(countdowns.countdown)
        assertEquals(40L, viewModel.state.value.startedSetEntryId)
    }

    @Test
    fun repeatStartKeepsTheOriginalLiveStartTime() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        var elapsedNow = 10_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        elapsedNow = 40_000L
        // A repeat Start on the started slot must not re-baseline the live start.
        viewModel.startGuidedSet(40L)
        assertEquals(40L, viewModel.state.value.startedSetEntryId)

        elapsedNow = 41_000L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()

        assertEquals(10_000L, repository.all().single().startedAtElapsedMillis)
        assertEquals(41_000L, repository.all().single().completedAtElapsedMillis)
        viewModel.cancelRestTimer()
    }

    @Test
    fun startingADifferentSlotMovesTheLiveStart() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoEntryOccurrence(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.startGuidedSet(42L)

        // Selecting a different untouched slot stays possible while a set is started.
        assertEquals(42L, viewModel.state.value.startedSetEntryId)
        viewModel.cancelRestTimer()
    }

    @Test
    fun ordinaryGuidedLogIsRefusedWhileASetIsStarted() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        // The ordinary log cannot consume the live start; the start is still owned by the slot.
        assertTrue(repository.all().isEmpty())
        assertEquals(40L, viewModel.state.value.startedSetEntryId)

        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(1, repository.all().size)
        assertEquals(WorkoutTimingProvenance.LIVE, repository.all().single().timingProvenance)
        assertNull(viewModel.state.value.startedSetEntryId)
        viewModel.cancelRestTimer()
    }

    @Test
    fun recordedWorkFreezesItsSlotForReplacement() = runTest(dispatcher) {
        // Working and warm-up sets both freeze; an entry-less row freezes only its matching exercise.
        assertEquals(setOf(40L), frozenEntryIdsFor(listOf(occurrenceSet(40L))))
        assertEquals(setOf(40L), frozenEntryIdsFor(listOf(occurrenceSet(40L, isWarmup = true))))
        assertEquals(
            setOf(40L),
            frozenEntryIdsFor(listOf(occurrenceSet(entryId = null, exerciseId = "back-squat")))
        )
        assertTrue(
            frozenEntryIdsFor(listOf(occurrenceSet(entryId = null, exerciseId = "bench-press")))
                .isEmpty()
        )
    }

    @Test
    fun endingTheActiveBlockClearsFrozenReplacementState() = runTest(dispatcher) {
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoEntryOccurrence(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(
            FakeWorkoutLogRepository(initial = listOf(occurrenceSet(40L))),
            schedule,
            guided = true
        )
        advanceUntilIdle()
        assertEquals(setOf(40L), viewModel.state.value.frozenEntryIds)

        schedule.clearBlock()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.frozenEntryIds.isEmpty())
        assertNull(viewModel.state.value.activeOccurrence)

        val baseActivation = blockActivation()
        val nextActivation = baseActivation.copy(
            id = 6L,
            workouts = baseActivation.workouts.map { workout ->
                workout.copy(
                    id = workout.id + 100,
                    entries = workout.entries.map { it.copy(id = it.id + 100) }
                )
            }
        )
        val baseOccurrence = twoEntryOccurrence().first()
        val nextOccurrence = baseOccurrence.copy(
            id = 130L,
            activationId = nextActivation.id,
            activationWorkoutId = nextActivation.workouts.first().id,
            entries = baseOccurrence.entries.map { entry ->
                entry.copy(
                    id = entry.id + 100,
                    sourceActivationEntryId = entry.sourceActivationEntryId?.plus(100)
                )
            }
        )
        schedule.set(
            nextActivation,
            listOf(nextOccurrence),
            selectedOccurrenceId = nextOccurrence.id
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.frozenEntryIds.isEmpty())
        assertTrue(viewModel.state.value.canReplaceEntry(140L))
    }

    @Test
    fun replacementIsHiddenForFrozenAndStartedSlotsOnly() = runTest(dispatcher) {
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoEntryOccurrence(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(
            FakeWorkoutLogRepository(initial = listOf(occurrenceSet(40L))),
            schedule,
            guided = true
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canReplaceEntry(40L))
        assertTrue(viewModel.state.value.canReplaceEntry(42L))
    }

    @Test
    fun startedSlotHidesReplacementUntilItIsReleased() = runTest(dispatcher) {
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoEntryOccurrence(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(FakeWorkoutLogRepository(), schedule, guided = true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canReplaceEntry(40L))
        viewModel.startGuidedSet(40L)
        assertFalse(viewModel.state.value.canReplaceEntry(40L))
    }

    @Test
    fun backdatingAGuidedDraftDiscardsTheLiveEventAndStartsNoRest() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.editGuidedSet(40L)
        assertTrue(viewModel.onDraftPerformedAtChanged(MONDAY - 60_000L))
        viewModel.confirmDraftEdit()
        runCurrent()
        // The editor only applies; the explicit completion records the backdated set.
        assertTrue(repository.all().isEmpty())
        viewModel.confirmGuidedSet(40L)
        runCurrent()

        // An explicit performed time clears the live event, so the set is recorded as catch-up and
        // no prompt begins; the session-identity guard never has to reconcile a backdated write.
        val recorded = repository.all().single()
        assertEquals(WorkoutTimingProvenance.CATCH_UP, recorded.timingProvenance)
        assertNull(recorded.startedAtElapsedMillis)
        assertNull(recorded.completedAtElapsedMillis)
        assertNull(viewModel.state.value.restTimer)
        assertNull(viewModel.state.value.startedSetEntryId)
    }

    @Test
    fun guidedMissingLoadWithoutWeightStaysLiveAndConsumesTheEventOnce() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = null) })
            } else {
                occurrence
            }
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        var elapsedNow = 5_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsedNow }
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertNotNull(viewModel.state.value.missingLoadPrompt)

        elapsedNow = 5_500L
        viewModel.logMissingLoadWithoutWeight()
        runCurrent()

        val recorded = repository.all().single()
        assertEquals(WorkoutTimingProvenance.LIVE, recorded.timingProvenance)
        assertEquals(5_000L, recorded.startedAtElapsedMillis)
        // The completion instant is the "Set completed now" tap, not the later load resolution.
        assertEquals(5_000L, recorded.completedAtElapsedMillis)
        // The deadline counts from that completion instant, so at 5_500 the timer has run 500 ms.
        assertEquals(119_500L, viewModel.state.value.restTimer?.remainingMillis)
        assertNull(viewModel.state.value.startedSetEntryId)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertEquals(1, repository.all().size)
        viewModel.cancelRestTimer()
    }

    @Test
    fun cancelingLegacyResolutionDiscardsLiveSetTimingAndRequiresFreshStart() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            if (occurrence.id == 30L) {
                occurrence.copy(
                    entries = occurrence.entries.map {
                        it.copy(
                            loadCapability = ExerciseLoadCapability.UNSPECIFIED,
                            loadKind = LoadKind.LEGACY_UNSPECIFIED
                        )
                    }
                )
            } else {
                occurrence
            }
        }
        val schedule = MutableWorkoutScheduleRepository().apply {
            set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        }
        var elapsed = 10_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsed }
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        elapsed = 10_400L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertNotNull(viewModel.state.value.legacyResolution)
        assertEquals(40L, viewModel.state.value.startedSetEntryId)

        viewModel.dismissLegacyResolution()
        assertNull(viewModel.state.value.startedSetEntryId)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertTrue(repository.all().isEmpty())

        elapsed = 20_000L
        viewModel.startGuidedSet(40L)
        elapsed = 20_500L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        viewModel.resolveLegacyAsExternal()
        runCurrent()

        val recorded = repository.all().single()
        assertEquals(WorkoutTimingProvenance.LIVE, recorded.timingProvenance)
        assertEquals(20_000L, recorded.startedAtElapsedMillis)
        assertEquals(20_500L, recorded.completedAtElapsedMillis)
        viewModel.cancelRestTimer()
    }

    @Test
    fun selectingAnotherOccurrenceDuringAPendingLiveWriteDoesNotStartRestOrReuseTheEvent() =
        runTimerTest {
            val writeGate = CompletableDeferred<Unit>()
            val repository = FakeWorkoutLogRepository().apply { addGate = writeGate }
            val schedule = MutableWorkoutScheduleRepository()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            val viewModel = occurrenceViewModel(repository, schedule, guided = true)
            advanceUntilIdle()

            viewModel.startGuidedSet(40L)
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 31L)
            advanceUntilIdle()
            writeGate.complete(Unit)
            runCurrent()

            assertNull(viewModel.state.value.restTimer)
            assertNull(viewModel.state.value.startedSetEntryId)
            assertEquals(1, repository.all().size)
        }

    @Test
    fun endingSessionCancelsTimerAndInvalidatesAnEarlierLiveIntent() = runTimerTest {
        val writeGate = CompletableDeferred<Unit>()
        val repository = FakeWorkoutLogRepository().apply { addGate = writeGate }
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        viewModel.endSession()
        writeGate.complete(Unit)
        runCurrent()

        assertNull(viewModel.state.value.restTimer)
        assertNull(viewModel.state.value.startedSetEntryId)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun invalidDurationEnteredWhileLiveWriteIsPendingDoesNotCrashOrStartTimer() = runTimerTest {
        val writeGate = CompletableDeferred<Unit>()
        val repository = FakeWorkoutLogRepository().apply { addGate = writeGate }
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        viewModel.onRestDurationChanged("86401")
        writeGate.complete(Unit)
        runCurrent()

        assertEquals(1, repository.all().size)
        assertNull(viewModel.state.value.restTimer)
        assertTrue(!viewModel.state.value.canStartRestTimer)
    }

    @Test
    fun successfulTimeCorrectionOfTimerOccurrenceKeepsThePrompt() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { 50_000L }
        )
        advanceUntilIdle()

        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertNotNull(viewModel.state.value.restTimer)
        val savedSet = repository.all().single()

        assertTrue(viewModel.correctSetTime(savedSet.id, MONDAY - 86_400_000L, 12, 0))
        runCurrent()

        // Correcting a recorded set does not change which workout is being logged, so the running
        // countdown keeps counting.
        assertEquals(120_000L, viewModel.state.value.restTimer?.remainingMillis)
    }

    @Test
    fun guidedEditedSetRecordsOneSetWithTheEditedValues() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.editGuidedSet(40L)
        viewModel.onDraftRepsChanged("7")
        viewModel.confirmDraftEdit()
        advanceUntilIdle()

        // Editing applies to the pending set; only the explicit completion records it.
        assertTrue(repository.all().isEmpty())
        viewModel.confirmGuidedSet(40L)
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        assertEquals(7, repository.all().single().reps)
        assertEquals(40L, repository.all().single().occurrenceEntryId)
        assertEquals(1, viewModel.state.value.draftSets.single().sets)
    }

    @Test
    fun deletingAGuidedSetRestoresTheRemainingPrescription() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.confirmGuidedSet(40L)
        advanceUntilIdle()
        assertEquals(
            1,
            viewModel.state.value.guidedProgress?.exercises?.single()?.performedWorkingSets
        )

        viewModel.deleteSet(viewModel.state.value.recentSets.single().id)
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
        assertEquals(
            0,
            viewModel.state.value.guidedProgress?.exercises?.single()?.performedWorkingSets
        )
        assertEquals(2, viewModel.state.value.draftSets.single().sets)
    }

    @Test
    fun guidedProgressIgnoresWarmupAndUnattributedSets() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("8")
        viewModel.onWarmupToggled(true)
        viewModel.log()
        advanceUntilIdle()

        viewModel.onExerciseSelected("bench-press")
        viewModel.onRepsChanged("8")
        viewModel.log()
        advanceUntilIdle()

        assertEquals(2, repository.all().size)
        assertEquals(
            0,
            viewModel.state.value.guidedProgress?.exercises?.single()?.performedWorkingSets
        )
    }

    @Test
    fun guidedLogSetHonoursTheWarmupToggleAndKeepsTheEntryLink() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(entries = occurrence.entries.map { it.copy(sets = 3) })
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        // The guided row's Log set uses the guided path, which must still honour the toggle.
        viewModel.onWarmupToggled(true)
        viewModel.confirmGuidedSet(40L)
        advanceUntilIdle()

        val logged = repository.all().single()
        assertTrue(logged.isWarmup)
        assertEquals(30L, logged.occurrenceId)
        assertEquals(40L, logged.occurrenceEntryId)
        assertEquals(
            0,
            viewModel.state.value.guidedProgress?.exercises?.single()?.performedWorkingSets
        )
    }

    @Test
    fun manualWarmupLogKeepsTheOccurrenceEntryLink() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(entries = occurrence.entries.map { it.copy(sets = 3) })
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        // A warm-up logged manually must still carry both occurrence and entry attribution, because
        // the replacement and completion guards key on the entry id.
        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWarmupToggled(true)
        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertTrue(logged.isWarmup)
        assertEquals(30L, logged.occurrenceId)
        assertEquals(40L, logged.occurrenceEntryId)
    }

    @Test
    fun guidedWarmupSurvivesEveryLoadResolution() = runTimerTest {
        for (resolution in listOf("without-weight", "last-load", "enter-load", "legacy")) {
            val repository = FakeWorkoutLogRepository(
                initial = listOf(
                    WorkoutSet(
                        id = 1L,
                        exerciseId = "back-squat",
                        reps = 5,
                        weightKg = 80.0,
                        performedAtMillis = MONDAY - DAY
                    )
                )
            )
            val schedule = MutableWorkoutScheduleRepository()
            val occurrences = twoOccurrences().map { occurrence ->
                occurrence.copy(
                    entries = occurrence.entries.map {
                        it.copy(
                            weightKg = null,
                            loadKind = if (resolution == "legacy") {
                                LoadKind.LEGACY_UNSPECIFIED
                            } else {
                                LoadKind.EXTERNAL
                            },
                            loadCapability = ExerciseLoadCapability.EXTERNAL
                        )
                    }
                )
            }
            schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
            val countdowns = FakeRestCountdownRepository(null)
            val viewModel = occurrenceViewModel(
                repository,
                schedule,
                guided = true,
                bootIdentity = { "boot" },
                restCountdownRepository = countdowns,
                coaching = true
            )
            runCurrent()

            viewModel.onWarmupToggled(true)
            viewModel.startGuidedSet(40L)
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            when (resolution) {
                "without-weight" -> viewModel.logMissingLoadWithoutWeight()
                "last-load" -> viewModel.useLastLoggedLoad()
                "enter-load" -> {
                    viewModel.enterMissingLoad()
                    viewModel.onDraftWeightChanged("80")
                    viewModel.confirmDraftEdit()
                    viewModel.confirmGuidedSetNow(40L)
                }
                "legacy" -> viewModel.resolveLegacyAsExternal()
            }
            runCurrent()

            val logged = repository.all().last()
            assertEquals(2, repository.all().size, resolution)
            assertTrue(logged.isWarmup, resolution)
            assertEquals(40L, logged.occurrenceEntryId, resolution)
            assertEquals(0, viewModel.state.value.guidedProgress?.performedSets, resolution)
            assertFalse(viewModel.state.value.isWarmup, resolution)
            assertNull(viewModel.state.value.restTimer, resolution)
            assertNull(countdowns.countdown, resolution)
        }
    }

    @Test
    fun liveWarmupsNeverStartOrPersistRestOrProduceCoaching() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val countdowns = FakeRestCountdownRepository(null)
        var elapsed = 1_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsed },
            bootIdentity = { "boot" },
            restCountdownRepository = countdowns,
            coaching = true
        )
        runCurrent()

        repeat(3) {
            viewModel.onWarmupToggled(true)
            viewModel.startGuidedSet(40L)
            assertNull(viewModel.state.value.shortRestCoachingExerciseName)
            elapsed += 1_000L
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            assertNull(viewModel.state.value.restTimer)
            assertNull(countdowns.countdown)
            elapsed += 1_000L
        }
        assertTrue(repository.all().all { it.isWarmup })
        assertEquals(0, viewModel.state.value.guidedProgress?.performedSets)
    }

    @Test
    fun failedGuidedWarmupRetainsItsClassificationUntilSuccessfulRetry() = runTimerTest {
        val repository = FakeWorkoutLogRepository(failOnAddAttempt = 1)
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()
        viewModel.onWarmupToggled(true)
        viewModel.startGuidedSet(40L)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertTrue(viewModel.state.value.guidedSetWriteFailed)
        assertTrue(repository.all().isEmpty())

        viewModel.onWarmupToggled(false)
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        assertTrue(repository.all().single().isWarmup)
        assertNull(viewModel.state.value.restTimer)
        assertEquals(0, viewModel.state.value.guidedProgress?.performedSets)

        viewModel.confirmGuidedSet(40L)
        runCurrent()
        assertFalse(repository.all().last().isWarmup)
        assertEquals(1, viewModel.state.value.guidedProgress?.performedSets)
    }

    @Test
    fun manualLogLinksThePrescribedEntryWhenTheExerciseMatchesADraft() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(entries = occurrence.entries.map { it.copy(sets = 3) })
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.log()
        advanceUntilIdle()

        val logged = repository.all().single()
        assertEquals(30L, logged.occurrenceId)
        assertEquals(40L, logged.occurrenceEntryId)
    }

    @Test
    fun warmupStartBreaksTheWorkingSetCoachingStreak() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(entries = occurrence.entries.map { it.copy(sets = 5) })
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        var elapsed = 1_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsed },
            bootIdentity = { "boot" },
            coaching = true
        )
        runCurrent()
        repeat(2) {
            viewModel.startGuidedSet(40L)
            elapsed += 1_000L
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
            elapsed += 1_000L
        }

        viewModel.onWarmupToggled(true)
        viewModel.startGuidedSet(40L)
        assertNull(viewModel.state.value.shortRestCoachingExerciseName)
        elapsed += 1_000L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()

        repeat(2) {
            elapsed += 1_000L
            viewModel.startGuidedSet(40L)
            assertNull(viewModel.state.value.shortRestCoachingExerciseName)
            elapsed += 1_000L
            viewModel.confirmGuidedSetNow(40L)
            runCurrent()
        }
        assertEquals(1, repository.all().count { it.isWarmup })
        assertEquals(4, viewModel.state.value.guidedProgress?.performedSets)
    }

    @Test
    fun cancelingMissingLoadDiscardsThePendingWarmupClassification() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(entries = occurrence.entries.map { it.copy(weightKg = null) })
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(repository, schedule, guided = true)
        runCurrent()
        viewModel.onWarmupToggled(true)
        viewModel.confirmGuidedSet(40L)
        assertNotNull(viewModel.state.value.missingLoadPrompt)
        viewModel.cancelMissingLoadPrompt()
        assertTrue(repository.all().isEmpty())

        viewModel.onWarmupToggled(false)
        viewModel.confirmGuidedSet(40L)
        viewModel.logMissingLoadWithoutWeight()
        runCurrent()
        assertFalse(repository.all().single().isWarmup)
        assertEquals(1, viewModel.state.value.guidedProgress?.performedSets)
    }

    @Test
    fun coachingUsesTwoLiveRestsWithoutRecordingASetAtStartAndCanBeDismissed() = runTimerTest {
        val repository = FakeWorkoutLogRepository()
        val schedule = MutableWorkoutScheduleRepository()
        val occurrences = twoOccurrences().map { occurrence ->
            occurrence.copy(entries = occurrence.entries.map { it.copy(sets = 3) })
        }
        schedule.set(blockActivation(), occurrences, selectedOccurrenceId = 30L)
        var elapsed = 1_000L
        val viewModel = occurrenceViewModel(
            repository,
            schedule,
            guided = true,
            elapsedNow = { elapsed },
            bootIdentity = { "boot" },
            coaching = true
        )
        runCurrent()
        viewModel.startGuidedSet(40L)
        elapsed = 2_000L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        elapsed = 12_000L
        viewModel.startGuidedSet(40L)
        assertNull(viewModel.state.value.shortRestCoachingExerciseName)
        elapsed = 13_000L
        viewModel.confirmGuidedSetNow(40L)
        runCurrent()
        elapsed = 23_000L
        viewModel.startGuidedSet(40L)
        assertEquals("Back Squat", viewModel.state.value.shortRestCoachingExerciseName)
        assertEquals(2, repository.all().size)
        viewModel.dismissShortRestCoaching()
        assertNull(viewModel.state.value.shortRestCoachingExerciseName)
    }

    @Test
    fun alertsWaitForReadinessReplaceDurationAndCancelWithoutALoggerViewModel() = runTimerTest {
        val stored = PersistedRestCountdown(
            121_000L,
            120_000L,
            "boot",
            "session",
            30L,
            "back-squat"
        )
        val countdowns = FakeRestCountdownRepository(stored)
        val preferences = FakeTrainingGuidancePreferences()
        preferences.setRestAlertsEnabled(true)
        val guided = FakeGuidedWorkoutPreferenceRepository(true)
        val sessions = FakeWorkoutSessionRepository(
            WorkoutSession("session", MONDAY, localEpochDay = 0L, occurrenceId = 30L)
        )
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        val ready = MutableStateFlow(false)
        val scheduled = mutableListOf<PersistedRestCountdown>()
        var cancellations = 0
        var elapsed = 1_000L
        val alertTiming = MutableStateFlow(RestAlertTiming.INEXACT)
        val alerts = object : com.hydrafit.app.core.domain.workout.RestAlertScheduler {
            override val timing = alertTiming
            override suspend fun schedule(countdown: PersistedRestCountdown) {
                scheduled.add(countdown)
            }
            override fun cancel() {
                cancellations++
            }
        }
        val coordinator = RestAlertCoordinator(
            countdowns,
            preferences,
            guided,
            sessions,
            schedule,
            BootIdentityProvider { "boot" },
            object : com.hydrafit.app.core.domain.startup.StartupReadiness {
                override val isReady = ready
            },
            alerts,
            StandardTestDispatcher(testScheduler),
            { elapsed }
        )
        try {
            coordinator.start()
            runCurrent()
            assertTrue(scheduled.isEmpty())
            ready.value = true
            runCurrent()
            assertEquals(listOf(stored), scheduled)
            alertTiming.value = RestAlertTiming.PRECISE
            runCurrent()
            assertEquals(listOf(stored, stored), scheduled)
            alertTiming.value = RestAlertTiming.INEXACT
            runCurrent()
            assertEquals(listOf(stored, stored, stored), scheduled)
            preferences.setShortRestCoachingEnabled(true)
            runCurrent()
            assertEquals(3, scheduled.size)
            val edited = stored.copy(deadlineElapsedMillis = 181_000L, durationMillis = 180_000L)
            countdowns.save(edited)
            runCurrent()
            assertEquals(listOf(stored, stored, stored, edited), scheduled)
            elapsed = 200_000L
            preferences.setShortRestCoachingEnabled(false)
            runCurrent()
            assertEquals(0, cancellations) // A delayed alarm is not erased by an unrelated setting.
            alertTiming.value = RestAlertTiming.PRECISE
            runCurrent()
            // A capability change cannot replay an already due alert.
            assertEquals(4, scheduled.size)
            assertEquals(0, cancellations)
            preferences.setRestAlertsEnabled(false)
            runCurrent()
            assertEquals(1, cancellations)
            preferences.setRestAlertsEnabled(true)
            runCurrent()
            assertEquals(2, cancellations) // Disabling and re-enabling reconcile explicitly.
            countdowns.save(stored.copy(deadlineElapsedMillis = 320_000L))
            runCurrent()
            assertEquals(5, scheduled.size)
            schedule.set(
                blockActivation(),
                twoOccurrences().map { it.copy(status = OccurrenceStatus.FINISHED) },
                selectedOccurrenceId = 30L
            )
            runCurrent()
            assertEquals(3, cancellations)
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            runCurrent()
            // Reopening eligible context reconciles its future countdown.
            assertEquals(6, scheduled.size)
            sessions.end("session", MONDAY)
            runCurrent()
            assertEquals(4, cancellations)
            alertTiming.value = RestAlertTiming.INEXACT
            runCurrent()
            // A closed session cannot be resurrected by access changes.
            assertEquals(6, scheduled.size)
        } finally {
            coordinator.close()
            runCurrent()
        }
    }

    @Test
    fun countdownPresentationIsIndependentAndRemovedAtExpiryOrContextChange() = runTimerTest {
        val stored = PersistedRestCountdown(
            121_000L,
            120_000L,
            "boot",
            "session",
            30L,
            "back-squat"
        )
        val countdowns = FakeRestCountdownRepository(stored)
        val preferences = FakeTrainingGuidancePreferences()
        preferences.setRestCountdownEnabled(true)
        val sessions = FakeWorkoutSessionRepository(
            WorkoutSession("session", MONDAY, localEpochDay = 0L, occurrenceId = 30L)
        )
        val schedule = MutableWorkoutScheduleRepository().also {
            it.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        }
        var shown: PersistedRestCountdown? = null
        var alarms = 0
        var elapsed = 1_000L
        val alerts = object : com.hydrafit.app.core.domain.workout.RestAlertScheduler {
            override suspend fun showCountdown(countdown: PersistedRestCountdown?) {
                shown = countdown
            }
            override suspend fun schedule(countdown: PersistedRestCountdown) {
                alarms++
            }
            override fun cancel() = Unit
        }
        val coordinator = RestAlertCoordinator(
            countdowns,
            preferences,
            FakeGuidedWorkoutPreferenceRepository(true),
            sessions,
            schedule,
            BootIdentityProvider { "boot" },
            object : com.hydrafit.app.core.domain.startup.StartupReadiness {
                override val isReady = MutableStateFlow(true)
            },
            alerts,
            StandardTestDispatcher(testScheduler),
            { elapsed }
        )
        try {
            coordinator.start()
            runCurrent()
            assertEquals(stored, shown)
            assertEquals(0, alarms)
            preferences.setRestCountdownEnabled(false)
            runCurrent()
            assertNull(shown)
            preferences.setRestCountdownEnabled(true)
            runCurrent()
            assertEquals(stored, shown)
            elapsed = 121_000L
            advanceTimeBy(120_000L)
            runCurrent()
            assertNull(shown)
            preferences.setShortRestCoachingEnabled(true)
            runCurrent()
            assertNull(shown) // Expired timers never reappear on a preference change.
            countdowns.save(stored.copy(deadlineElapsedMillis = 241_000L))
            runCurrent()
            assertTrue(shown != null)
            sessions.end("session", MONDAY)
            runCurrent()
            assertNull(shown)
        } finally {
            coordinator.close()
            runCurrent()
        }
    }

    @Test
    fun dueCountdownSurvivesCoordinatorStartupButExplicitCancellationStillWins() = runTimerTest {
        val due = PersistedRestCountdown(
            100_000L,
            120_000L,
            "boot",
            "session",
            30L,
            "back-squat"
        )
        val countdowns = FakeRestCountdownRepository(due)
        val preferences = FakeTrainingGuidancePreferences().also {
            it.setRestAlertsEnabled(true)
        }
        val guided = FakeGuidedWorkoutPreferenceRepository(true)
        val sessions = FakeWorkoutSessionRepository(
            WorkoutSession("session", MONDAY, localEpochDay = 0L, occurrenceId = 30L)
        )
        val schedule = MutableWorkoutScheduleRepository().also {
            it.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        }
        val readiness = MutableStateFlow(false)
        var schedules = 0
        var cancellations = 0
        val alerts = object : com.hydrafit.app.core.domain.workout.RestAlertScheduler {
            override suspend fun schedule(countdown: PersistedRestCountdown) {
                schedules++
            }
            override fun cancel() {
                cancellations++
            }
        }
        val coordinator = RestAlertCoordinator(
            countdowns,
            preferences,
            guided,
            sessions,
            schedule,
            BootIdentityProvider { "boot" },
            object : com.hydrafit.app.core.domain.startup.StartupReadiness {
                override val isReady = readiness
            },
            alerts,
            StandardTestDispatcher(testScheduler),
            { 100_000L }
        )
        try {
            coordinator.start()
            runCurrent()
            readiness.value = true
            runCurrent()
            assertEquals(0, cancellations)
            assertEquals(0, schedules)

            preferences.setRestAlertsEnabled(false)
            runCurrent()
            assertEquals(1, cancellations)
            assertEquals(0, schedules)
        } finally {
            coordinator.close()
            runCurrent()
        }
    }

    @Test
    fun occurrenceReplacementViewModelLoadsChoicesAndIgnoresResultsAfterDismissal() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val fixture = occurrenceReplacementFixture(sourceGate = gate)
            fixture.viewModel.open(30L, 40L)
            runCurrent()

            assertTrue(fixture.viewModel.state.value.loading)
            fixture.viewModel.dismiss()
            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.state.value.open)
            assertNull(fixture.viewModel.state.value.choices)
        }

    @Test
    fun occurrenceReplacementViewModelLoadsCandidateChoices() = runTest(dispatcher) {
        val fixture = occurrenceReplacementFixture()
        fixture.viewModel.open(30L, 40L)
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertTrue(state.open)
        assertFalse(state.loading)
        assertEquals("back-squat", state.choices?.original?.exerciseId)
        assertEquals(listOf("front-squat"), state.choices?.alternatives?.map { it.exerciseId })
    }

    @Test
    fun occurrenceReplacementViewModelPreventsDuplicateConfirmationWhileSaving() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val fixture = occurrenceReplacementFixture(replacementGate = gate)
            fixture.viewModel.open(30L, 40L)
            advanceUntilIdle()
            fixture.viewModel.confirm("front-squat")
            runCurrent()

            assertTrue(fixture.viewModel.state.value.saving)
            fixture.viewModel.confirm("front-squat")
            runCurrent()
            assertEquals(1, fixture.repository.calls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(fixture.viewModel.state.value.open)
            assertEquals(1, fixture.repository.calls)
        }

    @Test
    fun occurrenceReplacementViewModelExposesFailureAndCanRetry() = runTest(dispatcher) {
        val fixture = occurrenceReplacementFixture()
        fixture.repository.failNext = true
        fixture.viewModel.open(30L, 40L)
        advanceUntilIdle()
        fixture.viewModel.confirm("front-squat")
        advanceUntilIdle()

        assertFalse(fixture.viewModel.state.value.saving)
        assertEquals(
            OccurrenceReplacementFailure.RECORDED_SLOT,
            fixture.viewModel.state.value.failure
        )
        fixture.viewModel.dismiss()
        fixture.viewModel.open(30L, 40L)
        advanceUntilIdle()
        fixture.viewModel.confirm("front-squat")
        advanceUntilIdle()

        assertFalse(fixture.viewModel.state.value.open)
        assertEquals(2, fixture.repository.calls)
    }

    private fun occurrenceReplacementFixture(
        sourceGate: CompletableDeferred<Unit>? = null,
        replacementGate: CompletableDeferred<Unit>? = null
    ): OccurrenceReplacementFixture {
        val schedule = MutableWorkoutScheduleRepository().apply {
            set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
        }
        val catalog = object : ExerciseCatalog {
            override suspend fun all() = listOf(
                Exercise(
                    id = "back-squat",
                    name = "Back Squat",
                    requiredEquipment = setOf(EquipmentTag.BARBELL),
                    primaryMuscles = setOf(MuscleGroup.QUADS),
                    movementPattern = MovementPattern.SQUAT
                ),
                Exercise(
                    id = "front-squat",
                    name = "Front Squat",
                    requiredEquipment = setOf(EquipmentTag.BARBELL),
                    primaryMuscles = setOf(MuscleGroup.QUADS),
                    movementPattern = MovementPattern.SQUAT
                )
            )
        }
        val history = FakePlanHistoryRepository(null)
        val sources = object : WorkoutPlanSourcesRepository {
            override fun observe() = flow {
                sourceGate?.await()
                emit(
                    WorkoutPlanSources(
                        availableEquipment = setOf(EquipmentTag.BARBELL),
                        selectedEngine = PlannerEngineId.DETERMINISTIC,
                        daysPerWeek = 1,
                        loggedSets = emptyList()
                    )
                )
            }
        }
        val periodization = PeriodizationConfig()
        val inputs = ObserveWorkoutPlanInputsUseCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { MONDAY },
            planHistoryRepository = history,
            buildPlannerLoadInputs = BuildPlannerLoadInputsUseCase(
                catalog = catalog,
                buildRecentWeights = BuildRecentWeightsUseCase(),
                progressWeights = ProgressWeightsUseCase()
            ),
            periodization = periodization
        )
        val replacementRepository = FakeOccurrenceReplacementRepository(replacementGate)
        val replacement = ReplaceOccurrenceExerciseUseCase(
            schedule = schedule,
            log = FakeWorkoutLogRepository(),
            catalog = catalog,
            replacements = replacementRepository,
            ranking = DeterministicWorkoutPlannerEngine(catalog),
            weights = SuggestedWeightConfig(),
            periodization = periodization
        )
        return OccurrenceReplacementFixture(
            OccurrenceReplacementViewModel(replacement, inputs),
            replacementRepository
        )
    }

    private data class OccurrenceReplacementFixture(
        val viewModel: OccurrenceReplacementViewModel,
        val repository: FakeOccurrenceReplacementRepository
    )

    private class FakeOccurrenceReplacementRepository(
        private val gate: CompletableDeferred<Unit>? = null
    ) : OccurrenceReplacementRepository {
        var calls = 0
        var failNext = false

        override suspend fun replaceUntouchedEntry(
            occurrenceId: Long,
            expectedRevision: Int,
            replacement: OccurrenceEntry,
            nowMillis: Long
        ) {
            calls++
            gate?.await()
            if (failNext) {
                failNext = false
                throw OccurrenceReplacementException(OccurrenceReplacementFailure.RECORDED_SLOT)
            }
        }
    }

    private fun testEquipment() =
        WorkoutLoggerEquipment(FakeEquipment(emptyList()), FakeSelection(emptySet()))

    private class FakeEquipment(private val items: List<Equipment>) : EquipmentRepository {
        override fun observeAll(): Flow<List<Equipment>> = flowOf(items)

        override suspend fun all(): List<Equipment> = items

        override suspend fun add(name: String): Equipment = error("No equipment writes expected")

        override suspend fun remove(id: EquipmentTag) = error("No equipment writes expected")

        override suspend fun rename(
            id: EquipmentTag,
            name: String,
            maxWeightKg: Double?
        ): Equipment = error("No equipment writes expected")

        override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) =
            error("No equipment writes expected")
    }

    private class FakeSelection(private val selected: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        override suspend fun selected(): Set<EquipmentTag> = selected

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = flowOf(selected)

        override suspend fun setSelected(tags: Set<EquipmentTag>) =
            error("No selection writes expected")
    }

    /** One equipment item whose ownership and ceiling can change during a test. */
    private class MutableEquipment(private val tag: EquipmentTag, maxWeightKg: Double?) {
        private val items = MutableStateFlow(listOf(item(maxWeightKg)))
        private val selected = MutableStateFlow(true)

        private fun item(maxWeightKg: Double?) = Equipment(
            id = tag,
            name = "Barbell",
            isBuiltIn = true,
            maxWeightKg = maxWeightKg
        )

        fun collaborator() = WorkoutLoggerEquipment(
            equipmentRepository = object : EquipmentRepository {
                override fun observeAll(): Flow<List<Equipment>> = items

                override suspend fun all(): List<Equipment> = items.value

                override suspend fun add(name: String): Equipment = error("unused")

                override suspend fun remove(id: EquipmentTag) = error("unused")

                override suspend fun rename(
                    id: EquipmentTag,
                    name: String,
                    maxWeightKg: Double?
                ): Equipment = error("unused")

                override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) =
                    error("unused")
            },
            selectionRepository = object : EquipmentSelectionRepository {
                override suspend fun selected(): Set<EquipmentTag> =
                    if (selected.value) setOf(tag) else emptySet()

                override fun selectedFlow(): Flow<Set<EquipmentTag>> =
                    selected.map { owned -> if (owned) setOf(tag) else emptySet() }

                override suspend fun setSelected(tags: Set<EquipmentTag>) = error("unused")
            }
        )

        fun setMaxWeight(maxWeightKg: Double?) {
            items.value = listOf(item(maxWeightKg))
        }

        fun deselect() {
            selected.value = false
        }

        fun select() {
            selected.value = true
        }
    }

    /**
     * Equipment whose first snapshot is withheld until [release], so a test can observe the window
     * before availability or ceilings are known.
     */
    private class GatedEquipment(
        private val items: List<Equipment>,
        private val selected: Set<EquipmentTag>
    ) {
        private val equipmentFlow = MutableSharedFlow<List<Equipment>>()
        private val selectionFlow = MutableSharedFlow<Set<EquipmentTag>>()

        fun collaborator() = WorkoutLoggerEquipment(
            equipmentRepository = object : EquipmentRepository {
                override fun observeAll(): Flow<List<Equipment>> = equipmentFlow

                override suspend fun all(): List<Equipment> = items

                override suspend fun add(name: String): Equipment = error("unused")

                override suspend fun remove(id: EquipmentTag) = error("unused")

                override suspend fun rename(
                    id: EquipmentTag,
                    name: String,
                    maxWeightKg: Double?
                ): Equipment = error("unused")

                override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) =
                    error("unused")
            },
            selectionRepository = object : EquipmentSelectionRepository {
                override suspend fun selected(): Set<EquipmentTag> = selected

                override fun selectedFlow(): Flow<Set<EquipmentTag>> = selectionFlow

                override suspend fun setSelected(tags: Set<EquipmentTag>) = error("unused")
            }
        )

        suspend fun release() {
            equipmentFlow.emit(items)
            selectionFlow.emit(selected)
        }
    }

    private fun occurrenceViewModel(
        repository: WorkoutLogRepository,
        schedule: WorkoutScheduleRepository,
        catalog: ExerciseCatalog = FakeExerciseCatalog,
        guided: Boolean = false,
        elapsedNow: () -> Long = { 0L },
        restPreferences: FakeRestPreferenceRepository = FakeRestPreferenceRepository(),
        sessions: FakeWorkoutSessionRepository = FakeWorkoutSessionRepository(),
        bootIdentity: () -> String? = { null },
        restCountdownRepository: RestCountdownRepository? = null,
        coaching: Boolean = false,
        equipment: WorkoutLoggerEquipment = testEquipment(),
        weightUnit: WeightUnit = WeightUnit.KG,
        weightUnitFlow: MutableStateFlow<WeightUnit>? = null,
        warmupOverrides: FakeWarmupPreparationOverrideRepository =
            FakeWarmupPreparationOverrideRepository()
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessions),
        logWorkoutSet = logWorkoutSet(repository, sessions),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(
            FakePlanHistoryRepository(null),
            repository,
            schedule,
            sessionRepository = sessions,
            catalog = catalog,
            timeMillis = 0L
        ),
        exerciseCatalog = catalog,
        runtime = WorkoutLoggerRuntime(
            TimeProvider { MONDAY },
            elapsedNow,
            BootIdentityProvider(bootIdentity),
            restCountdownRepository
        ),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(weightUnitFlow ?: MutableStateFlow(weightUnit)),
            FakeGuidedWorkoutPreferenceRepository(guided),
            restPreferences,
            FakeTrainingGuidancePreferences(coaching)
        ),
        exerciseNoteRepository = FakeExerciseNoteRepository(),
        sessionNoteRepository = FakeSessionNoteRepository(),
        warmupOverrideRepository = warmupOverrides,
        equipment = equipment
    ).also { timerViewModels.add(it) }

    private fun blockActivation() = TrainingActivation(
        id = 5L,
        name = "Block",
        createdAtMillis = 0L,
        startEpochDay = 0L,
        mode = com.hydrafit.app.core.domain.schedule.ScheduleMode.WEEKDAY,
        weekdays = setOf(com.hydrafit.app.core.domain.time.DayOfWeek.MONDAY),
        status = ActivationStatus.ACTIVE,
        workouts = listOf(
            ActivationWorkout(
                id = 9L,
                position = 0,
                name = "Upper",
                entries = listOf(
                    ActivationEntry(
                        id = 20L,
                        exerciseId = "back-squat",
                        exerciseName = "Back Squat",
                        movementPattern = MovementPattern.SQUAT,
                        sets = 2,
                        reps = 8,
                        weightKg = 100.0
                    )
                )
            ),
            ActivationWorkout(
                id = 10L,
                position = 1,
                name = "Lower",
                entries = listOf(
                    ActivationEntry(
                        id = 21L,
                        exerciseId = "deadlift",
                        exerciseName = "Deadlift",
                        movementPattern = MovementPattern.HINGE,
                        sets = 2,
                        reps = 5,
                        weightKg = 140.0
                    )
                )
            )
        )
    )

    private fun twoOccurrences() = listOf(
        WorkoutOccurrence(
            id = 30L,
            activationId = 5L,
            activationWorkoutId = 9L,
            queuePosition = 0,
            status = OccurrenceStatus.PENDING,
            entries = listOf(
                OccurrenceEntry(
                    id = 40L,
                    sourceActivationEntryId = 20L,
                    position = 0,
                    exerciseId = "back-squat",
                    exerciseName = "Back Squat",
                    movementPattern = MovementPattern.SQUAT,
                    sets = 2,
                    reps = 8,
                    weightKg = 100.0
                )
            )
        ),
        WorkoutOccurrence(
            id = 31L,
            activationId = 5L,
            activationWorkoutId = 10L,
            queuePosition = 1,
            status = OccurrenceStatus.PENDING,
            entries = listOf(
                OccurrenceEntry(
                    id = 41L,
                    sourceActivationEntryId = 21L,
                    position = 0,
                    exerciseId = "deadlift",
                    exerciseName = "Deadlift",
                    movementPattern = MovementPattern.HINGE,
                    sets = 2,
                    reps = 5,
                    weightKg = 140.0
                )
            )
        )
    )

    /** [twoOccurrences] with the selected occurrence's only entry timed at 90 seconds. */
    private fun timedOccurrences() = twoOccurrences().map { occurrence ->
        if (occurrence.id != 30L) return@map occurrence
        occurrence.copy(
            entries = occurrence.entries.map { entry ->
                entry.copy(sets = 3, reps = null, durationSeconds = 90)
            }
        )
    }

    private suspend fun TestScope.frozenEntryIdsFor(sets: List<WorkoutSet>): Set<Long> {
        val schedule = MutableWorkoutScheduleRepository()
        schedule.set(blockActivation(), twoEntryOccurrence(), selectedOccurrenceId = 30L)
        val viewModel = occurrenceViewModel(
            FakeWorkoutLogRepository(initial = sets),
            schedule,
            guided = true
        )
        advanceUntilIdle()
        return viewModel.state.value.frozenEntryIds
    }

    private fun occurrenceSet(
        entryId: Long?,
        exerciseId: String = "back-squat",
        isWarmup: Boolean = false
    ) = WorkoutSet(
        id = 1L,
        exerciseId = exerciseId,
        reps = 8,
        weightKg = 100.0,
        isWarmup = isWarmup,
        performedAtMillis = 1L,
        occurrenceId = 30L,
        occurrenceEntryId = entryId
    )

    /** Occurrence 30's only slot as a three-set ordered prescription: 10/8/6 at 60/70/80 kg. */
    private fun orderedOccurrences() = twoOccurrences().map { occurrence ->
        if (occurrence.id != 30L) return@map occurrence
        val targets = orderedTargets()
        occurrence.copy(
            entries = occurrence.entries.map { entry ->
                entry.copy(
                    sets = targets.sets,
                    reps = targets.targets.first().reps,
                    weightKg = targets.targets.first().weightKg,
                    setTargets = targets
                )
            }
        )
    }

    /** Occurrence 30's only slot as one drop-set cluster: 8 × 100 kg, 6 × 80 kg, 4 × 60 kg. */
    private fun clusterOccurrences() = twoOccurrences().map { occurrence ->
        if (occurrence.id != 30L) return@map occurrence
        occurrence.copy(
            entries = occurrence.entries.map { entry ->
                entry.copy(
                    sets = 1,
                    reps = 8,
                    weightKg = 100.0,
                    cluster = ClusterPrescription(
                        kind = ClusterKind.DROP_SET,
                        segments = listOf(
                            ClusterSegmentTarget(reps = 8, weightKg = 100.0),
                            ClusterSegmentTarget(reps = 6, weightKg = 80.0),
                            ClusterSegmentTarget(reps = 4, weightKg = 60.0)
                        )
                    )
                )
            }
        )
    }

    private fun orderedTargets() = SlotSetTargets.of(
        listOf(
            SetTarget(reps = 10, weightKg = 60.0, loadKind = LoadKind.EXTERNAL),
            SetTarget(reps = 8, weightKg = 70.0, loadKind = LoadKind.EXTERNAL),
            SetTarget(reps = 6, weightKg = 80.0, loadKind = LoadKind.EXTERNAL)
        )
    )

    /** Occurrence 30's only slot as a three-set timed prescription: 60/90/75 seconds. */
    private fun timedOrderedOccurrences() = twoOccurrences().map { occurrence ->
        if (occurrence.id != 30L) return@map occurrence
        val targets = SlotSetTargets.of(
            listOf(
                SetTarget(durationSeconds = 60, weightKg = 40.0, loadKind = LoadKind.EXTERNAL),
                SetTarget(durationSeconds = 90, weightKg = 45.0, loadKind = LoadKind.EXTERNAL),
                SetTarget(durationSeconds = 75, weightKg = 42.5, loadKind = LoadKind.EXTERNAL)
            )
        )
        occurrence.copy(
            entries = occurrence.entries.map { entry ->
                entry.copy(
                    sets = targets.sets,
                    reps = null,
                    durationSeconds = targets.targets.first().durationSeconds,
                    weightKg = targets.targets.first().weightKg,
                    setTargets = targets
                )
            }
        )
    }

    /** Occurrence 30 with two identical adjacent ordered targets, so value equality cannot tell them apart. */
    private fun equalOrderedOccurrences() = twoOccurrences().map { occurrence ->
        if (occurrence.id != 30L) return@map occurrence
        val targets = SlotSetTargets.of(
            listOf(
                SetTarget(reps = 10, weightKg = 60.0, loadKind = LoadKind.EXTERNAL),
                SetTarget(reps = 10, weightKg = 60.0, loadKind = LoadKind.EXTERNAL),
                SetTarget(reps = 8, weightKg = 70.0, loadKind = LoadKind.EXTERNAL)
            )
        )
        occurrence.copy(
            entries = occurrence.entries.map { entry ->
                entry.copy(
                    sets = targets.sets,
                    reps = targets.targets.first().reps,
                    weightKg = targets.targets.first().weightKg,
                    setTargets = targets
                )
            }
        )
    }

    private fun twoEntryOccurrence() = twoOccurrences().map { occurrence ->
        if (occurrence.id != 30L) return@map occurrence
        occurrence.copy(
            entries = occurrence.entries + OccurrenceEntry(
                id = 42L,
                sourceActivationEntryId = 21L,
                position = 1,
                exerciseId = "deadlift",
                exerciseName = "Deadlift",
                movementPattern = MovementPattern.HINGE,
                sets = 2,
                reps = 5,
                weightKg = 140.0
            )
        )
    }

    private fun barbellOccurrences() = twoOccurrences().map { occurrence ->
        occurrence.copy(
            entries = occurrence.entries.map {
                it.copy(requiredEquipment = setOf(EquipmentTag.BARBELL))
            }
        )
    }

    private fun loggingActions(
        history: PlanHistoryRepository,
        workoutLog: WorkoutLogRepository = FakeWorkoutLogRepository(),
        schedule: WorkoutScheduleRepository = FakeWorkoutScheduleRepository(),
        sessionRepository: WorkoutSessionRepository = FakeWorkoutSessionRepository(),
        sessionNoteRepository: SessionNoteRepository = FakeSessionNoteRepository(),
        catalog: ExerciseCatalog = FakeExerciseCatalog,
        timeMillis: Long = 0L
    ): WorkoutLoggingActions {
        val time = TimeProvider { timeMillis }
        return WorkoutLoggingActions(
            observeAcceptedPlanUseCase = ObserveAcceptedPlanUseCase(history),
            scheduleRepository = schedule,
            startWorkoutOccurrence = StartWorkoutOccurrenceUseCase(schedule, time),
            finishWorkoutOccurrence = FinishWorkoutOccurrenceUseCase(schedule, workoutLog, time),
            skipWorkoutOccurrence = SkipWorkoutOccurrenceUseCase(schedule, workoutLog, time),
            getWorkoutSummary = GetWorkoutSummaryUseCase(
                scheduleRepository = schedule,
                workoutLogRepository = workoutLog,
                sessionRepository = sessionRepository,
                sessionNoteRepository = sessionNoteRepository,
                exerciseCatalog = catalog
            )
        )
    }

    private fun acceptedPlan(
        dayZeroExerciseIds: List<String>,
        suggestedWeightKg: Double? = null,
        loadKind: LoadKind = LoadKind.EXTERNAL,
        explanation: ExerciseTargetExplanation? = null
    ) = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.FULL_BODY,
                exercises = dayZeroExerciseIds.map { id ->
                    AcceptedExercise(
                        exerciseId = id,
                        sets = 3,
                        reps = 8,
                        name = id,
                        movementPattern = MovementPattern.CORE,
                        suggestedWeightKg = suggestedWeightKg,
                        loadCapability = when (id) {
                            "plank" -> ExerciseLoadCapability.BODYWEIGHT_ONLY
                            "pull-up" -> ExerciseLoadCapability.BODYWEIGHT_ADDABLE
                            else -> ExerciseLoadCapability.EXTERNAL
                        },
                        loadKind = loadKind,
                        explanation = explanation
                    )
                }
            )
        )
    )

    private fun targetExplanation() = ExerciseTargetExplanation(
        setCount = SetCountExplanation(
            baseSets = 3,
            deloadApplied = false,
            deloadVolumeScale = 0.7,
            fatigueEffect = FatigueSetEffect.NONE,
            finalSets = 3
        )
    )

    private class FakePlanHistoryRepository(accepted: AcceptedPlan?) : PlanHistoryRepository {
        private val state = MutableStateFlow(accepted)
        private val reemits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        var accepted: AcceptedPlan?
            get() = state.value
            set(value) {
                state.value = value
            }

        /** Re-emits the current plan, as the real combine-based flow can on an upstream emission. */
        fun reemit() {
            reemits.tryEmit(Unit)
        }

        override fun observeLatest(): Flow<AcceptedPlan?> =
            merge(state, reemits.map { state.value })

        override fun observeHistory(): Flow<List<AcceptedPlan>> = state.map { listOfNotNull(it) }

        override suspend fun latest(): AcceptedPlan? = state.value

        override suspend fun accept(plan: AcceptedPlan) {
            state.value = plan
        }

        override suspend fun substitute(
            planId: Long,
            dayIndex: Int,
            position: Int,
            newExerciseId: String,
            newExerciseName: String,
            newWeightKg: Double?,
            newLoadCapability: ExerciseLoadCapability,
            newLoadKind: LoadKind
        ) = Unit

        override suspend fun setMeasure(
            planId: Long,
            dayIndex: Int,
            position: Int,
            reps: Int?,
            durationSeconds: Int?,
            distanceMeters: Int?,
            forceNewtons: Double?
        ) = Unit

        override suspend fun delete(planId: Long) {
            if (state.value?.id == planId) state.value = null
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private object FakeExerciseCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "back-squat",
                name = "Back Squat",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS),
                movementPattern = MovementPattern.SQUAT
            ),
            Exercise(
                id = "bench-press",
                name = "Bench Press",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
                movementPattern = MovementPattern.HORIZONTAL_PUSH
            ),
            Exercise(
                id = "plank",
                name = "Plank",
                requiredEquipment = emptySet(),
                primaryMuscles = setOf(MuscleGroup.ABS),
                movementPattern = MovementPattern.CORE,
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
            ),
            Exercise(
                id = "dumbbell-curl",
                name = "Dumbbell Curl",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.BICEPS),
                movementPattern = MovementPattern.BICEPS_ISOLATION
            ),
            Exercise(
                id = "pull-up",
                name = "Pull-up",
                requiredEquipment = emptySet(),
                primaryMuscles = setOf(MuscleGroup.LATS),
                movementPattern = MovementPattern.VERTICAL_PULL,
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ADDABLE
            )
        )
    }

    private class FakeWorkoutLogRepository(
        initial: List<WorkoutSet> = emptyList(),
        failOnAddAttempt: Int? = null,
        private val failOnAddAttempts: Set<Int> = setOfNotNull(failOnAddAttempt)
    ) : WorkoutLogRepository {
        private val sets = initial.toMutableList()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L
        private var addAttempts = 0

        /** When set, the next [all] call suspends until it completes, to test a delayed lookup. */
        var allGate: CompletableDeferred<Unit>? = null

        /** When true, [all] throws, to test a post-write refresh failure. */
        var failAll: Boolean = false
        var addGate: CompletableDeferred<Unit>? = null
        var addGateOnAttempt: Int? = null
        var correctionGate: CompletableDeferred<Unit>? = null
        var failCorrection: Boolean = false
        var corrections: Int = 0

        /** Every performed cluster written through [addCluster], in write order. */
        val clusters = mutableListOf<WorkoutCluster>()

        /** Recorded segments by parent set id, mirroring the persisted segment rows. */
        val clusterSegmentsById = mutableMapOf<Long, List<PerformedClusterSegment>>()

        suspend fun correct(id: Long, correction: WorkoutSetCorrection) {
            corrections++
            correctionGate?.await()
            if (failCorrection) error("Injected correction failure")
            val index = sets.indexOfFirst { it.id == id }
            require(index >= 0)
            sets[index] = sets[index].copy(
                reps = correction.reps,
                durationSeconds = correction.durationSeconds,
                distanceMeters = correction.distanceMeters,
                forceNewtons = correction.forceNewtons,
                device = correction.device,
                weightKg = correction.weightKg,
                rir = correction.rir,
                performedAtMillis = correction.performedAtMillis
            )
            correction.clusterSegments?.let { clusterSegmentsById[id] = it }
        }

        override suspend fun clusterSegments(setId: Long): List<PerformedClusterSegment> =
            clusterSegmentsById[setId].orEmpty()

        override suspend fun add(set: WorkoutSet) {
            addAttempts++
            addGate
                ?.takeIf { addGateOnAttempt == null || addGateOnAttempt == addAttempts }
                ?.let { gate ->
                    addGate = null
                    gate.await()
                }
            if (addAttempts in failOnAddAttempts) {
                error("Injected add failure")
            }
            sets.add(set.copy(id = nextId++))
        }

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) {
            clusters += cluster
            val assignedId = nextId
            add(
                set.copy(
                    cluster = WorkoutClusterSummary(
                        kind = cluster.kind,
                        plannedSegments = cluster.plannedSegments,
                        performedSegments = cluster.performedSegments
                    )
                )
            )
            clusterSegmentsById[assignedId] = cluster.segments
        }

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) {
            val existing = clusterSegmentsById.getValue(setId)
            require(segments.first().position == existing.size)
            val rowIndex = sets.indexOfFirst { it.id == setId }
            val summary = requireNotNull(sets[rowIndex].cluster)
            require(existing.size + segments.size <= summary.plannedSegments)
            clusterSegmentsById[setId] = existing + segments
            sets[rowIndex] =
                sets[rowIndex].copy(
                    cluster = summary.copy(
                        performedSegments =
                        existing.size + segments.size
                    )
                )
        }

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) {
            sets.removeAll { it.id == id }
        }

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) {
            val index = sets.indexOfFirst { it.id == setId }
            if (index >= 0) sets[index] = sets[index].copy(performedAtMillis = performedAtMillis)
        }

        override suspend fun all(): List<WorkoutSet> {
            if (failAll) error("Injected read failure")
            allGate?.let { gate ->
                allGate = null
                gate.await()
            }
            return sets.toList()
        }

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? =
            sets.filter { it.sessionId == sessionId }
                .maxWithOrNull(compareBy({ it.performedAtMillis }, { it.id }))

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> =
            sets.filter { it.occurrenceId == occurrenceId }

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets.toList())

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() {
            sets.clear()
        }
    }

    private class FakeWorkoutSessionRepository(initialSession: WorkoutSession? = null) :
        WorkoutSessionRepository {
        private val sessions = mutableListOf<WorkoutSession>().apply {
            if (initialSession != null) add(initialSession)
        }
        private val openSession = MutableStateFlow(initialSession)

        override suspend fun create(session: WorkoutSession) {
            sessions.add(session)
            if (session.endedAtMillis == null) openSession.value = session
        }

        override suspend fun end(id: String, endedAtMillis: Long) {
            val index = sessions.indexOfFirst { it.id == id }
            if (index >= 0) sessions[index] = sessions[index].copy(endedAtMillis = endedAtMillis)
            if (openSession.value?.id == id) openSession.value = null
        }

        override suspend fun open(): WorkoutSession? = openSession.value

        override fun openFlow(): Flow<WorkoutSession?> = openSession

        override suspend fun all(): List<WorkoutSession> = sessions.toList()
    }

    /** A unit preference a test can change while an editor is already open. */
    private class FakeWeightUnitRepository(private val units: MutableStateFlow<WeightUnit>) :
        WeightUnitRepository {
        constructor(unit: WeightUnit) : this(MutableStateFlow(unit))

        override suspend fun selectedUnit(): WeightUnit = units.value

        override fun unitFlow(): Flow<WeightUnit> = units

        override suspend fun setUnit(unit: WeightUnit) = Unit
    }

    private class FakeDistanceUnitRepository(private val units: MutableStateFlow<DistanceUnit>) :
        DistanceUnitRepository {
        override suspend fun selectedUnit(): DistanceUnit = units.value

        override fun unitFlow(): Flow<DistanceUnit> = units

        override suspend fun setUnit(unit: DistanceUnit) = Unit
    }

    private class FakeGuidedWorkoutPreferenceRepository(private var enabled: Boolean) :
        GuidedWorkoutPreferenceRepository {
        override suspend fun isGuidedWorkoutEnabled(): Boolean = enabled

        override fun guidedWorkoutFlow(): Flow<Boolean> = flowOf(enabled)

        override suspend fun setGuidedWorkoutEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class FakeRestPreferenceRepository(defaultSeconds: Long = 120L) :
        RestPreferenceRepository {
        private val global = MutableStateFlow(defaultSeconds)
        private val exerciseOverrides = mutableMapOf<String, Long>()
        var overrideReadGate: CompletableDeferred<Unit>? = null
        var overrideReadsBeforeGate = 0

        override fun globalDefaultSecondsFlow(): Flow<Long> = global

        override suspend fun globalDefaultSeconds(): Long = global.value

        override suspend fun setGlobalDefaultSeconds(seconds: Long) {
            global.value = seconds
        }

        override suspend fun exerciseOverrideSeconds(exerciseId: String): Long? {
            if (overrideReadsBeforeGate > 0) {
                overrideReadsBeforeGate--
            } else {
                overrideReadGate?.await()
            }
            return exerciseOverrides[exerciseId]
        }

        override suspend fun setExerciseOverrideSeconds(exerciseId: String, seconds: Long) {
            exerciseOverrides[exerciseId] = seconds
        }

        override suspend fun clearExerciseOverride(exerciseId: String) {
            exerciseOverrides.remove(exerciseId)
        }
    }

    private class FakeTrainingGuidancePreferences(private val coaching: Boolean = false) :
        com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository {
        private val state = MutableStateFlow(
            com.hydrafit.app.core.domain.settings.TrainingGuidancePreferences(
                shortRestCoachingEnabled = coaching
            )
        )
        override fun observe() = state
        override suspend fun get() = state.value
        override suspend fun setRestAlertsEnabled(enabled: Boolean) {
            state.value = state.value.copy(restAlertsEnabled = enabled)
        }
        override suspend fun setShortRestCoachingEnabled(enabled: Boolean) {
            state.value = state.value.copy(shortRestCoachingEnabled = enabled)
        }
        override suspend fun setRestCountdownEnabled(enabled: Boolean) {
            state.value = state.value.copy(restCountdownEnabled = enabled)
        }
        override suspend fun setRestCompletionSignalEnabled(enabled: Boolean) {
            state.value = state.value.copy(restCompletionSignalEnabled = enabled)
        }
    }

    private class FakeRestCountdownRepository(countdown: PersistedRestCountdown?) :
        RestCountdownRepository {
        private val state = MutableStateFlow(countdown)
        var countdown: PersistedRestCountdown?
            get() = state.value
            set(value) {
                state.value = value
            }
        override fun observe() = state
        override suspend fun load(): PersistedRestCountdown? = countdown

        override suspend fun save(countdown: PersistedRestCountdown) {
            this.countdown = countdown
        }

        override suspend fun clear() {
            countdown = null
        }
    }

    /** No active block: the Logger falls back to the accepted-plan path. */
    private class FakeWorkoutScheduleRepository : WorkoutScheduleRepository {
        override fun observeScheduleState(): Flow<WorkoutScheduleState> =
            flowOf(WorkoutScheduleState())

        override fun observeActiveActivation(): Flow<TrainingActivation?> = flowOf(null)

        override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
            flowOf(emptyList())

        override suspend fun scheduleState(): WorkoutScheduleState = WorkoutScheduleState()

        override suspend fun setScheduleState(state: WorkoutScheduleState) = Unit

        override suspend fun activeActivation(): TrainingActivation? = null

        override suspend fun getActivation(id: Long): TrainingActivation? = null

        override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> = emptyList()

        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? = null

        override suspend fun acceptAndActivate(
            acceptedPlan: AcceptedPlan?,
            activation: TrainingActivation,
            scheduledEpochDays: List<Long?>,
            replaceActive: Boolean
        ): Long = 0L

        override suspend fun resolveOccurrence(
            occurrenceId: Long,
            expectedRevision: Int,
            status: OccurrenceStatus,
            resolvedAtMillis: Long,
            entries: List<OccurrenceEntry>
        ): WorkoutScheduleState = WorkoutScheduleState()

        override suspend fun updateActivationHeader(activation: TrainingActivation) = Unit

        override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = Unit

        override suspend fun replaceOccurrenceEntries(
            occurrenceId: Long,
            entries: List<OccurrenceEntry>
        ) = Unit

        override suspend fun deleteOccurrencesForActivation(activationId: Long) = Unit

        override suspend fun isTemplateReferenced(templateId: Long): Boolean = false
    }

    /** A single active block whose occurrences can be read and resolved in memory. */
    private class MutableWorkoutScheduleRepository : WorkoutScheduleRepository {
        private val activationState = MutableStateFlow<TrainingActivation?>(null)
        private val occurrenceState = MutableStateFlow<List<WorkoutOccurrence>>(emptyList())
        private val state = MutableStateFlow(WorkoutScheduleState())

        /** Simulates the active block being replaced or cancelled as a resolve commits. */
        var cancelBlockOnResolve: Boolean = false

        /** Simulates the occurrence read failing, as a storage error would, after a resolve. */
        var failOccurrenceReadAfterResolve: Boolean = false

        /** Simulates the occurrence disappearing, as a concurrent delete would, after a resolve. */
        var dropOccurrenceAfterResolve: Boolean = false

        private var resolvedOnce: Boolean = false

        fun set(
            activation: TrainingActivation,
            occurrences: List<WorkoutOccurrence>,
            selectedOccurrenceId: Long?
        ) {
            activationState.value = activation
            occurrenceState.value = occurrences
            state.value = WorkoutScheduleState(
                activeActivationId = activation.id,
                selectedOccurrenceId = selectedOccurrenceId,
                legacyFallbackEnabled = false
            )
        }

        /** Simulates the active block being cancelled or ended, clearing it and its occurrences. */
        fun clearBlock() {
            activationState.value = null
            occurrenceState.value = emptyList()
            state.value = WorkoutScheduleState()
        }

        override fun observeScheduleState(): Flow<WorkoutScheduleState> = state

        override fun observeActiveActivation(): Flow<TrainingActivation?> = activationState

        override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
            occurrenceState.map { list ->
                list.filter { it.activationId == activationId }.sortedBy { it.queuePosition }
            }

        override suspend fun scheduleState(): WorkoutScheduleState = state.value

        override suspend fun setScheduleState(state: WorkoutScheduleState) {
            this.state.value = state
        }

        override suspend fun activeActivation(): TrainingActivation? = activationState.value

        override suspend fun getActivation(id: Long): TrainingActivation? =
            activationState.value?.takeIf { it.id == id }

        override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> =
            occurrenceState.value.filter { it.activationId == activationId }

        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? {
            if (resolvedOnce && failOccurrenceReadAfterResolve) {
                error("occurrence read failed")
            }
            if (resolvedOnce && dropOccurrenceAfterResolve) return null
            return occurrenceState.value.firstOrNull { it.id == id }
        }

        override suspend fun acceptAndActivate(
            acceptedPlan: AcceptedPlan?,
            activation: TrainingActivation,
            scheduledEpochDays: List<Long?>,
            replaceActive: Boolean
        ): Long = 0L

        override suspend fun resolveOccurrence(
            occurrenceId: Long,
            expectedRevision: Int,
            status: OccurrenceStatus,
            resolvedAtMillis: Long,
            entries: List<OccurrenceEntry>
        ): WorkoutScheduleState {
            occurrenceState.value = occurrenceState.value.map {
                if (it.id == occurrenceId) {
                    it.copy(status = status, resolvedAtMillis = resolvedAtMillis, entries = entries)
                } else {
                    it
                }
            }
            val next = occurrenceState.value
                .filter { it.activationId == activationState.value?.id && !it.isResolved }
                .minByOrNull { it.queuePosition }
            state.value = state.value.copy(selectedOccurrenceId = next?.id)
            resolvedOnce = true
            if (cancelBlockOnResolve) {
                activationState.value = null
                state.value = WorkoutScheduleState(
                    activeActivationId = null,
                    selectedOccurrenceId = null,
                    legacyFallbackEnabled = false
                )
            }
            return state.value
        }

        override suspend fun updateActivationHeader(activation: TrainingActivation) = Unit

        override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = Unit

        override suspend fun replaceOccurrenceEntries(
            occurrenceId: Long,
            entries: List<OccurrenceEntry>
        ) = Unit

        override suspend fun deleteOccurrencesForActivation(activationId: Long) = Unit

        override suspend fun isTemplateReferenced(templateId: Long): Boolean = false
    }

    class FakeExerciseNoteRepository : ExerciseNoteRepository {
        private val notes = mutableMapOf<String, String?>()
        var failSave = false

        /** When set, [save] suspends until it completes, to test a write in flight. */
        var saveGate: CompletableDeferred<Unit>? = null

        override suspend fun note(exerciseId: String): String? = notes[exerciseId]

        override suspend fun save(exerciseId: String, text: String) {
            if (text.length > NOTE_MAX_LENGTH) {
                throw IllegalArgumentException(
                    "Exercise notes must not exceed $NOTE_MAX_LENGTH characters"
                )
            }
            if (failSave) throw IllegalStateException("Save failed")
            saveGate?.await()
            notes[exerciseId] = text
        }

        override suspend fun clear(exerciseId: String) {
            notes[exerciseId] = ""
        }
    }

    class FakeSessionNoteRepository : SessionNoteRepository {
        val notes = mutableMapOf<String, String?>()
        val saved = mutableListOf<Pair<String, String>>()
        var failSave = false
        var failClear = false
        var failRead = false

        override suspend fun note(sessionId: String): String? {
            if (failRead) throw IllegalStateException("Read failed")
            return notes[sessionId]
        }

        override suspend fun save(sessionId: String, text: String) {
            if (failSave) throw IllegalStateException("Save failed")
            notes[sessionId] = text
            saved.add(sessionId to text)
        }

        override suspend fun clear(sessionId: String) {
            if (failClear) throw IllegalStateException("Clear failed")
            notes[sessionId] = ""
            saved.add(sessionId to "")
        }
    }

    /** In-memory preparation overrides, shared across ViewModel instances to model a restart. */
    class FakeWarmupPreparationOverrideRepository : WarmupPreparationOverrideRepository {
        private val overrides =
            MutableStateFlow<Map<WarmupAction, WarmupPreparationItem>>(emptyMap())

        /** Pre-seeds a stored override, as if an earlier session had persisted it. */
        fun seed(action: WarmupAction, item: WarmupPreparationItem) {
            overrides.value = overrides.value + (action to item)
        }

        override fun observeAll(): Flow<Map<WarmupAction, WarmupPreparationItem>> = overrides

        override suspend fun set(item: WarmupPreparationItem) {
            overrides.value = overrides.value + (item.action to item)
        }

        override suspend fun clear(action: WarmupAction) {
            overrides.value = overrides.value - action
        }
    }

    @Test
    fun noteLoadReturnsAbsentWhenNoNoteExists() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.noteText)
        assertEquals("back-squat", viewModel.state.value.noteExerciseId)
    }

    @Test
    fun noteLoadPersistsSavedValue() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.onNoteTextChanged("Set up tall")
        viewModel.saveNote()
        advanceUntilIdle()

        assertEquals("Set up tall", noteRepo.note("back-squat"))
        assertNull(viewModel.state.value.noteExerciseId)
    }

    @Test
    fun noteClearPersistsEmptyString() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.save("back-squat", "initial note")
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.clearNote()
        advanceUntilIdle()

        assertEquals("", noteRepo.note("back-squat"))
        assertNull(viewModel.state.value.noteExerciseId)
    }

    @Test
    fun anEarlierNoteSaveDoesNotDismissALaterSavesConfirmation() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        // First save: its 2 s dismissal timer starts now.
        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onNoteTextChanged("first")
        viewModel.saveNote()
        runCurrent()
        assertEquals(true, viewModel.state.value.noteSaved)

        // Second save inside that window must own the confirmation.
        advanceTimeBy(1_000)
        runCurrent()
        viewModel.onExerciseSelected("back-squat")
        runCurrent()
        viewModel.onNoteTextChanged("second")
        viewModel.saveNote()
        runCurrent()
        assertEquals(true, viewModel.state.value.noteSaved)

        // The first timer fires here; it must not clear the newer confirmation.
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(true, viewModel.state.value.noteSaved)

        // The second timer still dismisses its own confirmation.
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(false, viewModel.state.value.noteSaved)
    }

    @Test
    fun cancellingWhileANoteSaveIsInFlightIsIgnoredSoTheEditorCannotStrand() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        val gate = CompletableDeferred<Unit>()
        noteRepo.saveGate = gate
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onNoteTextChanged("saved under a gate")
        viewModel.saveNote()
        runCurrent()
        assertEquals(true, viewModel.state.value.noteSaving)

        // The write owns the editor state while it runs.
        viewModel.cancelNote()
        assertEquals(true, viewModel.state.value.noteSaving)

        gate.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.state.value.noteSaving)
        assertEquals(true, viewModel.state.value.noteSaved)
        assertNull(viewModel.state.value.noteExerciseId)
        assertEquals("saved under a gate", noteRepo.note("back-squat"))
    }

    @Test
    fun noteCancelDiscardsDraft() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.save("back-squat", "original")
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.onNoteTextChanged("changed")
        viewModel.cancelNote()
        advanceUntilIdle()

        assertEquals("original", noteRepo.note("back-squat"))
        assertNull(viewModel.state.value.noteExerciseId)
    }

    @Test
    fun noteLengthBoundaryAllowsMaxAndRejectsOver() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        val maxText = "x".repeat(NOTE_MAX_LENGTH)
        viewModel.onNoteTextChanged(maxText)
        viewModel.saveNote()
        advanceUntilIdle()

        assertEquals(maxText, noteRepo.note("back-squat"))

        // Reset and test over limit
        noteRepo.clear("back-squat")
        viewModel.cancelNote()
        advanceUntilIdle()

        val overText = "x".repeat(NOTE_MAX_LENGTH + 1)
        viewModel.onNoteExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onNoteTextChanged(overText)
        // Input is truncated at NOTE_MAX_LENGTH, so save succeeds with the truncated text.
        assertEquals(NOTE_MAX_LENGTH, viewModel.state.value.noteText.length)
        viewModel.saveNote()
        advanceUntilIdle()

        assertEquals("x".repeat(NOTE_MAX_LENGTH), noteRepo.note("back-squat"))
    }

    @Test
    fun noteFailureRetainsDraftAndSetsFailedFlag() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.failSave = true
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.onNoteTextChanged("will fail")
        viewModel.saveNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.noteFailed)
        assertEquals("will fail", viewModel.state.value.noteText)
        assertNull(noteRepo.note("back-squat"))

        // Retry after disabling failure
        noteRepo.failSave = false
        viewModel.saveNote()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.noteFailed)
        assertEquals("will fail", noteRepo.note("back-squat"))
        assertNull(viewModel.state.value.noteExerciseId)
    }

    @Test
    fun noteSavingBlocksConcurrentSaves() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        val gate = CompletableDeferred<Unit>()
        val blockingRepo = object : ExerciseNoteRepository {
            override suspend fun note(exerciseId: String): String? = noteRepo.note(exerciseId)
            override suspend fun save(exerciseId: String, text: String) {
                gate.await()
                noteRepo.save(exerciseId, text)
            }
            override suspend fun clear(exerciseId: String) {
                noteRepo.clear(exerciseId)
            }
        }
        val viewModel = viewModel(noteRepository = blockingRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.onNoteTextChanged("first")
        viewModel.saveNote()
        viewModel.saveNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.noteSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.noteSaving)
        assertEquals("first", noteRepo.note("back-squat"))
    }

    @Test
    fun noteExerciseSwitchingDiscardsOldDraft() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.save("back-squat", "squat note")
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.onNoteTextChanged("changed squat")
        viewModel.onExerciseSelected("bench-press")
        advanceUntilIdle()

        // The bench-press has no note, so it should be empty
        assertEquals("", viewModel.state.value.noteText)
        assertEquals("bench-press", viewModel.state.value.noteExerciseId)
        // The squat note should be unchanged
        assertEquals("squat note", noteRepo.note("back-squat"))
    }

    @Test
    fun noteRecreationStartsFresh() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.save("back-squat", "persistent")
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.onNoteTextChanged("draft")
        // Simulate ViewModel recreation by checking initial state
        val recreated = viewModel(
            noteRepository = FakeExerciseNoteRepository().apply {
                save("back-squat", "persistent")
            }
        )
        advanceUntilIdle()

        assertEquals("", recreated.state.value.noteText)
        assertNull(recreated.state.value.noteExerciseId)
    }

    @Test
    fun noteExerciseSwitchDiscardsOldDraft() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.save("back-squat", "squat note")
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        viewModel.onNoteTextChanged("draft for squat")
        // Switching exercise should load the new note and discard the draft
        viewModel.onExerciseSelected("bench-press")
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.noteText)
        assertEquals("bench-press", viewModel.state.value.noteExerciseId)
        assertNull(noteRepo.note("bench-press"))
        // Original draft should not be persisted
        assertEquals("squat note", noteRepo.note("back-squat"))
    }

    @Test
    fun noteOutOfOrderLoadKeepsLatestSelection() = runTest(dispatcher) {
        val slowGate = CompletableDeferred<Unit>()
        val repo = object : ExerciseNoteRepository {
            private val notes = mutableMapOf<String, String?>()

            override suspend fun note(exerciseId: String): String? {
                if (exerciseId == "slow") slowGate.await()
                return notes[exerciseId]
            }

            override suspend fun save(exerciseId: String, text: String) {
                notes[exerciseId] = text
            }

            override suspend fun clear(exerciseId: String) {
                notes[exerciseId] = ""
            }
        }
        val viewModel = viewModel(noteRepository = repo)
        advanceUntilIdle()

        // Select "slow" first; its load suspends. Then select "fast", whose load completes.
        viewModel.onNoteExerciseSelected("slow")
        runCurrent()
        viewModel.onNoteExerciseSelected("fast")
        advanceUntilIdle()

        assertEquals("fast", viewModel.state.value.noteExerciseId)

        // The stale "slow" load now resumes; it must not overwrite the newer selection.
        slowGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("fast", viewModel.state.value.noteExerciseId)
    }

    @Test
    fun noteLoadFailureSetsLoadFailedFlag() = runTest(dispatcher) {
        val blockingRepo = object : ExerciseNoteRepository {
            var failLoad = false
            private val notes = mutableMapOf<String, String?>()

            override suspend fun note(exerciseId: String): String? {
                if (failLoad) throw IllegalStateException("Load failed")
                return notes[exerciseId]
            }

            override suspend fun save(exerciseId: String, text: String) {
                notes[exerciseId] = text
            }

            override suspend fun clear(exerciseId: String) {
                notes[exerciseId] = ""
            }
        }
        val viewModel = viewModel(noteRepository = blockingRepo)
        advanceUntilIdle()

        blockingRepo.failLoad = true
        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.noteLoadFailed)
        assertEquals("back-squat", viewModel.state.value.noteExerciseId)

        // Retry should succeed
        blockingRepo.failLoad = false
        viewModel.retryNoteLoad()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.noteLoadFailed)
        assertEquals("", viewModel.state.value.noteText)
    }

    @Test
    fun noteInputTruncatesAtMaxLength() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        val overText = "x".repeat(NOTE_MAX_LENGTH + 10)
        viewModel.onNoteTextChanged(overText)

        assertEquals(NOTE_MAX_LENGTH, viewModel.state.value.noteText.length)
        assertEquals("x".repeat(NOTE_MAX_LENGTH), viewModel.state.value.noteText)
    }

    @Test
    fun noteRecentSetSelectedLoadsNote() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.save("deadlift", "deadlift setup")
        val setRow = LoggedSetRow(
            id = 1L,
            performedAtMillis = 1_000_000L,
            exerciseId = "deadlift",
            exerciseName = "Deadlift",
            weightKg = 100.0,
            reps = 5,
            rir = 1,
            isWarmup = false,
            loadKind = LoadKind.EXTERNAL,
            occurrenceId = 1L,
            sessionId = "session-1"
        )
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onRecentSetSelected(setRow)
        advanceUntilIdle()

        assertEquals("deadlift", viewModel.state.value.selectedExerciseId)
        assertEquals("deadlift", viewModel.state.value.noteExerciseId)
        assertEquals("deadlift setup", viewModel.state.value.noteText)
    }

    @Test
    fun noteSelectionResetsEditorBeforeNewNoteLoads() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        noteRepo.save("back-squat", "squat note")
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        assertEquals("back-squat", viewModel.state.value.noteExerciseId)
        assertEquals("squat note", viewModel.state.value.noteText)

        // The editor reset is synchronous, so it must not keep showing back-squat's draft while
        // bench-press's note is read.
        viewModel.onExerciseSelected("bench-press")
        assertNull(viewModel.state.value.noteExerciseId)
        assertEquals("", viewModel.state.value.noteText)

        // With no loaded editor, a write cannot target the previous exercise.
        viewModel.saveNote()
        assertNull(noteRepo.note("bench-press"))

        advanceUntilIdle()
        assertEquals("bench-press", viewModel.state.value.noteExerciseId)
        assertEquals("", viewModel.state.value.noteText)
        assertEquals("squat note", noteRepo.note("back-squat"))
    }

    @Test
    fun noteSaveWithoutLoadedEditorIsNoOp() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        // No exercise selected means no editor and therefore no write target.
        viewModel.saveNote()
        viewModel.clearNote()
        advanceUntilIdle()

        assertNull(noteRepo.note("back-squat"))
    }

    @Test
    fun noteSelectionIgnoredWhileWriteInFlight() = runTest(dispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        val repo = object : ExerciseNoteRepository {
            private val notes = mutableMapOf<String, String?>("back-squat" to "squat note")

            override suspend fun note(exerciseId: String): String? = notes[exerciseId]

            override suspend fun save(exerciseId: String, text: String) {
                saveGate.await()
                notes[exerciseId] = text
            }

            override suspend fun clear(exerciseId: String) {
                notes[exerciseId] = ""
            }
        }
        val viewModel = viewModel(noteRepository = repo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        assertEquals("back-squat", viewModel.state.value.noteExerciseId)

        viewModel.onNoteTextChanged("edited squat")
        viewModel.saveNote()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.noteSaving)

        // A selection change during the write is ignored, so the write target cannot drift.
        viewModel.onExerciseSelected("bench-press")
        advanceUntilIdle()
        assertEquals("back-squat", viewModel.state.value.selectedExerciseId)

        saveGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.noteSaving)
        assertEquals("edited squat", repo.note("back-squat"))
        assertNull(repo.note("bench-press"))
    }

    @Test
    fun noteSavedConfirmationSurvivesEditorClose() = runTest(dispatcher) {
        val noteRepo = FakeExerciseNoteRepository()
        val viewModel = viewModel(noteRepository = noteRepo)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()
        viewModel.onNoteTextChanged("keep me")
        viewModel.saveNote()
        // runCurrent, not advanceUntilIdle, so the 2s auto-dismiss has not fired yet.
        runCurrent()

        assertNull(viewModel.state.value.noteExerciseId)
        assertTrue(viewModel.state.value.noteSaved)

        advanceUntilIdle()
        assertFalse(viewModel.state.value.noteSaved)
    }

    @Test
    fun sessionNoteLoadsForTheActiveSessionId() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id

        viewModel.openSessionNote()
        advanceUntilIdle()

        assertEquals(sessionId, viewModel.state.value.sessionNoteSessionId)
        assertEquals("", viewModel.state.value.sessionNoteText)
        assertFalse(viewModel.state.value.sessionNoteLoadFailed)
    }

    @Test
    fun sessionNoteSaveWritesToTheActiveSessionId() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id

        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("Felt strong today")
        viewModel.saveSessionNote()
        runCurrent()

        assertEquals(listOf(sessionId to "Felt strong today"), noteRepo.saved)
        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertTrue(viewModel.state.value.sessionNoteSaved)

        advanceUntilIdle()
        assertFalse(viewModel.state.value.sessionNoteSaved)
    }

    @Test
    fun sessionNoteClearPersistsExplicitEmptyString() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        noteRepo.save(sessionId, "initial note")

        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertEquals("", noteRepo.notes[sessionId])
        assertNull(viewModel.state.value.sessionNoteSessionId)
    }

    @Test
    fun sessionNoteCancelWritesNothing() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        noteRepo.save(sessionId, "original")

        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("changed")
        viewModel.cancelSessionNote()
        advanceUntilIdle()

        assertEquals("original", noteRepo.notes[sessionId])
        assertEquals(1, noteRepo.saved.size)
        assertNull(viewModel.state.value.sessionNoteSessionId)
    }

    @Test
    fun sessionNoteWithoutAnActiveSessionIsInertAndCreatesNoSession() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()
        assertNull(viewModel.state.value.activeSession)

        viewModel.openSessionNote()
        viewModel.onSessionNoteTextChanged("orphan draft")
        viewModel.saveSessionNote()
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertTrue(noteRepo.saved.isEmpty())
        assertTrue(sessions.all().isEmpty())
        assertTrue(logs.all().isEmpty())
    }

    @Test
    fun sessionNoteSaveWithoutAnOpenEditorIsNoOp() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()

        // No editor was opened, so there is no session-note write target even with a session active.
        viewModel.saveSessionNote()
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertTrue(noteRepo.saved.isEmpty())
    }

    @Test
    fun sessionNoteEndNewKeepsTheOldNoteAndDoesNotCarryTheDraft() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val first = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("A note")
        viewModel.saveSessionNote()
        advanceUntilIdle()
        assertEquals("A note", noteRepo.notes[first])

        // Reopen A's editor with an unpersisted draft, then start a new session.
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("draft that must not persist")
        viewModel.newSession()
        advanceUntilIdle()
        val second = sessions.open()!!.id

        assertEquals("", viewModel.state.value.sessionNoteText)
        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertEquals("A note", noteRepo.notes[first])
        assertNull(noteRepo.notes[second])

        // The new session's editor starts empty and never writes A's draft.
        viewModel.openSessionNote()
        advanceUntilIdle()
        assertEquals(second, viewModel.state.value.sessionNoteSessionId)
        assertEquals("", viewModel.state.value.sessionNoteText)
        viewModel.onSessionNoteTextChanged("B note")
        viewModel.saveSessionNote()
        advanceUntilIdle()

        assertEquals("B note", noteRepo.notes[second])
        assertEquals("A note", noteRepo.notes[first])
    }

    @Test
    fun sessionNoteRecreationReloadsThePersistedNote() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val first = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        first.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        first.openSessionNote()
        advanceUntilIdle()
        first.onSessionNoteTextChanged("persistent")
        first.saveSessionNote()
        advanceUntilIdle()

        val recreated = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()
        recreated.openSessionNote()
        advanceUntilIdle()

        assertEquals(sessionId, recreated.state.value.sessionNoteSessionId)
        assertEquals("persistent", recreated.state.value.sessionNoteText)
    }

    @Test
    fun sessionNoteSaveFailureRetainsDraftAndRetrySucceeds() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        noteRepo.failSave = true
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("will fail")
        viewModel.saveSessionNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sessionNoteFailed)
        assertEquals("will fail", viewModel.state.value.sessionNoteText)
        assertEquals(sessionId, viewModel.state.value.sessionNoteSessionId)
        assertNull(noteRepo.notes[sessionId])

        noteRepo.failSave = false
        viewModel.saveSessionNote()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sessionNoteFailed)
        assertEquals("will fail", noteRepo.notes[sessionId])
        assertNull(viewModel.state.value.sessionNoteSessionId)
    }

    @Test
    fun sessionNoteClearFailureRetainsDraftAndRetrySucceeds() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        noteRepo.save(sessionId, "keep me")
        noteRepo.failClear = true

        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sessionNoteFailed)
        assertEquals("keep me", viewModel.state.value.sessionNoteText)
        assertEquals("keep me", noteRepo.notes[sessionId])

        noteRepo.failClear = false
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertEquals("", noteRepo.notes[sessionId])
        assertNull(viewModel.state.value.sessionNoteSessionId)
    }

    @Test
    fun sessionNoteDuplicateSaveWritesOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? = noteRepo.note(sessionId)

            override suspend fun save(sessionId: String, text: String) {
                gate.await()
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) = noteRepo.clear(sessionId)
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("first")
        viewModel.saveSessionNote()
        viewModel.saveSessionNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sessionNoteSaving)
        assertTrue(noteRepo.saved.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sessionNoteSaving)
        assertEquals(listOf(sessionId to "first"), noteRepo.saved)
    }

    @Test
    fun sessionNoteStaleWriteCannotAffectAChangedSession() = runTest(dispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? = noteRepo.note(sessionId)

            override suspend fun save(sessionId: String, text: String) {
                saveGate.await()
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) = noteRepo.clear(sessionId)
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val first = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("A note")
        viewModel.saveSessionNote()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sessionNoteSaving)

        // The write is still in flight; switching sessions must clear the editor immediately.
        viewModel.newSession()
        advanceUntilIdle()
        val second = sessions.open()!!.id
        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertFalse(viewModel.state.value.sessionNoteSaving)

        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("B draft")

        // The old write finishes for its original session and must not touch the new editor.
        saveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("A note", noteRepo.notes[first])
        assertNull(noteRepo.notes[second])
        assertEquals("B draft", viewModel.state.value.sessionNoteText)
        assertEquals(second, viewModel.state.value.sessionNoteSessionId)
        assertFalse(viewModel.state.value.sessionNoteFailed)
        assertFalse(viewModel.state.value.sessionNoteSaved)
    }

    @Test
    fun sessionNoteStaleLoadCannotReopenAfterSessionChange() = runTest(dispatcher) {
        val readGate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? {
                readGate.await()
                return noteRepo.note(sessionId)
            }

            override suspend fun save(sessionId: String, text: String) {
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) = noteRepo.clear(sessionId)
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        viewModel.openSessionNote()
        runCurrent()
        assertTrue(viewModel.state.value.sessionNoteLoading)

        // The session changes while the read is suspended; the late result must not reopen it.
        viewModel.newSession()
        advanceUntilIdle()

        readGate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertFalse(viewModel.state.value.sessionNoteLoading)
    }

    @Test
    fun sessionNoteLoadFailureSetsFlagAndRetrySucceeds() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        noteRepo.failRead = true
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id

        viewModel.openSessionNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sessionNoteLoadFailed)
        assertEquals(sessionId, viewModel.state.value.sessionNoteSessionId)

        noteRepo.failRead = false
        noteRepo.save(sessionId, "readable")
        viewModel.retrySessionNoteLoad()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sessionNoteLoadFailed)
        assertEquals("readable", viewModel.state.value.sessionNoteText)
    }

    @Test
    fun sessionNoteIgnoresExerciseSelectionChanges() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("session draft")

        // Selecting a different exercise is exercise-note context and must not disturb the session
        // note's identity or draft.
        viewModel.onExerciseSelected("bench-press")
        advanceUntilIdle()

        assertEquals(sessionId, viewModel.state.value.sessionNoteSessionId)
        assertEquals("session draft", viewModel.state.value.sessionNoteText)
    }

    @Test
    fun sessionNoteInputTruncatesAtMaxLength() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        viewModel.openSessionNote()
        advanceUntilIdle()

        viewModel.onSessionNoteTextChanged("x".repeat(NOTE_MAX_LENGTH + 10))

        assertEquals(NOTE_MAX_LENGTH, viewModel.state.value.sessionNoteText.length)
        assertEquals("x".repeat(NOTE_MAX_LENGTH), viewModel.state.value.sessionNoteText)
    }

    @Test
    fun sessionNoteActionsDoNotWriteExerciseNotes() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val exerciseNotes = FakeExerciseNoteRepository()
        val sessionNotes = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            noteRepository = exerciseNotes,
            sessionNoteRepository = sessionNotes
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("session only")
        viewModel.saveSessionNote()
        advanceUntilIdle()

        assertEquals(1, sessionNotes.saved.size)
        assertNull(exerciseNotes.note("back-squat"))
        assertNull(exerciseNotes.note("bench-press"))
    }

    @Test
    fun sessionNoteEndSessionInvalidatesTheEditorAndNoSessionWrites() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("draft that must not persist")

        viewModel.endSession()
        advanceUntilIdle()

        assertNull(viewModel.state.value.activeSession)
        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertEquals("", viewModel.state.value.sessionNoteText)

        // With no session, editing and saving stay inert.
        viewModel.saveSessionNote()
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertTrue(noteRepo.saved.isEmpty())
        assertNull(noteRepo.notes[sessionId])
    }

    @Test
    fun sessionNoteExternalSessionChangeInvalidatesTheEditor() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val first = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("draft")

        // An external writer closes the current session and opens a different one; the Logger only
        // observes the change, so this is not the End/New action path.
        sessions.end(first, MONDAY)
        sessions.create(
            WorkoutSession(id = "external-session", startedAtMillis = MONDAY, localEpochDay = 0L)
        )
        advanceUntilIdle()

        assertEquals("external-session", viewModel.state.value.activeSession?.id)
        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertEquals("", viewModel.state.value.sessionNoteText)

        viewModel.saveSessionNote()
        viewModel.clearSessionNote()
        advanceUntilIdle()
        assertTrue(noteRepo.saved.isEmpty())
    }

    @Test
    fun sessionNoteDuplicateClearWritesOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? = noteRepo.note(sessionId)

            override suspend fun save(sessionId: String, text: String) {
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) {
                gate.await()
                noteRepo.clear(sessionId)
            }
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.clearSessionNote()
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sessionNoteSaving)
        assertTrue(noteRepo.saved.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(sessionId to ""), noteRepo.saved)
    }

    @Test
    fun sessionNoteMixedSaveAndClearSubmitsOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? = noteRepo.note(sessionId)

            override suspend fun save(sessionId: String, text: String) {
                gate.await()
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) = noteRepo.clear(sessionId)
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val sessionId = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("only save")
        viewModel.saveSessionNote()
        viewModel.clearSessionNote()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sessionNoteSaving)
        assertTrue(noteRepo.saved.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        // Only the first submission lands; the queued Clear never writes.
        assertEquals(listOf(sessionId to "only save"), noteRepo.saved)
    }

    @Test
    fun sessionNoteStaleWriteFailureCannotAffectAChangedSession() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? = noteRepo.note(sessionId)

            override suspend fun save(sessionId: String, text: String) {
                gate.await()
                throw IllegalStateException("Save failed")
            }

            override suspend fun clear(sessionId: String) = noteRepo.clear(sessionId)
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("A note")
        viewModel.saveSessionNote()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sessionNoteSaving)

        viewModel.newSession()
        advanceUntilIdle()
        val second = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("B draft")

        gate.complete(Unit)
        advanceUntilIdle()

        // The failed write belonged to the old session and must not error the new editor.
        assertFalse(viewModel.state.value.sessionNoteFailed)
        assertEquals("B draft", viewModel.state.value.sessionNoteText)
        assertEquals(second, viewModel.state.value.sessionNoteSessionId)
        assertFalse(viewModel.state.value.sessionNoteSaved)
    }

    @Test
    fun sessionNoteStaleLoadFailureCannotAffectANewerEditor() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? {
                gate.await()
                throw IllegalStateException("Read failed")
            }

            override suspend fun save(sessionId: String, text: String) {
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) = noteRepo.clear(sessionId)
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        viewModel.openSessionNote()
        runCurrent()
        assertTrue(viewModel.state.value.sessionNoteLoading)

        viewModel.newSession()
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sessionNoteLoadFailed)
        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertFalse(viewModel.state.value.sessionNoteLoading)
    }

    @Test
    fun sessionNoteCancelWhileLoadingDiscardsThePendingRead() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? {
                gate.await()
                return noteRepo.note(sessionId)
            }

            override suspend fun save(sessionId: String, text: String) {
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) = noteRepo.clear(sessionId)
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        viewModel.openSessionNote()
        runCurrent()
        assertTrue(viewModel.state.value.sessionNoteLoading)

        viewModel.cancelSessionNote()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.state.value.sessionNoteSessionId)
        assertFalse(viewModel.state.value.sessionNoteLoading)
        assertFalse(viewModel.state.value.sessionNoteLoadFailed)
        assertEquals("", viewModel.state.value.sessionNoteText)
    }

    @Test
    fun sessionNoteStaleClearCompletionCannotAffectAChangedSession() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val noteRepo = FakeSessionNoteRepository()
        val gated = object : SessionNoteRepository {
            override suspend fun note(sessionId: String): String? = noteRepo.note(sessionId)

            override suspend fun save(sessionId: String, text: String) {
                noteRepo.save(sessionId, text)
            }

            override suspend fun clear(sessionId: String) {
                gate.await()
                noteRepo.clear(sessionId)
            }
        }
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val viewModel = sessionViewModel(logs, sessions, sessionNoteRepository = gated) { MONDAY }
        advanceUntilIdle()

        viewModel.newSession()
        advanceUntilIdle()
        val first = sessions.open()!!.id
        noteRepo.save(first, "A note")
        viewModel.openSessionNote()
        advanceUntilIdle()
        assertEquals("A note", viewModel.state.value.sessionNoteText)

        viewModel.clearSessionNote()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sessionNoteSaving)

        viewModel.newSession()
        advanceUntilIdle()
        val second = sessions.open()!!.id
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("B draft")

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("", noteRepo.notes[first])
        assertNull(noteRepo.notes[second])
        assertEquals("B draft", viewModel.state.value.sessionNoteText)
        assertEquals(second, viewModel.state.value.sessionNoteSessionId)
        assertFalse(viewModel.state.value.sessionNoteSaved)
        assertFalse(viewModel.state.value.sessionNoteFailed)
    }

    @Test
    fun sessionNoteDelayedSuccessDismissalDoesNotClearNewerFeedback() = runTest(dispatcher) {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val noteRepo = FakeSessionNoteRepository()
        val viewModel = sessionViewModel(
            logs,
            sessions,
            sessionNoteRepository = noteRepo
        ) { MONDAY }
        advanceUntilIdle()

        // First success: its 2s confirmation timer is now pending (fires at t=2000).
        viewModel.newSession()
        advanceUntilIdle()
        viewModel.openSessionNote()
        advanceUntilIdle()
        viewModel.onSessionNoteTextChanged("A note")
        viewModel.saveSessionNote()
        runCurrent()
        assertTrue(viewModel.state.value.sessionNoteSaved)

        // Move to a new session and post a newer success before the first timer expires: advance
        // 1s so the newer confirmation timer lands well after the stale one.
        advanceTimeBy(1_000)
        viewModel.newSession()
        advanceTimeBy(1)
        val second = sessions.open()!!.id
        viewModel.openSessionNote()
        runCurrent()
        viewModel.onSessionNoteTextChanged("B note")
        viewModel.saveSessionNote()
        runCurrent()
        assertTrue(viewModel.state.value.sessionNoteSaved)

        // Cross the stale timer only; the newer confirmation must survive it.
        advanceTimeBy(1_100)
        runCurrent()
        assertTrue(viewModel.state.value.sessionNoteSaved)

        // The newer confirmation still auto-dismisses on its own timer.
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(viewModel.state.value.sessionNoteSaved)
        assertEquals("B note", noteRepo.notes[second])
    }

    private companion object {
        /** Epoch millis whose `dayOfWeek` is MONDAY, matching a plan's first day. */
        const val MONDAY = 4L * 24L * 60L * 60L * 1000L
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
