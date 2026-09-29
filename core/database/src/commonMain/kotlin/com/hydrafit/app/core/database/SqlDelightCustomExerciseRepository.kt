package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.CustomExerciseException
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository

class SqlDelightCustomExerciseRepository(private val database: HydraFitDatabase) :
    CustomExerciseRepository {
    private val queries = database.exerciseQueries
    private val equipmentQueries = database.equipmentQueries

    override suspend fun add(
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
        movementPattern: MovementPattern
    ): Exercise {
        val trimmed = validate(
            id = null,
            name = name,
            requiredEquipment = requiredEquipment,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles
        )
        val id = uniqueId(trimmed)
        queries.insertCustom(
            id = id,
            name = trimmed,
            requiredEquipment = encodeEquipment(requiredEquipment),
            primaryMuscles = encodeMuscles(primaryMuscles),
            secondaryMuscles = encodeMuscles(secondaryMuscles),
            movementPattern = movementPattern.name
        )
        return Exercise(
            id = id,
            name = trimmed,
            requiredEquipment = requiredEquipment,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            movementPattern = movementPattern,
            isCustom = true
        )
    }

    override suspend fun update(
        id: String,
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
        movementPattern: MovementPattern
    ) {
        val trimmed = validate(
            id = id,
            name = name,
            requiredEquipment = requiredEquipment,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles
        )
        queries.updateCustom(
            name = trimmed,
            requiredEquipment = encodeEquipment(requiredEquipment),
            primaryMuscles = encodeMuscles(primaryMuscles),
            secondaryMuscles = encodeMuscles(secondaryMuscles),
            movementPattern = movementPattern.name,
            id = id
        )
    }

    override suspend fun delete(id: String) {
        val references = queries.countSetsForExercise(id).executeAsOne()
        if (references > 0L) {
            throw CustomExerciseException("This exercise has logged sets and can't be deleted")
        }
        queries.deleteById(id)
    }

    private fun validate(
        id: String?,
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>
    ): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw CustomExerciseException("Name must not be blank")
        if (primaryMuscles.isEmpty()) {
            throw CustomExerciseException("Pick at least one primary muscle")
        }
        if (primaryMuscles.any { it in secondaryMuscles }) {
            throw CustomExerciseException("A muscle can't be both primary and secondary")
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
        if (duplicateName) {
            throw CustomExerciseException("An exercise named \"$trimmed\" already exists")
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
}
