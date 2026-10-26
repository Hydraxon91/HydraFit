package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseProgressCalculatorTest {
    @Test
    fun handCalculatedDailyMetricsAndChronologicalRecords() {
        val result = calculate(
            set(1, 60.0, 10, time = 1),
            set(2, 70.0, 10, time = 2),
            set(3, 65.0, 12, time = 3),
            set(4, 200.0, 10, time = 4, warmup = true)
        )
        val day = result.days.single()
        assertEquals(3, day.workingSets)
        assertEquals(32L, day.totalReps)
        assertEquals(2080.0, day.externalVolumeKg)
        assertEquals(70.0, day.bestLoads.single().value)
        assertEquals(12.0, day.bestReps.single().value)
        assertEquals(70.0 * (1 + 10 / 30.0), day.estimatedOneRepMax!!.value)
        assertEquals(listOf(2L, 2L), result.occurrenceEvents.map { it.setId })
        assertEquals(
            setOf(ProgressRecordType.LOAD_AT_REPS, ProgressRecordType.ESTIMATED_ONE_REP_MAX),
            result.occurrenceEvents.map { it.record.key.type }.toSet()
        )
    }

    @Test
    fun timedSetsFeedTheDayTrendButNeverRecordsOrEstimates() {
        val result = calculate(
            timed(1, seconds = 40, time = 1),
            timed(2, seconds = 50, time = 2),
            set(3, 100.0, 10, time = 3)
        )
        val day = result.days.single()
        assertEquals(3, day.workingSets)
        assertEquals(10L, day.totalReps)
        assertEquals(90L, day.totalDurationSeconds)
        assertEquals(1000.0, day.externalVolumeKg)
        // Only the counted set holds records; the timed sets contribute none.
        assertTrue(result.records.all { record -> record.setIds == listOf(3L) })
        assertEquals(100.0 * (1 + 10 / 30.0), day.estimatedOneRepMax!!.value)

        val timedOnly = calculate(timed(1, seconds = 30, time = 1))
        val onlyDay = timedOnly.days.single()
        assertEquals(1, onlyDay.workingSets)
        assertEquals(0L, onlyDay.totalReps)
        assertEquals(30L, onlyDay.totalDurationSeconds)
        assertTrue(timedOnly.records.isEmpty())
        assertNull(onlyDay.estimatedOneRepMax)
        assertNull(onlyDay.externalVolumeKg)
    }

    @Test
    fun distanceSetsFeedTheDayTrendButNeverRecordsOrEstimates() {
        val result = calculate(
            distance(1, meters = 400, load = 20.0, time = 1),
            distance(2, meters = 600, load = 20.0, time = 2)
        )
        val day = result.days.single()
        assertEquals(2, day.workingSets)
        assertEquals(1000L, day.totalDistanceMeters)
        // A distance carry's load is never turned into rep volume or a one-rep-max estimate.
        assertEquals(0L, day.totalReps)
        assertNull(day.externalVolumeKg)
        assertNull(day.estimatedOneRepMax)
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun forceSetsCountAsWorkWithoutContributingToRepsLoadOrRecords() {
        val forceOnly = calculate(
            force(1, newtons = 250.0, time = 1, device = DeviceAttribution("Grip meter")),
            force(2, newtons = 275.0, time = 2)
        )
        val forceDay = forceOnly.days.single()
        assertEquals(2, forceDay.workingSets)
        assertEquals(0L, forceDay.totalReps)
        assertNull(forceDay.externalVolumeKg)
        assertTrue(forceDay.bestLoads.isEmpty())
        assertTrue(forceDay.bestReps.isEmpty())
        assertNull(forceDay.estimatedOneRepMax)
        assertEquals(0L, forceDay.totalDurationSeconds)
        assertEquals(0L, forceDay.totalDistanceMeters)
        assertTrue(forceOnly.records.isEmpty())
        assertTrue(forceOnly.occurrenceEvents.isEmpty())

        val mixed = calculate(
            force(1, newtons = 250.0, time = 1, device = DeviceAttribution("Grip meter")),
            set(2, 60.0, 10, time = 2)
        )
        assertEquals(2, mixed.days.single().workingSets)
        assertEquals(10L, mixed.days.single().totalReps)
        assertEquals(600.0, mixed.days.single().externalVolumeKg)
        val loadRecord = mixed.records.single {
            it.key.type == ProgressRecordType.LOAD_AT_REPS
        }
        assertEquals(listOf(2L), loadRecord.setIds)
        assertTrue(
            mixed.records.any { it.key.type == ProgressRecordType.REPS_AT_LOAD }
        )
        assertTrue(
            mixed.records.any { it.key.type == ProgressRecordType.ESTIMATED_ONE_REP_MAX }
        )
    }

    @Test
    fun equalTimestampsUseEarlierBaselineAndKeepAllTiedHolders() {
        val result = calculate(
            set(1, 50.0, 10, time = 1),
            set(2, 60.0, 10, time = 2),
            set(3, 70.0, 10, time = 2),
            set(4, 70.0, 10, time = 3)
        )
        val record = result.records.single {
            it.key.type == ProgressRecordType.LOAD_AT_REPS
        }
        assertEquals(listOf(3L, 4L), record.setIds)
        assertEquals(setOf(2L, 3L), result.occurrenceEvents.map { it.setId }.toSet())
    }

    @Test
    fun bodyweightAddedLegacyAndMissingLoadsNeverBecomeExternalTonnageOrEstimates() {
        val result = calculate(
            set(1, null, 10, kind = LoadKind.BODYWEIGHT),
            set(2, 20.0, 10, kind = LoadKind.ADDED),
            set(3, 100.0, 10, kind = LoadKind.LEGACY_UNSPECIFIED),
            set(4, null, 10),
            set(5, 0.0, 10),
            set(6, 30.0, 16)
        )
        assertNull(result.days.single().estimatedOneRepMax)
        assertEquals(480.0, result.days.single().externalVolumeKg)
        assertTrue(result.records.none { it.key.loadKind == LoadKind.LEGACY_UNSPECIFIED })
        assertTrue(
            result.records.any {
                it.key.loadKind == LoadKind.BODYWEIGHT && it.key.weightKg == null
            }
        )
        assertTrue(
            result.records.any {
                it.key.loadKind == LoadKind.EXTERNAL && it.key.weightKg == 0.0
            }
        )
        assertEquals(6, result.days.single().workingSets)
        val addedOnly = calculate(set(1, 20.0, 10, kind = LoadKind.ADDED))
        assertNull(addedOnly.days.single().externalVolumeKg)
    }

    @Test
    fun rangeAndFutureFilteringDoNotTurnRangeBestsIntoAllTimeRecords() {
        val rows = listOf(
            set(1, 100.0, 10, time = 0),
            set(2, 60.0, 10, time = DAY),
            set(3, 200.0, 10, time = 3 * DAY)
        )
        val request = ExerciseProgressRequest("squat", 1, 1, 2 * DAY, occurrenceId = 7)
        val result = calculate(*rows.toTypedArray(), request = request)
        assertEquals(60.0, result.days.single().bestLoads.single().value)
        val loadRecord = result.records.first { it.key.type == ProgressRecordType.LOAD_AT_REPS }
        assertEquals(100.0, loadRecord.value)
        assertTrue(result.occurrenceEvents.isEmpty())
        val wider = calculate(*rows.toTypedArray(), request = request.copy(dayFrom = 0))
        assertEquals(result.records, wider.records)
    }

    @Test
    fun fixedOffsetHandlesNegativeEpochAndLeavesMissingDaysAbsent() {
        val request = ExerciseProgressRequest("squat", -1, 2, 3 * DAY, utcOffsetMillis = 1)
        val result = calculate(
            set(1, 10.0, 1, time = -2),
            set(2, 10.0, 1, time = DAY),
            request = request
        )
        assertEquals(listOf(-1L, 1L), result.days.map { it.localEpochDay })
    }

    @Test
    fun deletedOrCorrectedWinnersReplayFromRemainingFacts() {
        val first = set(1, 50.0, 10, time = 1)
        val winner = set(2, 100.0, 10, time = 2)
        val remaining = set(3, 70.0, 10, time = 3)
        fun holder(result: ExerciseProgress) = result.records.first {
            it.key.type == ProgressRecordType.LOAD_AT_REPS
        }.setIds
        assertEquals(listOf(2L), holder(calculate(first, winner, remaining)))
        assertEquals(listOf(3L), holder(calculate(first, remaining)))
        assertEquals(listOf(3L), holder(calculate(first, winner.copy(weightKg = 60.0), remaining)))
        val moved = calculate(remaining.copy(performedAtMillis = 0), first, winner)
        assertTrue(moved.occurrenceEvents.none { it.setId == 3L })
    }

    @Test
    fun requestsRejectInvalidFiltersAndReplayRejectsWrongOrderOrExercise() {
        assertFailsWith<IllegalArgumentException> { ExerciseProgressRequest("", 0, 1, 0) }
        assertFailsWith<IllegalArgumentException> { ExerciseProgressRequest("squat", 1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ExerciseHistoryRequest("squat", 101) }
        assertFailsWith<IllegalArgumentException> {
            ExerciseHistoryRequest("squat", cursor = ExerciseHistoryCursor("other", 0, 1))
        }
        assertFailsWith<IllegalArgumentException> {
            calculate(set(1, 10.0, 1, time = 2), set(2, 10.0, 1, time = 1))
        }
        assertFailsWith<IllegalArgumentException> {
            calculate(set(1, 10.0, 1).copy(exerciseId = "other"))
        }
        assertTrue(calculate().days.isEmpty())
    }

    @Test
    fun resultSnapshotsAreDetachedFromLaterTies() {
        val calculator = ExerciseProgressCalculator(
            ExerciseProgressRequest("squat", 0, 2, 2 * DAY, occurrenceId = 7)
        )
        calculator.accept(set(1, 70.0, 10, time = 1))
        val first = calculator.result()
        val snapshot = first.records.first { it.key.type == ProgressRecordType.LOAD_AT_REPS }
        assertEquals(listOf(1L), snapshot.setIds)

        calculator.accept(set(2, 70.0, 10, time = 2))
        calculator.accept(set(3, 70.0, 10, time = 2))

        assertEquals(listOf(1L), snapshot.setIds)
        val later = calculator.result().records.first {
            it.key.type == ProgressRecordType.LOAD_AT_REPS
        }
        assertEquals(listOf(1L, 2L, 3L), later.setIds)
    }

    @Test
    fun largeTiedPopulationKeepsEveryHolderExactlyOnce() {
        val calculator = ExerciseProgressCalculator(ExerciseProgressRequest("squat", 0, 2, 2 * DAY))
        val expected = (1L..5_000L).toList()
        expected.forEach { calculator.accept(set(it, 70.0, 10, time = 1)) }
        val record = calculator.result().records.first {
            it.key.type == ProgressRecordType.LOAD_AT_REPS
        }
        assertEquals(expected, record.setIds)
    }

    private fun calculate(
        vararg rows: WorkoutSet,
        request: ExerciseProgressRequest =
            ExerciseProgressRequest("squat", 0, 2, 2 * DAY, occurrenceId = 7)
    ): ExerciseProgress = ExerciseProgressCalculator(request).run {
        rows.forEach(::accept)
        result()
    }

    private fun set(
        id: Long,
        load: Double?,
        reps: Int,
        time: Long = 1,
        kind: LoadKind = LoadKind.EXTERNAL,
        warmup: Boolean = false
    ) = WorkoutSet(
        id = id,
        exerciseId = "squat",
        reps = reps,
        weightKg = load,
        loadKind = kind,
        performedAtMillis = time,
        isWarmup = warmup,
        occurrenceId = 7
    )

    private fun timed(id: Long, seconds: Int, time: Long = 1) = WorkoutSet(
        id = id,
        exerciseId = "squat",
        reps = null,
        weightKg = null,
        loadKind = LoadKind.BODYWEIGHT,
        performedAtMillis = time,
        occurrenceId = 7,
        durationSeconds = seconds
    )

    private fun distance(id: Long, meters: Int, load: Double?, time: Long = 1) = WorkoutSet(
        id = id,
        exerciseId = "squat",
        reps = null,
        weightKg = load,
        loadKind = if (load == null) LoadKind.BODYWEIGHT else LoadKind.EXTERNAL,
        performedAtMillis = time,
        occurrenceId = 7,
        distanceMeters = meters
    )

    private fun force(
        id: Long,
        newtons: Double,
        time: Long = 1,
        device: DeviceAttribution? = null
    ) = WorkoutSet(
        id = id,
        exerciseId = "squat",
        reps = null,
        weightKg = null,
        loadKind = LoadKind.BODYWEIGHT,
        performedAtMillis = time,
        occurrenceId = 7,
        forceNewtons = newtons,
        device = device
    )

    private companion object {
        const val DAY = 86_400_000L
    }
}
