package com.hydrafit.app.core.database

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * The production schema entry point: the generated [HydraFitDatabase.Schema] plus one targeted
 * repair of the `52.sqm` `workoutSet` rebuild.
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
 * schema, and a migration that does not cross 52 -> 53 is passed through with its callbacks intact.
 * The repair reads and writes the `sqlite_sequence` internal table with raw SQL (SQLDelight's
 * resolver rejects that table inside a `.sqm`), so no generated query depends on the final columns,
 * and the caller's migration transaction and error propagation are preserved.
 */
internal object HydraFitDatabaseSchema : SqlSchema<QueryResult.Value<Unit>> {

    /** `52.sqm` migrates 52 -> 53; this is the rebuild whose high-water mark is repaired. */
    private const val TIMED_SET_REBUILD_VERSION = 52L

    private const val WORKOUT_SET = "workoutSet"

    override val version: Long get() = HydraFitDatabase.Schema.version

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> =
        HydraFitDatabase.Schema.create(driver)

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> {
        if (oldVersion > TIMED_SET_REBUILD_VERSION || newVersion <= TIMED_SET_REBUILD_VERSION) {
            return HydraFitDatabase.Schema.migrate(driver, oldVersion, newVersion, *callbacks)
        }

        // Reach version 52 first: the mark must be read after every earlier migration, so a
        // multi-hop upgrade captures the value at the rebuild rather than one from before the
        // historical `20.sqm` rebuild, and `workoutSet` still has its pre-53 shape.
        val beforeRebuild = callbacks.filter { it.afterVersion < TIMED_SET_REBUILD_VERSION }
        if (oldVersion < TIMED_SET_REBUILD_VERSION) {
            HydraFitDatabase.Schema.migrate(
                driver,
                oldVersion,
                TIMED_SET_REBUILD_VERSION,
                *beforeRebuild.toTypedArray()
            )
        }
        val highWaterMark = readAutoincrementMark(driver)

        // Run the rebuild (and any later migration) with a hook that restores the mark as soon as
        // `52.sqm` has completed, ahead of the caller's own post-rebuild callbacks.
        val afterRebuild = callbacks.filter { it.afterVersion >= TIMED_SET_REBUILD_VERSION }
        HydraFitDatabase.Schema.migrate(
            driver,
            TIMED_SET_REBUILD_VERSION,
            newVersion,
            AfterVersion(TIMED_SET_REBUILD_VERSION) { restoringDriver ->
                restoreAutoincrementMark(restoringDriver, highWaterMark)
            },
            *afterRebuild.toTypedArray()
        )
        return QueryResult.Unit
    }

    /**
     * The stored AUTOINCREMENT high-water mark for `workoutSet`, or `null` when the table never
     * held a row (there is no `sqlite_sequence` entry to preserve). Read with raw SQL so no
     * generated query has to know the old column set.
     */
    private fun readAutoincrementMark(driver: SqlDriver): Long? {
        if (!sqliteSequenceExists(driver)) return null
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT seq FROM sqlite_sequence WHERE name = '$WORKOUT_SET'",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
            },
            parameters = 0
        ).value
    }

    /**
     * Restores [highWaterMark] after the rebuild, never lowering a larger mark the rebuilt table
     * already holds (for example, a copied row with a higher id). A `null` mark means there was
     * nothing to preserve.
     */
    private fun restoreAutoincrementMark(driver: SqlDriver, highWaterMark: Long?) {
        if (highWaterMark == null || !sqliteSequenceExists(driver)) return
        val current = driver.executeQuery(
            identifier = null,
            sql = "SELECT seq FROM sqlite_sequence WHERE name = '$WORKOUT_SET'",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
            },
            parameters = 0
        ).value
        if (current != null && current >= highWaterMark) return
        if (current == null) {
            driver.execute(
                identifier = null,
                sql = "INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)",
                parameters = 2,
                binders = {
                    bindString(0, WORKOUT_SET)
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
                    bindString(1, WORKOUT_SET)
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
