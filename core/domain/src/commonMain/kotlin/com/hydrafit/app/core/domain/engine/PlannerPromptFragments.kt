package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.workout.LoadKind

/**
 * Pure prompt fragments shared by the model-backed engines. Each engine assembles its own prompt
 * (order and wording differ), but the equipment, fatigue, deload, volume, and weight-history
 * fragments are formatted here so both prompts stay in lockstep.
 */
object PlannerPromptFragments {

    const val PROGRESSED_WEIGHTS_NOTE =
        "These are maximums, not working weights: suggest a working weight for the planned reps " +
            "that is well below them. The app lowers any suggestion that is too heavy."

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
        "The goal's rep ranges are ${request.goal.compoundRepRange.first}-" +
            "${request.goal.compoundRepRange.last} reps for compound exercises and " +
            "${request.goal.isolationRepRange.first}-${request.goal.isolationRepRange.last} reps " +
            "for accessory exercises. The app sets each week's exact reps and any held load by " +
            "double progression, and the selected set count controls volume; do not trade fewer " +
            "sets for more reps."

    /** Advisory weekly direct-arm coverage; output is assessed but not rejected for missing it. */
    fun directArmCoverageGuidance(request: PlanRequest): String = if (request.isDeload) {
        "This is a deload: do not add work to meet the normal-week direct arm coverage objective."
    } else {
        "Where compatible exercises and session capacity allow, include dedicated biceps and " +
            "triceps isolation work for about ${
                DirectArmCoverage.targetSetsPerMuscle(request.daysPerWeek, request.goal)
            } sets " +
            "per muscle across the week. Compound involvement is a separate estimate and does " +
            "not replace this direct-work objective. This is a planning preference, not a " +
            "guarantee of physiological adequacy."
    }

    /** How many exercises a day may hold, and which must-haves may go past the ordinary limit. */
    fun exerciseCountGuidance(): String =
        "Give each day up to ${PlannerExerciseCounts.TARGET_MAX_PER_DAY} exercises, and fewer " +
            "when the set count is high and the weekly targets are already met. Only these " +
            "must-haves may take a day up to ${PlannerExerciseCounts.MUST_HAVE_MAX_PER_DAY}: " +
            "direct biceps " +
            "and triceps isolation, one shrug on each PULL or UPPER day, and one leg extension " +
            "plus one leg curl on each LOWER or LEGS day."

    /**
     * The weekly involvement-weighted set target per muscle the deterministic planner uses, so the
     * model balances volume against the same numbers. Muscles without a target are left out.
     */
    fun weeklyTargetsLine(request: PlanRequest): String {
        val base = WeeklyVolumeTargets.forGoal(
            request.goal,
            if (request.isDeload) PeriodizationConfig().deloadVolumeScale else 1.0
        )
        val targets = MuscleGroup.entries
            .mapNotNull { muscle ->
                WeeklyVolumeTargets.forMuscle(base, muscle)?.let { target ->
                    "${muscle.name}=${formatSets(target.targetSets)}"
                }
            }
            .joinToString(", ")
        return "Weekly target sets per muscle (each set counts once per unit of the muscle's " +
            "involvement in the exercise; aim near these, not far above): $targets"
    }

    /** The user's prefer / prefer-less marks as exercise ids, or null when there are none. */
    fun preferencesLine(request: PlanRequest): String? {
        val prefer = request.exercisePreferences
            .filterValues { it == ExercisePreference.PREFER }
            .keys.sorted()
        val preferLess = request.exercisePreferences
            .filterValues { it == ExercisePreference.PREFER_LESS }
            .keys.sorted()
        if (prefer.isEmpty() && preferLess.isEmpty()) return null
        return buildList {
            if (prefer.isNotEmpty()) {
                add("The user prefers these exercises when suitable: ${prefer.joinToString(", ")}.")
            }
            if (preferLess.isNotEmpty()) {
                add(
                    "The user prefers to do less of these; choose them only when nothing equally " +
                        "suitable remains: ${preferLess.joinToString(", ")}."
                )
            }
        }.joinToString(" ")
    }

    /** One decimal at most, so 7.7 stays 7.7 and 11.0 prints as 11. */
    private fun formatSets(sets: Double): String {
        val tenths = kotlin.math.round(sets * 10).toLong()
        return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
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
