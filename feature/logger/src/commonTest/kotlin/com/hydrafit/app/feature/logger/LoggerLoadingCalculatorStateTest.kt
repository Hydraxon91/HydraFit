package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The helper's pure resolution: parsing, unit conversion, eligibility and result shaping. */
class LoggerLoadingCalculatorStateTest {

    private fun request(
        loadKind: LoadKind? = LoadKind.EXTERNAL,
        weightKg: Double? = 100.0,
        source: LoggerLoadingTargetSource = LoggerLoadingTargetSource.DRAFT,
        sourceId: String = "7"
    ) = LoggerLoadingCalculatorRequest(
        source = source,
        sourceId = sourceId,
        sourceName = "Bench Press",
        loadKind = loadKind,
        weightKg = weightKg,
        occurrenceId = 3L
    )

    @Test
    fun manualTargetAcceptsNativeDecimalsAndRefusesEverythingElse() {
        assertEquals(0, (parseLoggerLoadingTarget("0") as LoggerLoadingTargetParse.Valid).ticks)
        assertEquals(
            80_500,
            (parseLoggerLoadingTarget(" 80.5 ") as LoggerLoadingTargetParse.Valid).ticks
        )
        assertEquals(
            2_000_000,
            (parseLoggerLoadingTarget("2000") as LoggerLoadingTargetParse.Valid).ticks
        )
        assertEquals(
            LoggerLoadingCalculatorFailure.INVALID_TARGET,
            (parseLoggerLoadingTarget("") as LoggerLoadingTargetParse.Invalid).failure
        )
        assertEquals(
            LoggerLoadingCalculatorFailure.INVALID_TARGET,
            (parseLoggerLoadingTarget("1.0005") as LoggerLoadingTargetParse.Invalid).failure
        )
        assertEquals(
            LoggerLoadingCalculatorFailure.INVALID_TARGET,
            (parseLoggerLoadingTarget("-1") as LoggerLoadingTargetParse.Invalid).failure
        )
        assertEquals(
            LoggerLoadingCalculatorFailure.INVALID_TARGET,
            (parseLoggerLoadingTarget("1e3") as LoggerLoadingTargetParse.Invalid).failure
        )
        assertEquals(
            LoggerLoadingCalculatorFailure.OUT_OF_RANGE,
            (parseLoggerLoadingTarget("2000.001") as LoggerLoadingTargetParse.Invalid).failure
        )
        assertEquals(2_000, loggerLoadingMaxWholeAmount())
    }

    @Test
    fun prescribedKilogramsResolveInTheInventoryUnitNotTheDisplayPreference() {
        val exact = loggerLoadingTargetFor(request(weightKg = 45.0), WeightUnit.KG)
        assertNotNull(exact)
        assertEquals(45_000, exact.targetTicks)
        assertNull(exact.approximation)

        val approximate = loggerLoadingTargetFor(request(weightKg = 45.0), WeightUnit.LB)
        assertNotNull(approximate)
        // 45 kg = 99.208017981 lb, rounded to the nearest thousandth of the native unit.
        assertEquals(99_208, approximate.targetTicks)
        val disclosure = assertNotNull(approximate.approximation)
        assertEquals("99.208", disclosure.nativeAmount)
        assertEquals("lb", disclosure.unitLabel)
        assertEquals("45", disclosure.prescribedKg)
        // The prescribed kilograms are carried unchanged; nothing rewrites the target.
        assertEquals(45.0, approximate.prescribedKg)
    }

    @Test
    fun onlyNumericExternalOrAddedLoadCanSeedAContextualTarget() {
        assertTrue(loggerLoadingTargetEligible(LoadKind.EXTERNAL, 100.0))
        assertTrue(loggerLoadingTargetEligible(LoadKind.ADDED, 5.0))
        assertTrue(loggerLoadingTargetEligible(LoadKind.EXTERNAL, 0.0))
        assertFalse(loggerLoadingTargetEligible(LoadKind.EXTERNAL, null))
        assertFalse(loggerLoadingTargetEligible(LoadKind.BODYWEIGHT, 100.0))
        assertFalse(loggerLoadingTargetEligible(LoadKind.LEGACY_UNSPECIFIED, 100.0))
        assertFalse(loggerLoadingTargetEligible(LoadKind.EXTERNAL, Double.NaN))
        assertNull(loggerLoadingTargetFor(request(weightKg = null), WeightUnit.KG))
    }

