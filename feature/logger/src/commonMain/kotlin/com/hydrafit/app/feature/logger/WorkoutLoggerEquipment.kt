package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.WarmupEquipment
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The equipment the Logger's warm-up guidance is bounded by: which tags the user owns and the
 * heaviest weight each provides. Grouped into one cohesive collaborator so the ViewModel gains a
 * single dependency rather than one per repository; it owns no state of its own.
 */
class WorkoutLoggerEquipment(
    private val equipmentRepository: EquipmentRepository,
    private val selectionRepository: EquipmentSelectionRepository
) {
    /** Current availability and ceilings, refreshed whenever the inventory or selection changes. */
    fun equipmentFlow(): Flow<WarmupEquipment> = combine(
        equipmentRepository.observeAll(),
        selectionRepository.selectedFlow()
    ) { equipment, selected ->
        WarmupEquipment(
            selectedTags = selected,
            maxWeights = equipment.mapNotNull { item ->
                item.maxWeightKg?.let { item.id to it }
            }.toMap()
        )
    }
}
