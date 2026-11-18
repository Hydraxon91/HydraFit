package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.GetExercisePerformanceUseCase
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.WorkoutCluster
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val squat = Exercise(
        id = "back-squat",
        name = "Back Squat",
        requiredEquipment = setOf(EquipmentTag.BARBELL),
        primaryMuscles = setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
        secondaryMuscles = setOf(MuscleGroup.HAMSTRINGS)
    )
    private var catalogExercises = listOf(squat)
    private var sets = listOf<WorkoutSet>()
    private var failLogReads = false

    private val catalog = object : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = catalogExercises
    }

    private val log = object : WorkoutLogRepository {
        override suspend fun all(): List<WorkoutSet> {
            check(!failLogReads) { "read failed" }
            return sets
        }

        override suspend fun add(set: WorkoutSet) = error("unused")

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = error("unused")

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) = error("unused")

        override suspend fun assignSession(setId: Long, sessionId: String) = error("unused")

        override suspend fun delete(id: Long) = error("unused")

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) =
            error("unused")

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets)

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = error("unused")
    }

    private fun viewModel() = ExerciseDetailsViewModel(GetExercisePerformanceUseCase(log), catalog)

    private fun set(id: Long, at: Long, session: String) = WorkoutSet(
        id = id,
        exerciseId = "back-squat",
        reps = 8,
        weightKg = 80.0,
        performedAtMillis = at,
        sessionId = session
    )

    @Test
    fun loadsMusclesAndTheLastSession() = runTest {
        sets = listOf(set(1, 100, "s1"), set(2, 200, "s2"), set(3, 300, "s2"))
        val viewModel = viewModel()

        viewModel.load("back-squat")
        advanceUntilIdle()

        val details = requireNotNull(viewModel.details.value)
        assertEquals(listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), details.primaryMuscles)
        assertEquals(listOf(MuscleGroup.HAMSTRINGS), details.secondaryMuscles)
        assertEquals(listOf(2L, 3L), details.performance.lastSessionSets.map { it.id })
    }

    @Test
    fun anUnknownOrCustomExerciseStillLoadsWithNoMuscles() = runTest {
        catalogExercises = emptyList()
        val viewModel = viewModel()

        viewModel.load("custom-lift")
        advanceUntilIdle()

        val details = requireNotNull(viewModel.details.value)
        assertEquals(emptyList(), details.primaryMuscles)
        assertNull(details.performance.lastPerformedAtMillis)
    }

    @Test
    fun aFailedReadLeavesNoDetails() = runTest {
        failLogReads = true
        val viewModel = viewModel()

        viewModel.load("back-squat")
        advanceUntilIdle()

        assertNull(viewModel.details.value)
    }

    @Test
    fun loadingAnotherExerciseReplacesTheDetails() = runTest {
        catalogExercises = listOf(squat, squat.copy(id = "front-squat", name = "Front Squat"))
        val viewModel = viewModel()

        viewModel.load("back-squat")
        advanceUntilIdle()
        viewModel.load("front-squat")
        assertNull(viewModel.details.value)
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.details.value?.performance?.lastSessionSets)
    }
}
