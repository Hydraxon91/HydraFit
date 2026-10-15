package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The occurrence-records use case combines an occurrence's distinct exercises with each one's
 * chronological record events. These tests drive the real [ExerciseProgressCalculator] through a
 * fake repository, so they exercise the actual improvement semantics rather than a stubbed result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOccurrenceRecordsUseCaseTest {

    @Test
    fun improvementAgainstAnEarlierDayOutsideTheChartRangeIsReported() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        repository.sets += set(id = 1, weight = 60.0, time = DAY, occurrenceId = null)
        repository.sets += set(id = 2, weight = 70.0, time = 3 * DAY, occurrenceId = OCCURRENCE)
        val emissions = collect(useCase, repository)

        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()

        val improvements = emissions.last()
        val events = improvements.map { it.event.record.key.type }
        assertEquals(
            listOf(ProgressRecordType.LOAD_AT_REPS, ProgressRecordType.ESTIMATED_ONE_REP_MAX),
            events
        )
        assertEquals(70.0, improvements.first().event.record.value)
        assertEquals(2L, improvements.first().event.setId)
        assertEquals(70.0, improvements.first().set?.weightKg)
    }

    @Test
    fun firstObservationEstablishesNoImprovement() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        repository.sets += set(id = 1, weight = 70.0, time = 3 * DAY, occurrenceId = OCCURRENCE)
        val emissions = collect(useCase, repository)

        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()

        assertEquals(listOf(emptyList()), emissions)
    }

    @Test
    fun tiesAreNotNewImprovements() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        repository.sets += set(id = 1, weight = 70.0, time = DAY, occurrenceId = null)
        repository.sets += set(id = 2, weight = 70.0, time = 3 * DAY, occurrenceId = OCCURRENCE)
        val emissions = collect(useCase, repository)

        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()

        assertTrue(emissions.last().isEmpty())
    }

    @Test
    fun severalExercisesAcrossSessionsAreKeyedByStableExerciseId() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        repository.sets +=
            set(id = 1, exerciseId = "bench", weight = 50.0, time = DAY, occurrenceId = null)
        repository.sets +=
            set(
                id = 2,
                exerciseId = "bench",
                weight = 60.0,
                time = 3 * DAY,
                occurrenceId = OCCURRENCE
            )
        repository.sets +=
            set(
                id = 3,
                exerciseId = "bench",
                weight = 65.0,
                time = 3 * DAY + 1,
                occurrenceId = OCCURRENCE
            )
        repository.sets +=
            set(id = 4, exerciseId = "squat", weight = 80.0, time = DAY, occurrenceId = null)
        repository.sets +=
            set(
                id = 5,
                exerciseId = "squat",
                weight = 90.0,
                time = 3 * DAY,
                occurrenceId = OCCURRENCE
            )
        val emissions = collect(useCase, repository)

        repository.ids.emit(listOf("bench", "squat"))
        advanceUntilIdle()

        val result = emissions.last()
        assertEquals(
            listOf("bench" to 4, "squat" to 2),
            result.groupBy { it.exerciseId }.mapValues { it.value.size }.toList()
        )
        assertEquals(
            listOf(2L, 3L),
            result.filter {
                it.exerciseId == "bench" &&
                    it.event.record.key.type == ProgressRecordType.LOAD_AT_REPS
            }
                .map { it.event.setId }
        )
    }

    @Test
    fun correctingAnEarlierSetRetractsAndRestoringBringsTheImprovementBack() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        val earlier = set(id = 1, weight = 60.0, time = DAY, occurrenceId = null)
        repository.sets += earlier
        repository.sets += set(id = 2, weight = 70.0, time = 3 * DAY, occurrenceId = OCCURRENCE)
        val emissions = collect(useCase, repository)
        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()
        assertTrue(emissions.last().isNotEmpty())

        // Correct the earlier set above the occurrence's, so nothing improves; the event retracts.
        repository.sets.remove(earlier)
        repository.sets += earlier.copy(weightKg = 80.0)
        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()
        assertTrue(emissions.last().isEmpty())

        // Restore the earlier baseline and the improvement returns.
        repository.sets.removeAll { it.id == earlier.id }
        repository.sets += earlier
        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()
        assertTrue(emissions.last().isNotEmpty())
    }

    @Test
    fun deletingTheContributingSetRetractsAndRestoringItBringsItBack() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        repository.sets += set(id = 1, weight = 60.0, time = DAY, occurrenceId = null)
        val contributor = set(id = 2, weight = 70.0, time = 3 * DAY, occurrenceId = OCCURRENCE)
        repository.sets += contributor
        val emissions = collect(useCase, repository)
        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()
        assertTrue(emissions.last().isNotEmpty())

        repository.sets.remove(contributor)
        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()
        assertTrue(emissions.last().isEmpty())

        repository.sets += contributor
        repository.ids.emit(listOf("squat"))
        advanceUntilIdle()
        assertTrue(emissions.last().isNotEmpty())
    }

    @Test
    fun emptyOccurrenceEmitsAnEmptyList() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        val emissions = collect(useCase, repository)

        repository.ids.emit(emptyList())
        advanceUntilIdle()

        assertEquals(listOf(emptyList()), emissions)
    }

    @Test
    fun aReadFailurePropagates() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        repository.sets += set(id = 1, weight = 70.0, time = 3 * DAY, occurrenceId = OCCURRENCE)
        repository.failProgress = true
        repository.ids.emit(listOf("squat"))

        val failure = try {
            useCase(OCCURRENCE, nowMillis = 3 * DAY + 1, utcOffsetMillis = 0).first()
            null
        } catch (error: IllegalStateException) {
            error
        }
        assertEquals("read failed", failure?.message)
    }

    @Test
    fun cancellingTheCollectorCancelsTheReads() = runTest {
        val repository = FakeExerciseProgressRepository()
        val useCase = ObserveOccurrenceRecordsUseCase(repository)
        val emissions = mutableListOf<List<OccurrenceRecordImprovement>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(OCCURRENCE, nowMillis = 3 * DAY + 1, utcOffsetMillis = 0).collect {
                emissions +=
                    it
            }
        }
        repository.ids.emit(emptyList())
        advanceUntilIdle()

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(repository.idsCancelled)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.collect(
        useCase: ObserveOccurrenceRecordsUseCase,
        repository: FakeExerciseProgressRepository
    ): List<List<OccurrenceRecordImprovement>> {
        val emissions = mutableListOf<List<OccurrenceRecordImprovement>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(OCCURRENCE, nowMillis = 3 * DAY + 1, utcOffsetMillis = 0).collect {
                emissions +=
                    it
            }
        }
        advanceUntilIdle()
        return emissions
    }

    private fun set(
        id: Long,
        exerciseId: String = "squat",
        reps: Int = 10,
        weight: Double?,
        time: Long,
        occurrenceId: Long?
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        reps = reps,
        weightKg = weight,
        loadKind = LoadKind.EXTERNAL,
        performedAtMillis = time,
        occurrenceId = occurrenceId
    )

    private companion object {
        const val DAY = 86_400_000L
        const val OCCURRENCE = 7L
    }
}

