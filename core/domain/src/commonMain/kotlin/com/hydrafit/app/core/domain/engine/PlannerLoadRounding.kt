package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.LOADING_SCALE
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MAX_LOADING_TICKS
import com.hydrafit.app.core.domain.equipment.calculateDumbbellLoading
import com.hydrafit.app.core.domain.equipment.calculatePlateLoading
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import kotlin.math.floor

/**
 * What happened when a working load was matched against the active profile's saved bars and
 * dumbbells.
 *
 * [NoApplicableInventory] keeps the generic increment rounding exactly as before: no saved
 * implement of the exercise's kind exists, so there is nothing to round onto. [NoReachableLoad]
 * means an implement does apply but cannot supply any load at or below the bound; the caller then
 * withholds the number instead of rounding up or borrowing another load.
 */
sealed interface InventoryLoadResolution {
    data object NoApplicableInventory : InventoryLoadResolution

    /** The heaviest load the saved implement makes at or below the bound, in kilograms. */
    data class Reachable(val weightKg: Double) : InventoryLoadResolution

    data object NoReachableLoad : InventoryLoadResolution
}

/**
 * True when [inventory] holds an implement the exercise can actually be loaded on. Machines and
 * other equipment are not represented in [LoadingInventory], so they stay on the generic estimate.
 */
fun hasApplicableLoadingInventory(
    inventory: LoadingInventory?,
    requiredEquipment: Set<EquipmentTag>
): Boolean {
    if (inventory == null) return false
    return if (EquipmentTag.DUMBBELL in requiredEquipment) {
        inventory.dumbbells.isNotEmpty()
    } else {
        requiredEquipment.any { it in BAR_EQUIPMENT } && inventory.bars.isNotEmpty()
    }
}

/**
 * Resolves the highest reachable load at or below [boundKg], bounded by [ceilingKg] and by the
 * implement's own grid.
 *
 * A named [equipmentId] is authoritative: if it is not in the inventory, the result is
 * [InventoryLoadResolution.NoReachableLoad] rather than another implement's grid. Without one, every
 * compatible implement is tried and the highest reachable value wins. Inventory ticks are native to
 * the profile's unit, so a pound inventory converts the kilogram bound before solving and converts
 * the result back; a kilogram is never compared against raw pound ticks.
 */
fun resolveInventoryLoad(
    inventory: LoadingInventory?,
    equipmentId: String?,
    requiredEquipment: Set<EquipmentTag>,
    boundKg: Double,
    ceilingKg: Double? = null
): InventoryLoadResolution {
    if (inventory == null) return InventoryLoadResolution.NoApplicableInventory
    val unit = unitFor(inventory) ?: return InventoryLoadResolution.NoApplicableInventory
    val ceilingBound = ceilingKg?.let { minOf(boundKg, it) } ?: boundKg
    if (!ceilingBound.isFinite() || ceilingBound <= 0.0) {
        return InventoryLoadResolution.NoReachableLoad
    }
    val targetTicks =
        unit.nativeTicksAtOrBelow(ceilingBound) ?: return InventoryLoadResolution.NoReachableLoad

    if (equipmentId != null) {
        // A named identity is authoritative for both the grid and its kind: a dumbbell exercise
        // naming a bar (or the reverse) has no compatible implement here and must not borrow one.
        val useDumbbells = EquipmentTag.DUMBBELL in requiredEquipment
        val useBars = requiredEquipment.any { it in BAR_EQUIPMENT }
        val reachable = when {
            useDumbbells ->
                inventory.dumbbells
                    .firstOrNull { it.id == equipmentId }
                    ?.let { dumbbellReachableKg(inventory, it.id, targetTicks, unit) }

            useBars ->
                inventory.bars
                    .firstOrNull { it.id == equipmentId }
                    ?.let { barReachableKg(inventory, it.id, targetTicks, unit) }

            else -> null
        }
        return reachable?.let(InventoryLoadResolution::Reachable)
            ?: InventoryLoadResolution.NoReachableLoad
    }

    val useDumbbells = EquipmentTag.DUMBBELL in requiredEquipment
    val useBars = requiredEquipment.any { it in BAR_EQUIPMENT }
    val candidates = buildList {
        if (useDumbbells) {
            inventory.dumbbells.forEach {
                add(dumbbellReachableKg(inventory, it.id, targetTicks, unit))
            }
        }
        if (useBars) {
            inventory.bars.forEach { add(barReachableKg(inventory, it.id, targetTicks, unit)) }
        }
    }
    if (candidates.isEmpty()) return InventoryLoadResolution.NoApplicableInventory
    val best = candidates.filterNotNull().maxOrNull()
        ?: return InventoryLoadResolution.NoReachableLoad
    return InventoryLoadResolution.Reachable(best)
}

