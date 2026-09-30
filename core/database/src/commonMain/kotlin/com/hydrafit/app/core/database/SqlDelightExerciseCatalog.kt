package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SqlDelightExerciseCatalog(database: HydraFitDatabase) : ExerciseCatalog {
    private val queries = database.exerciseQueries
    private val overrideQueries = database.exerciseOverrideQueries

    override suspend fun all(): List<Exercise> {
        val overrides = overrideQueries.selectAll().executeAsList()
            .associateBy { it.exerciseId }
        return queries.selectAll().executeAsList().map { row ->
            row.toDomain(overrides[row.id])
        }
    }

    override fun observeAll(): Flow<List<Exercise>> = combine(
        queries.selectAll().asFlow().mapToList(Dispatchers.Default),
        overrideQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
    ) { rows, overrideRows ->
        val overrides = overrideRows.associateBy { it.exerciseId }
        rows.map { row -> row.toDomain(overrides[row.id]) }
    }

    private fun com.hydrafit.app.core.database.Exercise.toDomain(
        override: ExerciseOverride?
    ): Exercise {
        val resolved = decodeInvolvements(override?.involvements ?: involvements)
        return Exercise(
            id = id,
            name = override?.name ?: name,
            requiredEquipment = decodeEquipment(override?.requiredEquipment ?: requiredEquipment),
            primaryMuscles = resolved.filterValues { it >= PRIMARY_THRESHOLD }.keys,
            secondaryMuscles = resolved.filterValues { it < PRIMARY_THRESHOLD }.keys,
            movementPattern = decodeMovementPattern(override?.movementPattern ?: movementPattern),
            isCustom = isCustom != 0L,
            isUnilateral = override?.isUnilateral?.let { it != 0L } ?: (isUnilateral != 0L),
            involvements = resolved
        )
    }

    private companion object {
        /** Involvement weight at or above which a muscle counts as a "primary" (display tag). */
        const val PRIMARY_THRESHOLD = 0.7
    }
}
