package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.replacementOccurrenceEntry
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * End-to-end load-policy parity: identical synthetic history goes through the real
 * [BuildPlannerLoadInputsUseCase], its output builds one [PlanRequest], and the Deterministic engine
 * and the shared [WeeklyPlanSanitizer] must then derive the same working load from the same
 * progression-adjusted e1RM bound. The per-class tests exercise each stage with independently supplied
 * numbers, so only this test proves the stages agree.
 */
class PlannerLoadPolicyParityTest {

    private val engine = DeterministicWorkoutPlannerEngine(FakeCatalog)
    private val sanitizer = WeeklyPlanSanitizer(FakeCatalog)
    private val dumbbellCatalog = LocalCatalog(DUMBBELL_CATALOG)
    private val addableCatalog = LocalCatalog(ADDABLE_CATALOG)
    private val builder = BuildPlannerLoadInputsUseCase(
        catalog = FakeCatalog,
        buildRecentWeights = BuildRecentWeightsUseCase(),
        progressWeights = ProgressWeightsUseCase()
    )
    private val addedBuilder = BuildPlannerLoadInputsUseCase(
        catalog = addableCatalog,
        buildRecentWeights = BuildRecentWeightsUseCase(),
        progressWeights = ProgressWeightsUseCase()
    )
    private val addedEngine = DeterministicWorkoutPlannerEngine(addableCatalog)
    private val addedSanitizer = WeeklyPlanSanitizer(addableCatalog)

    @Test
    fun deterministicAndSanitizerAgreeWithoutProgression() = runTest {
        val inputs = builder(build = sources(legacySets()), nowMillis = NOW)

        // No recorded target, so no increment: Epley(100 x 8) = 126.667;
        // BALANCED compound 6 reps -> 126.667 x 0.765 = 96.9 -> 97.5 kg.
        assertEquals(97.5, deterministicWeight(inputs))
        assertEquals(97.5, sanitizedWeight(inputs))
    }

    @Test
    fun deterministicAndSanitizerPrescribeTheSameRecordedAddedLoad() = runTest {
        val inputs = addedBuilder(
            sources = sources(addedSets()),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW
        )

        // The recorded 10 kg added load earns one 2.5 kg increment; neither engine estimates it.
        assertEquals(12.5, addedDeterministicWeight(inputs))
        assertEquals(12.5, addedSanitizedWeight(inputs))
    }

    @Test
    fun deterministicAndSanitizerAgreeWithEarnedProgression() = runTest {
        val inputs = builder(build = sources(completedSets()))

        // Baseline + one earned increment = 129.167; x 0.765 = 98.8125 -> 100 kg.
        assertEquals(100.0, deterministicWeight(inputs))
        assertEquals(100.0, sanitizedWeight(inputs))
    }

    @Test
    fun deterministicAndSanitizerAgreeOnADeloadWeek() = runTest {
        val inputs = builder(build = sources(completedSets()))

        // 129.167 x 0.765 x 0.8 = 79.05 -> 80 kg.
        assertEquals(80.0, deterministicWeight(inputs, isDeload = true))
        assertEquals(80.0, sanitizedWeight(inputs, isDeload = true))
    }

    @Test
    fun bothEnginesClampToTheEquipmentCeilingBelowTheConvertedBound() = runTest {
        val inputs = builder(build = sources(completedSets()))
        val ceiling = mapOf(EquipmentTag.CABLE_MACHINE to 60.0)

        assertEquals(60.0, deterministicWeight(inputs, equipmentMaxWeights = ceiling))
        assertEquals(60.0, sanitizedWeight(inputs, equipmentMaxWeights = ceiling))
    }

    @Test
    fun bothEnginesWithholdWhenRecentEvidenceIsInsufficient() = runTest {
        val inputs = builder(build = sources(listOf(set(day = 1))))

        assertNull(deterministicWeight(inputs))
        assertNull(sanitizedWeight(inputs))
    }

    @Test
    fun sanitizerKeepsABelowBoundProposalWhileACapIsActive() = runTest {
        val inputs = builder(build = sources(completedSets()))

        // The converted bound is 97.5 kg; a model proposal under it is not rounded or raised.
        assertEquals(50.0, sanitizedWeight(inputs, proposedWeightKg = 50.0))
    }

