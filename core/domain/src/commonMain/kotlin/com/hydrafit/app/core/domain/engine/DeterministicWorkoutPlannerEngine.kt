package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import kotlin.math.roundToInt

class DeterministicWorkoutPlannerEngine(
    private val catalog: ExerciseCatalog,
    private val volumeAwareReps: VolumeAwareReps = VolumeAwareReps(),
    private val weightConfig: SuggestedWeightConfig = SuggestedWeightConfig(),
    private val periodization: PeriodizationConfig = PeriodizationConfig(),
    private val applySetPrescription: ApplySetPrescriptionRecipeUseCase =
        ApplySetPrescriptionRecipeUseCase()
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

    private val fatigueConfig = FatigueConfig()

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        val exercises = catalog.all()
        PlannerCandidateEligibility.requireWorkouts(exercises, request)
        return plan(request, exercises)
    }

    internal fun plan(request: PlanRequest, exercises: List<Exercise>): WeeklyPlan {
        require(request.daysPerWeek in MIN_DAYS..MAX_DAYS) {
            "daysPerWeek must be between $MIN_DAYS and $MAX_DAYS"
        }
        require(request.setsPerExercise in MIN_SETS..MAX_SETS) {
            "setsPerExercise must be between $MIN_SETS and $MAX_SETS"
        }
        require(request.accessorySetsPerExercise in MIN_SETS..MAX_SETS) {
            "accessorySetsPerExercise must be between $MIN_SETS and $MAX_SETS"
        }

        val focuses = SplitResolver.focusSequence(request.splitPreference, request.daysPerWeek)
        val availableExercises = PlannerCandidateEligibility.candidates(exercises, request)
        val isDeload = request.isDeload
        // Exercises already chosen earlier in the week; a fresh compound is preferred, but one may
        // repeat when no alternative exists. Accessories merely prefer a fresh option when one does.
        val weekUsed = mutableSetOf<String>()
        // Running involvement-weighted sets per muscle, accumulated across the week so each day can
        // chase the largest remaining volume deficit.
        val weeklyVolume = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
        val directArmSets = MuscleGroup.entries.associateWith { 0 }.toMutableMap()
        val skippedForSoreness = mutableSetOf<String>()
        val target = WeeklyVolumeTargets.forGoal(
            request.goal,
            if (isDeload) periodization.deloadVolumeScale else 1.0
        )
        val armTargetSets = DirectArmCoverage.targetSetsPerMuscle(request.daysPerWeek, request.goal)
        // Pace each muscle's weekly target across the days whose focus can mainly train it, so the
        // first such day fills only its share and later days are not left with compounds alone.
        val trainableByFocus = focuses.distinct().associateWith { focus ->
            val patterns = SplitResolver.compoundGroups(focus).flatten() + isolationPatterns(focus)
            availableExercises
                .filter { it.movementPattern in patterns }
                .flatMap { exercise ->
                    exercise.effectiveInvolvements.filterValues { it >= MAIN_INVOLVEMENT }.keys
                }
                .toSet()
        }
        fun paceForDay(dayIndex: Int): Map<MuscleGroup, Double> =
            MuscleGroup.entries.associateWith { muscle ->
                val total = focuses.count { muscle in trainableByFocus.getValue(it) }
                if (total == 0) {
                    1.0
                } else {
                    focuses.take(dayIndex + 1)
                        .count { muscle in trainableByFocus.getValue(it) }
                        .toDouble() / total
                }
            }
        // A deload does not chase the arm target, so its arm work keeps the plain accessory count.
        val armSetsPerExercise = if (isDeload) {
            emptyMap()
        } else {
            listOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS).associateWith { muscle ->
                DirectArmCoverage.setsPerArmExercise(
                    muscle,
                    focuses,
                    armTargetSets,
                    request.accessorySetsPerExercise
                )
            }
        }

        val days = List(request.daysPerWeek) { index ->
            val focus = focuses[index]
            WorkoutDay(
                dayIndex = index,
                focus = focus,
                exercises = selectExercises(
                    focus,
                    availableExercises,
                    weekUsed,
                    weeklyVolume,
                    directArmSets,
                    target,
                    armTargetSets,
                    request.fatigueForWorkout(index),
                    request.setsPerExercise,
                    request.accessorySetsPerExercise,
                    request.goal,
                    request.recentExerciseIdsByPattern,
                    request.suggestedWeightsKg,
                    request.recentWeightCaps,
                    request.suggestedAddedLoadsKg,
                    request.withheldWeightExerciseIds,
                    request.lowRepManualFloorsKg,
                    request.equipmentMaxWeights,
                    request.loadingInventory,
                    request.exercisePreferences,
                    request.loadExplanations,
                    skippedForSoreness,
                    isDeload,
                    request.automaticSetPrescription,
                    armSetsPerExercise,
                    paceForDay(index),
                    request.repProgressions,
                    request.sessionLength
                )
            )
        }

        val compatibleArmCandidates = DirectArmCoverage.compatibleCandidates(
            availableExercises,
            focuses
        )
        return WeeklyPlan(
            engine = id,
            days = days,
            weekNumber = request.weekNumber,
            cycleNumber = request.cycleNumber,
            armCoverage = DirectArmCoverage.assess(
                days = days,
                exercisesById = availableExercises.associateBy { it.id },
                compatibleCandidatesByMuscle = compatibleArmCandidates,
                fatigue = request.muscleFatigue,
                isDeload = isDeload,
                targetSets = armTargetSets,
                skippedCandidateIds = skippedForSoreness
            ),
            explanation = PlanExplanation(
                engine = id,
                attribution = PlanAttribution.DETERMINISTIC,
                isDeload = isDeload,
                deloadVolumeScale = periodization.deloadVolumeScale,
                deloadIntensityScale = periodization.deloadIntensityScale,
                fatigueSkippedExerciseIds = skippedForSoreness.toList()
            )
        )
    }

    private fun selectExercises(
        focus: SplitFocus,
        exercises: List<Exercise>,
        weekUsed: MutableSet<String>,
        weeklyVolume: MutableMap<MuscleGroup, Double>,
        directArmSets: MutableMap<MuscleGroup, Int>,
        target: VolumeTarget,
        armTargetSets: Int,
        fatigue: Map<MuscleGroup, Double>,
        setsPerExercise: Int,
        accessorySetsPerExercise: Int,
        goal: TrainingGoal,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>>,
        suggestedWeightsKg: Map<String, Double>,
        recentWeightCaps: Map<String, Double>,
        suggestedAddedLoadsKg: Map<String, Double>,
        withheldWeightExerciseIds: Set<String>,
        lowRepManualFloorsKg: Map<String, Double>,
        equipmentMaxWeights: Map<EquipmentTag, Double>,
        loadingInventory: LoadingInventory?,
        exercisePreferences: Map<String, ExercisePreference>,
        loadExplanations: Map<String, LoadOriginExplanation>,
        skippedForSoreness: MutableSet<String>,
        isDeload: Boolean,
        automaticSetPrescription: AutomaticSetPrescription? = null,
        armSetsPerExercise: Map<MuscleGroup, Int> = emptyMap(),
        pace: Map<MuscleGroup, Double> = emptyMap(),
        repProgressions: Map<String, RepProgressionHistory> = emptyMap(),
        sessionLength: SessionLength = SessionLength.DEFAULT
    ): List<PlannedExercise> {
        val used = mutableSetOf<String>()
        val picks = mutableListOf<PlannedExercise>()
        val targetMax = PlannerExerciseCounts.targetMax(sessionLength)
        val mustHaveMax = PlannerExerciseCounts.mustHaveMax(sessionLength)

        fun pick(
            candidates: List<Exercise>,
            slotRanking: Boolean = false,
            floorRanking: Boolean = false
        ): Boolean {
            // Ordering is by weighted fatigue, but the skip uses raw targeted fatigue, so a lower-
            // ranked candidate can be fresh while the top one is sore. Try candidates in order and
            // take the first that is not sore instead of rejecting the whole group.
            val ranked = rankCandidates(
                candidates = candidates,
                fatigue = fatigue,
                weekUsed = weekUsed,
                recentExerciseIdsByPattern = recentExerciseIdsByPattern,
                weeklyVolume = weeklyVolume,
                target = target,
                exercisePreferences = exercisePreferences,
                slotRanking = slotRanking,
                pace = pace,
                floorRanking = floorRanking
            )
            val candidate = pickFirstNonSore(ranked, fatigue)
            ranked.takeWhile { it.id != candidate?.id }.forEach { skippedForSoreness += it.id }
            if (candidate == null) return false
            val soreness = targetedFatigue(candidate, fatigue)
            val planned = plannedExercise(
                candidate,
                soreness,
                setsPerExercise,
                accessorySetsPerExercise,
                goal,
                suggestedWeightsKg,
                recentWeightCaps,
                suggestedAddedLoadsKg,
                withheldWeightExerciseIds,
                lowRepManualFloorsKg,
                equipmentMaxWeights,
                loadingInventory,
                loadExplanations,
                isDeload,
                automaticSetPrescription,
                armSetsPerExercise,
                repProgressions
            )
            used += candidate.id
            weekUsed += candidate.id
            picks += planned
            candidate.effectiveInvolvements.forEach { (muscle, weight) ->
                weeklyVolume[muscle] = weeklyVolume.getValue(muscle) + planned.sets * weight
            }
            DirectArmCoverage.muscleFor(candidate)?.let { muscle ->
                directArmSets[muscle] = directArmSets.getValue(muscle) + planned.sets
            }
            return true
        }

        // Variety within the week: an isolation exercise not yet used this week is preferred, and one
        // repeats only when no other eligible candidate remains. An exercise the user prefers is never
        // treated as stale, so their preference still wins.
        fun pickFreshFirst(candidates: List<Exercise>, floorRanking: Boolean = false): Boolean {
            val fresh = candidates.filter {
                it.id !in weekUsed ||
                    exercisePreferences[it.id] == ExercisePreference.PREFER
            }
            return pick(fresh, floorRanking = floorRanking) ||
                pick(candidates, floorRanking = floorRanking)
        }

        // One compound for each major pattern in the focus, chosen by the largest remaining deficit.
        // Prefer a compound not yet used this week; if none remains, repeat an eligible one so the
        // day is not left incomplete (completeness outranks within-week novelty).
        SplitResolver.compoundGroups(focus).forEach { group ->
            if (picks.size >= targetMax) return@forEach
            val eligible = exercises.filter {
                it.movementPattern in group &&
                    it.id !in used &&
                    !isAtMax(it, weeklyVolume, target)
            }
            val fresh = eligible.filter { it.id !in weekUsed }
            // Each candidate is ranked by its own pattern's muscles, so a press is chosen for its
            // chest work and not for a single high triceps weight.
            pick(fresh, slotRanking = true) || pick(eligible, slotRanking = true)
        }

        // Pursue unmet direct arm coverage first, then add accessories only while a muscle they
        // mainly train is still under its weekly target, up to TARGET_MAX. There is no forced
        // minimum: the weekly volume target is the budget, so a higher set count fills it with fewer
        // exercises instead of padding every day to the same length.
        val isolationPool = isolationPatterns(focus)
        var trapSlotTried = false
        var legSlotsTried = false
        var mustHaves = 0

        // The arm target is paced like the volume targets: this day only owes its share, so the
        // first arm day does not take the whole week's direct arm work.
        fun needsArmWork(exercise: Exercise): Boolean =
            DirectArmCoverage.muscleFor(exercise)?.let { muscle ->
                val share = armTargetSets * (pace[muscle] ?: 1.0)
                (directArmSets[muscle] ?: 0) < kotlin.math.ceil(share - ARM_PACE_TOLERANCE)
            } == true

        // How far a prime mover is below its weekly floor, paced like the targets so a day owes only
        // its share. A floor exercise must train a floor muscle mainly (involvement >= MAIN_INVOLVEMENT).
        fun floorDeficit(exercise: Exercise): Double = exercise.plannerInvolvements()
            .maxOfOrNull { (muscle, weight) ->
                if (muscle in WeeklyVolumeTargets.FLOOR_MUSCLES && weight >= MAIN_INVOLVEMENT) {
                    floorShortfall(muscle, weeklyVolume, target, pace) * weight
                } else {
                    0.0
                }
            } ?: 0.0
        // Floors are filled from the focus's isolation work only: a day keeps one compound per
        // pattern (see neverRepeatsAPatternWithinADay), so a muscle with no isolation path, such as
        // the upper back, can stay under its floor and is reported rather than forced. Arm isolation
        // never fills a floor (a dip's chest credit must not stand in for chest work).
        val floorPatterns = isolationPool
        // Three caps: the must-haves (direct arm coverage, floor fills, the shrug, leg extension and
        // leg curl) may fill a day up to the must-have maximum, while ordinary deficit-filling
        // accessories stop at the target maximum. Must-haves always come first, so they never
        // compete with extras.
        while (picks.size < mustHaveMax) {
            val candidates = exercises.filter {
                it.movementPattern in isolationPool &&
                    it.id !in used
            }
            val directArmCandidates = if (isDeload) {
                emptyList()
            } else {
                candidates.filter { needsArmWork(it) }
            }
            // Fairness: feed the arm with fewer direct sets first, so one arm cannot consume every
            // accessory slot while the other stays under target.
            val priorityPools = directArmCandidates
                .mapNotNull { exercise ->
                    DirectArmCoverage.muscleFor(exercise)?.let { muscle -> muscle to exercise }
                }
                .groupBy({ it.first }, { it.second })
                .entries.sortedBy { (muscle, _) -> directArmSets[muscle] ?: 0 }
            if (priorityPools.any { (_, pool) -> pickFreshFirst(pool) }) {
                // Dedicated arm coverage takes precedence over the weighted ceiling: compound
                // credits must not make qualifying direct work ineligible.
                mustHaves++
                continue
            }
            // A prime mover still below its paced weekly floor is filled next, after the direct arm
            // work, so the floors are met before the shrug or any ordinary accessory.
            val floorPool = exercises.filter {
                it.movementPattern in floorPatterns &&
                    it.id !in used &&
                    DirectArmCoverage.muscleFor(it) == null &&
                    !isAtMax(it, weeklyVolume, target) &&
                    floorDeficit(it) > 0.0
            }
            if (floorPool.isNotEmpty() && pickFreshFirst(floorPool, floorRanking = true)) {
                mustHaves++
                continue
            }
            // Traps have no weekly volume target, so no deficit ever selects a shrug. It is optional
            // work, so only one is planned a week, on the first pull or upper day that still has a
            // slot once the arms and floors are met. It never displaces a compound, arm coverage or a
            // floor, and still comes before the ordinary accessories. The must-have maximum applies
            // through the loop condition.
            val shrugPlannedThisWeek = exercises.any {
                it.id in weekUsed && it.movementPattern == MovementPattern.TRAP_ISOLATION
            }
            if (!trapSlotTried &&
                !shrugPlannedThisWeek &&
                (focus == SplitFocus.PULL || focus == SplitFocus.UPPER)
            ) {
                trapSlotTried = true
                val trapCandidates = exercises.filter {
                    it.movementPattern == MovementPattern.TRAP_ISOLATION && it.id !in used
                }
                if (pickFreshFirst(trapCandidates)) {
                    mustHaves++
                    continue
                }
            }
            // A squat and a hinge do not replace knee-extension and knee-flexion work: hinges train
            // the hamstrings at the hip, not the knee, and squats under-train the rectus femoris.
            // Every lower or legs day therefore gets one leg extension and one leg curl, when the
            // equipment allows, before the deficit-driven accessories and within the must-have cap.
            if (!legSlotsTried && (focus == SplitFocus.LOWER || focus == SplitFocus.LEGS)) {
                legSlotsTried = true
                var pickedLegWork = false
                KNEE_ISOLATION_MUSCLES.forEach { muscle ->
                    if (picks.size >= mustHaveMax) return@forEach
                    val pool = candidates.filter {
                        it.movementPattern == MovementPattern.LEG_ISOLATION &&
                            it.id !in used &&
                            (it.effectiveInvolvements[muscle] ?: 0.0) >= MAIN_INVOLVEMENT
                    }
                    if (pickFreshFirst(pool)) {
                        pickedLegWork = true
                        mustHaves++
                    }
                }
                if (pickedLegWork) continue
            }
            // Compounds and ordinary accessories share the normal daily maximum; must-haves sit
            // outside it, up to the must-have cap the loop condition enforces.
            if (picks.size - mustHaves >= targetMax) break
            val remaining = candidates.filter { candidate ->
                // A deload does not chase the direct-arm target, so an eligible arm isolation is an
                // ordinary accessory there; only a normal week reserves it for the priority branch.
                (
                    isDeload ||
                        !DirectArmCoverage.needsCoverage(candidate, directArmSets, armTargetSets)
                    ) &&
                    hasDeficit(candidate, weeklyVolume, target, pace)
            }
            // Direct arm work beyond the weekly target is a last resort: other isolation work fills
            // the slot first, so one arm cannot soak up every accessory slot on a pull or push day.
            val (armOverflow, ordinary) = remaining.partition { candidate ->
                DirectArmCoverage.muscleFor(candidate)?.let { muscle ->
                    (directArmSets[muscle] ?: 0) >= armTargetSets
                } == true
            }
            if (!(pickFreshFirst(ordinary) || pickFreshFirst(armOverflow))) break
        }

        return picks
    }

    /**
     * Orders candidate exercises for selection: explicit user preference first (a soft tier above
     * the unvalidated heuristic, never bypassing a hard gate), then the largest remaining weekly
     * deficit, then recovery, then freshness within the generated week, previous-plan compound
     * rotation, equipment preference, and finally id for a stable order. Extracted from
     * [selectExercises] so the same ranking can drive substitution of an accepted plan's slot
     * ([SubstituteExerciseUseCase]).
     */
    internal fun rankCandidates(
        candidates: List<Exercise>,
        fatigue: Map<MuscleGroup, Double>,
        weekUsed: Set<String>,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>>,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget,
        exercisePreferences: Map<String, ExercisePreference> = emptyMap(),
        slotRanking: Boolean = false,
        pace: Map<MuscleGroup, Double> = emptyMap(),
        floorRanking: Boolean = false
    ): List<Exercise> {
        val comparator = compareBy<Exercise>(
            // Explicit user preference ranks first among candidates that already passed the hard
            // gates. Prefer-less is soft: it only orders below neutral, it never removes a candidate.
            { (exercisePreferences[it.id] ?: ExercisePreference.NEUTRAL).ordinal },
            // Coverage next: address the largest remaining shortfall, then use the (unvalidated)
            // fatigue estimate only to break ties, so a negligible fatigue difference cannot
            // override a much larger coverage deficit.
            { -deficitScore(it, weeklyVolume, target, slotRanking, pace, floorRanking) },
            // When two lifts fill their muscles' deficit equally, the one that reaches more of them
            // wins, so a press for both chest regions beats one for the lower chest alone.
            { -slotBreadth(it, weeklyVolume, target, slotRanking) },
            { weightedFatigue(it, fatigue) },
            { it.id in weekUsed },
            { it.id in recentExerciseIdsByPattern[it.movementPattern].orEmpty() },
            { equipmentRank(it) },
            { it.id }
        )
        return candidates.sortedWith(comparator)
    }

    /**
     * The first ranked candidate that is not sore. Ordering is by weighted fatigue while the skip
     * uses raw targeted fatigue, so a lower-ranked candidate can be fresh while the top one is sore.
     */
    internal fun pickFirstNonSore(
        ranked: List<Exercise>,
        fatigue: Map<MuscleGroup, Double>
    ): Exercise? = ranked.firstOrNull {
        targetedFatigue(it, fatigue) < fatigueConfig.skipThreshold
    }

    private fun plannedExercise(
        candidate: Exercise,
        soreness: Double,
        setsPerExercise: Int,
        accessorySetsPerExercise: Int,
        goal: TrainingGoal,
        suggestedWeightsKg: Map<String, Double>,
        recentWeightCaps: Map<String, Double>,
        suggestedAddedLoadsKg: Map<String, Double>,
        withheldWeightExerciseIds: Set<String>,
        lowRepManualFloorsKg: Map<String, Double>,
        equipmentMaxWeights: Map<EquipmentTag, Double>,
        loadingInventory: LoadingInventory?,
        loadExplanations: Map<String, LoadOriginExplanation>,
        isDeload: Boolean,
        automaticSetPrescription: AutomaticSetPrescription? = null,
        armSetsPerExercise: Map<MuscleGroup, Int> = emptyMap(),
        repProgressions: Map<String, RepProgressionHistory> = emptyMap()
    ): PlannedExercise {
        val isCompound = candidate.movementPattern.isCompound
        // Direct arm work uses the split's arm set count (the accessory count, raised only when the
        // split's arm slots could not otherwise reach the weekly arm target).
        val baseSets = when {
            isCompound -> setsPerExercise
            // The shrug is optional, once-a-week work, so it never takes a full accessory count.
            candidate.movementPattern == MovementPattern.TRAP_ISOLATION ->
                minOf(accessorySetsPerExercise, TRAP_SETS)
            else -> DirectArmCoverage.muscleFor(candidate)?.let { armSetsPerExercise[it] }
                ?: accessorySetsPerExercise
        }
        val deloadedSets = if (isDeload) {
            (baseSets * periodization.deloadVolumeScale).roundToInt().coerceAtLeast(1)
        } else {
            baseSets
        }
        val sets = (deloadedSets - if (soreness >= fatigueConfig.reduceThreshold) 1 else 0)
            .coerceAtLeast(1)
        val setCount = SetCountExplanation(
            baseSets = baseSets,
            deloadApplied = isDeload,
            deloadVolumeScale = periodization.deloadVolumeScale,
            fatigueEffect = if (sets < deloadedSets) {
                FatigueSetEffect.REDUCED
            } else {
                FatigueSetEffect.NONE
            },
            finalSets = sets
        )
        val preferredMeasure = ExerciseMeasureDefaults.preferredMeasure(candidate.id)
        if (preferredMeasure is ExerciseMeasure.Timed) {
            return timedExercise(candidate, sets, setCount, preferredMeasure.durationSeconds)
        }
        // Double progression inside the goal's rep range: the week's reps, and the load to hold when
        // the last comparable target supplies one.
        val range = goal.repRange(isCompound)
        val repTarget = RepProgression.next(
            last = repProgressions[candidate.id],
            range = range,
            loadStepKg = weightConfig.roundToKg,
            holdForDeload = isDeload
        )
        val reps = repTarget.reps
        val capability = candidate.loadCapability
        val scale = intensityScale(isDeload)
        // A numeric working weight is only meaningful for external resistance. A bodyweight or
        // added-load exercise is prescribed without a generated number, even if a stale baseline
        // exists for its id.
        val allowed = WorkoutLoadPolicy.allowsAutomaticLoad(capability)
        val withheld = candidate.id in withheldWeightExerciseIds
        val progressedBaseline = if (allowed && !withheld) {
            suggestedWeightsKg[candidate.id]?.let { suggestedOneRepMax ->
                recentWeightCaps[candidate.id]?.let { minOf(suggestedOneRepMax, it) }
                    ?: suggestedOneRepMax
            }
        } else {
            null
        }
        val lowRepFloor = if (allowed &&
            reps < BuildPlannerLoadInputsUseCase
                .MIN_MANUAL_PR_REPS_FOR_FLOOR
        ) {
            lowRepManualFloorsKg[candidate.id]
        } else {
            null
        }
        val oneRepMax = if (allowed) {
            oneRepMaxBoundForReps(reps, progressedBaseline, lowRepFloor)
        } else {
            null
        }
        val ceiling = EquipmentWeightLimit.ceilingFor(candidate, equipmentMaxWeights)
        // A held load from double progression replaces the conversion: the same weight gains a rep,
        // or steps up at the top of the range. It is still deload-scaled, capped by the equipment
        // ceiling and resolved onto a reachable load below.
        val heldLoad = repTarget.heldLoadKg?.takeIf { allowed && !withheld && it > 0.0 }
        // The conversion is recorded at the generic increment, while reachability is resolved from
        // the unrounded working bound so an inventory load can never land above it.
        val unroundedWorking = heldLoad?.let { it * scale } ?: oneRepMax?.let {
            it * weightConfig.intensityForReps(reps) * scale
        }
        val working = unroundedWorking?.let(weightConfig::roundToIncrement)
        val externalSuggestedWeightKg = unroundedWorking?.let { bound ->
            when (
                val resolution = resolveInventoryLoad(
                    inventory = loadingInventory,
                    equipmentId = null,
                    requiredEquipment = candidate.requiredEquipment,
                    boundKg = bound,
                    ceilingKg = ceiling
                )
            ) {
                InventoryLoadResolution.NoApplicableInventory ->
                    working?.let { EquipmentWeightLimit.clamp(it, ceiling) }

                is InventoryLoadResolution.Reachable -> resolution.weightKg
                InventoryLoadResolution.NoReachableLoad -> null
            }
        }
        // Added load is prescribed as the recorded number itself. It is never scaled for a deload
        // or converted from an estimate, because EX-02-A has no approved formula for either.
        val addedSuggestedWeightKg =
            if (!allowed && capability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE) {
                suggestedAddedLoadsKg[candidate.id]
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let(weightConfig::roundToIncrement)
            } else {
                null
            }
        val suggestedWeightKg = externalSuggestedWeightKg ?: addedSuggestedWeightKg
        val slotKind = when {
            externalSuggestedWeightKg != null -> LoadKind.EXTERNAL
            addedSuggestedWeightKg != null -> LoadKind.ADDED
            else -> WorkoutLoadPolicy.defaultKind(capability)
        }
        // The app's own ordered-prescription stage, shared with the sanitizer so both engines reach
        // the same shape from the same conversion. A slot-level bound may be absent while a manual
        // low-rep PR still legitimately supplies the short targets of a mixed prescription.
        val orderedFloor = if (allowed) lowRepManualFloorsKg[candidate.id] else null
        val orderedGenerationBound = oneRepMax ?: orderedFloor
        val setTargets = automaticSetPrescription?.let { automatic ->
            roundOrderedTargetsToInventory(
                targets = applySetPrescription(
                    AutomaticSetPrescriptionRequest(
                        automatic = automatic,
                        sets = sets,
                        reps = reps,
                        loadKind = slotKind,
                        estimatedOneRepMaxKg = orderedGenerationBound,
                        intensityScale = scale
                    )
                ),
                inventory = loadingInventory,
                equipmentId = null,
                requiredEquipment = candidate.requiredEquipment,
                estimatedOneRepMaxKg = progressedBaseline ?: 0.0,
                intensityScale = scale,
                ceilingKg = ceiling,
                weightConfig = weightConfig,
                // The raw floor is passed and gated per ordered target's own reps. The slot-level
                // floor above describes only the scalar rep count and must not hide a short target.
                lowRepOneRepMaxKg = orderedFloor,
                generatedFromOneRepMaxKg = orderedGenerationBound
            )
        }
        return PlannedExercise(
            exerciseId = candidate.id,
            sets = sets,
            reps = reps,
            suggestedWeightKg = suggestedWeightKg,
            loadKind = slotKind,
            loadCapability = capability,
            setTargets = setTargets,
            // The goal's range travels with the slot, so accepted plans, blocks and routines keep
            // the same double-progression range and step.
            progressionPolicy = SlotProgressionPolicy(
                minReps = range.first,
                maxReps = range.last,
                incrementKg = weightConfig.roundToKg
            ),
            explanation = ExerciseTargetExplanation(
                baseline = loadExplanations[candidate.id]?.baseline,
                progression = loadExplanations[candidate.id]?.progression,
                // A held load was not converted from the estimate, so no conversion is recorded.
                conversion = working?.takeIf { heldLoad == null }?.let {
                    LoadConversionExplanation(
                        reps = reps,
                        intensityFraction = weightConfig.intensityForReps(reps),
                        deloadIntensityScale = scale,
                        roundToKg = weightConfig.roundToKg,
                        uncappedWorkingKg = it,
                        equipmentCeilingKg = ceiling,
                        equipmentCeilingBinds = ceiling != null && it > ceiling
                    )
                },
                setCount = setCount,
                unavailableReason = when {
                    !allowed -> LoadUnavailableReason.NOT_LOAD_TRACKED
                    oneRepMax == null && heldLoad == null ->
                        LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
                    else -> null
                },
                repProgression = RepProgressionExplanation(
                    step = repTarget.step,
                    minReps = range.first,
                    maxReps = range.last,
                    previousReps = repProgressions[candidate.id]?.reps,
                    previousWeightKg = repProgressions[candidate.id]?.weightKg
                )
            )
        )
    }

    /**
     * A preferred-timed slot carries its seconds in place of reps and never a load target. Its set
     * count keeps the same deload/fatigue treatment as counted work; ordered rep targets are not
     * fabricated for a timed slot.
     */
    private fun timedExercise(
        candidate: Exercise,
        sets: Int,
        setCount: SetCountExplanation,
        durationSeconds: Int
    ): PlannedExercise = PlannedExercise(
        exerciseId = candidate.id,
        sets = sets,
        durationSeconds = durationSeconds,
        loadKind = WorkoutLoadPolicy.defaultKind(candidate.loadCapability),
        loadCapability = candidate.loadCapability,
        // A timed slot has no load suggestion, so no unavailable-load reason is recorded; the
        // duration is the whole prescription.
        explanation = ExerciseTargetExplanation(setCount = setCount)
    )

    /**
     * Involvements that drive weekly-volume deficit targeting. TRAPS is excluded: the trapezius
     * shares most work with UPPER_BACK, so giving it its own deficit window would double-count the
     * same sets. It still contributes to fatigue and soreness (see [weightedFatigue]); only the
     * weekly volume ledger ignores it, until a dedicated policy is agreed (VOL-01).
     */
    private fun Exercise.plannerInvolvements(): Map<MuscleGroup, Double> =
        effectiveInvolvements.filterKeys { it !in PLANNER_DEFERRED_MUSCLES }

    /** The largest weighted shortfall among the muscles this exercise trains below their target. */
    private fun deficitScore(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget,
        slotRanking: Boolean = false,
        pace: Map<MuscleGroup, Double> = emptyMap(),
        floorRanking: Boolean = false
    ): Double {
        val involvements = exercise.plannerInvolvements()
        if (floorRanking) {
            // A floor fill is chosen only for the floor shortfall it closes, so a high credit to a
            // muscle with a larger soft-target deficit (a dip's lower chest) cannot outrank it.
            return involvements.maxOfOrNull { (muscle, weight) ->
                if (muscle in WeeklyVolumeTargets.FLOOR_MUSCLES) {
                    floorShortfall(muscle, weeklyVolume, target, pace) * weight
                } else {
                    0.0
                }
            } ?: 0.0
        }
        val primaries = PATTERN_PRIMARY_MUSCLES[exercise.movementPattern]
        if (slotRanking && !primaries.isNullOrEmpty()) {
            // A compound slot is ranked by the mean deficit of the pattern's muscles that the
            // exercise actually trains, so a press is chosen for its chest work and not for one high
            // triceps weight, and a lift is neither rewarded nor penalized for listing more or fewer
            // muscles. A weight at or above the targeted-involvement cutoff counts in full: the
            // catalog records the same lift's chest work as 0.7 on one press and 1.0 on another, and
            // that difference is modelling noise, not a reason to prefer one press.
            val trained = primaries.filter { (involvements[it] ?: 0.0) > 0.0 }
            if (trained.isEmpty()) return 0.0
            return trained.sumOf { muscle ->
                val weight = involvements.getValue(muscle)
                val effective = if (weight >=
                    fatigueConfig.targetedInvolvementCutoff
                ) {
                    1.0
                } else {
                    weight
                }
                relativeShortfall(muscle, weeklyVolume, target) * effective
            } / trained.size
        }
        return involvements
            .maxOfOrNull { (muscle, weight) ->
                shortfall(muscle, weeklyVolume, target, pace) * weight
            }
            ?.takeIf { it > 0.0 }
            ?: 0.0
    }

    /**
     * How far [muscle] is below its own target; zero when met or when it has none. [pace] is the
     * share of the muscle's training days reached so far in the week (missing means the whole
     * week), so an accessory fills only that day's part of the weekly target instead of the first
     * day that trains the muscle taking all of it.
     */
    private fun shortfall(
        muscle: MuscleGroup,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget,
        pace: Map<MuscleGroup, Double> = emptyMap()
    ): Double {
        val muscleTarget = WeeklyVolumeTargets.forMuscle(target, muscle) ?: return 0.0
        val pacedTarget = muscleTarget.targetSets * (pace[muscle] ?: 1.0)
        return maxOf(0.0, pacedTarget - (weeklyVolume[muscle] ?: 0.0))
    }

    /**
     * How far [muscle] is below its weekly floor ([VolumeTarget.minSets]), paced like [shortfall] so
     * a day owes only its share. Zero for a muscle with no target or already at its floor.
     */
    private fun floorShortfall(
        muscle: MuscleGroup,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget,
        pace: Map<MuscleGroup, Double> = emptyMap()
    ): Double {
        val muscleTarget = WeeklyVolumeTargets.forMuscle(target, muscle) ?: return 0.0
        val pacedFloor = muscleTarget.minSets * (pace[muscle] ?: 1.0)
        return maxOf(0.0, pacedFloor - (weeklyVolume[muscle] ?: 0.0))
    }

    /**
     * [shortfall] as a fraction of the muscle's own weekly target. Compound slots compare patterns
     * whose primary muscles have different-sized targets (a hinge includes the smaller lower-back
     * target), so they rank by how much of each target is still missing, not by raw sets.
     */
    private fun relativeShortfall(
        muscle: MuscleGroup,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Double {
        val muscleTarget = WeeklyVolumeTargets.forMuscle(target, muscle) ?: return 0.0
        if (muscleTarget.targetSets <= 0.0) return 0.0
        return shortfall(muscle, weeklyVolume, target) / muscleTarget.targetSets
    }

    /** The summed deficit a compound slot's candidate reaches; zero outside slot ranking. */
    private fun slotBreadth(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget,
        slotRanking: Boolean
    ): Double {
        val primaries = PATTERN_PRIMARY_MUSCLES[exercise.movementPattern]
        if (!slotRanking || primaries.isNullOrEmpty()) return 0.0
        val involvements = exercise.plannerInvolvements()
        return primaries.sumOf { muscle ->
            val weight = involvements[muscle] ?: 0.0
            val effective = if (weight >= fatigueConfig.targetedInvolvementCutoff) 1.0 else weight
            relativeShortfall(muscle, weeklyVolume, target) * effective
        }
    }

    /**
     * True while a muscle the exercise mainly trains (involvement at or above
     * [MAIN_INVOLVEMENT]) is still under its weekly target. A small side share, such as 0.3 abs
     * on a press, still counts toward the weekly totals but cannot justify adding a slot: almost
     * every exercise touches some under-target muscle, so counting those kept every day full.
     */
    private fun hasDeficit(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget,
        pace: Map<MuscleGroup, Double> = emptyMap()
    ): Boolean = exercise.plannerInvolvements().any { (muscle, weight) ->
        weight >= MAIN_INVOLVEMENT && shortfall(muscle, weeklyVolume, target, pace) > 0.0
    }

    /**
     * Soft ceiling: true when every muscle this exercise targets is already at or beyond its weekly
     * `maxSets`, checked before adding a whole slot. Additions can overshoot, and the direct-arm
     * coverage priority bypasses this gate.
     */
    private fun isAtMax(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Boolean {
        val ceilings = exercise.plannerInvolvements().keys.mapNotNull { muscle ->
            WeeklyVolumeTargets.forMuscle(target, muscle)?.let { muscle to it.maxSets }
        }
        return ceilings.isNotEmpty() &&
            ceilings.all { (muscle, maxSets) -> (weeklyVolume[muscle] ?: 0.0) >= maxSets }
    }

    private fun intensityScale(isDeload: Boolean): Double =
        if (isDeload) periodization.deloadIntensityScale else 1.0

    /**
     * Candidate ordering score: how sore the exercise leaves you, scaled by how strongly it loads
     * each muscle. A 0.4-weighted stabiliser at high fatigue counts less than a 1.0 primary.
     */
    private fun weightedFatigue(exercise: Exercise, fatigue: Map<MuscleGroup, Double>): Double =
        exercise.effectiveInvolvements.maxOfOrNull { (muscle, weight) ->
            weight * (fatigue[muscle] ?: 0.0)
        } ?: 0.0

    /**
     * The raw fatigue of muscles at or above [FatigueConfig.targetedInvolvementCutoff],
     * used for the skip/reduce decision so a low-weight stabiliser can't veto an exercise.
     */
    private fun targetedFatigue(exercise: Exercise, fatigue: Map<MuscleGroup, Double>): Double =
        DirectArmCoverage.targetedFatigue(exercise, fatigue, fatigueConfig)

    /** Prefers barbell > dumbbell > machine/kettlebell > band > pull-up bar > bodyweight. */
    private fun equipmentRank(exercise: Exercise): Int = exercise.requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .minOfOrNull { EQUIPMENT_RANK[it] ?: CUSTOM_EQUIPMENT_RANK }
        ?: Int.MAX_VALUE

    /** The isolation families a focus fills after its compounds, by largest remaining deficit. */
    private fun isolationPatterns(focus: SplitFocus): List<MovementPattern> =
        DirectArmCoverage.isolationPatternsFor(focus)

    companion object {
        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
        const val MIN_SETS = 1
        const val MAX_SETS = 8
        const val DEFAULT_SETS = DEFAULT_SETS_PER_EXERCISE

        /** Balanced's starting reps, the bottom of its compound and isolation ranges. */
        val COMPOUND_REPS = TrainingGoal.BALANCED.compoundReps
        val ISOLATION_REPS = TrainingGoal.BALANCED.isolationReps

        /** Any user-added equipment is ranked after the built-ins until it has its own preference. */
        const val CUSTOM_EQUIPMENT_RANK = 20

        /** Sets for the weekly shrug, a product default for optional upper-trap work. */
        private const val TRAP_SETS = 3

        /** The involvement at which a muscle counts as one an exercise mainly trains (D1). */
        const val MAIN_INVOLVEMENT = 0.5

        /** Knee extension, then knee flexion: the two leg isolations every lower or legs day gets. */
        private val KNEE_ISOLATION_MUSCLES = listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS)

        /** Keeps floating-point pacing (e.g. 8 x 0.5) from rounding a met share up a whole set. */
        private const val ARM_PACE_TOLERANCE = 1e-9

        /** Muscles excluded from weekly-volume deficit targeting (still counted for fatigue). */
        private val PLANNER_DEFERRED_MUSCLES = setOf(MuscleGroup.TRAPS)

        /** The muscles a compound pattern exists to train, used to rank that slot's candidates. */
        private val PATTERN_PRIMARY_MUSCLES: Map<MovementPattern, Set<MuscleGroup>> = mapOf(
            MovementPattern.HORIZONTAL_PUSH to
                setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            MovementPattern.VERTICAL_PUSH to setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            MovementPattern.HORIZONTAL_PULL to setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS),
            MovementPattern.VERTICAL_PULL to setOf(MuscleGroup.LATS),
            MovementPattern.SQUAT to setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            MovementPattern.LUNGE to setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            MovementPattern.HINGE to setOf(
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.GLUTES,
                MuscleGroup.LOWER_BACK
            )
        )

        private val EQUIPMENT_RANK = mapOf(
            EquipmentTag.BARBELL to 0,
            EquipmentTag.DUMBBELL to 1,
            EquipmentTag.CABLE_MACHINE to 2,
            EquipmentTag.KETTLEBELL to 3,
            EquipmentTag.RESISTANCE_BAND to 4,
            EquipmentTag.PULL_UP_BAR to 5,
            EquipmentTag.BENCH to 6,
            EquipmentTag.BODYWEIGHT to 7
        )
    }
}
