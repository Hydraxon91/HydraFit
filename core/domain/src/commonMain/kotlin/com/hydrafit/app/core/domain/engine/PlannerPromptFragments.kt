package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.workout.LoadKind

/**
 * Pure prompt fragments shared by the model-backed engines. Each engine assembles its own prompt
 * (order and wording differ), but the equipment, fatigue, deload, volume, and weight-history
 * fragments are formatted here so both prompts stay in lockstep.
 */
object PlannerPromptFragments {

    const val PROGRESSED_WEIGHTS_NOTE =
        "Use these as \"suggestedWeightKg\" unless the history clearly disagrees."

    /** "Available equipment: <display names>" — identical in both model prompts. */
    fun equipmentLine(request: PlanRequest): String =
        "Available equipment: ${equipmentList(request)}"

    /** Per-equipment weight ceilings, or null when no equipment records a maximum. */
    fun equipmentCapsLine(request: PlanRequest): String? {
        if (request.equipmentMaxWeights.isEmpty()) return null
        val caps = request.equipmentMaxWeights.entries
            .sortedBy { it.key.displayName }
            .joinToString("; ") { "${it.key.displayName}: ${it.value}kg" }
        return "Equipment weight limits (do not exceed): $caps"
    }

    /** "<label> (0.0-1.0): <MUSCLE=value, ...>" — the label differs per engine. */
    fun fatigueLine(request: PlanRequest, label: String): String =
        "$label (0.0-1.0): ${fatigueList(request)}"

    /** Periodization position; the deload wording stays in [deloadInstruction]. */
    fun periodizationLine(request: PlanRequest): String =
        "Periodization: week ${request.weekNumber} of cycle ${request.cycleNumber}."

    /** Deload instruction, or null when the week is not a deload. */
    fun deloadInstruction(request: PlanRequest): String? = if (request.isDeload) {
        "This is a deload week: use fewer sets and roughly 80% of the normal working " +
            "weight to allow recovery."
    } else {
        null
    }

    /** Volume/rep guidance derived from the training goal. */
    fun volumeRepsGuidance(request: PlanRequest): String =
        "Use the goal's fixed rep bands: ${request.goal.compoundReps} reps for compound " +
            "exercises and ${request.goal.isolationReps} reps for accessory exercises. The " +
            "selected set count controls volume; do not trade fewer sets for more reps."

    /** Advisory weekly direct-arm coverage; output is assessed but not rejected for missing it. */
    fun directArmCoverageGuidance(request: PlanRequest): String = if (request.isDeload) {
        "This is a deload: do not add work to meet the normal-week direct arm coverage objective."
    } else {
        "Where compatible exercises and session capacity allow, include dedicated biceps and " +
            "triceps isolation work for about ${DirectArmCoverage.TARGET_SETS_PER_MUSCLE} sets " +
            "per muscle across the week. Compound involvement is a separate estimate and does " +
            "not replace this direct-work objective. This is a planning preference, not a " +
            "guarantee of physiological adequacy."
    }

    /** Recent working-weight entries, or null when sharing is off or there is no history. */
    fun recentWeightsList(request: PlanRequest): String? {
        if (!request.includeWorkoutData || request.recentWeights.isEmpty()) return null
        return request.recentWeights.joinToString("; ") { entry ->
            val weight = describeLoad(entry)
            val rir = entry.rir?.let { " (rir $it)" } ?: ""
            val snapshot = if (entry.weekNumber != null && entry.dayIndex != null) {
                " (wk ${entry.weekNumber}, day ${entry.dayIndex + 1})"
            } else {
                ""
            }
            "${entry.exerciseId} ${isoDateUtc(entry.performedAtMillis)}: $weight x ${entry.reps}" +
                rir + snapshot
        }
    }

    /** Progressed starting-weight entries, or null when sharing is off or there are none. */
    fun progressedWeightsList(request: PlanRequest): String? {
        if (!request.includeWorkoutData) return null
        return request.suggestedWeightsKg.entries
            .sortedBy { it.key }
            .joinToString("; ") { "${it.key}: ${it.value}kg" }
            .ifEmpty { null }
    }

    /** Honest wording for a history entry so a model never reads added/legacy load as external. */
    private fun describeLoad(entry: WeightHistoryEntry): String = when (entry.loadKind) {
        LoadKind.EXTERNAL -> entry.weightKg?.let { "${it}kg" } ?: "external load unspecified"
        LoadKind.BODYWEIGHT -> "Bodyweight"
        LoadKind.ADDED -> entry.weightKg?.let { "Bodyweight + ${it}kg added" }
            ?: "Bodyweight + added load (unspecified)"
        LoadKind.LEGACY_UNSPECIFIED ->
            entry.weightKg?.let { "${it}kg (recorded, meaning unconfirmed)" }
                ?: "load unspecified"
    }

    private fun equipmentList(request: PlanRequest): String =
        request.availableEquipment.joinToString(", ") { it.displayName }

    private fun fatigueList(request: PlanRequest): String =
        request.muscleFatigue.entries.joinToString(", ") { "${it.key.name}=${it.value}" }
}
