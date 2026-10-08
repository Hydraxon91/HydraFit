package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ArmMuscleCoverage
import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlanBuilderActions
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.ProgressWeightsUseCase
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SubstituteExerciseUseCase
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.MuscleInvolvement
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.routine.ConvertPlanToTemplateUseCase
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.SaveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.schedule.ActivateRoutineUseCase
import com.hydrafit.app.core.domain.schedule.CreateTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.PreviewWorkoutScheduleUseCase
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SplitBuilderViewModelTest {

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
    fun buildsPlanOnlyFromSelectedEquipment() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        val plan = viewModel.state.value.plan
        assertEquals(4, plan?.days?.size)
        val ids = plan!!.days.flatMap { day -> day.exercises.map { it.exerciseId } }.toSet()
        assertEquals(setOf("goblet-squat"), ids)
        assertEquals("Goblet Squat", viewModel.state.value.exerciseNames["goblet-squat"])
    }

    @Test
    fun changingDaysPerWeekRegeneratesThePlan() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        viewModel.onDaysPerWeekSelected(5)
        advanceUntilIdle()

        val focuses = viewModel.state.value.plan!!.days.map { it.focus }
        assertEquals(5, focuses.size)
        assertEquals(SplitFocus.PUSH, focuses.first())
        assertEquals(SplitFocus.PULL, focuses[1])
        assertEquals(SplitFocus.LEGS, focuses[2])
    }

    @Test
    fun producesDaysWithNoExercisesWhenNoEquipmentIsSelected() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = emptySet())
        advanceUntilIdle()

        val days = viewModel.state.value.plan!!.days
        assertEquals(4, days.size)
        assertTrue(days.all { it.exercises.isEmpty() })
    }

    @Test
    fun reportsErrorInsteadOfCrashingWhenTheEngineFails() = runTest(dispatcher) {
        val equipment = FakeEquipmentSelectionRepository(emptySet())
        val preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        val workoutLog = FakeWorkoutLogRepository()
        val sources = FakeWorkoutPlanSourcesRepository(equipment, preference, workoutLog)
        val viewModel = SplitBuilderViewModel(
            observeWorkoutPlanInputs = observeInputs(sources = sources),
            generateWeeklySplit = GenerateWeeklySplitUseCase(
                WorkoutPlannerEngineProvider { throw IllegalStateException("engine boom") }
            ),
            planBuilderActions = planBuilderActions(
                history = FakePlanHistoryRepository(),
                catalog = FakeExerciseCatalog()
            ),
            planHistory = EmptyPlanHistoryRepository,
            exerciseCatalog = FakeExerciseCatalog(),
            enginePreference = preference
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasError)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.plan)
    }

    @Test
    fun flagsTransientErrorsSoTheUserCanRetry() = runTest(dispatcher) {
        val equipment = FakeEquipmentSelectionRepository(emptySet())
        val preference = FakeEnginePreferenceRepository(PlannerEngineId.GEMINI_API)
        val workoutLog = FakeWorkoutLogRepository()
        val viewModel = SplitBuilderViewModel(
            observeWorkoutPlanInputs = observeInputs(
                sources = FakeWorkoutPlanSourcesRepository(equipment, preference, workoutLog)
            ),
            generateWeeklySplit = GenerateWeeklySplitUseCase(
                WorkoutPlannerEngineProvider {
                    throw PlanGenerationException(transient = true, message = "503")
                }
            ),
            planBuilderActions = planBuilderActions(
                history = FakePlanHistoryRepository(),
                catalog = FakeExerciseCatalog()
            ),
            planHistory = EmptyPlanHistoryRepository,
            exerciseCatalog = FakeExerciseCatalog(),
            enginePreference = preference
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasError)
        assertTrue(viewModel.state.value.isTransientError)
    }

    @Test
    fun flagsWhenTheRequestedEngineFellBack() = runTest(dispatcher) {
        val catalog = FakeExerciseCatalog()
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(catalog),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.LOCAL_LLM)
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PlannerEngineId.LOCAL_LLM, state.requestedEngine)
        assertEquals(PlannerEngineId.DETERMINISTIC, state.plan?.engine)
        assertTrue(state.usedFallbackEngine)
    }

    @Test
    fun startingThePlanOpensAScheduleDialogWithOneDatePerWorkout() = runTest(dispatcher) {
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(FakeExerciseCatalog()),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        )
        advanceUntilIdle()
        val plan = assertNotNull(viewModel.state.value.plan)

        viewModel.onScheduleRequested()

        val dialog = assertNotNull(viewModel.state.value.scheduleDialog)
        assertEquals(plan.days.size, dialog.preview.size)
        assertFalse(dialog.replaceActive)
    }

    @Test
    fun dismissingTheScheduleDialogLeavesThePlanUntouched() = runTest(dispatcher) {
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(FakeExerciseCatalog()),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        )
        advanceUntilIdle()
        viewModel.onScheduleRequested()
        assertNotNull(viewModel.state.value.scheduleDialog)

        viewModel.onScheduleDismissed()

        assertNull(viewModel.state.value.scheduleDialog)
        assertFalse(viewModel.state.value.isPlanAccepted)
    }

    @Test
    fun savingThePlanAsARoutineDoesNotAcceptIt() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(FakeExerciseCatalog()),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC),
            planHistory = history
        )
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.plan)

        viewModel.onSaveAsRoutine("Test routine")
        advanceUntilIdle()

        assertNull(history.latest())
        assertFalse(viewModel.state.value.isPlanAccepted)
        // Saving signals the confirmation; it is cleared once the screen has shown it.
        assertTrue(viewModel.state.value.routineSaved)
        viewModel.onRoutineSavedShown()
        assertFalse(viewModel.state.value.routineSaved)
    }

    @Test
    fun startingAGeneratedPlanUsesOneWeekdayPerWorkout() = runTest(dispatcher) {
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(FakeExerciseCatalog()),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        )
        advanceUntilIdle()
        val plan = assertNotNull(viewModel.state.value.plan)

        viewModel.onScheduleRequested()

        val dialog = assertNotNull(viewModel.state.value.scheduleDialog)
        assertEquals(plan.days.size, dialog.weekdays.size)
        assertEquals(plan.days.size, dialog.preview.size)
    }

    @Test
    fun aGeneratedPlanCannotDropToFewerWeekdays() = runTest(dispatcher) {
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(FakeExerciseCatalog()),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        )
        advanceUntilIdle()
        viewModel.onScheduleRequested()
        val dialog = assertNotNull(viewModel.state.value.scheduleDialog)

        viewModel.onScheduleWeekdayToggled(dialog.weekdays.first())
        viewModel.onConfirmSchedule()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.scheduleDialog)
        assertFalse(viewModel.state.value.isPlanAccepted)
    }

    @Test
    fun loadsDaysPerWeekFromPreference() = runTest(dispatcher) {
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            preference = FakeEnginePreferenceRepository(
                PlannerEngineId.DETERMINISTIC,
                storedDaysPerWeek = 5
            )
        )
        advanceUntilIdle()

        assertEquals(5, viewModel.state.value.daysPerWeek)
        assertEquals(5, viewModel.state.value.plan?.days?.size)
    }

    @Test
    fun persistsDaysPerWeekWhenChanged() = runTest(dispatcher) {
        val preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            preference = preference
        )
        advanceUntilIdle()

        viewModel.onDaysPerWeekSelected(3)
        advanceUntilIdle()

        assertEquals(3, preference.storedDaysPerWeek)
    }

    @Test
    fun appliesSelectedSetCountToThePlan() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        viewModel.onSetsPerExerciseChanged(5)
        advanceUntilIdle()

        val sets = viewModel.state.value.plan!!.days
            .flatMap { it.exercises }
            .map { it.sets }
        assertTrue(sets.isNotEmpty())
        assertTrue(sets.all { it == 5 })
    }

    @Test
    fun regeneratesWhenEquipmentChanges() = runTest(dispatcher) {
        val equipment = FakeEquipmentSelectionRepository(emptySet())
        val viewModel = viewModel(availableEquipment = emptySet(), equipmentRepository = equipment)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.plan!!.days.all { it.exercises.isEmpty() })

        equipment.setSelected(setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        val ids = viewModel.state.value.plan!!.days
            .flatMap { day -> day.exercises.map { it.exerciseId } }
            .toSet()
        assertEquals(setOf("goblet-squat"), ids)
    }

    @Test
    fun regeneratesWhenEnginePreferenceChanges() = runTest(dispatcher) {
        val preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            preference = preference
        )
        advanceUntilIdle()

        preference.setEngine(PlannerEngineId.LOCAL_LLM)
        advanceUntilIdle()

        assertEquals(PlannerEngineId.LOCAL_LLM, viewModel.state.value.requestedEngine)
        assertTrue(viewModel.state.value.usedFallbackEngine)
    }

    @Test
    fun regeneratesWhenLoggedSetsChange() = runTest(dispatcher) {
        val workoutLog = FakeWorkoutLogRepository()
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            workoutLogRepository = workoutLog
        )
        advanceUntilIdle()

        val initialSets = viewModel.state.value.plan!!.days
            .flatMap { it.exercises }
            .filter { it.exerciseId == "goblet-squat" }
            .map { it.sets }
        assertTrue(initialSets.isNotEmpty())
        assertTrue(initialSets.all { it == 3 })

        workoutLog.setLoggedSets(
            List(12) {
                LoggedSet(
                    timestampMillis = 0L,
                    targets = listOf(
                        MuscleTarget(MuscleGroup.QUADS, MuscleInvolvement.PRIMARY.volumeWeight)
                    ),
                    isWarmup = false
                )
            }
        )
        advanceUntilIdle()

        val fatiguedSets = viewModel.state.value.plan!!.days
            .flatMap { it.exercises }
            .filter { it.exerciseId == "goblet-squat" }
            .map { it.sets }
        assertTrue(fatiguedSets.isNotEmpty())
        assertTrue(fatiguedSets.all { it == 2 })
    }

    @Test
    fun acceptingThePlanPersistsIt() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            planHistory = history
        )
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isPlanAccepted)

        viewModel.onAcceptPlan()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isPlanAccepted)
        val accepted = requireNotNull(history.latest())
        assertEquals(viewModel.state.value.plan?.engine, accepted.engine)
        assertEquals(viewModel.state.value.plan?.days?.size, accepted.days.size)
    }

    @Test
    fun acceptanceResetsWhenThePlanRegenerates() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()
        viewModel.onAcceptPlan()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isPlanAccepted)

        viewModel.onSetsPerExerciseChanged(5)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isPlanAccepted)
    }

    @Test
    fun doesNotFlagFallbackWhenRequestedEngineIsUsed() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.usedFallbackEngine)
    }

    @Test
    fun flagsAnInvalidResponseWhenGeminiFallsBack() = runTest(dispatcher) {
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(FakeExerciseCatalog()),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.GEMINI_API)
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.usedFallbackEngine)
        assertEquals(PlanFailureReason.INVALID_RESPONSE, viewModel.state.value.fallbackReason)
    }

    @Test
    fun doesNotFlagAnInvalidResponseForAnOnDeviceFallback() = runTest(dispatcher) {
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(FakeExerciseCatalog()),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.LOCAL_LLM)
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.usedFallbackEngine)
        assertNull(viewModel.state.value.fallbackReason)
    }

    @Test
    fun clearsThePreviousFallbackPlanWhileRegenerating() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val engine = object : WorkoutPlannerEngine {
            override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

            override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
                calls++
                if (calls > 1) gate.await()
                return DeterministicWorkoutPlannerEngine(FakeExerciseCatalog())
                    .generatePlan(request)
            }
        }
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = engine,
            preference = FakeEnginePreferenceRepository(PlannerEngineId.LOCAL_LLM)
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.usedFallbackEngine)

        viewModel.onSetsPerExerciseChanged(4)
        runCurrent()

        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.plan)
        assertFalse(viewModel.state.value.usedFallbackEngine)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun showsTheAcceptedPlanOnLaunchInsteadOfGeneratingADraft() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(acceptedPlan())
        var generated = 0
        val engine = object : WorkoutPlannerEngine {
            override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

            override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
                generated++
                return DeterministicWorkoutPlannerEngine(FakeExerciseCatalog())
                    .generatePlan(request)
            }
        }

        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = engine,
            planHistory = history
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isPlanAccepted)
        assertEquals(listOf(acceptedPlan().days.size), listOf(state.plan?.days?.size))
        assertEquals(0, generated)
        assertEquals(1, state.history.size)
    }

    @Test
    fun regeneratingAfterAnAcceptedPlanProducesADraft() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(acceptedPlan())
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            planHistory = history
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isPlanAccepted)

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isPlanAccepted)
        assertTrue(viewModel.state.value.plan != null)
    }

    @Test
    fun appliesTheAccessorySetCountToAccessorySlots() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        viewModel.onAccessorySetsPerExerciseChanged(6)
        advanceUntilIdle()

        // The fake catalog only has compound squat variants, so the picker value still reaches the
        // plan for compound slots; this verifies the second picker is wired end to end.
        assertEquals(6, viewModel.state.value.accessorySetsPerExercise)
        val sets = viewModel.state.value.plan!!.days
            .flatMap { it.exercises }
            .map { it.sets }
        assertTrue(sets.all { it == 3 })
    }

    @Test
    fun disablesRegenerateWhenTheDeterministicInputsAreUnchanged() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canRegenerate)
    }

    @Test
    fun relocksRegenerateAfterAnInputChangeRegenerates() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        viewModel.onSetsPerExerciseChanged(5)
        advanceUntilIdle()

        // The input change regenerated the plan, which now matches the inputs again.
        assertFalse(viewModel.state.value.canRegenerate)
    }

    @Test
    fun enablesRegenerateWhileShowingAnAcceptedPlan() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(acceptedPlan())
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            planHistory = history
        )
        advanceUntilIdle()

        // The shown accepted plan came from a past generation, so a fresh draft is possible.
        assertTrue(viewModel.state.value.canRegenerate)
    }

    @Test
    fun locksRegenerateAfterProducingADraftFromAnAcceptedPlan() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(acceptedPlan())
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            planHistory = history
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canRegenerate)
    }

    @Test
    fun keepsRegenerateEnabledForNonDeterministicEngines() = runTest(dispatcher) {
        val catalog = FakeExerciseCatalog()
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            engine = DeterministicWorkoutPlannerEngine(catalog),
            preference = FakeEnginePreferenceRepository(PlannerEngineId.LOCAL_LLM)
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canRegenerate)
    }

    @Test
    fun deletesAPlanFromHistory() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        val viewModel = viewModel(availableEquipment = emptySet(), planHistory = history)
        val plan = acceptedPlan().copy(id = 5L)
        history.accept(plan)
        advanceUntilIdle()

        viewModel.onDeletePlan(plan)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.history.isEmpty())
    }

    @Test
    fun swapReplacesTheExerciseAtPosition() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(acceptedPlan().copy(id = 1L))
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BARBELL),
            planHistory = history
        )
        advanceUntilIdle()

        viewModel.onSwapRequested(0, 0)
        advanceUntilIdle()
        assertEquals(
            listOf("back-squat"),
            viewModel.state.value.swapCandidates.map { it.exerciseId }
        )

        viewModel.onSwapCandidateSelected("back-squat")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.swapDialogOpen)
        assertEquals("back-squat", state.plan!!.days.single().exercises.single().exerciseId)
        assertEquals("Back Squat", state.exerciseNames["back-squat"])
        assertEquals(
            "back-squat",
            history.latest()!!.days.single().exercises.single().exerciseId
        )
    }

    @Test
    fun confirmedSwapInvalidatesTheVolumeExplanation() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(
            acceptedPlan().copy(
                id = 1L,
                armCoverage = listOf(
                    ArmMuscleCoverage(
                        muscle = MuscleGroup.BICEPS,
                        targetSets = 4,
                        isTargetEnforced = true,
                        directIsolationSets = 4,
                        estimatedOtherInvolvementCredits = 1.0,
                        unmetReason = null
                    )
                ),
                volumeAttribution = PlanAttribution.DETERMINISTIC
            )
        )
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BARBELL),
            planHistory = history
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.volumeExplanationInvalidated)
        assertEquals(1, viewModel.state.value.plan!!.armCoverage.size)

        viewModel.onSwapRequested(0, 0)
        advanceUntilIdle()
        viewModel.onSwapCandidateSelected("back-squat")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.volumeExplanationInvalidated)
        assertTrue(state.plan!!.armCoverage.isEmpty())
    }

    @Test
    fun swapShowsErrorWhenNoCandidates() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(acceptedPlan().copy(id = 1L))
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            planHistory = history
        )
        advanceUntilIdle()

        viewModel.onSwapRequested(0, 0)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.swapDialogOpen)
        assertTrue(state.swapCandidates.isEmpty())
    }

    @Test
    fun swapLeavesThePlanIntactWhenDialogDismissed() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository()
        history.accept(acceptedPlan().copy(id = 1L))
        val viewModel = viewModel(
            availableEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BARBELL),
            planHistory = history
        )
        advanceUntilIdle()

        viewModel.onSwapRequested(0, 0)
        advanceUntilIdle()
        viewModel.onSwapDialogDismissed()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.swapDialogOpen)
        assertEquals("goblet-squat", state.plan!!.days.single().exercises.single().exerciseId)
        assertEquals(
            "goblet-squat",
            history.latest()!!.days.single().exercises.single().exerciseId
        )
    }

    private fun acceptedPlan() = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PUSH,
                exercises = listOf(
                    AcceptedExercise(
                        exerciseId = "goblet-squat",
                        sets = 3,
                        reps = 8,
                        name = "Goblet Squat",
                        movementPattern = MovementPattern.SQUAT
                    )
                )
            )
        )
    )

    @Test
    fun exclusionLeavingNoEligibleWorkShowsAnActionableError() = runTest(dispatcher) {
        val emptyEngine = object : WorkoutPlannerEngine {
            override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC
            override suspend fun generatePlan(request: PlanRequest) =
                WeeklyPlan(PlannerEngineId.DETERMINISTIC, emptyList())
        }
        val history = FakePlanHistoryRepository()
        val sources = FakeWorkoutPlanSourcesRepository(
            FakeEquipmentSelectionRepository(emptySet()),
            FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC),
            FakeWorkoutLogRepository(),
            exclusions = listOf(ExerciseExclusion("back-squat"))
        )
        val viewModel = SplitBuilderViewModel(
            observeWorkoutPlanInputs = observeInputs(sources = sources),
            generateWeeklySplit = GenerateWeeklySplitUseCase(
                WorkoutPlannerEngineProvider { emptyEngine }
            ),
            planBuilderActions = planBuilderActions(
                history = history,
                catalog = FakeExerciseCatalog()
            ),
            planHistory = history,
            exerciseCatalog = FakeExerciseCatalog(),
            enginePreference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasError)
        assertEquals(
            PlanFailureReason.NO_ELIGIBLE_EXERCISES,
            viewModel.state.value.failureReason
        )
    }

    private fun viewModel(
        availableEquipment: Set<EquipmentTag>,
        engine: WorkoutPlannerEngine? = null,
        preference: FakeEnginePreferenceRepository =
            FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC),
        equipmentRepository: EquipmentSelectionRepository =
            FakeEquipmentSelectionRepository(availableEquipment),
        workoutLogRepository: FakeWorkoutLogRepository = FakeWorkoutLogRepository(),
        planHistory: FakePlanHistoryRepository = FakePlanHistoryRepository()
    ): SplitBuilderViewModel {
        val catalog = FakeExerciseCatalog()
        val sources = FakeWorkoutPlanSourcesRepository(
            equipment = equipmentRepository,
            preference = preference,
            workoutLog = workoutLogRepository
        )
        return SplitBuilderViewModel(
            observeWorkoutPlanInputs = observeInputs(sources = sources),
            generateWeeklySplit = GenerateWeeklySplitUseCase(
                WorkoutPlannerEngineProvider {
                    engine ?: DeterministicWorkoutPlannerEngine(catalog)
                }
            ),
            planBuilderActions = planBuilderActions(history = planHistory, catalog = catalog),
            planHistory = planHistory,
            exerciseCatalog = catalog,
            enginePreference = preference
        )
    }

    private fun planBuilderActions(
        history: PlanHistoryRepository,
        catalog: ExerciseCatalog
    ): PlanBuilderActions {
        val time = TimeProvider { 0L }
        val preview = PreviewWorkoutScheduleUseCase()
        val scheduleRepository = object : WorkoutScheduleRepository {
            override fun observeScheduleState(): Flow<WorkoutScheduleState> =
                flowOf(WorkoutScheduleState())

            override fun observeActiveActivation(): Flow<TrainingActivation?> = flowOf(null)

            override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
                flowOf(emptyList())

            override suspend fun scheduleState(): WorkoutScheduleState = WorkoutScheduleState()

            override suspend fun setScheduleState(state: WorkoutScheduleState) = Unit

            override suspend fun activeActivation(): TrainingActivation? = null

            override suspend fun getActivation(id: Long): TrainingActivation? = null

            override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> =
                emptyList()

            override suspend fun getOccurrence(id: Long): WorkoutOccurrence? = null

            override suspend fun acceptAndActivate(
                acceptedPlan: AcceptedPlan?,
                activation: TrainingActivation,
                scheduledEpochDays: List<Long?>,
                replaceActive: Boolean
            ): Long = 1L

            override suspend fun resolveOccurrence(
                occurrenceId: Long,
                expectedRevision: Int,
                status: com.hydrafit.app.core.domain.schedule.OccurrenceStatus,
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
        val routineRepository = object : RoutineTemplateRepository {
            private var stored: RoutineTemplate? = null

            override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(listOfNotNull(stored))

            override suspend fun get(id: Long): RoutineTemplate? = stored?.takeIf { it.id == id }

            override suspend fun save(template: RoutineTemplate): Long {
                val id = if (template.id == 0L) 1L else template.id
                stored = template.copy(id = id)
                return id
            }

            override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit

            override suspend fun isReferencedByActivation(id: Long): Boolean = false

            override suspend fun delete(id: Long) {
                stored = null
            }
        }
        return PlanBuilderActions(
            acceptWeeklyPlan = AcceptWeeklyPlanUseCase(history, catalog, time),
            substituteExercise = SubstituteExerciseUseCase(
                catalog,
                history,
                DeterministicWorkoutPlannerEngine(catalog)
            ),
            convertPlanToTemplate = ConvertPlanToTemplateUseCase(),
            saveRoutineTemplate = SaveRoutineTemplateUseCase(routineRepository, time),
            activateRoutine = ActivateRoutineUseCase(
                catalog,
                CreateTrainingActivationUseCase(scheduleRepository, preview, time)
            ),
            previewWorkoutSchedule = preview,
            scheduleRepository = scheduleRepository,
            timeProvider = time
        )
    }

    private fun observeInputs(
        sources: WorkoutPlanSourcesRepository,
        planHistoryRepository: PlanHistoryRepository = EmptyPlanHistoryRepository
    ) = ObserveWorkoutPlanInputsUseCase(
        sources = sources,
        calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
        timeProvider = TimeProvider { 0L },
        planHistoryRepository = planHistoryRepository,
        buildPlannerLoadInputs = BuildPlannerLoadInputsUseCase(
            catalog = FakeExerciseCatalog(),
            buildRecentWeights = BuildRecentWeightsUseCase(),
            progressWeights = ProgressWeightsUseCase()
        ),
        periodization = PeriodizationConfig()
    )

    private class FakePlanHistoryRepository : PlanHistoryRepository {
        private val state = MutableStateFlow<AcceptedPlan?>(null)

        override fun observeLatest(): Flow<AcceptedPlan?> = state.asStateFlow()

        override fun observeHistory(): Flow<List<AcceptedPlan>> =
            state.asStateFlow().map { listOfNotNull(it) }

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
        ) {
            val plan = state.value ?: return
            if (plan.id != planId) return
            state.value = plan.copy(
                days = plan.days.map { day ->
                    if (day.dayIndex != dayIndex) {
                        day
                    } else {
                        day.copy(
                            exercises = day.exercises.mapIndexed { index, exercise ->
                                if (index != position) {
                                    exercise
                                } else {
                                    exercise.copy(
                                        exerciseId = newExerciseId,
                                        name = newExerciseName,
                                        suggestedWeightKg = newWeightKg,
                                        loadCapability = newLoadCapability,
                                        loadKind = newLoadKind
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }

        override suspend fun delete(planId: Long) {
            if (state.value?.id == planId) state.value = null
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private class FakeWorkoutPlanSourcesRepository(
        private val equipment: EquipmentSelectionRepository,
        private val preference: EnginePreferenceRepository,
        private val workoutLog: WorkoutLogRepository,
        private val exclusions: List<ExerciseExclusion> = emptyList()
    ) : WorkoutPlanSourcesRepository {
        override fun observe(): Flow<WorkoutPlanSources> = combine(
            equipment.selectedFlow(),
            preference.engineFlow(),
            preference.daysPerWeekFlow(),
            workoutLog.loggedSetsFlow()
        ) { availableEquipment, selectedEngine, daysPerWeek, loggedSets ->
            WorkoutPlanSources(
                availableEquipment = availableEquipment,
                selectedEngine = selectedEngine,
                daysPerWeek = daysPerWeek,
                loggedSets = loggedSets,
                exerciseExclusions = exclusions
            )
        }
    }

    private object EmptyPlanHistoryRepository : PlanHistoryRepository {
        private val state = MutableStateFlow<AcceptedPlan?>(null)

        override fun observeLatest(): Flow<AcceptedPlan?> = state.asStateFlow()

        override fun observeHistory(): Flow<List<AcceptedPlan>> = flowOf(emptyList())

        override suspend fun latest(): AcceptedPlan? = null

        override suspend fun accept(plan: AcceptedPlan) = Unit

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

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }

    private class FakeExerciseCatalog : ExerciseCatalog {
        private val exercises = listOf(
            Exercise(
                id = "goblet-squat",
                name = "Goblet Squat",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS),
                movementPattern = MovementPattern.SQUAT
            ),
            Exercise(
                id = "back-squat",
                name = "Back Squat",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS),
                movementPattern = MovementPattern.SQUAT
            )
        )

        override suspend fun all(): List<Exercise> = exercises
    }

    private class FakeEnginePreferenceRepository(
        private val engine: PlannerEngineId,
        var storedDaysPerWeek: Int = 4
    ) : EnginePreferenceRepository {
        private val engineState = MutableStateFlow(engine)
        private val daysState = MutableStateFlow(storedDaysPerWeek)

        override suspend fun selectedEngine(): PlannerEngineId = engineState.value

        override fun engineFlow(): Flow<PlannerEngineId> = engineState.asStateFlow()

        override suspend fun setEngine(engine: PlannerEngineId) {
            engineState.value = engine
        }

        override suspend fun selectedDaysPerWeek(): Int = storedDaysPerWeek

        override fun daysPerWeekFlow(): Flow<Int> = daysState.asStateFlow()

        override suspend fun setDaysPerWeek(daysPerWeek: Int) {
            storedDaysPerWeek = daysPerWeek
            daysState.value = daysPerWeek
        }

        override suspend fun isWorkoutDataSharingEnabled(): Boolean = false

        override fun workoutDataSharingFlow(): Flow<Boolean> = flowOf(false)

        override suspend fun setWorkoutDataSharingEnabled(enabled: Boolean) = Unit
    }

    private class FakeEquipmentSelectionRepository(selected: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        private val state = MutableStateFlow(selected)

        override suspend fun selected(): Set<EquipmentTag> = state.value

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = state.asStateFlow()

        override suspend fun setSelected(tags: Set<EquipmentTag>) {
            state.value = tags
        }
    }

    private class FakeWorkoutLogRepository : WorkoutLogRepository {
        private val loggedState = MutableStateFlow(emptyList<LoggedSet>())

        fun setLoggedSets(sets: List<LoggedSet>) {
            loggedState.value = sets
        }

        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

        override suspend fun loggedSets(): List<LoggedSet> = loggedState.value

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = loggedState.asStateFlow()

        override suspend fun clear() = Unit
    }
}
