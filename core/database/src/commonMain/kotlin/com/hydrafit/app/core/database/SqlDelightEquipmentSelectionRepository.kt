package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightEquipmentSelectionRepository(private val database: HydraFitDatabase) :
    EquipmentSelectionRepository {
    private val queries = database.userEquipmentQueries

    override suspend fun selected(): Set<EquipmentTag> =
        database.readEquipmentProfileState().active.selected

    override fun selectedFlow(): Flow<Set<EquipmentTag>> =
        SqlDelightEquipmentProfileRepository(database).observe().map { it.active.selected }

    override suspend fun setSelected(tags: Set<EquipmentTag>) {
        queries.transaction {
            val state = database.equipmentProfileQueries.selectState().executeAsOne()
            val ceilings = database.equipmentProfileQueries
                .selectProfileEntries(state.activeProfileId)
                .executeAsList().mapNotNull { entry ->
                    entry.maxWeightKg?.let { EquipmentTag(entry.equipmentId) to it }
                }.toMap()
            tags.forEach {
                require(database.equipmentQueries.selectById(it.id).executeAsOneOrNull() != null)
            }
            database.equipmentProfileQueries.deleteEntries(state.activeProfileId)
            (tags + ceilings.keys).forEach {
                database.equipmentProfileQueries.upsertEntry(
                    state.activeProfileId,
                    it.id,
                    if (it in tags) 1L else 0L,
                    ceilings[it]
                )
            }
            database.equipmentProfileQueries.bumpRevision()
            queries.deleteAllSelected()
            tags.sortedBy { it.id }.forEach { tag -> queries.insertSelected(tag.id) }
        }
    }
}
