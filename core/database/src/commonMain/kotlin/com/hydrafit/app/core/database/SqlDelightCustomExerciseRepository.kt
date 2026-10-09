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
        queries.insertCustom(
            id = id,
            name = trimmed,
            requiredEquipment = encodeEquipment(requiredEquipment),
            movementPattern = movementPattern.name,
            isUnilateral = if (isUnilateral) 1L else 0L,
            loadCapability = loadCapability.name,
            involvements = encodeInvolvements(involvements)
        )
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
        queries.updateCustom(
            name = trimmed,
            requiredEquipment = encodeEquipment(requiredEquipment),
            movementPattern = movementPattern.name,
            isUnilateral = if (isUnilateral) 1L else 0L,
            loadCapability = loadCapability.name,
            involvements = encodeInvolvements(involvements),
            id = id
        )
    }

    override suspend fun delete(id: String) {
        val references = queries.countSetsForExercise(id).executeAsOne()
        if (references > 0L) {
            throw CustomExerciseException("This exercise has logged sets and can't be deleted")
        }
        val planReferences = database.planHistoryQueries.countEntriesForExercise(id).executeAsOne()
        val routineReferences =
            database.routineTemplateQueries.countRoutineEntriesForExercise(id).executeAsOne()
        val activationReferences =
            database.trainingScheduleQueries.countActivationEntriesForExercise(id).executeAsOne()
        val occurrenceReferences =
            database.trainingScheduleQueries.countOccurrenceEntriesForExercise(id).executeAsOne()
        if (planReferences + routineReferences + activationReferences + occurrenceReferences > 0L) {
            throw CustomExerciseException(
                "This exercise is used by a plan or routine and can't be deleted"
            )
        }
        queries.deleteById(id)
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
        val duplicateName = queries.selectAll().executeAsList().any { row ->
            row.id != id && row.name.equals(trimmed, ignoreCase = true)
        }
        // Match precisely the existing startup merge rule, not search/alias separator equivalence.
        val seededIdentity = DefaultExercises.all.any { canonical ->
            normalizeExerciseName(canonical.name) == normalizeExerciseName(trimmed)
        }
        if (duplicateName || seededIdentity) {
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
