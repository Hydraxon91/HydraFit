package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.equipment.Equipment as EquipmentModel
import com.hydrafit.app.core.domain.equipment.EquipmentIdentity
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SqlDelightEquipmentRepository(private val database: HydraFitDatabase) : EquipmentRepository {
    private val queries = database.equipmentQueries

    override fun observeAll(): Flow<List<EquipmentModel>> = combine(
        queries.selectAll().asFlow().mapToList(Dispatchers.Default),
        SqlDelightEquipmentProfileRepository(database).observe()
    ) { rows, profiles ->
        rows.map {
            it.toModel().copy(maxWeightKg = profiles.active.maxWeightsKg[EquipmentTag(it.id)])
        }
    }

    override suspend fun all(): List<EquipmentModel> = queries.transactionWithResult {
        val active = database.readEquipmentProfileState().active
        queries.selectAll().executeAsList().map {
            it.toModel().copy(maxWeightKg = active.maxWeightsKg[EquipmentTag(it.id)])
        }
    }

    override suspend fun add(name: String): EquipmentModel {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Equipment name must not be blank" }
        val id = uniqueId(trimmed.toEquipmentId())
        queries.insert(id, trimmed, 0)
        return EquipmentModel(EquipmentTag(id), trimmed, isBuiltIn = false)
    }

    override suspend fun remove(id: EquipmentTag) = remove(activeContext(), id)

    override suspend fun remove(context: EquipmentProfileContext, id: EquipmentTag) {
        queries.transaction {
            database.requireEquipmentProfileContext(context)
            val row = queries.selectById(id.id).executeAsOne()
            require(row.isBuiltIn == 0L)
            val state = database.equipmentProfileQueries.selectState().executeAsOne()
            val references = database.equipmentProfileQueries.selectEquipmentReferences(id.id)
                .executeAsList()
            require(references.none { it != state.activeProfileId })
            require(!queries.hasProtectedReference(id.id).executeAsOne())
            database.equipmentProfileQueries.deleteEquipmentEntry(state.activeProfileId, id.id)
            // Stations are global to the catalog row even when they are profile-scoped, so clear
            // every profile's station for this equipment. Recorded sets keep their frozen station
            // label and load basis; only the station/equipment identity goes away.
            database.equipmentStationQueries.deleteStationsForEquipment(id.id)
            queries.deleteById(id.id)
            database.equipmentProfileQueries.bumpRevision()
        }
    }

    override suspend fun rename(
        id: EquipmentTag,
        name: String,
        maxWeightKg: Double?
    ): EquipmentModel = rename(activeContext(), id, name, maxWeightKg)

    override suspend fun rename(
        context: EquipmentProfileContext,
        id: EquipmentTag,
        name: String,
        maxWeightKg: Double?
    ): EquipmentModel = queries.transactionWithResult {
        database.requireEquipmentProfileContext(context)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Equipment name must not be blank" }
        require(queries.selectById(id.id).executeAsOne().isBuiltIn == 0L)
        writeCeiling(context, id, maxWeightKg)
        queries.renameCustom(name = trimmed, id = id.id)
        EquipmentModel(id, trimmed, isBuiltIn = false, maxWeightKg = maxWeightKg)
    }

    override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) {
        setMaxWeight(activeContext(), id, maxWeightKg)
    }

    override suspend fun setMaxWeight(
        context: EquipmentProfileContext,
        id: EquipmentTag,
        maxWeightKg: Double?
    ) {
        queries.transaction {
            database.requireEquipmentProfileContext(context)
            writeCeiling(context, id, maxWeightKg)
        }
    }

    private fun writeCeiling(
        context: EquipmentProfileContext,
        id: EquipmentTag,
        maxWeightKg: Double?
    ) {
        require(maxWeightKg == null || (maxWeightKg.isFinite() && maxWeightKg >= 0.0))
        require(queries.selectById(id.id).executeAsOneOrNull() != null)
        val selected = database.equipmentProfileQueries.selectEntry(context.profileId, id.id)
            .executeAsOneOrNull()?.selected == 1L
        database.equipmentProfileQueries.upsertEntry(
            context.profileId,
            id.id,
            if (selected) 1L else 0L,
            maxWeightKg
        )
        database.equipmentProfileQueries.bumpRevision()
    }

    private fun activeContext(): EquipmentProfileContext =
        database.equipmentProfileQueries.selectState().executeAsOne().let {
            EquipmentProfileContext(it.activeProfileId, it.revision)
        }

    private fun Equipment.toModel(): EquipmentModel = EquipmentModel(
        id = EquipmentTag(id),
        name = name,
        isBuiltIn = isBuiltIn != 0L,
        maxWeightKg = maxWeightKg
    )

    private fun String.toEquipmentId(): String =
        uppercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

    /** Distinct names can normalize to the same id (e.g. "Lat Pulldown"/"Lat-Pulldown"); suffix on collision. */
    private fun uniqueId(base: String): String {
        val taken = queries.selectAll().executeAsList().map { it.id }.toSet()
        return EquipmentIdentity.availableCustomId(
            base,
            taken + EquipmentTag.BUILT_IN.map { it.id }
        )
    }
}
