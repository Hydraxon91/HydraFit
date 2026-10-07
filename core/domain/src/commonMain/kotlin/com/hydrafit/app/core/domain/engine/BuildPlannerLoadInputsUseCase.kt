package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy

/** The load-derived fields of a plan request: the progressed baseline and the recent history. */
data class PlannerLoadInputs(
    val suggestedWeightsKg: Map<String, Double>,
    val recentWeights: List<WeightHistoryEntry>
)

/**
 * Builds the load half of a [PlanRequest] from the workout sources and the latest accepted plan.
 *
 * Only load that the current catalog says is external resistance contributes to the baseline and the
 * progression target: a legacy number counts only while the exercise is external today (the approved
 * read-time compatibility exception), and added or bodyweight work never seeds a numeric suggestion.
 * Progression compares only typed external performances against typed external prescriptions, so a
 * pre-EX-02 accepted plan seeds the baseline but not a streak. Stored rows are never rewritten.
 *
 * Grouped here rather than in the observer so the observer stays within its dependency budget.
 */
class BuildPlannerLoadInputsUseCase(
    private val catalog: ExerciseCatalog,
    private val suggestWeights: SuggestWeightsUseCase,
    private val buildRecentWeights: BuildRecentWeightsUseCase,
    private val progressWeights: ProgressWeightsUseCase
) {
    suspend operator fun invoke(
        sources: WorkoutPlanSources,
        latestPlan: AcceptedPlan?,
        pauseIncrements: Boolean,
        utcOffsetMillis: Long
    ): PlannerLoadInputs {
        val capabilityByExercise = catalog.all().associate { it.id to it.loadCapability }
        fun capabilityOf(exerciseId: String) =
            capabilityByExercise[exerciseId] ?: ExerciseLoadCapability.UNSPECIFIED
        fun contributesToLoadMath(exerciseId: String, kind: LoadKind): Boolean =
            WorkoutLoadPolicy.contributesToLoadMath(capabilityOf(exerciseId), kind)

        // A logged set's Epley 1RM, lifted to at least any manually entered personal record. Only
        // load that is external resistance (or a compatible legacy number) enters the baseline.
        val baseline = suggestWeights(
            sources.loggedWorkoutSets.filter { contributesToLoadMath(it.exerciseId, it.loadKind) }
        ).toMutableMap()
        sources.personalRecords
            .filter { contributesToLoadMath(it.exerciseId, it.loadKind) }
            .forEach { record ->
                baseline[record.exerciseId] = maxOf(
                    baseline[record.exerciseId] ?: 0.0,
                    OneRepMax.estimate(record.weightKg, record.reps)
                )
            }

        val progressed = progressWeights(
            baseline = baseline,
            prescriptions = externalPrescriptions(latestPlan),
            sets = sources.loggedWorkoutSets.filter { it.loadKind == LoadKind.EXTERNAL },
            pauseIncrements = pauseIncrements,
            utcOffsetMillis = utcOffsetMillis
        )

        val recentWeights = if (sources.workoutDataSharingEnabled) {
            buildRecentWeights(sources.loggedWorkoutSets, utcOffsetMillis)
        } else {
            emptyList()
        }
        return PlannerLoadInputs(suggestedWeightsKg = progressed, recentWeights = recentWeights)
    }

    /**
     * The typed external targets each exercise was prescribed in the most recent accepted plan.
     * Pre-EX-02 (legacy) prescriptions are excluded, so they cannot fabricate a completed streak.
     */
    private fun externalPrescriptions(plan: AcceptedPlan?): Map<String, Prescription> = plan
        ?.days
        ?.flatMap { it.exercises }
        ?.mapNotNull { exercise ->
            if (exercise.loadKind != LoadKind.EXTERNAL) return@mapNotNull null
            val weight = exercise.suggestedWeightKg ?: return@mapNotNull null
            exercise.exerciseId to Prescription(
                exerciseId = exercise.exerciseId,
                sets = exercise.sets,
                reps = exercise.reps,
                weightKg = weight
            )
        }
        ?.toMap()
        .orEmpty()
}
