package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutPlanSourcesRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun progressionReplayExcludesStationAttributedSets() = runTest {
        SeedEquipmentCatalog(database).seed()
        val log = SqlDelightWorkoutLogRepository(database)
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            SqlDelightEquipmentProfileRepository(database),
            SqlDelightEnginePreferenceRepository(database),
            log,
            SqlDelightTrainingGoalRepository(database),
            SqlDelightPersonalRecordRepository(database, TimeProvider { 100L }),
            SqlDelightExercisePreferenceRepository(database),
            SqlDelightExerciseExclusionRepository(database),
            database
        )
        log.add(
            WorkoutSet(
                exerciseId = "barbell-bench-press",
                reps = 5,
                weightKg = 80.0,
                performedAtMillis = 1L
            )
        )
        log.add(
            WorkoutSet(
                exerciseId = "barbell-bench-press",
                reps = 5,
                weightKg = 100.0,
                performedAtMillis = 2L,
                station = StationAttribution(1L, "Rack A", StationLoadBasis.TOTAL)
            )
        )

        assertEquals(1, sources.observe().first().progressionHistory.sets.size)
        assertEquals(80.0, sources.observe().first().progressionHistory.sets.single().weightKg)
    }

    @Test
    fun sessionClosureInvalidatesCoherentRecordedTargetHistoryWithoutCountChange() = runTest {
        SeedEquipmentCatalog(database).seed()
        val log = SqlDelightWorkoutLogRepository(database)
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            SqlDelightEquipmentProfileRepository(database),
            SqlDelightEnginePreferenceRepository(database),
            log,
            SqlDelightTrainingGoalRepository(database),
            SqlDelightPersonalRecordRepository(database, TimeProvider { 100L }),
            SqlDelightExercisePreferenceRepository(database),
            SqlDelightExerciseExclusionRepository(database),
            database
        )
        database.workoutSessionQueries.insertSession("target-session", 1, null, 0)
        val target = WorkoutPrescriptionSnapshot(
            exerciseId = "barbell-bench-press",
            sets = 1,
            reps = 5,
            loadKind = LoadKind.EXTERNAL,
            weightKg = 80.0,
            isDeload = false
        )
        log.add(
            WorkoutSet(
                exerciseId = target.exerciseId,
                reps = 5,
                weightKg = 80.0,
                performedAtMillis = 2,
                sessionId = "target-session",
                prescriptionSnapshot = target
            )
        )
        val opened = sources.observe().first().progressionHistory
        assertEquals(null, opened.sessions.single().endedAtMillis)
        assertEquals(target, opened.sets.single().prescriptionSnapshot)

        database.workoutSessionQueries.endSession(3, "target-session")

        val closed = sources.observe().first().progressionHistory
        assertEquals(3L, closed.sessions.single().endedAtMillis)
        assertEquals(target, closed.sets.single().prescriptionSnapshot)
        assertEquals(1, closed.sets.size)
    }

    @Test
    fun derivesALegacyTargetOnlyFromUnambiguousFrozenOccurrenceWork() = runTest {
        SeedEquipmentCatalog(database).seed()
        val log = SqlDelightWorkoutLogRepository(database)
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            SqlDelightEquipmentProfileRepository(database),
            SqlDelightEnginePreferenceRepository(database),
            log,
            SqlDelightTrainingGoalRepository(database),
            SqlDelightPersonalRecordRepository(database, TimeProvider { 100L }),
            SqlDelightExercisePreferenceRepository(database),
            SqlDelightExerciseExclusionRepository(database),
            database
        )
        val (occurrenceId, entryId) = seedOccurrenceEntry(
            exerciseId = "barbell-bench-press",
            weekNumber = 1
        )
        val setId = seedLegacySet(occurrenceId, entryId)

        val derived = sources.observe().first().progressionHistory
        assertEquals(
            WorkoutPrescriptionSnapshot(
                exerciseId = "barbell-bench-press",
                sets = 3,
                reps = 5,
                loadKind = LoadKind.EXTERNAL,
                weightKg = 80.0,
                isDeload = false
            ),
            derived.legacyTargets.getValue(setId)
        )

        // An entry that names a different exercise than the set is never guessed at.
        val (_, otherEntry) = seedOccurrenceEntry(
            exerciseId = "back-squat",
            weekNumber = 1,
            occurrenceId = occurrenceId
        )
        database.workoutLogQueries.assignOccurrence(occurrenceId, otherEntry, setId)
        assertTrue(sources.observe().first().progressionHistory.legacyTargets.isEmpty())
    }

    @Test
    fun derivesNoLegacyTargetWithoutAnActivationWeek() = runTest {
        SeedEquipmentCatalog(database).seed()
        val log = SqlDelightWorkoutLogRepository(database)
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            SqlDelightEquipmentProfileRepository(database),
            SqlDelightEnginePreferenceRepository(database),
            log,
            SqlDelightTrainingGoalRepository(database),
            SqlDelightPersonalRecordRepository(database, TimeProvider { 100L }),
            SqlDelightExercisePreferenceRepository(database),
            SqlDelightExerciseExclusionRepository(database),
            database
        )
        val (occurrenceId, entryId) = seedOccurrenceEntry(
            exerciseId = "barbell-bench-press",
            weekNumber = null
        )
        seedLegacySet(occurrenceId, entryId)

        assertTrue(sources.observe().first().progressionHistory.legacyTargets.isEmpty())
    }

    /** One closed session holding a single snapshot-free set linked to [entryId]. */
    private fun seedLegacySet(occurrenceId: Long, entryId: Long): Long {
        database.workoutSessionQueries.insertSession("legacy-s", 1, 2, 0)
        val setId = insertSetWith("legacy-s")
        database.workoutLogQueries.assignOccurrence(occurrenceId, entryId, setId)
        return setId
    }

    private fun insertSetWith(sessionId: String): Long {
        database.workoutLogQueries.insertSetWithTimingProvenance(
            exerciseId = "barbell-bench-press",
            reps = 5,
            weightKg = 80.0,
            performedAt = 2,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = sessionId,
            loadKind = "EXTERNAL",
            timingProvenance = "UNKNOWN",
            startedAtElapsedMillis = null,
            completedAtElapsedMillis = null,
            durationSeconds = null,
            prescriptionSnapshot = null
        )
        return database.workoutLogQueries.lastInsertedSetId().executeAsOne()
    }

    /** One activation/workout/occurrence/entry chain; returns (occurrenceId, occurrenceEntryId). */
    private fun seedOccurrenceEntry(
        exerciseId: String,
        weekNumber: Int?,
        occurrenceId: Long? = null
    ): Pair<Long, Long> {
        val activation = database.trainingScheduleQueries.selectActivationById(1L)
            .executeAsOneOrNull()
        if (activation == null) {
            database.trainingScheduleQueries.insertActivation(
                templateId = null,
                templateRevision = null,
                sourcePlanId = null,
                name = "Block",
                createdAtMillis = 0,
                startEpochDay = 0,
                mode = "SEQUENCE",
                weekdayMask = 0,
                status = "ACTIVE",
                weekNumber = weekNumber?.toLong(),
                cycleNumber = 1,
                endedAtMillis = null,
                revision = 0
            )
            database.trainingScheduleQueries.insertActivationWorkout(1L, null, 0, "Day", null)
            database.trainingScheduleQueries.insertOccurrence(
                activationId = 1L,
                activationWorkoutId = 1L,
                queuePosition = 0,
                scheduledEpochDay = null,
                notBeforeEpochDay = null,
                startedAtMillis = null,
                resolvedAtMillis = null,
                status = "PENDING",
                revision = 0
            )
        }
        val resolvedOccurrenceId = occurrenceId
            ?: database.trainingScheduleQueries.lastInsertedOccurrenceId().executeAsOne()
        database.trainingScheduleQueries.insertOccurrenceEntry(
            occurrenceId = resolvedOccurrenceId,
            sourceActivationEntryId = null,
            position = 0,
            exerciseId = exerciseId,
            exerciseName = exerciseId,
            movementPattern = "HORIZONTAL_PUSH",
            requiredEquipment = "",
            involvements = null,
            isUnilateral = 0,
            sets = 3,
            reps = 5,
            durationSeconds = null,
            weightKg = 80.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL",
            remainingDisposition = null,
            terminalRemainingSets = null,
            progressionPolicy = null,
            setTargets = null
        )
        val entryId = database.trainingScheduleQueries.lastInsertedOccurrenceEntryId()
            .executeAsOne()
        return resolvedOccurrenceId to entryId
    }

    @Test
    fun combinesCurrentEquipmentEngineScheduleAndLoggedSets() = runTest {
        val equipment = SqlDelightEquipmentSelectionRepository(database)
        val preference = SqlDelightEnginePreferenceRepository(database)
        val goal = SqlDelightTrainingGoalRepository(database)
        val workoutLog = SqlDelightWorkoutLogRepository(database)
        val equipmentCatalog = SqlDelightEquipmentRepository(database)
        SeedEquipmentCatalog(database).seed()
        val records = SqlDelightPersonalRecordRepository(database, TimeProvider { 0L })
        val exercisePreferences = SqlDelightExercisePreferenceRepository(database)
        val exerciseExclusions = SqlDelightExerciseExclusionRepository(database)
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            SqlDelightEquipmentProfileRepository(database),
            preference,
            workoutLog,
            goal,
            records,
            exercisePreferences,
            exerciseExclusions,
            database
        )
        equipment.setSelected(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH))
        preference.setEngine(PlannerEngineId.GEMINI_API)
        preference.setDaysPerWeek(5)
        goal.setGoal(TrainingGoal.HYPERTROPHY)
        equipmentCatalog.setMaxWeight(EquipmentTag.CABLE_MACHINE, 100.0)
        exercisePreferences.set("barbell-bench-press", ExercisePreference.PREFER)
        exerciseExclusions.set(ExerciseExclusion("barbell-row"))
        workoutLog.add(
            WorkoutSet(
                exerciseId = "barbell-bench-press",
                reps = 5,
                weightKg = 80.0,
                performedAtMillis = 100L
            )
        )

        val result = sources.observe().first()

        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), result.availableEquipment)
        assertEquals(PlannerEngineId.GEMINI_API, result.selectedEngine)
        assertEquals(5, result.daysPerWeek)
        assertEquals(TrainingGoal.HYPERTROPHY, result.goal)
        assertEquals(100L, result.loggedSets.single().timestampMillis)
        assertEquals(80.0, result.loggedWorkoutSets.single().weightKg)
        assertEquals(mapOf(EquipmentTag.CABLE_MACHINE to 100.0), result.equipmentMaxWeights)
        assertEquals(
            mapOf("barbell-bench-press" to ExercisePreference.PREFER),
            result.exercisePreferences
        )
        assertEquals(
            listOf(ExerciseExclusion("barbell-row")),
            result.exerciseExclusions
        )
    }

    @Test
    fun theActiveEquipmentProfilesLoadingInventoryReachesThePlanSources() = runTest {
        SeedEquipmentCatalog(database).seed()
        val profiles = SqlDelightEquipmentProfileRepository(database)
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            profiles,
            SqlDelightEnginePreferenceRepository(database),
            SqlDelightWorkoutLogRepository(database),
            SqlDelightTrainingGoalRepository(database),
            SqlDelightPersonalRecordRepository(database, TimeProvider { 0L }),
            SqlDelightExercisePreferenceRepository(database),
            SqlDelightExerciseExclusionRepository(database),
            database
        )
        val saved = LoadingInventory(
            bars = listOf(
                LoadingBar(id = "bar", name = "Bar", weightTicks = 20_000, collarTicks = 0)
            ),
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 25_000, 30_000)
                )
            )
        )
        profiles.saveLoadingInventory(profiles.current().context, saved)
        assertEquals(saved, sources.observe().first().loadingInventory)

        // Rewriting the active profile's inventory is what the sources observe next.
        val changed = saved.copy(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(35_000))
            )
        )
        profiles.saveLoadingInventory(profiles.current().context, changed)
        assertEquals(changed, sources.observe().first().loadingInventory)

        // A different active profile reports its own inventory, not the previous profile's.
        val secondId = profiles.create("Home")
        profiles.activate(profiles.current().context, secondId)
        assertEquals(LoadingInventory(), sources.observe().first().loadingInventory)
    }
}
