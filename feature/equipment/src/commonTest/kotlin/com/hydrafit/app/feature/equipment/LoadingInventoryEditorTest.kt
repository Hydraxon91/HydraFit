package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The loading-inventory draft's validation and copy rules: what a draft accepts, what it refuses
 * with a typed failure, and that the stored unit is copied verbatim rather than reinterpreted.
 */
class LoadingInventoryEditorTest {

    private fun invalid(state: LoadingInventoryEditorState): LoadingEditorFailure =
        assertIs<LoadingDraftResult.Invalid>(state.buildInventory()).failure

    private fun inventory(state: LoadingInventoryEditorState): LoadingInventory =
        assertIs<LoadingDraftResult.Valid>(state.buildInventory()).inventory

    private fun draft(
        plates: List<OwnedPlateDraft> = emptyList(),
        bars: List<LoadingBarDraft> = emptyList(),
        dumbbells: List<LoadingDumbbellDraft> = emptyList()
    ) = LoadingInventoryEditorState(
        open = true,
        plates = plates,
        bars = bars,
        dumbbells = dumbbells
    )

    @Test
    fun anEmptyInventoryLeavesTheUnitSelectable() {
        val state = LoadingInventoryEditorState.forInventory(LoadingInventory(), token = 1L)

        assertTrue(state.isOpen)
        assertFalse(state.unitLocked)
        assertEquals(LoadingUnitOption.KG, state.unit)
        assertEquals(LoadingUnitOption.LB, state.withUnit(LoadingUnitOption.LB).unit)
    }

    @Test
    fun aPopulatedInventoryCopiesAmountsVerbatimAndLocksItsUnit() {
        val stored = LoadingInventory(
            unit = "LB",
            plates = listOf(OwnedPlate(45_000, 2)),
            bars = listOf(LoadingBar("BAR", "Bar", 20_000, collarTicks = 2_500)),
            dumbbells = listOf(
                LoadingDumbbell("DB", "Dumbbells", fixedTicks = listOf(10_000, 20_000))
            )
        )

        val state = LoadingInventoryEditorState.forInventory(stored, token = 7L)

        assertEquals(7L, state.token)
        assertEquals(LoadingUnitOption.LB, state.unit)
        assertTrue(state.unitLocked)
        assertEquals(LoadingUnitOption.LB, state.withUnit(LoadingUnitOption.KG).unit)
        assertEquals(listOf(OwnedPlateDraft("45", "2")), state.plates)
        assertEquals(listOf(LoadingBarDraft("BAR", "Bar", "20", "2.5")), state.bars)
        assertEquals(
            listOf(
                LoadingDumbbellDraft(
                    sourceId = "DB",
                    name = "Dumbbells",
                    mode = DumbbellDraftMode.FIXED_VALUES,
                    fixedInput = "10, 20"
                )
            ),
            state.dumbbells
        )

        // The untouched copy rebuilds to the stored configuration, in the same unit.
        assertEquals(stored, inventory(state))
    }