/**
 * Re-derives an automatic ordered prescription's numeric loads from each target's own eligible
 * e1RM bound.
 *
 * Eligibility is per target, not per slot: a short target may use the low-rep PR floor while a
 * range target in the same prescription may not. [generatedFromOneRepMaxKg] is the bound generation
 * actually used; a target whose own bound equals it keeps its generation-time number, so generic
 * rounding is unchanged, while a target whose bound differs is recomputed even without an inventory.
 *
 * A target that cannot be reached keeps no number rather than inheriting one from another set. When
 * no compatible implement exists the target keeps its generic increment rounding, clamped to the
 * equipment ceiling.
 */
fun roundOrderedTargetsToInventory(
    targets: SlotSetTargets?,
    inventory: LoadingInventory?,
    equipmentId: String?,
    requiredEquipment: Set<EquipmentTag>,
    estimatedOneRepMaxKg: Double,
    intensityScale: Double,
    ceilingKg: Double?,
    weightConfig: SuggestedWeightConfig,
    /** A short target's low-rep PR floor; ignored for targets of three or more reps. */
    lowRepOneRepMaxKg: Double? = null,
    /**
     * The e1RM bound the supplied targets were generated from, when known. Passing it lets a target
     * with a different eligible bound (for example a short set under a rep-range slot) be recomputed
     * without disturbing the others' exact generation-time numbers.
     */
    generatedFromOneRepMaxKg: Double? = null
): SlotSetTargets? {
    if (targets == null) return null
    if (inventory == null && generatedFromOneRepMaxKg == null) return targets
    val rebuilt = targets.targets.map { target ->
        val reps = target.reps
        if (target.loadKind != LoadKind.EXTERNAL || reps == null || target.weightKg == null) {
            target
        } else {
            val oneRepMax = oneRepMaxBoundForReps(
                reps = reps,
                progressedBaselineKg = estimatedOneRepMaxKg.takeIf { it > 0.0 },
                lowRepManualPrFloorKg = lowRepOneRepMaxKg
            ) ?: return@map target.copy(weightKg = null)
            val unrounded = oneRepMax *
                weightConfig.intensityForReps(reps) * intensityScale
            when (
                val resolution = resolveInventoryLoad(
                    inventory = inventory,
                    equipmentId = equipmentId,
                    requiredEquipment = requiredEquipment,
                    boundKg = unrounded,
                    ceilingKg = ceilingKg
                )
            ) {
                InventoryLoadResolution.NoApplicableInventory ->
                    if (generatedFromOneRepMaxKg != null && oneRepMax == generatedFromOneRepMaxKg) {
                        target
                    } else {
                        target.copy(
                            weightKg = EquipmentWeightLimit.clamp(
                                weightConfig.roundToIncrement(unrounded),
                                ceilingKg
                            )
                        )
                    }

                is InventoryLoadResolution.Reachable ->
                    target.copy(weightKg = resolution.weightKg)

                InventoryLoadResolution.NoReachableLoad -> target.copy(weightKg = null)
            }
        }
    }
    return SlotSetTargets.of(rebuilt, targets.recipe)
}

private fun unitFor(inventory: LoadingInventory): WeightUnit? = when (inventory.unit) {
    WeightUnit.KG.label.uppercase() -> WeightUnit.KG
    WeightUnit.LB.label.uppercase() -> WeightUnit.LB
    else -> null
}

/** The bound in native ticks, floored so a reachable load can never exceed it. */
private fun WeightUnit.nativeTicksAtOrBelow(kilograms: Double): Int? {
    val display = kilogramsToDisplay(kilograms)
    if (!display.isFinite() || display < 0.0) return null
    val ticks = floor(display * LOADING_SCALE + TICK_EPSILON).toLong()
    return ticks.coerceAtMost(MAX_LOADING_TICKS.toLong()).toInt()
}

private fun WeightUnit.kilogramsFromTicks(ticks: Int): Double =
    displayToKilograms(ticks.toDouble() / LOADING_SCALE)

private fun barReachableKg(
    inventory: LoadingInventory,
    barId: String,
    targetTicks: Int,
    unit: WeightUnit
): Double? = calculatePlateLoading(inventory, barId, targetTicks)
    .let { it.exact ?: it.lower }
    ?.let { unit.kilogramsFromTicks(it.totalTicks) }
    ?.takeIf { it > 0.0 }