    @Test
    fun plateResultsReportPerSideCountsAndTheFullTotal() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(5_000, 2)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000, collarTicks = 500))
        )
        val exact = assertNotNull(
            runLoggerLoadingCalculation(
                inventory,
                LoggerLoadingCalculatorKind.PLATE,
                "bar",
                30_500
            )
        )
        assertTrue(exact.isExact)
        assertEquals("30.5 kg", exact.targetWeight)
        val option = exact.options.single()
        assertEquals(LoggerLoadingResultLabel.EXACT, option.label)
        // Bar plus collars once, plus both sides of plates.
        assertEquals("30.5 kg", option.totalWeight)
        assertEquals(listOf(LoggerLoadingPlateLine("5", 1)), option.plateLines)
        assertFalse(option.perDumbbell)
    }

    @Test
    fun anUnreachablePlateTargetReportsNearestSidesAndMissingArrangements() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(5_000, 2)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        val result = assertNotNull(
            runLoggerLoadingCalculation(inventory, LoggerLoadingCalculatorKind.PLATE, "bar", 31_000)
        )
        assertFalse(result.isExact)
        assertEquals("31 kg", result.targetWeight)
        val lower = result.options.single { it.label == LoggerLoadingResultLabel.LOWER }
        assertEquals("30 kg", lower.totalWeight)
        assertEquals(listOf(LoggerLoadingPlateLine("5", 1)), lower.plateLines)
        assertTrue(result.missingUpper)
        assertFalse(result.missingLower)

        val belowEverything = assertNotNull(
            runLoggerLoadingCalculation(inventory, LoggerLoadingCalculatorKind.PLATE, "bar", 10_000)
        )
        assertFalse(belowEverything.isExact)
        assertEquals("20 kg", belowEverything.options.single().totalWeight)
        assertTrue(belowEverything.missingLower)
        assertFalse(belowEverything.missingUpper)
    }

    @Test
    fun dumbbellResultsArePerDumbbellAndNeverDoubled() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell("db", "Dumbbell", fixedTicks = listOf(10_000, 20_000))
            )
        )
        val exact = assertNotNull(
            runLoggerLoadingCalculation(
                inventory,
                LoggerLoadingCalculatorKind.DUMBBELL,
                "db",
                20_000
            )
        )
        val option = exact.options.single()
        assertEquals(LoggerLoadingResultLabel.EXACT, option.label)
        assertEquals("20 kg", option.totalWeight)
        assertTrue(option.perDumbbell)

        val between = assertNotNull(
            runLoggerLoadingCalculation(
                inventory,
                LoggerLoadingCalculatorKind.DUMBBELL,
                "db",
                15_000
            )
        )
        assertFalse(between.isExact)
        assertEquals(
            "10 kg",
            between.options.single { it.label == LoggerLoadingResultLabel.LOWER }.totalWeight
        )
        assertEquals(
            "20 kg",
            between.options.single { it.label == LoggerLoadingResultLabel.UPPER }.totalWeight
        )
    }

    @Test
    fun anUnknownConfigurationOrInvalidInventoryResolvesToNull() {
        val inventory = LoadingInventory(bars = listOf(LoadingBar("bar", "Bar", 20_000)))
        assertNull(
            runLoggerLoadingCalculation(
                inventory,
                LoggerLoadingCalculatorKind.PLATE,
                "missing",
                30_000
            )
        )
        assertNull(
            runLoggerLoadingCalculation(
                LoadingInventory(plates = listOf(OwnedPlate(0, 2))),
                LoggerLoadingCalculatorKind.PLATE,
                "bar",
                30_000
            )
        )
    }

    @Test
    fun theStoredInventoryUnitLabelsResultsAndDefaultsToKilograms() {
        assertEquals(WeightUnit.LB, loggerLoadingInventoryUnit("LB"))
        assertEquals(WeightUnit.KG, loggerLoadingInventoryUnit("KG"))
        assertEquals("99.208 lb", formatLoggerLoadingAmount(99_208, WeightUnit.LB))
        assertEquals(
            listOf("Bar"),
            loggerLoadingCalculatorOptions(
                LoadingInventory(bars = listOf(LoadingBar("bar", "Bar", 20_000))),
                LoggerLoadingCalculatorKind.PLATE
            ).map { it.label }
        )
    }
}
