package com.hydrafit.app.core.domain.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A slot's approved progression policy: the rep range the user works inside, plus the load step that
 * applies once the top of that range is reached.
 *
 * Both numbers are user inputs, not research-derived thresholds. The app never invents a rep range or
 * an increment for a slot the user did not configure, and nothing here claims an optimal range.
 *
 * [equipmentId] optionally names the bar or dumbbell whose real loading grid resolves the next
 * reachable weight. Absent means the plain numeric [incrementKg] applies. A named identity that no
 * longer resolves holds the suggestion rather than silently borrowing another inventory's grid.
 */
@Serializable
data class SlotProgressionPolicy(
    val version: Int = POLICY_VERSION,
    /** Lowest prescribed rep count of the range. */
    val minReps: Int,
    /** Highest prescribed rep count; reaching it applies the load step and returns to [minReps]. */
    val maxReps: Int,
    /** The user's chosen load step in kilograms. */
    val incrementKg: Double,
    /** Explicit bar/dumbbell identity for reachability, or null for the plain numeric step. */
    val equipmentId: String? = null
) {
    fun requireValid() {
        require(version == POLICY_VERSION) { "Unsupported progression policy version" }
        require(minReps in MIN_REPS..MAX_REPS) { "minReps must be between $MIN_REPS and $MAX_REPS" }
        require(maxReps in minReps..MAX_REPS) { "maxReps must be between minReps and $MAX_REPS" }
        require(incrementKg.isFinite() && incrementKg > 0.0) { "incrementKg must be positive" }
        require(equipmentId == null || equipmentId.isValidEquipmentId()) {
            "equipmentId must be a non-blank identity of at most $MAX_ID_LENGTH characters"
        }
    }

    private fun String.isValidEquipmentId(): Boolean =
        isNotBlank() && length <= MAX_ID_LENGTH && this == trim()

    companion object {
        const val POLICY_VERSION: Int = 1
        const val MIN_REPS: Int = 1
        const val MAX_REPS: Int = 100
        const val MAX_ID_LENGTH: Int = 80
    }
}

/** Strict persisted encoding; a corrupt policy must never silently become an approved rule. */
object SlotProgressionPolicyJson {
    private val json = Json { encodeDefaults = true }

    fun encode(policy: SlotProgressionPolicy): String {
        policy.requireValid()
        return json.encodeToString(SlotProgressionPolicy.serializer(), policy)
    }

    fun decode(text: String): SlotProgressionPolicy =
        json.decodeFromString(SlotProgressionPolicy.serializer(), text).also { it.requireValid() }
}
