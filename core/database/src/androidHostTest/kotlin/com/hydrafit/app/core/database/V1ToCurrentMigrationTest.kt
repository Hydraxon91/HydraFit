package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Full-chain migration coverage: a database created by the first schema version (v1, only the
 * `exercise` catalog table) must migrate through every `.sqm` to the current schema, preserving the
 * recorded row and materialising the columns later versions add. This is what the SQLDelight
 * `verifySqlDelightMigration` snapshot cannot cover retroactively, because only the current schema
 * snapshot can be generated.
 */
class V1ToCurrentMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratesFromV1ToTheCurrentSchemaPreservingRecordedRows() = runTest {
        driver = HistoricalDatabaseFixtures.v1()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, secondaryMuscles) " +
                "VALUES ('back-squat', 'Back Squat', 'BARBELL', 'QUADS', 'GLUTES')"
        )

        HydraFitDatabase.Schema.migrate(driver, 1, HydraFitDatabase.Schema.version)

        val database = HydraFitDatabase(driver)
        val exercise = database.exerciseQueries.selectById("back-squat").executeAsOne()
        // The recorded identity and non-migrated columns survive the whole chain.
        assertEquals("Back Squat", exercise.name)
        assertEquals("BARBELL", exercise.requiredEquipment)
        // Columns added by later migrations materialise with their defaults.
        assertEquals("CORE", exercise.movementPattern)
        assertEquals(0L, exercise.isCustom)
        assertEquals(0L, exercise.isUnilateral)
        assertEquals("UNSPECIFIED", exercise.loadCapability)
        // 19.sqm/20.sqm turn the v1 legacy muscle tags into per-muscle involvements.
        assertEquals("GLUTES:0.5,QUADS:1.0", exercise.involvements)

        // The newest set-level timing columns exist and default to unknown/no elapsed instants.
        database.workoutLogQueries.insertSet(
            exerciseId = "back-squat",
            reps = 5,
            weightKg = null,
            performedAt = 1L,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "LEGACY_UNSPECIFIED"
        )
        val set = database.workoutLogQueries.selectAllSets().executeAsOne()
        assertEquals("UNKNOWN", set.timingProvenance)
        assertNull(set.startedAtElapsedMillis)
        assertNull(set.completedAtElapsedMillis)
    }
}
