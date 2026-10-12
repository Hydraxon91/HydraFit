package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.ProgressWeightsUseCase
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.FinishWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementException
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementFailure
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementRepository
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
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
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.ExerciseNoteRepository
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.SessionNoteRepository
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutLogMutations
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        assertEquals("220.5", viewModel.state.value.weightInput)
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
            sessionNoteRepository = FakeSessionNoteRepository()
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
            sessionNoteRepository = FakeSessionNoteRepository()
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
            sessionNoteRepository = FakeSessionNoteRepository()
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
            sessionNoteRepository = FakeSessionNoteRepository()
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
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("back-squat"), suggestedWeightKg = 100.0),
            weightUnit = WeightUnit.LB
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")

        // 100 kg -> 220.46 lb, shown to one decimal
        assertEquals("220.5", viewModel.state.value.weightInput)
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
            sessionNoteRepository = FakeSessionNoteRepository()
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
        viewModel.log()
        viewModel.log()
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

    private fun viewModel(
        repository: WorkoutLogRepository = FakeWorkoutLogRepository(),
        sessionRepository: WorkoutSessionRepository = FakeWorkoutSessionRepository(),
        timeMillis: Long = 1_000L,
        acceptedPlan: AcceptedPlan? = null,
        history: FakePlanHistoryRepository = FakePlanHistoryRepository(acceptedPlan),
        catalog: ExerciseCatalog = FakeExerciseCatalog,
        weightUnit: WeightUnit = WeightUnit.KG,
        noteRepository: ExerciseNoteRepository = FakeExerciseNoteRepository(),
        sessionNoteRepository: SessionNoteRepository = FakeSessionNoteRepository()
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessionRepository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(history, repository, timeMillis = timeMillis),
        exerciseCatalog = catalog,
        runtime = WorkoutLoggerRuntime(TimeProvider { timeMillis }),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(weightUnit),
            FakeGuidedWorkoutPreferenceRepository(false),
            FakeRestPreferenceRepository(),
            FakeTrainingGuidancePreferences()
        ),
        exerciseNoteRepository = noteRepository,
        sessionNoteRepository = sessionNoteRepository
    )

    private fun sessionViewModel(
        repository: WorkoutLogRepository,
        sessionRepository: WorkoutSessionRepository,
        noteRepository: ExerciseNoteRepository = FakeExerciseNoteRepository(),
        sessionNoteRepository: SessionNoteRepository = FakeSessionNoteRepository(),
        now: () -> Long
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessionRepository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(FakePlanHistoryRepository(null), repository),
        exerciseCatalog = FakeExerciseCatalog,
        runtime = WorkoutLoggerRuntime(TimeProvider { now() }),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(WeightUnit.KG),
            FakeGuidedWorkoutPreferenceRepository(false),
            FakeRestPreferenceRepository(),
            FakeTrainingGuidancePreferences()
        ),
        exerciseNoteRepository = noteRepository,
        sessionNoteRepository = sessionNoteRepository
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
            logWorkoutSet = LogWorkoutSetUseCase(
                repository = repository,
                startWorkoutSession = StartWorkoutSessionUseCase(sessionRepository),
                endWorkoutSession = EndWorkoutSessionUseCase(sessionRepository),
                observeOpenWorkoutSession = ObserveOpenWorkoutSessionUseCase(sessionRepository),
                config = SessionConfig()
            ),
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
        viewModel.log()
        viewModel.log()
        advanceUntilIdle()
        assertEquals(30L, schedule.scheduleState().selectedOccurrenceId)

        viewModel.finishWorkout()
        advanceUntilIdle()

        assertEquals(31L, schedule.scheduleState().selectedOccurrenceId)
        assertEquals("Lower", viewModel.state.value.activeOccurrence?.workoutName)
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
            assertTrue(viewModel.state.value.guidedSetWriteFailed)
            assertTrue(repository.all().isEmpty())
            viewModel.onWarmupToggled(!warmup)
            viewModel.confirmDraftEdit()
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
    fun successfulTimeCorrectionOfTimerOccurrenceCancelsPrompt() = runTimerTest {
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

        assertNull(viewModel.state.value.restTimer)
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
        val alerts = object : com.hydrafit.app.core.domain.workout.RestAlertScheduler {
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
            preferences.setShortRestCoachingEnabled(true)
            runCurrent()
            assertEquals(1, scheduled.size)
            val edited = stored.copy(deadlineElapsedMillis = 181_000L, durationMillis = 180_000L)
            countdowns.save(edited)
            runCurrent()
            assertEquals(listOf(stored, edited), scheduled)
            elapsed = 200_000L
            preferences.setShortRestCoachingEnabled(false)
            runCurrent()
            assertEquals(0, cancellations) // A delayed alarm is not erased by an unrelated setting.
            preferences.setRestAlertsEnabled(false)
            runCurrent()
            assertEquals(1, cancellations)
            preferences.setRestAlertsEnabled(true)
            runCurrent()
            assertEquals(2, cancellations) // Disabling and re-enabling reconcile explicitly.
            countdowns.save(stored.copy(deadlineElapsedMillis = 320_000L))
            runCurrent()
            assertEquals(3, scheduled.size)
            schedule.set(
                blockActivation(),
                twoOccurrences().map { it.copy(status = OccurrenceStatus.FINISHED) },
                selectedOccurrenceId = 30L
            )
            runCurrent()
            assertEquals(3, cancellations)
            schedule.set(blockActivation(), twoOccurrences(), selectedOccurrenceId = 30L)
            runCurrent()
            sessions.end("session", MONDAY)
            runCurrent()
            assertEquals(4, cancellations)
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
        coaching: Boolean = false
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessions),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(FakePlanHistoryRepository(null), repository, schedule, 0L),
        exerciseCatalog = catalog,
        runtime = WorkoutLoggerRuntime(
            TimeProvider { MONDAY },
            elapsedNow,
            BootIdentityProvider(bootIdentity),
            restCountdownRepository
        ),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(WeightUnit.KG),
            FakeGuidedWorkoutPreferenceRepository(guided),
            restPreferences,
            FakeTrainingGuidancePreferences(coaching)
        ),
        exerciseNoteRepository = FakeExerciseNoteRepository(),
        sessionNoteRepository = FakeSessionNoteRepository()
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

    private fun loggingActions(
        history: PlanHistoryRepository,
        workoutLog: WorkoutLogRepository = FakeWorkoutLogRepository(),
        schedule: WorkoutScheduleRepository = FakeWorkoutScheduleRepository(),
        timeMillis: Long = 0L
    ): WorkoutLoggingActions {
        val time = TimeProvider { timeMillis }
        return WorkoutLoggingActions(
            observeAcceptedPlanUseCase = ObserveAcceptedPlanUseCase(history),
            scheduleRepository = schedule,
            startWorkoutOccurrence = StartWorkoutOccurrenceUseCase(schedule, time),
            finishWorkoutOccurrence = FinishWorkoutOccurrenceUseCase(schedule, workoutLog, time),
            skipWorkoutOccurrence = SkipWorkoutOccurrenceUseCase(schedule, workoutLog, time)
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
        var addGate: CompletableDeferred<Unit>? = null
        var addGateOnAttempt: Int? = null
        var correctionGate: CompletableDeferred<Unit>? = null
        var failCorrection: Boolean = false
        var corrections: Int = 0

        suspend fun correct(id: Long, correction: WorkoutSetCorrection) {
            corrections++
            correctionGate?.await()
            if (failCorrection) error("Injected correction failure")
            val index = sets.indexOfFirst { it.id == id }
            require(index >= 0)
            sets[index] = sets[index].copy(
                reps = correction.reps,
                weightKg = correction.weightKg,
                rir = correction.rir,
                performedAtMillis = correction.performedAtMillis
            )
        }

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

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) {
            sets.removeAll { it.id == id }
        }

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) {
            val index = sets.indexOfFirst { it.id == setId }
            if (index >= 0) sets[index] = sets[index].copy(performedAtMillis = performedAtMillis)
        }

        override suspend fun all(): List<WorkoutSet> {
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

    private class FakeWeightUnitRepository(private val unit: WeightUnit) : WeightUnitRepository {
        override suspend fun selectedUnit(): WeightUnit = unit

        override fun unitFlow(): Flow<WeightUnit> = flowOf(unit)

        override suspend fun setUnit(unit: WeightUnit) = Unit
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

        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? =
            occurrenceState.value.firstOrNull { it.id == id }

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
