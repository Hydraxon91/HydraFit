package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.LoadingInventoryJson
import com.hydrafit.app.core.userdata.equipment.EquipmentProfile
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import com.hydrafit.app.core.userdata.equipment.StaleEquipmentProfileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightEquipmentProfileRepository(private val database: HydraFitDatabase) :
    EquipmentProfileRepository {
    private val queries = database.equipmentProfileQueries

    override fun observe(): Flow<EquipmentProfileState> = queries.selectRevision()
        .asFlow()
        .mapToOne(Dispatchers.Default)
        .map { current() }

    override suspend fun current(): EquipmentProfileState = withContext(Dispatchers.Default) {
        database.readEquipmentProfileState()
    }

    override suspend fun create(name: String, copyFrom: Long?): Long =
        withContext(Dispatchers.Default) {
            requireProfileName(name)
            queries.transactionWithResult {
                val source = copyFrom?.let { id ->
                    database.readEquipmentProfileState().profiles.single { it.id == id }
                }
                queries.insertProfile(
                    name,
                    LoadingInventoryJson.encode(source?.loadingInventory ?: LoadingInventory())
                )
                val id = queries.lastInsertedId().executeAsOne()
                source?.let { profile ->
                    writeEntries(id, profile.selected, profile.maxWeightsKg)
                }
                queries.bumpRevision()
                id
            }
        }

    override suspend fun rename(context: EquipmentProfileContext, name: String) = mutate(context) {
        requireProfileName(name)
        queries.renameProfile(name, context.profileId)
    }

    override suspend fun activate(context: EquipmentProfileContext, profileId: Long) =
        mutate(context) {
            require(queries.selectProfiles().executeAsList().any { it.id == profileId })
            queries.activateProfile(profileId)
        }

    override suspend fun delete(
        context: EquipmentProfileContext,
        profileId: Long,
        replacementId: Long?
    ) = mutate(context) {
        val profiles = queries.selectProfiles().executeAsList()
        require(profiles.size > 1 && profiles.any { it.id == profileId })
        if (profileId == context.profileId) {
            require(replacementId != null && replacementId != profileId)
            require(profiles.any { it.id == replacementId })
            queries.activateProfile(replacementId)
        }
        queries.deleteEntries(profileId)
        queries.deleteProfile(profileId)
    }

    override suspend fun saveEquipment(
        context: EquipmentProfileContext,
        selected: Set<EquipmentTag>,
        maxWeightsKg: Map<EquipmentTag, Double>
    ) = mutate(context) {
        (selected + maxWeightsKg.keys).forEach { tag ->
            require(database.equipmentQueries.selectById(tag.id).executeAsOneOrNull() != null)
        }
        require(maxWeightsKg.values.all { it.isFinite() && it >= 0.0 })
        queries.deleteEntries(context.profileId)
        writeEntries(context.profileId, selected, maxWeightsKg)
    }

    override suspend fun saveLoadingInventory(
        context: EquipmentProfileContext,
        inventory: LoadingInventory
    ) = mutate(context) {
        queries.updateInventory(LoadingInventoryJson.encode(inventory), context.profileId)
    }

    private suspend fun mutate(context: EquipmentProfileContext, action: () -> Unit) =
        withContext(Dispatchers.Default) {
            queries.transaction {
                database.requireEquipmentProfileContext(context)
                action()
                queries.bumpRevision()
            }
        }

    private fun writeEntries(
        profileId: Long,
        selected: Set<EquipmentTag>,
        maxWeightsKg: Map<EquipmentTag, Double>
    ) {
        (selected + maxWeightsKg.keys).sortedBy { it.id }.forEach { tag ->
            queries.upsertEntry(
                profileId,
                tag.id,
                if (tag in selected) 1L else 0L,
                maxWeightsKg[tag]
            )
        }
    }
}

internal fun HydraFitDatabase.requireEquipmentProfileContext(context: EquipmentProfileContext) {
    val state = equipmentProfileQueries.selectState().executeAsOne()
    if (state.activeProfileId != context.profileId || state.revision != context.revision) {
        throw StaleEquipmentProfileException()
    }
    check(state.revision < Long.MAX_VALUE - 1)
}

internal fun HydraFitDatabase.readEquipmentProfileState(): EquipmentProfileState =
    equipmentProfileQueries.transactionWithResult {
        val state = equipmentProfileQueries.selectState().executeAsOne()
        val entries = equipmentProfileQueries.selectEntries().executeAsList()
            .groupBy { it.profileId }
        EquipmentProfileState(
            EquipmentProfileContext(state.activeProfileId, state.revision),
            equipmentProfileQueries.selectProfiles().executeAsList().map { profile ->
                val inventory = entries[profile.id].orEmpty()
                EquipmentProfile(
                    profile.id,
                    profile.name,
                    inventory.filter { it.selected != 0L }
                        .map { EquipmentTag(it.equipmentId) }.toSet(),
                    inventory.mapNotNull { entry ->
                        entry.maxWeightKg?.let { EquipmentTag(entry.equipmentId) to it }
                    }.toMap(),
                    LoadingInventoryJson.decode(profile.loadingInventory)
                )
            }
        )
    }

private fun requireProfileName(name: String) {
    require(name.isNotBlank() && name.length <= 80 && name == name.trim())
}
