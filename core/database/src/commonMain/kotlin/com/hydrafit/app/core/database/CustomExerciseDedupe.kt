package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.Exercise as CatalogExercise
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
                updatedAt = customRecord.updatedAt
            )
        }
        database.personalRecordQueries.deleteById(customId)
    }

    private fun mergeOverride(custom: Exercise, canonical: CatalogExercise) {
        val existing =
            database.exerciseOverrideQueries.selectById(canonical.id).executeAsOneOrNull()
        val name = existing?.name
        var equipment = existing?.requiredEquipment
        var pattern = existing?.movementPattern
        var unilateral = existing?.isUnilateral
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
            involvements = involvements
        )
    }
}
