package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository

class SqlDelightEquipmentSelectionRepository(database: HydraFitDatabase) :
    EquipmentSelectionRepository {
    private val queries = database.userEquipmentQueries

    override suspend fun selected(): Set<EquipmentTag> =
        queries.selectAllSelected().executeAsList().map(EquipmentTag::valueOf).toSet()

    override suspend fun setSelected(tags: Set<EquipmentTag>) {
        queries.transaction {
            queries.deleteAllSelected()
            tags.sortedBy { it.name }.forEach { tag -> queries.insertSelected(tag.name) }
        }
    }
}
