package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ProgressionHistory
import com.hydrafit.app.core.domain.engine.SlotProgressionContext
import com.hydrafit.app.core.domain.engine.SlotProgressionContextRepository
import com.hydrafit.app.core.domain.engine.SlotProgressionInputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Derives every planned slot's identity from the existing attribution chain and filters one recorded
 * history snapshot to the work performed against each of them.
 *
 * The chain is occurrence entry → source activation entry → activation workout → activation. A
 * template-backed activation keys on its template id, a plan-backed one on its accepted plan id, and
 * the activation workout plus activation entry positions distinguish two slots that share an
 * exercise. Accepting a new plan therefore starts a new `P<planId>` lineage by design. Legacy or
 * unattributed rows (no source entry, no template/plan id) yield no key and therefore no evidence.
 *
 * Everything one occurrence needs is read in a single off-main pass: the history is read once, then
 * filtered per entry, and the active equipment snapshot the load step is bounded by is read in the
 * same call. A card list therefore costs one history read, not one per slot.
 */
class SqlDelightSlotProgressionContextRepository(private val database: HydraFitDatabase) :
    SlotProgressionContextRepository {

    override suspend fun inputsFor(occurrenceId: Long): SlotProgressionInputs =
        withContext(Dispatchers.Default) {
            val history = database.readProgressionHistory()
            val contexts = database.trainingScheduleQueries
                .selectOccurrenceTracksForOccurrence(occurrenceId)
                .executeAsList()
                .associate { track ->
                    val key = track.trackKey()
                    track.occurrenceEntryId to SlotProgressionContext(
                        trackKey = key?.value,
                        history = if (key == null) {
                            ProgressionHistory()
                        } else {
                            history.filteredTo(
                                database.trainingScheduleQueries,
                                key
                            )
                        }
                    )
                }
            val equipment = database.readEquipmentProfileState().active
            SlotProgressionInputs(
                contexts = contexts,
                loadingInventory = equipment.loadingInventory,
                equipmentMaxWeights = equipment.maxWeightsKg
            )
        }

    private fun ProgressionHistory.filteredTo(
        queries: TrainingScheduleQueries,
        key: TrackKey
    ): ProgressionHistory {
        val setIds = when (key) {
            is TrackKey.Template ->
                queries.selectTemplateTrackSetIds(
                    key.templateId,
                    key.workoutPosition,
                    key.entryPosition
                )
                    .executeAsList()

            is TrackKey.Plan ->
                queries.selectPlanTrackSetIds(key.planId, key.workoutPosition, key.entryPosition)
                    .executeAsList()
        }.toSet()
        return copy(
            sets = sets.filter { it.id in setIds },
            legacyTargets = legacyTargets.filterKeys { it in setIds }
        )
    }
}

/** A stable slot identity derived from the frozen activation chain, never stored on a set. */
private sealed interface TrackKey {
    val value: String

    data class Template(val templateId: Long, val workoutPosition: Long, val entryPosition: Long) :
        TrackKey {
        override val value: String = "T$templateId:$workoutPosition:$entryPosition"
    }

    data class Plan(val planId: Long, val workoutPosition: Long, val entryPosition: Long) :
        TrackKey {
        override val value: String = "P$planId:$workoutPosition:$entryPosition"
    }
}

private fun SelectOccurrenceTracksForOccurrence.trackKey(): TrackKey? {
    val template = templateId
    val plan = sourcePlanId
    val workout = workoutPosition
    val entry = entryPosition
    if (workout == null || entry == null) return null
    return when {
        template != null -> TrackKey.Template(template, workout, entry)
        plan != null -> TrackKey.Plan(plan, workout, entry)
        else -> null
    }
}
