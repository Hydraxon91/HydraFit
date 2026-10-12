package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.freezesOccurrenceSlot
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WarmupEquipment
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.buildGuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.buildWarmupGuidance
import com.hydrafit.app.core.domain.workout.buildWorkoutSummary
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The 0.7.3 path through real producers and consumers rather than fixtures: a guidance-suggested ramp
 * logged through [LogWorkoutSetUseCase] into real storage, then read back by the guided-progress,
 * summary, fatigue-facing and planner-recency consumers, and finally corrected and checked against the
 * occurrence-replacement guard.
 */
class WarmupGuidanceIntegrationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun aSuggestedRampIsStoredAsAWarmUpAndExcludedFromWorkingWork() = runTest {
        val log = SqlDelightWorkoutLogRepository(database)
        val occurrence = occurrence()
        val ramp = buildWarmupGuidance(occurrence, equipment()).ramps.single().rampSets.first()

        logWorkoutSetUseCase()(rampSet(occurrence, ramp.reps, ramp.weightKg), 0L)

        val stored = log.all().single()
        assertTrue(stored.isWarmup)
        assertEquals(ramp.reps, stored.reps)
        assertEquals(ramp.weightKg, stored.weightKg)
        assertEquals(occurrence.id, stored.occurrenceId)
        assertEquals(ENTRY_ID, stored.occurrenceEntryId)

        // The fatigue-facing projection keeps the row but marks it a warm-up.
        assertTrue(log.loggedSets().single().isWarmup)

        // Guided progress counts no working set for the slot.
        val performedByEntry = log.setsForOccurrence(occurrence.id)
            .filter { !it.isWarmup && it.occurrenceEntryId != null }
            .groupingBy { requireNotNull(it.occurrenceEntryId) }
            .eachCount()
        assertEquals(0, buildGuidedWorkoutProgress(occurrence, performedByEntry).performedSets)

        // The summary lists it as a warm-up, never as performed or additional work.
        val summary = buildWorkoutSummary(occurrence, log.all(), emptyList(), emptyMap(), null)
        assertEquals(1, summary.warmupSets.size)
        assertTrue(summary.exercises.single().performedSets.isEmpty())
        assertTrue(summary.extraSets.isEmpty())

        // The planner's recent-weight projection excludes warm-ups.
        assertTrue(BuildRecentWeightsUseCase()(log.all()).isEmpty())
    }

    @Test
    fun aCorrectionAndAReplacementGuardKeepTheRecordedRamp() = runTest {
        val log = SqlDelightWorkoutLogRepository(database)
        val occurrence = occurrence()
        val ramp = buildWarmupGuidance(occurrence, equipment()).ramps.single().rampSets.first()
        logWorkoutSetUseCase()(rampSet(occurrence, ramp.reps, ramp.weightKg), 0L)
        val setId = log.all().single().id

        CorrectWorkoutSetUseCase(
            SqlDelightSessionResegmenter(database),
            TimeProvider { 120_000L }
        )(
            setId,
            WorkoutSetCorrection(
                reps = 5,
                weightKg = 42.5,
                rir = null,
                performedAtMillis = 60_000L
            )
        )

        val corrected = log.all().single()
        assertEquals(setId, corrected.id)
        assertTrue(corrected.isWarmup)
        assertEquals(5, corrected.reps)
        assertEquals(42.5, corrected.weightKg)
        // The recorded ramp still freezes its slot against replacement.
        assertTrue(corrected.freezesOccurrenceSlot(occurrence.id, ENTRY_ID, "back-squat"))
    }

    private fun equipment() = WarmupEquipment(
        selectedTags = setOf(EquipmentTag.BARBELL),
        maxWeights = mapOf(EquipmentTag.BARBELL to 200.0)
    )

    private fun rampSet(occurrence: WorkoutOccurrence, reps: Int, weightKg: Double) = WorkoutSet(
        exerciseId = "back-squat",
        reps = reps,
        weightKg = weightKg,
        loadKind = LoadKind.EXTERNAL,
        performedAtMillis = 60_000L,
        isWarmup = true,
        occurrenceId = occurrence.id,
        occurrenceEntryId = ENTRY_ID
    )

    private fun occurrence() = WorkoutOccurrence(
        id = OCCURRENCE_ID,
        activationId = 5L,
        activationWorkoutId = 9L,
        queuePosition = 0,
        entries = listOf(
            OccurrenceEntry(
                id = ENTRY_ID,
                position = 0,
                exerciseId = "back-squat",
                exerciseName = "Back Squat",
                movementPattern = MovementPattern.SQUAT,
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                sets = 3,
                reps = 8,
                weightKg = 100.0,
                loadCapability = ExerciseLoadCapability.EXTERNAL,
                loadKind = LoadKind.EXTERNAL
            )
        )
    )

    private fun logWorkoutSetUseCase(): LogWorkoutSetUseCase {
        val sessions = SqlDelightWorkoutSessionRepository(database)
        return LogWorkoutSetUseCase(
            repository = SqlDelightWorkoutLogRepository(database),
            startWorkoutSession = StartWorkoutSessionUseCase(sessions),
            endWorkoutSession = EndWorkoutSessionUseCase(sessions),
            observeOpenWorkoutSession = ObserveOpenWorkoutSessionUseCase(sessions),
            config = SessionConfig()
        )
    }

    private companion object {
        const val OCCURRENCE_ID = 30L
        const val ENTRY_ID = 40L
    }
}
