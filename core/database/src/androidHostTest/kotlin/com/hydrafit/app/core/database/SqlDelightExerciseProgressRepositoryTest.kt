package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseProgress
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SqlDelightExerciseProgressRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightExerciseProgressRepository
    private lateinit var writer: SqlDelightWorkoutLogRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightExerciseProgressRepository(database)
        writer = SqlDelightWorkoutLogRepository(database)
        database.exerciseQueries.insertCustom(
            "squat",
            "Squat",
            "BARBELL",
            "SQUAT",
            0L,
            "EXTERNAL",
            null
        )
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun tiedTimestampPagesNeverDuplicateOrOmitAndDoNotTruncateProgress() = runBlocking {
        repeat(600) { writer.add(set(time = 1, weight = 10.0 + it % 10)) }
        val ids = mutableListOf<Long>()
        var request = ExerciseHistoryRequest("squat", pageSize = 17)
        do {
            val page = repository.observeHistory(request).first()
            ids += page.sets.map { it.id }
            val cursor = page.nextCursor
            if (cursor != null) request = request.copy(cursor = cursor)
        } while (page.hasMore)
        assertEquals((600L downTo 1L).toList(), ids)
        val result = repository.observeProgress(progressRequest()).first()
        assertEquals(600, result.days.single().workingSets)
        assertTrue(result.occurrenceEvents.isEmpty())
        val loadRecord = result.records.first { it.key.type == ProgressRecordType.LOAD_AT_REPS }
        assertEquals(60, loadRecord.setIds.size)
    }

    @Test
    fun historyPreservesWarmupsFutureRowsAndMissingCatalogWhileChartsRespectBounds() = runBlocking {
        writer.add(set(time = DAY - 1, weight = 90.0))
        writer.add(set(time = DAY, weight = 60.0))
        writer.add(set(time = 2 * DAY - 1, weight = 70.0))
        writer.add(set(time = 2 * DAY, weight = 80.0).copy(isWarmup = true))
        writer.add(set(time = 3 * DAY, weight = 100.0))
        val request = progressRequest().copy(dayFrom = 1, dayTo = 1, nowMillis = 2 * DAY)
        val progress = repository.observeProgress(request).first()
        assertEquals(2, progress.days.single().workingSets)
        assertEquals(70.0, progress.days.single().bestLoads.single().value)
        val loadRecord = progress.records.first { it.key.type == ProgressRecordType.LOAD_AT_REPS }
        assertEquals(90.0, loadRecord.value)
        val history = repository.observeHistory(ExerciseHistoryRequest("squat")).first()
        assertEquals(5, history.sets.size)
        assertTrue(history.sets.any { it.isWarmup })
        database.exerciseQueries.deleteById("squat")
        val orphaned = repository.observeHistory(ExerciseHistoryRequest("squat")).first()
        assertNull(orphaned.sets.first().exerciseName)
    }

    @Test
    fun correctionsAndReplacementInvalidateLiveProjection() = runBlocking {
        val emissions = Channel<ExerciseProgress>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            repository.observeProgress(progressRequest()).collect { emissions.send(it) }
        }
        suspend fun next() = withTimeout(5_000) { emissions.receive() }
        try {
            assertTrue(next().days.isEmpty())
            writer.add(set(time = 1, weight = 50.0))
            assertEquals(50.0, next().days.single().bestLoads.single().value)
            writer.add(set(time = 2, weight = 100.0))
            assertEquals(100.0, next().days.single().bestLoads.single().value)
            database.workoutLogQueries.updateSetValues(10, 40.0, null, 2)
            assertEquals(50.0, next().days.single().bestLoads.single().value)
            writer.updateSetPerformedAt(1, DAY)
            assertEquals(listOf(0L, 1L), next().days.map { it.localEpochDay })
            writer.delete(1)
            assertEquals(40.0, next().days.single().bestLoads.single().value)
            // Same-count replacement, as used by restore; old derived state must vanish.
            database.transaction {
                database.workoutLogQueries.deleteAllSets()
                database.workoutLogQueries.insertSet(
                    "squat", 5, 20.0, 1, 0, null, null, null, null, null, null, "EXTERNAL"
                )
            }
            assertEquals(20.0, next().days.single().bestLoads.single().value)
        } finally {
            job.cancel()
            job.join()
            emissions.close()
        }
    }

    @Test
    fun effectiveNameChangesRefreshHistoryWithoutChangingRecords() = runBlocking {
        writer.add(set(time = 1, weight = 50.0))
        val before = repository.observeProgress(progressRequest()).first()
        database.exerciseOverrideQueries.upsert("squat", "Renamed", null, null, null, null, null)
        val renamed = repository.observeHistory(ExerciseHistoryRequest("squat")).first()
        assertEquals("Renamed", renamed.sets.single().exerciseName)
        assertEquals(before, repository.observeProgress(progressRequest()).first())
        val empty = repository.observeHistory(ExerciseHistoryRequest("other")).first()
        assertFalse(empty.hasMore)
        assertNull(empty.nextCursor)
    }

    private fun progressRequest() = ExerciseProgressRequest("squat", 0, 2, 2 * DAY)

    private fun set(time: Long, weight: Double) = WorkoutSet(
        exerciseId = "squat",
        reps = 10,
        weightKg = weight,
        performedAtMillis = time
    )

    private companion object {
        const val DAY = 86_400_000L
    }
}