private fun dumbbellReachableKg(
    inventory: LoadingInventory,
    dumbbellId: String,
    targetTicks: Int,
    unit: WeightUnit
): Double? = calculateDumbbellLoading(inventory, dumbbellId, targetTicks)
    .let { it.exact ?: it.lower }
    ?.let { unit.kilogramsFromTicks(it.perDumbbellTicks) }
    ?.takeIf { it > 0.0 }

private val BAR_EQUIPMENT = setOf(EquipmentTag.BARBELL, EquipmentTag.EZ_BAR, EquipmentTag.TRAP_BAR)

/**
 * The e1RM bound one target may use. A manual PR below
 * [BuildPlannerLoadInputsUseCase.MIN_MANUAL_PR_REPS_FOR_FLOOR] reps is a legitimate floor only for a
 * short prescription; it stays reference-only and cannot lift a rep-range target.
 */
fun oneRepMaxBoundForReps(
    reps: Int?,
    progressedBaselineKg: Double?,
    lowRepManualPrFloorKg: Double?
): Double? = if (reps != null &&
    reps < BuildPlannerLoadInputsUseCase.MIN_MANUAL_PR_REPS_FOR_FLOOR
) {
    listOfNotNull(progressedBaselineKg, lowRepManualPrFloorKg).maxOrNull()
} else {
    progressedBaselineKg
}

/**
 * How an automatic external slot resolved its numeric target: on a saved implement's grid, on the
 * generic estimate, withheld because the saved implement cannot reach the bound, or withheld for
 * lack of recorded evidence. Derived for display only; it is never stored, so no plan or backup
 * encoding grows a new reason.
 */
enum class LoadInventoryOutcome {
    INVENTORY_ROUNDED,
    ESTIMATE_WITHOUT_INVENTORY,
    NO_REACHABLE_LOAD,
    INSUFFICIENT_RECENT_EVIDENCE
}

/**
 * Derives one slot's [LoadInventoryOutcome] from the existing explanation fields. A withheld load is
 * distinguished by whether the app had converted a working number at all: a conversion that produced
 * no reachable target is the inventory's limit, while no conversion means no evidence.
 */
fun loadInventoryOutcome(
    suggestedWeightKg: Double?,
    unavailableReason: LoadUnavailableReason?,
    hasConvertedWorkingLoad: Boolean,
    applicableInventory: Boolean
): LoadInventoryOutcome? = when {
    suggestedWeightKg != null ->
        if (applicableInventory) {
            LoadInventoryOutcome.INVENTORY_ROUNDED
        } else {
            LoadInventoryOutcome.ESTIMATE_WITHOUT_INVENTORY
        }

    unavailableReason == LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE ->
        LoadInventoryOutcome.INSUFFICIENT_RECENT_EVIDENCE

    hasConvertedWorkingLoad -> LoadInventoryOutcome.NO_REACHABLE_LOAD
    else -> null
}

/** Absorbs binary-float noise without letting a target climb past the bound. */
private const val TICK_EPSILON = 1e-6

/**
 * The candidate exercise's own prescribed load for one rep count, with the provenance the
 * deterministic engine records for a generated target.
 *
 * [weightKg] is null when the candidate has no usable evidence for this rep count or when its
 * external evidence is withheld. A bodyweight-addable candidate may carry its recorded added load
 * instead. The value is never taken from the slot being replaced.
 */
data class CandidatePrescribedLoad(
    val weightKg: Double?,
    val loadKind: LoadKind,
    val explanation: ExerciseTargetExplanation
)

/**
 * Converts the candidate's own progressed baseline at [reps] exactly the way generation does: the
 * suggested e1RM bound capped by any active recent-weight cap, the deload intensity scale, the
 * automatic-load capability gate and the equipment ceiling.
 *
 * Shared by accepted-plan substitution and occurrence replacement so the two cannot drift. The
 * replaced slot's stored load is not an input here.
 */
