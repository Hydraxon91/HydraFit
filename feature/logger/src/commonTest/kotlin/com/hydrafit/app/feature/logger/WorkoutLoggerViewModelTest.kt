package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.FinishWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.SkipWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.StartWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutLoggingActions
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLoggerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

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
                FakeGuidedWorkoutPreferenceRepository(false)
            )
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
                FakeGuidedWorkoutPreferenceRepository(false)
            )
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
                FakeGuidedWorkoutPreferenceRepository(false)
            )
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
                FakeGuidedWorkoutPreferenceRepository(false)
            )
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
                FakeGuidedWorkoutPreferenceRepository(false)
            )
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

    private fun viewModel(
        repository: WorkoutLogRepository = FakeWorkoutLogRepository(),
        sessionRepository: WorkoutSessionRepository = FakeWorkoutSessionRepository(),
        timeMillis: Long = 1_000L,
        acceptedPlan: AcceptedPlan? = null,
        history: FakePlanHistoryRepository = FakePlanHistoryRepository(acceptedPlan),
        catalog: ExerciseCatalog = FakeExerciseCatalog,
        weightUnit: WeightUnit = WeightUnit.KG
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessionRepository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(history, repository, timeMillis = timeMillis),
        exerciseCatalog = catalog,
        runtime = WorkoutLoggerRuntime(TimeProvider { timeMillis }),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(weightUnit),
            FakeGuidedWorkoutPreferenceRepository(false)
        )
    )

    private fun sessionViewModel(
        repository: WorkoutLogRepository,
        sessionRepository: WorkoutSessionRepository,
        now: () -> Long
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessionRepository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(FakePlanHistoryRepository(null), repository),
        exerciseCatalog = FakeExerciseCatalog,
        runtime = WorkoutLoggerRuntime(TimeProvider { now() }),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(WeightUnit.KG),
            FakeGuidedWorkoutPreferenceRepository(false)
        )
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
    fun liveGuidedCompletionStartsTimerOnlyAfterSuccessfulWriteAndUsesNow() = runTest(dispatcher) {
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
    fun useLastLoadConsumesLiveCompletionSoItCannotBeReused() = runTest(dispatcher) {
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
    fun failedLiveGuidedWriteDoesNotStartRestAndOrdinaryConfirmNeverStartsRest() =
        runTest(dispatcher) {
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
    fun guidedEditConfirmStaysLiveAndConsumesTheEventOnce() = runTest(dispatcher) {
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
    fun guidedMissingLoadWithoutWeightStaysLiveAndConsumesTheEventOnce() = runTest(dispatcher) {
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
    fun selectingAnotherOccurrenceDuringAPendingLiveWriteDoesNotStartRestOrReuseTheEvent() =
        runTest(dispatcher) {
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
    fun endingSessionCancelsTimerAndInvalidatesAnEarlierLiveIntent() = runTest(dispatcher) {
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
    fun invalidDurationEnteredWhileLiveWriteIsPendingDoesNotCrashOrStartTimer() =
        runTest(dispatcher) {
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
    fun successfulTimeCorrectionOfTimerOccurrenceCancelsPrompt() = runTest(dispatcher) {
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

    private fun occurrenceViewModel(
        repository: WorkoutLogRepository,
        schedule: WorkoutScheduleRepository,
        catalog: ExerciseCatalog = FakeExerciseCatalog,
        guided: Boolean = false,
        elapsedNow: () -> Long = { 0L }
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, FakeWorkoutSessionRepository()),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        loggingActions = loggingActions(FakePlanHistoryRepository(null), repository, schedule, 0L),
        exerciseCatalog = catalog,
        runtime = WorkoutLoggerRuntime(TimeProvider { MONDAY }, elapsedNow),
        settings = WorkoutLoggerSettings(
            FakeWeightUnitRepository(WeightUnit.KG),
            FakeGuidedWorkoutPreferenceRepository(guided)
        )
    )

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
        loadKind: LoadKind = LoadKind.EXTERNAL
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
                        loadKind = loadKind
                    )
                }
            )
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

    private class FakeWorkoutSessionRepository : WorkoutSessionRepository {
        private val sessions = mutableListOf<WorkoutSession>()
        private val openSession = MutableStateFlow<WorkoutSession?>(null)

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

    private companion object {
        /** Epoch millis whose `dayOfWeek` is MONDAY, matching a plan's first day. */
        const val MONDAY = 4L * 24L * 60L * 60L * 1000L
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
