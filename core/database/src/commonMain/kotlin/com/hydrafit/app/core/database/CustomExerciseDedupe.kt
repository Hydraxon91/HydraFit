package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.Exercise as CatalogExercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshotJson
import kotlin.math.abs

private const val WEIGHT_EPSILON = 1e-9

internal fun normalizeExerciseName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase()

class CustomExerciseDedupe(private val database: HydraFitDatabase) {
    fun run() {
        val protectedP7Ids = DefaultExercisesCatalogP7.all.map { it.id }.toSet()
        val protected081Ids = DefaultExercisesCatalog081.all.map { it.id }.toSet()
        val protectedManualIds = DefaultExercisesCatalogManual.all.map { it.id }.toSet()
        // New/clarified calf labels must not merge a pre-existing same-name custom identity.
        val protectedCalfIds = setOf("standing-calf-raise", "standing-calf-raise-machine")
        val canonicalByName = DefaultExercises.all
            .filterNot {
                it.id in protectedP7Ids ||
                    it.id in protectedCalfIds ||
                    it.id in protected081Ids ||
                    it.id in protectedManualIds
            }
            .associateBy { normalizeExerciseName(it.name) }
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
                remapRecordedTargets(custom.id, canonical.id)
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
                mergeRestOverride(custom.id, canonical.id)
                mergeNote(custom.id, canonical.id)
                database.exerciseOverrideQueries.deleteById(custom.id)
                database.exerciseQueries.deleteById(custom.id)
            }
        }
    }

    /**
     * A recorded target names its exercise, so merging a custom identity must remap it too: otherwise
     * the set would claim one exercise while its target claimed the deleted one, which breaks both
     * progression eligibility and the backup validator. Only the identity is rewritten; the recorded
     * prescription itself is untouched.
     */
    private fun remapRecordedTargets(customId: String, canonicalId: String) {
        database.workoutLogQueries.selectAllSets().executeAsList()
            .filter { it.prescriptionSnapshot != null }
            .forEach { row ->
                val encoded = row.prescriptionSnapshot ?: return@forEach
                val target = runCatching { WorkoutPrescriptionSnapshotJson.decode(encoded) }
                    .getOrNull() ?: return@forEach
                if (target.exerciseId != customId) return@forEach
                database.workoutLogQueries.updatePrescriptionSnapshot(
                    prescriptionSnapshot = WorkoutPrescriptionSnapshotJson.encode(
                        target.copy(exerciseId = canonicalId)
                    ),
                    id = row.id
                )
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

    /**
     * Moves an explicit exercise rest-duration override onto the merged seeded id. When the canonical
     * id already has an override that choice wins and the custom row is discarded; otherwise the
     * custom override is reassigned, mirroring [mergePreference].
     */
    private fun mergeRestOverride(customId: String, canonicalId: String) {
        val duration = database.restPreferenceQueries.selectExerciseOverride(customId)
            .executeAsOneOrNull() ?: return
        val canonicalExists = database.restPreferenceQueries.selectExerciseOverride(canonicalId)
            .executeAsOneOrNull() != null
        if (!canonicalExists) {
            database.restPreferenceQueries.upsertExerciseOverride(
                canonicalId,
                canonicalId,
                duration
            )
        }
        database.restPreferenceQueries.deleteExerciseOverride(customId)
    }

    /**
     * Moves a saved setup note onto the merged seeded id. When the canonical id already has a note
     * row (including an explicit clear), that entry wins and the custom note is discarded;
     * otherwise the custom note is reassigned, mirroring [mergePreference].
     */
    private fun mergeNote(customId: String, canonicalId: String) {
        val hasCustomNote =
            database.exerciseNoteQueries.selectById(customId).executeAsOneOrNull() != null
        if (!hasCustomNote) return
        val canonicalNote =
            database.exerciseNoteQueries.selectById(canonicalId).executeAsOneOrNull()
        if (canonicalNote == null) {
            database.exerciseNoteQueries.updateExerciseId(newId = canonicalId, oldId = customId)
        } else {
            database.exerciseNoteQueries.deleteById(customId)
        }
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
