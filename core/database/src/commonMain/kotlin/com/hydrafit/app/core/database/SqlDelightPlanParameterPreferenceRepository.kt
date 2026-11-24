package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.AutomaticSetPrescription
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SessionLength
import com.hydrafit.app.core.domain.engine.SetPrescriptionShape
import com.hydrafit.app.core.userdata.settings.PlanParameterPreferenceRepository
import com.hydrafit.app.core.userdata.settings.PlanParameterPreferences

class SqlDelightPlanParameterPreferenceRepository(database: HydraFitDatabase) :
    PlanParameterPreferenceRepository {
    private val queries = database.plannerEngineQueries

    override suspend fun current(): PlanParameterPreferences {
        val row = queries.selectPlanParameters().executeAsOneOrNull()
            ?: return PlanParameterPreferences()
        val shape = row.autoTargetShape?.let { stored ->
            SetPrescriptionShape.entries.firstOrNull { it.name == stored }
        }
        val minReps = row.autoTargetMinReps?.toInt()
        val maxReps = row.autoTargetMaxReps?.toInt()
        // A stored opt-in that no longer validates (an unknown shape or an out-of-range value) is
        // treated as "not chosen" instead of failing the split builder.
        val automatic = if (shape != null && minReps != null && maxReps != null) {
            AutomaticSetPrescription(
                shape = shape,
                minReps = minReps,
                maxReps = maxReps,
                targetRir = row.autoTargetRir?.toInt()
            ).takeIf { runCatching { it.requireValid() }.isSuccess }
        } else {
            null
        }
        return PlanParameterPreferences(
            setsPerExercise = row.setsPerExercise?.toInt(),
            accessorySetsPerExercise = row.accessorySetsPerExercise?.toInt(),
            automaticSetPrescription = automatic,
            // A stored value that is no longer a known length is treated as "not chosen".
            sessionLength = row.sessionLength?.let { stored ->
                SessionLength.entries.firstOrNull { it.name == stored }
            }
        )
    }

    override suspend fun setSessionLength(length: SessionLength?) {
        queries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        queries.updateSessionLength(length?.name)
    }

    override suspend fun setSetsPerExercise(sets: Int?) {
        queries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        queries.updateSetsPerExercise(sets?.toLong())
    }

    override suspend fun setAccessorySetsPerExercise(sets: Int?) {
        queries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        queries.updateAccessorySetsPerExercise(sets?.toLong())
    }

    override suspend fun setAutomaticSetPrescription(prescription: AutomaticSetPrescription?) {
        queries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        queries.updateAutoTarget(
            autoTargetShape = prescription?.shape?.name,
            autoTargetMinReps = prescription?.minReps?.toLong(),
            autoTargetMaxReps = prescription?.maxReps?.toLong(),
            autoTargetRir = prescription?.targetRir?.toLong()
        )
    }
}
