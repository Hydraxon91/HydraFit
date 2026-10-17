package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration 55 -> 56 adds `warmupPreparationOverride`, the persisted per-action warm-up preparation
 * overrides. It is a purely additive table, so no existing table is rebuilt and no AUTOINCREMENT
 * high-water mark needs repair. A pre-existing database starts with no overrides, and a fresh schema
 * stores a counted or timed override with its exclusive measure.
 */
class WarmupPreparationOverrideMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV55CreatesAnEmptyOverrideTable() {
        driver = HistoricalDatabaseFixtures.empty()
        // 55 is the pre-override schema: the table does not exist yet.
        HydraFitDatabaseSchema.migrate(driver, 55, HydraFitDatabaseSchema.version)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "warmupPreparationOverride"))
    }

    @Test
    fun freshSchemaStoresACountedAndATimedOverride() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabaseSchema.create(driver)
        val database = HydraFitDatabase(driver)

        database.warmupPreparationOverrideQueries.upsert(
            action = "LOWER_BODY_PRACTICE",
            reps = 15,
            durationSeconds = null
        )
        database.warmupPreparationOverrideQueries.upsert(
            action = "EASY_GENERAL_MOVEMENT",
            reps = null,
            durationSeconds = 300
        )

        val rows = database.warmupPreparationOverrideQueries.selectAll().executeAsList()
        val counted = rows.single { it.action == "LOWER_BODY_PRACTICE" }
        assertEquals(15L, counted.reps)
        assertNull(counted.durationSeconds)
        val timed = rows.single { it.action == "EASY_GENERAL_MOVEMENT" }
        assertNull(timed.reps)
        assertEquals(300L, timed.durationSeconds)
    }

    @Test
    fun multiHopUpgradeFromV1CreatesTheTableAndAcceptsAnOverride() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, HydraFitDatabaseSchema.version)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "warmupPreparationOverride"))
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO warmupPreparationOverride(action, reps, durationSeconds) " +
                "VALUES ('SHOULDER_CIRCLES', 20, NULL)"
        )
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "warmupPreparationOverride"))
    }
}