/**
 * A repository whose occurrence-exercise ids re-emit on demand, and whose progress is the real
 * [ExerciseProgressCalculator] result over a mutable set list. Records cancellation of the id flow.
 */
private class FakeExerciseProgressRepository : ExerciseProgressRepository {
    val ids = MutableSharedFlow<List<String>>(replay = 1, extraBufferCapacity = 8)
    val sets = mutableListOf<WorkoutSet>()
    var failProgress = false
    var idsCancelled = false

    override fun observeOccurrenceExerciseIds(occurrenceId: Long): Flow<List<String>> =
        ids.onCompletion { idsCancelled = true }

    override fun observeProgress(request: ExerciseProgressRequest): Flow<ExerciseProgress> = flow {
        if (failProgress) throw IllegalStateException("read failed")
        val calculator = ExerciseProgressCalculator(request)
        sets.filter { it.exerciseId == request.exerciseId }
            .sortedWith(compareBy({ it.performedAtMillis }, { it.id }))
            .forEach(calculator::accept)
        emit(calculator.result())
    }

    override fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage> =
        flowOf(ExerciseHistoryPage(emptyList(), hasMore = false))

    override fun observeSet(exerciseId: String, setId: Long): Flow<WorkoutHistorySet?> = flowOf(
        sets.firstOrNull { it.exerciseId == exerciseId && it.id == setId }?.let { set ->
            WorkoutHistorySet(
                id = set.id,
                exerciseId = set.exerciseId,
                exerciseName = null,
                reps = set.reps,
                weightKg = set.weightKg,
                loadKind = set.loadKind,
                isWarmup = set.isWarmup,
                rir = set.rir,
                performedAtMillis = set.performedAtMillis,
                timingProvenance = set.timingProvenance,
                occurrenceId = set.occurrenceId
            )
        }
    )
}
