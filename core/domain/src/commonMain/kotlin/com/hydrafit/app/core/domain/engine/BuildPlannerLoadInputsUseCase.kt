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
    /**
     * Recorded added load advanced by the same success/miss streak rule, per bodyweight-addable
     * exercise id. No estimated-1RM conversion and no body mass participate.
     */
    val suggestedAddedLoadsKg: Map<String, Double> = emptyMap(),
    /** External-load exercises without sufficient evidence must not receive model-invented loads. */
    val withheldWeightExerciseIds: Set<String> = emptySet(),
    /** Recorded baseline/progression provenance per exercise id; never affects selection. */
    val loadExplanations: Map<String, LoadOriginExplanation> = emptyMap(),
    /**
     * A manual PR of fewer than [MIN_MANUAL_PR_REPS_FOR_FLOOR] reps, progressed like the baseline.
     * It is reference-only for rep-range prescriptions and only supplies a short prescription's
     * floor, so it is kept apart from the baseline instead of being dropped.
     */
    val lowRepManualFloorsKg: Map<String, Double> = emptyMap()
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
 * The recent baseline is session-balanced: each session votes with its best qualifying set and the
 * median vote wins, so one loose set or one unusually heavy session cannot outrank a longer, more
 * consistent history. A manual PR raises that baseline only when it records at least three reps.
 *
 * Added load follows a separate, narrower rule (EX-02-A): the latest recorded added load is the
 * baseline, and the same streak increment advances it. Nothing is estimated from reps, and body mass
 * is neither read nor requested. Station-attributed work never seeds either baseline.
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
        pauseIncrements: Boolean,
        utcOffsetMillis: Long,
        nowMillis: Long
    ): PlannerLoadInputs {
        val capabilityByExercise = catalog.all().associate { it.id to it.loadCapability }
        fun capabilityOf(exerciseId: String) =
            capabilityByExercise[exerciseId] ?: ExerciseLoadCapability.UNSPECIFIED
        fun contributesToLoadMath(exerciseId: String, kind: LoadKind): Boolean =
            WorkoutLoadPolicy.contributesToLoadMath(capabilityOf(exerciseId), kind)

        // A station-attributed set belongs to one physical stack and must never seed the generic
        // exercise baseline or progression. Withholding the number is safer than mixing stations.
        val loadSets = sources.loggedWorkoutSets.filter {
            it.station == null &&
                it.cluster == null &&
                it.performedAtMillis <= nowMillis &&
                contributesToLoadMath(it.exerciseId, it.loadKind)
        }
        val contributes = { id: String, kind: LoadKind -> contributesToLoadMath(id, kind) }
        val recentQualifyingSets = recentQualifyingSets(loadSets, nowMillis, contributes)
        val recentEstimates = recentQualifyingSets
            .filterValues { it.size >= MIN_RECENT_SETS }
            .mapValues { (_, group) -> sessionBalancedEstimate(group) }
        val eligibleManualRecords = sources.personalRecords
            .filter { record ->
                record.loadKind == LoadKind.EXTERNAL &&
                    contributesToLoadMath(record.exerciseId, record.loadKind) &&
                    record.weightKg.isFinite() &&
                    record.weightKg > 0.0 &&
                    weightConfig.hasUsableEstimate(record.reps)
            }
        val manualRecords = eligibleManualRecords
            .filter { it.reps >= MIN_MANUAL_PR_REPS_FOR_FLOOR }
            .groupBy { it.exerciseId }
            .mapValues { (_, records) ->
                records.maxOf { OneRepMax.estimate(it.weightKg, it.reps) }
            }
        // A ≤2-rep PR stays reference-only for a 3+-rep prescription, but it is the floor a short
        // prescription may legitimately use. It is carried separately instead of being dropped.
        val lowRepManualRecords = eligibleManualRecords
            .filter { it.reps < MIN_MANUAL_PR_REPS_FOR_FLOOR }
            .groupBy { it.exerciseId }
            .mapValues { (_, records) ->
                records.maxOf { OneRepMax.estimate(it.weightKg, it.reps) }
            }
        val externalExerciseIds = capabilityByExercise
            .filterValues { WorkoutLoadPolicy.allowsAutomaticLoad(it) }
            .keys
        val baselineExplanations = externalExerciseIds.mapNotNull { exerciseId ->
            val recentEstimate = recentEstimates[exerciseId]
            val manualFloor = manualRecords[exerciseId]
            val resolved = recentEstimate ?: manualFloor ?: return@mapNotNull null
            // The source names the input that determined the baseline, so it cannot contradict the
            // value: a higher manual floor is the source, not merely an accompanying record.
            val source = if (recentEstimate == null ||
                (manualFloor != null && manualFloor > recentEstimate)
            ) {
                LoadBaselineSource.MANUAL_PERSONAL_RECORD
            } else {
                LoadBaselineSource.RECENT_PERFORMANCE
            }
            exerciseId to LoadBaselineExplanation(
                source = source,
                recentEstimateKg = recentEstimate,
                qualifyingSetCount = recentQualifyingSets[exerciseId]?.size ?: 0,
                manualFloorKg = manualFloor,
                baselineKg = maxOf(resolved, manualFloor ?: 0.0)
            )
        }.toMap()
        val baseline = baselineExplanations.mapValues { (_, explanation) -> explanation.baselineKg }

        val resolved = progressWeights.resolve(
            baseline = baseline,
            history = sources.progressionHistory,
            pauseIncrements = pauseIncrements,
            nowMillis = nowMillis
        )
        val progressed = resolved.mapValues { (_, value) -> value.progressedKg }
        val loadExplanations = baselineExplanations.mapValues { (exerciseId, explanation) ->
            LoadOriginExplanation(
                baseline = explanation,
                progression = resolved.getValue(exerciseId).explanation
            )
        }

        val recentWeights = if (sources.workoutDataSharingEnabled) {
            buildRecentWeights(
                sources.loggedWorkoutSets.filter {
                    it.station == null && it.cluster == null && it.performedAtMillis <= nowMillis
                },
                utcOffsetMillis
            )
        } else {
            emptyList()
        }
        // The short-prescription floor follows the same progression rule as the baseline, so a
        // low-rep PR and the rep-range prescription cannot disagree about earned increments.
        val lowRepManualFloorsKg = progressWeights.resolve(
            baseline = lowRepManualRecords,
            history = sources.progressionHistory,
            pauseIncrements = pauseIncrements,
            nowMillis = nowMillis
        ).mapValues { (_, value) -> value.progressedKg }
        // Added load is not an estimate: the latest recorded added load is continued by the same
        // comparable streak rule, so a bodyweight-addable exercise can be prescribed numerically
        // without inventing an e1RM or reading body mass.
        val addedExerciseIds = capabilityByExercise
            .filterValues { it == ExerciseLoadCapability.BODYWEIGHT_ADDABLE }
            .keys
        val addedBaselines = addedLoadBaselines(
            sets = sources.loggedWorkoutSets,
            exerciseIds = addedExerciseIds,
            nowMillis = nowMillis
        )
        val suggestedAddedLoadsKg = progressWeights.resolve(
            baseline = addedBaselines,
            history = sources.progressionHistory,
            pauseIncrements = pauseIncrements,
            nowMillis = nowMillis,
            loadKind = LoadKind.ADDED
        ).mapValues { (_, value) -> value.progressedKg }
        // The progressed estimate is the same bound used by deterministic and model-backed engines.
        val recentWeightCaps = progressed
        val withheldWeightExerciseIds = externalExerciseIds - progressed.keys
        return PlannerLoadInputs(
            suggestedWeightsKg = progressed,
            recentWeights = recentWeights,
            recentWeightCaps = recentWeightCaps,
            suggestedAddedLoadsKg = suggestedAddedLoadsKg,
            withheldWeightExerciseIds = withheldWeightExerciseIds,
            loadExplanations = loadExplanations,
            lowRepManualFloorsKg = lowRepManualFloorsKg
        )
    }

    /**
     * The added-load baseline per exercise: its most recently recorded qualifying added set.
     *
     * A set qualifies only when it names a bodyweight-addable exercise, is not station-attributed,
     * warm-up or future work, records a positive finite added load and a rep count, and carries no
     * duration, distance or force measurement. Recency is by performance time, with the stored id
     * breaking ties so the choice is deterministic. The baseline is the recorded number itself; no
     * rep-count or body-mass conversion runs here.
     */
    private fun addedLoadBaselines(
        sets: List<WorkoutSet>,
        exerciseIds: Set<String>,
        nowMillis: Long
    ): Map<String, Double> = sets
        .filter { set ->
            set.exerciseId in exerciseIds &&
                set.station == null &&
                set.performedAtMillis <= nowMillis &&
                !set.isWarmup &&
                set.loadKind == LoadKind.ADDED &&
                set.reps != null &&
                set.durationSeconds == null &&
                set.distanceMeters == null &&
                set.forceNewtons == null &&
                set.weightKg?.let { it.isFinite() && it > 0.0 } == true
        }
        .groupBy { it.exerciseId }
        .mapValues { (_, exerciseSets) ->
            val latest = exerciseSets.maxWith(
                compareBy<WorkoutSet> { it.performedAtMillis }.thenBy { it.id }
            )
            requireNotNull(latest.weightKg)
        }

    /**
     * The qualifying external working sets per exercise inside the recent window: a positive finite
     * weight and a usable rep count. Callers apply [MIN_RECENT_SETS], which counts sets rather than
     * sessions and imposes no minimum session count. The window, sample threshold and rep limit are
     * product defaults, not validated measures of current capacity.
     */
    private fun recentQualifyingSets(
        sets: List<WorkoutSet>,
        nowMillis: Long,
        contributesToLoadMath: (String, LoadKind) -> Boolean
    ): Map<String, List<WorkoutSet>> {
        val cutoff = nowMillis - RECENT_WINDOW_MILLIS
        return sets
            .filter { set ->
                val reps = set.reps
                set.performedAtMillis in cutoff..nowMillis &&
                    contributesToLoadMath(set.exerciseId, set.loadKind) &&
                    !set.isWarmup &&
                    reps != null &&
                    set.weightKg?.let { it.isFinite() && it > 0.0 } == true &&
                    weightConfig.hasUsableEstimate(reps)
            }
            .groupBy { it.exerciseId }
    }

    /**
     * The recent baseline for one exercise, balanced across sessions rather than across sets: each
     * session contributes its single best qualifying estimate, and the baseline is the median of
     * those votes.
     *
     * A per-set maximum let one loose set outrank several cleaner sessions, and a per-set median
     * would let one high-volume session cast most of the votes. One vote per session keeps a
     * straight-set session from dominating and an ascending session from being counted repeatedly.
     *
     * Votes are sorted ascending and an even count averages the two middle values, so the result is
     * deterministic and independent of set order.
     *
     * A qualifying set with no session id cannot be attributed to a session, so each such set casts
     * its own vote. Merging all unattributed rows would stack unrelated work into one strong vote
     * that could outrank a real session; dropping them would discard recorded evidence.
     */
    private fun sessionBalancedEstimate(sets: List<WorkoutSet>): Double {
        val sessionBestEstimates = sets
            .filter { it.sessionId != null }
            .groupBy { requireNotNull(it.sessionId) }
            .values
            .map { sessionSets -> sessionSets.maxOf(::estimateOf) }
        val unattributedEstimates = sets.filter { it.sessionId == null }.map(::estimateOf)
        return medianOf(sessionBestEstimates + unattributedEstimates)
    }

    private fun estimateOf(set: WorkoutSet): Double =
        OneRepMax.estimate(requireNotNull(set.weightKg), requireNotNull(set.reps))

    private fun medianOf(estimates: List<Double>): Double {
        val sorted = estimates.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    companion object {
        /** Product default recent-performance window; not a research-derived prescription horizon. */
        const val RECENT_WINDOW_DAYS = 42L

        /** Product default minimum recent sample; not proof of maximum capacity. Counts sets. */
        const val MIN_RECENT_SETS = 2

        /**
         * Product default: a manual PR recorded below this rep count is a low-rep peak attempt
         * rather than demonstrated working capacity, so it does not raise the automatic load
         * baseline. It remains a stored record and is never rewritten. Not a validated threshold.
         */
        const val MIN_MANUAL_PR_REPS_FOR_FLOOR = 3

        const val RECENT_WINDOW_MILLIS = RECENT_WINDOW_DAYS * 24L * 60L * 60L * 1000L
    }
}
