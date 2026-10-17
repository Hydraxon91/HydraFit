package com.hydrafit.app.core.database

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the [HydraFitDatabaseSchema] wrapper itself. The 52 -> 53 high-water regression assertions
 * live in [TimedWorkoutSetMigrationTest]; this file proves that the generated schema is delegated to
 * unchanged, that the capture/restore is scoped to the one rebuild, and that caller callbacks are
 * passed through in order and see the restored invariant.
 */
class HydraFitDatabaseSchemaTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun freshCreateKeepsTheGeneratedSchemaAndNumbersFromOne() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabaseSchema.create(driver)

        assertEquals(HydraFitDatabase.Schema.version, HydraFitDatabaseSchema.version)
        val database = HydraFitDatabase(driver)
        insertSet(database, reps = 8, durationSeconds = null, performedAt = 1)
        insertSet(database, reps = null, durationSeconds = 60, performedAt = 2)

        val sets = database.workoutLogQueries.selectAllSets().executeAsList()
        assertEquals(listOf(1L, 2L), sets.map { it.id })
        assertEquals(3L, indexCount())
    }

    @Test
    fun directRebuildRestoresTheMarkBeforeTheCallersPostRebuildCallback() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 52)
        insertCountedSetWithId(id = 1000L, performedAt = 10)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM workoutSet WHERE id = 1000")
        assertEquals(1000L, sequenceHighWater())

        var markSeenByCallback: Long? = null
        HydraFitDatabaseSchema.migrate(
            driver,
            52,
            HydraFitDatabaseSchema.version,
            AfterVersion(52) { markSeenByCallback = sequenceHighWater() }
        )

        assertEquals(1000L, sequenceHighWater())
        // The caller's rebuild-boundary callback sees the restored mark.
        assertEquals(1000L, markSeenByCallback)
        assertEquals(1001L, insertSetAndReturnId(performedAt = 20))
    }

    @Test
    fun multiHopUpgradeCapturesTheMarkOnlyAtTheRebuild() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 40)
        insertCountedSetWithId(id = 700L, performedAt = 10)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM workoutSet")
        assertEquals(700L, sequenceHighWater())

        val order = mutableListOf<Long>()
        var markAtRebuildCallback: Long? = null
        HydraFitDatabaseSchema.migrate(
            driver,
            40,
            HydraFitDatabaseSchema.version,
            AfterVersion(45) { order += 45 },
            AfterVersion(52) {
                order += 52
                markAtRebuildCallback = sequenceHighWater()
            }
        )

        // Both caller callbacks ran once, in version order.
        assertEquals(listOf(45L, 52L), order)
        assertEquals(700L, markAtRebuildCallback)
        assertEquals(700L, sequenceHighWater())
        assertEquals(701L, insertSetAndReturnId(performedAt = 20))
    }

    @Test
    fun multiHopUpgradeDoesNotReviveAMarkLostByAnEarlierRebuild() {
        driver = HistoricalDatabaseFixtures.v1()
        // v10 predates the historical 20.sqm `workoutSet` rebuild, which already resets the mark.
        HydraFitDatabase.Schema.migrate(driver, 1, 10)
        insertCountedSetWithId(id = 800L, performedAt = 10)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM workoutSet")

        HydraFitDatabaseSchema.migrate(driver, 10, HydraFitDatabaseSchema.version)

        // The wrapper repairs only 52 -> 53, so 20.sqm's own mark loss is left unchanged.
        assertEquals(0L, sequenceHighWater())
        assertEquals(1L, insertSetAndReturnId(performedAt = 20))
    }

    @Test
    fun migrationStoppingBeforeTheRebuildLeavesTheMarkAndAllocationAlone() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 52)
        insertCountedSetWithId(id = 400L, performedAt = 10)
        assertEquals(400L, sequenceHighWater())

        // 52 -> 52 is a no-op and must not run the capture/restore path.
        HydraFitDatabaseSchema.migrate(driver, 52, 52)

        assertEquals(400L, sequenceHighWater())
        // A raw insert at the pre-timed schema still gets the next id, not a reset one.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, performedAt) VALUES ('back-squat', 5, 20)"
        )
        assertEquals(401L, sequenceHighWater())
    }

    @Test
    fun repeatedNoOpMigrationDoesNotChangeAllocations() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 52)
        insertCountedSetWithId(id = 1000L, performedAt = 10)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM workoutSet")
        HydraFitDatabaseSchema.migrate(driver, 52, HydraFitDatabaseSchema.version)
        assertEquals(1000L, sequenceHighWater())

        HydraFitDatabaseSchema.migrate(
            driver,
            HydraFitDatabaseSchema.version,
            HydraFitDatabaseSchema.version
        )

        assertEquals(1000L, sequenceHighWater())
        assertEquals(1001L, insertSetAndReturnId(performedAt = 20))
    }

    @Test
    fun productionDriverFactoriesUseTheSchemaWrapper() {
        val factorySources = listOf(
            "core/database/src/androidMain/kotlin/com/hydrafit/app/core/database/" +
                "AndroidDatabaseDriverFactory.kt",
            "core/database/src/iosMain/kotlin/com/hydrafit/app/core/database/" +
                "NativeDatabaseDriverFactory.kt"
        )
        factorySources.forEach { path ->
            val source = repositoryFile(path).readText()
            assertTrue(
                source.contains("HydraFitDatabaseSchema"),
                "$path must pass the production wrapper to its driver"
            )
            assertTrue(
                !source.contains("HydraFitDatabase.Schema"),
                "$path must not pass the generated schema directly"
            )
        }
    }

    private fun insertSet(
        database: HydraFitDatabase,
        reps: Long?,
        durationSeconds: Long?,
        performedAt: Long
    ) {
        database.workoutLogQueries.insertSet(
            exerciseId = "back-squat",
            reps = reps,
            weightKg = null,
            performedAt = performedAt,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "BODYWEIGHT",
            durationSeconds = durationSeconds
        )
    }

    private fun insertSetAndReturnId(performedAt: Long): Long {
        val database = HydraFitDatabase(driver)
        insertSet(database, reps = 5, durationSeconds = null, performedAt = performedAt)
        return database.workoutLogQueries.lastInsertedSetId().executeAsOne()
    }

    /** The pre-53 schema stores a set's rep count as NOT NULL, so only those columns are needed. */
    private fun insertCountedSetWithId(id: Long, performedAt: Long) {
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(id, exerciseId, reps, performedAt) " +
                "VALUES ($id, 'back-squat', 5, $performedAt)"
        )
    }

    private fun sequenceHighWater() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'workoutSet'), 0)"
    )

    private fun indexCount() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type = 'index' AND name IN " +
            "('workoutSet_sessionId_performedAt', 'workoutSet_exerciseId_performedAt_id', " +
            "'workoutSet_occurrenceId_exerciseId')"
    )

    /** Resolves a repository path from wherever Gradle starts the test JVM. */
    private fun repositoryFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
                ?: error("Could not locate $relativePath from ${System.getProperty("user.dir")}")
        }
    }
}
