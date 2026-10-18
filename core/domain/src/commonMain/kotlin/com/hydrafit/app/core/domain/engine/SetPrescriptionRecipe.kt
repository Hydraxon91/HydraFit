package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.math.abs
import kotlin.math.round
import kotlinx.serialization.Serializable

/**
 * The default rounding step for generated loads, matching the planner's own step. It is a product
 * default, not a claim about the smallest meaningful increment on any particular equipment.
 */
const val DEFAULT_SET_PRESCRIPTION_ROUND_TO_KG: Double = 2.5

/**
 * The recipe inputs that produced an ordered prescription, recorded alongside the result so reopening
 * a slot shows exactly what the user entered rather than a shape inferred from the generated targets.
 */
@Serializable
data class SetPrescriptionRecipeSnapshot(
    val shape: SetPrescriptionShape,
    val sets: Int,
    val startReps: Int,
    val endReps: Int,
    val startLoadKg: Double?,
    val endLoadKg: Double?,
    val roundToKg: Double,
    val targetRir: Int? = null
) {
    fun requireValid() {
        require(sets in PrescriptionBounds.MIN_SETS..PrescriptionBounds.MAX_SETS) {
            "A prescription allows between ${PrescriptionBounds.MIN_SETS} and " +
                "${PrescriptionBounds.MAX_SETS} sets"
        }
        require(startReps in PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS) {
            "Reps must be between ${PrescriptionBounds.MIN_REPS} and ${PrescriptionBounds.MAX_REPS}"
        }
        require(endReps in PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS) {
            "Reps must be between ${PrescriptionBounds.MIN_REPS} and ${PrescriptionBounds.MAX_REPS}"
        }
        require(roundToKg.isFinite() && roundToKg > 0.0) { "The rounding step must be positive" }
        require(targetRir == null || targetRir in SetTarget.MIN_RIR..SetTarget.MAX_RIR) {
            "A prescribed RIR must be between ${SetTarget.MIN_RIR} and ${SetTarget.MAX_RIR}"
        }
    }
}

/**
 * The user's opt-in for generating ordered per-set prescriptions at plan time.
 *
 * The rep range is the user's own; the app never invents one. Absent means no automatic ordered
 * prescription, which keeps every existing plan shape unchanged.
 */
data class AutomaticSetPrescription(
    val shape: SetPrescriptionShape,
    val minReps: Int,
    val maxReps: Int,
    /** Prescribed RIR applied to every generated set, or null for no effort target. */
    val targetRir: Int? = null
) {
    fun requireValid() {
        require(minReps in PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS) {
            "Reps must be between ${PrescriptionBounds.MIN_REPS} and ${PrescriptionBounds.MAX_REPS}"
        }
        require(maxReps in minReps..PrescriptionBounds.MAX_REPS) {
            "The highest rep count must not be below the lowest"
        }
        require(targetRir == null || targetRir in SetTarget.MIN_RIR..SetTarget.MAX_RIR) {
            "A prescribed RIR must be between ${SetTarget.MIN_RIR} and ${SetTarget.MAX_RIR}"
        }
    }
}

/** One slot's inputs for an automatic ordered prescription. */
data class AutomaticSetPrescriptionRequest(
    val automatic: AutomaticSetPrescription,
    val sets: Int,
    val reps: Int?,
    val loadKind: LoadKind,
    /** The slot's estimated-1RM bound; without one there is no numeric load to convert. */
    val estimatedOneRepMaxKg: Double?,
    /** The deload intensity scale the planner is already applying to this slot. */
    val intensityScale: Double = 1.0
)

/**
 * Builds a slot's ordered targets from the user's own approved range and the planner's existing
 * conversion.
 *
 * The rep endpoints come from [AutomaticSetPrescription] — never from the app — and each set's load is
 * the same estimated-1RM conversion the planner already uses for that set's reps, so an automatic
 * prescription is not an arbitrary percentage ladder. External-load work only: a non-external slot, a
 * slot without a prescribed rep count, or a slot without an estimated-1RM bound produces no ordered
 * targets, and a caller that produces none leaves the slot exactly as it was.
 */
