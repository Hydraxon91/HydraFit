package com.hydrafit.app.core.database

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseProgress
import com.hydrafit.app.core.domain.workout.ExerciseProgressCalculator
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.StationFilter
import com.hydrafit.app.core.domain.workout.WorkoutSet
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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

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
    fun stationFilterSeparatesAttributedFromUnattributedAndAllSets() = runBlocking {
        val stationA = StationAttribution(1L, "Rack A", StationLoadBasis.TOTAL)
        val stationB = StationAttribution(2L, "Rack B", StationLoadBasis.TOTAL)
        writer.add(set(time = 1, weight = 90.0, station = stationA))
        writer.add(set(time = 2, weight = 100.0, station = stationB))
        writer.add(set(time = 3, weight = 110.0))
        writer.add(
            set(
                time = 4,
                weight = 120.0,
                station = StationAttribution(0L, "Merged cable row", StationLoadBasis.TOTAL)
            )
        )

        val all = repository.observeProgress(progressRequest()).first()
        val onlyA = repository.observeProgress(
            progressRequest().copy(stationFilter = StationFilter.Station(1L))
        ).first()
        val unattributed = repository.observeProgress(
            progressRequest().copy(stationFilter = StationFilter.Unattributed)
        ).first()

        assertEquals(4, all.days.single().workingSets)
        assertEquals(1, onlyA.days.single().workingSets)
        assertEquals(1, unattributed.days.single().workingSets)
        assertEquals(
            stationA,
            repository.observeHistory(ExerciseHistoryRequest("squat")).first()
                .sets.first { it.performedAtMillis == 1L }.station
        )
        assertEquals(
            listOf(stationA, stationB),
            repository.observeStationOptions("squat").first()
        )
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
            database.workoutLogQueries.updateSetValues(10, null, 40.0, null, 2)
            assertEquals(50.0, next().days.single().bestLoads.single().value)
            writer.updateSetPerformedAt(1, DAY)
            assertEquals(listOf(0L, 1L), next().days.map { it.localEpochDay })
            writer.delete(1)
            assertEquals(40.0, next().days.single().bestLoads.single().value)
            // Same-count replacement, as used by restore; old derived state must vanish.
            database.transaction {
                database.workoutLogQueries.deleteAllSets()
                database.workoutLogQueries.insertSet(
                    "squat", 5, 20.0, 1, 0, null, null, null, null, null, null, "EXTERNAL", null
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

    @Test
    fun historyContinuationWithTiesAndNegativeTimesKeepsCursorAndOrder() = runBlocking {
        val times = listOf(0L, 0L, -3L, 5L, 5L, 5L, -3L, 0L, 7L, -10L, 5L, -3L, -10L, 2L)
        times.forEachIndexed { index, time -> writer.add(set(time = time, weight = 10.0 + index)) }
        val expected = (1L..times.size.toLong()).sortedWith(
            compareByDescending<Long> { times[(it - 1).toInt()] }.thenByDescending { it }
        )
        val seen = mutableListOf<Long>()
        var request = ExerciseHistoryRequest("squat", pageSize = 2)
        var pages = 0
        while (true) {
            val page = repository.observeHistory(request).first()
            assertTrue(page.sets.size <= 2)
            seen += page.sets.map { it.id }
            pages++
            if (!page.hasMore) {
                assertNull(page.nextCursor)
                break
            }
            val cursor = assertNotNull(page.nextCursor)
            val last = page.sets.last()
            assertEquals(last.exerciseId, cursor.exerciseId)
            assertEquals(last.performedAtMillis, cursor.performedAtMillis)
            assertEquals(last.id, cursor.setId)
            request = request.copy(cursor = cursor)
            check(pages <= 20)
        }
        assertEquals(expected, seen)
    }

    @Test
    fun deepReplayPagesMatchExplicitAscendingReplayWithTiesAndNegatives() = runBlocking {
        val times = List(700) { index -> listOf(-5L, -5L, 0L, 9L)[index % 4] }
        times.forEachIndexed { index, time ->
            writer.add(set(time = time, weight = 10.0 + index % 7))
        }
        val request = ExerciseProgressRequest("squat", dayFrom = -1, dayTo = 1, nowMillis = 10)
        val fromRepository = repository.observeProgress(request).first()
        val calculator = ExerciseProgressCalculator(request)
        (1L..times.size.toLong()).sortedWith(
            compareBy<Long> { times[(it - 1).toInt()] }.thenBy { it }
        ).forEach { id ->
            calculator.accept(
                WorkoutSet(
                    id = id,
                    exerciseId = "squat",
                    reps = 10,
                    weightKg = 10.0 + (id - 1) % 7,
                    loadKind = LoadKind.EXTERNAL,
                    performedAtMillis = times[(id - 1).toInt()]
                )
            )
        }
        assertEquals(calculator.result(), fromRepository)
    }

    @Test
    fun observeSetResolvesOneContributingSetAndTracksCorrectionRenameAndDeletion() = runBlocking {
        writer.add(set(time = DAY, weight = 60.0))
        val historySet = repository
            .observeHistory(ExerciseHistoryRequest("squat"))
            .first()
            .sets
            .single()

        assertEquals(historySet, repository.observeSet("squat", historySet.id).first())
        // The read is exercise-scoped: the same id under another exercise resolves to nothing.
        assertNull(repository.observeSet("other", historySet.id).first())
        assertNull(repository.observeSet("squat", 9_999L).first())

        database.workoutLogQueries.updateSetValues(5, null, 72.5, null, historySet.id)
        assertEquals(72.5, repository.observeSet("squat", historySet.id).first()?.weightKg)

        database.exerciseOverrideQueries.upsert("squat", "Renamed", null, null, null, null, null)
        assertEquals("Renamed", repository.observeSet("squat", historySet.id).first()?.exerciseName)

        writer.delete(historySet.id)
        assertNull(repository.observeSet("squat", historySet.id).first())
    }

    @Test
    fun occurrenceExerciseIdsTrackAddsDistinctSlotsOrderCorrectionsDeletesAndReplacement() =
        runBlocking {
            database.exerciseQueries.insertCustom(
                "bench",
                "Bench",
                "BARBELL",
                "PUSH",
                0L,
                "EXTERNAL",
                null
            )
            suspend fun ids() = repository.observeOccurrenceExerciseIds(OCCURRENCE).first()

            assertEquals(emptyList(), ids())
            // A set logged against another occurrence never appears here.
            writer.add(set(time = 0, weight = 40.0).copy(occurrenceId = OCCURRENCE + 1))
            assertEquals(emptyList(), ids())
            writer.add(
                set(time = 1, weight = 50.0).copy(occurrenceId = OCCURRENCE, exerciseId = "squat")
            )
            assertEquals(listOf("squat"), ids())
            // A repeated slot for the same exercise does not duplicate the id.
            writer.add(
                set(time = 2, weight = 55.0).copy(occurrenceId = OCCURRENCE, exerciseId = "squat")
            )
            assertEquals(listOf("squat"), ids())
            // A second exercise is ordered deterministically.
            writer.add(
                set(time = 3, weight = 30.0).copy(occurrenceId = OCCURRENCE, exerciseId = "bench")
            )
            assertEquals(listOf("bench", "squat"), ids())
            // Deleting each squat slot retracts the id once none remains, keeping the other exercise.
            writer.delete(2)
            writer.delete(3)
            assertEquals(listOf("bench"), ids())
            // A same-count replacement (as used by restore) is reflected directly.
            database.transaction {
                database.workoutLogQueries.deleteAllSets()
                database.workoutLogQueries.insertSet(
                    "squat", 5, 20.0, 1, 0, null, null, null, null, null, null, "EXTERNAL", null
                )
                database.workoutLogQueries.assignOccurrence(
                    OCCURRENCE,
                    null,
                    database.workoutLogQueries.lastInsertedSetId().executeAsOne()
                )
            }
            assertEquals(listOf("squat"), ids())
        }

    @Test
    fun occurrenceExerciseIdsEmitAfterAWrite() = runBlocking {
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            repository.observeOccurrenceExerciseIds(OCCURRENCE).collect { emissions.send(it) }
        }
        try {
            assertEquals(emptyList(), withTimeout(5_000) { emissions.receive() })
            writer.add(
                set(time = 1, weight = 50.0).copy(occurrenceId = OCCURRENCE, exerciseId = "squat")
            )
            assertEquals(listOf("squat"), withTimeout(5_000) { emissions.receive() })
        } finally {
            job.cancel()
            job.join()
            emissions.close()
        }
    }

    @Test
    fun concurrentReplaysNeverHoldTwoTransactionsOnTheSharedConnection() = runBlocking {
        // The occurrence record list combines one replay per exercise, and the chart selector cancels
        // one replay before starting the next, so two replays are routinely in flight together. On a
        // single-connection driver the second transaction is rejected outright, which is the recorded
        // intermittent failure. This asserts the repository serializes them instead.
        database.exerciseQueries.insertCustom(
            "bench",
            "Bench press",
            "BARBELL",
            "PUSH",
            0L,
            "EXTERNAL",
            null
        )
        writer.add(set(time = 1, weight = 50.0))
        writer.add(set(time = 1, weight = 80.0).copy(exerciseId = "bench"))

        val detecting = OverlapDetectingDriver(driver)
        val detectingRepository = SqlDelightExerciseProgressRepository(HydraFitDatabase(detecting))

        // The second reader starts only once the first transaction is already open, so the overlap is
        // forced by ordering rather than by racing two launches and hoping they interleave.
        val squat = async(Dispatchers.Default) {
            detectingRepository.observeProgress(progressRequest()).first()
        }
        detecting.awaitFirstTransaction()
        val bench = async(Dispatchers.Default) {
            detectingRepository
                .observeProgress(ExerciseProgressRequest("bench", 0, 2, 2 * DAY))
                .first()
        }
        val results = withTimeout(30_000) { listOf(squat.await(), bench.await()) }

        assertEquals(0, detecting.overlaps)
        assertEquals(
            listOf(50.0, 80.0),
            results.map { it.days.single().bestLoads.single().value }
        )
    }

    @Test
    fun aCancelledReplayReleasesTheLockBeforeTheNextOneBegins() = runBlocking {
        // The chart selector cancels one replay and starts another, so a cancelled replay must release
        // the lock and finish its rollback rather than leaving the next reader waiting on a transaction
        // that never ends. This is a guard, not a pre-fix regression: the cancellation is joined before
        // the next replay starts, so the pre-fix code passes it too.
        writer.add(set(time = 1, weight = 50.0))
        val detecting = OverlapDetectingDriver(driver)
        val detectingRepository = SqlDelightExerciseProgressRepository(HydraFitDatabase(detecting))

        val cancelled = launch(Dispatchers.Default) {
            detectingRepository.observeProgress(progressRequest()).collect { }
        }
        detecting.awaitFirstTransaction()
        cancelled.cancel()
        cancelled.join()

        val after = withTimeout(30_000) {
            detectingRepository.observeProgress(progressRequest()).first()
        }

        assertEquals(0, detecting.overlaps)
        assertEquals(50.0, after.days.single().bestLoads.single().value)
    }

    private fun progressRequest() = ExerciseProgressRequest("squat", 0, 2, 2 * DAY)

    private fun set(time: Long, weight: Double, station: StationAttribution? = null) = WorkoutSet(
        exerciseId = "squat",
        reps = 10,
        weightKg = weight,
        performedAtMillis = time,
        station = station
    )

    private companion object {
        const val DAY = 86_400_000L
        const val OCCURRENCE = 7L
    }
}

/**
 * Delegating driver that records whether two transactions were ever requested at the same time.
 *
 * It holds the first transaction open — the delegate has already begun it when this returns — until a
 * second one is requested, bounded by [HOLD_TIMEOUT_MILLIS]. That forces an overlap rather than hoping
 * for one: without the repository's serialization the second request arrives while the first
 * transaction is still open and [overlaps] counts it, while with it the second reader is still
 * suspended on the lock and the counter stays zero. The hold is a timing budget, not the assertion —
 * what is asserted is the counter, and only the serialization decides its value.
 */
private class OverlapDetectingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
    private val lock = Any()
    private val secondAttempt = CompletableDeferred<Unit>()
    private val firstTransaction = CompletableDeferred<Unit>()
    private var attempts = 0
    private var holding = false

    var overlaps = 0
        private set

    /** Completes once the first transaction is open, so a test can act while a replay is in flight. */
    suspend fun awaitFirstTransaction() = firstTransaction.await()

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val isFirst = synchronized(lock) {
            attempts += 1
            // Only a request that lands while the first transaction is still held is an overlap. A
            // second request after that hold ends is a normal sequential replay and must not count.
            if (attempts > 1 && holding) overlaps += 1
            attempts == 1
        }
        if (!isFirst) secondAttempt.complete(Unit)
        val transaction = delegate.newTransaction()
        if (isFirst) {
            synchronized(lock) { holding = true }
            firstTransaction.complete(Unit)
            runBlocking { withTimeoutOrNull(HOLD_TIMEOUT_MILLIS) { secondAttempt.await() } }
            synchronized(lock) { holding = false }
        }
        return transaction
    }

    private companion object {
        const val HOLD_TIMEOUT_MILLIS = 2_000L
    }
}
