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
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
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
            observeAcceptedPlan = ObserveAcceptedPlanUseCase(history),
            exerciseCatalog = FakeExerciseCatalog,
            timeProvider = TimeProvider { MONDAY },
            weightUnitRepository = FakeWeightUnitRepository(WeightUnit.KG)
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
            observeAcceptedPlan = ObserveAcceptedPlanUseCase(history),
            exerciseCatalog = FakeExerciseCatalog,
            timeProvider = TimeProvider { MONDAY },
            weightUnitRepository = FakeWeightUnitRepository(WeightUnit.KG)
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
            observeAcceptedPlan = ObserveAcceptedPlanUseCase(FakePlanHistoryRepository(twoDayPlan)),
            exerciseCatalog = FakeExerciseCatalog,
            timeProvider = TimeProvider { now },
            weightUnitRepository = FakeWeightUnitRepository(WeightUnit.KG)
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

        viewModel.onExerciseSelected("plank")
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

        viewModel.onExerciseSelected("plank")
        viewModel.onRevealWeight()
        viewModel.onRepsChanged("10")
        viewModel.onWeightChanged("10")
        viewModel.log()
        advanceUntilIdle()

        assertEquals(10.0, repository.all().single().weightKg)
    }

    @Test
    fun prefillsARevealedBodyweightWeightFromTheAcceptedPlan() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("plank"), suggestedWeightKg = 42.0)
        )
        advanceUntilIdle()

        viewModel.onExerciseSelected("plank")
        assertFalse(viewModel.state.value.showWeightField)
        assertEquals("42", viewModel.state.value.weightInput)

        viewModel.onRevealWeight()
        assertTrue(viewModel.state.value.showWeightField)
        assertEquals("42", viewModel.state.value.weightInput)
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
            listOf("Back Squat", "Bench Press", "Dumbbell Curl", "Plank"),
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
            listOf("Plank", "Back Squat", "Bench Press", "Dumbbell Curl"),
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
            listOf("Plank", "Back Squat", "Bench Press", "Dumbbell Curl"),
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
            observeAcceptedPlan = ObserveAcceptedPlanUseCase(
                FakePlanHistoryRepository(acceptedPlan(listOf("plank")))
            ),
            exerciseCatalog = FakeExerciseCatalog,
            timeProvider = TimeProvider { now },
            weightUnitRepository = FakeWeightUnitRepository(WeightUnit.KG)
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

        assertEquals(3, repository.all().size)
        assertTrue(repository.all().all { it.performedAtMillis == chosen })
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

        // performedAt desc with ties kept in repository order (performedAt, id asc).
        assertEquals(listOf(1L, 2L, 3L), viewModel.state.value.recentSets.map { it.id })
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
        observeAcceptedPlan = ObserveAcceptedPlanUseCase(history),
        exerciseCatalog = catalog,
        timeProvider = TimeProvider { timeMillis },
        weightUnitRepository = FakeWeightUnitRepository(weightUnit)
    )

    private fun sessionViewModel(
        repository: WorkoutLogRepository,
        sessionRepository: WorkoutSessionRepository,
        now: () -> Long
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logMutations = logMutations(repository, sessionRepository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        observeAcceptedPlan = ObserveAcceptedPlanUseCase(FakePlanHistoryRepository(null)),
        exerciseCatalog = FakeExerciseCatalog,
        timeProvider = TimeProvider { now() },
        weightUnitRepository = FakeWeightUnitRepository(WeightUnit.KG)
    )

    private fun logMutations(
        repository: WorkoutLogRepository,
        sessionRepository: WorkoutSessionRepository
    ) = WorkoutLogMutations(
        logWorkoutSet = LogWorkoutSetUseCase(
            repository = repository,
            startWorkoutSession = StartWorkoutSessionUseCase(sessionRepository),
            endWorkoutSession = EndWorkoutSessionUseCase(sessionRepository),
            observeOpenWorkoutSession = ObserveOpenWorkoutSessionUseCase(sessionRepository),
            config = SessionConfig()
        ),
        deleteWorkoutSet = DeleteWorkoutSetUseCase(repository),
        correctWorkoutSetTime = CorrectWorkoutSetTimeUseCase(
            resegmenter = object : SessionResegmenter {
                override suspend fun resegmentAfterTimeCorrection(
                    setId: Long,
                    performedAtMillis: Long,
                    utcOffsetMillis: Long
                ) {
                    repository.updateSetPerformedAt(setId, performedAtMillis)
                }
            },
            timeProvider = TimeProvider { 0L }
        )
    )

    private fun acceptedPlan(dayZeroExerciseIds: List<String>, suggestedWeightKg: Double? = null) =
        AcceptedPlan(
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
                            suggestedWeightKg = suggestedWeightKg
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
                primaryMuscles = setOf(MuscleGroup.CHEST),
                movementPattern = MovementPattern.HORIZONTAL_PUSH
            ),
            Exercise(
                id = "plank",
                name = "Plank",
                requiredEquipment = emptySet(),
                primaryMuscles = setOf(MuscleGroup.CORE),
                movementPattern = MovementPattern.CORE
            ),
            Exercise(
                id = "dumbbell-curl",
                name = "Dumbbell Curl",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.BICEPS),
                movementPattern = MovementPattern.BICEPS_ISOLATION
            )
        )
    }

    private class FakeWorkoutLogRepository(initial: List<WorkoutSet> = emptyList()) :
        WorkoutLogRepository {
        private val sets = initial.toMutableList()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun add(set: WorkoutSet) {
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

        override suspend fun all(): List<WorkoutSet> = sets.toList()

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

    private companion object {
        /** Epoch millis whose `dayOfWeek` is MONDAY, matching a plan's first day. */
        const val MONDAY = 4L * 24L * 60L * 60L * 1000L
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
