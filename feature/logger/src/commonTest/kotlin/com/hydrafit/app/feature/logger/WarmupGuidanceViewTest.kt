package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupGuidance
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupRampPlan
import com.hydrafit.app.core.domain.workout.WarmupRampSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarmupGuidanceViewTest {

    private val guidance = WarmupGuidance(
        preparation = listOf(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 180),
            WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 10)
        ),
        ramps = listOf(
            WarmupRampPlan(
                occurrenceEntryId = 40L,
                exerciseId = "back-squat",
                exerciseName = "Back Squat",
                movementPattern = MovementPattern.SQUAT,
                workingWeightKg = 100.0,
                prescribedReps = 8,
                loadKind = LoadKind.EXTERNAL,
                loadCapability = ExerciseLoadCapability.EXTERNAL,
                rampSets = listOf(WarmupRampSet(40, 8, 40.0))
            )
        )
    )

    @Test
    fun defaultsRenderWithoutEditsAndKeepTheirPercentage() {
        val view = buildWarmupGuidanceView(guidance)

        assertEquals(2, view.preparation.size)
        assertFalse(view.preparation.any { it.edited })
        assertEquals(40, view.ramps.single().rows.single().percentOfWorkingWeight)
    }

    @Test
    fun appliesPreparationEditsAndRemovesSkippedItems() {
        val view = buildWarmupGuidanceView(
            guidance,
            preparationEdits = mapOf(
                WarmupAction.LOWER_BODY_PRACTICE to
                    WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 15)
            ),
            skipped = setOf(WarmupAction.EASY_GENERAL_MOVEMENT)
        )

        assertEquals(listOf(WarmupAction.LOWER_BODY_PRACTICE), view.preparation.map { it.action })
        val row = view.preparation.single()
        assertEquals(15, row.reps)
        assertTrue(row.edited)
    }

    @Test
    fun anEditedRampDropsItsDefaultPercentage() {
        val key = WarmupRampKey(occurrenceEntryId = 40L, rampIndex = 0)
        val view = buildWarmupGuidanceView(
            guidance,
            rampEdits = mapOf(
                key to WarmupRampSet(percentOfWorkingWeight = 40, reps = 6, weightKg = 45.0)
            )
        )

        val row = view.ramps.single().rows.single()
        assertEquals(6, row.reps)
        assertEquals(45.0, row.weightKg)
        assertNull(row.percentOfWorkingWeight)
    }

    @Test
    fun anEquipmentLimitedDefaultIsMarkedAndAnEditIsNot() {
        val limited = guidance.copy(
            ramps = guidance.ramps.map { plan ->
                plan.copy(
                    ceilingKg = 50.0,
                    rampSets = listOf(WarmupRampSet(60, 5, 50.0, isEquipmentLimited = true))
                )
            }
        )

        val row = buildWarmupGuidanceView(limited).ramps.single().rows.single()
        assertEquals(50.0, row.weightKg)
        assertTrue(row.isEquipmentLimited)
        assertFalse(row.exceedsCeiling)

        // An edit shows the user's own value, so the equipment-limit marking no longer applies.
        val edited = buildWarmupGuidanceView(
            limited,
            rampEdits = mapOf(
                WarmupRampKey(occurrenceEntryId = 40L, rampIndex = 0) to
                    WarmupRampSet(60, 5, 55.0, isEquipmentLimited = true)
            )
        ).ramps.single().rows.single()
        assertFalse(edited.isEquipmentLimited)
    }

    @Test
    fun aRetainedEditStaysVisibleWhenItsDefaultCollapsedAway() {
        // The ceiling deduped the three profile loads down to a single 30 kg row.
        val collapsed = guidance.copy(
            ramps = guidance.ramps.map { plan ->
                plan.copy(
                    ceilingKg = 30.0,
                    rampSets = listOf(WarmupRampSet(40, 8, 30.0, isEquipmentLimited = true))
                )
            }
        )
        val retained = WarmupRampKey(occurrenceEntryId = 40L, rampIndex = 2)
        val view = buildWarmupGuidanceView(
            collapsed,
            rampEdits = mapOf(retained to WarmupRampSet(80, 3, 65.0))
        )

        val rows = view.ramps.single().rows
        assertEquals(listOf(0, 2), rows.map { it.key.rampIndex })
        assertEquals(30.0, rows.first().weightKg)

        val edited = rows.last()
        assertEquals(3, edited.reps)
        assertEquals(65.0, edited.weightKg)
        assertNull(edited.percentOfWorkingWeight)
        assertTrue(edited.exceedsCeiling)
    }

    @Test
    fun skippingEveryPreparationStillReportsSomethingToRestore() {
        val preparationOnly = WarmupGuidance(
            preparation = listOf(
                WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 180)
            ),
            ramps = emptyList()
        )

        val skipped = buildWarmupGuidanceView(
            preparationOnly,
            skipped = setOf(WarmupAction.EASY_GENERAL_MOVEMENT)
        )
        assertTrue(skipped.preparation.isEmpty())
        assertEquals(1, skipped.skippedPreparationCount)
        assertFalse(skipped.isEmpty)

        // A skipped action that no longer exists cannot keep an empty card alive.
        val stale = buildWarmupGuidanceView(
            preparationOnly,
            skipped = setOf(WarmupAction.LIGHT_PULL_PRACTICE)
        )
        assertEquals(0, stale.skippedPreparationCount)

        assertTrue(
            buildWarmupGuidanceView(preparationOnly.copy(preparation = emptyList())).isEmpty
        )
    }

    @Test
    fun anEmptyGuidanceViewReportsItselfEmpty() {
        val empty = buildWarmupGuidanceView(
            WarmupGuidance(preparation = emptyList(), ramps = emptyList())
        )

        assertTrue(empty.isEmpty)
    }
}
