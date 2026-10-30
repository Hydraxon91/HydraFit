package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import kotlin.math.roundToInt

/**
 * Shared guard for model-backed plans. It drops exercises that are unknown or need unselected
 * equipment, applies the requested set count and the compound/isolation rep scheme (or the app's
 * preferred timed measure for a timed exercise), and rejects a plan that is not a complete week.
 * Returns null when the plan should be discarded.
 */
class WeeklyPlanSanitizer(
    private val catalog: ExerciseCatalog,
    private val volumeAwareReps: VolumeAwareReps = VolumeAwareReps(),
    private val varietyEnforcer: PlanVarietyEnforcer = PlanVarietyEnforcer(),
    private val periodization: PeriodizationConfig = PeriodizationConfig(),
    private val weightConfig: SuggestedWeightConfig = SuggestedWeightConfig(),
    private val applySetPrescription: ApplySetPrescriptionRecipeUseCase =
        ApplySetPrescriptionRecipeUseCase()
) {

    suspend fun sanitize(plan: WeeklyPlan, request: PlanRequest): WeeklyPlan? {
        val minSets = DeterministicWorkoutPlannerEngine.MIN_SETS
        val maxSets = DeterministicWorkoutPlannerEngine.MAX_SETS
        require(request.setsPerExercise in minSets..maxSets) {
            "setsPerExercise must be between $minSets and $maxSets"
        }
        require(request.accessorySetsPerExercise in minSets..maxSets) {
            "accessorySetsPerExercise must be between $minSets and $maxSets"
        }
        val usable = PlannerCandidateEligibility.candidates(catalog.all(), request)
            .associateBy { it.id }
        val isDeload = request.isDeload

        val days = plan.days.map { day ->
            day.copy(
                exercises = day.exercises.mapNotNull { planned ->
                    val exercise = usable[planned.exerciseId] ?: return@mapNotNull null
                    val sets = setsFor(exercise, request, isDeload)
                    val preferredMeasure =
                        ExerciseMeasureDefaults.preferredMeasure(exercise.id)
                    if (preferredMeasure is ExerciseMeasure.Timed) {
                        return@mapNotNull timedExercise(
                            planned = planned,
                            exercise = exercise,
                            sets = sets,
                            durationSeconds = preferredMeasure.durationSeconds,
                            request = request,
                            isDeload = isDeload
                        )
                    }
                    val reps = volumeAwareReps.repsFor(
                        request.goal,
                        exercise.movementPattern.isCompound,
                        sets
                    )
                    val capability = exercise.loadCapability
                    val intensityScale = if (isDeload) periodization.deloadIntensityScale else 1.0
                    // The model may propose a number, but the app decides its meaning: only external
                    // resistance keeps one. A bodyweight or added-load exercise is sanitized to a
                    // typed no-number prescription without discarding the selection.
                    val allowed = WorkoutLoadPolicy.allowsAutomaticLoad(capability)
                    val lowRepFloor = if (allowed &&
                        reps < BuildPlannerLoadInputsUseCase.MIN_MANUAL_PR_REPS_FOR_FLOOR
                    ) {
                        request.lowRepManualFloorsKg[exercise.id]
                    } else {
                        null
                    }
                    // A low-rep PR supplies evidence for a short prescription, so it lifts the
                    // evidence gate only there; it never becomes a rep-range prescription's floor.
                    val withheldForEvidence = exercise.id in request.withheldWeightExerciseIds &&
                        lowRepFloor == null
                    val proposed = planned.suggestedWeightKg
                        ?.takeIf {
                            request.includeWorkoutData &&
                                !withheldForEvidence &&
                                it > 0.0 &&
                                it <= MAX_SUGGESTED_WEIGHT_KG
                        }
                        ?.let { weight -> weight * intensityScale }
                    val progressedCap = request.recentWeightCaps[exercise.id]
                    val effectiveOneRepMax = when {
                        !allowed -> null
                        lowRepFloor != null &&
                            (progressedCap == null || lowRepFloor > progressedCap) -> lowRepFloor
                        else -> progressedCap
                    }
                    val rawBound = effectiveOneRepMax?.let {
                        it * weightConfig.intensityForReps(reps) * intensityScale
                    }
                    val bound = rawBound?.let(weightConfig::roundToIncrement)
                    val ceiling = EquipmentWeightLimit.ceilingFor(
                        exercise,
                        request.equipmentMaxWeights
                    )
                    val uncapped = if (proposed == null) {
                        null
                    } else if (bound == null) {
                        proposed
                    } else {
                        minOf(proposed, bound)
                    }
                    // Inventory parity needs the raw bound: re-rounding an already-rounded value
                    // could keep 27.5 kg on a grid whose reachable load is 25 kg.
                    val rawUncapped = if (proposed == null) {
                        null
                    } else if (rawBound == null) {
                        proposed
                    } else {
                        minOf(proposed, rawBound)
                    }
                    // A saved implement re-rounds the accepted bound down onto a real load; without
                    // one the model's own value keeps its existing treatment.
                    val externalSuggestedWeightKg = if (allowed) {
                        rawUncapped?.let { bound ->
                            when (
                                val resolution = resolveInventoryLoad(
                                    inventory = request.loadingInventory,
                                    equipmentId = null,
                                    requiredEquipment = exercise.requiredEquipment,
                                    boundKg = bound,
                                    ceilingKg = ceiling
                                )
                            ) {
                                InventoryLoadResolution.NoApplicableInventory ->
                                    uncapped?.let { EquipmentWeightLimit.clamp(it, ceiling) }

                                is InventoryLoadResolution.Reachable -> resolution.weightKg
                                InventoryLoadResolution.NoReachableLoad -> null
                            }
                        }
                    } else {
                        null
                    }
                    // A model number never becomes added load: EX-02-A prescribes the recorded number
                    // advanced by the app's own streak rule, or nothing at all.
                    val addedSuggestedWeightKg =
                        if (!allowed && capability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE) {
                            request.suggestedAddedLoadsKg[exercise.id]
                                ?.takeIf { it.isFinite() && it > 0.0 }
                                ?.let(weightConfig::roundToIncrement)
                        } else {
                            null
                        }
                    val suggestedWeightKg = externalSuggestedWeightKg ?: addedSuggestedWeightKg
                    val ceilingBinds = ceiling != null && uncapped != null && uncapped > ceiling
                    // A model number is only "accepted" when the app keeps a numeric load for this
                    // exercise; a discarded proposal must not also claim an applied bound or ceiling.
                    val retained = allowed && proposed != null
                    val boundedByProgression = retained && bound != null && bound < proposed
                    val cappedByEquipment = retained && ceilingBinds
                    val unavailableReason = when {
                        // Added load is the app's own recorded value, so it never depends on a model
                        // proposal or on workout-data sharing.
                        addedSuggestedWeightKg != null -> null
                        capability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE ->
                            LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
                        !allowed -> LoadUnavailableReason.NOT_LOAD_TRACKED
                        !request.includeWorkoutData -> LoadUnavailableReason.AI_DATA_NOT_SHARED
                        exercise.id in request.withheldWeightExerciseIds && lowRepFloor == null ->
                            LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
                        planned.suggestedWeightKg == null ->
                            LoadUnavailableReason.AI_PROPOSAL_OMITTED
                        proposed == null -> LoadUnavailableReason.AI_PROPOSAL_REJECTED
                        else -> null
                    }
                    val slotKind = when {
                        externalSuggestedWeightKg != null -> LoadKind.EXTERNAL
                        addedSuggestedWeightKg != null -> LoadKind.ADDED
                        else -> WorkoutLoadPolicy.defaultKind(capability)
                    }
                    // The app's own ordered-prescription stage runs after the model's numbers are
                    // settled, so every engine reaches the same shape and a model cannot bypass it.
                    // A manual low-rep PR can supply the short targets even when the slot-level
                    // bound is absent; each target's eligibility is decided by its own reps.
                    val orderedFloor = if (allowed) {
                        request.lowRepManualFloorsKg[exercise.id]
                    } else {
                        null
                    }
                    val orderedGenerationBound = effectiveOneRepMax ?: orderedFloor
                    val setTargets = request.automaticSetPrescription?.let { automatic ->
                        roundOrderedTargetsToInventory(
                            targets = applySetPrescription(
                                AutomaticSetPrescriptionRequest(
                                    automatic = automatic,
                                    sets = sets,
                                    reps = reps,
                                    loadKind = slotKind,
                                    estimatedOneRepMaxKg = orderedGenerationBound,
                                    intensityScale = intensityScale
                                )
                            ),
                            inventory = request.loadingInventory,
                            equipmentId = null,
                            requiredEquipment = exercise.requiredEquipment,
                            estimatedOneRepMaxKg = progressedCap ?: 0.0,
                            intensityScale = intensityScale,
                            ceilingKg = ceiling,
                            weightConfig = weightConfig,
                            lowRepOneRepMaxKg = orderedFloor,
                            generatedFromOneRepMaxKg = orderedGenerationBound
                        )
                    }
                    PlannedExercise(
                        exerciseId = exercise.id,
                        sets = sets,
                        reps = reps,
                        suggestedWeightKg = suggestedWeightKg,
                        loadKind = slotKind,
                        loadCapability = capability,
                        setTargets = setTargets,
                        explanation = ExerciseTargetExplanation(
                            // The model chose the number; the app only scaled and bounded it, so the
                            // app's adjustments are recorded as separate operations rather than as a
                            // rep-intensity conversion the sanitizer never ran.
                            aiAdjustment = AiLoadExplanation(
                                proposedKg = planned.suggestedWeightKg,
                                proposedAccepted = retained,
                                deloadScaled = isDeload && retained,
                                deloadIntensityScale = intensityScale,
                                progressionBoundKg = if (retained) bound else null,
                                boundedByProgression = boundedByProgression,
                                equipmentCeilingKg = if (retained) ceiling else null,
                                cappedByEquipment = cappedByEquipment
                            ),
                            setCount = SetCountExplanation(
                                baseSets = if (exercise.movementPattern.isCompound) {
                                    request.setsPerExercise
                                } else {
                                    request.accessorySetsPerExercise
                                },
                                deloadApplied = isDeload,
                                deloadVolumeScale = periodization.deloadVolumeScale,
                                fatigueEffect = FatigueSetEffect.NONE,
                                finalSets = sets
                            ),
                            unavailableReason = unavailableReason
                        )
                    )
                }
            )
        }

        if (days.size < request.daysPerWeek) return null
        if (days.any { it.exercises.size < PlannerExerciseCounts.FLOOR_PER_DAY }) return null
        val trimmed = plan.copy(
            days = days.take(request.daysPerWeek)
                .mapIndexed { index, day -> day.copy(dayIndex = index) },
            weekNumber = request.weekNumber,
            cycleNumber = request.cycleNumber
        )
        val enforced = varietyEnforcer.enforce(trimmed, request) { id ->
            usable[id]?.movementPattern?.isCompound ?: true
        } ?: return null
        val focuses = SplitResolver.focusSequence(request.splitPreference, request.daysPerWeek)
        return enforced.copy(
            armCoverage = DirectArmCoverage.assess(
                days = enforced.days,
                exercisesById = usable,
                compatibleCandidatesByMuscle = DirectArmCoverage.compatibleCandidates(
                    usable.values.toList(),
                    focuses
                ),
                fatigue = request.muscleFatigue,
                isDeload = isDeload,
                attribution = PlanAttribution.AI_GENERATED
            ),
            explanation = PlanExplanation(
                engine = enforced.engine,
                attribution = PlanAttribution.AI_GENERATED,
                isDeload = isDeload,
                deloadVolumeScale = periodization.deloadVolumeScale,
                deloadIntensityScale = periodization.deloadIntensityScale
            )
        )
    }

    /**
     * Applies the app's preferred measure to a model slot. The model's reps and load are discarded:
     * the duration is the app's product default, never the model's number, and ordered rep targets
     * are not fabricated for a timed slot. Sets keep the same rule as counted work.
     */
    private fun timedExercise(
        planned: PlannedExercise,
        exercise: Exercise,
        sets: Int,
        durationSeconds: Int,
        request: PlanRequest,
        isDeload: Boolean
    ): PlannedExercise {
        val baseSets = if (exercise.movementPattern.isCompound) {
            request.setsPerExercise
        } else {
            request.accessorySetsPerExercise
        }
        val intensityScale = if (isDeload) periodization.deloadIntensityScale else 1.0
        return PlannedExercise(
            exerciseId = exercise.id,
            sets = sets,
            durationSeconds = durationSeconds,
            loadKind = WorkoutLoadPolicy.defaultKind(exercise.loadCapability),
            loadCapability = exercise.loadCapability,
            explanation = ExerciseTargetExplanation(
                aiAdjustment = AiLoadExplanation(
                    proposedKg = planned.suggestedWeightKg,
                    proposedAccepted = false,
                    deloadScaled = false,
                    deloadIntensityScale = intensityScale,
                    progressionBoundKg = null,
                    boundedByProgression = false,
                    equipmentCeilingKg = null,
                    cappedByEquipment = false
                ),
                setCount = SetCountExplanation(
                    baseSets = baseSets,
                    deloadApplied = isDeload,
                    deloadVolumeScale = periodization.deloadVolumeScale,
                    fatigueEffect = FatigueSetEffect.NONE,
                    finalSets = sets
                )
            )
        )
    }

    private fun setsFor(exercise: Exercise, request: PlanRequest, isDeload: Boolean): Int {
        val baseSets = if (exercise.movementPattern.isCompound) {
            request.setsPerExercise
        } else {
            request.accessorySetsPerExercise
        }
        return if (isDeload) {
            (baseSets * periodization.deloadVolumeScale).roundToInt().coerceAtLeast(1)
        } else {
            baseSets
        }
    }

    companion object {
        const val MAX_SUGGESTED_WEIGHT_KG = 1_000.0
    }
}
