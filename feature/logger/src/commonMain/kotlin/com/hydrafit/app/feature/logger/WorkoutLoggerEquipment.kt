package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.workout.WarmupEquipment
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The equipment the Logger's warm-up guidance is bounded by: which tags the user owns and the
 * heaviest weight each provides. Grouped into one cohesive collaborator so the ViewModel gains a
 * single dependency rather than one per repository; it owns no state of its own.
 */
class WorkoutLoggerEquipment(private val profiles: EquipmentProfileRepository) {
    /** Current availability and ceilings, refreshed whenever the inventory or selection changes. */
    fun equipmentFlow(): Flow<WarmupEquipment> = profiles.observe().map { state ->
        WarmupEquipment(
            selectedTags = state.active.selected,
            maxWeights = state.active.maxWeightsKg
        )
    }

    /** Stations belong to the active profile and follow profile switches and edits. */
    fun stationsFlow(): Flow<List<EquipmentStation>> = profiles.observe().map { state ->
        state.active.stations
    }
}
