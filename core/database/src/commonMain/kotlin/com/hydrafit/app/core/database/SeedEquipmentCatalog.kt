package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag

class SeedEquipmentCatalog(private val database: HydraFitDatabase) {
    fun seed() {
        database.equipmentQueries.transaction {
            EquipmentTag.BUILT_IN.forEach { tag ->
                database.equipmentQueries.insertIgnore(tag.id, tag.displayName, 1)
            }
        }
    }
}
