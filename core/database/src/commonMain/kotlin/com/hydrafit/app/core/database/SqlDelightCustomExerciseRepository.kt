package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.CustomExerciseException
import com.hydrafit.app.core.userdata.equipment.CustomExerciseFailureReason
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository

class SqlDelightCustomExerciseRepository(private val database: HydraFitDatabase) :
    CustomExerciseRepository {
    private val queries = database.exerciseQueries
    private val equipmentQueries = database.equipmentQueries

    override suspend fun add(
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        involvements: Map<MuscleGroup, Double>,
        movementPattern: MovementPattern,
        isUnilateral: Boolean,
        loadCapability: ExerciseLoadCapability
    ): Exercise {
        val trimmed = validate(
            id = null,
            name = name,
            requiredEquipment = requiredEquipment,
            involvements = involvements
        )
        val id = uniqueId(trimmed)
        database.transaction {
            queries.insertCustom(
                id = id,
                name = trimmed,
                requiredEquipment = encodeEquipment(requiredEquipment),
                movementPattern = movementPattern.name,
                isUnilateral = if (isUnilateral) 1L else 0L,
                loadCapability = loadCapability.name,
                involvements = encodeInvolvements(involvements)
            )
            // The stored projection is the effective name, so a restored rename of this custom follows.
            database.refreshExerciseSearchName(id)
        }
        return Exercise(
            id = id,
            name = trimmed,
            requiredEquipment = requiredEquipment,
            primaryMuscles = involvements.filterValues { it >= PRIMARY_THRESHOLD }.keys,
            secondaryMuscles = involvements.filterValues { it < PRIMARY_THRESHOLD }.keys,
            movementPattern = movementPattern,
            isCustom = true,
            isUnilateral = isUnilateral,
            loadCapability = loadCapability,
            involvements = involvements
        )
    }

    override suspend fun update(
        id: String,
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        involvements: Map<MuscleGroup, Double>,
        movementPattern: MovementPattern,
        isUnilateral: Boolean,
        loadCapability: ExerciseLoadCapability
    ) {
        val trimmed = validate(
            id = id,
            name = name,
            requiredEquipment = requiredEquipment,
            involvements = involvements
        )
        database.transaction {
            queries.updateCustom(
                name = trimmed,
                requiredEquipment = encodeEquipment(requiredEquipment),
                movementPattern = movementPattern.name,
                isUnilateral = if (isUnilateral) 1L else 0L,
                loadCapability = loadCapability.name,
                involvements = encodeInvolvements(involvements),
                id = id
            )
            // Renaming a custom must not put its own name over a restored override's name.
            database.refreshExerciseSearchName(id)
        }
    }

    override suspend fun delete(id: String) {
        database.transaction {
            // Only a custom row may be deleted; a built-in id must never clear its dependent rows,
            // because deleteById is a no-op for it (isCustom = 0) and the exercise would survive.
            val existing = queries.selectById(id).executeAsOneOrNull()
            if (existing != null && existing.isCustom != 1L) {
                throw CustomExerciseException("Only custom exercises can be deleted")
            }
            val references = queries.countSetsForExercise(id).executeAsOne()
            if (references > 0L) {
                throw CustomExerciseException("This exercise has logged sets and can't be deleted")
            }
            if (database.personalRecordQueries.selectById(id).executeAsOneOrNull() != null) {
                throw CustomExerciseException(
                    "This exercise has a personal record and can't be deleted",
                    CustomExerciseFailureReason.PERSONAL_RECORD_REFERENCE
                )
            }
            val planReferences =
                database.planHistoryQueries.countEntriesForExercise(id).executeAsOne()
            val routineReferences =
                database.routineTemplateQueries.countRoutineEntriesForExercise(id).executeAsOne()
            val activationReferences =
                database.trainingScheduleQueries.countActivationEntriesForExercise(id)
                    .executeAsOne()
            val occurrenceReferences =
                database.trainingScheduleQueries.countOccurrenceEntriesForExercise(id)
                    .executeAsOne()
            if (
                planReferences + routineReferences + activationReferences + occurrenceReferences >
                0L
            ) {
                throw CustomExerciseException(
                    "This exercise is used by a plan or routine and can't be deleted"
                )
            }
            // Every exercise-keyed row must go in the same transaction, or an export would carry a
            // reference the backup validator rejects. `exerciseOverride` is normally built-in-only
            // but is defensively cleared here for restored/legacy rows, mirroring CustomExerciseDedupe.
            database.exerciseOverrideQueries.deleteById(id)
            database.restPreferenceQueries.deleteExerciseOverride(id)
            database.exercisePreferenceQueries.deleteById(id)
            database.exerciseExclusionQueries.deleteById(id)
            database.exerciseNoteQueries.deleteById(id)
            queries.deleteById(id)
        }
    }

    private fun validate(
        id: String?,
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        involvements: Map<MuscleGroup, Double>
    ): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw CustomExerciseException("Name must not be blank")
        if (involvements.isEmpty()) {
            throw CustomExerciseException("Pick at least one muscle")
        }
        val existingIds = equipmentQueries.selectAll().executeAsList().map { it.id }.toSet()
        val unknown = requiredEquipment.filterNot { it.id in existingIds }
        if (unknown.isNotEmpty()) {
            val names = unknown.joinToString { it.id }
            throw CustomExerciseException("Unknown equipment: $names")
        }
        val rows = queries.selectAll().executeAsList()
        val current = rows.firstOrNull { it.id == id }
        val protectedSeeds = DefaultExercisesCatalogP7.all + DefaultExercisesCatalog081.all
        val retainedProtectedCollision = current?.isCustom == 1L &&
            protectedSeeds.any { seeded ->
                normalizeExerciseName(seeded.name) == normalizeExerciseName(current.name) &&
                    normalizeExerciseName(seeded.name) == normalizeExerciseName(trimmed)
            }
        val duplicateName = rows.any { row ->
            row.id != id &&
                row.name.equals(trimmed, ignoreCase = true) &&
                !(retainedProtectedCollision && row.id in protectedSeeds.map { it.id })
        }
        // Match precisely the existing startup merge rule, not search/alias separator equivalence.
        val seededIdentity = DefaultExercises.all.any { canonical ->
            normalizeExerciseName(canonical.name) == normalizeExerciseName(trimmed)
        }
        if (duplicateName || (seededIdentity && !retainedProtectedCollision)) {
            throw CustomExerciseException(
                "An exercise named \"$trimmed\" already exists",
                CustomExerciseFailureReason.NAME_CONFLICT
            )
        }
        return trimmed
    }

    private fun uniqueId(name: String): String {
        val base = "user-" + name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
        val taken = queries.selectAll().executeAsList().map { it.id }.toSet()
        if (base !in taken) return base
        var suffix = 2
        while ("$base-$suffix" in taken) suffix++
        return "$base-$suffix"
    }

    private companion object {
        const val PRIMARY_THRESHOLD = 0.7
    }
}
