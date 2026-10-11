package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.AiLoadExplanation
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.LoadBaselineExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.LoadConversionExplanation
import com.hydrafit.app.core.domain.engine.LoadProgressionExplanation
import com.hydrafit.app.core.domain.engine.LoadUnavailableReason
import com.hydrafit.app.core.domain.engine.ProgressionOutcome
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetExplanationTextTest {

    @Test
    fun aCalculatedTargetListsItsRecordedStagesInOrder() {
        val explanation = ExerciseTargetExplanation(
            baseline = LoadBaselineExplanation(
                source = LoadBaselineSource.RECENT_PERFORMANCE,
                recentEstimateKg = 100.0,
                qualifyingSetCount = 3,
                manualFloorKg = 90.0,
                baselineKg = 100.0
            ),
            progression = LoadProgressionExplanation(
                outcome = ProgressionOutcome.APPLIED,
                increments = 2,
                deltaKg = 5.0
            ),
            conversion = LoadConversionExplanation(
                reps = 8,
                intensityFraction = 0.8,
                deloadIntensityScale = 0.8,
                roundToKg = 2.5,
                uncappedWorkingKg = 102.5,
                equipmentCeilingKg = 100.0,
                equipmentCeilingBinds = true
            ),
            setCount = SetCountExplanation(
                baseSets = 4,
                deloadApplied = false,
                deloadVolumeScale = 0.7,
                fatigueEffect = FatigueSetEffect.REDUCED,
                finalSets = 3
            )
        )

        val view = requireNotNull(explanation.toExplanationView())

        assertEquals(TargetExplanationReason.SUMMARY_CALCULATED, view.summary)
        assertEquals(
            listOf(
                TargetExplanationReason.BASELINE_RECENT_ESTIMATE to 100.0,
                TargetExplanationReason.BASELINE_MANUAL_FLOOR to 90.0,
                TargetExplanationReason.BASELINE_QUALIFYING_SETS to 3.0,
                TargetExplanationReason.BASELINE_EFFECTIVE to 100.0,
                TargetExplanationReason.PROGRESSION_APPLIED to 2.0,
                TargetExplanationReason.PROGRESSION_DELTA to 5.0,
                TargetExplanationReason.PROGRESSION_SUCCESS_STREAK_SIZE to 3.0,
                TargetExplanationReason.PROGRESSION_FAILURE_STREAK_SIZE to 3.0,
                TargetExplanationReason.CONVERSION_REPS to 8.0,
                TargetExplanationReason.CONVERSION_INTENSITY to 0.8,
                TargetExplanationReason.CONVERSION_DELOAD_SCALE to 0.8,
                TargetExplanationReason.CONVERSION_ROUNDED_TO to 2.5,
                TargetExplanationReason.CONVERSION_BEFORE_CEILING to 102.5,
                TargetExplanationReason.CONVERSION_CEILING to 100.0,
                TargetExplanationReason.CONVERSION_CEILING_BINDS to null,
                TargetExplanationReason.SETS_BASE to 4.0,
                TargetExplanationReason.SETS_REDUCED_FOR_FATIGUE to null,
                TargetExplanationReason.SETS_FINAL to 3.0
            ),
            view.lines.map { it.reason to it.value }
        )
        assertEquals(
            TargetExplanationValueKind.KILOGRAMS,
            view.lines.first { it.reason == TargetExplanationReason.CONVERSION_ROUNDED_TO }
                .valueKind
        )
        assertEquals(
            TargetExplanationValueKind.PERCENT,
            view.lines.first { it.reason == TargetExplanationReason.CONVERSION_INTENSITY }
                .valueKind
        )
    }

    @Test
    fun aModelProposalReportsTheAppOwnedAdjustmentsItCanProve() {
        val explanation = ExerciseTargetExplanation(
            aiAdjustment = AiLoadExplanation(
                proposedKg = 100.0,
                proposedAccepted = false,
                deloadScaled = true,
                deloadIntensityScale = 0.8,
                progressionBoundKg = 95.0,
                boundedByProgression = true,
                equipmentCeilingKg = 90.0,
                cappedByEquipment = true
            )
        )

        val view = requireNotNull(explanation.toExplanationView())

        assertEquals(TargetExplanationReason.SUMMARY_AI_PROPOSAL, view.summary)
        assertEquals(
            listOf(
                TargetExplanationReason.AI_PROPOSED to 100.0,
                TargetExplanationReason.AI_NOT_ACCEPTED to null,
                TargetExplanationReason.AI_BOUNDED_BY_PROGRESSION to 95.0,
                TargetExplanationReason.AI_CAPPED_BY_EQUIPMENT to 90.0,
                TargetExplanationReason.AI_DELOAD_SCALED to null,
                TargetExplanationReason.AI_DELOAD_SCALE to 0.8
            ),
            view.lines.map { it.reason to it.value }
        )
    }

    @Test
    fun aMissingLoadReportsWhyWithoutInventingADetail() {
        val view = requireNotNull(
            ExerciseTargetExplanation(
                unavailableReason = LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
            ).toExplanationView()
        )

        assertEquals(
            TargetExplanationReason.SUMMARY_UNAVAILABLE_INSUFFICIENT_EVIDENCE,
            view.summary
        )
        assertTrue(view.lines.isEmpty())
    }

    @Test
    fun everyUnavailableReasonMapsToItsOwnSummary() {
        val summaries = LoadUnavailableReason.entries.map { reason ->
            requireNotNull(
                ExerciseTargetExplanation(unavailableReason = reason).toExplanationView()
            )
                .summary
        }

        assertEquals(LoadUnavailableReason.entries.size, summaries.toSet().size)
    }

    @Test
    fun everyProgressionOutcomeReadsDifferently() {
        val reasons = ProgressionOutcome.entries.map { outcome ->
            val view = requireNotNull(
                ExerciseTargetExplanation(
                    progression = LoadProgressionExplanation(outcome = outcome)
                ).toExplanationView()
            )
            view.lines.first().reason
        }

        assertEquals(ProgressionOutcome.entries.size, reasons.toSet().size)
    }

    @Test
    fun aSetCountOnlyTargetReportsTheSetStages() {
        val view = requireNotNull(
            ExerciseTargetExplanation(
                setCount = SetCountExplanation(
                    baseSets = 4,
                    deloadApplied = true,
                    deloadVolumeScale = 0.7,
                    fatigueEffect = FatigueSetEffect.NONE,
                    finalSets = 3
                )
            ).toExplanationView()
        )

        assertEquals(TargetExplanationReason.SUMMARY_SETS_ONLY, view.summary)
        assertEquals(
            listOf(
                TargetExplanationReason.SETS_BASE to 4.0,
                TargetExplanationReason.SETS_DELOAD_SCALE to 0.7,
                TargetExplanationReason.SETS_FINAL to 3.0
            ),
            view.lines.map { it.reason to it.value }
        )
    }

    @Test
    fun aReplacedOrManualSlotShowsNothing() {
        assertNull(ExerciseTargetExplanation().toExplanationView())
    }
}
