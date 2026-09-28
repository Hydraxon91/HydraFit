package com.hydrafit.app.core.domain.equipment

class FilterExercisesByEquipmentUseCase {
    operator fun invoke(
        exercises: List<Exercise>,
        availableEquipment: Set<EquipmentTag>
    ): List<Exercise> = exercises.filter { it.isAvailableWith(availableEquipment) }
}
