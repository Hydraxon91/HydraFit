package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory

/**
 * One planned slot's own comparable evidence: the derived identity of the prescription slot and the
 * recorded history filtered to that slot.
 *
 * [trackKey] is derived from the frozen activation chain rather than stored on the set, so two
 * activations of the same routine/plan slot share it while a different slot of the same exercise
 * does not. Null means the row is legacy, unattributed or has no stable slot identity; such work is
 * never eligible evidence and the caller holds instead of guessing a predecessor.
 */
data class SlotProgressionContext(val trackKey: String?, val history: ProgressionHistory)

/**
 * Everything one occurrence's slot decisions need, read in one coherent pass: the per-entry track
 * identity and filtered history, plus the active equipment snapshot a load step is bounded by.
 *
 * [contexts] is keyed by occurrence entry id and includes entries without a stable identity, so a
 * batch caller never needs a second read to find their hold.
 */
data class SlotProgressionInputs(
    val contexts: Map<Long, SlotProgressionContext>,
    val loadingInventory: LoadingInventory? = null,
    val equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap()
)

/** Reads one occurrence's slot identities, histories and equipment bounds in one coherent pass. */
interface SlotProgressionContextRepository {
    suspend fun inputsFor(occurrenceId: Long): SlotProgressionInputs
}