    @Test
    fun substitutionAndOccurrenceReplacementConvertTheCandidatesOwnBaselineIdentically() = runTest {
        val replacement = exercise(REPLACEMENT, MovementPattern.VERTICAL_PULL, MuscleGroup.LATS)
        val catalog = LocalCatalog(CATALOG + replacement)
        val inputs = builder(build = sources(completedSets()))
        val baseline = inputs.suggestedWeightsKg + (REPLACEMENT to 130.0)
        val substitute = SubstituteExerciseUseCase(
            catalog,
            UnusedPlanHistory,
            engine,
            SuggestedWeightConfig()
        )

        listOf(false, true).forEach { deload ->
            val request = request(
                daysPerWeek = 3,
                splitPreference = SplitType.PUSH_PULL_LEGS,
                inputs = inputs,
                isDeload = deload,
                equipmentMaxWeights = emptyMap()
            ).copy(suggestedWeightsKg = baseline)
            val scale = if (deload) {
                PeriodizationConfig.DEFAULT_DELOAD_INTENSITY_SCALE
            } else {
                1.0
            }
            val previewed = substitute.candidates(
                acceptedPrescription(isDeload = deload),
                dayIndex = 0,
                position = 0,
                request = request
            ).first { it.exerciseId == REPLACEMENT }
            val replaced = replacementOccurrenceEntry(
                original = OccurrenceEntry(
                    exerciseId = EXERCISE,
                    exerciseName = "Lat Pulldown",
                    movementPattern = MovementPattern.VERTICAL_PULL,
                    sets = 3,
                    reps = 8
                ),
                candidate = replacement,
                request = request,
                intensityScale = scale,
                weights = SuggestedWeightConfig()
            )

            // 130 x 0.72 (8 reps) = 93.6 -> 92.5 kg; a deload week scales it to 75 kg for both paths.
            val expected = if (deload) 75.0 else 92.5
            assertEquals(expected, previewed.suggestedWeightKg)
            assertEquals(expected, replaced.weightKg)
            assertEquals(previewed.loadKind, replaced.loadKind)
        }
    }

    @Test
    fun deterministicAndSanitizerBothDropAnAbsentDumbbellWeight() = runTest {
        val request = dumbbellRequest(DUMBBELL_INVENTORY)
        val plan = DeterministicWorkoutPlannerEngine(
            dumbbellCatalog
        ).plan(request, DUMBBELL_CATALOG)
        val deterministic = plan.days.flatMap { it.exercises }
            .first { it.exerciseId == DUMBBELL_ROW }
            .suggestedWeightKg

        val sanitized = WeeklyPlanSanitizer(dumbbellCatalog)
            .sanitize(
                modelPlan(proposedWeightKg = 500.0, row = DUMBBELL_ROW),
                request.copy(daysPerWeek = 1)
            )
            ?.days?.single()?.exercises
            ?.first { it.exerciseId == DUMBBELL_ROW }
            ?.suggestedWeightKg

        // 36 x 0.765 = 27.54; the generic step would say 27.5, which this grid does not own.
        assertEquals(25.0, deterministic)
        assertEquals(25.0, sanitized)
    }

    @Test
    fun substitutionAndOccurrenceReplacementUseTheSameReachableDumbbellLoad() = runTest {
        val request = dumbbellRequest(DUMBBELL_INVENTORY)
        val substitute = SubstituteExerciseUseCase(
            dumbbellCatalog,
            UnusedPlanHistory,
            DeterministicWorkoutPlannerEngine(dumbbellCatalog),
            SuggestedWeightConfig()
        )
        val previewed = substitute.candidates(
            acceptedDumbbellPrescription(),
            dayIndex = 0,
            position = 0,
            request = request
        ).first { it.exerciseId == DUMBBELL_CURL_ALT }
        val replaced = replacementOccurrenceEntry(
            original = OccurrenceEntry(
                exerciseId = "dumbbell-curl",
                exerciseName = "Dumbbell Curl",
                movementPattern = MovementPattern.BICEPS_ISOLATION,
                sets = 3,
                reps = 6
            ),
            candidate = dumbbellExercise(
                DUMBBELL_CURL_ALT,
                MovementPattern.BICEPS_ISOLATION,
                MuscleGroup.BICEPS
            ),
            request = request,
            intensityScale = 1.0,
            weights = SuggestedWeightConfig()
        )

        assertEquals(25.0, previewed.suggestedWeightKg)
        assertEquals(25.0, replaced.weightKg)
        // The candidate's own conversion reasoning is recorded; the replaced slot's is not copied.
        assertEquals(27.5, replaced.explanation?.conversion?.uncappedWorkingKg)
        assertNull(replaced.explanation?.baseline)
    }

    @Test
    fun anUnreachableKnownInventoryWithholdsTheTargetInsteadOfRoundingUp() = runTest {
        val request = dumbbellRequest(
            LoadingInventory(
                dumbbells = listOf(
                    LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(40_000))
                )
            )
        )
        val plan = DeterministicWorkoutPlannerEngine(
            dumbbellCatalog
        ).plan(request, DUMBBELL_CATALOG)
        val row = plan.days.flatMap { it.exercises }.first { it.exerciseId == DUMBBELL_ROW }

