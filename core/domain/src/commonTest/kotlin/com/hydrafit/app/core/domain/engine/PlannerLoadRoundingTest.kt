package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerLoadRoundingTest {

    private val dumbbellEquipment = setOf(EquipmentTag.DUMBBELL)
    private val barbellEquipment = setOf(EquipmentTag.BARBELL)

    @Test
    fun aDumbbellGridWithout275NeverProduces275() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(20_000, 22_500, 25_000, 30_000)
                )
            )
        )

        val resolution = resolveInventoryLoad(
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = dumbbellEquipment,
            boundKg = 27.4
        )

        assertIs<InventoryLoadResolution.Reachable>(resolution)
        assertEquals(25.0, resolution.weightKg)
    }

    @Test
    fun theReachableLoadNeverExceedsTheUnroundedBound() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(25_000, 30_000))
            )
        )

        val resolution = resolveInventoryLoad(
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = dumbbellEquipment,
            boundKg = 29.999
        )

        assertIs<InventoryLoadResolution.Reachable>(resolution)
        assertTrue(resolution.weightKg <= 29.999)
        assertEquals(25.0, resolution.weightKg)
    }

    @Test
    fun anExplicitDumbbellIdentityIsNeverBorrowed() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "light", name = "Light", fixedTicks = listOf(20_000)),
                LoadingDumbbell(id = "heavy", name = "Heavy", fixedTicks = listOf(25_000))
            )
        )

        val named = resolveInventoryLoad(
            inventory = inventory,
            equipmentId = "light",
            requiredEquipment = dumbbellEquipment,
            boundKg = 24.0
        )
        val missing = resolveInventoryLoad(
            inventory = inventory,
            equipmentId = "absent",
            requiredEquipment = dumbbellEquipment,
            boundKg = 24.0
        )

        assertIs<InventoryLoadResolution.Reachable>(named)
        assertEquals(20.0, named.weightKg)
        assertEquals(InventoryLoadResolution.NoReachableLoad, missing)
    }

    @Test
    fun aKnownInventoryWithNothingAtOrBelowTheBoundWithholdsTheLoad() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(40_000))
            )
        )

        assertEquals(
            InventoryLoadResolution.NoReachableLoad,
            resolveInventoryLoad(
                inventory = inventory,
                equipmentId = null,
                requiredEquipment = dumbbellEquipment,
                boundKg = 30.0
            )
        )
    }

    @Test
    fun platesRespectOwnedPlateCountsAndTheBarWeight() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(10_000, 4)),
            bars = listOf(LoadingBar(id = "bar", name = "Bar", weightTicks = 20_000))
        )

        val resolution = resolveInventoryLoad(
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = barbellEquipment,
            boundKg = 40.4
        )

        assertIs<InventoryLoadResolution.Reachable>(resolution)
        assertEquals(40.0, resolution.weightKg)
    }

    @Test
    fun aPoundInventoryConvertsTheKilogramBoundInsteadOfTreatingItAsPounds() {
        val inventory = LoadingInventory(
            unit = "LB",
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Pounds", fixedTicks = listOf(55_000))
            )
        )

        val resolution = resolveInventoryLoad(
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = dumbbellEquipment,
            boundKg = 25.0
        )

        assertIs<InventoryLoadResolution.Reachable>(resolution)
        val expected = WeightUnit.LB.displayToKilograms(55.0)
        assertTrue(abs(resolution.weightKg - expected) < 1e-6)
        assertFalse(abs(resolution.weightKg - 25.0) < 1e-6)
    }

    @Test
    fun anAbsentOrIncompatibleInventoryKeepsTheGenericEstimate() {
        assertEquals(
            InventoryLoadResolution.NoApplicableInventory,
            resolveInventoryLoad(
                inventory = null,
                equipmentId = null,
                requiredEquipment = dumbbellEquipment,
                boundKg = 30.0
            )
        )
        val barsOnly = LoadingInventory(
            bars = listOf(LoadingBar(id = "bar", name = "Bar", weightTicks = 20_000))
        )
        assertEquals(
            InventoryLoadResolution.NoApplicableInventory,
            resolveInventoryLoad(
                inventory = barsOnly,
                equipmentId = null,
                requiredEquipment = dumbbellEquipment,
                boundKg = 30.0
            )
        )
        assertTrue(hasApplicableLoadingInventory(barsOnly, barbellEquipment))
        assertFalse(hasApplicableLoadingInventory(barsOnly, dumbbellEquipment))
    }

    @Test
    fun anExplicitIdentityOfTheWrongImplementKindWithholdsTheLoad() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(10_000, 4)),
            bars = listOf(LoadingBar(id = "bar", name = "Bar", weightTicks = 20_000)),
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(20_000))
            )
        )

        // A dumbbell exercise naming a saved bar must not borrow the bar's grid, and neither may a
        // barbell exercise borrow a dumbbell's.
        assertEquals(
            InventoryLoadResolution.NoReachableLoad,
            resolveInventoryLoad(
                inventory = inventory,
                equipmentId = "bar",
                requiredEquipment = dumbbellEquipment,
                boundKg = 25.0
            )
        )
        assertEquals(
            InventoryLoadResolution.NoReachableLoad,
            resolveInventoryLoad(
                inventory = inventory,
                equipmentId = "db",
                requiredEquipment = barbellEquipment,
                boundKg = 25.0
            )
        )
    }

    @Test
    fun aLowRepManualPrFloorOnlyLiftsAShortPrescription() {
        assertNull(
            oneRepMaxBoundForReps(
                reps = 5,
                progressedBaselineKg = null,
                lowRepManualPrFloorKg = 120.0
            )
        )
        assertEquals(
            120.0,
            oneRepMaxBoundForReps(
                reps = 2,
                progressedBaselineKg = null,
                lowRepManualPrFloorKg = 120.0
            )
        )
        assertEquals(
            120.0,
            oneRepMaxBoundForReps(
                reps = 1,
                progressedBaselineKg = 110.0,
                lowRepManualPrFloorKg = 120.0
            )
        )
        assertEquals(
            110.0,
            oneRepMaxBoundForReps(
                reps = 5,
                progressedBaselineKg = 110.0,
                lowRepManualPrFloorKg = 120.0
            )
        )
    }

    @Test
    fun theEquipmentCeilingBoundsTheReachableLoad() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    id = "db",
                    name = "Dumbbells",
                    fixedTicks = listOf(25_000, 30_000, 35_000)
                )
            )
        )

        val resolution = resolveInventoryLoad(
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = dumbbellEquipment,
            boundKg = 34.0,
            ceilingKg = 30.0
        )

        assertIs<InventoryLoadResolution.Reachable>(resolution)
        assertEquals(30.0, resolution.weightKg)
    }

    @Test
    fun orderedTargetsUseTheirOwnReachableLoadPerSet() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(25_000, 30_000))
            )
        )
        val targets = SlotSetTargets.of(
            listOf(
                SetTarget(reps = 5, loadKind = LoadKind.EXTERNAL, weightKg = 35.0),
                SetTarget(reps = 10, loadKind = LoadKind.EXTERNAL, weightKg = 27.5)
            )
        )

        val rounded = roundOrderedTargetsToInventory(
            targets = targets,
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = dumbbellEquipment,
            estimatedOneRepMaxKg = 45.0,
            intensityScale = 0.9,
            ceilingKg = null,
            weightConfig = SuggestedWeightConfig()
        )

        // 45 x 0.783 x 0.9 = 31.71 -> 30; 45 x 0.675 x 0.9 = 27.34 -> 25.
        assertEquals(listOf(30.0, 25.0), rounded?.targets?.map { it.weightKg })
    }

    @Test
    fun anUnreachableOrderedTargetKeepsItsMeasureButLosesItsNumber() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(id = "db", name = "Dumbbells", fixedTicks = listOf(40_000))
            )
        )
        val targets = SlotSetTargets.of(
            listOf(SetTarget(reps = 5, loadKind = LoadKind.EXTERNAL, weightKg = 32.5))
        )

        val rounded = roundOrderedTargetsToInventory(
            targets = targets,
            inventory = inventory,
            equipmentId = null,
            requiredEquipment = dumbbellEquipment,
            estimatedOneRepMaxKg = 40.0,
            intensityScale = 0.9,
            ceilingKg = null,
            weightConfig = SuggestedWeightConfig()
        )

        assertEquals(5, rounded?.targets?.single()?.reps)
        assertNull(rounded?.targets?.single()?.weightKg)
    }
}