class ApplySetPrescriptionRecipeUseCase(
    private val weightConfig: SuggestedWeightConfig = SuggestedWeightConfig()
) {

    operator fun invoke(request: AutomaticSetPrescriptionRequest): SlotSetTargets? {
        request.automatic.requireValid()
        if (request.loadKind != LoadKind.EXTERNAL) return null
        val reps = request.reps ?: return null
        if (request.sets !in PrescriptionBounds.MIN_SETS..PrescriptionBounds.MAX_SETS) return null
        val bound = request.estimatedOneRepMaxKg ?: return null
        if (!bound.isFinite() || bound <= 0.0) return null

        val shape = request.automatic.shape
        val targets = (0 until request.sets).map { index ->
            val setReps = repsFor(shape, request.automatic, reps, index, request.sets)
            SetTarget(
                reps = setReps,
                loadKind = LoadKind.EXTERNAL,
                weightKg = roundToGram(
                    weightConfig.workingWeightFor(bound, setReps, request.intensityScale)
                ),
                targetRir = request.automatic.targetRir
            )
        }
        // No recipe is recorded: a recipe describes the manual builder's own inputs, and this
        // prescription was derived from the generation opt-in instead. Recording one would promise a
        // reopen that the manual builder cannot reproduce, because its loads interpolate between
        // endpoints while these are the conversion for each set's own reps. The editor already shows
        // a recipe-less slot's stored targets with empty inputs rather than inventing a shape.
        return SlotSetTargets.of(targets)
    }

    /** This set's rep count: the user's endpoints placed across the set count by the chosen shape. */
    private fun repsFor(
        shape: SetPrescriptionShape,
        automatic: AutomaticSetPrescription,
        prescribedReps: Int,
        index: Int,
        sets: Int
    ): Int {
        if (shape == SetPrescriptionShape.STRAIGHT) return prescribedReps
        val start = if (shape == SetPrescriptionShape.DESCENDING_LOAD) {
            automatic.minReps
        } else {
            automatic.maxReps
        }
        val end = if (shape == SetPrescriptionShape.DESCENDING_LOAD) {
            automatic.maxReps
        } else {
            automatic.minReps
        }
        if (sets == 1) return start
        val fraction = if (shape == SetPrescriptionShape.PYRAMID) {
            1.0 - abs(2.0 * index / (sets - 1) - 1.0)
        } else {
            index.toDouble() / (sets - 1)
        }
        val value = start + (end - start) * fraction
        return round(value).toInt().coerceIn(
            PrescriptionBounds.MIN_REPS,
            PrescriptionBounds.MAX_REPS
        )
    }

    private fun roundToGram(value: Double): Double = round(value * 1_000.0) / 1_000.0
}

/**
 * The supported ordered-prescription shapes.
 *
 * A shape is a configurable recipe, not a claim that any of them is superior. The user chooses the
 * shape and every number in it; nothing here decides a range, a load or an effort target on its own.
 */
enum class SetPrescriptionShape {
    /** Every set repeats the same reps and load. */
    STRAIGHT,

    /** Load climbs and reps fall from the first set to the last. */
    ASCENDING_LOAD,

    /** Load falls and reps climb from the first set to the last. */
    DESCENDING_LOAD,

    /** Load peaks and reps trough at the middle set, returning to the endpoints. */
    PYRAMID
}

/**
 * The inputs for building an ordered prescription.
 *
 * [startReps]/[endReps] and [startLoadKg]/[endLoadKg] describe the recipe's endpoints. For
 * [SetPrescriptionShape.PYRAMID] the "end" values are the middle-set peak and trough instead, and the
 * first and last sets use the start values. Every value is the user's own.
 */
