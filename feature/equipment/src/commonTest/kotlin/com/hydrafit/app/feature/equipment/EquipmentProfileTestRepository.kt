package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.userdata.equipment.EquipmentProfile
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import com.hydrafit.app.core.userdata.equipment.StaleEquipmentProfileException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A profile with explicit configuration; every unspecified value stays empty. */
internal fun testProfile(
    id: Long,
    name: String,
    selected: Set<EquipmentTag> = emptySet(),
    maxWeightsKg: Map<EquipmentTag, Double> = emptyMap(),
    loadingInventory: LoadingInventory = LoadingInventory(),
    stations: List<EquipmentStation> = emptyList()
) = EquipmentProfile(
    id = id,
    name = name,
    selected = selected,
    maxWeightsKg = maxWeightsKg,
    loadingInventory = loadingInventory,
    stations = stations
)

/**
 * In-memory [EquipmentProfileRepository] for the profile/loading-editor tests.
 *
 * It mirrors the SQLDelight repository's contract: every successful write bumps the context
 * revision, a captured stale context is refused with [StaleEquipmentProfileException], and
 * creating a profile does not switch the active one. A write can be parked on [gate] so a test can
 * assert what happens when its result arrives after the active profile changed.
 */
internal class EquipmentProfileTestRepository(
    profiles: List<EquipmentProfile> = listOf(testProfile(1L, "Default")),
    activeId: Long = profiles.first().id
) : EquipmentProfileRepository {

    private val flow = MutableStateFlow(
        EquipmentProfileState(EquipmentProfileContext(activeId, 0L), profiles)
    )
    private var nextId = (profiles.maxOfOrNull { it.id } ?: 0L) + 1L
    private var nextStationId = (profiles.flatMap { it.stations }.maxOfOrNull { it.id } ?: 0L) + 1L

    /** When set, every write waits on it first, exposing an in-flight result to the test. */
    var gate: CompletableDeferred<Unit>? = null

    var createFailure: Exception? = null
    var renameFailure: Exception? = null
    var activateFailure: Exception? = null
    var deleteFailure: Exception? = null
    var saveEquipmentFailure: Exception? = null
    var saveInventoryFailure: Exception? = null

    var createCalls = 0
        private set
    var renameCalls = 0
        private set
    var activateCalls = 0
        private set
    var deleteCalls = 0
        private set
    var saveEquipmentCalls = 0
        private set
    var saveInventoryCalls = 0
        private set
    var currentCalls = 0
        private set

    var lastCopyFrom: Long? = null
        private set
    var lastSavedEquipment: Set<EquipmentTag> = emptySet()
        private set
    var lastSavedMaxWeights: Map<EquipmentTag, Double> = emptyMap()
        private set
    var lastSavedInventory: LoadingInventory? = null
        private set

    val state: EquipmentProfileState get() = flow.value

    override fun observe(): Flow<EquipmentProfileState> = flow.asStateFlow()

    override suspend fun current(): EquipmentProfileState {
        currentCalls++
        return flow.value
    }

    override suspend fun create(name: String, copyFrom: Long?): Long {
        createCalls++
        createFailure?.let { throw it }
        gate?.await()
        val source = copyFrom?.let { id -> flow.value.profiles.first { it.id == id } }
        lastCopyFrom = copyFrom
        val created = EquipmentProfile(
            id = nextId++,
            name = name,
            selected = source?.selected ?: emptySet(),
            maxWeightsKg = source?.maxWeightsKg ?: emptyMap(),
            loadingInventory = source?.loadingInventory ?: LoadingInventory(),
            stations = source?.stations.orEmpty().map {
                it.copy(id = nextStationId++, profileId = nextId - 1L)
            }
        )
        publish(profiles = flow.value.profiles + created, activeId = flow.value.context.profileId)
        return created.id
    }

    override suspend fun rename(context: EquipmentProfileContext, name: String) {
        renameCalls++
        renameFailure?.let { throw it }
        gate?.await()
        requireContext(context)
        publish(
            profiles = flow.value.profiles.map {
                if (it.id == context.profileId) it.copy(name = name) else it
            },
            activeId = context.profileId
        )
    }

    override suspend fun activate(context: EquipmentProfileContext, profileId: Long) {
        activateCalls++
        activateFailure?.let { throw it }
        gate?.await()
        requireContext(context)
        require(flow.value.profiles.any { it.id == profileId })
        publish(profiles = flow.value.profiles, activeId = profileId)
    }

    override suspend fun delete(
        context: EquipmentProfileContext,
        profileId: Long,
        replacementId: Long?
    ) {
        deleteCalls++
        deleteFailure?.let { throw it }
        gate?.await()
        requireContext(context)
        require(flow.value.profiles.size > 1)
        val active = context.profileId
        if (profileId == active) {
            require(replacementId != null && replacementId != profileId)
        }
        publish(
            profiles = flow.value.profiles.filterNot { it.id == profileId },
            activeId = if (profileId == active) requireNotNull(replacementId) else active
        )
    }

    override suspend fun saveEquipment(
        context: EquipmentProfileContext,
        selected: Set<EquipmentTag>,
        maxWeightsKg: Map<EquipmentTag, Double>
    ) {
        saveEquipmentCalls++
        saveEquipmentFailure?.let { throw it }
        gate?.await()
        requireContext(context)
        lastSavedEquipment = selected
        lastSavedMaxWeights = maxWeightsKg
        publish(
            profiles = flow.value.profiles.map {
                if (it.id == context.profileId) {
                    it.copy(selected = selected, maxWeightsKg = maxWeightsKg)
                } else {
                    it
                }
            },
            activeId = context.profileId
        )
    }

    override suspend fun saveLoadingInventory(
        context: EquipmentProfileContext,
        inventory: LoadingInventory
    ) {
        saveInventoryCalls++
        saveInventoryFailure?.let { throw it }
        gate?.await()
        requireContext(context)
        lastSavedInventory = inventory
        publish(
            profiles = flow.value.profiles.map {
                if (it.id == context.profileId) it.copy(loadingInventory = inventory) else it
            },
            activeId = context.profileId
        )
    }

    override suspend fun saveStation(
        context: EquipmentProfileContext,
        station: EquipmentStation
    ): Long {
        gate?.await()
        requireContext(context)
        val id = if (station.id == 0L) nextStationId++ else station.id
        val persisted = station.copy(id = id, profileId = context.profileId)
        publish(
            profiles = flow.value.profiles.map {
                if (it.id != context.profileId) {
                    it
                } else {
                    it.copy(
                        stations = it.stations.filterNot { row -> row.id == id } + persisted
                    )
                }
            },
            activeId = context.profileId
        )
        return id
    }

    override suspend fun retireStation(context: EquipmentProfileContext, stationId: Long) {
        gate?.await()
        requireContext(context)
        publish(
            profiles = flow.value.profiles.map {
                if (it.id != context.profileId) {
                    it
                } else {
                    it.copy(
                        stations = it.stations.map { row ->
                            if (row.id == stationId) row.copy(retired = true) else row
                        }
                    )
                }
            },
            activeId = context.profileId
        )
    }

    /** Refuses a write captured against a profile or revision that is no longer current. */
    private fun requireContext(context: EquipmentProfileContext) {
        val current = flow.value.context
        if (current.profileId != context.profileId || current.revision != context.revision) {
            throw StaleEquipmentProfileException()
        }
    }

    private fun publish(profiles: List<EquipmentProfile>, activeId: Long) {
        flow.value = EquipmentProfileState(
            EquipmentProfileContext(activeId, flow.value.context.revision + 1),
            profiles
        )
    }
}
