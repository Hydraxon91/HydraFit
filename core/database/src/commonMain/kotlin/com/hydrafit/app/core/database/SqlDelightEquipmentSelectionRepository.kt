package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightEquipmentSelectionRepository(database: HydraFitDatabase) :
    EquipmentSelectionRepository {
    private val queries = database.userEquipmentQueries

    override suspend fun selected(): Set<EquipmentTag> =
        queries.selectAllSelected().executeAsList().map(EquipmentTag::valueOf).toSet()

    override fun selectedFlow(): Flow<Set<EquipmentTag>> = queries.selectAllSelected()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { tags -> tags.map(EquipmentTag::valueOf).toSet() }

    override suspend fun setSelected(tags: Set<EquipmentTag>) {
        queries.transaction {
            queries.deleteAllSelected()
            tags.sortedBy { it.name }.forEach { tag -> queries.insertSelected(tag.name) }
        }
    }
}
