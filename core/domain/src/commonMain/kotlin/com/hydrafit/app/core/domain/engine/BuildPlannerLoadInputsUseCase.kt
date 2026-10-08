package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutSet

/** The load-derived fields of a plan request: the progressed baseline and the recent history. */
data class PlannerLoadInputs(
    val suggestedWeightsKg: Map<String, Double>,
    val recentWeights: List<WeightHistoryEntry>,
    /** Progression-adjusted e1RM bound shared by all engines. */
    val recentWeightCaps: Map<String, Double> = emptyMap(),
    /** External-load exercises without sufficient evidence must not receive model-invented loads. */
    val withheldWeightExerciseIds: Set<String> = emptySet()
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
    private val buildRecentWeights: BuildRecentWeightsUseCase,
    private val progressWeights: ProgressWeightsUseCase,
    private val weightConfig: SuggestedWeightConfig = SuggestedWeightConfig()
) {
    suspend operator fun invoke(
        sources: WorkoutPlanSources,
        latestPlan: AcceptedPlan?,
        pauseIncrements: Boolean,
        utcOffsetMillis: Long,
        nowMillis: Long
    ): PlannerLoadInputs {
        val capabilityByExercise = catalog.all().associate { it.id to it.loadCapability }
        fun capabilityOf(exerciseId: String) =
            capabilityByExercise[exerciseId] ?: ExerciseLoadCapability.UNSPECIFIED
        fun contributesToLoadMath(exerciseId: String, kind: LoadKind): Boolean =
            WorkoutLoadPolicy.contributesToLoadMath(capabilityOf(exerciseId), kind)

        val loadSets = sources.loggedWorkoutSets.filter {
            it.performedAtMillis <= nowMillis && contributesToLoadMath(it.exerciseId, it.loadKind)
        }
        val recentBest = recentBestByExercise(loadSets, nowMillis) { id, kind ->
            contributesToLoadMath(id, kind)
        }
        val manualRecords = sources.personalRecords
            .filter { record ->
                record.loadKind == LoadKind.EXTERNAL &&
                    contributesToLoadMath(record.exerciseId, record.loadKind) &&
                    record.weightKg.isFinite() &&
                    record.weightKg > 0.0 &&
                    weightConfig.hasUsableEstimate(record.reps)
            }
            .groupBy { it.exerciseId }
            .mapValues { (_, records) ->
                records.maxOf { OneRepMax.estimate(it.weightKg, it.reps) }
            }
        val externalExerciseIds = capabilityByExercise
            .filterValues { WorkoutLoadPolicy.allowsAutomaticLoad(it) }
            .keys
        val baseline = externalExerciseIds.mapNotNull { exerciseId ->
            val estimate = recentBest[exerciseId] ?: manualRecords[exerciseId]
                ?: return@mapNotNull null
            exerciseId to maxOf(estimate, manualRecords[exerciseId] ?: 0.0)
        }.toMap()

        val progressed = progressWeights(
            baseline = baseline,
            prescriptions = externalPrescriptions(latestPlan),
            sets = loadSets.filter { it.loadKind == LoadKind.EXTERNAL },
            pauseIncrements = pauseIncrements,
            utcOffsetMillis = utcOffsetMillis
        )

        val recentWeights = if (sources.workoutDataSharingEnabled) {
            buildRecentWeights(
                sources.loggedWorkoutSets.filter { it.performedAtMillis <= nowMillis },
                utcOffsetMillis
            )
        } else {
            emptyList()
        }
        // The progressed estimate is the same bound used by deterministic and model-backed engines.
        val recentWeightCaps = progressed
        val withheldWeightExerciseIds = externalExerciseIds - progressed.keys
        return PlannerLoadInputs(
            suggestedWeightsKg = progressed,
            recentWeights = recentWeights,
            recentWeightCaps = recentWeightCaps,
            withheldWeightExerciseIds = withheldWeightExerciseIds
        )
    }

    /**
     * The best Epley estimate per exercise among qualifying external working sets inside the recent
     * window. Exercises with fewer than [MIN_RECENT_SETS] such sets are omitted. The window and
     * sample threshold are product defaults, not validated measures of current capacity.
     */
    private fun recentBestByExercise(
        sets: List<WorkoutSet>,
        nowMillis: Long,
        contributesToLoadMath: (String, LoadKind) -> Boolean
    ): Map<String, Double> {
        val cutoff = nowMillis - RECENT_WINDOW_MILLIS
        return sets
            .filter { set ->
                set.performedAtMillis in cutoff..nowMillis &&
                    contributesToLoadMath(set.exerciseId, set.loadKind) &&
                    !set.isWarmup &&
                    set.weightKg?.let { it.isFinite() && it > 0.0 } == true &&
                    weightConfig.hasUsableEstimate(set.reps)
            }
            .groupBy { it.exerciseId }
            .filterValues { it.size >= MIN_RECENT_SETS }
            .mapValues { (_, group) -> group.maxOf { OneRepMax.estimate(it.weightKg!!, it.reps) } }
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

    companion object {
        /** Product default recent-performance window; not a research-derived prescription horizon. */
        const val RECENT_WINDOW_DAYS = 42L

        /** Product default minimum recent sample; not proof of maximum capacity. */
        const val MIN_RECENT_SETS = 2

        const val RECENT_WINDOW_MILLIS = RECENT_WINDOW_DAYS * 24L * 60L * 60L * 1000L
    }
}
