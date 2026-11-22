package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.routine.Superset

/**
 * The user's opt-in for automatic planner pairing, with the rest they chose. A null preference on
 * [PlanRequest] leaves every generated day exactly as the engine produced it.
 */
data class PlannerSupersetPreference(val transitionSeconds: Int, val pairRestSeconds: Int) {
    fun requireValid() {
        Superset(
            key = "planner",
            transitionSeconds = transitionSeconds,
            pairRestSeconds = pairRestSeconds
        )
            .requireValid()
    }
}

/**
 * Curated, opt-in agonist/antagonist pairing for generated plans.
 *
 * Only already-selected biceps/triceps isolation slots are grouped. When a day has both but none
 * adjacent, one is moved next to the other first; nothing is added, dropped or re-prescribed, and a
 * slot already carrying a manual group is left alone. `SeededPlannerQualityTest` keeps the curated
 * ids in step with every plannable seeded biceps and triceps isolation. The curated ids
 * exclude custom exercises and close-grip bench press, which the catalog classifies as a compound
 * `HORIZONTAL_PUSH` rather than an isolation pattern. Grouping changes guidance only; fatigue and
 * progression inputs are untouched.
 */
object PlannerSupersetPairing {
    /** Curated built-in biceps isolation ids. */
    val BICEPS_ISOLATION_IDS: Set<String> = setOf(
        "barbell-curl",
        "dumbbell-curl",
        "band-curl",
        "hammer-curl",
        "preacher-curl",
        "ez-bar-curl",
        "incline-hammer-curls",
        "incline-dumbbell-curl"
    )

    /** Curated built-in triceps isolation ids; the close-grip bench press is deliberately absent. */
    val TRICEPS_ISOLATION_IDS: Set<String> = setOf(
        "triceps-pushdown",
        "overhead-triceps-extension",
        "ez-bar-skullcrusher",
        "tricep-dumbbell-kickback",
        "bench-dips",
        "feet-elevated-bench-dips"
    )

    fun apply(plan: WeeklyPlan, preference: PlannerSupersetPreference?): WeeklyPlan {
        if (preference == null) return plan
        preference.requireValid()
        return plan.copy(
            days = plan.days.map { day ->
                day.copy(exercises = pairDay(day, preference))
            }
        )
    }

    private fun pairDay(
        day: WorkoutDay,
        preference: PlannerSupersetPreference
    ): List<PlannedExercise> {
        val paired = withArmPairAdjacent(day.exercises).toMutableList()
        var index = 0
        while (index < paired.lastIndex) {
            val first = paired[index]
            val second = paired[index + 1]
            val eligible = first.superset == null &&
                second.superset == null &&
                isCuratedPair(first.exerciseId, second.exerciseId)
            if (eligible) {
                val group = Superset(
                    key = "planner-${day.dayIndex}-$index",
                    transitionSeconds = preference.transitionSeconds,
                    pairRestSeconds = preference.pairRestSeconds
                )
                paired[index] = first.copy(superset = group)
                paired[index + 1] = second.copy(superset = group)
                index += 2
            } else {
                index += 1
            }
        }
        return paired
    }

    /**
     * The planner adds arm work by need, so a day's biceps and triceps isolations are often not next
     * to each other and could never pair. When a day has an unpaired curated biceps and triceps slot
     * but no adjacent curated pair, the later of the first two moves to sit right after the earlier.
     * Only the order changes: no slot is added, dropped or re-prescribed.
     */
    private fun withArmPairAdjacent(exercises: List<PlannedExercise>): List<PlannedExercise> {
        val hasAdjacentPair = exercises.zipWithNext().any { (first, second) ->
            first.superset == null &&
                second.superset == null &&
                isCuratedPair(first.exerciseId, second.exerciseId)
        }
        if (hasAdjacentPair) return exercises
        val biceps = exercises.indexOfFirst {
            it.superset == null && it.exerciseId in BICEPS_ISOLATION_IDS
        }
        val triceps = exercises.indexOfFirst {
            it.superset == null && it.exerciseId in TRICEPS_ISOLATION_IDS
        }
        if (biceps < 0 || triceps < 0) return exercises
        val anchor = minOf(biceps, triceps)
        val moved = maxOf(biceps, triceps)
        val reordered = exercises.toMutableList()
        val slot = reordered.removeAt(moved)
        reordered.add(anchor + 1, slot)
        return reordered
    }

    private fun isCuratedPair(first: String, second: String): Boolean =
        (first in BICEPS_ISOLATION_IDS && second in TRICEPS_ISOLATION_IDS) ||
            (first in TRICEPS_ISOLATION_IDS && second in BICEPS_ISOLATION_IDS)
}
