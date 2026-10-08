package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.Exercise as CatalogExercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import kotlin.math.abs

private const val WEIGHT_EPSILON = 1e-9

internal fun normalizeExerciseName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase()

class CustomExerciseDedupe(private val database: HydraFitDatabase) {
    fun run() {
        val canonicalByName = DefaultExercises.all.associateBy { normalizeExerciseName(it.name) }
        val customRows = database.exerciseQueries.selectAll().executeAsList()
            .filter { it.isCustom == 1L }
        val merges = customRows.mapNotNull { row ->
            val canonical = canonicalByName[normalizeExerciseName(row.name)]
                ?.takeIf { it.id != row.id }
                ?: return@mapNotNull null
            row to canonical
        }
        if (merges.isEmpty()) return
        database.transaction {
            merges.forEach { (custom, canonical) ->
                database.workoutLogQueries.updateSetExerciseId(
                    newId = canonical.id,
                    oldId = custom.id
                )
                database.planHistoryQueries.updateEntryExerciseId(
                    newId = canonical.id,
                    oldId = custom.id
                )
                database.routineTemplateQueries.updateRoutineEntryExerciseId(
                    newId = canonical.id,
                    oldId = custom.id
                )
                // Frozen activation/occurrence snapshots keep their recorded name but follow the
                // canonical id, matching how accepted-plan history is remapped.
                database.trainingScheduleQueries.updateActivationEntryExerciseId(
                    newId = canonical.id,
                    oldId = custom.id
                )
                database.trainingScheduleQueries.updateOccurrenceEntryExerciseId(
                    newId = canonical.id,
                    oldId = custom.id
                )
                mergePersonalRecord(custom.id, canonical.id)
                mergeOverride(custom, canonical)
                mergePreference(custom.id, canonical.id)
                mergeExclusion(custom.id, canonical.id)
                database.exerciseOverrideQueries.deleteById(custom.id)
                database.exerciseQueries.deleteById(custom.id)
            }
        }
    }

    private fun mergePersonalRecord(customId: String, canonicalId: String) {
        val customRecord = database.personalRecordQueries.selectById(customId).executeAsOneOrNull()
            ?: return
        val canonicalRecord =
            database.personalRecordQueries.selectById(canonicalId).executeAsOneOrNull()
        if (canonicalRecord == null) {
            database.personalRecordQueries.updateExerciseId(newId = canonicalId, oldId = customId)
            return
        }
        val sameWeight = abs(customRecord.weightKg - canonicalRecord.weightKg) < WEIGHT_EPSILON
        val heavier = customRecord.weightKg > canonicalRecord.weightKg + WEIGHT_EPSILON
        val moreReps = sameWeight && customRecord.reps > canonicalRecord.reps
        val sameReps = sameWeight && customRecord.reps == canonicalRecord.reps
        val newer = customRecord.updatedAt > canonicalRecord.updatedAt
        if (heavier || moreReps || (sameReps && newer)) {
            database.personalRecordQueries.upsert(
                exerciseId = canonicalId,
                weightKg = customRecord.weightKg,
                reps = customRecord.reps,
                updatedAt = customRecord.updatedAt,
                loadKind = customRecord.loadKind
            )
        }
        database.personalRecordQueries.deleteById(customId)
    }

    /**
     * Moves an explicit preference onto the merged seeded id. When the canonical id already has an
     * explicit preference (including an explicitly stored NEUTRAL), that choice wins and the custom
     * row is discarded; otherwise the custom preference is reassigned.
     */
    private fun mergePreference(customId: String, canonicalId: String) {
        val hasCustomPreference =
            database.exercisePreferenceQueries.selectById(customId).executeAsOneOrNull() != null
        if (!hasCustomPreference) return
        val canonicalPreference =
            database.exercisePreferenceQueries.selectById(canonicalId).executeAsOneOrNull()
        if (canonicalPreference == null) {
            database.exercisePreferenceQueries.updateExerciseId(
                newId = canonicalId,
                oldId = customId
            )
        } else {
            database.exercisePreferenceQueries.deleteById(customId)
        }
    }

    /**
     * Moves an exclusion onto the merged seeded id. An indefinite exclusion wins over a dated one
     * (the more protective choice); between two dated exclusions the later expiry wins.
     */
    private fun mergeExclusion(customId: String, canonicalId: String) {
        val custom =
            database.exerciseExclusionQueries.selectById(customId).executeAsOneOrNull() ?: return
        val canonical =
            database.exerciseExclusionQueries.selectById(canonicalId).executeAsOneOrNull()
        if (canonical == null) {
            database.exerciseExclusionQueries.updateExerciseId(
                newId = canonicalId,
                oldId = customId
            )
            return
        }
        val customWins = custom.expiresAt == null ||
            (canonical.expiresAt != null && custom.expiresAt > canonical.expiresAt)
        if (customWins) {
            database.exerciseExclusionQueries.upsert(
                exerciseId = canonicalId,
                expiresAt = custom.expiresAt
            )
        }
        database.exerciseExclusionQueries.deleteById(customId)
    }

    private fun mergeOverride(custom: Exercise, canonical: CatalogExercise) {
        val existing =
            database.exerciseOverrideQueries.selectById(canonical.id).executeAsOneOrNull()
        val name = existing?.name
        var equipment = existing?.requiredEquipment
        var pattern = existing?.movementPattern
        var unilateral = existing?.isUnilateral
        var loadCapability = existing?.loadCapability
        var involvements = existing?.involvements
        var changed = false

        val customEquipment = decodeEquipment(custom.requiredEquipment)
        if (customEquipment.isNotEmpty() && customEquipment != canonical.requiredEquipment) {
            equipment = custom.requiredEquipment
            changed = true
        }
        val customPattern = decodeMovementPattern(custom.movementPattern)
        if (customPattern != MovementPattern.CORE && customPattern != canonical.movementPattern) {
            pattern = custom.movementPattern
            changed = true
        }
        if (custom.isUnilateral != 0L && !canonical.isUnilateral) {
            unilateral = custom.isUnilateral
            changed = true
        }
        // Preserve an explicit custom capability selection, but only when it differs from the
        // canonical built-in (matching the "materialize differences only" rule above). A legacy
        // custom row still carrying UNSPECIFIED never overwrites the curated default.
        if (custom.loadCapability != ExerciseLoadCapability.UNSPECIFIED.name &&
            custom.loadCapability != canonical.loadCapability.name
        ) {
            loadCapability = custom.loadCapability
            changed = true
        }
        if (custom.involvements != null &&
            decodeInvolvements(custom.involvements) != canonical.effectiveInvolvements
        ) {
            involvements = custom.involvements
            changed = true
        }
        if (!changed) return
        database.exerciseOverrideQueries.upsert(
            exerciseId = canonical.id,
            name = name,
            requiredEquipment = equipment,
            movementPattern = pattern,
            isUnilateral = unilateral,
            loadCapability = loadCapability,
            involvements = involvements
        )
    }
}
