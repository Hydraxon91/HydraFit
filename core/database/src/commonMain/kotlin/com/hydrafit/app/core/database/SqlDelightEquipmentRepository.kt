package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.equipment.Equipment as EquipmentModel
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightEquipmentRepository(database: HydraFitDatabase) : EquipmentRepository {
    private val queries = database.equipmentQueries

    override fun observeAll(): Flow<List<EquipmentModel>> = queries.selectAll()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows -> rows.map { it.toModel() } }

    override suspend fun all(): List<EquipmentModel> =
        queries.selectAll().executeAsList().map { it.toModel() }

    override suspend fun add(name: String): EquipmentModel {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Equipment name must not be blank" }
        val tag = EquipmentTag(trimmed.toEquipmentId())
        queries.insert(tag.id, trimmed, 0)
        return EquipmentModel(tag, trimmed, isBuiltIn = false)
    }

    override suspend fun remove(id: EquipmentTag) {
        queries.deleteById(id.id)
    }

    override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) {
        queries.updateMaxWeight(maxWeightKg = maxWeightKg, id = id.id)
    }

    private fun Equipment.toModel(): EquipmentModel = EquipmentModel(
        id = EquipmentTag(id),
        name = name,
        isBuiltIn = isBuiltIn != 0L,
        maxWeightKg = maxWeightKg
    )

    private fun String.toEquipmentId(): String =
        uppercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
}
