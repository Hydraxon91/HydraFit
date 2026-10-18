package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SetPrescriptionRecipeTest {

    private val build = BuildSetPrescriptionUseCase()

    @Test
    fun aStraightRecipeRepeatsOneTarget() {
        val targets = build(request(shape = SetPrescriptionShape.STRAIGHT))

        assertEquals(listOf(8, 8, 8), targets.targets.map { it.reps })
        assertEquals(listOf(60.0, 60.0, 60.0), targets.targets.map { it.weightKg })
    }

    @Test
    fun anAscendingRecipeClimbsLoadAndFallsReps() {
        val targets = build(
            request(
                shape = SetPrescriptionShape.ASCENDING_LOAD,
                startReps = 10,
                endReps = 6,
                startLoadKg = 60.0,
                endLoadKg = 80.0
            )
        )

        assertEquals(listOf(10, 8, 6), targets.targets.map { it.reps })
        assertEquals(listOf(60.0, 70.0, 80.0), targets.targets.map { it.weightKg })
    }

    @Test
    fun aDescendingRecipeFallsLoadAndClimbsReps() {
        val targets = build(
            request(
                shape = SetPrescriptionShape.DESCENDING_LOAD,
                startReps = 6,
                endReps = 10,
                startLoadKg = 80.0,
                endLoadKg = 60.0
            )
        )

        assertEquals(listOf(6, 8, 10), targets.targets.map { it.reps })
        assertEquals(listOf(80.0, 70.0, 60.0), targets.targets.map { it.weightKg })
    }

    @Test
    fun aPyramidPeaksLoadAndTroughsRepsAtTheMiddleSet() {
        val targets = build(
            request(
                shape = SetPrescriptionShape.PYRAMID,
                startReps = 10,
                endReps = 6,
                startLoadKg = 60.0,
                endLoadKg = 80.0
            )
        )

        assertEquals(listOf(10, 6, 10), targets.targets.map { it.reps })
        assertEquals(listOf(60.0, 80.0, 60.0), targets.targets.map { it.weightKg })
    }

    @Test
    fun generatedLoadsLandOnTheUsersOwnRoundingStep() {
        val targets = build(
            request(
                shape = SetPrescriptionShape.ASCENDING_LOAD,
                startLoadKg = 60.0,
                endLoadKg = 65.0,
                roundToKg = 2.5
            )
        )

        assertEquals(listOf(60.0, 62.5, 65.0), targets.targets.map { it.weightKg })
    }

    @Test
    fun aSingleSetRecipeUsesTheStartValuesRatherThanDividingByZero() {
        val targets = build(
            request(
                shape = SetPrescriptionShape.PYRAMID,
                sets = 1,
                endReps = 4,
                endLoadKg = 90.0
            )
        )

        assertEquals(listOf(8), targets.targets.map { it.reps })
        assertEquals(listOf(60.0), targets.targets.map { it.weightKg })
    }

    @Test
    fun aPrescribedEffortTargetIsAppliedToEveryGeneratedSet() {
        val targets = build(request(targetRir = 2))

        assertEquals(listOf(2, 2, 2), targets.targets.map { it.targetRir })
        assertEquals(listOf(8, 8, 8), targets.targets.map { it.equivalentRpe })
    }

    @Test
    fun anUnspecifiedLoadStaysUnspecifiedRatherThanBecomingZero() {
        val targets = build(request(startLoadKg = null, endLoadKg = null))

        assertEquals(listOf(null, null, null), targets.targets.map { it.weightKg })
    }

    @Test
    fun theRecipeThatProducedTheTargetsIsRecordedSoReopeningIsExact() {
        val built = build(
            request(
                shape = SetPrescriptionShape.PYRAMID,
                startReps = 10,
                endReps = 6,
                startLoadKg = 60.0,
                endLoadKg = 80.0,
                targetRir = 2
            )
        )

        val recipe = built.recipe
        assertEquals(SetPrescriptionShape.PYRAMID, recipe?.shape)
        assertEquals(3, recipe?.sets)
        assertEquals(10, recipe?.startReps)
        assertEquals(6, recipe?.endReps)
        assertEquals(60.0, recipe?.startLoadKg)
        assertEquals(80.0, recipe?.endLoadKg)
        assertEquals(2, recipe?.targetRir)
    }

    @Test
    fun anAutomaticPrescriptionUsesTheUsersRangeAndThePlannersOwnConversion() {
        val automatic = AutomaticSetPrescription(
            shape = SetPrescriptionShape.ASCENDING_LOAD,
            minReps = 6,
            maxReps = 10
        )
        val weightConfig = SuggestedWeightConfig()

        val targets = ApplySetPrescriptionRecipeUseCase(weightConfig)(
            AutomaticSetPrescriptionRequest(
                automatic = automatic,
                sets = 3,
                reps = 8,
                loadKind = LoadKind.EXTERNAL,
                estimatedOneRepMaxKg = 100.0
            )
        )

        assertEquals(listOf(10, 8, 6), targets?.targets?.map { it.reps })
        // Each set's load is the planner's own conversion for that set's reps, not a ladder.
        assertEquals(
            listOf(
                weightConfig.workingWeightFor(100.0, 10),
                weightConfig.workingWeightFor(100.0, 8),
                weightConfig.workingWeightFor(100.0, 6)
            ),
            targets?.targets?.map { it.weightKg }
        )
        // The automatic path derives each set's load from the conversion, so it records no manual
        // recipe; the editor shows the stored targets rather than a shape it cannot reproduce.
        assertNull(targets?.recipe)
    }

    @Test
    fun anAutomaticPrescriptionRecordsNoManualRecipe() {
        // A recipe describes the manual builder's inputs. This prescription came from the generation
        // opt-in, and its per-set loads are the conversion for each set's own reps, so a manual recipe
        // could not reproduce them; the editor shows a recipe-less slot's stored targets instead.
        for (shape in SetPrescriptionShape.entries) {
            val targets = ApplySetPrescriptionRecipeUseCase()(
                AutomaticSetPrescriptionRequest(
                    automatic = AutomaticSetPrescription(shape, minReps = 6, maxReps = 10),
                    sets = 3,
                    reps = 8,
                    loadKind = LoadKind.EXTERNAL,
                    estimatedOneRepMaxKg = 100.0
                )
            )

            assertNull(targets?.recipe, "shape $shape must not claim a manual recipe")
        }
    }

    @Test
    fun anAutomaticPrescriptionProducesNothingWithoutSomethingToBuildFrom() {
        val automatic = AutomaticSetPrescription(
            shape = SetPrescriptionShape.STRAIGHT,
            minReps = 6,
            maxReps = 10
        )
        val useCase = ApplySetPrescriptionRecipeUseCase()

        fun build(loadKind: LoadKind = LoadKind.EXTERNAL, reps: Int? = 8, bound: Double? = 100.0) =
            useCase(
                AutomaticSetPrescriptionRequest(
                    automatic = automatic,
                    sets = 3,
                    reps = reps,
                    loadKind = loadKind,
                    estimatedOneRepMaxKg = bound
                )
            )

        // External-load work only, and only with a rep count and an estimated-1RM bound to convert.
        assertNull(build(loadKind = LoadKind.BODYWEIGHT))
        assertNull(build(reps = null))
        assertNull(build(bound = null))
        assertNull(build(bound = 0.0))
    }

    @Test
    fun anAutomaticStraightPrescriptionRepeatsTheSlotsOwnReps() {
        val targets = ApplySetPrescriptionRecipeUseCase()(
            AutomaticSetPrescriptionRequest(
                automatic = AutomaticSetPrescription(
                    shape = SetPrescriptionShape.STRAIGHT,
                    minReps = 6,
                    maxReps = 10,
                    targetRir = 2
                ),
                sets = 2,
                reps = 8,
                loadKind = LoadKind.EXTERNAL,
                estimatedOneRepMaxKg = 100.0
            )
        )

        assertEquals(listOf(8, 8), targets?.targets?.map { it.reps })
        assertEquals(listOf(2, 2), targets?.targets?.map { it.targetRir })
    }

    @Test
    fun outOfRangeInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { build(request(sets = 0)) }
        assertFailsWith<IllegalArgumentException> { build(request(sets = 9)) }
        assertFailsWith<IllegalArgumentException> { build(request(roundToKg = 0.0)) }
        assertFailsWith<IllegalArgumentException> { build(request(targetRir = 9)) }
        assertFailsWith<IllegalArgumentException> { build(request(startReps = 0)) }
    }

    private fun request(
        shape: SetPrescriptionShape = SetPrescriptionShape.STRAIGHT,
        sets: Int = 3,
        startReps: Int = 8,
        endReps: Int = 8,
        startLoadKg: Double? = 60.0,
        endLoadKg: Double? = 60.0,
        roundToKg: Double = DEFAULT_SET_PRESCRIPTION_ROUND_TO_KG,
        targetRir: Int? = null
    ) = SetPrescriptionRequest(
        shape = shape,
        sets = sets,
        startReps = startReps,
        endReps = endReps,
        startLoadKg = startLoadKg,
        endLoadKg = endLoadKg,
        roundToKg = roundToKg,
        targetRir = targetRir
    )
}
