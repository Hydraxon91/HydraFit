package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.hydrafit.app.core.domain.equipment.EquipmentStation as DomainStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.LoadingInventoryJson
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
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
                    val profile = queries.selectProfile(id).executeAsOne()
                    val entries = queries.selectProfileEntries(id).executeAsList()
                    EquipmentProfile(
                        id = id,
                        name = profile.name,
                        selected = entries.filter { it.selected != 0L }
                            .map { EquipmentTag(it.equipmentId) }.toSet(),
                        maxWeightsKg = entries.mapNotNull { entry ->
                            entry.maxWeightKg?.let { EquipmentTag(entry.equipmentId) to it }
                        }.toMap(),
                        loadingInventory = LoadingInventoryJson.decode(profile.loadingInventory),
                        stations = database.equipmentStationQueries
                            .selectStationsForProfile(id)
                            .executeAsList()
                            .map { it.toDomainStation() }
                    )
                }
                queries.insertProfile(
                    name,
                    LoadingInventoryJson.encode(source?.loadingInventory ?: LoadingInventory())
                )
                val id = queries.lastInsertedId().executeAsOne()
                source?.let { profile ->
                    writeEntries(id, profile.selected, profile.maxWeightsKg)
                    // Copying a profile creates new station identities: the copied profile is a new
                    // availability context, not a claim that it is the same physical station.
                    profile.stations.forEach { station ->
                        database.equipmentStationQueries.insertStation(
                            profileId = id,
                            equipmentId = station.equipmentId.id,
                            name = station.name,
                            loadBasis = station.loadBasis.name,
                            minWeightKg = station.minWeightKg,
                            maxWeightKg = station.maxWeightKg,
                            incrementKg = station.incrementKg,
                            retired = if (station.retired) 1L else 0L
                        )
                    }
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
            require(queries.selectProfile(profileId).executeAsOneOrNull() != null)
            queries.activateProfile(profileId)
        }

    override suspend fun delete(
        context: EquipmentProfileContext,
        profileId: Long,
        replacementId: Long?
    ) = mutate(context) {
        require(queries.countProfiles().executeAsOne() > 1)
        require(queries.selectProfile(profileId).executeAsOneOrNull() != null)
        if (profileId == context.profileId) {
            require(replacementId != null && replacementId != profileId)
            require(queries.selectProfile(replacementId).executeAsOneOrNull() != null)
            queries.activateProfile(replacementId)
        }
        database.equipmentStationQueries.deleteStationsForProfile(profileId)
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

    override suspend fun saveStation(
        context: EquipmentProfileContext,
        station: DomainStation
    ): Long = withContext(Dispatchers.Default) {
        station.requireValid()
        require(station.profileId == context.profileId) {
            "A station must belong to the active profile"
        }
        queries.transactionWithResult {
            database.requireEquipmentProfileContext(context)
            val existingNames = database.equipmentStationQueries
                .selectStationsForProfile(context.profileId)
                .executeAsList()
                .filter { it.id != station.id && it.retired == 0L }
            require(
                existingNames.none {
                    it.name.equals(station.name, ignoreCase = true) &&
                        it.equipmentId == station.equipmentId.id
                }
            ) {
                "An active station with that name already exists for this equipment"
            }
            val id = if (station.id == 0L) {
                database.equipmentStationQueries.insertStation(
                    profileId = context.profileId,
                    equipmentId = station.equipmentId.id,
                    name = station.name,
                    loadBasis = station.loadBasis.name,
                    minWeightKg = station.minWeightKg,
                    maxWeightKg = station.maxWeightKg,
                    incrementKg = station.incrementKg,
                    retired = 0L
                )
                database.equipmentStationQueries.lastInsertedStationId().executeAsOne()
            } else {
                val updated = database.equipmentStationQueries.updateStation(
                    equipmentId = station.equipmentId.id,
                    name = station.name,
                    loadBasis = station.loadBasis.name,
                    minWeightKg = station.minWeightKg,
                    maxWeightKg = station.maxWeightKg,
                    incrementKg = station.incrementKg,
                    id = station.id,
                    profileId = context.profileId
                ).value
                require(updated == 1L) { "The station no longer belongs to this profile" }
                station.id
            }
            queries.bumpRevision()
            id
        }
    }

    override suspend fun retireStation(context: EquipmentProfileContext, stationId: Long) =
        mutate(context) {
            val updated = database.equipmentStationQueries
                .retireStation(id = stationId, profileId = context.profileId)
                .value
            require(updated == 1L) { "The station no longer belongs to this profile" }
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
        val stations = equipmentStationQueries.selectStations()
            .executeAsList()
            .groupBy { it.profileId }
        EquipmentProfileState(
            EquipmentProfileContext(state.activeProfileId, state.revision),
            equipmentProfileQueries.selectProfiles().executeAsList().map { profile ->
                val inventory = entries[profile.id].orEmpty()
                EquipmentProfile(
                    id = profile.id,
                    name = profile.name,
                    selected = inventory.filter { it.selected != 0L }
                        .map { EquipmentTag(it.equipmentId) }.toSet(),
                    maxWeightsKg = inventory.mapNotNull { entry ->
                        entry.maxWeightKg?.let { EquipmentTag(entry.equipmentId) to it }
                    }.toMap(),
                    loadingInventory = LoadingInventoryJson.decode(profile.loadingInventory),
                    stations = stations[profile.id].orEmpty().map { it.toDomainStation() }
                )
            }
        )
    }

private fun requireProfileName(name: String) {
    require(name.isNotBlank() && name.length <= 80 && name == name.trim())
}

private fun EquipmentStation.toDomainStation(): DomainStation = DomainStation(
    id = id,
    profileId = profileId,
    equipmentId = EquipmentTag(equipmentId),
    name = name,
    loadBasis = StationLoadBasis.fromStored(loadBasis)
        ?: error("Unknown stored station load basis"),
    minWeightKg = minWeightKg,
    maxWeightKg = maxWeightKg,
    incrementKg = incrementKg,
    retired = retired != 0L
)
