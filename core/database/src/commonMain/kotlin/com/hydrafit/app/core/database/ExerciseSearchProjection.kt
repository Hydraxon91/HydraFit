package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.unicodeCaseFold

/**
 * Keeps `exercise.searchName` in step with the name the app actually shows.
 *
 * The projection is the folded **effective** display name: the catalog's own name, or the name a user
 * override gives it (`SqlDelightExerciseCatalog` resolves the same rule with
 * `override?.name ?: name`). History search matches this projection, so a renamed built-in must be
 * findable by the name the user sees.
 *
 * Only the runtime override write paths call this. The merge paths that run inside startup
 * maintenance (custom-exercise dedupe) are healed by [SearchProjectionBackfill], which recomputes the
 * same rule for every row and runs in the same pass, before `StartupReadiness` reports ready.
 */
internal fun HydraFitDatabase.refreshExerciseSearchName(exerciseId: String) {
    val baseName = exerciseQueries.selectById(exerciseId).executeAsOneOrNull()?.name ?: return
    val overrideName = exerciseOverrideQueries.selectById(exerciseId).executeAsOneOrNull()?.name
    exerciseQueries.updateSearchName(
        searchName = unicodeCaseFold(overrideName ?: baseName),
        id = exerciseId
    )
}
