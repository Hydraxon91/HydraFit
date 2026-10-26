package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.equipment.EquipmentIdentity

/** Repairs only declared custom equipment that occupies a receiver's reserved built-in identity. */
internal fun BackupFile.withNonConflictingEquipment(reservedIds: Set<String>): BackupFile {
    if (equipment.map { it.id }.distinct().size != equipment.size) {
        throw BackupException(BackupFailure.DUPLICATE_ID)
    }
    val customIds = equipment.filterNot { it.isBuiltIn }.map { it.id }.toSet()
    if (customIds.intersect(reservedIds).isEmpty()) return this

    // Dangling references reserve their tokens too: repair must never make invalid input valid by
    // allocating a new identity that accidentally matches an originally unknown reference.
    val references = selectedEquipment +
        equipmentProfiles.flatMap { profile ->
            profile.selectedEquipment + profile.ceilings.map { it.equipmentId }
        } +
        equipmentStations.map { it.equipmentId } +
        customExercises.flatMap { it.requiredEquipment.split(',') } +
        exerciseOverrides.flatMap { it.requiredEquipment?.split(',').orEmpty() } +
        activationEntries.flatMap { it.requiredEquipment.split(',') } +
        occurrenceEntries.flatMap { it.requiredEquipment.split(',') }
    val mapping = EquipmentIdentity.collisionMapping(
        customIds,
        equipment.map { it.id }.toSet() + references,
        reservedIds
    )
    return copy(
        equipment = equipment.map { it.copy(id = mapping[it.id] ?: it.id) },
        selectedEquipment = selectedEquipment.map { mapping[it] ?: it },
        equipmentProfiles = equipmentProfiles.map { profile ->
            profile.copy(
                selectedEquipment = profile.selectedEquipment.map { mapping[it] ?: it },
                ceilings = profile.ceilings.map {
                    it.copy(equipmentId = mapping[it.equipmentId] ?: it.equipmentId)
                }
            )
        },
        equipmentStations = equipmentStations.map {
            it.copy(equipmentId = mapping[it.equipmentId] ?: it.equipmentId)
        },
        customExercises = customExercises.map {
            it.copy(requiredEquipment = EquipmentIdentity.remapCsv(it.requiredEquipment, mapping))
        },
        exerciseOverrides = exerciseOverrides.map { row ->
            row.copy(
                requiredEquipment = row.requiredEquipment?.let {
                    EquipmentIdentity.remapCsv(it, mapping)
                }
            )
        },
        activationEntries = activationEntries.map {
            it.copy(requiredEquipment = EquipmentIdentity.remapCsv(it.requiredEquipment, mapping))
        },
        occurrenceEntries = occurrenceEntries.map {
            it.copy(requiredEquipment = EquipmentIdentity.remapCsv(it.requiredEquipment, mapping))
        }
    )
}
