package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentIdentity
import com.hydrafit.app.core.domain.equipment.EquipmentTag

class SeedEquipmentCatalog(private val database: HydraFitDatabase) {
    fun seed() {
        database.equipmentQueries.transaction {
            preserveCollidingCustomEquipment()
            EquipmentTag.BUILT_IN.forEach { tag ->
                database.equipmentQueries.insertIgnore(tag.id, tag.displayName, 1)
            }
        }
    }

    private fun preserveCollidingCustomEquipment() {
        val equipment = database.equipmentQueries.selectAll().executeAsList()
        val reserved = EquipmentTag.BUILT_IN.map { it.id }.toSet()
        val customIds = equipment.filter { it.isBuiltIn == 0L }.map { it.id }.toSet()
        if (customIds.intersect(reserved).isEmpty()) return

        val selected = database.userEquipmentQueries.selectAllSelected().executeAsList()
        val customs = database.exerciseQueries.selectAll().executeAsList().filter {
            it.isCustom != 0L
        }
        val overrides = database.exerciseOverrideQueries.selectAll().executeAsList()
        val activationEntries = database.trainingScheduleQueries.selectAllActivationEntries()
            .executeAsList()
        val occurrenceEntries = database.trainingScheduleQueries.selectAllOccurrenceEntries()
            .executeAsList()
        val references = selected + customs.flatMap { it.requiredEquipment.split(',') } +
            overrides.flatMap { it.requiredEquipment?.split(',').orEmpty() } +
            activationEntries.flatMap { it.requiredEquipment.split(',') } +
            occurrenceEntries.flatMap { it.requiredEquipment.split(',') }
        val mapping = EquipmentIdentity.collisionMapping(
            customIds,
            equipment.map { it.id }.toSet() + references,
            reserved
        )
        mapping.forEach { (oldId, newId) ->
            database.equipmentQueries.moveCustomIdentity(newId, oldId)
            if (oldId in selected) {
                database.userEquipmentQueries.deleteSelected(oldId)
                database.userEquipmentQueries.insertSelected(newId)
            }
        }
        customs.forEach {
            val updated = EquipmentIdentity.remapCsv(it.requiredEquipment, mapping)
            if (updated != it.requiredEquipment) {
                database.exerciseQueries.remapCustomEquipment(updated, it.id)
            }
        }
        overrides.forEach { row ->
            row.requiredEquipment?.let {
                val updated = EquipmentIdentity.remapCsv(it, mapping)
                if (updated != it) {
                    database.exerciseOverrideQueries.remapEquipment(updated, row.exerciseId)
                }
            }
        }
        activationEntries.forEach {
            val updated = EquipmentIdentity.remapCsv(it.requiredEquipment, mapping)
            if (updated != it.requiredEquipment) {
                database.trainingScheduleQueries.remapActivationEquipment(updated, it.id)
            }
        }
        occurrenceEntries.forEach {
            val updated = EquipmentIdentity.remapCsv(it.requiredEquipment, mapping)
            if (updated != it.requiredEquipment) {
                database.trainingScheduleQueries.remapOccurrenceEquipment(updated, it.id)
            }
        }
    }
}
