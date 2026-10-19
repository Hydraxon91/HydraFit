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
 * Only already-selected, adjacent biceps/triceps isolation slots are grouped: nothing is reordered,
 * added or re-prescribed, and a slot already carrying a manual group is left alone. The curated ids
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
        "supinated-curl",
        "preacher-curl"
    )

    /** Curated built-in triceps isolation ids; the close-grip bench press is deliberately absent. */
    val TRICEPS_ISOLATION_IDS: Set<String> = setOf(
        "triceps-pushdown",
        "overhead-triceps-extension",
        "dumbbell-triceps-overhead-extension"
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
        val exercises = day.exercises
        val paired = exercises.toMutableList()
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

    private fun isCuratedPair(first: String, second: String): Boolean =
        (first in BICEPS_ISOLATION_IDS && second in TRICEPS_ISOLATION_IDS) ||
            (first in TRICEPS_ISOLATION_IDS && second in BICEPS_ISOLATION_IDS)
}
