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
 * Every writer that can change an exercise's displayed name calls this: the seed catalog, the
 * custom-exercise repository (add and rename), the custom-exercise restore path, and the override
 * update/reset paths. The custom-exercise dedupe merge carries the existing override `name` forward
 * unchanged, so it cannot change a projection and does not call this.
 * [SearchProjectionBackfill] remains the self-healing rebuild for rows written before the projection
 * existed and for any drift.
 */
internal fun HydraFitDatabase.refreshExerciseSearchName(exerciseId: String) {
    val baseName = exerciseQueries.selectById(exerciseId).executeAsOneOrNull()?.name ?: return
    val overrideName = exerciseOverrideQueries.selectById(exerciseId).executeAsOneOrNull()?.name
    exerciseQueries.updateSearchName(
        searchName = unicodeCaseFold(overrideName ?: baseName),
        id = exerciseId
    )
}
