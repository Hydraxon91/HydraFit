package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.hydrafit.app.core.domain.unit.DistanceUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Migration 60 -> 61 adds the exclusive distance measurement: `workoutSet` and the four
 * prescription stores gain a nullable `distanceMeters`, and `plannerEngine` gains a persisted
 * distance display unit. The migration is additive: every recorded row keeps the measurement it
 * already had and gains no distance, and an existing preference row defaults to metres.
 */
class DistanceMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun everyDistanceStoreGainsANullableColumn() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 60)

        HydraFitDatabaseSchema.migrate(driver, 60, HydraFitDatabaseSchema.version)

        listOf(
            "workoutSet",
            "routineEntry",
            "planHistoryEntry",
            "activationEntry",
            "occurrenceEntry"
        ).forEach { table ->
            val column = columnInfo(table).firstOrNull { it.name == "distanceMeters" }
            assertEquals("INTEGER", column?.type, "$table.distanceMeters")
            assertEquals(false, column?.notNull, "$table.distanceMeters must stay nullable")
            assertNull(column?.defaultValue, "$table.distanceMeters must not be backfilled")
        }
    }

    @Test
    fun recordedMeasuresSurviveAndGainNoDistance() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 60)
        // A counted set and a counted routine slot, as a pre-distance install could have recorded them.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup) " +
                "VALUES ('back-squat', 5, 100.0, 1, 0)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO routineWorkout(id, templateId, position, name) " +
                "VALUES (1, 1, 0, 'Push')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO routineEntry(workoutId, position, exerciseId, sets, reps, weightKg) " +
                "VALUES (1, 0, 'back-squat', 3, 5, 100.0)"
        )

        HydraFitDatabaseSchema.migrate(driver, 60, HydraFitDatabaseSchema.version)

        val database = HydraFitDatabase(driver)
        val set = database.workoutLogQueries.selectAllSets().executeAsOne()
        assertEquals(5L, set.reps)
        assertEquals(100.0, set.weightKg)
        assertNull(set.distanceMeters)

        val entry = database.routineTemplateQueries.selectEntriesForWorkout(1L).executeAsOne()
        assertEquals(5L, entry.reps)
        assertEquals(100.0, entry.weightKg)
        assertNull(entry.distanceMeters)
    }

    @Test
    fun anExistingPlannerRowDefaultsItsDistanceUnitToMetres() = runTest {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 60)
        // A preference row a pre-distance install could already have written.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO plannerEngine(id, engineId, weightUnit) VALUES (0, 'DETERMINISTIC', 'LB')"
        )

        HydraFitDatabaseSchema.migrate(driver, 60, HydraFitDatabaseSchema.version)

        val database = HydraFitDatabase(driver)
        assertEquals("LB", database.plannerEngineQueries.selectWeightUnit().executeAsOne())
        val repository = SqlDelightDistanceUnitRepository(database)
        assertEquals(DistanceUnit.METERS, repository.selectedUnit())
        // The preference round-trips and stays independent of the weight unit.
        repository.setUnit(DistanceUnit.FEET)
        assertEquals(DistanceUnit.FEET, repository.selectedUnit())
        assertEquals("LB", database.plannerEngineQueries.selectWeightUnit().executeAsOne())
    }

    @Test
    fun aDistanceSetStoresWholeMetresWithNoOtherMeasure() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, HydraFitDatabaseSchema.version)

        val database = HydraFitDatabase(driver)
        database.workoutLogQueries.insertSet(
            exerciseId = "back-squat",
            reps = null,
            weightKg = null,
            performedAt = 1L,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "EXTERNAL",
            durationSeconds = null
        )
        val id = database.workoutLogQueries.lastInsertedSetId().executeAsOne()
        database.workoutLogQueries.assignDistance(2500L, id)

        val set = database.workoutLogQueries.selectSetById(id).executeAsOne()
        assertEquals(2500L, set.distanceMeters)
        assertNull(set.reps)
        assertNull(set.durationSeconds)
    }

    private data class ColumnInfo(
        val name: String,
        val type: String?,
        val notNull: Boolean,
        val defaultValue: String?
    )

    private fun columnInfo(table: String): List<ColumnInfo> = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA table_info($table)",
        mapper = { cursor ->
            val columns = mutableListOf<ColumnInfo>()
            while (cursor.next().value) {
                columns += ColumnInfo(
                    name = cursor.getString(1).orEmpty(),
                    type = cursor.getString(2),
                    notNull = cursor.getLong(3) == 1L,
                    defaultValue = cursor.getString(4)
                )
            }
            QueryResult.Value(columns)
        },
        parameters = 0
    ).value
}