data class SetPrescriptionRequest(
    val shape: SetPrescriptionShape,
    val sets: Int,
    val startReps: Int,
    val endReps: Int,
    val startLoadKg: Double?,
    val endLoadKg: Double?,
    /** The user's rounding step for generated loads; the same step the slot's policy uses. */
    val roundToKg: Double = DEFAULT_SET_PRESCRIPTION_ROUND_TO_KG,
    /** Prescribed RIR applied to every generated set, or null for no effort target. */
    val targetRir: Int? = null
)

/**
 * Builds an ordered prescription from a [SetPrescriptionRequest].
 *
 * Reps interpolate linearly across the set count and loads are rounded to the request's step, so a
 * generated load is a real increment rather than an arbitrary ladder. The builder never derives a
 * load from a percentage of anything: automatic numeric loads come from the existing NSCA conversion
 * elsewhere, and this recipe only places the user's own endpoints.
 */
class BuildSetPrescriptionUseCase {

    operator fun invoke(request: SetPrescriptionRequest): SlotSetTargets {
        require(request.sets in 1..PrescriptionBounds.MAX_SETS) {
            "A prescription allows between 1 and ${PrescriptionBounds.MAX_SETS} sets"
        }
        require(request.startReps in PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS) {
            "Reps must be between ${PrescriptionBounds.MIN_REPS} and ${PrescriptionBounds.MAX_REPS}"
        }
        require(request.endReps in PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS) {
            "Reps must be between ${PrescriptionBounds.MIN_REPS} and ${PrescriptionBounds.MAX_REPS}"
        }
        require(request.roundToKg.isFinite() && request.roundToKg > 0.0) {
            "The rounding step must be positive"
        }
        request.targetRir?.let { rir ->
            require(rir in SetTarget.MIN_RIR..SetTarget.MAX_RIR) {
                "A prescribed RIR must be between ${SetTarget.MIN_RIR} and ${SetTarget.MAX_RIR}"
            }
        }

        val targets = (0 until request.sets).map { index ->
            val fraction = request.fractionFor(index)
            SetTarget(
                reps = interpolateReps(request, fraction),
                durationSeconds = null,
                loadKind = LoadKind.EXTERNAL,
                weightKg = interpolateLoad(request, fraction),
                targetRir = request.targetRir
            )
        }
        return SlotSetTargets.of(
            targets = targets,
            recipe = SetPrescriptionRecipeSnapshot(
                shape = request.shape,
                sets = request.sets,
                startReps = request.startReps,
                endReps = request.endReps,
                startLoadKg = request.startLoadKg,
                endLoadKg = request.endLoadKg,
                roundToKg = request.roundToKg,
                targetRir = request.targetRir
            )
        )
    }

    /** Where this set sits between the recipe's endpoints: 0 at the start, 1 at the far end. */
    private fun SetPrescriptionRequest.fractionFor(index: Int): Double = when {
        shape == SetPrescriptionShape.STRAIGHT -> 0.0
        sets == 1 -> 0.0
        shape == SetPrescriptionShape.PYRAMID ->
            // Peaks at the middle set and returns to the endpoints, so the middle is the far end.
            1.0 - abs(2.0 * index / (sets - 1) - 1.0)

        else -> index.toDouble() / (sets - 1)
    }

    private fun interpolateReps(request: SetPrescriptionRequest, fraction: Double): Int {
        val span = (request.endReps - request.startReps).toDouble() * fraction
        return (request.startReps + span).roundToInt().coerceIn(
            PrescriptionBounds.MIN_REPS,
            PrescriptionBounds.MAX_REPS
        )
    }

    private fun interpolateLoad(request: SetPrescriptionRequest, fraction: Double): Double? {
        val start = request.startLoadKg ?: return null
        val end = request.endLoadKg ?: start
        return roundToStep(start + (end - start) * fraction, request.roundToKg)
    }

    private fun roundToStep(value: Double, step: Double): Double {
        val stepped = round(value / step) * step
        return round(stepped * 1_000.0) / 1_000.0
    }

    private fun Double.roundToInt(): Int = round(this).toInt()
}
