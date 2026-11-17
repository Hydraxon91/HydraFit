package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.OneRepMax
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import kotlinx.coroutines.flow.first

/**
 * How a person's own bodyweight vertical pulls (chin-ups, pull-ups) compare with their externally
 * loaded vertical pulls (pulldowns). Every number is an estimated one-rep max in kilograms from the
 * person's own recent sets; [ratio] is the external level divided by the bodyweight-pull level. It
 * is shown for information only and never feeds a planned weight.
 */
data class BodyweightPullEvidence(
    val bodyWeightKg: Double,
    val bodyweightPullExerciseId: String,
    val bodyweightPullOneRepMaxKg: Double,
    val bodyweightPullSetCount: Int,
    val externalPullExerciseId: String,
    val externalPullOneRepMaxKg: Double,
    val externalPullSetCount: Int
) {
    val ratio: Double get() = externalPullOneRepMaxKg / bodyweightPullOneRepMaxKg
}

/** A recorded counted working set reduced to what the comparison needs. */
data class PullSetSample(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double?,
    val loadKind: LoadKind
)

/** The pure comparison; the use case below only gathers the samples. */
object BodyweightPullComparison {
    /** Epley is unreliable far beyond this many repetitions, so longer sets are ignored. */
    const val MAX_REPS = 15

    fun compare(
        bodyWeightKg: Double?,
        bodyweightSets: List<PullSetSample>,
        externalSets: List<PullSetSample>,
        minSetsPerSide: Int = BuildPlannerLoadInputsUseCase.MIN_RECENT_SETS
    ): BodyweightPullEvidence? {
        if (bodyWeightKg == null || !bodyWeightKg.isFinite() || bodyWeightKg <= 0.0) return null
        val bodyweight = bodyweightSets.mapNotNull { sample ->
            bodyweightEstimate(bodyWeightKg, sample)?.let { sample.exerciseId to it }
        }
        val external = externalSets.mapNotNull { sample ->
            externalEstimate(sample)?.let { sample.exerciseId to it }
        }
        if (bodyweight.size < minSetsPerSide || external.size < minSetsPerSide) return null
        val bestBodyweight = bodyweight.maxBy { it.second }
        val bestExternal = external.maxBy { it.second }
        return BodyweightPullEvidence(
            bodyWeightKg = bodyWeightKg,
            bodyweightPullExerciseId = bestBodyweight.first,
            bodyweightPullOneRepMaxKg = bestBodyweight.second,
            bodyweightPullSetCount = bodyweight.size,
            externalPullExerciseId = bestExternal.first,
            externalPullOneRepMaxKg = bestExternal.second,
            externalPullSetCount = external.size
        )
    }

    private fun bodyweightEstimate(bodyWeightKg: Double, sample: PullSetSample): Double? {
        if (sample.reps !in 1..MAX_REPS) return null
        val load = when (sample.loadKind) {
            LoadKind.BODYWEIGHT -> bodyWeightKg
            LoadKind.ADDED ->
                sample.weightKg
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.let { bodyWeightKg + it }

            else -> null
        } ?: return null
        return OneRepMax.estimate(load, sample.reps)
    }

    private fun externalEstimate(sample: PullSetSample): Double? {
        if (sample.reps !in 1..MAX_REPS || sample.loadKind != LoadKind.EXTERNAL) return null
        val weight = sample.weightKg?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return OneRepMax.estimate(weight, sample.reps)
    }
}

/**
 * Reads the recent working sets of every vertical-pull exercise in the catalog and compares the
 * bodyweight ones with the externally loaded ones. Read-only; returns null whenever there is not
 * enough recorded evidence on both sides, never an average or a guess.
 */
class BuildBodyweightPullEvidenceUseCase(
    private val catalog: ExerciseCatalog,
    private val progress: ExerciseProgressRepository
) {
    suspend operator fun invoke(
        bodyWeightKg: Double?,
        nowMillis: Long,
        windowDays: Long = BuildPlannerLoadInputsUseCase.RECENT_WINDOW_DAYS
    ): BodyweightPullEvidence? {
        if (bodyWeightKg == null) return null
        val cutoff = nowMillis - windowDays * MILLIS_PER_DAY
        val pulls = catalog.all().filter { it.movementPattern == MovementPattern.VERTICAL_PULL }
        val bodyweight = mutableListOf<PullSetSample>()
        val external = mutableListOf<PullSetSample>()
        pulls.forEach { exercise ->
            val target = when (exercise.loadCapability) {
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                ExerciseLoadCapability.BODYWEIGHT_ONLY -> bodyweight

                ExerciseLoadCapability.EXTERNAL -> external
                else -> return@forEach
            }
            progress.observeHistory(ExerciseHistoryRequest(exercise.id, pageSize = PAGE_SIZE))
                .first()
                .sets
                .filter { set ->
                    !set.isWarmup && set.performedAtMillis in cutoff..nowMillis
                }
                .forEach { set ->
                    set.reps?.let { reps ->
                        target += PullSetSample(exercise.id, reps, set.weightKg, set.loadKind)
                    }
                }
        }
        return BodyweightPullComparison.compare(bodyWeightKg, bodyweight, external)
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
