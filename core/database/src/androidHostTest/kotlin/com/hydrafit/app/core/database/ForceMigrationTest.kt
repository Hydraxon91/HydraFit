package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Migration 62 -> 63 adds the exclusive measured-force measurement and a performed set's descriptive
 * device identity: `forceNewtons` on `workoutSet` and the four prescription stores, plus
 * `deviceName`/`deviceRatingKg` on `workoutSet` only. The migration is additive: every recorded row
 * keeps the measurement it already had and gains no force or device.
 */
class ForceMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun everyForceStoreGainsANullableColumn() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 62)

        HydraFitDatabaseSchema.migrate(driver, 62, HydraFitDatabaseSchema.version)

        listOf(
            "workoutSet",
            "routineEntry",
            "planHistoryEntry",
            "activationEntry",
            "occurrenceEntry"
        ).forEach { table ->
            val column = columnInfo(table).firstOrNull { it.name == "forceNewtons" }
            assertEquals("REAL", column?.type, "$table.forceNewtons")
            assertEquals(false, column?.notNull, "$table.forceNewtons must stay nullable")
            assertNull(column?.defaultValue, "$table.forceNewtons must not be backfilled")
        }
    }

    @Test
    fun onlyAPerformedSetCarriesTheDeviceIdentity() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 62)
        HydraFitDatabaseSchema.migrate(driver, 62, HydraFitDatabaseSchema.version)

        listOf("deviceName" to "TEXT", "deviceRatingKg" to "REAL").forEach { (name, type) ->
            val column = columnInfo("workoutSet").firstOrNull { it.name == name }
            assertEquals(type, column?.type, "workoutSet.$name")
            assertEquals(false, column?.notNull, "workoutSet.$name must stay nullable")
        }
        // A device only describes a performed force reading, never a prescription slot.
        listOf(
            "routineEntry",
            "planHistoryEntry",
            "activationEntry",
            "occurrenceEntry"
        ).forEach { table ->
            assertNull(
                columnInfo(table).firstOrNull { it.name == "deviceName" },
                "$table has no device identity"
            )
        }
    }

    @Test
    fun recordedMeasuresSurviveAndGainNoForceOrDevice() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 62)
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

        HydraFitDatabaseSchema.migrate(driver, 62, HydraFitDatabaseSchema.version)

        val database = HydraFitDatabase(driver)
        val set = database.workoutLogQueries.selectAllSets().executeAsOne()
        assertEquals(5L, set.reps)
        assertEquals(100.0, set.weightKg)
        assertNull(set.forceNewtons)
        assertNull(set.deviceName)
        assertNull(set.deviceRatingKg)

        val entry = database.routineTemplateQueries.selectEntriesForWorkout(1L).executeAsOne()
        assertEquals(5L, entry.reps)
        assertEquals(100.0, entry.weightKg)
        assertNull(entry.forceNewtons)
    }

    @Test
    fun aForceSetStoresNewtonsAndADescriptiveDeviceWithoutLoad() = runTest {
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
        database.workoutLogQueries.assignForce(250.0, id)
        database.workoutLogQueries.assignDevice(
            deviceName = "Grip Trainer",
            deviceRatingKg = 60.0,
            id = id
        )

        val set = database.workoutLogQueries.selectSetById(id).executeAsOne()
        assertEquals(250.0, set.forceNewtons)
        assertEquals("Grip Trainer", set.deviceName)
        assertEquals(60.0, set.deviceRatingKg)
        assertNull(set.reps)
        assertNull(set.weightKg)
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
