package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.userdata.equipment.EquipmentProfile
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One saved profile with the supplied inventory and name. */
internal fun loadingProfile(
    id: Long,
    name: String,
    inventory: LoadingInventory = loadingInventory()
): EquipmentProfile = EquipmentProfile(id, name, emptySet(), emptyMap(), inventory)

/** A bar with one plate pair, plus one fixed dumbbell, so both calculator kinds resolve. */
internal fun loadingInventory(
    unit: String = "KG",
    bars: List<LoadingBar> = listOf(LoadingBar("bar", "Bar", 20_000)),
    plates: List<OwnedPlate> = listOf(OwnedPlate(5_000, 2)),
    dumbbells: List<LoadingDumbbell> = listOf(
        LoadingDumbbell("db", "Dumbbell", fixedTicks = listOf(10_000, 20_000))
    )
): LoadingInventory =
    LoadingInventory(unit = unit, plates = plates, bars = bars, dumbbells = dumbbells)

/**
 * An in-memory equipment-profile source for the helper's lifecycle tests.
 *
 * [writes] records every mutating call, so a test can prove the helper never wrote. [activate] and
 * [editInventory] bump the revision the way the real repository does, so a profile switch, a switch
 * back and an inventory edit are all observable as the same context change.
 */
internal class LoggerLoadingProfileTestRepository(
    profiles: List<EquipmentProfile> = listOf(loadingProfile(1L, "Home"))
) : EquipmentProfileRepository {

    private val initial = profiles.ifEmpty { listOf(loadingProfile(1L, "Home")) }
    private val _state = MutableStateFlow(
        EquipmentProfileState(EquipmentProfileContext(initial.first().id, 0L), initial)
    )
    val state: StateFlow<EquipmentProfileState> = _state.asStateFlow()

    /** Every mutating call this repository received; the helper must leave it empty. */
    val writes = mutableListOf<String>()

    /** Switches the active profile and bumps the revision, as `activate` does when it commits. */
    fun activate(profileId: Long) {
        _state.value = _state.value.copy(
            context = EquipmentProfileContext(profileId, _state.value.context.revision + 1)
        )
    }

    /** Replaces one profile's inventory and bumps the revision, as a saved inventory edit does. */
    fun editInventory(profileId: Long, inventory: LoadingInventory) {
        _state.value = EquipmentProfileState(
            context = EquipmentProfileContext(profileId, _state.value.context.revision + 1),
            profiles = _state.value.profiles.map {
                if (it.id == profileId) it.copy(loadingInventory = inventory) else it
            }
        )
    }

    override fun observe() = _state.asStateFlow()

    override suspend fun current() = _state.value

    override suspend fun create(name: String, copyFrom: Long?): Long = unsupported("create")

    override suspend fun rename(context: EquipmentProfileContext, name: String) =
        unsupported("rename")

    override suspend fun activate(context: EquipmentProfileContext, profileId: Long) =
        unsupported("activate")

    override suspend fun delete(
        context: EquipmentProfileContext,
        profileId: Long,
        replacementId: Long?
    ) = unsupported("delete")

    override suspend fun saveEquipment(
        context: EquipmentProfileContext,
        selected: Set<EquipmentTag>,
        maxWeightsKg: Map<EquipmentTag, Double>
    ) = unsupported("saveEquipment")

    override suspend fun saveLoadingInventory(
        context: EquipmentProfileContext,
        inventory: LoadingInventory
    ) = unsupported("saveLoadingInventory")

    override suspend fun saveStation(
        context: EquipmentProfileContext,
        station: EquipmentStation
    ): Long = unsupported("saveStation")

    override suspend fun retireStation(context: EquipmentProfileContext, stationId: Long) =
        unsupported("retireStation")

    private fun unsupported(name: String): Nothing {
        writes += name
        error("$name is not expected from the loading helper")
    }
}