        assertNull(row.suggestedWeightKg)
        assertNull(row.explanation?.unavailableReason)
    }

    @Test
    fun theSanitizerUsesTheRawProgressionBoundBeforeGenericRounding() = runTest {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(25_000, 27_500)
                )
            )
        )
        val request = dumbbellRequest(inventory).copy(
            suggestedWeightsKg = mapOf(DUMBBELL_ROW to 35.5),
            recentWeightCaps = mapOf(DUMBBELL_ROW to 35.5)
        )
        val sanitized = WeeklyPlanSanitizer(dumbbellCatalog)
            .sanitize(
                modelPlan(proposedWeightKg = 500.0, row = DUMBBELL_ROW),
                request.copy(daysPerWeek = 1)
            )
            ?.days?.single()?.exercises
            ?.first { it.exerciseId == DUMBBELL_ROW }
            ?.suggestedWeightKg

        // 35.5 x 0.765 = 27.1575 kg. The generic step would first say 27.5 kg, which this grid also
        // owns; the raw bound must win and leave 25 kg, the highest reachable load below it.
        assertEquals(25.0, sanitized)
    }

    @Test
    fun aLowRepManualPrFloorOnlySuppliesAShortPrescriptionsLoad() = runTest {
        val request = PlanRequest(
            daysPerWeek = 2,
            availableEquipment = setOf(EquipmentTag.DUMBBELL),
            muscleFatigue = emptyMap(),
            nowMillis = NOW,
            suggestedWeightsKg = emptyMap(),
            recentWeightCaps = emptyMap(),
            withheldWeightExerciseIds = setOf(DUMBBELL_ROW),
            lowRepManualFloorsKg = mapOf(DUMBBELL_ROW to 50.0),
            includeWorkoutData = true
        )

        val short = resolveCandidatePrescribedLoad(
            candidateId = DUMBBELL_ROW,
            capability = ExerciseLoadCapability.EXTERNAL,
            reps = 2,
            request = request,
            equipmentCeilingKg = null,
            intensityScale = 1.0,
            weightConfig = SuggestedWeightConfig()
        )
        val repRange = resolveCandidatePrescribedLoad(
            candidateId = DUMBBELL_ROW,
            capability = ExerciseLoadCapability.EXTERNAL,
            reps = 5,
            request = request,
            equipmentCeilingKg = null,
            intensityScale = 1.0,
            weightConfig = SuggestedWeightConfig()
        )

        // 50 x 0.855 -> 42.5 kg generic, while a rep-range prescription has no eligible evidence.
        assertEquals(42.5, short.weightKg)
        assertNull(repRange.weightKg)
        assertEquals(
            LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE,
            repRange.explanation.unavailableReason
        )
    }

    @Test
    fun aLowRepFloorAppliesOnlyToTheShortTargetsOfAMixedPrescription() = runTest {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(25_000, 30_000, 35_000, 40_000)
                )
            )
        )
        val targets = SlotSetTargets.of(
            listOf(
                SetTarget(reps = 2, loadKind = LoadKind.EXTERNAL, weightKg = 10.0),
                SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 10.0)
            )
        )

        val rounded = roundOrderedTargetsToInventory(
            targets = targets,
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            estimatedOneRepMaxKg = 40.0,
            intensityScale = 1.0,
            ceilingKg = null,
            weightConfig = SuggestedWeightConfig(),
            lowRepOneRepMaxKg = 50.0
        )

        // Short: 50 x 0.855 = 42.75 -> 40; range: 40 x 0.72 = 28.8 -> 25. A blanket floor would
        // have put the 8-rep target on 35 kg instead.
        assertEquals(listOf(40.0, 25.0), rounded?.targets?.map { it.weightKg })
    }

    @Test
    fun mixedOrderedTargetsApplyTheLowRepFloorPerTargetWithAndWithoutInventory() = runTest {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 25_000, 30_000, 35_000, 40_000, 45_000, 50_000)
                )
            )
        )

        // Scalar slot reps are 6, so the slot-level floor is not eligible; only the 2-rep target is.
        listOf(null, inventory).forEach { activeInventory ->
            val request = mixedOrderedRequest(
                inventory = activeInventory,
                suggested = mapOf(DUMBBELL_ROW to 40.0),
                caps = mapOf(DUMBBELL_ROW to 40.0),
                floors = mapOf(DUMBBELL_ROW to 60.0)
            )
            val expected = if (activeInventory == null) {
                listOf(52.5, 30.0)
            } else {
                listOf(50.0, 25.0)
            }
            val row = DeterministicWorkoutPlannerEngine(dumbbellCatalog)
                .plan(request, DUMBBELL_CATALOG)
                .days.flatMap { it.exercises }
                .first { it.exerciseId == DUMBBELL_ROW }
            assertEquals(listOf(2, 8), row.setTargets?.targets?.map { it.reps })
            assertEquals(expected, row.setTargets?.targets?.map { it.weightKg })

            val sanitized = WeeklyPlanSanitizer(dumbbellCatalog)
                .sanitize(
                    modelPlan(proposedWeightKg = 500.0, row = DUMBBELL_ROW),
                    request.copy(daysPerWeek = 1)
                )
                ?.days?.single()?.exercises
                ?.first { it.exerciseId == DUMBBELL_ROW }
            assertEquals(expected, sanitized?.setTargets?.targets?.map { it.weightKg })
        }
    }

    @Test
    fun orderedLowRepTargetsNeverExceedTheEquipmentCeilingWithoutApplicableInventory() = runTest {
        // No saved implement for the exercise's kind: the recalculated low-rep target keeps generic
        // increment rounding, which must still respect the equipment's own maximum.
        val cases = listOf(
            "null inventory" to null,
            "empty inventory" to LoadingInventory(),
            "non-matching inventory" to LoadingInventory(
                bars = listOf(LoadingBar(id = "bar", name = "Bar", weightTicks = 20_000))
            )
        )
        val ceiling = mapOf(EquipmentTag.DUMBBELL to 40.0)
        cases.forEach { (label, inventory) ->
            val request = mixedOrderedRequest(
                inventory = inventory,
                suggested = mapOf(DUMBBELL_ROW to 40.0),
                caps = mapOf(DUMBBELL_ROW to 40.0),
                floors = mapOf(DUMBBELL_ROW to 60.0)
            ).copy(equipmentMaxWeights = ceiling)
            val row = DeterministicWorkoutPlannerEngine(dumbbellCatalog)
                .plan(request, DUMBBELL_CATALOG)
                .days.flatMap { it.exercises }
                .first { it.exerciseId == DUMBBELL_ROW }
            val weights = row.setTargets?.targets?.map { it.weightKg }
            assertEquals(listOf(2, 8), row.setTargets?.targets?.map { it.reps }, label)
            // 60 x 0.855 = 51.3 would round to 52.5 kg, above the 40 kg dumbbell maximum.
            assertEquals(listOf(40.0, 30.0), weights, label)
            assertTrue(weights.orEmpty().all { it == null || it <= 40.0 }, label)

            val sanitized = WeeklyPlanSanitizer(dumbbellCatalog)
                .sanitize(
                    modelPlan(proposedWeightKg = 500.0, row = DUMBBELL_ROW),
                    request.copy(daysPerWeek = 1)
                )
                ?.days?.single()?.exercises
                ?.first { it.exerciseId == DUMBBELL_ROW }
            val sanitizedWeights = sanitized?.setTargets?.targets?.map { it.weightKg }
            assertEquals(listOf(40.0, 30.0), sanitizedWeights, label)
        }
    }

    @Test
    fun aManualOnlyShortTargetIsGeneratedWithoutAScalarBound() = runTest {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 25_000, 30_000, 35_000, 40_000, 45_000, 50_000)
                )
            )
        )
        val request = mixedOrderedRequest(
            inventory = inventory,
            suggested = emptyMap(),
            caps = emptyMap(),
            floors = mapOf(DUMBBELL_ROW to 60.0),
            withheld = setOf(DUMBBELL_ROW)
        )
        val row = DeterministicWorkoutPlannerEngine(dumbbellCatalog)
            .plan(request, DUMBBELL_CATALOG)
            .days.flatMap { it.exercises }
            .first { it.exerciseId == DUMBBELL_ROW }

        // Thin evidence: no scalar number, but the recorded short-rep floor still supplies set 1.
        assertNull(row.suggestedWeightKg)
        assertEquals(
            LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE,
            row.explanation?.unavailableReason
        )
        assertEquals(listOf(50.0, null), row.setTargets?.targets?.map { it.weightKg })

        val sanitized = WeeklyPlanSanitizer(dumbbellCatalog)
            .sanitize(
                modelPlan(proposedWeightKg = null, row = DUMBBELL_ROW),
                request.copy(daysPerWeek = 1)
            )
            ?.days?.single()?.exercises
            ?.first { it.exerciseId == DUMBBELL_ROW }
        assertEquals(listOf(50.0, null), sanitized?.setTargets?.targets?.map { it.weightKg })
    }

    @Test
    fun swapCandidatesCarryTheirOwnLoadOutcomeRatherThanThePlans() = runTest {
        val alternative = dumbbellExercise(
            "dumbbell-curl-alt-2",
            MovementPattern.BICEPS_ISOLATION,
            MuscleGroup.BICEPS
        )
        val catalog = LocalCatalog(DUMBBELL_CATALOG + alternative)
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(40_000))
            )
        )
        val request = dumbbellRequest(inventory).copy(
            suggestedWeightsKg = mapOf(DUMBBELL_CURL_ALT to 40.0),
            recentWeightCaps = mapOf(DUMBBELL_CURL_ALT to 40.0)
        )
        val candidates = SubstituteExerciseUseCase(
            catalog,
            UnusedPlanHistory,
            DeterministicWorkoutPlannerEngine(catalog),
            SuggestedWeightConfig()
        ).candidates(acceptedDumbbellPrescription(), dayIndex = 0, position = 0, request = request)

        val unreachable = candidates.first { it.exerciseId == DUMBBELL_CURL_ALT }
        val noHistory = candidates.first { it.exerciseId == "dumbbell-curl-alt-2" }
        assertNull(unreachable.suggestedWeightKg)
        assertEquals(LoadInventoryOutcome.NO_REACHABLE_LOAD, unreachable.loadInventoryOutcome)
        assertNull(noHistory.suggestedWeightKg)
        assertEquals(
            LoadInventoryOutcome.INSUFFICIENT_RECENT_EVIDENCE,
            noHistory.loadInventoryOutcome
        )
    }

    @Test
    fun aSanitizedAiNumberWithheldByTheInventoryKeepsItsAcceptedAdjustment() = runTest {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(40_000))
            )
        )
        val request = dumbbellRequest(inventory).copy(daysPerWeek = 1)
        val row = WeeklyPlanSanitizer(dumbbellCatalog)
            .sanitize(modelPlan(proposedWeightKg = 500.0, row = DUMBBELL_ROW), request)
            ?.days?.single()?.exercises
            ?.first { it.exerciseId == DUMBBELL_ROW }

        // The model number was accepted and then withheld by the saved implement; the adjustment
        // record is what proves the app had converted a number, not a conversion field.
        assertNull(row?.suggestedWeightKg)
        assertEquals(true, row?.explanation?.aiAdjustment?.proposedAccepted)
        assertNull(row?.explanation?.conversion)
    }

    private fun mixedOrderedRequest(
        inventory: LoadingInventory?,
        suggested: Map<String, Double>,
        caps: Map<String, Double>,
        floors: Map<String, Double> = emptyMap(),
        withheld: Set<String> = emptySet()
    ) = PlanRequest(
        daysPerWeek = 2,
        availableEquipment = setOf(EquipmentTag.DUMBBELL),
        muscleFatigue = emptyMap(),
        splitPreference = SplitType.AUTO,
        nowMillis = NOW,
        goal = TrainingGoal.BALANCED,
        setsPerExercise = 2,
        accessorySetsPerExercise = 2,
        suggestedWeightsKg = suggested,
        recentWeightCaps = caps,
        withheldWeightExerciseIds = withheld,
        lowRepManualFloorsKg = floors,
        loadingInventory = inventory,
        includeWorkoutData = true,
        automaticSetPrescription = AutomaticSetPrescription(
            shape = SetPrescriptionShape.DESCENDING_LOAD,
            minReps = 2,
            maxReps = 8
        )
    )

    @Test
    fun bothReplacementConsumersWithholdACompatibleGridWhenTheNamedKindIsWrong() = runTest {
        val inventory = LoadingInventory(
            bars = listOf(LoadingBar(id = "bar", name = "Bar", weightTicks = 20_000)),
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 25_000, 30_000)
                )
            )
        )
        val request = dumbbellRequest(inventory)
        val policy = SlotProgressionPolicy(
            minReps = 6,
            maxReps = 8,
            incrementKg = 2.5,
            equipmentId = "bar"
        )
        val substitute = SubstituteExerciseUseCase(
            dumbbellCatalog,
            UnusedPlanHistory,
            DeterministicWorkoutPlannerEngine(dumbbellCatalog),
            SuggestedWeightConfig()
        )
        val plan = acceptedDumbbellPrescription().let { accepted ->
            accepted.copy(
                days = accepted.days.map { day ->
                    day.copy(
                        exercises = day.exercises.map { it.copy(progressionPolicy = policy) }
                    )
                }
            )
        }
        val previewed = substitute.candidates(
            plan,
            dayIndex = 0,
            position = 0,
            request = request
        ).first { it.exerciseId == DUMBBELL_CURL_ALT }
        val replaced = replacementOccurrenceEntry(
            original = OccurrenceEntry(
                exerciseId = DUMBBELL_CURL,
                exerciseName = "Dumbbell Curl",
                movementPattern = MovementPattern.BICEPS_ISOLATION,
                sets = 3,
                reps = 6,
                progressionPolicy = policy
            ),
            candidate = dumbbellExercise(
                DUMBBELL_CURL_ALT,
                MovementPattern.BICEPS_ISOLATION,
                MuscleGroup.BICEPS
            ),
            request = request,
            intensityScale = 1.0,
            weights = SuggestedWeightConfig()
        )

        // The bar's grid is reachable numerically, but a dumbbell exercise cannot use it.
        assertNull(previewed.suggestedWeightKg)
        assertNull(replaced.weightKg)
    }

    private fun dumbbellRequest(inventory: LoadingInventory) = PlanRequest(
        daysPerWeek = 2,
        availableEquipment = setOf(EquipmentTag.DUMBBELL),
        muscleFatigue = emptyMap(),
        splitPreference = SplitType.AUTO,
        nowMillis = NOW,
        goal = TrainingGoal.BALANCED,
        suggestedWeightsKg = mapOf(DUMBBELL_ROW to 36.0, DUMBBELL_CURL_ALT to 36.0),
        recentWeightCaps = mapOf(DUMBBELL_ROW to 36.0, DUMBBELL_CURL_ALT to 36.0),
        loadingInventory = inventory,
        includeWorkoutData = true
    )

    private fun modelPlan(proposedWeightKg: Double?, row: String) = WeeklyPlan(
        engine = PlannerEngineId.GEMINI_API,
        days = listOf(
            WorkoutDay(
                dayIndex = 0,
                focus = SplitFocus.FULL_BODY,
                exercises = listOf(
                    PlannedExercise(
                        exerciseId = row,
                        sets = 3,
                        reps = 6,
                        suggestedWeightKg = proposedWeightKg
                    ),
                    PlannedExercise(
                        exerciseId = "dumbbell-curl",
                        sets = 3,
                        reps = 6,
                        suggestedWeightKg = proposedWeightKg
                    )
                )
            )
        )
    )

    private fun acceptedDumbbellPrescription() = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        id = 1L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PULL,
                exercises = listOf(
                    AcceptedExercise(
                        exerciseId = DUMBBELL_CURL,
                        sets = 3,
                        reps = 6,
                        name = "Dumbbell Curl",
                        movementPattern = MovementPattern.BICEPS_ISOLATION,
                        suggestedWeightKg = 10.0,
                        loadCapability = ExerciseLoadCapability.EXTERNAL,
                        loadKind = LoadKind.EXTERNAL
                    )
                )
            )
        )
    )

    private fun dumbbellExercise(id: String, pattern: MovementPattern, primary: MuscleGroup) =
        Exercise(
            id = id,
            name = id,
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(primary),
            movementPattern = pattern,
            loadCapability = ExerciseLoadCapability.EXTERNAL
        )

    private object UnusedPlanHistory : PlanHistoryRepository {
        override fun observeLatest(): Flow<AcceptedPlan?> = flowOf(null)

        override fun observeHistory(): Flow<List<AcceptedPlan>> = flowOf(emptyList())

        override suspend fun latest(): AcceptedPlan? = null

        override suspend fun accept(plan: AcceptedPlan) = Unit

        override suspend fun substitute(
            planId: Long,
            dayIndex: Int,
            position: Int,
            newExerciseId: String,
            newExerciseName: String,
            newWeightKg: Double?,
            newLoadCapability: ExerciseLoadCapability,
            newLoadKind: LoadKind
        ) = Unit

        override suspend fun setMeasure(
            planId: Long,
            dayIndex: Int,
            position: Int,
            reps: Int?,
            durationSeconds: Int?,
            distanceMeters: Int?,
            forceNewtons: Double?
        ) = Unit

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }

    private suspend fun builder(
        build: WorkoutPlanSources,
        nowMillis: Long = NOW
    ): PlannerLoadInputs = builder(
        sources = build,
        pauseIncrements = false,
        utcOffsetMillis = 0L,
        nowMillis = nowMillis
    )

    private fun deterministicWeight(
        inputs: PlannerLoadInputs,
        isDeload: Boolean = false,
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap()
    ): Double? {
        val request = request(
            daysPerWeek = 3,
            splitPreference = SplitType.PUSH_PULL_LEGS,
            inputs = inputs,
            isDeload = isDeload,
            equipmentMaxWeights = equipmentMaxWeights
        )
        return engine.plan(request, CATALOG)
            .days.flatMap { it.exercises }
            .firstOrNull { it.exerciseId == EXERCISE }
            ?.suggestedWeightKg
    }

    private fun addedDeterministicWeight(inputs: PlannerLoadInputs): Double? {
        val request = PlanRequest(
            daysPerWeek = 2,
            availableEquipment = emptySet(),
            muscleFatigue = emptyMap(),
            nowMillis = NOW,
            suggestedAddedLoadsKg = inputs.suggestedAddedLoadsKg
        )
        return addedEngine.plan(request, ADDABLE_CATALOG)
            .days.flatMap { it.exercises }
            .firstOrNull { it.exerciseId == ADDED_EXERCISE }
            ?.suggestedWeightKg
    }

    private suspend fun addedSanitizedWeight(inputs: PlannerLoadInputs): Double? {
        val request = PlanRequest(
            daysPerWeek = 1,
            availableEquipment = emptySet(),
            muscleFatigue = emptyMap(),
            nowMillis = NOW,
            suggestedAddedLoadsKg = inputs.suggestedAddedLoadsKg
        )
        val modelPlan = WeeklyPlan(
            engine = PlannerEngineId.GEMINI_API,
            days = listOf(
                WorkoutDay(
                    dayIndex = 0,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = ADDED_EXERCISE,
                            sets = 3,
                            reps = 8,
                            suggestedWeightKg = ABOVE_ANY_BOUND
                        ),
                        PlannedExercise(
                            exerciseId = "weighted-dip",
                            sets = 3,
                            reps = 8,
                            suggestedWeightKg = ABOVE_ANY_BOUND
                        )
                    )
                )
            )
        )
        return addedSanitizer.sanitize(modelPlan, request)!!
            .days.single().exercises
            .first { it.exerciseId == ADDED_EXERCISE }
            .suggestedWeightKg
    }

    private suspend fun sanitizedWeight(
        inputs: PlannerLoadInputs,
        isDeload: Boolean = false,
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
        proposedWeightKg: Double = ABOVE_ANY_BOUND
    ): Double? {
        val request = request(
            daysPerWeek = 1,
            splitPreference = SplitType.AUTO,
            inputs = inputs,
            isDeload = isDeload,
            equipmentMaxWeights = equipmentMaxWeights
        )
        val modelPlan = WeeklyPlan(
            engine = PlannerEngineId.GEMINI_API,
            days = listOf(
                WorkoutDay(
                    dayIndex = 0,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = EXERCISE,
                            sets = 3,
                            reps = 6,
                            suggestedWeightKg = proposedWeightKg
                        ),
                        PlannedExercise(
                            exerciseId = "cable-row",
                            sets = 3,
                            reps = 6,
                            suggestedWeightKg = proposedWeightKg
                        )
                    )
                )
            )
        )
        return sanitizer.sanitize(modelPlan, request)!!
            .days.single().exercises
            .first { it.exerciseId == EXERCISE }
            .suggestedWeightKg
    }

    private fun request(
        daysPerWeek: Int,
        splitPreference: SplitType,
        inputs: PlannerLoadInputs,
        isDeload: Boolean,
        equipmentMaxWeights: Map<EquipmentTag, Double>
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.CABLE_MACHINE),
        muscleFatigue = emptyMap(),
        splitPreference = splitPreference,
        nowMillis = NOW,
        goal = TrainingGoal.BALANCED,
        suggestedWeightsKg = inputs.suggestedWeightsKg,
        recentWeightCaps = inputs.recentWeightCaps,
        withheldWeightExerciseIds = inputs.withheldWeightExerciseIds,
        equipmentMaxWeights = equipmentMaxWeights,
        includeWorkoutData = true,
        isDeload = isDeload
    )

    /** Three completed added-load sessions inside the window, each carrying its own recorded target. */
    private fun addedSets(): List<WorkoutSet> = listOf(98L, 99L, 100L).flatMap { day ->
        List(3) {
            WorkoutSet(
                exerciseId = ADDED_EXERCISE,
                reps = 8,
                weightKg = 10.0,
                loadKind = LoadKind.ADDED,
                performedAtMillis = day * DAY_MILLIS,
                sessionId = "s$day",
                prescriptionSnapshot = WorkoutPrescriptionSnapshot(
                    exerciseId = ADDED_EXERCISE,
                    sets = 3,
                    reps = 8,
                    loadKind = LoadKind.ADDED,
                    weightKg = 10.0,
                    isDeload = false
                )
            )
        }
    }

    /** Three completed external sessions inside the window, each carrying its own recorded target. */
    private fun completedSets(): List<WorkoutSet> =
        listOf(98L, 99L, 100L).flatMap { day -> List(3) { set(day) } }

    /** The same history without any recorded target, so progression has nothing to progress from. */
    private fun legacySets(): List<WorkoutSet> =
        listOf(98L, 99L, 100L).flatMap { day -> List(3) { set(day, withTarget = false) } }

    private fun acceptedPrescription(isDeload: Boolean = false): AcceptedPlan = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        id = 1L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PULL,
                exercises = listOf(
                    AcceptedExercise(
                        exerciseId = EXERCISE,
                        sets = 3,
                        reps = 8,
                        name = "Lat Pulldown",
                        movementPattern = MovementPattern.VERTICAL_PULL,
                        suggestedWeightKg = 80.0,
                        loadCapability = ExerciseLoadCapability.EXTERNAL,
                        loadKind = LoadKind.EXTERNAL
                    )
                )
            )
        ),
        explanation = FrozenPlanExplanation(
            attribution = PlanAttribution.DETERMINISTIC,
            isDeload = isDeload,
            deloadVolumeScale = PeriodizationConfig.DEFAULT_DELOAD_VOLUME_SCALE,
            deloadIntensityScale = PeriodizationConfig.DEFAULT_DELOAD_INTENSITY_SCALE
        )
    )

    private fun sources(sets: List<WorkoutSet>): WorkoutPlanSources = WorkoutPlanSources(
        availableEquipment = setOf(EquipmentTag.CABLE_MACHINE),
        selectedEngine = PlannerEngineId.DETERMINISTIC,
        daysPerWeek = 3,
        loggedSets = emptyList(),
        loggedWorkoutSets = sets,
        progressionHistory = ProgressionHistory(sets, sessionsOf(sets))
    )

    private fun sessionsOf(sets: List<WorkoutSet>) = sets
        .mapNotNull { set -> set.sessionId?.let { it to set } }
        .groupBy({ it.first }, { it.second })
        .map { (id, group) ->
            WorkoutSession(
                id = id,
                startedAtMillis = group.minOf { it.performedAtMillis },
                endedAtMillis = group.maxOf { it.performedAtMillis },
                localEpochDay = 0
            )
        }

    private fun set(day: Long, withTarget: Boolean = true): WorkoutSet = WorkoutSet(
        exerciseId = EXERCISE,
        reps = 8,
        weightKg = 100.0,
        loadKind = LoadKind.EXTERNAL,
        performedAtMillis = day * DAY_MILLIS,
        sessionId = "s$day",
        prescriptionSnapshot = if (withTarget) {
            WorkoutPrescriptionSnapshot(
                exerciseId = EXERCISE,
                sets = 3,
                reps = 8,
                loadKind = LoadKind.EXTERNAL,
                weightKg = 80.0,
                isDeload = false
            )
        } else {
            null
        }
    )

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = CATALOG
    }

    private class LocalCatalog(private val exercises: List<Exercise>) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = exercises
    }

    private companion object {
        const val EXERCISE = "lat-pulldown"
        const val ADDED_EXERCISE = "weighted-pull-up"
        const val REPLACEMENT = "cable-pulldown"
        const val DUMBBELL_ROW = "dumbbell-row"
        const val DUMBBELL_CURL = "dumbbell-curl"
        const val DUMBBELL_CURL_ALT = "dumbbell-curl-alt"
        const val DAY_MILLIS = 86_400_000L
        const val NOW = 100 * DAY_MILLIS

        val DUMBBELL_INVENTORY = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 22_500, 25_000, 30_000)
                )
            )
        )

        val DUMBBELL_CATALOG: List<Exercise> = listOf(
            Exercise(
                id = "dumbbell-squat",
                name = "Dumbbell Squat",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS),
                movementPattern = MovementPattern.SQUAT
            ),
            Exercise(
                id = "dumbbell-press",
                name = "Dumbbell Press",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
                movementPattern = MovementPattern.HORIZONTAL_PUSH
            ),
            Exercise(
                id = DUMBBELL_ROW,
                name = "Dumbbell Row",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.UPPER_BACK),
                movementPattern = MovementPattern.HORIZONTAL_PULL
            ),
            Exercise(
                id = DUMBBELL_CURL,
                name = "Dumbbell Curl",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.BICEPS),
                movementPattern = MovementPattern.BICEPS_ISOLATION
            ),
            Exercise(
                id = DUMBBELL_CURL_ALT,
                name = "Dumbbell Curl Alt",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.BICEPS),
                movementPattern = MovementPattern.BICEPS_ISOLATION
            )
        )

        /** Higher than any converted bound here, so the sanitizer always clamps unless withheld. */
        const val ABOVE_ANY_BOUND = 500.0

        val ADDABLE_CATALOG: List<Exercise> = listOf(
            Exercise(
                id = ADDED_EXERCISE,
                name = "Weighted Pull-Up",
                requiredEquipment = emptySet(),
                primaryMuscles = setOf(MuscleGroup.LATS),
                movementPattern = MovementPattern.VERTICAL_PULL,
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ADDABLE
            ),
            Exercise(
                id = "weighted-dip",
                name = "Weighted Dip",
                requiredEquipment = emptySet(),
                primaryMuscles = setOf(MuscleGroup.CHEST_LOWER),
                movementPattern = MovementPattern.VERTICAL_PUSH,
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ADDABLE
            )
        )

        val CATALOG = listOf(
            exercise(EXERCISE, MovementPattern.VERTICAL_PULL, MuscleGroup.LATS),
            exercise("cable-row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.UPPER_BACK),
            exercise("biceps-curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS),
            exercise("triceps-pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
        )

        fun exercise(id: String, pattern: MovementPattern, primary: MuscleGroup) = Exercise(
            id = id,
            name = id,
            requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE),
            primaryMuscles = setOf(primary),
            movementPattern = pattern,
            loadCapability = ExerciseLoadCapability.EXTERNAL
        )
    }
}
