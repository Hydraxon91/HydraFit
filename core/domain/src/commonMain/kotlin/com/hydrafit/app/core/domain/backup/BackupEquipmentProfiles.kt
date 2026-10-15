package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.equipment.requireLoadingInventory

internal fun validateEquipmentProfiles(file: BackupFile, knownEquipment: Set<String>) {
    fun invalid(): Nothing = throw BackupException(BackupFailure.INVALID_VALUE)
    if (file.formatVersion < EQUIPMENT_PROFILE_FORMAT_VERSION) {
        if (file.equipmentProfiles.isNotEmpty() || file.activeEquipmentProfileId != null) invalid()
        return
    }
    val profiles = file.equipmentProfiles
    if (profiles.isEmpty() || profiles.map { it.id }.distinct().size != profiles.size) invalid()
    profiles.forEach { profile ->
        if (profile.id <= 0L ||
            profile.id == Long.MAX_VALUE ||
            profile.name.isBlank() ||
            profile.name.length > 80 ||
            profile.name != profile.name.trim()
        ) {
            invalid()
        }
        if (profile.selectedEquipment.distinct().size != profile.selectedEquipment.size) invalid()
        val ceilingIds = profile.ceilings.map { it.equipmentId }
        if (ceilingIds.distinct().size != ceilingIds.size) invalid()
        (profile.selectedEquipment + profile.ceilings.map { it.equipmentId }).forEach {
            if (it !in knownEquipment) throw BackupException(BackupFailure.UNKNOWN_CATALOG_ID)
        }
        profile.ceilings.forEach {
            if (!it.maxWeightKg.isFinite() || it.maxWeightKg < 0.0) invalid()
        }
        try {
            requireLoadingInventory(profile.loadingInventory)
        } catch (_: IllegalArgumentException) {
            invalid()
        }
    }
    val active = profiles.singleOrNull { it.id == file.activeEquipmentProfileId } ?: invalid()
    if (active.selectedEquipment.toSet() != file.selectedEquipment.toSet()) invalid()
    val legacyCeilings = file.equipment.mapNotNull { row ->
        row.maxWeightKg?.let { row.id to it }
    }.toMap()
    if (legacyCeilings != active.ceilings.associate { it.equipmentId to it.maxWeightKg }) invalid()
}
