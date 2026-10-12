package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.unicodeCaseFold

/**
 * Rebuilds the case-folded search projections for every exercise row and session note, so a
 * database upgraded from before migration `42` never serves a stale (empty) projection, and so a
 * stored projection always matches the rule it is written by. Rebuilding every exercise row also
 * covers a built-in row that is no longer in the current seed catalog, which
 * [SeedExerciseCatalog] alone would miss, and recomputing from the *effective* name covers a
 * built-in a user has renamed. Runs once at startup, in one transaction, before
 * `StartupReadiness` reports ready.
 */
class SearchProjectionBackfill(private val database: HydraFitDatabase) {
    fun run() {
        database.transaction {
            // `exerciseOverride.name` is null when the override does not rename the exercise.
            val overrideNames = database.exerciseOverrideQueries.selectAll()
                .executeAsList()
                .associate { it.exerciseId to it.name }
            database.exerciseQueries.selectAll()
                .executeAsList()
                .forEach { exercise ->
                    database.exerciseQueries.updateSearchName(
                        searchName = unicodeCaseFold(overrideNames[exercise.id] ?: exercise.name),
                        id = exercise.id
                    )
                }
            database.sessionNoteQueries.selectAll().executeAsList().forEach { note ->
                database.sessionNoteQueries.updateSearchNote(
                    searchNote = unicodeCaseFold(note.note),
                    sessionId = note.sessionId
                )
            }
        }
    }
}