    @Test
    fun anAdjustableDumbbellKeepsItsRangeFields() {
        val stored = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    "ADJ",
                    "Adjustable",
                    minimumTicks = 5_000,
                    maximumTicks = 25_000,
                    incrementTicks = 5_000
                )
            )
        )

        val state = LoadingInventoryEditorState.forInventory(stored, token = 1L)

        assertEquals(
            listOf(
                LoadingDumbbellDraft(
                    sourceId = "ADJ",
                    name = "Adjustable",
                    mode = DumbbellDraftMode.ADJUSTABLE_RANGE,
                    minimumInput = "5",
                    maximumInput = "25",
                    incrementInput = "5"
                )
            ),
            state.dumbbells
        )
        assertEquals(stored, inventory(state))
    }

    @Test
    fun overprecisionIsRejectedRatherThanTruncated() {
        assertEquals(
            LoadingEditorFailure.PLATE_VALUE,
            invalid(draft(plates = listOf(OwnedPlateDraft("1.2345", "2"))))
        )
        assertEquals(
            LoadingEditorFailure.AMOUNT_RANGE,
            invalid(draft(bars = listOf(LoadingBarDraft(name = "Bar", weightInput = "1.2345"))))
        )
    }

    @Test
    fun aBlankPlateRowIsRejectedInsteadOfBecomingZero() {
        assertEquals(
            LoadingEditorFailure.PLATE_VALUE,
            invalid(draft(plates = listOf(OwnedPlateDraft("", "2"))))
        )
        assertEquals(
            LoadingEditorFailure.PLATE_VALUE,
            invalid(draft(plates = listOf(OwnedPlateDraft("0", "2"))))
        )
        assertEquals(
            LoadingEditorFailure.PLATE_VALUE,
            invalid(draft(plates = listOf(OwnedPlateDraft("3000", "2"))))
        )
    }

    @Test
    fun aPlateCountOutsideTheOwnedRangeIsRejected() {
        assertEquals(
            LoadingEditorFailure.PLATE_COUNT,
            invalid(draft(plates = listOf(OwnedPlateDraft("20", ""))))
        )
        assertEquals(
            LoadingEditorFailure.PLATE_COUNT,
            invalid(draft(plates = listOf(OwnedPlateDraft("20", "101"))))
        )
        // An owned-but-unused denomination (count 0) is still a valid entry.
        assertEquals(
            listOf(OwnedPlate(20_000, 0)),
            inventory(draft(plates = listOf(OwnedPlateDraft("20", "0")))).plates
        )
    }

    @Test
    fun duplicateDenominationsAndOverLimitInventoriesAreRejected() {
        assertEquals(
            LoadingEditorFailure.DUPLICATE_PLATE,
            invalid(
                draft(
                    plates = listOf(OwnedPlateDraft("20", "2"), OwnedPlateDraft("20", "4"))
                )
            )
        )
        val tooMany = List(17) { OwnedPlateDraft("${it + 1}", "2") }
        assertEquals(LoadingEditorFailure.PLATE_LIMIT, invalid(draft(plates = tooMany)))

        val tooHeavy = draft(plates = listOf(OwnedPlateDraft("2000", "2")))
        assertEquals(LoadingEditorFailure.INVENTORY_LIMIT, invalid(tooHeavy))
    }

    @Test
    fun aBarKeepsItsIdentityAndCarriesATotalCollarWeight() {
        val built = inventory(
            draft(
                bars = listOf(
                    LoadingBarDraft(
                        sourceId = "BAR",
                        name = "Bar",
                        weightInput = "20",
                        collarInput = "2.5"
                    )
                )
            )
        )

        assertEquals(listOf(LoadingBar("BAR", "Bar", 20_000, 2_500)), built.bars)
    }

    @Test
    fun aBarNeedsAUniqueNameAndInRangeAmounts() {
        assertEquals(
            LoadingEditorFailure.NAME_REQUIRED,
            invalid(
                draft(
                    bars = listOf(
                        LoadingBarDraft(name = "", weightInput = "20", collarInput = "0")
                    )
                )
            )
        )
        assertEquals(
            LoadingEditorFailure.NAME_DUPLICATE,
            invalid(
                draft(
                    bars = listOf(
                        LoadingBarDraft(name = "Bar", weightInput = "20", collarInput = "0"),
                        LoadingBarDraft(name = "Bar", weightInput = "15", collarInput = "0")
                    )
                )
            )
        )
        // A weighted bar must state its total collar weight; a blank collar is not silently zero.
        assertEquals(
            LoadingEditorFailure.AMOUNT_RANGE,
            invalid(draft(bars = listOf(LoadingBarDraft(name = "Bar", weightInput = ""))))
        )
        assertEquals(
            LoadingEditorFailure.AMOUNT_RANGE,
            invalid(draft(bars = listOf(LoadingBarDraft(name = "Bar", weightInput = "20"))))
        )
    }

    @Test
    fun dumbbellsUseExactlyOneRepresentation() {
        val fixed = inventory(
            draft(
                dumbbells = listOf(
                    LoadingDumbbellDraft(name = "Dumbbells", fixedInput = "10, 20")
                )
            )
        )
        assertEquals(
            listOf(LoadingDumbbell("DUMBBELLS", "Dumbbells", fixedTicks = listOf(10_000, 20_000))),
            fixed.dumbbells
        )

        val adjustable = inventory(
            draft(
                dumbbells = listOf(
                    LoadingDumbbellDraft(
                        name = "Adjustable",
                        mode = DumbbellDraftMode.ADJUSTABLE_RANGE,
                        minimumInput = "5",
                        maximumInput = "25",
                        incrementInput = "5"
                    )
                )
            )
        )
        assertEquals(
            listOf(
                LoadingDumbbell(
                    "ADJUSTABLE",
                    "Adjustable",
                    minimumTicks = 5_000,
                    maximumTicks = 25_000,
                    incrementTicks = 5_000
                )
            ),
            adjustable.dumbbells
        )
    }

    @Test
    fun incompleteOrInconsistentDumbbellDraftsAreRejected() {
        assertEquals(
            LoadingEditorFailure.DUMBBELL_FIXED_VALUE,
            invalid(
                draft(
                    dumbbells = listOf(LoadingDumbbellDraft(name = "D", fixedInput = "10, 10"))
                )
            )
        )
        assertEquals(
            LoadingEditorFailure.DUMBBELL_FIXED_VALUE,
            invalid(
                draft(dumbbells = listOf(LoadingDumbbellDraft(name = "D", fixedInput = "10, ")))
            )
        )
        assertEquals(
            LoadingEditorFailure.DUMBBELL_RANGE,
            invalid(
                draft(
                    dumbbells = listOf(
                        LoadingDumbbellDraft(
                            name = "D",
                            mode = DumbbellDraftMode.ADJUSTABLE_RANGE,
                            minimumInput = "5",
                            maximumInput = "25"
                        )
                    )
                )
            )
        )
        assertEquals(
            LoadingEditorFailure.DUMBBELL_RANGE,
            invalid(
                draft(
                    dumbbells = listOf(
                        LoadingDumbbellDraft(
                            name = "D",
                            mode = DumbbellDraftMode.ADJUSTABLE_RANGE,
                            minimumInput = "5",
                            maximumInput = "25",
                            incrementInput = "7"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun aValidDraftBuildsAnInventoryInItsOwnUnit() {
        val state = draft(plates = listOf(OwnedPlateDraft("20", "2")))
            .withUnit(LoadingUnitOption.LB)

        assertEquals(
            LoadingInventory(unit = "LB", plates = listOf(OwnedPlate(20_000, 2))),
            inventory(state)
        )
    }

    @Test
    fun aNewBarRowStartsWithAnExplicitZeroCollar() {
        val state = LoadingInventoryEditorState(open = true).withBarAdded()

        assertEquals(listOf(LoadingBarDraft(collarInput = "0")), state.bars)
    }

    @Test
    fun removingARowOnlyChangesTheDraft() {
        val state = draft(
            plates = listOf(OwnedPlateDraft("20", "2"), OwnedPlateDraft("10", "4")),
            bars = listOf(LoadingBarDraft(name = "Bar", weightInput = "20")),
            dumbbells = listOf(LoadingDumbbellDraft(name = "D", fixedInput = "10"))
        )

        val reduced = state.withPlateRemoved(0).withBarRemoved(0).withDumbbellRemoved(0)

        assertEquals(listOf(OwnedPlateDraft("10", "4")), reduced.plates)
        assertTrue(reduced.bars.isEmpty())
        assertTrue(reduced.dumbbells.isEmpty())
        assertEquals(2, state.plates.size)
        assertEquals(1, state.bars.size)
        assertEquals(1, state.dumbbells.size)
    }

    @Test
    fun anUnknownStoredUnitFallsBackToKilogramsWithoutReinterpretingAmounts() {
        val state = LoadingInventoryEditorState.forInventory(
            LoadingInventory(unit = "STONE", plates = listOf(OwnedPlate(45_000, 2))),
            token = 1L
        )

        assertEquals(LoadingUnitOption.KG, state.unit)
        assertTrue(state.unitLocked)
        assertEquals(listOf(OwnedPlateDraft("45", "2")), state.plates)
    }
}