fun resolveCandidatePrescribedLoad(
    candidateId: String,
    capability: ExerciseLoadCapability,
    reps: Int?,
    request: PlanRequest,
    equipmentCeilingKg: Double?,
    intensityScale: Double,
    weightConfig: SuggestedWeightConfig,
    requiredEquipment: Set<EquipmentTag> = emptySet(),
    equipmentId: String? = null
): CandidatePrescribedLoad {
    val allowed = WorkoutLoadPolicy.allowsAutomaticLoad(capability)
    val withheld = candidateId in request.withheldWeightExerciseIds
    val progressedBaseline = if (allowed && !withheld) {
        request.suggestedWeightsKg[candidateId]?.let { suggested ->
            request.recentWeightCaps[candidateId]?.let { cap -> minOf(suggested, cap) } ?: suggested
        }
    } else {
        null
    }
    val lowRepFloor = if (allowed &&
        reps != null &&
        reps < BuildPlannerLoadInputsUseCase.MIN_MANUAL_PR_REPS_FOR_FLOOR
    ) {
        request.lowRepManualFloorsKg[candidateId]
    } else {
        null
    }
    val baseline = if (allowed) {
        oneRepMaxBoundForReps(reps, progressedBaseline, lowRepFloor)
    } else {
        null
    }
    val unroundedWorking = if (reps != null) {
        baseline?.let { it * weightConfig.intensityForReps(reps) * intensityScale }
    } else {
        null
    }
    // The conversion record keeps the generic increment rounding it always described; inventory
    // reachability is a separate stage applied after that conversion.
    val uncappedWorking = unroundedWorking?.let(weightConfig::roundToIncrement)
    val weightKg = unroundedWorking?.let { working ->
        when (
            val resolution = resolveInventoryLoad(
                inventory = request.loadingInventory,
                equipmentId = equipmentId,
                requiredEquipment = requiredEquipment,
                boundKg = working,
                ceilingKg = equipmentCeilingKg
            )
        ) {
            InventoryLoadResolution.NoApplicableInventory ->
                EquipmentWeightLimit.clamp(requireNotNull(uncappedWorking), equipmentCeilingKg)

            is InventoryLoadResolution.Reachable -> resolution.weightKg
            InventoryLoadResolution.NoReachableLoad -> null
        }
    }
    // Added load is the app's recorded number, so a replacement keeps the same value generation
    // would prescribe; it is never scaled by the external-load intensity or deload curve.
    val addedWeightKg =
        if (!allowed && capability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE && reps != null) {
            request.suggestedAddedLoadsKg[candidateId]
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let(weightConfig::roundToIncrement)
        } else {
            null
        }
    val origin = request.loadExplanations[candidateId]
    val conversion = if (uncappedWorking != null && reps != null) {
        LoadConversionExplanation(
            reps = reps,
            intensityFraction = weightConfig.intensityForReps(reps),
            deloadIntensityScale = intensityScale,
            roundToKg = weightConfig.roundToKg,
            uncappedWorkingKg = uncappedWorking,
            equipmentCeilingKg = equipmentCeilingKg,
            equipmentCeilingBinds = equipmentCeilingKg != null &&
                uncappedWorking > equipmentCeilingKg
        )
    } else {
        null
    }
    val unavailableReason = when {
        addedWeightKg != null -> null
        capability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE ->
            LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
        !allowed -> LoadUnavailableReason.NOT_LOAD_TRACKED
        reps != null && baseline == null -> LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
        else -> null
    }
    return CandidatePrescribedLoad(
        weightKg = weightKg ?: addedWeightKg,
        loadKind = when {
            weightKg != null -> LoadKind.EXTERNAL
            addedWeightKg != null -> LoadKind.ADDED
            else -> WorkoutLoadPolicy.defaultKind(capability)
        },
        explanation = ExerciseTargetExplanation(
            baseline = origin?.baseline,
            progression = origin?.progression,
            conversion = conversion,
            unavailableReason = unavailableReason
        )
    )
}

/**
 * Recalculates a slot's ordered per-set targets for a replacement candidate.
 *
 * Each set keeps its own measure and prescribed effort; only the numeric external load is looked up
 * again from the candidate's baseline at that set's reps. The manual recipe is dropped because it
 * described the replaced exercise's inputs. When the candidate cannot carry the targets' load kind,
 * the whole ordered prescription is cleared rather than keeping loads that mean something else. The
 * list size always stays equal to the slot's set count.
 */
fun recalculatedOrderedTargets(
    targets: SlotSetTargets?,
    capability: ExerciseLoadCapability,
    candidateId: String,
    request: PlanRequest,
    equipmentCeilingKg: Double?,
    intensityScale: Double,
    weightConfig: SuggestedWeightConfig,
    requiredEquipment: Set<EquipmentTag> = emptySet(),
    equipmentId: String? = null
): SlotSetTargets? {
    if (targets == null) return null
    if (targets.targets.any { !WorkoutLoadPolicy.permits(capability, it.loadKind) }) return null
    val rebuilt = targets.targets.map { target ->
        val weight = if (target.loadKind == LoadKind.EXTERNAL) {
            resolveCandidatePrescribedLoad(
                candidateId = candidateId,
                capability = capability,
                reps = target.reps,
                request = request,
                equipmentCeilingKg = equipmentCeilingKg,
                intensityScale = intensityScale,
                weightConfig = weightConfig,
                requiredEquipment = requiredEquipment,
                equipmentId = equipmentId
            ).weightKg
        } else {
            null
        }
        target.copy(weightKg = weight)
    }
    return SlotSetTargets.of(rebuilt)
}
