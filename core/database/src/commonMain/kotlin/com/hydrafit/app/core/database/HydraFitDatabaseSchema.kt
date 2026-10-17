package com.hydrafit.app.core.database

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * The production schema entry point: the generated [HydraFitDatabase.Schema] plus one targeted
 * repair of the `52.sqm` workout-set and `53.sqm` prescription-entry rebuilds.
 *
 * `52.sqm` rebuilds `workoutSet` to make `reps` nullable and add `durationSeconds`. SQLite cannot
 * drop NOT NULL in place, so the migration copies the surviving rows into a new table and drops the
 * old one. Dropping the old table also drops its `sqlite_sequence` row, so the rebuilt table's
 * AUTOINCREMENT high-water mark falls back to the largest copied id, or to nothing when every row
 * was deleted. An id that was allocated and later deleted could then be handed out a second time.
 * This wrapper captures the stored mark immediately before the rebuild and restores it immediately
 * after, never lowering it below what the rebuilt table already holds.
 *
 * Everything else is delegated unchanged: [version] and [create] come straight from the generated
 * schema, and migrations outside these rebuilds are passed through with their callbacks intact.
 * The repair reads and writes the `sqlite_sequence` internal table with raw SQL (SQLDelight's
 * resolver rejects that table inside a `.sqm`), so no generated query depends on the final columns,
 * and the caller's migration transaction and error propagation are preserved.
 */
internal object HydraFitDatabaseSchema : SqlSchema<QueryResult.Value<Unit>> {

    /** `52.sqm` migrates 52 -> 53; this is the rebuild whose high-water mark is repaired. */
    private const val TIMED_SET_REBUILD_VERSION = 52L

    private const val WORKOUT_SET = "workoutSet"

    private val rebuildTables = mapOf(
        TIMED_SET_REBUILD_VERSION to listOf(WORKOUT_SET),
        53L to listOf("routineEntry", "activationEntry", "occurrenceEntry")
    )

    override val version: Long get() = HydraFitDatabase.Schema.version

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> =
        HydraFitDatabase.Schema.create(driver)

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> {
        var fromVersion = oldVersion
        rebuildTables.forEach { (rebuildVersion, tables) ->
            if (fromVersion <= rebuildVersion && newVersion > rebuildVersion) {
                if (fromVersion < rebuildVersion) {
                    HydraFitDatabase.Schema.migrate(
                        driver,
                        fromVersion,
                        rebuildVersion,
                        *callbacks.filter {
                            it.afterVersion >= fromVersion && it.afterVersion < rebuildVersion
                        }.toTypedArray()
                    )
                }
                val marks = tables.associateWith { readAutoincrementMark(driver, it) }
                HydraFitDatabase.Schema.migrate(
                    driver,
                    rebuildVersion,
                    rebuildVersion + 1,
                    AfterVersion(rebuildVersion) { restoringDriver ->
                        marks.forEach { (table, mark) ->
                            restoreAutoincrementMark(restoringDriver, table, mark)
                        }
                    },
                    *callbacks.filter { it.afterVersion == rebuildVersion }.toTypedArray()
                )
                fromVersion = rebuildVersion + 1
            }
        }
        if (fromVersion < newVersion) {
            HydraFitDatabase.Schema.migrate(
                driver,
                fromVersion,
                newVersion,
                *callbacks.filter { it.afterVersion >= fromVersion }.toTypedArray()
            )
        }
        return QueryResult.Unit
    }

    /**
     * The stored AUTOINCREMENT high-water mark for `workoutSet`, or `null` when the table never
     * held a row (there is no `sqlite_sequence` entry to preserve). Read with raw SQL so no
     * generated query has to know the old column set.
     */
    private fun readAutoincrementMark(driver: SqlDriver, table: String): Long? {
        if (!sqliteSequenceExists(driver)) return null
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT seq FROM sqlite_sequence WHERE name = ?",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
            },
            parameters = 1,
            binders = { bindString(0, table) }
        ).value
    }

    /**
     * Restores [highWaterMark] after the rebuild, never lowering a larger mark the rebuilt table
     * already holds (for example, a copied row with a higher id). A `null` mark means there was
     * nothing to preserve.
     */
    private fun restoreAutoincrementMark(driver: SqlDriver, table: String, highWaterMark: Long?) {
        if (highWaterMark == null || !sqliteSequenceExists(driver)) return
        val current = driver.executeQuery(
            identifier = null,
            sql = "SELECT seq FROM sqlite_sequence WHERE name = ?",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
            },
            parameters = 1,
            binders = { bindString(0, table) }
        ).value
        if (current != null && current >= highWaterMark) return
        if (current == null) {
            driver.execute(
                identifier = null,
                sql = "INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)",
                parameters = 2,
                binders = {
                    bindString(0, table)
                    bindLong(1, highWaterMark)
                }
            )
        } else {
            driver.execute(
                identifier = null,
                sql = "UPDATE sqlite_sequence SET seq = ? WHERE name = ?",
                parameters = 2,
                binders = {
                    bindLong(0, highWaterMark)
                    bindString(1, table)
                }
            )
        }
    }

    /** `sqlite_sequence` exists only once an AUTOINCREMENT table has been created. */
    private fun sqliteSequenceExists(driver: SqlDriver): Boolean = driver.executeQuery(
        identifier = null,
        sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'",
        mapper = { cursor ->
            QueryResult.Value(cursor.next().value)
        },
        parameters = 0
    ).value
}
